package com.dualshield.phone.core.rules

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction

/**
 * Everything one SIM needs to make a decision, pre-indexed.
 *
 * The tiers mirror the documented precedence order and exist as separate indexes so that
 * evaluation is a fixed sequence of cheap lookups rather than a sort at call time.
 */
data class SimRuleSet(
    val slotIndex: Int,
    val label: String,
    val filteringEnabled: Boolean,
    val allowNumbers: Set<String>,
    val userAllow: RuleIndex,
    val userBlock: RuleIndex,
    val builtInAllow: RuleIndex,
    val builtInBlock: RuleIndex,
    val heuristicBlock: RuleIndex,
) {
    companion object {
        fun disabled(slotIndex: Int, label: String) = SimRuleSet(
            slotIndex = slotIndex,
            label = label,
            filteringEnabled = false,
            allowNumbers = emptySet(),
            userAllow = RuleIndex.EMPTY,
            userBlock = RuleIndex.EMPTY,
            builtInAllow = RuleIndex.EMPTY,
            builtInBlock = RuleIndex.EMPTY,
            heuristicBlock = RuleIndex.EMPTY,
        )
    }
}

/**
 * An immutable, in-memory view of the whole rule database, keyed by SIM slot.
 *
 * The screening service reads this and only this. Room is never touched on the call path.
 */
data class RuleSnapshot(
    val perSlot: Map<Int, SimRuleSet>,
    val revision: Long = 0L,
) {
    fun forSlot(slotIndex: Int): SimRuleSet? = perSlot[slotIndex]

    companion object {
        val EMPTY = RuleSnapshot(emptyMap())

        /**
         * Splits [rules] into the precedence tiers for a single slot.
         *
         * A rule only lands in a slot's set if its scope covers that slot, which is where
         * SIM isolation is actually enforced — a SIM 2 rule is not merely skipped at
         * evaluation time, it is never indexed for SIM 1 at all.
         */
        fun buildSimRuleSet(
            slotIndex: Int,
            label: String,
            filteringEnabled: Boolean,
            allowNumbers: Set<String>,
            rules: List<CompiledRule>,
        ): SimRuleSet {
            val scoped = rules.filter { it.simScope.coversSlot(slotIndex) }

            val userAllow = ArrayList<CompiledRule>()
            val userBlock = ArrayList<CompiledRule>()
            val builtInAllow = ArrayList<CompiledRule>()
            val builtInBlock = ArrayList<CompiledRule>()
            val heuristicBlock = ArrayList<CompiledRule>()

            for (rule in scoped) {
                val userDefined = rule.provenance == Provenance.USER_DEFINED || !rule.builtIn
                when {
                    rule.action == RuleAction.ALLOW && userDefined -> userAllow += rule
                    rule.action == RuleAction.ALLOW -> builtInAllow += rule
                    userDefined -> userBlock += rule
                    rule.confidence == Confidence.LOW -> heuristicBlock += rule
                    else -> builtInBlock += rule
                }
            }

            return SimRuleSet(
                slotIndex = slotIndex,
                label = label,
                filteringEnabled = filteringEnabled,
                allowNumbers = allowNumbers,
                userAllow = RuleIndex.build(userAllow),
                userBlock = RuleIndex.build(userBlock),
                builtInAllow = RuleIndex.build(builtInAllow),
                builtInBlock = RuleIndex.build(builtInBlock),
                heuristicBlock = RuleIndex.build(heuristicBlock),
            )
        }
    }
}
