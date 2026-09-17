package com.dualshield.phone.data

import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.RuleEngine
import com.dualshield.phone.core.rules.RuleSnapshot
import com.dualshield.phone.core.rules.ShieldDecision
import com.dualshield.phone.data.repository.compile
import com.dualshield.phone.data.rulepack.RulePackParser
import com.dualshield.phone.data.rulepack.RulePackResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Checks the pack that actually ships, not a fixture.
 *
 * The defaults in section 28 of the handoff are a product promise; this locks them in so a
 * future edit to the JSON cannot quietly change what a fresh install does.
 */
class BundledIndiaPackTest {

    private lateinit var pack: RulePackResult.Success

    @Before
    fun loadPack() {
        val candidates = listOf(
            File("src/main/assets/rules/india_rules.json"),
            File("app/src/main/assets/rules/india_rules.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Bundled India rule pack not found; looked in $candidates")
        val parsed = RulePackParser.parse(file.readText(), now = 0L)
        assertTrue("Bundled pack failed to parse: $parsed", parsed is RulePackResult.Success)
        pack = parsed as RulePackResult.Success
    }

    @Test
    fun `the bundled pack has no validation warnings`() {
        assertEquals(emptyList<String>(), pack.warnings)
    }

    @Test
    fun `heuristics ship disabled and are never presented as official`() {
        val heuristics = pack.rules.filter { it.provenance == Provenance.COMMUNITY }
        assertTrue("Expected seeded heuristics", heuristics.isNotEmpty())
        heuristics.forEach { rule ->
            assertFalse("${rule.stableId} should ship disabled", rule.enabled)
            assertTrue(
                "${rule.stableId} must say it is not official",
                rule.description.contains("not an official", ignoreCase = true),
            )
        }
    }

    @Test
    fun `special caller rules ship disabled`() {
        pack.rules
            .filter { it.stableId.startsWith("special-") }
            .also { assertTrue(it.isNotEmpty()) }
            .forEach { assertFalse("${it.stableId} should ship disabled", it.enabled) }
    }

    @Test
    fun `transactional and toll free series are allow rules`() {
        listOf("india-1600", "india-1601", "india-1800").forEach { id ->
            val rule = pack.rules.first { it.stableId == id }
            assertEquals("$id must allow", RuleAction.ALLOW, rule.action)
            assertEquals("$id should cover both SIMs", SimScope.BOTH, rule.simScope)
            assertTrue("$id must be enabled", rule.enabled)
        }
    }

    @Test
    fun `fresh install defaults behave as documented`() {
        val compiled = pack.rules.filter { it.enabled }.mapNotNull { it.compile() }
        val snapshot = RuleSnapshot(
            perSlot = mapOf(
                // SIM 1 — Duty: filtering off out of the box.
                0 to RuleSnapshot.buildSimRuleSet(0, "Duty", false, emptySet(), compiled),
                // SIM 2 — Personal: filtering on.
                1 to RuleSnapshot.buildSimRuleSet(1, "Personal", true, emptySet(), compiled),
            ),
        )

        fun decide(number: String, slot: Int) =
            RuleEngine.evaluate(snapshot, PhoneNumberNormalizer.normalize(number), slot)

        // Promotional and premium-rate are blocked on the personal line.
        assertTrue(decide("1401234567", 1) is ShieldDecision.Block)
        assertTrue(decide("0900123456", 1) is ShieldDecision.Block)

        // Service, transactional and toll-free traffic still gets through.
        assertTrue(decide("1600123456", 1) is ShieldDecision.Allow)
        assertTrue(decide("1601123456", 1) is ShieldDecision.Allow)
        assertTrue(decide("18002026161", 1) is ShieldDecision.Allow)

        // An ordinary mobile is untouched.
        assertTrue(decide("9876543210", 1) is ShieldDecision.Allow)

        // A number matching a seeded heuristic is allowed, because heuristics ship off.
        assertTrue(decide("9880351234", 1) is ShieldDecision.Allow)

        // And the duty line is filtered by nothing at all.
        assertTrue(decide("1401234567", 0) is ShieldDecision.Allow)
        assertTrue(decide("0900123456", 0) is ShieldDecision.Allow)
    }
}
