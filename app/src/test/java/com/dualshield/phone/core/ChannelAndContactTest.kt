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
import com.dualshield.phone.core.rules.ShieldChannel
import com.dualshield.phone.core.rules.ShieldDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two additions that change what gets blocked: a rule's calls/SMS target, and
 * contacts bypassing the blocklist.
 */
class ChannelAndContactTest {

    private fun rule(
        pattern: String,
        blocksCalls: Boolean = true,
        blocksSms: Boolean = true,
        action: RuleAction = RuleAction.BLOCK,
        patternType: PatternType = PatternType.EXACT,
    ): CompiledRule = requireNotNull(
        CompiledRule.from(
            id = 1,
            stableId = "r",
            name = "Test rule",
            category = RuleCategory.USER_BLOCK,
            pattern = pattern,
            patternType = patternType,
            action = action,
            simScope = SimScope.SIM2,
            confidence = Confidence.HIGH,
            provenance = Provenance.USER_DEFINED,
            priority = 50,
            builtIn = false,
            description = "",
            blocksCalls = blocksCalls,
            blocksSms = blocksSms,
        ),
    )

    private fun snapshot(
        rules: List<CompiledRule>,
        allowContacts: Boolean = true,
    ) = RuleSnapshot(
        perSlot = mapOf(
            1 to RuleSnapshot.buildSimRuleSet(
                slotIndex = 1,
                label = "Personal",
                filteringEnabled = true,
                allowNumbers = emptySet(),
                rules = rules,
                allowContacts = allowContacts,
            ),
        ),
    )

    private fun decide(
        snapshot: RuleSnapshot,
        number: String,
        channel: ShieldChannel,
        isContact: Boolean = false,
    ) = RuleEngine.evaluate(
        snapshot = snapshot,
        info = PhoneNumberNormalizer.normalize(number),
        slotIndex = 1,
        channel = channel,
        isContact = isContact,
    )

    @Test
    fun `a calls-only rule leaves messages alone`() {
        val snapshot = snapshot(listOf(rule("9876543210", blocksCalls = true, blocksSms = false)))
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.CALL) is ShieldDecision.Block)
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.SMS) is ShieldDecision.Allow)
    }

    @Test
    fun `an SMS-only rule leaves calls alone`() {
        val snapshot = snapshot(listOf(rule("9876543210", blocksCalls = false, blocksSms = true)))
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.CALL) is ShieldDecision.Allow)
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.SMS) is ShieldDecision.Block)
    }

    @Test
    fun `a rule targeting both blocks both`() {
        val snapshot = snapshot(listOf(rule("9876543210")))
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.CALL) is ShieldDecision.Block)
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.SMS) is ShieldDecision.Block)
    }

    @Test
    fun `a rule targeting neither channel can never block`() {
        val snapshot = snapshot(listOf(rule("9876543210", blocksCalls = false, blocksSms = false)))
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.CALL) is ShieldDecision.Allow)
        assertTrue(decide(snapshot, "9876543210", ShieldChannel.SMS) is ShieldDecision.Allow)
    }

    @Test
    fun `a saved contact beats a matching block rule`() {
        val snapshot = snapshot(listOf(rule("9876543210")))
        val decision = decide(snapshot, "9876543210", ShieldChannel.CALL, isContact = true)
        assertEquals(AllowReason.CONTACT, (decision as ShieldDecision.Allow).reason)
    }

    @Test
    fun `a contact is blocked once the contacts bypass is switched off`() {
        val snapshot = snapshot(listOf(rule("9876543210")), allowContacts = false)
        assertTrue(
            decide(snapshot, "9876543210", ShieldChannel.CALL, isContact = true)
                is ShieldDecision.Block,
        )
    }

    @Test
    fun `a contact never overrides an emergency allow`() {
        // Belt and braces: emergency is checked before contacts, so the reason stays EMERGENCY.
        val snapshot = snapshot(listOf(rule("1", patternType = PatternType.PREFIX)))
        val decision = decide(snapshot, "112", ShieldChannel.CALL, isContact = true)
        assertEquals(AllowReason.EMERGENCY, (decision as ShieldDecision.Allow).reason)
    }

    @Test
    fun `two rules on the same number with different channels both apply`() {
        // The exact index stores a list per pattern precisely so this works.
        val callsOnly = rule("9876543210", blocksCalls = true, blocksSms = false)
        val smsOnly = requireNotNull(
            CompiledRule.from(
                id = 2,
                stableId = "r2",
                name = "SMS rule",
                category = RuleCategory.USER_BLOCK,
                pattern = "9876543210",
                patternType = PatternType.EXACT,
                action = RuleAction.BLOCK,
                simScope = SimScope.SIM2,
                confidence = Confidence.HIGH,
                provenance = Provenance.USER_DEFINED,
                priority = 51,
                builtIn = false,
                description = "",
                blocksCalls = false,
                blocksSms = true,
            ),
        )
        val snapshot = snapshot(listOf(callsOnly, smsOnly))
        assertEquals(
            "Test rule",
            (decide(snapshot, "9876543210", ShieldChannel.CALL) as ShieldDecision.Block).rule.name,
        )
        assertEquals(
            "SMS rule",
            (decide(snapshot, "9876543210", ShieldChannel.SMS) as ShieldDecision.Block).rule.name,
        )
    }
}
