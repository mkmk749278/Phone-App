package com.dualshield.phone.core

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.AllowReason
import com.dualshield.phone.core.rules.CompiledRule
import com.dualshield.phone.core.rules.RuleEngine
import com.dualshield.phone.core.rules.RuleSnapshot
import com.dualshield.phone.core.rules.ShieldDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tests that matter most: SIM isolation and fail-open.
 *
 * If any of these regress, the product's central promise is broken, so they are written
 * against the same [RuleEngine] entry point the screening service calls.
 */
class RuleEngineTest {

    private fun rule(
        id: Long,
        name: String,
        pattern: String,
        patternType: PatternType = PatternType.REGEX,
        action: RuleAction = RuleAction.BLOCK,
        scope: SimScope = SimScope.SIM2,
        confidence: Confidence = Confidence.HIGH,
        provenance: Provenance = Provenance.OFFICIAL,
        builtIn: Boolean = true,
        priority: Int = 100,
    ): CompiledRule = requireNotNull(
        CompiledRule.from(
            id = id,
            stableId = "rule-$id",
            name = name,
            category = RuleCategory.OTHER,
            pattern = pattern,
            patternType = patternType,
            action = action,
            simScope = scope,
            confidence = confidence,
            provenance = provenance,
            priority = priority,
            builtIn = builtIn,
            description = "",
        ),
    ) { "rule '$name' failed to compile" }

    private fun snapshot(
        sim1Enabled: Boolean = false,
        sim2Enabled: Boolean = true,
        rules: List<CompiledRule> = emptyList(),
        sim1Allow: Set<String> = emptySet(),
        sim2Allow: Set<String> = emptySet(),
    ) = RuleSnapshot(
        perSlot = mapOf(
            0 to RuleSnapshot.buildSimRuleSet(0, "Duty", sim1Enabled, sim1Allow, rules),
            1 to RuleSnapshot.buildSimRuleSet(1, "Personal", sim2Enabled, sim2Allow, rules),
        ),
    )

    private fun evaluate(snapshot: RuleSnapshot, number: String, slot: Int?) =
        RuleEngine.evaluate(snapshot, PhoneNumberNormalizer.normalize(number), slot)

    // ------------------------------------------------------------------ SIM isolation

    @Test
    fun `a SIM 2 rule blocks on SIM 2`() {
        val snapshot = snapshot(rules = listOf(rule(1, "140", "^140[0-9]{7}$")))
        val decision = evaluate(snapshot, "1401234567", slot = 1)
        assertTrue(decision is ShieldDecision.Block)
        assertEquals("140", (decision as ShieldDecision.Block).rule.name)
    }

    @Test
    fun `the same SIM 2 rule never touches SIM 1`() {
        // SIM 1 filtering is on, to prove isolation is about scope and not about the switch.
        val snapshot = snapshot(
            sim1Enabled = true,
            rules = listOf(rule(1, "140", "^140[0-9]{7}$", scope = SimScope.SIM2)),
        )
        val decision = evaluate(snapshot, "1401234567", slot = 0)
        assertTrue(decision is ShieldDecision.Allow)
        assertEquals(AllowReason.NO_MATCH, (decision as ShieldDecision.Allow).reason)
    }

    @Test
    fun `a BOTH rule reaches either SIM`() {
        val snapshot = snapshot(
            sim1Enabled = true,
            rules = listOf(rule(1, "140", "^140[0-9]{7}$", scope = SimScope.BOTH)),
        )
        assertTrue(evaluate(snapshot, "1401234567", slot = 0) is ShieldDecision.Block)
        assertTrue(evaluate(snapshot, "1401234567", slot = 1) is ShieldDecision.Block)
    }

    // ------------------------------------------------------------------ fail open

