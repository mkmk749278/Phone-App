package com.dualshield.phone.telecom

import android.annotation.SuppressLint
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The subset of a Telecom [Call] the in-call UI actually renders. */
data class UiCall(
    val id: String,
    val number: String?,
    val displayName: String?,
    val state: Int,
    val slotIndex: Int?,
    val simLabel: String?,
    val connectTimeMillis: Long,
) {
    val isRinging: Boolean get() = state == Call.STATE_RINGING
    val isActive: Boolean get() = state == Call.STATE_ACTIVE
    val isDialing: Boolean
        get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    val isOnHold: Boolean get() = state == Call.STATE_HOLDING
}

/**
 * Bridges Telecom's callback-based [Call] objects to a Compose-friendly state flow.
 *
 * A process-wide singleton because [DualShieldInCallService] is constructed by the system,
 * not by us, and the in-call activity has to find the same call list it publishes.
 */
@SuppressLint("StaticFieldLeak")
object CallRegistry {

    private val calls = LinkedHashMap<String, Call>()
    private val callbacks = HashMap<String, Call.Callback>()
    private val simLabelResolver = MutableStateFlow<(android.telecom.PhoneAccountHandle?) -> Pair<Int?, String?>> { null to null }

    private val _uiCalls = MutableStateFlow<List<UiCall>>(emptyList())
    val uiCalls: StateFlow<List<UiCall>> = _uiCalls.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    /**
     * The bound [InCallService], used for the audio-route controls.
     *
     * Process-wide by necessity: the system constructs the service and the in-call activity
     * has to reach the same instance. The reference is cleared in `onCallRemoved` as soon as
     * the last call ends, so it never outlives the call it belongs to.
     */
    @Volatile
    private var service: InCallService? = null

    fun attachService(inCallService: InCallService?) {
        service = inCallService
    }

    /** Lets the app teach the registry how to turn a phone account into a SIM label. */
    fun setSimLabelResolver(resolver: (android.telecom.PhoneAccountHandle?) -> Pair<Int?, String?>) {
        simLabelResolver.value = resolver
        publish()
    }

    fun add(call: Call) {
        val id = call.identity()
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) = publish()
            override fun onDetailsChanged(c: Call, details: Call.Details) = publish()
        }
        calls[id] = call
        callbacks[id] = callback
        call.registerCallback(callback)
        publish()
    }

    fun remove(call: Call) {
        val id = call.identity()
        callbacks.remove(id)?.let { runCatching { call.unregisterCallback(it) } }
        calls.remove(id)
        publish()
    }

    fun onAudioStateChanged(state: CallAudioState?) {
        _muted.value = state?.isMuted ?: false
        _speakerOn.value = state?.route == CallAudioState.ROUTE_SPEAKER
    }

    // ------------------------------------------------------------------ actions

    fun answer(id: String) = withCall(id) {
        it.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject(id: String) = withCall(id) { it.reject(false, null) }

    fun hangUp(id: String) = withCall(id) { it.disconnect() }

    fun hold(id: String) = withCall(id) { it.hold() }

    fun unhold(id: String) = withCall(id) { it.unhold() }

    fun playDtmf(id: String, digit: Char) = withCall(id) {
        it.playDtmfTone(digit)
        it.stopDtmfTone()
    }

    fun toggleMute() {
        val next = !_muted.value
        runCatching { service?.setMuted(next) }
        _muted.value = next
    }

    /**
     * Toggles the speaker.
     *
     * `setAudioRoute` is soft-deprecated in favour of `requestCallEndpointChange`, which
     * only exists from API 34. Until minSdk catches up this is the one call that works
     * across every supported version.
     */
    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val next = !_speakerOn.value
        runCatching {
            service?.setAudioRoute(
                if (next) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE,
            )
        }
        _speakerOn.value = next
    }

    private inline fun withCall(id: String, action: (Call) -> Unit) {
        runCatching { calls[id]?.let(action) }
    }

    private fun publish() {
        val resolve = simLabelResolver.value
        _uiCalls.value = calls.values.map { call ->
            val details = call.details
            val (slot, label) = runCatching { resolve(details.accountHandle) }
                .getOrDefault(null to null)
            UiCall(
                id = call.identity(),
                number = details.handle?.schemeSpecificPart,
                displayName = details.callerDisplayName?.takeIf { it.isNotBlank() },
                state = call.currentState(),
                slotIndex = slot,
                simLabel = label,
                connectTimeMillis = details.connectTimeMillis,
            )
        }
    }

    private fun Call.identity(): String =
        details.handle?.toString().orEmpty() + "#" + System.identityHashCode(this)

    /** `Call.getState()` moved onto `Call.Details` in API 31; both are needed at minSdk 29. */
    private fun Call.currentState(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            details.state
        } else {
            @Suppress("DEPRECATION")
            state
        }
}
