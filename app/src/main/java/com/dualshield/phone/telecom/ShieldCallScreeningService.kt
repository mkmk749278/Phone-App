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
 * The contract with the platform is tight — respond quickly, respond exactly once — so the
 * decision happens in [com.dualshield.phone.shield.ShieldEngine] against pre-warmed memory,
 * with no I/O at all, and the response goes back before anything is written down.
 *
 * Blocking a call never launches an Activity, shows a dialog, takes a wake lock or asks for
 * the display. A call blocked while the phone is locked is meant to leave the screen dark.
 */
class ShieldCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val engine = (application as? DualShieldApplication)?.container?.shieldEngine

        val outcome = try {
            engine?.screen(callDetails.handle?.schemeSpecificPart, callDetails.accountHandle)
        } catch (t: Throwable) {
            Log.e(TAG, "Screening threw; allowing the call.", t)
            null
        }

        val decision = outcome?.decision
        if (decision == null || decision is ShieldDecision.Allow) {
            respondToCall(callDetails, allowResponse())
            return
        }

        if (decision is ShieldDecision.Screen) {
            // Silenced, not rejected. The call still reaches the call log and can be
            // returned; it just does not ring. Behavioural signals are about how someone is
            // calling, not who they are, and that is not enough certainty to refuse a call.
            respondToCall(callDetails, silenceResponse())
            Log.i(TAG, "Silenced a call on slot ${outcome.slotIndex}: ${decision.summary}")
            return
        }

        // Respond first. Everything below this line is bookkeeping, and none of it is
        // allowed to delay the answer Telecom is waiting for or to change it.
        respondToCall(callDetails, blockResponse())

        Log.i(
            TAG,
            "Blocked a call on slot ${outcome.slotIndex} via rule " +
                "'${(decision as ShieldDecision.Block).rule.name}'.",
        )
        engine?.recordBlockedCall(outcome)
        notifyBlocked()
    }

    private fun allowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    /**
     * Silence the ringer while leaving the call alone otherwise.
     *
     * Not `setDisallowCall`: the call is still offered, still written to the call log, and
     * still returnable. The user simply is not interrupted by it.
     */
    private fun silenceResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(true)
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
