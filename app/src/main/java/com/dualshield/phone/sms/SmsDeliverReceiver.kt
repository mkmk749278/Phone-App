package com.dualshield.phone.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.dualshield.phone.DualShieldApplication

/**
 * Receives SMS while DualShieldPhone is the default SMS app.
 *
 * Being the default SMS app makes us responsible for persisting the message, so that is the
 * first thing this does. Shield then decides whether the message deserves the user's
 * attention.
 *
 * A filtered message is still written to the system inbox — marked read so it raises no
 * notification — and recorded in the Vault. Shield hides messages; it never destroys them,
 * so a false positive costs the user a trip to the Vault rather than a lost message.
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

        val container = DualShieldApplication.containerOrNull(context)
        if (container == null) {
            Log.w(TAG, "No container available; dropping SMS persistence for this message.")
            return
        }

        val filtered = runCatching {
            container.shieldEngine.screenMessage(address, body, timestamp, subscriptionId)
        }.getOrDefault(false)

        container.smsRepository.persistIncoming(
            address = address,
            body = body,
            timestamp = timestamp,
            subscriptionId = subscriptionId,
            markRead = filtered,
        )
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
