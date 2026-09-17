package com.dualshield.phone.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.data.system.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Contacts tab. Reads the device's contacts and nothing else. */
class ContactsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val contacts: List<Contact> = emptyList(),
        val query: String = "",
        val hasPermission: Boolean = true,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val contacts = container.contactsRepository.loadContacts()
            _state.update {
                it.copy(
                    contacts = contacts,
                    hasPermission = container.contactsRepository.hasPermission(),
                    loading = false,
                )
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun visibleContacts(): List<Contact> =
        container.contactsRepository.search(_state.value.contacts, _state.value.query)

    fun contactById(id: Long): Contact? = _state.value.contacts.firstOrNull { it.id == id }
}
