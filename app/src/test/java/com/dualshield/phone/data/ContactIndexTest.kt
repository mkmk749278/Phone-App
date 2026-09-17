package com.dualshield.phone.data

import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.ContactIndex
import com.dualshield.phone.data.system.ContactNumber
import com.dualshield.phone.data.system.ContactRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the identity half of the duplicate-contact problem: whatever spelling a screen
 * happens to be holding, it must reach the same person, with the same photo.
 */
class ContactIndexTest {

    private fun number(raw: String) = ContactNumber(
        raw = raw,
        display = PhoneNumberFormatter.display(raw),
        matchKey = PhoneNumberFormatter.matchKey(raw),
        typeLabel = "Mobile",
    )

    private fun contact(id: Long, name: String, vararg numbers: String) = Contact(
        id = id,
        lookupKey = "lookup-$id",
        displayName = name,
        phoneNumbers = numbers.map(::number),
        photoUri = "content://contacts/photo/$id",
        starred = false,
        hasName = true,
    )

    private fun indexOf(vararg contacts: Contact): ContactIndex {
        val byKey = HashMap<String, ContactRef>()
        for (c in contacts) {
            val ref = ContactRef(c.id, c.lookupKey, c.displayName, c.photoUri)
            c.phoneNumbers.forEach { n ->
                if (n.matchKey.isNotEmpty()) byKey.putIfAbsent(n.matchKey, ref)
            }
        }
        return ContactIndex(byKey)
    }

    @Test
    fun `any spelling of a saved number finds the same person and photo`() {
        val index = indexOf(contact(1, "Kishore", "+91 96185 79123"))
        listOf("9618579123", "+919618579123", "09618579123", "+91 96185 79123").forEach {
            assertEquals("failed for '$it'", "Kishore", index.nameForNumber(it))
            assertEquals("content://contacts/photo/1", index.photoUriFor(it))
        }
    }

    @Test
    fun `a contact with several lines is reachable by all of them`() {
        val index = indexOf(contact(2, "Amma", "+919876543210", "04023456789"))
        assertEquals("Amma", index.nameForNumber("9876543210"))
        assertEquals("Amma", index.nameForNumber("04023456789"))
    }

    @Test
    fun `an unsaved number resolves to nobody`() {
        val index = indexOf(contact(1, "Kishore", "+919618579123"))
        assertNull(index.nameForNumber("+918069378433"))
    }

    @Test
    fun `a withheld caller is never matched to a contact`() {
        val index = indexOf(contact(1, "Kishore", "+919618579123"))
        listOf(null, "", "Unknown", "Restricted").forEach {
            assertNull("'$it' must not resolve to a contact", index.nameForNumber(it))
        }
    }

    @Test
    fun `an empty index answers nothing rather than throwing`() {
        assertNull(ContactIndex.EMPTY.nameForNumber("+919618579123"))
        assertEquals(0, ContactIndex.EMPTY.size)
    }
}
