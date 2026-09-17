package com.dualshield.phone.core.rules

/**
 * What is being screened.
 *
 * A rule declares whether it acts on calls, messages or both, so "block this number" can
 * mean "stop it ringing but let its texts through" — which is what people usually want from
 * a bank or a delivery service.
 */
enum class ShieldChannel {
    CALL,
    SMS;

    /** True when [rule] is allowed to act on this channel. Allow rules act on everything. */
    fun appliesTo(rule: CompiledRule): Boolean = when (this) {
        CALL -> rule.blocksCalls
        SMS -> rule.blocksSms
    }
}
