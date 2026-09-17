package com.dualshield.phone.core

import com.dualshield.phone.core.shield.CallerActivity
import com.dualshield.phone.core.shield.RecoveryEvaluator
import com.dualshield.phone.core.shield.RecoveryMode
import com.dualshield.phone.core.shield.RecoverySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerActivityTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val day = CallerActivity.DAY_MS

    private fun activity(vararg offsetsFromNow: Long) =
        CallerActivity("9618579123", offsetsFromNow.map { now - it }.sorted())

    @Test
    fun `counting is limited to the window asked for`() {
        val activity = activity(minute, 2 * day, 10 * day)
        assertEquals(1, activity.countWithin(CallerActivity.DAY_MS, now))
        assertEquals(2, activity.countWithin(CallerActivity.WEEK_MS, now))
        assertEquals(3, activity.countWithin(CallerActivity.MONTH_MS, now))
    }

    @Test
    fun `many calls in one afternoon are one day, not many`() {
        val burst = activity(minute, 2 * minute, 3 * minute, 4 * minute)
        assertEquals(
            "a burst within one day must not read as persistence across days",
            1,
            burst.distinctDaysWithin(CallerActivity.WEEK_MS, now),
        )
    }

    @Test
    fun `calling across separate days is counted as separate days`() {
        val spread = activity(minute, day + minute, 2 * day + minute)
        assertEquals(3, spread.distinctDaysWithin(CallerActivity.WEEK_MS, now))
    }

    @Test
    fun `three calls close together is a rapid repeat and two is not`() {
        assertFalse(activity(minute, 2 * minute).hasRapidRepeat(now))
        assertTrue(activity(minute, 2 * minute, 3 * minute).hasRapidRepeat(now))
        assertFalse(
            "spread over hours is not a rapid repeat",
            activity(minute, 120 * minute, 240 * minute).hasRapidRepeat(now),
        )
    }

    @Test
    fun `history older than the retention window is dropped on record`() {
        val old = CallerActivity("9618579123", listOf(now - 40 * day))
        val updated = old.recording(now)
        assertEquals("only the new attempt survives", 1, updated.totalAttempts)
    }

    @Test
    fun `a caller cannot grow history without bound`() {
        var activity = CallerActivity("9618579123")
        repeat(CallerActivity.MAX_ATTEMPTS + 50) { i -> activity = activity.recording(now + i) }
        assertEquals(CallerActivity.MAX_ATTEMPTS, activity.totalAttempts)
    }
}

class RecoveryEvaluatorTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val day = CallerActivity.DAY_MS

    private fun activity(vararg offsetsFromNow: Long) =
        CallerActivity("9618579123", offsetsFromNow.map { now - it }.sorted())

    private val screening = RecoverySettings(mode = RecoveryMode.SCREEN)

    @Test
    fun `protection off means no verdict at all`() {
        val heavy = activity(minute, 2 * minute, 3 * minute, 4 * minute, 5 * minute, 6 * minute)
        val result = RecoveryEvaluator.evaluate(
            heavy,
            RecoverySettings.DEFAULT,
            isContact = false,
            now = now,
        )
        assertFalse(result.suspicious)
        assertTrue(result.signals.isEmpty())
    }

    @Test
    fun `protection is off until the user turns it on`() {
        assertEquals(RecoveryMode.NORMAL, RecoverySettings.DEFAULT.mode)
        assertFalse(RecoverySettings.DEFAULT.isActive)
    }

    @Test
    fun `a saved contact is never suspicious however they call`() {
        val relentless = activity(
            minute, 2 * minute, 3 * minute, 4 * minute, 5 * minute,
            6 * minute, 7 * minute, 8 * minute,
        )
        val result = RecoveryEvaluator.evaluate(relentless, screening, isContact = true, now = now)
        assertFalse(
            "someone in the address book calling many times is an emergency, not a spammer",
            result.suspicious,
        )
    }

    @Test
    fun `high frequency in a single day is a signal`() {
        val busy = activity(minute, 2 * minute, 30 * minute, 60 * minute, 120 * minute)
        val result = RecoveryEvaluator.evaluate(busy, screening, isContact = false, now = now)
        assertTrue(result.suspicious)
        assertTrue(result.summary.contains("High-frequency"))
    }

    @Test
    fun `calling on several separate days is a signal`() {
        val persistent = activity(minute, day + minute, 2 * day + minute)
        val result = RecoveryEvaluator.evaluate(persistent, screening, isContact = false, now = now)
        assertTrue(result.suspicious)
        assertTrue(result.summary.contains("Repeated caller"))
    }

    @Test
    fun `one call from someone new is not suspicious`() {
        val first = activity(minute)
        val result = RecoveryEvaluator.evaluate(first, screening, isContact = false, now = now)
        assertFalse("a first call from anyone new must not be caught", result.suspicious)
    }

    @Test
    fun `being unknown is never on its own a reason to act`() {
        val unknownOnly = RecoverySettings(
            mode = RecoveryMode.SCREEN,
            repeatedCallers = false,
            highFrequencyCallers = false,
            unknownCallers = true,
            rapidRepeat = false,
        )
        val result = RecoveryEvaluator.evaluate(
            activity(minute),
            unknownOnly,
            isContact = false,
            now = now,
        )
        assertFalse(
            "almost every first call from anyone would match 'unknown'; it cannot stand alone",
            result.suspicious,
        )
    }

    @Test
    fun `a disabled signal does not contribute`() {
        val busy = activity(minute, 2 * minute, 30 * minute, 60 * minute, 120 * minute)
        val withoutFrequency = screening.copy(
            highFrequencyCallers = false,
            repeatedCallers = false,
            rapidRepeat = false,
        )
        val result = RecoveryEvaluator.evaluate(
            busy,
            withoutFrequency,
            isContact = false,
            now = now,
        )
        assertFalse(result.suspicious)
    }

    @Test
    fun `signals describe behaviour and never claim an identity`() {
        val busy = activity(minute, 2 * minute, 30 * minute, 60 * minute, 120 * minute)
        val result = RecoveryEvaluator.evaluate(busy, screening, isContact = false, now = now)
        val forbidden = listOf("fraud", "scam", "recovery agency", "illegal", "criminal")
        result.signals.forEach { signal ->
            forbidden.forEach { word ->
                assertFalse(
                    "signal '${signal.label}' must not assert an identity the app cannot verify",
                    signal.label.lowercase().contains(word),
                )
            }
        }
    }
}
