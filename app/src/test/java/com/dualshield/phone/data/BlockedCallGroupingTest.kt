package com.dualshield.phone.data

import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.repository.groupBlockedCalls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recents must stay a plain record of calls that happened, so blocked attempts live behind
 * one entry and are grouped per caller once you open it. A number that tried seventeen times
 * is one row saying seventeen.
 */
class BlockedCallGroupingTest {

    private var nextId = 1L

    private fun record(
        number: String,
        timestamp: Long,
        name: String? = null,
        rule: String = "Prefix blocklist",
    ) = BlockedCallEntity(
        id = nextId++,
        rawNumber = number,
        normalizedNumber = number.filter { it.isDigit() },
        displayName = name,
        timestamp = timestamp,
        simSlot = 0,
        subscriptionId = 1,
        simLabel = "SIM 1",
        matchedRuleId = 7L,
        matchedRuleStableId = "rule-7",
        matchedRuleName = rule,
        category = RuleCategory.OTHER,
        reason = "Matched \"$rule\"",
    )

    @Test
    fun `repeated attempts from one number collapse to a single counted row`() {
        val groups = groupBlockedCalls(
            (1..17).map { record("+911600400024", timestamp = 1_000L * it) },
        )
        assertEquals(1, groups.size)
        assertEquals(17, groups.first().attempts)
        assertEquals("(17)", groups.first().attemptsLabel)
    }

    @Test
    fun `one attempt carries no count label`() {
        val groups = groupBlockedCalls(listOf(record("+918069378433", 1_000L)))
        assertEquals(1, groups.first().attempts)
        assertEquals("", groups.first().attemptsLabel)
    }

    @Test
    fun `the same line blocked under different spellings is one caller`() {
        val groups = groupBlockedCalls(
            listOf(
                record("+91 96185 79123", 3_000L),
                record("09618579123", 2_000L),
                record("9618579123", 1_000L),
            ),
        )
        assertEquals("three spellings of one line must not read as three callers", 1, groups.size)
        assertEquals(3, groups.first().attempts)
    }

    @Test
    fun `different numbers stay separate`() {
        val groups = groupBlockedCalls(
            listOf(
                record("+911600400024", 3_000L),
                record("+911600300542", 2_000L),
                record("+914069860394", 1_000L),
            ),
        )
        assertEquals(3, groups.size)
    }

    @Test
    fun `groups are ordered by the most recent attempt`() {
        val groups = groupBlockedCalls(
            listOf(
                record("+911600400024", 1_000L),
                record("+918069378433", 9_000L),
                record("+911600300542", 5_000L),
            ),
        )
        assertEquals(listOf(9_000L, 5_000L, 1_000L), groups.map { it.latestTimestamp })
    }

    @Test
    fun `two withheld callers are never merged into one`() {
        val groups = groupBlockedCalls(
            listOf(record("Unknown", 2_000L), record("Unknown", 1_000L)),
        )
        assertEquals(
            "a withheld caller has no identity, so two of them are not the same party",
            2,
            groups.size,
        )
    }

    @Test
    fun `a name learned later is used for the whole group`() {
        val groups = groupBlockedCalls(
            listOf(
                record("+919618579123", 2_000L, name = "Kishore"),
                record("+919618579123", 1_000L, name = null),
            ),
        )
        assertEquals("Kishore", groups.first().title)
    }

    @Test
    fun `an unnamed caller falls back to the formatted number`() {
        val groups = groupBlockedCalls(listOf(record("+919618579123", 1_000L)))
        assertEquals("+91 96185 79123", groups.first().title)
    }

    @Test
    fun `no records means no groups rather than an error`() {
        assertTrue(groupBlockedCalls(emptyList()).isEmpty())
    }

    @Test
    fun `every attempt is kept for the detail view`() {
        val groups = groupBlockedCalls(
            (1..5).map { record("+911600400024", timestamp = 1_000L * it) },
        )
        assertEquals(5, groups.first().records.size)
        assertEquals(
            "newest first, so the detail view reads like a history",
            listOf(5_000L, 4_000L, 3_000L, 2_000L, 1_000L),
            groups.first().records.map { it.timestamp },
        )
    }
}
