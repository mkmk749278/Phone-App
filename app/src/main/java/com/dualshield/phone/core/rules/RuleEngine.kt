package com.dualshield.phone.core.rules

import com.dualshield.phone.core.model.PhoneNumberInfo
import com.dualshield.phone.core.number.PhoneNumberNormalizer

/**
 * The decision function at the centre of the product.
 *
 * Precedence, highest first:
 *
 *  1. SIM isolation — a call is only ever evaluated against its own SIM's rule set.
 *  2. Emergency and safety numbers.
 *  3. The SIM's explicit allowlist.
 *  4. User-authored allow rules.
 *  5. User-authored block rules.
 *  6. Built-in high/medium-confidence rules (allow beats block inside this tier).
 *  7. Optional low-confidence heuristics.
 *  8. Default allow.
 *
 * Any uncertainty at all — unresolved SIM, unknown slot, thrown exception — resolves to
 * allow. Blocking is only ever the result of a positive, deliberate match.
 */
object RuleEngine {

    /**
     * @param slotIndex zero-based SIM slot, or null when Telecom could not tell us.
     */
    fun evaluate(
        snapshot: RuleSnapshot,
        info: PhoneNumberInfo,
        slotIndex: Int?,
    ): ShieldDecision = try {
        evaluateInternal(snapshot, info, slotIndex)
    } catch (t: Throwable) {
        // Fail open, loudly in logs but silently for the user: a crash must never
        // become a blocked call.
        ShieldDecision.Allow(AllowReason.ENGINE_ERROR)
    }

    private fun evaluateInternal(
        snapshot: RuleSnapshot,
        info: PhoneNumberInfo,
        slotIndex: Int?,
    ): ShieldDecision {
        if (slotIndex == null || slotIndex < 0) {
            return ShieldDecision.Allow(AllowReason.SIM_UNRESOLVED)
        }
        val simRules = snapshot.forSlot(slotIndex)
            ?: return ShieldDecision.Allow(AllowReason.SLOT_OUT_OF_RANGE)

        if (!simRules.filteringEnabled) {
            return ShieldDecision.Allow(AllowReason.FILTERING_DISABLED)
        }
        if (PhoneNumberNormalizer.isEmergency(info)) {
            return ShieldDecision.Allow(AllowReason.EMERGENCY)
        }
        if (info.matchCandidates.any { it in simRules.allowNumbers }) {
            return ShieldDecision.Allow(AllowReason.ALLOWLIST)
        }

        simRules.userAllow.match(info)?.let {
            return ShieldDecision.Allow(AllowReason.ALLOW_RULE, it)
        }
        simRules.userBlock.match(info)?.let {
            return ShieldDecision.Block(it)
        }

        // Inside the built-in tier an explicit allow (1600 / 1601 / 1800) always wins over
        // a block, so a toll-free service number cannot be caught by a broader series rule.
        simRules.builtInAllow.match(info)?.let {
            return ShieldDecision.Allow(AllowReason.ALLOW_RULE, it)
        }
        simRules.builtInBlock.match(info)?.let {
            return ShieldDecision.Block(it)
        }
        simRules.heuristicBlock.match(info)?.let {
            return ShieldDecision.Block(it)
        }

        return ShieldDecision.Allow(AllowReason.NO_MATCH)
    }
}
