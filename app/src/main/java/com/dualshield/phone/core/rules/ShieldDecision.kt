package com.dualshield.phone.core.rules

/** Why a call was let through. Surfaced verbatim in diagnostics and the rule tester. */
enum class AllowReason(val explanation: String) {
    SIM_UNRESOLVED("SIM could not be identified, so the call was allowed"),
    SLOT_OUT_OF_RANGE("Call arrived on a SIM slot with no profile, so it was allowed"),
    FILTERING_DISABLED("Protection is off for this SIM"),
    EMERGENCY("Emergency and safety numbers are never blocked"),
    CONTACT("Saved contacts always get through"),
    ALLOWLIST("Number is on this SIM's allowlist"),
    ALLOW_RULE("Matched an allow rule"),
    NO_MATCH("No blocking rule matched"),
    ENGINE_ERROR("Protection could not be evaluated, so the call was allowed"),
    SHIELD_PAUSED("Shield is paused, so nothing is being blocked"),
}

/**
 * The outcome of evaluating one call against one SIM's rules.
 *
 * Three states, and the distinction between the last two matters:
 *
 *  - [Allow] — ring normally. Everything uncertain lands here. An unresolved SIM, a
 *    malformed number, a thrown exception: all allow. That is the fail-open guarantee, and
 *    it is why no failure path returns anything else.
 *  - [Block] — reject. Only ever from a positive, deliberate match.
 *  - [Screen] — silence without rejecting. The call stays reachable in the call log and can
 *    be returned; it simply does not ring. This is what behavioural signals produce, because
 *    they are about how someone is calling rather than who they are, and that is not enough
 *    certainty to refuse a call outright.
 */
sealed interface ShieldDecision {

    data class Allow(
        val reason: AllowReason,
        val rule: CompiledRule? = null,
    ) : ShieldDecision

    data class Block(
        val rule: CompiledRule,
    ) : ShieldDecision

    /**
     * Silence the call.
     *
     * [signals] are the observations that led here, in the words the user will see. They
     * describe behaviour only — never an identity the app cannot verify.
     */
    data class Screen(
        val signals: List<String>,
    ) : ShieldDecision {
        val summary: String get() = signals.joinToString(" · ")
    }

    val isBlocked: Boolean get() = this is Block

    val isScreened: Boolean get() = this is Screen

    /** True when the call should not ring, whether it was rejected or merely silenced. */
    val silencesRinging: Boolean get() = this is Block || this is Screen
}
