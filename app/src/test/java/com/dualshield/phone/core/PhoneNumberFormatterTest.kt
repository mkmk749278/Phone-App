package com.dualshield.phone.core

import com.dualshield.phone.core.number.PhoneNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The formatter is the app's promise that one number reads the same way on every screen, and
 * that four spellings of one line are recognised as one line.
 */
class PhoneNumberFormatterTest {

    @Test
    fun `every spelling of one indian mobile shares a canonical form`() {
        val spellings = listOf(
            "+91 87125 82492",
            "+918712582492",
            "08712582492",
            "8712582492",
            "+91-87125-82492",
            "0091 87125 82492",
        )
        val canonical = spellings.map { PhoneNumberFormatter.canonical(it) }.toSet()
        assertEquals(setOf("+918712582492"), canonical)
    }

    @Test
    fun `every spelling of one indian mobile shares a match key`() {
        val keys = listOf("+91 87125 82492", "+918712582492", "08712582492", "8712582492")
            .map { PhoneNumberFormatter.matchKey(it) }
            .toSet()
        assertEquals(setOf("8712582492"), keys)
    }

    @Test
    fun `indian mobiles display in the grouping people actually read`() {
        listOf("9618579123", "+919618579123", "09618579123").forEach {
            assertEquals("failed for '$it'", "+91 96185 79123", PhoneNumberFormatter.display(it))
        }
        assertEquals("96185 79123", PhoneNumberFormatter.displayNational("+919618579123"))
    }

    @Test
    fun `service codes are shown as dialled and not regrouped`() {
        assertEquals("1600400024", PhoneNumberFormatter.display("1600400024"))
        assertEquals("140123456", PhoneNumberFormatter.display("140123456"))
        assertEquals("112", PhoneNumberFormatter.display("112"))
    }

    @Test
    fun `foreign numbers are never given an invented grouping`() {
        assertEquals("+14155552671", PhoneNumberFormatter.display("+1 415 555 2671"))
        assertEquals("+14155552671", PhoneNumberFormatter.canonical("+1 415 555 2671"))
    }

    @Test
    fun `a withheld caller has no identity to group on`() {
        listOf(null, "", "Unknown", "Restricted").forEach {
            assertEquals("Private number", PhoneNumberFormatter.display(it))
            assertEquals("", PhoneNumberFormatter.canonical(it))
            assertTrue(
                "a withheld caller must not get a groupable key",
                PhoneNumberFormatter.matchKey(it).isEmpty(),
            )
        }
    }

    @Test
    fun `sender ids display as themselves rather than as a private number`() {
        assertEquals("AXISBK", PhoneNumberFormatter.display("AXISBK"))
        assertEquals("HDFCBK", PhoneNumberFormatter.display("VM-HDFCBK"))
        assertEquals("AXISBK", PhoneNumberFormatter.canonical("AD-AXISBK"))
    }

    @Test
    fun `two different service numbers never collapse onto one key`() {
        assertNotEquals(
            PhoneNumberFormatter.matchKey("1600400024"),
            PhoneNumberFormatter.matchKey("1600300542"),
        )
    }
}
