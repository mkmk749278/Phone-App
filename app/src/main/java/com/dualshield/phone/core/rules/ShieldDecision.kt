package com.dualshield.phone.core.rules

/** Why a call was let through. Surfaced verbatim in diagnostics and the rule tester. */
enum class AllowReason(val explanation: String) {
    SIM_UNRESOLVED("SIM could not be identified, so the call was allowed"),
    SLOT_OUT_OF_RANGE("Call arrived on a SIM slot with no profile, so it was allowed"),
    FILTERING_DISABLED("Protection is off for this SIM"),
    EMERGENCY("Emergency and safety numbers are never blocked"),
    ALLOWLIST("Number is on this SIM's allowlist"),
    ALLOW_RULE("Matched an allow rule"),
    NO_MATCH("No blocking rule matched"),
    ENGINE_ERROR("Protection could not be evaluated, so the call was allowed"),
}

/**
 * The outcome of evaluating one call against one SIM's rules.
 *
 * There is intentionally no third state: anything that is not a definite [Block] is an
 * [Allow]. That is the fail-open guarantee expressed in the type system.
 */
sealed interface ShieldDecision {

    data class Allow(
        val reason: AllowReason,
        val rule: CompiledRule? = null,
    ) : ShieldDecision

    data class Block(
        val rule: CompiledRule,
    ) : ShieldDecision

    val isBlocked: Boolean get() = this is Block
}
