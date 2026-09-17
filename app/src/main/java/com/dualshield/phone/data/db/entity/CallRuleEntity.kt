package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope

/**
 * One filtering rule.
 *
 * [stableId] is the identity used by rule packs and by import/export, so re-importing the
 * India pack updates the existing rows instead of duplicating them. [id] is only a local
 * row id.
 */
@Entity(
    tableName = "call_rules",
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["simScope", "enabled"]),
    ],
)
data class CallRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String,
    val name: String,
    val category: RuleCategory,
    val pattern: String,
    val patternType: PatternType,
    val action: RuleAction,
    val simScope: SimScope,
    val enabled: Boolean,
    val priority: Int,
    val confidence: Confidence,
    val provenance: Provenance,
    val description: String,
    val builtIn: Boolean,
    val packId: String? = null,
    val matchCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
