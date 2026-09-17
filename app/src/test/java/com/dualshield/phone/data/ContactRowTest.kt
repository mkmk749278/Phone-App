package com.dualshield.phone.data

import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.ContactNumber
import com.dualshield.phone.ui.contacts.contactSubtitle
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A contact row shows a person once.
 *
 * §83 of the handoff reports duplicate-looking rows in Contacts:
 *
 * ```
 * +91 93986 79480
 * +91 93986 79480
 * ```
 *
 * They are not duplicates. A contact saved without a name falls back to showing its number
 * as its name — right for a list of people — and the row then printed the number underneath
 * as well. One contact, the same digits twice, one line above the other.
 */
class ContactRowTest {

    private fun number(raw: String, type: String = "Mobile") = ContactNumber(
        raw = raw,
        display = raw,
        matchKey = raw.filter { it.isDigit() }.takeLast(10),
        typeLabel = type,
    )

    private fun contact(
        name: String,
        hasName: Boolean,
        numbers: List<ContactNumber>,
    ) = Contact(
        id = 1,
        lookupKey = null,
        displayName = name,
        phoneNumbers = numbers,
        photoUri = null,
        starred = false,
        hasName = hasName,
    )

    @Test
    fun `a nameless contact does not print its number twice`() {
        val nameless = contact("+91 93986 79480", hasName = false, listOf(number("+919398679480")))
        val subtitle = contactSubtitle(nameless)
        assertNotEquals(
            "the second line must not repeat the first",
            nameless.displayName,
            subtitle,
        )
        assertFalse(
            "and must not contain the number at all when the title already is it",
            subtitle.orEmpty().filter { it.isDigit() }.contains("9398679480"),
        )
    }

    @Test
    fun `a named contact still shows its number underneath`() {
        val named = contact("Kishore", hasName = true, listOf(number("+919618579123")))
        // Compared on digits: the subtitle is formatted for reading ("+91 96185 79123"),
        // so the raw digits are not a contiguous substring of it.
        val subtitle = contactSubtitle(named).orEmpty()
        assertTrue(
            "the number is what a name most wants under it, but got '$subtitle'",
            subtitle.filter { it.isDigit() }.contains("9618579123"),
        )
    }

    @Test
    fun `a contact with several numbers says so`() {
        // A row that silently picks one of three numbers sends someone to the wrong one.
        val many = contact(
            "Kishore",
            hasName = true,
            listOf(number("+919618579123"), number("+918035123456"), number("+911234567890")),
        )
        assertTrue(contactSubtitle(many).orEmpty().contains("+2 more"))
    }

    @Test
    fun `the row is fed by the model rather than by guessing at the name`() {
        // hasName comes from the provider column being empty, not from comparing the
        // display name against the number — a contact genuinely named after their own
        // number would then be treated as nameless.
        val source = listOf(
            File("src/main/java/com/dualshield/phone/data/system/ContactsRepository.kt"),
            File("app/src/main/java/com/dualshield/phone/data/system/ContactsRepository.kt"),
        ).first { it.exists() }.readText()

        assertTrue(
            "hasName must be read from the contact's name column",
            source.contains("hasName = !cursor.getString(nameIdx)"),
        )
    }

}
