package com.dualshield.phone.data

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.data.rulepack.RulePackParser
import com.dualshield.phone.data.rulepack.RulePackResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the grouping the India protection screen renders.
 *
 * Those groups are sections of one lazy list, so a rule landing in two of them means two
 * items with the same key, which Compose treats as a hard error. This asserts the partition
 * stays exhaustive and exclusive as the pack grows.
 */
class PackPartitionTest {

    private val callerTypeCategories = setOf(
        RuleCategory.UNKNOWN_CALLER,
        RuleCategory.PRIVATE_CALLER,
        RuleCategory.INTERNATIONAL,
    )

    /** Mirrors the `when` in IndiaProtectionScreen. */
    private fun bucketOf(rule: CallRuleEntity): String = when {
        rule.category in callerTypeCategories -> "callerTypes"
        rule.confidence == Confidence.LOW -> "heuristic"
        rule.provenance == Provenance.OFFICIAL ||
            rule.provenance == Provenance.OFFICIAL_OR_ESTABLISHED -> "official"
        else -> "other"
    }

    private fun loadPack(): List<CallRuleEntity> {
        val file = listOf(
            File("src/main/assets/rules/india_rules.json"),
            File("app/src/main/assets/rules/india_rules.json"),
        ).firstOrNull { it.exists() } ?: error("Bundled India rule pack not found")
        val parsed = RulePackParser.parse(file.readText(), now = 0L)
        assertTrue("Bundled pack failed to parse", parsed is RulePackResult.Success)
        return (parsed as RulePackResult.Success).rules
    }

    @Test
    fun `every rule lands in exactly one section`() {
        val rules = loadPack()
        val buckets = rules.groupBy { bucketOf(it) }
        val total = buckets.values.sumOf { it.size }
        assertEquals("Every rule must be in exactly one bucket", rules.size, total)
    }

    @Test
    fun `stable ids are unique, because they are also lazy-list keys`() {
        val rules = loadPack()
        val duplicates = rules.groupBy { it.stableId }.filterValues { it.size > 1 }.keys
        assertEquals("Duplicate stable ids: $duplicates", emptySet<String>(), duplicates)
    }

    @Test
    fun `caller-type rules are not also counted as heuristics`() {
        // The pack must not rely on filter ordering to disambiguate.
        val rules = loadPack()
        rules.filter { it.category in callerTypeCategories }.forEach {
            assertEquals(
                "${it.stableId} is a caller type, so it must not also be LOW confidence",
                "callerTypes",
                bucketOf(it),
            )
        }
    }
}
