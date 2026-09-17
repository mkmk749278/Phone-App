package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dualshield.phone.core.model.RuleCategory

/**
 * A Shield Vault record: one call that Shield rejected.
 *
 * These are written *before* the call is rejected, so a crash between the two leaves an
 * explainable record rather than a silent disappearance. Vault records are the only place
 * blocked calls appear — they are deliberately kept out of the app's Recents.
 */
@Entity(
    tableName = "blocked_calls",
    indices = [Index(value = ["timestamp"]), Index(value = ["normalizedNumber"])],
)
data class BlockedCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String?,
    val timestamp: Long,
    val simSlot: Int,
    val subscriptionId: Int,
    val simLabel: String,
    val matchedRuleId: Long?,
    val matchedRuleStableId: String?,
    val matchedRuleName: String,
    val category: RuleCategory,
    val reason: String,
)
