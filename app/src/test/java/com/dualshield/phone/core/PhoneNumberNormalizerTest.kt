package com.dualshield.phone.core

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `every spelling of an indian mobile normalizes the same way`() {
        val expected = "9876543210"
        listOf(
            "9876543210",
            "09876543210",
            "+919876543210",
            "+91 98765 43210",
            "0091-98765-43210",
            "(+91) 98765 43210",
            "91 9876543210",
        ).forEach { input ->
            val info = PhoneNumberNormalizer.normalize(input)
            assertEquals("failed for '$input'", expected, info.normalized)
            assertEquals(NumberKind.INDIAN_SUBSCRIBER, info.kind)
        }
    }

    @Test
    fun `premium rate leading zero survives normalization`() {
        // The 0900 rule is a prefix rule. Stripping this zero as a trunk prefix would
        // silently disable it, so the normalizer must leave it alone.
        val info = PhoneNumberNormalizer.normalize("0900123456")
        assertEquals("0900123456", info.normalized)
        assertTrue(info.normalized.startsWith("0900"))
    }

    @Test
    fun `trunk zero is only stripped from a real subscriber number`() {
        assertEquals("9876543210", PhoneNumberNormalizer.normalize("09876543210").normalized)
        // Not 0 + ten digits beginning 6-9, so nothing is stripped.
        assertEquals("0123456", PhoneNumberNormalizer.normalize("0123456").normalized)
    }

    @Test
    fun `indian service series are recognised`() {
        listOf("1401234567", "1600123456", "1601123456", "18002026161", "112").forEach {
            val info = PhoneNumberNormalizer.normalize(it)
            assertEquals("failed for '$it'", NumberKind.INDIAN_SERVICE_CODE, info.kind)
        }
    }

    @Test
    fun `international numbers keep their country code and are not indianised`() {
        val info = PhoneNumberNormalizer.normalize("+1 415 555 2671")
        assertEquals(NumberKind.INTERNATIONAL, info.kind)
        assertEquals("1", info.countryCode)
        assertEquals("14155552671", info.normalized)
        assertEquals("4155552671", info.nationalDigits)
    }

    @Test
    fun `withheld callers become private rather than malformed`() {
        listOf(null, "", "   ", "Unknown", "private", "-1", "Restricted").forEach { input ->
            val info = PhoneNumberNormalizer.normalize(input)
            assertEquals("failed for '$input'", NumberKind.PRIVATE, info.kind)
            assertTrue(info.isPrivate)
            assertFalse(info.hasDigits)
        }
    }

    @Test
    fun `emergency numbers are recognised through any formatting`() {
        listOf("112", "100", "101", "108", "+91 112").forEach { input ->
            val info = PhoneNumberNormalizer.normalize(input)
            assertTrue("failed for '$input'", PhoneNumberNormalizer.isEmergency(info))
        }
        assertFalse(
            PhoneNumberNormalizer.isEmergency(PhoneNumberNormalizer.normalize("9876543210")),
        )
    }

    @Test
    fun `match candidates cover both the full and national spelling`() {
        val info = PhoneNumberNormalizer.normalize("+14155552671")
        assertTrue(info.matchCandidates.contains("14155552671"))
        assertTrue(info.matchCandidates.contains("4155552671"))
    }
}
