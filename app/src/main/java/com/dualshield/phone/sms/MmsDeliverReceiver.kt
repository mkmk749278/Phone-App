package com.dualshield.phone.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Present so the default-SMS role can be held at all — the platform requires a
 * WAP_PUSH_DELIVER receiver from any candidate SMS app.
 *
 * MMS retrieval is out of scope for this release: it would need network access, which the
 * app deliberately does not have. Incoming MMS is left to the system.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The action is verified even though only the system can send this broadcast:
        // a receiver that acts on any intent it is handed is a habit worth not forming.
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
    }
}
