package com.dualshield.phone.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.dualshield.phone.ui.incall.InCallActivity

/**
 * The in-call UI, active only while DualShieldPhone holds the default phone app role.
 *
 * Deliberately thin: it forwards Telecom's callbacks into [CallRegistry] and brings the
 * call screen forward. All filtering logic lives in the screening service, which works
 * whether or not we are the default dialer.
 */
class DualShieldInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRegistry.attachService(this)
        CallRegistry.add(call)
        startActivity(
            Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallRegistry.remove(call)
        if (CallRegistry.uiCalls.value.isEmpty()) {
            CallRegistry.attachService(null)
        }
    }

    /**
     * Superseded by `onCallEndpointChanged` on API 34, but that callback does not exist at
     * minSdk 29, and the platform still delivers this one on every supported version.
     */
    @Deprecated("Replaced by onCallEndpointChanged on API 34+")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallRegistry.onAudioStateChanged(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        startActivity(
            Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
