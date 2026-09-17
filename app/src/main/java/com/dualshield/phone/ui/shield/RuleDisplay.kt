package com.dualshield.phone.ui.shield

import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.displayForSlot

/**
 * How a rule reads in a list.
 *
 * MIUI's blocklist shows the pattern itself as the title — `140*` rather than "140
 * Promotional Calls" — because when you are scanning a blocklist you are looking for a
 * number, not a name. These helpers produce that form.
 */
object RuleDisplay {

    /**
     * What a rule matches, in a form a person can read.
     *
     * `9876543210`, `140…`, `…8035…` — and, crucially, never a regular expression. The
     * bundled India rules are written as anchored regexes because that is what matches a
     * numbering series precisely, but `^1600[0-9]{6}$` in a list of blocked numbers tells
     * the user nothing except that this app was built by someone who forgot they were not
     * the user. Where the leading digits can be recovered from the expression they are
     * shown; where they cannot, this returns null and the caller shows the rule's name
     * alone rather than inventing something.
     *
     * The expression itself is still available, in the rule editor, to the person who typed
     * it. That is the one place it means anything.
     */
    fun pattern(rule: CallRuleEntity): String? = when (rule.patternType) {
        PatternType.EXACT -> rule.pattern
        PatternType.PREFIX -> "${rule.pattern}…"
        PatternType.CONTAINS -> "…${rule.pattern}…"
        PatternType.REGEX -> leadingDigitsOf(rule.pattern)?.let { "$it…" }
        PatternType.SPECIAL -> rule.pattern.lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
        PatternType.REPEATED_CALL -> "Repeated callers"
    }

    /**
     * [pattern], or the rule's name when the pattern cannot be put into words.
     *
     * For anywhere that needs a title and cannot show nothing.
     */
    fun patternOrName(rule: CallRuleEntity): String =
        pattern(rule)?.takeIf { it.isNotBlank() } ?: rule.name

    /**
     * The literal digits an anchored regex starts with, or null.
     *
     * `^1600[0-9]{6}$` → `1600`. Deliberately conservative: it reads the leading run of
     * plain digits and stops at the first character that could mean anything else. A regex
     * it cannot summarise honestly gets summarised not at all, because a half-understood
     * pattern shown as fact is worse than a name.
     */
    fun leadingDigitsOf(pattern: String): String? {
        val digits = pattern.removePrefix("^").takeWhile { it.isDigit() }
        return digits.takeIf { it.isNotEmpty() }
    }

    /** The raw pattern, for the editor where the user authored it. Never for a list. */
    fun technicalPattern(rule: CallRuleEntity): String = rule.pattern

    /**
     * One word for what this rule is doing right now: `Allowed`, `Blocked` or `Off`.
     *
     * For the built-in list, where every row has the same shape and the user is scanning a
     * column rather than reading sentences. A switched-off rule reads `Off` rather than the
     * action it *would* take — the switch and the label must never disagree, and a row that
     * says "Blocked" beside an off switch is the kind of thing that makes someone believe
     * they are protected when they are not.
     */
    fun status(rule: CallRuleEntity): String = when {
        !rule.enabled -> "Off"
        rule.action == com.dualshield.phone.core.model.RuleAction.ALLOW -> "Allowed"
        else -> "Blocked"
    }

    /** "Block calls and SMS" / "Block calls" / "Block SMS" / "Allow". */
    fun action(rule: CallRuleEntity): String {
        if (rule.action == com.dualshield.phone.core.model.RuleAction.ALLOW) {
            return "Always allow"
        }
        return when {
            rule.blocksCalls && rule.blocksSms -> "Block calls and SMS"
            rule.blocksSms -> "Block SMS"
            rule.blocksCalls -> "Block calls"
            // Neither: the rule exists but does nothing. Say so rather than implying it works.
            else -> "Not blocking anything"
        }
    }

    /** "SIM 1 · Duty", "Both SIMs". */
    fun scope(scope: SimScope, sims: List<SimOption>): String = when (scope) {
        SimScope.BOTH -> "Both SIMs"
        SimScope.SIM1 -> sims.displayForSlot(0)
        SimScope.SIM2 -> sims.displayForSlot(1)
    }

    /** The one-line subtitle used under a blocklist entry. */
    fun subtitle(rule: CallRuleEntity, sims: List<SimOption>): String = buildString {
        append(action(rule))
        append(" · ")
        append(scope(rule.simScope, sims))
        if (rule.matchCount > 0) append(" · ${rule.matchCount} blocked")
    }
}
