package com.dualshield.phone.ui

import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.displayForSlot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a SIM is named, everywhere.
 *
 * The app stopped seeding "Duty" and "Personal" as labels, because a seeded label reads back
 * as though the user chose it. What that left behind was every screen still formatting
 * `"SIM N · $label"` unconditionally — so an unnamed line rendered as `SIM 2 · ` with a
 * dangling separator, which looks like a value that failed to load rather than a line
 * nobody has named.
 *
 * The rule now lives in one function. These tests are mostly about keeping it that way: a
 * second copy is how the first one got out of step.
 */
class SimLabelTest {

    @Test
    fun `an unnamed SIM is named by its slot, with no dangling separator`() {
        listOf(null, "", " ", "\t").forEach { label ->
            val shown = Formatting.simLabel(0, label)
            assertEquals("SIM 1", shown)
            assertFalse("'$shown' must not end in a separator", shown.trimEnd().endsWith("·"))
        }
    }

    @Test
    fun `a named SIM shows the name the user chose`() {
        assertEquals("SIM 1 · Personal", Formatting.simLabel(0, "Personal"))
        assertEquals("SIM 2 · Duty", Formatting.simLabel(1, "Duty"))
        // Whatever they actually typed, not a label the app decided on their behalf.
        assertEquals("SIM 2 · Shop", Formatting.simLabel(1, "Shop"))
    }

    @Test
    fun `an unresolved SIM is not called SIM 0`() {
        // Telecom does not always say which line a call arrived on. Inventing a slot number
        // there would put a specific, wrong line on screen next to a real call.
        assertEquals("Unknown SIM", Formatting.simLabel(null, null))
        assertEquals("Unknown SIM", Formatting.simLabel(null, "  "))
    }

    @Test
    fun `SimOption does not carry its own copy of the rule`() {
        listOf("", "Personal").forEach { label ->
            val option = SimOption(slotIndex = 1, label = label, present = true, protectionEnabled = true)
            assertEquals(Formatting.simLabel(1, label), option.display)
        }
    }

    @Test
    fun `a slot with no profile loaded still has a name`() {
        // Profiles arrive from Room a frame or two after the first composition. A screen
        // that renders before they land must not show an empty string where a line's name
        // belongs.
        assertEquals("SIM 1", emptyList<SimOption>().displayForSlot(0))
        assertEquals("SIM 2", emptyList<SimOption>().displayForSlot(1))
        assertEquals(
            "SIM 2 · Duty",
            listOf(SimOption(1, "Duty", present = true, protectionEnabled = false))
                .displayForSlot(1),
        )
    }

    @Test
    fun `no screen builds a SIM name of its own`() {
        // The check that actually keeps this fixed. Three copies of the rule is how one of
        // them stayed broken after the others were corrected.
        val root = listOf(
            File("src/main/java/com/dualshield/phone/ui"),
            File("app/src/main/java/com/dualshield/phone/ui"),
        ).first { it.isDirectory }

        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" && it.name != "Formatting.kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filterNot { (_, line) ->
                        val t = line.trimStart()
                        t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
                    }
                    // "SIM ${slotIndex + 1}" and friends — the rule, written out again.
                    .filter { (_, line) -> line.contains(Regex("\"SIM \\$\\{")) }
                    .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
            }
            .toList()

        assertEquals(
            "Use Formatting.slotName / Formatting.simLabel instead of rebuilding the name",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `every SIM-bearing screen goes through the shared helpers`() {
        // §96 lists the surfaces that must show the configured label. This does not prove
        // each one is correct — only that none of them resolves a SIM name by hand, which
        // is the failure mode that produced the defect in the first place.
        val root = listOf(
            File("src/main/java/com/dualshield/phone/ui"),
            File("app/src/main/java/com/dualshield/phone/ui"),
        ).first { it.isDirectory }

        val surfaces = listOf(
            "DialpadScreen.kt", "PhoneScreen.kt", "CallDetailsScreen.kt", "InCallScreen.kt",
            "SettingsScreen.kt", "ShieldScreen.kt", "IndiaProtectionScreen.kt",
            "RecoveryProtectionScreen.kt", "VaultScreen.kt", "RuleTesterScreen.kt",
        )
        val files = root.walkTopDown().filter { it.extension == "kt" }.associateBy { it.name }

        surfaces.forEach { name ->
            val file = files[name] ?: error("§96 names $name but it does not exist")
            val text = file.readText()
            val resolvesSims = text.contains("SimOption") || text.contains("simLabel") ||
                text.contains("displayForSlot") || text.contains(".display") ||
                text.contains("slotName")
            assertTrue("$name shows SIM context but resolves no label", resolvesSims)
        }
    }

    @Test
    fun `an avatar never claims to identify someone by their country code`() {
        // "919" was the monogram for every Indian mobile in the recording's Contacts list:
        // a column of identical circles, each standing for a different person.
        assertNull(Formatting.initials(null, "+919398679480"))
        assertNull(Formatting.initials("", "+919398679480"))
        assertNull(Formatting.initials("+91 93986 79480", "+919398679480"))
        assertNull(Formatting.initials("06303114934", "06303114934"))
    }

    @Test
    fun `a real name still gets its monogram`() {
        assertEquals("KK", Formatting.initials("Kishore Kumar"))
        assertEquals("AR", Formatting.initials("A.RAVI PC-830"))
        assertEquals("MU", Formatting.initials("Mum"))
    }
}
