package com.dualshield.phone.data.rulepack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for a rule pack, used for both the bundled India pack and user import/export.
 *
 * Every field that can sensibly default does, so a hand-written pack stays readable and a
 * future schema addition does not invalidate existing files.
 */
@Serializable
data class RulePackDto(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: Int,
    val country: String = "IN",
    val source: String = "",
    val notes: String = "",
    val rules: List<RulePackRuleDto> = emptyList(),
)

@Serializable
data class RulePackRuleDto(
    @SerialName("id") val stableId: String,
    val name: String,
    val category: String,
    val pattern: String,
    val patternType: String,
    val action: String,
    val simScope: String,
    val enabled: Boolean = false,
    val priority: Int = 500,
    val confidence: String = "LOW",
    val provenance: String = "COMMUNITY",
    val description: String = "",
)
