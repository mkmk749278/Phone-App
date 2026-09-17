package com.dualshield.phone.core

import com.dualshield.phone.core.recording.RecordingCapability
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Call recording is the feature most likely to be quietly faked, because the fake is easy
 * and looks like the real thing until someone plays back a conversation they needed.
 *
 * These tests exist to make the honest behaviour hard to undo by accident.
 */
class RecordingCapabilityTest {

    private fun sourceOf(repoRelativePath: String): String {
        val candidates = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File(repoRelativePath.removePrefix("app/")),
        )
        val found = candidates.firstOrNull { it.exists() }
        requireNotNull(found) { "Could not locate $repoRelativePath from ${File(".").absolutePath}" }
        return found.readText()
    }

    @Test
    fun `only genuine two-way capture counts as call recording`() {
        assertTrue(RecordingCapability.FULL_TWO_WAY.supportsCallRecording)

        listOf(
            RecordingCapability.UPLINK_ONLY,
            RecordingCapability.DOWNLINK_ONLY,
            RecordingCapability.MIC_ONLY,
            RecordingCapability.UNAVAILABLE,
        ).forEach {
            assertFalse(
                "$it must not be offered as call recording: it is not a recording of the call",
                it.supportsCallRecording,
            )
        }
    }

    @Test
    fun `microphone-only is never presented as recording the call`() {
        // The single most important line in the feature. A mic recording during a call
        // captures one side; offering it as "call recording" is the failure this whole
        // capability layer exists to prevent.
        assertFalse(RecordingCapability.MIC_ONLY.supportsCallRecording)
        assertTrue(
            "the explanation must say what is actually missing",
            RecordingCapability.MIC_ONLY.explanation.contains("not the same as recording the call"),
        )
    }

    @Test
    fun `every capability explains itself to the user`() {
        RecordingCapability.entries.forEach {
            assertTrue(
                "$it needs an explanation a user can act on",
                it.explanation.length > 40,
            )
        }
    }

    @Test
    fun `the app does not hold microphone permission`() {
        // Detection would be more precise with RECORD_AUDIO, but the permission would not
        // make recording work, and an app that asks for the microphone and never records is
        // asking for trust it does not need.
        val manifest = sourceOf("app/src/main/AndroidManifest.xml")
        assertFalse(
            "RECORD_AUDIO must not be declared unless recording is genuinely implemented",
            manifest.contains("android.permission.RECORD_AUDIO"),
        )
        assertFalse(
            "CAPTURE_AUDIO_OUTPUT is a system permission; declaring it would be theatre",
            manifest.contains("CAPTURE_AUDIO_OUTPUT"),
        )
    }

    @Test
    fun `capability is detected rather than guessed from the manufacturer`() {
        val manager = sourceOf(
            "app/src/main/java/com/dualshield/phone/recording/CallRecordingCapabilityManager.kt",
        )
        val code = manager.lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")

        listOf("Build.MANUFACTURER", "Build.BRAND", "Build.MODEL", "xiaomi", "samsung")
            .forEach { forbidden ->
                assertFalse(
                    "Capability must come from asking the audio system, not from '$forbidden': " +
                        "a name-based guess is wrong the moment a firmware update changes it, " +
                        "and wrong silently.",
                    code.contains(forbidden, ignoreCase = true),
                )
            }
    }

    @Test
    fun `the privacy promise still holds`() {
        val screen = sourceOf(
            "app/src/main/java/com/dualshield/phone/ui/settings/CallRecordingScreen.kt",
        )
        assertTrue(
            "the screen must state that recordings stay on the device",
            screen.contains("kept on this device only"),
        )
    }
}
