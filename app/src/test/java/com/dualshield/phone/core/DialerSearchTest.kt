package com.dualshield.phone.core

import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.core.search.DialerIndex
import com.dualshield.phone.core.search.MatchKind
import com.dualshield.phone.core.search.T9
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.ContactNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {

    @Test
    fun `the keypad mapping is the standard one`() {
        assertEquals(
            "22233344455566677778889999",
            T9.encode("abcdefghijklmnopqrstuvwxyz"),
        )
    }

    @Test
    fun `kishore encodes to the number from the spec`() {
        assertEquals("5474673", T9.encode("KISHORE"))
        assertEquals("5474673", T9.encode("kishore"))
    }

    @Test
    fun `words keep their boundaries so a surname stays findable`() {
        assertEquals(listOf("26263", "58627"), T9.encodeWords("Anand Kumar"))
    }

    @Test
    fun `a name in another script encodes without crashing`() {
        assertTrue(T9.encode("కిషోర్").isBlank())
    }
}

class DialerIndexTest {

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
        photoUri = null,
        starred = false,
    )

    private val index = DialerIndex.build(
        listOf(
            contact(1, "Kishore", "+91 96185 79123"),
            contact(2, "Anand Kumar", "+91 98765 43210"),
            contact(3, "Ravi", "9618500000"),
            contact(4, "Office", "04023456789"),
        ),
    )

    @Test
    fun `typing a name in t9 finds the contact`() {
        val results = index.search("5474")
        assertEquals("Kishore", results.first().contact.displayName)
        assertEquals(MatchKind.T9_NAME_PREFIX, results.first().matchKind)
    }

    @Test
    fun `t9 narrows progressively as more digits are typed`() {
        val names = { q: String -> index.search(q).map { it.contact.displayName } }
        assertTrue("K… should be in the broad result", names("5").contains("Kishore"))
        assertTrue(names("54").contains("Kishore"))
        assertEquals(listOf("Kishore"), names("5474"))
    }

    @Test
    fun `a surname is findable on its own`() {
        // KUMAR -> 58627
        assertEquals(
            listOf("Anand Kumar"),
            index.search("58627").map { it.contact.displayName },
        )
    }

    @Test
    fun `typing digits finds numbers beginning with them`() {
        val results = index.search("96185")
        assertTrue(results.all { it.matchKind == MatchKind.NUMBER_PREFIX })
        assertEquals(setOf("Kishore", "Ravi"), results.map { it.contact.displayName }.toSet())
    }

    @Test
    fun `a stored country code does not hide a nationally dialled prefix`() {
        // Kishore is saved as +91…, but nobody types the 91 first.
        val results = index.search("9618579")
        assertEquals("Kishore", results.first().contact.displayName)
    }

    @Test
    fun `an exact number outranks every other kind of match`() {
        val results = index.search("9618579123")
        assertEquals(MatchKind.NUMBER_EXACT, results.first().matchKind)
        assertEquals("Kishore", results.first().contact.displayName)
    }

    @Test
    fun `results are ordered by why they matched`() {
        val kinds = index.search("96185").map { it.matchKind }
        assertEquals(kinds.sortedBy { it.ordinal }, kinds)
    }

    @Test
    fun `a contact is never listed twice for one line`() {
        val duplicated = DialerIndex.build(
            listOf(contact(9, "Kishore", "+91 96185 79123", "09618579123", "9618579123")),
        )
        assertEquals(1, duplicated.search("96185").size)
    }

    @Test
    fun `a contact with two genuinely different lines offers both`() {
        val twoLines = DialerIndex.build(
            listOf(contact(9, "Kishore", "+919618579123", "+919618579456")),
        )
        assertEquals(2, twoLines.search("96185").size)
    }

    @Test
    fun `typed text searches names rather than numbers`() {
        val results = index.search("kish")
        assertEquals("Kishore", results.first().contact.displayName)
        assertEquals(MatchKind.TEXT_NAME_MATCH, results.first().matchKind)
    }

    @Test
    fun `an empty query returns nothing rather than everything`() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun `a query matching nobody returns nothing`() {
        assertTrue(index.search("77777777").isEmpty())
    }

    @Test
    fun `an empty address book searches without throwing`() {
        assertEquals(0, DialerIndex.EMPTY.size)
        assertTrue(DialerIndex.EMPTY.search("5474").isEmpty())
    }
}
