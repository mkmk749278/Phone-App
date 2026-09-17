package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message Shield kept out of the inbox view.
 *
 * The underlying SMS is never deleted from the system provider — we only record that we
 * filtered it, so the user can always recover it.
 */
@Entity(
    tableName = "blocked_messages",
    indices = [Index(value = ["timestamp"])],
)
data class BlockedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String?,
    val body: String,
    val timestamp: Long,
    val simSlot: Int,
    val simLabel: String,
    val matchedRuleName: String,
    val reason: String,
)