    @Test
    fun `an unresolved SIM always allows`() {
        val snapshot = snapshot(rules = listOf(rule(1, "140", "^140[0-9]{7}$")))
        val decision = evaluate(snapshot, "1401234567", slot = null)
        assertEquals(
            AllowReason.SIM_UNRESOLVED,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `a slot with no profile always allows`() {
        val snapshot = snapshot(rules = listOf(rule(1, "140", "^140[0-9]{7}$")))
        val decision = evaluate(snapshot, "1401234567", slot = 7)
        assertEquals(
            AllowReason.SLOT_OUT_OF_RANGE,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `protection off means nothing is blocked`() {
        val snapshot = snapshot(
            sim2Enabled = false,
            rules = listOf(rule(1, "140", "^140[0-9]{7}$")),
        )
        val decision = evaluate(snapshot, "1401234567", slot = 1)
        assertEquals(
            AllowReason.FILTERING_DISABLED,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `an empty snapshot allows everything`() {
        val decision = evaluate(RuleSnapshot.EMPTY, "1401234567", slot = 1)
        assertTrue(decision is ShieldDecision.Allow)
    }

    // ------------------------------------------------------------------ precedence

    @Test
    fun `the allowlist beats a matching block rule`() {
        val snapshot = snapshot(
            rules = listOf(rule(1, "140", "^140[0-9]{7}$")),
            sim2Allow = setOf("1401234567"),
        )
        val decision = evaluate(snapshot, "1401234567", slot = 1)
        assertEquals(AllowReason.ALLOWLIST, (decision as ShieldDecision.Allow).reason)
    }

    @Test
    fun `an allowlist entry on one SIM does not leak to the other`() {
        val snapshot = snapshot(
            sim1Enabled = true,
            rules = listOf(rule(1, "140", "^140[0-9]{7}$", scope = SimScope.BOTH)),
            sim2Allow = setOf("1401234567"),
        )
        assertTrue(evaluate(snapshot, "1401234567", slot = 0) is ShieldDecision.Block)
        assertTrue(evaluate(snapshot, "1401234567", slot = 1) is ShieldDecision.Allow)
    }

    @Test
    fun `a user block beats a built-in allow`() {
        val snapshot = snapshot(
            rules = listOf(
                rule(1, "1800 toll free", "1800", PatternType.PREFIX, RuleAction.ALLOW),
                rule(
                    id = 2,
                    name = "My block",
                    pattern = "18002026161",
                    patternType = PatternType.EXACT,
                    action = RuleAction.BLOCK,
                    provenance = Provenance.USER_DEFINED,
                    builtIn = false,
                    priority = 50,
                ),
            ),
        )
        val decision = evaluate(snapshot, "18002026161", slot = 1)
        assertEquals("My block", (decision as ShieldDecision.Block).rule.name)
    }

    @Test
    fun `a built-in allow beats a built-in block`() {
        val snapshot = snapshot(
            rules = listOf(
                rule(1, "1800 toll free", "1800", PatternType.PREFIX, RuleAction.ALLOW),
                rule(2, "Broad block", "180", PatternType.PREFIX, RuleAction.BLOCK),
            ),
        )
        val decision = evaluate(snapshot, "18002026161", slot = 1)
        assertEquals(AllowReason.ALLOW_RULE, (decision as ShieldDecision.Allow).reason)
    }

    @Test
    fun `a high confidence rule is consulted before a heuristic`() {
        val snapshot = snapshot(
            rules = listOf(
                rule(
                    id = 1,
                    name = "Heuristic",
                    pattern = "8035",
                    patternType = PatternType.CONTAINS,
                    confidence = Confidence.LOW,
                    provenance = Provenance.COMMUNITY,
                    priority = 500,
                ),
                rule(2, "Official", "^988035[0-9]{4}$", priority = 100),
            ),
        )
        val decision = evaluate(snapshot, "9880351234", slot = 1)
        assertEquals("Official", (decision as ShieldDecision.Block).rule.name)
    }

    // ------------------------------------------------------------------ safety

    @Test
    fun `emergency numbers survive a rule that would otherwise match them`() {
        val snapshot = snapshot(
            rules = listOf(rule(1, "Everything short", "1", PatternType.PREFIX)),
        )
        listOf("112", "100", "101", "108").forEach { number ->
            val decision = evaluate(snapshot, number, slot = 1)
            assertEquals(
                "failed for $number",
                AllowReason.EMERGENCY,
                (decision as ShieldDecision.Allow).reason,
            )
        }
    }

    @Test
    fun `a broken regex disables its rule instead of blocking everything`() {
        val broken = CompiledRule.from(
            id = 1,
            stableId = "broken",
            name = "Broken",
            category = RuleCategory.OTHER,
            pattern = "^140[0-9",
            patternType = PatternType.REGEX,
            action = RuleAction.BLOCK,
            simScope = SimScope.SIM2,
            confidence = Confidence.HIGH,
            provenance = Provenance.USER_DEFINED,
            priority = 50,
            builtIn = false,
            description = "",
        )
        assertEquals(null, broken)
    }

    @Test
    fun `private callers are only blocked when the special rule is present`() {
        val without = snapshot(rules = listOf(rule(1, "140", "^140[0-9]{7}$")))
        assertTrue(evaluate(without, "", slot = 1) is ShieldDecision.Allow)

        val with = snapshot(
            rules = listOf(
                rule(
                    id = 1,
                    name = "Private callers",
                    pattern = "PRIVATE_CALLER",
                    patternType = PatternType.SPECIAL,
                    provenance = Provenance.USER_DEFINED,
                    builtIn = false,
                ),
            ),
        )
        assertTrue(evaluate(with, "", slot = 1) is ShieldDecision.Block)
    }

    @Test
    fun `international blocking does not catch indian numbers`() {
        val snapshot = snapshot(
            rules = listOf(
                rule(
                    id = 1,
                    name = "International",
                    pattern = "INTERNATIONAL_CALLER",
                    patternType = PatternType.SPECIAL,
                    provenance = Provenance.USER_DEFINED,
                    builtIn = false,
                ),
            ),
        )
        assertTrue(evaluate(snapshot, "+14155552671", slot = 1) is ShieldDecision.Block)
        assertTrue(evaluate(snapshot, "+919876543210", slot = 1) is ShieldDecision.Allow)
        assertTrue(evaluate(snapshot, "9876543210", slot = 1) is ShieldDecision.Allow)
    }
}
