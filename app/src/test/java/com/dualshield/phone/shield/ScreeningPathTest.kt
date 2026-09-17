package com.dualshield.phone.shield

import com.dualshield.phone.core.number.PhoneNumberFormatter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two properties of the screening path that cannot be checked by running the
 * engine on a JVM: that it does no blocking work, and that persistence happens after the
 * response rather than before it.
 *
 * These are read as source rather than executed because the behaviour they protect only
 * appears under a real `CallScreeningService`, where the failure mode is a call that rings
 * late or a block that silently became a ring. A structural check that fails in CI is worth
 * more than a device test nobody runs.
 */
class ScreeningPathTest {

    private val engineSource: String by lazy {
        sourceOf("app/src/main/java/com/dualshield/phone/shield/ShieldEngine.kt")
    }

    private val serviceSource: String by lazy {
        sourceOf("app/src/main/java/com/dualshield/phone/telecom/ShieldCallScreeningService.kt")
    }

    /**
     * Unit tests run with the module directory as the working directory in some setups and
     * the repository root in others, so both are tried before giving up.
     */
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

    /** Everything outside comments, so prose describing the rule cannot satisfy the rule. */
    private fun code(source: String): String =
        source.lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .filterNot { it.trimStart().startsWith("/*") }
            .joinToString("\n")

    @Test
    fun `the engine never blocks a thread`() {
        val code = code(engineSource)
        listOf("runBlocking", "withTimeoutOrNull", "Thread.sleep", ".await()", "latch")
            .forEach { forbidden ->
                assertFalse(
                    "ShieldEngine must not use '$forbidden': the screening callback is a " +
                        "real-time path and Android is waiting on it.",
                    code.contains(forbidden),
                )
            }
    }

    @Test
    fun `the screening service responds before it records anything`() {
        val code = code(serviceSource)
        val respondAt = code.indexOf("respondToCall(callDetails, blockResponse())")
        val recordAt = code.indexOf("recordBlockedCall")

        assertTrue("the block response must be sent", respondAt >= 0)
        assertTrue("the blocked call must still be recorded", recordAt >= 0)
        assertTrue(
            "recordBlockedCall must come after the response to Telecom, never before: a " +
                "storage failure must not be able to turn a block into a ring.",
            recordAt > respondAt,
        )
    }

    @Test
    fun `blocking a call never reaches for the screen`() {
        val code = code(serviceSource)
        listOf("WakeLock", "setTurnScreenOn", "setShowWhenLocked", "startActivity", "FullScreenIntent")
            .forEach { forbidden ->
                assertFalse(
                    "Blocking must leave a locked device dark; '$forbidden' asks for the " +
                        "display or launches UI.",
                    code.contains(forbidden),
                )
            }
    }

    @Test
    fun `the engine decides from memory rather than from storage`() {
        val code = code(engineSource)
        val screenAt = code.indexOf("fun screen(")
        val screenEnd = code.indexOf("fun recordBlockedCall(")
        assertTrue("screen() must exist", screenAt >= 0)
        assertTrue("recordBlockedCall() must follow it", screenEnd > screenAt)

        val decisionPath = code.substring(screenAt, screenEnd)
        listOf("vaultRepository", "buildSnapshotNow", "loadContacts", "displayNameFor")
            .forEach { forbidden ->
                assertFalse(
                    "screen() must not touch '$forbidden'; it is I/O on the critical path.",
                    decisionPath.contains(forbidden),
                )
            }
    }

    @Test
    fun `contact keys are the in-memory form the decision path can use`() {
        // The match key is what the engine compares against, so a contact saved in any
        // spelling has to reduce to the same key the incoming caller does.
        val saved = PhoneNumberFormatter.matchKey("+91 96185 79123")
        val incoming = PhoneNumberFormatter.matchKey("9618579123")
        assertEquals(saved, incoming)
        assertTrue(saved.isNotEmpty())
    }
}
