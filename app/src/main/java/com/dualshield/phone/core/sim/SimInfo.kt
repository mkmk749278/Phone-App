package com.dualshield.phone.core.sim

import android.telecom.PhoneAccountHandle

/**
 * One physical SIM, as the system actually reports it.
 *
 * [slotIndex] is the only thing rules key off. It is read from
 * `SubscriptionInfo.simSlotIndex` and never inferred from [subscriptionId] — the two are
 * unrelated numbers, and assuming otherwise is the classic dual-SIM bug.
 */
data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val carrierName: String,
    val displayName: String,
    val phoneNumber: String?,
    val phoneAccountHandle: PhoneAccountHandle?,
) {
    /** Human-facing "SIM 1" / "SIM 2". */
    val slotLabel: String get() = "SIM ${slotIndex + 1}"
}
