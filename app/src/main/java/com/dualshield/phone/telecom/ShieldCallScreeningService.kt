package com.dualshield.phone.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.dualshield.phone.DualShieldApplication
import com.dualshield.phone.core.rules.ShieldDecision

/**
 * The call firewall.
 *
 * Android Telecom binds this for every incoming call once the user grants the call-screening
 * role (or makes us the default phone app). It runs entirely in the background: no Compose
 * screen, no activity, and no dependency on the app being open.
 *
 * The contract with the platform is tight — respond quickly, respond exactly once — so all
 * the real work happens in [com.dualshield.phone.shield.ShieldEngine] against a pre-built
 * in-memory snapshot.
 */
class ShieldCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val outcome = try {
            val app = application as? DualShieldApplication
            val rawNumber = callDetails.handle?.schemeSpecificPart
            app?.container?.shieldEngine?.screen(rawNumber, callDetails.accountHandle)
        } catch (t: Throwable) {
            Log.e(TAG, "Screening threw; allowing the call.", t)
            null
        }

        if (outcome == null || outcome.decision !is ShieldDecision.Block) {
            respondToCall(callDetails, allowResponse())
            return
        }

        Log.i(
            TAG,
            "Blocked a call on slot ${outcome.slotIndex} via rule " +
                "'${(outcome.decision as ShieldDecision.Block).rule.name}'.",
        )
        respondToCall(callDetails, blockResponse())
        notifyBlocked()
    }

    private fun allowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    private fun blockResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            // Best effort only. The platform honours this for the default phone app; for an
            // ordinary screening app many OEMs still write a system call-log entry. The app
            // never claims otherwise, and never edits the platform call log to compensate.
            .setSkipCallLog(true)
            .build()

    private fun notifyBlocked() {
        runCatching {
            (application as? DualShieldApplication)?.container?.shieldNotifier?.onCallBlocked()
        }
    }

    private companion object {
        const val TAG = "ShieldScreening"
    }
}
