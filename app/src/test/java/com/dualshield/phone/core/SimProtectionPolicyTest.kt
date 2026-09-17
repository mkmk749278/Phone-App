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
import com.dualshield.phone.core.shield.CallerActivity
import com.dualshield.phone.core.shield.RecoveryEvaluator
import com.dualshield.phone.core.shield.RecoveryMode
import com.dualshield.phone.core.shield.RecoverySettings
import com.dualshield.phone.core.shield.ShieldPause
import com.dualshield.phone.data.repository.SimRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The asymmetric two-SIM policy: one line filtered, one line that rings for anything.
 *
 * The asymmetry is the part most easily lost. It is not a preference the user set once and
 * can be re-derived from — it is the difference between a duty line that rings for an unknown
 * number at 3am and one that quietly rejects it. Every test here is written against the same
 * decision function the screening service calls, with the SIM the call arrived on as an
 * explicit argument, because "which line was this?" is the input the whole policy turns on.
 *
 * Screen-off behaviour is not testable here at all — no unit test can prove the display
 * stayed dark. What is testable is that the blocking path reaches its verdict from memory
 * and starts nothing that could wake anything; `ScreeningPathTest` holds that line, and
 * `docs/DEVICE_TESTING.md` §1 covers the rest on a real phone.
 */
class SimProtectionPolicyTest {

    private val filtered = 0
    private val unfiltered = 1

    private fun rule(
        id: Long,
        name: String,
        pattern: String,
        patternType: PatternType = PatternType.EXACT,
        action: RuleAction = RuleAction.BLOCK,
        scope: SimScope = SimScope.BOTH,
        confidence: Confidence = Confidence.HIGH,
        provenance: Provenance = Provenance.USER_DEFINED,
        builtIn: Boolean = false,
    ): CompiledRule = requireNotNull(
        CompiledRule.from(
            id = id,
            stableId = "rule-$id",
            name = name,
            category = RuleCategory.USER_BLOCK,
            pattern = pattern,
            patternType = patternType,
            action = action,
            simScope = scope,
            confidence = confidence,
            provenance = provenance,
            priority = 100,
            builtIn = builtIn,
            description = "",
        ),
    ) { "rule '$name' failed to compile" }

    /** The shipped shape: slot 0 enforcing, slot 1 not, with the same rules saved on both. */
    private fun snapshot(
        slot0Enabled: Boolean = true,
        slot1Enabled: Boolean = false,
        rules: List<CompiledRule> = listOf(rule(1, "Blocked caller", "9876543210")),
    ) = RuleSnapshot(
        perSlot = mapOf(
            0 to RuleSnapshot.buildSimRuleSet(0, "", slot0Enabled, emptySet(), rules),
            1 to RuleSnapshot.buildSimRuleSet(1, "", slot1Enabled, emptySet(), rules),
        ),
    )

    private fun decide(snapshot: RuleSnapshot, number: String, slot: Int?) =
        RuleEngine.evaluate(snapshot, PhoneNumberNormalizer.normalize(number), slot)

    // --------------------------------------------------------------- first-run defaults

    @Test
    fun `the first line ships protected and the second does not`() {
        assertTrue(
            "slot 0 is the line the product filters",
            SimRepository.defaultFilteringForSlot(0),
        )
        assertFalse(
            "slot 1 must ring for anything until the user says otherwise",
            SimRepository.defaultFilteringForSlot(1),
        )
    }

    @Test
    fun `a slot the app did not expect starts unfiltered`() {
        // Three-SIM and eSIM-plus-two hardware exists. An unfamiliar slot silently filtering
        // is a missed call the user cannot explain; unfiltered is merely unprotected.
        (2..5).forEach { slot ->
            assertFalse("slot $slot", SimRepository.defaultFilteringForSlot(slot))
        }
    }

    // ------------------------------------------------------------------ Tests 1 and 2

