package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dualshield.phone.core.model.SimScope

/**
 * An explicit "never block this number" entry.
 *
 * Deliberately a separate table from [CallRuleEntity]: the allowlist is the user's safety
 * net against their own over-broad rules, and keeping it separate makes it impossible for a
 * rule-pack import or a bulk rule edit to disturb it.
 */
@Entity(
    tableName = "allow_rules",
    indices = [Index(value = ["normalizedNumber", "simScope"], unique = true)],
)
data class AllowRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedNumber: String,
    val displayName: String?,
    val simScope: SimScope,
    val createdAt: Long,
)
