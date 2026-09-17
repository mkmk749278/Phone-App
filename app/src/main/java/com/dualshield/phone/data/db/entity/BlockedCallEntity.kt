package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dualshield.phone.core.model.RuleCategory

/**
 * One call that Shield rejected.
 *
 * Written *after* the call has already been rejected, off the screening path: storage does
 * not get a vote on whether a block happens. Losing a record means losing an audit row, not
 * letting an unwanted call through.
 *
 * These records are the only place blocked calls appear. They are deliberately kept out of
 * Recents, which stays a plain history of calls that actually happened.
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
