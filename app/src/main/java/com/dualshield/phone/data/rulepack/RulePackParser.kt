package com.dualshield.phone.data.rulepack

import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.model.SpecialRule
import com.dualshield.phone.data.db.entity.CallRuleEntity
import kotlinx.serialization.json.Json

/** Outcome of reading a rule pack, with human-readable problems rather than stack traces. */
sealed interface RulePackResult {
    data class Success(
        val pack: RulePackDto,
        val rules: List<CallRuleEntity>,
        val warnings: List<String>,
    ) : RulePackResult

    data class Failure(val message: String) : RulePackResult
}

/**
 * Parses and validates rule packs.
 *
 * Validation is strict in one specific way: a rule whose pattern cannot be compiled is
 * dropped with a warning rather than imported in a broken state. Shipping a rule that
 * silently never matches is worse than telling the user it was rejected.
 */
object RulePackParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun parse(raw: String, now: Long): RulePackResult {
        val pack = try {
            json.decodeFromString(RulePackDto.serializer(), raw)
        } catch (t: Throwable) {
            return RulePackResult.Failure("This file isn't a valid rule pack. Check the JSON and try again.")
        }

        if (pack.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            return RulePackResult.Failure(
                "This pack needs a newer version of DualShieldPhone (schema ${pack.schemaVersion}).",
            )
        }
        if (pack.packId.isBlank()) {
            return RulePackResult.Failure("This pack has no id, so it can't be tracked or updated.")
        }
        if (pack.rules.isEmpty()) {
            return RulePackResult.Failure("This pack contains no rules.")
        }

        val warnings = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val entities = mutableListOf<CallRuleEntity>()

        for (dto in pack.rules) {
            val problem = validate(dto)
            if (problem != null) {
                warnings += "${dto.name.ifBlank { dto.stableId }}: $problem"
                continue
            }
            if (!seen.add(dto.stableId)) {
                warnings += "${dto.stableId}: duplicate id, later copy ignored."
                continue
            }
            entities += dto.toEntity(pack.packId, now)
        }

        if (entities.isEmpty()) {
            return RulePackResult.Failure("None of the rules in this pack could be used.")
        }
        return RulePackResult.Success(pack, entities, warnings)
    }

    fun encode(pack: RulePackDto): String = json.encodeToString(RulePackDto.serializer(), pack)

    /** @return a user-facing problem description, or null when the rule is fine. */
    fun validate(dto: RulePackRuleDto): String? {
        if (dto.stableId.isBlank()) return "missing id."
        if (dto.name.isBlank()) return "missing name."
        val patternType = enumOrNull<PatternType>(dto.patternType)
            ?: return "unknown pattern type '${dto.patternType}'."
        enumOrNull<RuleAction>(dto.action) ?: return "unknown action '${dto.action}'."
        enumOrNull<SimScope>(dto.simScope) ?: return "unknown SIM scope '${dto.simScope}'."

        return when (patternType) {
            PatternType.REGEX -> validateRegex(dto.pattern)
            PatternType.SPECIAL ->
                if (SpecialRule.fromPattern(dto.pattern) == null) {
                    "'${dto.pattern}' is not a known caller class."
                } else {
                    null
                }
            PatternType.REPEATED_CALL -> "repeated-caller rules aren't supported yet."
            else ->
                if (dto.pattern.none { it.isDigit() }) {
                    "the pattern has no digits to match."
                } else {
                    null
                }
        }
    }

    /** Shared by the rule editor so the UI and the importer agree on what is valid. */
    fun validateRegex(pattern: String): String? {
        if (pattern.isBlank()) return "the pattern is empty."
        return try {
            Regex(pattern)
            null
        } catch (t: Throwable) {
            "this pattern isn't valid. Check the pattern and try again."
        }
    }

    private fun RulePackRuleDto.toEntity(packId: String, now: Long) = CallRuleEntity(
        stableId = stableId,
        name = name,
        category = RuleCategory.fromName(category),
        pattern = pattern,
        patternType = enumOrNull<PatternType>(patternType) ?: PatternType.EXACT,
        action = enumOrNull<RuleAction>(action) ?: RuleAction.BLOCK,
        simScope = enumOrNull<SimScope>(simScope) ?: SimScope.SIM2,
        enabled = enabled,
        priority = priority,
        confidence = enumOrNull<Confidence>(confidence) ?: Confidence.LOW,
        provenance = enumOrNull<Provenance>(provenance) ?: Provenance.COMMUNITY,
        description = description,
        builtIn = true,
        packId = packId,
        createdAt = now,
        updatedAt = now,
    )

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        enumValues<T>().firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
}
