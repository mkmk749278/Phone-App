package com.dualshield.phone.ui

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.shield.RuleDisplay
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the app uses, and the words it must not.
 *
 * Terminology drifts one screen at a time and is never worth a bug report on its own, which
 * is exactly why it ends up with three names for the same thing. Two failures here are
 * worth more than tidiness, though: an implementation word shown to a user ("Vault") gives
 * them a term that appears nowhere else and means nothing, and a raw regular expression
 * shown as a rule's identity is unreadable to everyone who did not write it.
 */
class ProductLanguageTest {

    private fun uiSources(): List<File> {
        val roots = listOf(
            File("src/main/java/com/dualshield/phone/ui"),
            File("app/src/main/java/com/dualshield/phone/ui"),
        )
        val root = roots.firstOrNull { it.isDirectory }
            ?: error("UI sources not found from ${File(".").absolutePath}")
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /** Text inside double quotes — an approximation of "what the user can read". */
    private val stringLiteral = Regex("\"([^\"\\\\]|\\\\.)*\"")

    private fun userFacingText(file: File): List<String> =
        file.readLines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .flatMap { line -> stringLiteral.findAll(line).map { it.value }.toList() }

    @Test
    fun `no implementation word reaches the user`() {
        // "Vault" is what the storage layer calls it. What the user blocked is a call, and
        // what they want to look at is a list of blocked calls.
        val offenders = uiSources().flatMap { file ->
            userFacingText(file)
                .filter { it.contains("Vault") }
                .map { "${file.name}: $it" }
        }
        assertEquals("These strings expose an internal name", emptyList<String>(), offenders)
    }

    @Test
    fun `the built-in list has one name`() {
        // It is the "India blocklist" on every screen that mentions it. It was previously
        // "India protection" in three places and "India blocklist" in none.
        val offenders = uiSources().flatMap { file ->
            userFacingText(file)
                .filter { it.contains("India protection", ignoreCase = true) }
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `a regular expression is never a rule's public identity`() {
        val regexRule = rule(pattern = "^1600[0-9]{6}$", patternType = PatternType.REGEX)
        val shown = RuleDisplay.pattern(regexRule)
        assertFalse(
            "a pattern shown to the user must not contain regex syntax: $shown",
            shown.orEmpty().any { it in "^$[]{}\\|()" },
        )
        assertEquals("1600…", shown)
    }

    @Test
    fun `a regex that cannot be summarised honestly is not summarised at all`() {
        // Guessing at a pattern the code does not actually understand, and presenting the
        // guess as what the rule does, is worse than showing the rule's name.
        listOf(".*", "[0-9]{10}", "(140|141)[0-9]+", "").forEach { pattern ->
            assertNull(
                "'$pattern' has no honest short form",
                RuleDisplay.pattern(rule(pattern = pattern, patternType = PatternType.REGEX)),
            )
        }
        assertEquals(
            "and the caller falls back to the name",
            "140 Promotional Calls",
            RuleDisplay.patternOrName(
                rule(pattern = ".*", patternType = PatternType.REGEX, name = "140 Promotional Calls"),
            ),
        )
    }

    @Test
    fun `readable patterns keep their meaning`() {
        assertEquals(
            "9876543210",
            RuleDisplay.pattern(rule(pattern = "9876543210", patternType = PatternType.EXACT)),
        )
        assertEquals(
            "140…",
            RuleDisplay.pattern(rule(pattern = "140", patternType = PatternType.PREFIX)),
        )
        assertEquals(
            "…8035…",
            RuleDisplay.pattern(rule(pattern = "8035", patternType = PatternType.CONTAINS)),
        )
    }

    @Test
    fun `a switched-off rule never advertises what it would do`() {
        // The label and the switch must never disagree. A row reading "Blocked" beside an
        // off switch is the same class of failure as an unprotected SIM listing its rules
        // as active: it tells someone they are covered when they are not.
        val off = rule(pattern = "140", patternType = PatternType.PREFIX, enabled = false)
        assertEquals("Off", RuleDisplay.status(off))
        assertEquals("Blocked", RuleDisplay.status(off.copy(enabled = true)))
        assertEquals(
            "Allowed",
            RuleDisplay.status(off.copy(enabled = true, action = RuleAction.ALLOW)),
        )
    }

    @Test
    fun `the India list shows names rather than patterns`() {
        val source = uiSources().first { it.name == "IndiaProtectionScreen.kt" }
        val code = source.readLines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertFalse(
            "the built-in list must not render a rule's pattern; its name carries the series",
            code.contains("RuleDisplay.pattern("),
        )
        assertTrue(code.contains("RuleDisplay.status("))
    }

    private fun rule(
        pattern: String,
        patternType: PatternType,
        name: String = "Rule",
        enabled: Boolean = true,
        action: RuleAction = RuleAction.BLOCK,
    ) = CallRuleEntity(
        stableId = "test",
        name = name,
        category = RuleCategory.OTHER,
        pattern = pattern,
        patternType = patternType,
        action = action,
        simScope = SimScope.BOTH,
        enabled = enabled,
        priority = 100,
        confidence = Confidence.HIGH,
        provenance = Provenance.OFFICIAL,
        description = "",
        builtIn = true,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
