package com.dualshield.phone.core.rules

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.model.SpecialRule

/**
 * A rule with its pattern already parsed and (for regex) already compiled.
 *
 * Compilation happens when the snapshot is built, never on the call-screening path — a
 * regex that failed to compile becomes [regex] == null and is simply skipped, which keeps
 * the engine fail-open in the face of a bad pattern.
 */
data class CompiledRule(
    val id: Long,
    val stableId: String,
    val name: String,
    val category: RuleCategory,
    val pattern: String,
    val patternType: PatternType,
    val action: RuleAction,
    val simScope: SimScope,
    val confidence: Confidence,
    val provenance: Provenance,
    val priority: Int,
    val builtIn: Boolean,
    val description: String,
    val regex: Regex? = null,
    val special: SpecialRule? = null,
) {
    /** Rules whose pattern could not be understood are inert rather than dangerous. */
    val isUsable: Boolean
        get() = when (patternType) {
            PatternType.REGEX -> regex != null
            PatternType.SPECIAL -> special != null
            PatternType.REPEATED_CALL -> false
            else -> pattern.isNotBlank()
        }

    companion object {
        /**
         * Builds a [CompiledRule], compiling the regex / resolving the special class.
         * Returns null when the rule can never match anything useful.
         */
        fun from(
            id: Long,
            stableId: String,
            name: String,
            category: RuleCategory,
            pattern: String,
            patternType: PatternType,
            action: RuleAction,
            simScope: SimScope,
            confidence: Confidence,
            provenance: Provenance,
            priority: Int,
            builtIn: Boolean,
            description: String,
        ): CompiledRule? {
            val normalizedPattern = when (patternType) {
                PatternType.EXACT, PatternType.PREFIX, PatternType.CONTAINS ->
                    pattern.filter { it.isDigit() }
                else -> pattern.trim()
            }
            val regex = if (patternType == PatternType.REGEX) {
                runCatching { Regex(normalizedPattern) }.getOrNull()
            } else {
                null
            }
            val special = if (patternType == PatternType.SPECIAL) {
                SpecialRule.fromPattern(normalizedPattern)
            } else {
                null
            }
            val rule = CompiledRule(
                id = id,
                stableId = stableId,
                name = name,
                category = category,
                pattern = normalizedPattern,
                patternType = patternType,
                action = action,
                simScope = simScope,
                confidence = confidence,
                provenance = provenance,
                priority = priority,
                builtIn = builtIn,
                description = description,
                regex = regex,
                special = special,
            )
            return rule.takeIf { it.isUsable }
        }
    }
}
