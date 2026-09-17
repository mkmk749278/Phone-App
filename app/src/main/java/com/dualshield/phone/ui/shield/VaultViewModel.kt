package com.dualshield.phone.ui.shield

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.db.entity.BlockedMessageEntity
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shield Vault.
 *
 * Clearing the Vault removes records only. Rules, the allowlist and SIM settings survive,
 * and the UI says so before the user confirms.
 */
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    @Immutable
    data class UiState(
        val blockedCalls: List<BlockedCallEntity> = emptyList(),
        val blockedMessages: List<BlockedMessageEntity> = emptyList(),
        val sims: List<SimOption> = emptyList(),
        val selectedIds: Set<Long> = emptySet(),
        val message: String? = null,
    ) {
        val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.vaultRepository.observeBlockedCalls().collect { calls ->
                _state.update { it.copy(blockedCalls = calls) }
            }
        }
        viewModelScope.launch {
            container.vaultRepository.observeBlockedMessages().collect { messages ->
                _state.update { it.copy(blockedMessages = messages) }
            }
        }
        viewModelScope.launch {
            container.simOptionsFlow().collect { sims -> _state.update { it.copy(sims = sims) } }
        }
    }

    fun record(id: Long): BlockedCallEntity? =
        _state.value.blockedCalls.firstOrNull { it.id == id }

    fun toggleSelection(id: Long) = _state.update { current ->
        current.copy(
            selectedIds = if (id in current.selectedIds) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            },
        )
    }

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.vaultRepository.deleteBlockedCalls(ids)
            _state.update {
                it.copy(selectedIds = emptySet(), message = "${ids.size} records deleted")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            container.vaultRepository.deleteBlockedCall(id)
            _state.update { it.copy(message = "Record deleted") }
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            container.vaultRepository.clearBlockedCalls()
            _state.update { it.copy(selectedIds = emptySet(), message = "Vault cleared. Your rules are unchanged.") }
        }
    }

    fun allow(record: BlockedCallEntity, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.allowNumber(
                    rawNumber = record.rawNumber.ifBlank { record.normalizedNumber },
                    displayName = record.displayName,
                    scope = scope,
                    now = System.currentTimeMillis(),
                )
            }.onSuccess {
                _state.update { it.copy(message = "This number will always ring through") }
            }.onFailure {
                _state.update { it.copy(message = "That number couldn't be allowed.") }
            }
        }
    }

    fun blockAlways(record: BlockedCallEntity, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.blockNumber(
                    rawNumber = record.rawNumber.ifBlank { record.normalizedNumber },
                    displayName = record.displayName,
                    scope = scope,
                    now = System.currentTimeMillis(),
                )
            }.onSuccess { _state.update { it.copy(message = "Number blocked") } }
                .onFailure { _state.update { it.copy(message = "That number couldn't be blocked.") } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
