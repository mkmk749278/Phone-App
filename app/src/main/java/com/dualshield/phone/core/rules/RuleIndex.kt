package com.dualshield.phone.core.rules

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.PhoneNumberInfo
import com.dualshield.phone.core.model.SpecialRule

/**
 * Pattern-type-specialised lookup over a set of rules.
 *
 * Exact matches are a hash lookup; everything else is a short pre-filtered list. The point is
 * that [match] does no allocation-heavy or I/O work, because it runs inside
 * `CallScreeningService.onScreenCall`, where Android gives us only a few seconds.
 */
class RuleIndex private constructor(
    private val exact: Map<String, List<CompiledRule>>,
    private val prefixes: List<CompiledRule>,
    private val contains: List<CompiledRule>,
    private val regexes: List<CompiledRule>,
    private val specials: List<CompiledRule>,
) {

    val isEmpty: Boolean
        get() = exact.isEmpty() && prefixes.isEmpty() && contains.isEmpty() &&
            regexes.isEmpty() && specials.isEmpty()

    /**
     * Returns the first rule that matches [info] and satisfies [accepts], or null.
     *
     * [accepts] is how the calls/SMS target is honoured: a rule that only blocks calls is
     * skipped entirely when a message is being screened. Never throws.
     */
    fun match(
        info: PhoneNumberInfo,
        accepts: (CompiledRule) -> Boolean = { true },
    ): CompiledRule? {
        specials.firstOrNull { accepts(it) && matchesSpecial(it.special, info) }
            ?.let { return it }

        if (!info.hasDigits) return null

        for (candidate in info.matchCandidates) {
            exact[candidate]?.firstOrNull(accepts)?.let { return it }
        }
        prefixes.firstOrNull { rule ->
            accepts(rule) && info.matchCandidates.any { it.startsWith(rule.pattern) }
        }?.let { return it }
        contains.firstOrNull { rule ->
            accepts(rule) && info.matchCandidates.any { it.contains(rule.pattern) }
        }?.let { return it }
        regexes.firstOrNull { rule ->
            if (!accepts(rule)) return@firstOrNull false
            val regex = rule.regex ?: return@firstOrNull false
            info.matchCandidates.any { runCatching { regex.matches(it) }.getOrDefault(false) }
        }?.let { return it }

        return null
    }

    private fun matchesSpecial(special: SpecialRule?, info: PhoneNumberInfo): Boolean =
        when (special) {
            // An alphanumeric SMS sender is deliberately not a "private caller": nothing was
            // withheld. It does still count as unknown, so a user who had turned on the
            // unknown-sender rule keeps exactly the coverage they had before sender IDs were
            // split out of PRIVATE.
            SpecialRule.PRIVATE_CALLER -> info.kind == NumberKind.PRIVATE
            SpecialRule.INTERNATIONAL_CALLER -> info.kind == NumberKind.INTERNATIONAL
            SpecialRule.UNKNOWN_CALLER ->
                info.kind == NumberKind.PRIVATE ||
                    info.kind == NumberKind.MALFORMED ||
                    info.kind == NumberKind.ALPHANUMERIC_SENDER
            null -> false
        }

    companion object {
        val EMPTY = RuleIndex(emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())

        fun build(rules: List<CompiledRule>): RuleIndex {
            if (rules.isEmpty()) return EMPTY
            // Lower `priority` wins; ties fall back to the more specific (longer) pattern.
            val ordered = rules.sortedWith(
                compareBy<CompiledRule> { it.priority }.thenByDescending { it.pattern.length },
            )
            val exact = LinkedHashMap<String, MutableList<CompiledRule>>()
            val prefixes = ArrayList<CompiledRule>()
            val contains = ArrayList<CompiledRule>()
            val regexes = ArrayList<CompiledRule>()
            val specials = ArrayList<CompiledRule>()
            for (rule in ordered) {
                when (rule.patternType) {
                    // A list per pattern, because two rules can target the same number with
                    // different channels (block its calls, allow its texts).
                    PatternType.EXACT ->
                        exact.getOrPut(rule.pattern) { ArrayList(1) }.add(rule)
                    PatternType.PREFIX -> prefixes += rule
                    PatternType.CONTAINS -> contains += rule
                    PatternType.REGEX -> regexes += rule
                    PatternType.SPECIAL -> specials += rule
                    PatternType.REPEATED_CALL -> Unit // handled in a later phase
                }
            }
            return RuleIndex(exact, prefixes, contains, regexes, specials)
        }
    }
}
