package com.dualshield.phone.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.dualshield.phone.core.recording.RecordingCapability

/**
 * Works out what this device will genuinely let the app capture from a call.
 *
 * The honest position, stated plainly because the product depends on it: since Android 10,
 * capturing the audio of a phone call requires `CAPTURE_AUDIO_OUTPUT`, a signature permission
 * held by the system and by apps that shipped with the phone. An installed app does not have
 * it and cannot ask for it. Where a manufacturer's own dialer records calls, it does so with
 * privileges no third-party app can hold.
 *
 * So this app does not record calls, and does not ask for the microphone either. An earlier
 * version of this class opened an `AudioRecord` against `VOICE_CALL` to probe what the audio
 * system would allow — lint pointed out the obvious: without `RECORD_AUDIO` in the manifest
 * that probe can never succeed, so it was code that looked like detection while always
 * returning the same answer. Dead certainty dressed as a runtime check is worse than an
 * honest constant.
 *
 * What is deliberately *not* here:
 *
 *  - Recording the microphone and presenting it as a recording of the call. During a call
 *    the microphone hears the user and, at best, a faint trace of the earpiece. Labelling
 *    that "call recording" means someone finds out it is half a conversation at the moment
 *    they most need the other half.
 *  - Guessing from the manufacturer name. A hard-coded "Xiaomi supports it" is wrong as soon
 *    as a carrier, region or firmware update changes, and it is wrong silently.
 *
 * If a genuine path ever exists — a documented OEM API, or this app shipping as a system
 * component — it belongs here, returning the capability it actually provides. Until then the
 * answer is [RecordingCapability.UNAVAILABLE] and the app says so in as many words.
 */
class CallRecordingCapabilityManager(private val context: Context) {

    /**
     * What this device allows.
     *
     * Cheap and deterministic today. Kept as a method rather than a constant so the call
     * sites are already shaped for the day a real capability exists.
     */
    fun capability(): RecordingCapability {
        if (!hasRecordAudioPermission()) {
            // The expected and intended branch. The permission is not in the manifest, so
            // this is not a device limitation being reported — it is this app declining to
            // ask for access that would not deliver the feature anyway.
            return RecordingCapability.UNAVAILABLE
        }

        // Only reachable if a future build declares RECORD_AUDIO deliberately. Even then,
        // microphone access alone is not call recording, and is reported as what it is.
        return RecordingCapability.MIC_ONLY
    }

    /** Re-runs the check. Behind the settings screen's explicit "Check again". */
    fun redetect(): RecordingCapability = capability()

    private fun hasRecordAudioPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
