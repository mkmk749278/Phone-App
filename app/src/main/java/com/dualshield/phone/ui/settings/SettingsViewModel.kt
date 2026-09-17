package com.dualshield.phone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.data.db.entity.RulePackMetadataEntity
import com.dualshield.phone.data.repository.AppSettings
import com.dualshield.phone.data.rulepack.RulePackResult
import com.dualshield.phone.telecom.RoleStatus
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings, SIM profiles, rule packs and the privacy page. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val sims: List<SimOption> = emptyList(),
        val packs: List<RulePackMetadataEntity> = emptyList(),
        val roles: RoleStatus = RoleStatus(false, false, false),
        val exportedJson: String? = null,
        val message: String? = null,
    )

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

    fun refreshRoles() {
        _state.update { it.copy(roles = container.roleRepository.status()) }
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
