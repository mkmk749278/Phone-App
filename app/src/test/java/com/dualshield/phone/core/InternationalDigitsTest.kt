package com.dualshield.phone.core

import com.dualshield.phone.core.number.PhoneNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the number that goes into a `wa.me` link.
 *
 * WhatsApp documents the format as the full international number with no `+`, spaces,
 * brackets or dashes. Anything that cannot be put in that form must return null, so the
 * action is hidden rather than offered and then failing in front of the user.
 */
class InternationalDigitsTest {

    @Test
    fun `indian mobiles get a country code however they were stored`() {
        listOf("9618579123", "+919618579123", "09618579123", "+91 96185 79123").forEach {
            assertEquals("failed for '$it'", "919618579123", PhoneNumberFormatter.internationalDigits(it))
        }
    }

    @Test
    fun `the link form carries no punctuation at all`() {
        val digits = PhoneNumberFormatter.internationalDigits("+91 (96185) 79-123")
        assertEquals("919618579123", digits)
        assertEquals(
            "the link number must be digits only",
            digits,
            digits?.filter { it.isDigit() },
        )
    }

    @Test
    fun `foreign numbers keep their own country code`() {
        assertEquals("14155552671", PhoneNumberFormatter.internationalDigits("+1 415 555 2671"))
    }

    @Test
    fun `service codes and short codes are not addressable`() {
        listOf("1600400024", "140123456", "112", "121", "1800123456").forEach {
            assertNull("'$it' must not produce a link", PhoneNumberFormatter.internationalDigits(it))
        }
    }

    @Test
    fun `sender ids and withheld callers are not addressable`() {
        listOf("AXISBK", "VM-HDFCBK", "Unknown", "", null).forEach {
            assertNull("'$it' must not produce a link", PhoneNumberFormatter.internationalDigits(it))
        }
    }
}
