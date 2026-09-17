package com.dualshield.phone.ui.shield

import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.SimOption

/**
 * How a rule reads in a list.
 *
 * MIUI's blocklist shows the pattern itself as the title — `140*` rather than "140
 * Promotional Calls" — because when you are scanning a blocklist you are looking for a
 * number, not a name. These helpers produce that form.
 */
object RuleDisplay {

    /** `9876543210`, `140*`, `*8035*`, or the raw pattern for a regex. */
    fun pattern(rule: CallRuleEntity): String = when (rule.patternType) {
        PatternType.EXACT -> rule.pattern
        PatternType.PREFIX -> "${rule.pattern}*"
        PatternType.CONTAINS -> "*${rule.pattern}*"
        PatternType.REGEX -> rule.pattern
        PatternType.SPECIAL -> rule.pattern.lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
        PatternType.REPEATED_CALL -> "Repeated callers"
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
        SimScope.SIM1 -> sims.firstOrNull { it.slotIndex == 0 }?.display ?: "SIM 1"
        SimScope.SIM2 -> sims.firstOrNull { it.slotIndex == 1 }?.display ?: "SIM 2"
    }

    /** The one-line subtitle used under a blocklist entry. */
    fun subtitle(rule: CallRuleEntity, sims: List<SimOption>): String = buildString {
        append(action(rule))
        append(" · ")
        append(scope(rule.simScope, sims))
        if (rule.matchCount > 0) append(" · ${rule.matchCount} blocked")
    }
}
