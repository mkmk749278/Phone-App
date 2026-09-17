package com.dualshield.phone.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SubscriptionManager
import com.dualshield.phone.DualShieldApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles "respond with a message" from the incoming-call screen.
 *
 * Required for the default-SMS role, and genuinely useful: declining a call with a text is
 * one of the few places a phone app must send SMS without any UI.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = intent?.data?.schemeSpecificPart?.trim()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val repository = (application as? DualShieldApplication)?.container?.smsRepository

        if (!recipient.isNullOrBlank() && !body.isNullOrBlank() && repository != null) {
            val subscriptionId = intent.getIntExtra(
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            )
            scope.launch { repository.send(recipient, body, subscriptionId) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
