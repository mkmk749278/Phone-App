package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-facing SIM profile, keyed by physical slot.
 *
 * Slot is the primary key rather than subscription id on purpose: subscription ids change
 * when a SIM is re-inserted or the device reboots on some OEMs, and the user's mental model
 * ("SIM 1 is my duty line") is tied to the slot, not to a transient id.
 */
@Entity(tableName = "sim_profiles")
data class SimProfileEntity(
    @PrimaryKey val slotIndex: Int,
    val label: String,
    val filteringEnabled: Boolean,
    /**
     * Saved contacts bypass every block rule on this SIM.
     *
     * On by default. A broad prefix rule is far more likely to catch someone the user knows
     * than to be worth the false positive.
     */
    val allowContacts: Boolean = true,
    val subscriptionId: Int = -1,
    val carrierName: String = "",
    val lastSeenAt: Long = 0L,
)
