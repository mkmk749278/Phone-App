package com.dualshield.phone.core

import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.shield.PauseDuration
import com.dualshield.phone.core.shield.ShieldPause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pause is the feature that lets the user open a deliberate call window once or twice a
 * week, so the two things that must not go wrong are: it ends when it said it would, and it
 * never quietly covers a line the user did not pause.
 */
class ShieldPauseTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60_000L

    @Test
    fun `a timed pause holds until its expiry and not a moment past it`() {
        val pause = ShieldPause.timed(SimScope.BOTH, hour, now)

        assertTrue("active at the moment it starts", pause.isActiveAt(now))
        assertTrue("active a second before expiry", pause.isActiveAt(now + hour - 1_000))
        assertFalse("over exactly at expiry", pause.isActiveAt(now + hour))
        assertFalse("over after expiry", pause.isActiveAt(now + hour + 1))
    }

    @Test
    fun `an indefinite pause never lapses on its own`() {
        val pause = ShieldPause.indefinite(SimScope.BOTH)
        assertTrue(pause.isIndefinite)
        assertTrue(pause.isActiveAt(now))
        assertTrue("still on a week later", pause.isActiveAt(now + 7 * 24 * hour))
    }

    @Test
    fun `pausing one SIM leaves the other protected`() {
        val sim1 = ShieldPause.timed(SimScope.SIM1, hour, now)
        assertTrue("SIM 1 is paused", sim1.suspends(slotIndex = 0, now = now))
        assertFalse("SIM 2 must stay protected", sim1.suspends(slotIndex = 1, now = now))

        val sim2 = ShieldPause.timed(SimScope.SIM2, hour, now)
        assertFalse("SIM 1 must stay protected", sim2.suspends(slotIndex = 0, now = now))
        assertTrue("SIM 2 is paused", sim2.suspends(slotIndex = 1, now = now))
    }

    @Test
    fun `a both-SIM pause covers every line including an unresolved one`() {
        val pause = ShieldPause.timed(SimScope.BOTH, hour, now)
        assertTrue(pause.suspends(0, now))
        assertTrue(pause.suspends(1, now))
        assertTrue("an unidentified SIM is covered by a pause on everything", pause.suspends(null, now))
    }

    @Test
    fun `a single-SIM pause does not cover a call whose SIM is unknown`() {
        // Not knowing which line a call arrived on must not be read as "the paused one":
        // that would silently stop filtering calls the user still expects to be filtered.
        val pause = ShieldPause.timed(SimScope.SIM1, hour, now)
        assertFalse(pause.suspends(slotIndex = null, now = now))
    }

    @Test
    fun `an expired pause suspends nothing even on the SIM it covered`() {
        val pause = ShieldPause.timed(SimScope.BOTH, hour, now)
        assertFalse(pause.suspends(0, now + hour))
        assertFalse(pause.suspends(1, now + hour + 5_000))
    }

    @Test
    fun `the offered durations produce the pause they describe`() {
        assertEquals(now + hour, PauseDuration.ONE_HOUR.toPause(SimScope.BOTH, now).expiresAtMillis)
        assertEquals(
            now + 4 * hour,
            PauseDuration.FOUR_HOURS.toPause(SimScope.BOTH, now).expiresAtMillis,
        )
        assertNull(
            "'until I turn it back on' must not carry an expiry",
            PauseDuration.UNTIL_RESUMED.toPause(SimScope.BOTH, now).expiresAtMillis,
        )
    }

    @Test
    fun `a pause set before a restart still ends at the time it promised`() {
        // Wall-clock expiry, not elapsed time: the process dying and coming back must not
        // extend a pause, and must not cut it short either.
        val pause = ShieldPause.timed(SimScope.BOTH, hour, now)
        val afterRestart = ShieldPause(pause.scope, pause.expiresAtMillis)

        assertTrue(afterRestart.isActiveAt(now + hour / 2))
        assertFalse(afterRestart.isActiveAt(now + hour))
    }
}
