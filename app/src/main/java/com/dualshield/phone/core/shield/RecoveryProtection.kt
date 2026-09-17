package com.dualshield.phone.core.shield

/** What Shield should do about a caller the local signals find suspicious. */
enum class RecoveryMode(val label: String, val description: String) {
    /** Heuristics off. Only the user's rules and the blocklists apply. */
    NORMAL("Normal", "Only your own rules and blocklists apply"),

    /**
     * Silence suspicious calls without rejecting them.
     *
     * The default, and deliberately not blocking. These signals are about behaviour, not
     * identity, and behaviour is sometimes explained by something innocent — a clinic
     * calling back three times, a courier at the door. Silencing costs a missed ring;
     * blocking costs a call the user needed.
     */
    SCREEN("Screen suspicious calls", "Silence them, but keep them reachable in Recents"),

    /** Reject suspicious calls outright. The user has to ask for this. */
    BLOCK("Block suspicious calls", "Reject them the way an explicit block rule would"),
}

/** Which locally observable signals count, and what to do when they add up. */
data class RecoverySettings(
    val mode: RecoveryMode = RecoveryMode.NORMAL,
    val repeatedCallers: Boolean = true,
    val highFrequencyCallers: Boolean = true,
    val unknownCallers: Boolean = false,
    val rapidRepeat: Boolean = true,
) {
    val isActive: Boolean get() = mode != RecoveryMode.NORMAL

    companion object {
        /**
         * Off until the user turns it on.
         *
         * A behavioural filter that starts enabled would silence calls on someone's phone
         * without them ever having asked for it, and the first they would know is a call
         * they missed.
         */
        val DEFAULT = RecoverySettings()
    }
}

/** One reason a caller looked suspicious, in words that describe behaviour, not identity. */
data class SuspicionSignal(val label: String)

/** The verdict, with the reasons that produced it. */
data class SuspicionResult(
    val suspicious: Boolean,
    val signals: List<SuspicionSignal>,
) {
    val summary: String get() = signals.joinToString(" · ") { it.label }

    companion object {
        val NONE = SuspicionResult(suspicious = false, signals = emptyList())
    }
}

/**
 * Turns observed activity into a verdict, using only what this device saw.
 *
 * Pure and deterministic, so the thresholds can be argued with in a test rather than guessed
 * at on a device. Every signal is phrased as an observation — "repeated caller · 61 calls" —
 * because the app has no verified source that would justify calling anyone a fraudster.
 */
object RecoveryEvaluator {

    /** Calls in a day beyond which the caller is "high-frequency". */
    const val HIGH_FREQUENCY_PER_DAY = 5

    /** Separate days called within a week beyond which the caller is "repeated". */
    const val REPEATED_DAYS_PER_WEEK = 3

    fun evaluate(
        activity: CallerActivity,
        settings: RecoverySettings,
        isContact: Boolean,
        now: Long,
    ): SuspicionResult {
        if (!settings.isActive) return SuspicionResult.NONE
        // Someone in the address book is someone the user chose to know. Behaviour never
        // overrides that: a family member calling ten times is an emergency, not a spammer.
        if (isContact) return SuspicionResult.NONE

        val signals = buildList {
            if (settings.highFrequencyCallers) {
                val today = activity.countWithin(CallerActivity.DAY_MS, now)
                if (today >= HIGH_FREQUENCY_PER_DAY) {
                    add(SuspicionSignal("High-frequency caller · $today calls today"))
                }
            }
            if (settings.repeatedCallers) {
                val days = activity.distinctDaysWithin(CallerActivity.WEEK_MS, now)
                if (days >= REPEATED_DAYS_PER_WEEK) {
                    add(SuspicionSignal("Repeated caller · $days days this week"))
                }
            }
            if (settings.rapidRepeat && activity.hasRapidRepeat(now)) {
                add(SuspicionSignal("Rapid repeat calls"))
            }
            if (settings.unknownCallers && activity.totalAttempts > 0) {
                add(SuspicionSignal("Not in your contacts"))
            }
        }

        // "Unknown caller" on its own is not a reason to do anything: almost every first
        // call from anyone new would match it. It only adds colour to a verdict something
        // else already reached.
        val substantive = signals.any { it.label != UNKNOWN_LABEL }
        return SuspicionResult(suspicious = substantive, signals = signals)
    }

    private const val UNKNOWN_LABEL = "Not in your contacts"
}