    @Test
    fun `test 1 - a blocked caller on the protected line is blocked`() {
        val decision = decide(snapshot(), "9876543210", filtered)
        assertTrue("the protected line enforces its rules", decision is ShieldDecision.Block)
    }

    @Test
    fun `test 2 - the same blocked caller on the unprotected line rings`() {
        val decision = decide(snapshot(), "9876543210", unfiltered)
        assertTrue(decision is ShieldDecision.Allow)
        assertEquals(
            "the rule is saved on this SIM and deliberately not applied",
            AllowReason.FILTERING_DISABLED,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `the rules are still there while the line is unprotected`() {
        // "Not enforced" must never quietly become "deleted". Turning protection on has to
        // restore the exact configuration, so the rule stays indexed for the slot either way.
        val snapshot = snapshot()
        val slot1 = requireNotNull(snapshot.forSlot(1))
        assertFalse("the rule is indexed for this slot", slot1.userBlock.isEmpty)
        assertFalse("it is simply not consulted", slot1.filteringEnabled)
    }

    // ----------------------------------------------------------------------- Test 3

    @Test
    fun `test 3 - an unknown caller on the unprotected line rings`() {
        val decision = decide(snapshot(), "9000000001", unfiltered)
        assertEquals(
            AllowReason.FILTERING_DISABLED,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `nothing the built-in pack or a heuristic says applies to an unprotected line`() {
        // The strongest form of the requirement: not "no rule happened to match", but that
        // the highest-confidence official rule in the pack is not consulted at all.
        val pack = listOf(
            rule(
                id = 2,
                name = "140 series",
                pattern = "140",
                patternType = PatternType.PREFIX,
                provenance = Provenance.OFFICIAL,
                builtIn = true,
            ),
            rule(
                id = 3,
                name = "Heuristic",
                pattern = "9000",
                patternType = PatternType.PREFIX,
                confidence = Confidence.LOW,
                provenance = Provenance.COMMUNITY,
                builtIn = true,
            ),
        )
        val snapshot = snapshot(rules = pack)
        listOf("1401234567", "9000123456").forEach { number ->
            assertTrue(
                "$number must ring on the unprotected line",
                decide(snapshot, number, unfiltered) is ShieldDecision.Allow,
            )
            assertTrue(
                "$number is blocked on the protected line, so the difference is the SIM",
                decide(snapshot, number, filtered) is ShieldDecision.Block,
            )
        }
    }

    @Test
    fun `behavioural signals never escalate a call on an unprotected line`() {
        // Recovery runs only on a plain no-match allow. An unprotected line returns a
        // different reason, and that is what keeps a persistent caller on the duty line
        // from being silenced by a heuristic the user never asked for on that SIM.
        val decision = decide(snapshot(), "9876543210", unfiltered) as ShieldDecision.Allow
        assertFalse(
            "FILTERING_DISABLED must not be mistaken for 'nothing matched'",
            decision.reason == AllowReason.NO_MATCH,
        )

        // And the evaluator itself would have escalated, had it been asked.
        val hammering = (1..RecoveryEvaluator.HIGH_FREQUENCY_PER_DAY)
            .fold(CallerActivity(matchKey = "9876543210")) { activity, i ->
                activity.recording(now = i * 60_000L)
            }
        val verdict = RecoveryEvaluator.evaluate(
            activity = hammering,
            settings = RecoverySettings(mode = RecoveryMode.SCREEN),
            isContact = false,
            now = RecoveryEvaluator.HIGH_FREQUENCY_PER_DAY * 60_000L,
        )
        assertTrue("the signal is real; the SIM is why it is not acted on", verdict.suspicious)
    }

    // ------------------------------------------------------------------ Tests 4 and 5

    @Test
    fun `test 4 - enabling protection on the second line enforces its saved rules`() {
        val decision = decide(snapshot(slot1Enabled = true), "9876543210", unfiltered)
        assertTrue(
            "the same rule, the same number, now enforced",
            decision is ShieldDecision.Block,
        )
    }

    @Test
    fun `test 5 - disabling it again returns the line to ringing for everything`() {
        val on = decide(snapshot(slot1Enabled = true), "9876543210", unfiltered)
        val off = decide(snapshot(slot1Enabled = false), "9876543210", unfiltered)
        assertTrue(on is ShieldDecision.Block)
        assertEquals(AllowReason.FILTERING_DISABLED, (off as ShieldDecision.Allow).reason)
    }

    @Test
    fun `turning protection off and on again is not destructive`() {
        val rules = listOf(rule(1, "Blocked caller", "9876543210"))
        val before = RuleSnapshot.buildSimRuleSet(1, "", true, setOf("9998887776"), rules)
        val during = RuleSnapshot.buildSimRuleSet(1, "", false, setOf("9998887776"), rules)
        val after = RuleSnapshot.buildSimRuleSet(1, "", true, setOf("9998887776"), rules)

        assertEquals("the allowlist survives", before.allowNumbers, during.allowNumbers)
        assertEquals(before.allowNumbers, after.allowNumbers)
        assertEquals(
            "and so does the decision it produces",
            (decide(RuleSnapshot(mapOf(1 to before)), "9876543210", 1) as ShieldDecision.Block)
                .rule.name,
            (decide(RuleSnapshot(mapOf(1 to after)), "9876543210", 1) as ShieldDecision.Block)
                .rule.name,
        )
    }

    // ------------------------------------------------------------------ Tests 6 and 7

    @Test
    fun `test 6 - pausing the protected line suspends only that line`() {
        val now = 1_000_000L
        val pause = ShieldPause.timed(SimScope.SIM1, durationMillis = 60L * 60_000L, now = now)

        assertTrue("the protected line is paused", pause.suspends(filtered, now))
        assertFalse("the other line is not", pause.suspends(unfiltered, now))
        assertFalse(
            "and the pause lapses on its own",
            pause.suspends(filtered, now + 60L * 60_000L),
        )
    }

    @Test
    fun `test 7 - the unprotected line is unchanged by a pause and by its resumption`() {
        val now = 2_000_000L
        val snapshot = snapshot()
        val pause = ShieldPause.timed(SimScope.SIM1, durationMillis = 60L * 60_000L, now = now)

        // Before, during and after a pause on the other line, the answer for this line is
        // the same one, for the same reason.
        listOf(now - 1, now + 1, now + 60L * 60_000L + 1).forEach { instant ->
            assertFalse("pause must not reach slot 1", pause.suspends(unfiltered, instant))
            assertEquals(
                AllowReason.FILTERING_DISABLED,
                (decide(snapshot, "9876543210", unfiltered) as ShieldDecision.Allow).reason,
            )
        }

        // And the protected line goes back to blocking once the pause lapses.
        assertFalse(pause.suspends(filtered, now + 60L * 60_000L + 1))
        assertTrue(decide(snapshot, "9876543210", filtered) is ShieldDecision.Block)
    }

    // ---------------------------------------------------------------------- fail open

    @Test
    fun `a call whose SIM could not be resolved is never blocked`() {
        // The policy is per-SIM, so not knowing the SIM means not knowing the policy. A
        // guess here would block a duty-line call on the strength of the other line's rules.
        val decision = decide(snapshot(), "9876543210", slot = null)
        assertEquals(
            AllowReason.SIM_UNRESOLVED,
            (decision as ShieldDecision.Allow).reason,
        )
    }

    @Test
    fun `an indefinite pause on one line still does not cover an unresolved SIM`() {
        val pause = ShieldPause.indefinite(SimScope.SIM1)
        assertFalse(pause.suspends(null, now = 1L))
        assertTrue(ShieldPause.indefinite(SimScope.BOTH).suspends(null, now = 1L))
    }
}
