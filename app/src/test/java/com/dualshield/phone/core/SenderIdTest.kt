package com.dualshield.phone.core

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.model.SenderIdentity
import com.dualshield.phone.core.model.SenderType
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which part of an Indian SMS header is the sender.
 *
 * Found in a screen recording, not in a test: every bank, operator and government message in
 * the Messages list was titled with a single letter — `S`, `T`, `P`, `G`. The list looked
 * like a column of initials.
 *
 * The cause is worth keeping in front of whoever changes this next. Headers used to be two
 * parts, `VM-AXISBK`, so the code took the last token and was right. TRAI's current format
 * appends a content category as a final single letter — `AX-SBIINB-S` for service, `-T`
 * transactional, `-P` promotional, `-G` government — and taking the last token then returns
 * the category instead of the sender. The old rule did not break loudly; it kept working on
 * the old form and quietly destroyed the new one.
 */
class SenderIdTest {

    private fun senderIdOf(raw: String): String? =
        PhoneNumberNormalizer.normalize(raw).senderId

    @Test
    fun `the content category is not the sender`() {
        // The exact headers from the recording's Messages list.
        mapOf(
            "AX-SBIINB-S" to "SBIINB",
            "AD-IMDGOV-G" to "IMDGOV",
            "TX-KOTAKB-T" to "KOTAKB",
            "BP-SPOTFY-P" to "SPOTFY",
        ).forEach { (header, expected) ->
            assertEquals("'$header' is from $expected", expected, senderIdOf(header))
        }
    }

    @Test
    fun `no sender is ever reduced to a single letter`() {
        // The symptom, stated as the rule. A one-letter title tells the user nothing and is
        // indistinguishable from a bug, which is what it was.
        listOf(
            "AX-SBIINB-S", "AD-IMDGOV-G", "TX-KOTAKB-T", "BP-SPOTFY-P",
            "VM-AXISBK", "AD-HDFCBK", "JIO", "AIRTEL", "DM-AMAZON-S",
        ).forEach { header ->
            val id = senderIdOf(header)
            assertTrue(
                "'$header' resolved to '$id', which is too short to mean anything",
                (id?.length ?: 0) > 1,
            )
        }
    }

    @Test
    fun `the older two-part form still resolves the same way`() {
        // The rule that was right before is still right; this is a widening, not a swap.
        assertEquals("AXISBK", senderIdOf("VM-AXISBK"))
        assertEquals("HDFCBK", senderIdOf("AD-HDFCBK"))
        assertEquals("ICICIB", senderIdOf("VK-ICICIB"))
    }

    @Test
    fun `a bare sender with no route prefix is left alone`() {
        assertEquals("JIO", senderIdOf("JIO"))
        assertEquals("AIRTEL", senderIdOf("AIRTEL"))
        assertEquals("AXISBK", senderIdOf("AXISBK"))
    }

    @Test
    fun `a numeric sender is not treated as a sender ID at all`() {
        // 1600318342 showed correctly in the recording and must keep doing so: it is a
        // number, it can be called back, and it takes the numeric path.
        val info = PhoneNumberNormalizer.normalize("1600318342")
        assertEquals(null, info.senderId)
        assertTrue(info.kind != NumberKind.ALPHANUMERIC_SENDER)
    }

    @Test
    fun `the resolved identity shows the sender, not the category`() {
        // End to end, through the type the Messages list actually renders.
        val identity = SenderIdentity.resolve("AX-SBIINB-S")
        assertEquals(SenderType.BUSINESS_SENDER, identity.type)
        assertEquals("SBIINB", identity.displayName)
    }

    @Test
    fun `the longest run of letters wins, wherever it sits`() {
        // Stated as the rule rather than as a list of known prefixes and categories, so a
        // header shape nobody has seen yet still resolves sensibly.
        assertEquals("LONGNAME", senderIdOf("AB-LONGNAME-S"))
        assertEquals("LONGNAME", senderIdOf("LONGNAME-S"))
        assertEquals("LONGNAME", senderIdOf("AB-LONGNAME"))
    }
}
