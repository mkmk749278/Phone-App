package com.dualshield.phone.data

import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.rulepack.RulePackParser
import com.dualshield.phone.data.rulepack.RulePackResult
import com.dualshield.phone.data.rulepack.RulePackRuleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePackParserTest {

    private val minimalPack = """
        {
          "schemaVersion": 1,
          "packId": "test",
          "packVersion": 1,
          "rules": [
            {
              "id": "t-1",
              "name": "Test 140",
              "category": "TELEMARKETING_PROMOTIONAL",
              "pattern": "^140[0-9]{7}${'$'}",
              "patternType": "REGEX",
              "action": "BLOCK",
              "simScope": "SIM2",
              "enabled": true
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a valid pack parses into entities`() {
        val result = RulePackParser.parse(minimalPack, now = 1L)
        assertTrue(result is RulePackResult.Success)
        val success = result as RulePackResult.Success
        assertEquals(1, success.rules.size)
        assertEquals("t-1", success.rules.first().stableId)
        assertEquals(PatternType.REGEX, success.rules.first().patternType)
        assertEquals(RuleAction.BLOCK, success.rules.first().action)
        assertEquals(SimScope.SIM2, success.rules.first().simScope)
        assertTrue(success.rules.first().builtIn)
    }

    @Test
    fun `malformed json fails with a readable message rather than an exception`() {
        val result = RulePackParser.parse("{ not json", now = 1L)
        assertTrue(result is RulePackResult.Failure)
        assertTrue((result as RulePackResult.Failure).message.contains("valid rule pack"))
    }

    @Test
    fun `a future schema version is refused`() {
        val future = minimalPack.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        val result = RulePackParser.parse(future, now = 1L)
        assertTrue(result is RulePackResult.Failure)
    }

    @Test
    fun `an invalid regex is skipped with a warning instead of being imported broken`() {
        val broken = minimalPack.replace("^140[0-9]{7}${'$'}", "^140[0-9")
        val result = RulePackParser.parse(broken, now = 1L)
        // The only rule was rejected, so the pack as a whole has nothing usable.
        assertTrue(result is RulePackResult.Failure)
    }

    @Test
    fun `duplicate ids keep the first definition only`() {
        val duplicated = minimalPack.replace(
            "\"rules\": [",
            """
            "rules": [
              {
                "id": "t-1",
                "name": "First",
                "category": "OTHER",
                "pattern": "999",
                "patternType": "PREFIX",
                "action": "BLOCK",
                "simScope": "SIM2"
              },
            """.trimIndent(),
        )
        val result = RulePackParser.parse(duplicated, now = 1L) as RulePackResult.Success
        assertEquals(1, result.rules.size)
        assertEquals("First", result.rules.first().name)
        assertTrue(result.warnings.any { it.contains("duplicate") })
    }

    @Test
    fun `regex validation reports a problem instead of throwing`() {
        assertNull(RulePackParser.validateRegex("^140[0-9]{7}${'$'}"))
        assertNotNull(RulePackParser.validateRegex("^140[0-9"))
        assertNotNull(RulePackParser.validateRegex(""))
    }

    @Test
    fun `rule validation rejects unknown enums`() {
        val dto = RulePackRuleDto(
            stableId = "x",
            name = "x",
            category = "OTHER",
            pattern = "140",
            patternType = "NOT_A_TYPE",
            action = "BLOCK",
            simScope = "SIM2",
        )
        assertNotNull(RulePackParser.validate(dto))
    }

    @Test
    fun `a special rule must name a known caller class`() {
        val good = RulePackRuleDto(
            stableId = "x",
            name = "x",
            category = "UNKNOWN_CALLER",
            pattern = "UNKNOWN_CALLER",
            patternType = "SPECIAL",
            action = "BLOCK",
            simScope = "SIM2",
        )
        assertNull(RulePackParser.validate(good))
        assertNotNull(RulePackParser.validate(good.copy(pattern = "SOMETHING_ELSE")))
    }
}
