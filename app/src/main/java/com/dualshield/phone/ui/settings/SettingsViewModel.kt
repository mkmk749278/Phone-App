package com.dualshield.phone.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.data.db.entity.RulePackMetadataEntity
import com.dualshield.phone.data.repository.AppSettings
import com.dualshield.phone.data.rulepack.RulePackResult
import com.dualshield.phone.core.recording.RecordingCapability
import com.dualshield.phone.telecom.RoleStatus
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings, SIM profiles, rule packs and the privacy page. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** Which runtime permissions are currently held. Re-read whenever Setup is shown. */
    data class Permissions(
        val phone: Boolean = false,
        val contacts: Boolean = false,
        val sms: Boolean = false,
    )

    @Immutable
    data class UiState(
        val settings: AppSettings = AppSettings(),
        val sims: List<SimOption> = emptyList(),
        val packs: List<RulePackMetadataEntity> = emptyList(),
        val roles: RoleStatus = RoleStatus(false, false, false),
        val permissions: Permissions = Permissions(),
        val exportedJson: String? = null,
        /**
         * What this device will let the app capture from a call.
         *
         * Detected lazily rather than at startup: it opens and releases an audio recorder,
         * which is not work worth doing on every launch for a screen most users never open.
         */
        val recordingCapability: RecordingCapability = RecordingCapability.UNAVAILABLE,
        val message: String? = null,
    ) {
        /** Setup is only genuinely finished once Shield can actually screen a call. */
        val readyToProtect: Boolean get() = roles.isCallScreener && permissions.phone
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.simOptionsFlow().collect { sims -> _state.update { it.copy(sims = sims) } }
        }
        viewModelScope.launch {
            container.ruleRepository.observePacks().collect { packs ->
                _state.update { it.copy(packs = packs) }
            }
        }
        refreshRoles()
    }

    /**
     * Re-reads roles and permissions.
     *
     * Called every time Setup or Settings is shown, because the user can change any of this
     * in system settings while the app is in the background and we would otherwise keep
     * showing a stale "not granted".
     */
    /** Detects what this device allows. Called when the call-recording screen opens. */
    fun detectRecordingCapability(force: Boolean = false) {
        viewModelScope.launch {
            val capability = withContext(Dispatchers.IO) {
                if (force) {
                    container.recordingCapabilityManager.redetect()
                } else {
                    container.recordingCapabilityManager.capability()
                }
            }
            _state.update { it.copy(recordingCapability = capability) }
        }
    }

    fun refreshRoles() {
        container.simResolver.invalidate()
        _state.update {
            it.copy(
                roles = container.roleRepository.status(),
                permissions = Permissions(
                    phone = container.simResolver.hasPhoneStatePermission(),
                    contacts = container.contactsRepository.hasPermission(),
                    sms = container.smsRepository.hasReadPermission(),
                ),
            )
        }
    }

    fun setProtectionEnabled(slotIndex: Int, enabled: Boolean) {
        viewModelScope.launch { container.simRepository.setFilteringEnabled(slotIndex, enabled) }
    }

    fun setNotifyOnBlockedCall(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setNotifyOnBlockedCall(enabled) }
    }

    fun setShowSimLabels(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setShowSimLabelsEverywhere(enabled) }
    }

    fun setOnboardingComplete() {
        viewModelScope.launch { container.settingsRepository.setOnboardingComplete(true) }
    }

    /**
     * Answers the one-time SIM policy prompt.
     *
     * [protectionBySlot] is null when the user chose to keep what they had, which is a real
     * answer and is recorded as one: the prompt does not come back either way. The
     * acknowledgement is written last, so a failure partway through means the prompt is
     * asked again rather than silently lost with the settings half applied.
     */
    fun acknowledgeSimPolicy(protectionBySlot: Map<Int, Boolean>? = null) {
        viewModelScope.launch {
            protectionBySlot?.forEach { (slotIndex, enabled) ->
                container.simRepository.setFilteringEnabled(slotIndex, enabled)
            }
            container.settingsRepository.acknowledgeSimPolicy()
        }
    }

    fun setSimLabel(slotIndex: Int, label: String) {
        viewModelScope.launch { container.simRepository.setLabel(slotIndex, label) }
    }

    fun exportRules() {
        viewModelScope.launch {
            val json = runCatching { container.ruleRepository.exportUserRules() }.getOrNull()
            _state.update {
                it.copy(
                    exportedJson = json,
                    message = if (json == null) "Your rules couldn't be exported." else null,
                )
            }
        }
    }

    fun importRules(raw: String) {
        viewModelScope.launch {
            when (val result = container.ruleRepository.importPack(raw, System.currentTimeMillis())) {
                is RulePackResult.Failure ->
                    _state.update { it.copy(message = result.message) }
                is RulePackResult.Success -> {
                    val skipped = result.warnings.size
                    _state.update {
                        it.copy(
                            message = buildString {
                                append("Imported ${result.rules.size} rules")
                                if (skipped > 0) append(", skipped $skipped that weren't valid")
                                append(".")
                            },
                        )
                    }
                }
            }
        }
    }

    fun consumeExport() = _state.update { it.copy(exportedJson = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
