package com.dualshield.phone.data

import com.dualshield.phone.data.repository.AppSettings
import com.dualshield.phone.data.repository.CURRENT_SIM_POLICY_VERSION
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app is allowed to ask which line should be protected — and, more importantly,
 * when it is not.
 *
 * The prompt exists because the shipped SIM policy changed after release and existing
 * installs deliberately keep what they had. It is the only thing in the app that interrupts
 * a user on launch, so every condition that suppresses it matters more than the one that
 * shows it.
 */
class SimPolicyPromptTest {

    private fun settings(
        isLoaded: Boolean = true,
        onboardingComplete: Boolean = true,
        ack: Int = 0,
    ) = AppSettings(
        isLoaded = isLoaded,
        onboardingComplete = onboardingComplete,
        simPolicyAckVersion = ack,
    )

    @Test
    fun `an existing install is asked once`() {
        assertTrue(settings().needsSimPolicyReview)
        assertFalse(
            "answering it must be the end of it",
            settings(ack = CURRENT_SIM_POLICY_VERSION).needsSimPolicyReview,
        )
    }

    @Test
    fun `a new install is never asked`() {
        // Setup asks the same question properly, with room to name the lines. Asking again
        // immediately afterwards would read as the first answer not having registered.
        assertFalse(settings(onboardingComplete = false).needsSimPolicyReview)
    }

    @Test
    fun `nothing is asked before settings have loaded`() {
        // Defaults report ack = 0, so a prompt keyed off the pre-load state would flash on
        // every launch for every user, including ones who answered months ago.
        assertFalse(settings(isLoaded = false).needsSimPolicyReview)
        assertFalse(AppSettings().needsSimPolicyReview)
    }

    @Test
    fun `a user who skips releases is still asked only once`() {
        // The ack stores the policy version, not the app version, so a future bump is what
        // re-asks — not the passage of releases that changed nothing about the policy.
        assertFalse(settings(ack = CURRENT_SIM_POLICY_VERSION).needsSimPolicyReview)
        assertFalse(settings(ack = CURRENT_SIM_POLICY_VERSION + 5).needsSimPolicyReview)
    }

    @Test
    fun `finishing onboarding records the policy in the same write`() {
        // Two separate writes would leave a window where a crash in between produces a
        // brand-new install that is then asked the upgrade question. There is no way to
        // exercise DataStore from a plain unit test, so the atomicity is asserted where it
        // is actually expressed.
        val source = listOf(
            File("src/main/java/com/dualshield/phone/data/repository/SettingsRepository.kt"),
            File("app/src/main/java/com/dualshield/phone/data/repository/SettingsRepository.kt"),
        ).firstOrNull { it.exists() } ?: error("SettingsRepository source not found")

        val body = source.readText()
            .substringAfter("suspend fun setOnboardingComplete")
            .substringBefore("suspend fun acknowledgeSimPolicy")

        assertTrue(
            "setOnboardingComplete must write the SIM policy acknowledgement itself",
            body.contains("KEY_SIM_POLICY_ACK"),
        )
        assertFalse(
            "and must not do it in a second edit block",
            body.substringAfter("KEY_SIM_POLICY_ACK").contains("edit {"),
        )
    }
}
