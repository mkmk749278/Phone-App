package com.dualshield.phone.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.telecom.CallPlacer
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone tab: Recents, Favorites, the dial pad, and call details. */
class PhoneViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val recents: List<RecentCall> = emptyList(),
        val contacts: List<Contact> = emptyList(),
        val sims: List<SimOption> = emptyList(),
        val selectedSlot: Int? = null,
        val query: String = "",
        val dialInput: String = "",
        val hasCallLogPermission: Boolean = true,
        val loading: Boolean = true,
        val message: String? = null,
    ) {
        val favorites: List<Contact> get() = contacts.filter { it.starred }

        /** Recents filtered by the search box, matching name or number. */
        val filteredRecents: List<RecentCall>
            get() {
                val trimmed = query.trim().lowercase()
                if (trimmed.isEmpty()) return recents
                return recents.filter {
                    it.displayName?.lowercase()?.contains(trimmed) == true ||
                        it.number.contains(trimmed)
                }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.simOptionsFlow().collect { sims ->
                _state.update { current ->
                    current.copy(
                        sims = sims,
                        selectedSlot = current.selectedSlot
                            ?: sims.firstOrNull { it.present }?.slotIndex,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val recents = container.callLogRepository.recentCalls { accountId ->
                container.simRepository.slotForPhoneAccountId(accountId)
            }
            val contacts = container.contactsRepository.loadContacts()
            _state.update {
                it.copy(
                    recents = recents,
                    contacts = contacts,
                    hasCallLogPermission = container.callLogRepository.hasPermission(),
                    loading = false,
                )
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onSelectSim(slotIndex: Int) = _state.update { it.copy(selectedSlot = slotIndex) }

    // ------------------------------------------------------------------ dial pad

    fun onDigit(digit: Char) = _state.update { it.copy(dialInput = it.dialInput + digit) }

    fun onBackspace() = _state.update { it.copy(dialInput = it.dialInput.dropLast(1)) }

    fun onClearDial() = _state.update { it.copy(dialInput = "") }

    /** Long-pressing zero types the international prefix, as every dialer does. */
    fun onZeroLongPress() = _state.update {
        it.copy(dialInput = it.dialInput.dropLast(1) + "+")
    }

    fun setDialInput(value: String) = _state.update { it.copy(dialInput = value) }

    /** Local, offline contact matches for whatever is currently typed on the keypad. */
    fun dialSuggestions(): List<Contact> {
        val input = _state.value.dialInput
        if (input.length < 2) return emptyList()
        return container.contactsRepository
            .search(_state.value.contacts, input)
            .take(6)
    }

    fun call(number: String) {
        val slot = _state.value.selectedSlot
        when (val result = container.callPlacer.placeCall(number, slot)) {
            is CallPlacer.Result.Placed -> {
                val label = _state.value.sims.firstOrNull { it.slotIndex == slot }?.display
                _state.update {
                    it.copy(message = label?.let { name -> "Calling with $name" })
                }
            }
            is CallPlacer.Result.Failed ->
                _state.update { it.copy(message = result.message) }
        }
    }

    fun blockNumber(number: String, displayName: String?, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.blockNumber(
                    rawNumber = number,
                    displayName = displayName,
                    scope = scope,
                    now = System.currentTimeMillis(),
                )
            }.onSuccess {
                _state.update { it.copy(message = "Blocked on ${scopeLabel(scope)}") }
            }.onFailure {
                _state.update { it.copy(message = "That number couldn't be blocked.") }
            }
        }
    }

    fun allowNumber(number: String, displayName: String?, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.allowNumber(
                    rawNumber = number,
                    displayName = displayName,
                    scope = scope,
                    now = System.currentTimeMillis(),
                )
            }.onSuccess {
                _state.update { it.copy(message = "Always allowed on ${scopeLabel(scope)}") }
            }.onFailure {
                _state.update { it.copy(message = "That number couldn't be allowed.") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Call history for one number, used by the call-details screen. */
    fun historyFor(number: String): List<RecentCall> {
        val digits = number.filter { it.isDigit() }.takeLast(10)
        if (digits.isEmpty()) return emptyList()
        return _state.value.recents.filter { it.number.filter(Char::isDigit).endsWith(digits) }
    }

    fun contactFor(number: String): Contact? {
        val digits = number.filter { it.isDigit() }.takeLast(10)
        if (digits.isEmpty()) return null
        return _state.value.contacts.firstOrNull { contact ->
            contact.phoneNumbers.any { it.filter(Char::isDigit).endsWith(digits) }
        }
    }

    private fun scopeLabel(scope: SimScope): String {
        val sims = _state.value.sims
        return when (scope) {
            SimScope.BOTH -> "both SIMs"
            SimScope.SIM1 -> sims.firstOrNull { it.slotIndex == 0 }?.display ?: "SIM 1"
            SimScope.SIM2 -> sims.firstOrNull { it.slotIndex == 1 }?.display ?: "SIM 2"
        }
    }
}
