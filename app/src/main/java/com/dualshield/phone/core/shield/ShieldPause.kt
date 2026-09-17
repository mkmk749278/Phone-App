package com.dualshield.phone.core.shield

import com.dualshield.phone.core.model.SimScope

/**
 * A deliberate, temporary suspension of Shield enforcement.
 *
 * This exists because not every unwanted caller should be blocked forever. The user needs to
 * open a window once or twice a week, take the calls they have decided to take, and have
 * protection come back on its own — without rebuilding any rules, and without having to
 * remember to switch it back.
 *
 * Pause is runtime state, never a rule change. Nothing in the rule set is modified, disabled
 * or deleted while a pause is in force, so resuming restores exactly the configuration that
 * was there before.
 *
 * It is also not the allowlist. The allowlist is a permanent, explicit decision to trust a
 * number; a pause is a temporary decision to stop enforcing anything at all, for a scope and
 * for a while. Keeping them separate matters: one is about *who*, the other about *when*.
 */
data class ShieldPause(
    /** Which lines the pause covers. */
    val scope: SimScope,
    /**
     * When the pause lapses, in wall-clock milliseconds, or null for "until I turn it back
     * on".
     *
     * Wall clock rather than elapsed time on purpose: the user is promised "back on at
     * 6:30", and that promise has to survive the device sleeping or restarting in between.
     */
    val expiresAtMillis: Long?,
) {
    val isIndefinite: Boolean get() = expiresAtMillis == null

    /**
     * Whether this pause is still in force at [now].
     *
     * The expiry is decided by comparing timestamps at the moment the question is asked,
     * rather than by a timer that fires and flips a flag. A timer can be late, can be killed
     * with the process, and can race with a call arriving on the boundary; a comparison
     * cannot.
     */
    fun isActiveAt(now: Long): Boolean = expiresAtMillis == null || now < expiresAtMillis

    /** Whether this pause covers [slotIndex]. A null slot is covered only by a both-SIM pause. */
    fun covers(slotIndex: Int?): Boolean = when (scope) {
        SimScope.BOTH -> true
        SimScope.SIM1 -> slotIndex == 0
        SimScope.SIM2 -> slotIndex == 1
    }

    /** Whether enforcement is suspended for [slotIndex] at [now]. */
    fun suspends(slotIndex: Int?, now: Long): Boolean = isActiveAt(now) && covers(slotIndex)

    companion object {
        /** No pause in force. */
        val NONE: ShieldPause? = null

        /** A pause of [durationMillis] from [now], covering [scope]. */
        fun timed(scope: SimScope, durationMillis: Long, now: Long): ShieldPause =
            ShieldPause(scope, now + durationMillis)

        /** A pause with no end, which the user must lift themselves. */
        fun indefinite(scope: SimScope): ShieldPause = ShieldPause(scope, null)
    }
}

/** The durations offered in the UI, plus the labels that describe them. */
enum class PauseDuration(val label: String, val millis: Long?) {
    ONE_HOUR("1 hour", 60L * 60_000L),
    FOUR_HOURS("4 hours", 4L * 60L * 60_000L),
    UNTIL_RESUMED("Until I turn it back on", null),
    ;

    fun toPause(scope: SimScope, now: Long): ShieldPause =
        millis?.let { ShieldPause.timed(scope, it, now) } ?: ShieldPause.indefinite(scope)
}
