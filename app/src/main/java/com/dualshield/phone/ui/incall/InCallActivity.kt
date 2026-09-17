package com.dualshield.phone.ui.incall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualshield.phone.telecom.CallRegistry
import com.dualshield.phone.ui.theme.DualShieldTheme

/**
 * Hosts the in-call UI.
 *
 * Only ever launched by [com.dualshield.phone.telecom.DualShieldInCallService], and it
 * finishes itself the moment the last call goes away so it never lingers in the task list.
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DualShieldTheme {
                val calls by CallRegistry.uiCalls.collectAsStateWithLifecycle()
                val muted by CallRegistry.muted.collectAsStateWithLifecycle()
                val speakerOn by CallRegistry.speakerOn.collectAsStateWithLifecycle()

                // Prefer whatever needs the user's attention: a ringing call first, then the
                // active one.
                val call = calls.firstOrNull { it.isRinging } ?: calls.firstOrNull()

                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) finish()
                }

                InCallScreen(
                    call = call,
                    muted = muted,
                    speakerOn = speakerOn,
                    onAnswer = { call?.let { CallRegistry.answer(it.id) } },
                    onReject = { call?.let { CallRegistry.reject(it.id) } },
                    onHangUp = { call?.let { CallRegistry.hangUp(it.id) } },
                    onToggleMute = { CallRegistry.toggleMute() },
                    onToggleSpeaker = { CallRegistry.toggleSpeaker() },
                )
            }
        }
    }
}
