package com.dualshield.phone.core.shield

/**
 * What this device has actually seen from one caller.
 *
 * Deliberately built from the user's own phone and nothing else. There is no list of known
 * recovery agencies, BPOs or scam numbers in this app, and there will not be: those numbers
 * rotate constantly, public ownership data is incomplete, and a stale list would label real
 * people wrongly. Repetition, timing and whether the user knows the caller are things this
 * device can observe first-hand, so those are what it reasons about.
 *
 * Nothing here is a claim about *who* is calling. It supports statements like "61 calls in
 * seven days", never "this is a recovery agency".
 */
data class CallerActivity(
    val matchKey: String,
    /** Attempt times, newest last. Bounded — old entries are pruned, not accumulated forever. */
    val attemptsMillis: List<Long> = emptyList(),
) {

    val totalAttempts: Int get() = attemptsMillis.size

    val lastAttemptMillis: Long? get() = attemptsMillis.lastOrNull()

    /** Attempts within [windowMillis] before [now]. */
    fun countWithin(windowMillis: Long, now: Long): Int =
        attemptsMillis.count { it > now - windowMillis }

    /**
     * How many distinct local days this caller has tried on, within the window.
     *
     * Calling on many separate days is a different signal from calling many times in one
     * afternoon: one is persistence, the other is a burst.
     */
    fun distinctDaysWithin(windowMillis: Long, now: Long): Int =
        attemptsMillis
            .filter { it > now - windowMillis }
            .map { it / DAY_MS }
            .distinct()
            .size

    /**
     * Whether the caller is dialling again immediately after a previous attempt.
     *
     * Three or more inside [RAPID_WINDOW_MS] is the pattern of an autodialler or someone
     * ringing back the instant the line clears.
     */
    fun hasRapidRepeat(now: Long): Boolean =
        countWithin(RAPID_WINDOW_MS, now) >= RAPID_THRESHOLD

    /** Returns a copy with [now] appended and anything past the retention window dropped. */
    fun recording(now: Long): CallerActivity {
        val kept = (attemptsMillis + now)
            .filter { it > now - RETENTION_MS }
            .takeLast(MAX_ATTEMPTS)
        return copy(attemptsMillis = kept)
    }

    /** Whether this record still carries anything worth keeping at [now]. */
    fun isStaleAt(now: Long): Boolean = attemptsMillis.none { it > now - RETENTION_MS }

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val WEEK_MS = 7L * DAY_MS
        const val MONTH_MS = 30L * DAY_MS

        /** Attempts closer together than this count as a rapid repeat. */
        const val RAPID_WINDOW_MS = 10L * 60L * 1000L
        const val RAPID_THRESHOLD = 3

        /**
         * How long attempt history is kept.
         *
         * Thirty days is enough for "calls on many separate days" to mean something, and
         * short enough that someone who stopped calling a month ago stops being counted.
         */
        const val RETENTION_MS = MONTH_MS

        /** A hard cap per caller, so a pathological dialler cannot grow this without bound. */
        const val MAX_ATTEMPTS = 200
    }
}
