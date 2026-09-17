package com.dualshield.phone.core.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Maps between Telecom's [PhoneAccountHandle] world and the SIM slots the user sees.
 *
 * Every public method here is defensive. When the platform will not tell us which SIM a
 * call arrived on, the answer is `null`, and the rule engine turns that into "allow". A
 * wrong guess would mean blocking a duty-line call, which is the single worst failure this
 * app can have.
 */
class SimResolver(private val context: Context) {

    private val telecomManager: TelecomManager?
        get() = context.getSystemService(TelecomManager::class.java)

    private val telephonyManager: TelephonyManager?
        get() = context.getSystemService(TelephonyManager::class.java)

    private val subscriptionManager: SubscriptionManager?
        get() = context.getSystemService(SubscriptionManager::class.java)

    fun hasPhoneStatePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Active SIMs ordered by physical slot.
     *
     * Returns an empty list rather than throwing when the permission is missing, the
     * device has no telephony, or the platform simply refuses.
     */
    // `SubscriptionInfo.number` is deprecated with no replacement below API 33, and is only
    // ever used here as a cosmetic subtitle.
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun activeSims(): List<SimInfo> {
        if (!hasPhoneStatePermission()) return emptyList()
        val subscriptions = runCatching {
            subscriptionManager?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()

        val accountsBySubId = callCapableAccountsBySubscriptionId()

        return subscriptions
            .mapNotNull { sub ->
                val slot = sub.simSlotIndex
                if (slot < 0) return@mapNotNull null
                SimInfo(
                    slotIndex = slot,
                    subscriptionId = sub.subscriptionId,
                    carrierName = sub.carrierName?.toString().orEmpty(),
                    displayName = sub.displayName?.toString().orEmpty(),
                    phoneNumber = runCatching { sub.number }.getOrNull()?.takeIf { it.isNotBlank() },
                    phoneAccountHandle = accountsBySubId[sub.subscriptionId],
                )
            }
            .sortedBy { it.slotIndex }
    }

    /**
     * Resolves the SIM slot a call belongs to.
     *
     * Order of attempts:
     *  1. `TelephonyManager.getSubscriptionId(handle)` — the only officially supported
     *     mapping, available from API 30.
     *  2. Treating the handle id as a subscription id, which is what AOSP's own telephony
     *     stack uses, then matching it against the active subscription list.
     *  3. Matching the handle id against each subscription's ICC id.
     *
     * @return zero-based slot index, or null when we genuinely do not know.
     */
    @SuppressLint("MissingPermission")
    fun resolveSlotIndex(handle: PhoneAccountHandle?): Int? {
        if (handle == null) return null
        if (!hasPhoneStatePermission()) return null

        val subscriptions = runCatching {
            subscriptionManager?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()
        if (subscriptions.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val subId = runCatching { telephonyManager?.getSubscriptionId(handle) }.getOrNull()
            if (subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subscriptions.firstOrNull { it.subscriptionId == subId }
                    ?.takeIf { it.simSlotIndex >= 0 }
                    ?.let { return it.simSlotIndex }
            }
        }

        val handleId = handle.id
        handleId.toIntOrNull()?.let { asSubId ->
            subscriptions.firstOrNull { it.subscriptionId == asSubId }
                ?.takeIf { it.simSlotIndex >= 0 }
                ?.let { return it.simSlotIndex }
        }

        subscriptions.firstOrNull { sub ->
            val iccId = runCatching { sub.iccId }.getOrNull()
            !iccId.isNullOrBlank() && handleId.contains(iccId)
        }?.takeIf { it.simSlotIndex >= 0 }?.let { return it.simSlotIndex }

        Log.w(TAG, "Unable to resolve SIM slot for phone account; allowing call.")
        return null
    }

    /** The Telecom account to place an outgoing call with, for a given slot. */
    fun phoneAccountHandleForSlot(slotIndex: Int): PhoneAccountHandle? =
        activeSims().firstOrNull { it.slotIndex == slotIndex }?.phoneAccountHandle

    @SuppressLint("MissingPermission")
    private fun callCapableAccountsBySubscriptionId(): Map<Int, PhoneAccountHandle> {
        val accounts = runCatching {
            telecomManager?.callCapablePhoneAccounts
        }.getOrNull().orEmpty()
        if (accounts.isEmpty()) return emptyMap()

        val result = LinkedHashMap<Int, PhoneAccountHandle>()
        for (account in accounts) {
            val phoneAccount = runCatching { telecomManager?.getPhoneAccount(account) }.getOrNull()
            if (phoneAccount != null &&
                !phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            ) {
                continue
            }
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { telephonyManager?.getSubscriptionId(account) }.getOrNull()
            } else {
                null
            } ?: account.id.toIntOrNull() ?: continue
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) continue
            result.putIfAbsent(subId, account)
        }
        return result
    }

    private companion object {
        const val TAG = "SimResolver"
    }
}
