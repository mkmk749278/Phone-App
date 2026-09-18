package com.dualshield.phone.data

import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.ContactNumber
import com.dualshield.phone.data.system.collapseNamelessDuplicates
import com.dualshield.phone.ui.contacts.contactSubtitle
import org.junit.Assert.assertEquals
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
        numbers: List<ContactNumber>,
    ) = Contact(
        id = 1,
        lookupKey = null,
        displayName = name,
        phoneNumbers = numbers,
        photoUri = null,
        starred = false,
    )

    @Test
    fun `a nameless contact does not print its number twice`() {
        val nameless = contact("+91 93986 79480", listOf(number("+919398679480")))
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
        val named = contact("Kishore", listOf(number("+919618579123")))
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
            listOf(number("+919618579123"), number("+918035123456"), number("+911234567890")),
        )
        assertTrue(contactSubtitle(many).orEmpty().contains("+2 more"))
    }

    @Test
    fun `a name that is really the number is not a name`() {
        // The defect this replaces a wrong test for. ContactsContract does not leave the
        // name column empty for a contact saved without a name — it fills it in with the
        // number. Asking the provider "was the name column empty?" therefore always
        // answered no, hasName was always true, and the row printed the number twice.
        listOf(
            "+919010791018" to "+919010791018",
            "06303114934" to "+916303114934",
            "70138 20364" to "+917013820364",
            "+91 93986 79480" to "+919398679480",
        ).forEach { (name, raw) ->
            val c = contact(name, listOf(number(raw)))
            assertFalse("'$name' is this contact's own number, not a name", c.hasName)
            assertNotEquals(
                "so the row must not repeat it underneath",
                c.displayName,
                contactSubtitle(c),
            )
        }
    }

    @Test
    fun `anything with a letter in it is a name`() {
        listOf("Kishore", "A.RAVI PC-830", "Mum", "O2", "X").forEach { name ->
            assertTrue("'$name' is a name", contact(name, listOf(number("+919618579123"))).hasName)
        }
    }

    @Test
    fun `digits that are not this contact's number are left as a name`() {
        // Only the contact's *own* number counts. A contact someone deliberately labelled
        // with a different number is odd, but it is still something they typed.
        val c = contact("1234567890", listOf(number("+919618579123")))
        assertTrue(c.hasName)
    }

    @Test
    fun `the check survives a different spelling of the same number`() {
        // The provider's fallback name and the stored number are rarely formatted the same
        // way — the recording showed "+919010791018" above "+91 90107 91018" — so the
        // comparison has to be on the canonical key, not on the text.
        val c = contact("+91 90107 91018", listOf(number("+919010791018")))
        assertFalse(c.hasName)
    }

    @Test
    fun `two unnamed records of one number become one row`() {
        // The recording showed "+91 93986 79480" twice and "+91 99125 23460" twice, as
        // separate rows. The address book really does hold several records for one number —
        // a SIM copy, a Google copy, one left behind by a messaging app — and with no name
        // on any of them there is nothing on screen to tell them apart.
        val a = contact("+91 93986 79480", listOf(number("+919398679480")))
        val b = contact("+919398679480", listOf(number("+919398679480")))
        val collapsed = collapseNamelessDuplicates(listOf(a, b))
        assertEquals(1, collapsed.size)
    }

    @Test
    fun `a photo on either copy survives the collapse`() {
        // One of the duplicate pairs in the recording had a photo and the other did not.
        // The photo is the only thing that distinguished them, so it is what to keep.
        val plain = contact("+919398679480", listOf(number("+919398679480")))
        val withPhoto = plain.copy(id = 2, photoUri = "content://photo/2")
        listOf(listOf(plain, withPhoto), listOf(withPhoto, plain)).forEach { input ->
            val collapsed = collapseNamelessDuplicates(input)
            assertEquals(1, collapsed.size)
            assertEquals("content://photo/2", collapsed.single().photoUri)
        }
    }

    @Test
    fun `named contacts sharing a number are never merged`() {
        // "Mum" and "Mum work" on one number are two things the user deliberately wrote
        // down. Collapsing them loses a distinction only they can make.
        val mum = contact("Mum", listOf(number("+919398679480")))
        val work = contact("Mum work", listOf(number("+919398679480"))).copy(id = 2)
        assertEquals(2, collapseNamelessDuplicates(listOf(mum, work)).size)
    }

    @Test
    fun `an unnamed contact is never merged into a named one`() {
        val named = contact("Kishore", listOf(number("+919398679480")))
        val unnamed = contact("+919398679480", listOf(number("+919398679480"))).copy(id = 2)
        assertEquals(2, collapseNamelessDuplicates(listOf(named, unnamed)).size)
    }

    @Test
    fun `contacts with no usable number are left alone`() {
        // Two unparseable entries are not evidence of the same person.
        val a = contact("", listOf(ContactNumber("", "", "", "Mobile")))
        val b = a.copy(id = 2)
        assertEquals(2, collapseNamelessDuplicates(listOf(a, b)).size)
    }
}
