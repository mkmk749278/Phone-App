package com.dualshield.phone.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Receives SMS while DualShieldPhone is the default SMS app.
 *
 * Being the default SMS app makes us responsible for persisting the message, so that is
 * exactly what this does and nothing more. Message filtering is a Phase 3 feature and,
 * when it arrives, it will record what it hid without ever deleting the underlying SMS.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return
        if (messages.isEmpty()) return

        val subscriptionId = intent.getIntExtra("subscription", -1)
        val address = messages.first().displayOriginatingAddress?.trim().orEmpty()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val timestamp = messages.first().timestampMillis

        if (address.isEmpty()) return

        val repository = com.dualshield.phone.DualShieldApplication
            .containerOrNull(context)?.smsRepository
        if (repository == null) {
            Log.w(TAG, "No container available; dropping SMS persistence for this message.")
            return
        }
        repository.persistIncoming(address, body, timestamp, subscriptionId)
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
