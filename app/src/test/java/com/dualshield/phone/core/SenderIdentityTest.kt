package com.dualshield.phone.core

import com.dualshield.phone.core.model.ContactLookup
import com.dualshield.phone.core.model.SenderIdentity
import com.dualshield.phone.core.model.SenderType
import com.dualshield.phone.core.number.PhoneNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reported bug this guards against: every bank, operator and service the user hears from
 * was being shown as "Private number", which is a label that should only ever appear when the
 * network genuinely withheld a caller's identity.
 */
class SenderIdentityTest {

    private val contacts = object : ContactLookup {
        private val byKey = mapOf(
            PhoneNumberFormatter.matchKey("+919618579123") to "Kishore",
        )

        override fun nameFor(key: String): String? = byKey[key]

        override fun photoFor(key: String): String? =
            byKey[key]?.let { "content://contacts/photo/1" }
    }

    private fun resolve(address: String?) = SenderIdentity.resolve(address, contacts)

    @Test
    fun `a bank sender id keeps its own name`() {
        val sender = resolve("AXISBK")
        assertEquals(SenderType.BUSINESS_SENDER, sender.type)
        assertEquals("AXISBK", sender.displayName)
    }

    @Test
    fun `a routed dlt sender id drops the operator prefix`() {
        listOf("VM-HDFCBK", "AD-HDFCBK", "TX-HDFCBK").forEach {
            val sender = resolve(it)
            assertEquals("failed for '$it'", SenderType.BUSINESS_SENDER, sender.type)
            assertEquals("failed for '$it'", "HDFCBK", sender.displayName)
        }
    }

    @Test
    fun `only a genuinely withheld caller is called a private number`() {
        listOf(null, "", "Unknown", "Restricted").forEach {
            val sender = resolve(it)
            assertEquals("failed for '$it'", SenderType.WITHHELD, sender.type)
            assertEquals("failed for '$it'", "Private number", sender.displayName)
        }
        // And nothing else is.
        listOf("AXISBK", "1600400024", "+919876543210", "121").forEach {
            assertEquals(
                "'$it' must not be shown as a private number",
                false,
                resolve(it).displayName == "Private number",
            )
        }
    }

    @Test
    fun `a saved contact wins over the formatted number`() {
        val sender = resolve("+91 96185 79123")
        assertEquals(SenderType.KNOWN_CONTACT, sender.type)
        assertEquals("Kishore", sender.displayName)
        assertEquals("+91 96185 79123", sender.displayNumber)
        assertEquals("content://contacts/photo/1", sender.photoUri)
    }

    @Test
    fun `short codes are distinguished from ordinary numbers`() {
        assertEquals(SenderType.SHORT_CODE, resolve("121").type)
        assertEquals(SenderType.SHORT_CODE, resolve("199").type)
        assertEquals(SenderType.PHONE_NUMBER, resolve("1600400024").type)
        assertEquals(SenderType.PHONE_NUMBER, resolve("+919876543210").type)
    }

    @Test
    fun `a withheld caller carries no key that could group it with another`() {
        assertEquals("", resolve("Unknown").matchKey)
        assertNull(resolve("Unknown").displayNumber)
    }
}
