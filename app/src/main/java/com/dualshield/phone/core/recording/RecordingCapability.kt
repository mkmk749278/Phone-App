package com.dualshield.phone.core.recording

/**
 * What this device will actually let the app capture from a call.
 *
 * The honest answer on most Android phones is [UNAVAILABLE], and the app says so rather than
 * offering a Record button that produces something other than a recording of the call.
 */
enum class RecordingCapability {
    /** Both sides. What people mean by "call recording". */
    FULL_TWO_WAY,

    /** Only the user's own voice reaches the file. */
    UPLINK_ONLY,

    /** Only the other party's voice reaches the file. */
    DOWNLINK_ONLY,

    /**
     * Only the microphone is available, which during a call picks up the user's side and
     * whatever leaks from the earpiece.
     *
     * Deliberately not treated as call recording. A file with one voice in it, offered under
     * a button labelled Record, is worse than no button: the user finds out what it actually
     * captured when they play back a conversation they needed.
     */
    MIC_ONLY,

    /** Nothing usable. */
    UNAVAILABLE,
    ;

    /** Whether a Record control should exist at all. */
    val supportsCallRecording: Boolean get() = this == FULL_TWO_WAY

    /** What the settings screen says about this device. */
    val explanation: String
        get() = when (this) {
            FULL_TWO_WAY ->
                "This device provides both sides of the call, so recordings capture the whole " +
                    "conversation."
            UPLINK_ONLY ->
                "This device only provides your side of the call, so a recording would be " +
                    "missing the other person. Call recording is therefore switched off."
            DOWNLINK_ONLY ->
                "This device only provides the other person's side of the call, so a " +
                    "recording would be missing you. Call recording is therefore switched off."
            MIC_ONLY ->
                "This device only allows microphone access during a call, which is not the " +
                    "same as recording the call: the other person would be missing or barely " +
                    "audible. Call recording is therefore switched off."
            UNAVAILABLE ->
                "Call recording isn't supported on this device. Since Android 10, capturing " +
                    "call audio is reserved for the system and pre-installed apps, so no " +
                    "ordinary app — including this one — can record a call unless the " +
                    "manufacturer allows it."
        }
}
