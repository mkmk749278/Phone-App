package com.dualshield.phone.ui.contacts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.core.search.DialerIndex
import com.dualshield.phone.data.system.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Contacts tab. Reads the device's contacts and nothing else. */
@OptIn(FlowPreview::class)
class ContactsViewModel(private val container: AppContainer) : ViewModel() {

    @Immutable
    data class UiState(
        val contacts: List<Contact> = emptyList(),
        val query: String = "",
        val hasPermission: Boolean = true,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(
        UiState(
            contacts = container.contactsRepository.cachedContacts,
            loading = container.contactsRepository.cachedContacts.isEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")


    /**
     * The filtered list the screen renders.
     *
     * Filtering used to run in the composable body, which meant a full scan of every contact
     * on every recomposition — on the main thread, while the user was typing. It now runs
     * once per settled keystroke on a background dispatcher, and the screen just reads the
     * result.
     */
    val visibleContacts: StateFlow<List<Contact>> =
        combine(
            _state.map { it.contacts }.distinctUntilChanged().map(DialerIndex::build),
            query.debounce(120L).distinctUntilChanged(),
            _state.map { it.contacts }.distinctUntilChanged(),
        ) { index, text, contacts ->
            if (text.isBlank()) {
                contacts
            } else {
                // The same index and the same ranking the dialer uses, so a name searched
                // here and dialled there produces the same order.
                index.search(text, limit = Int.MAX_VALUE)
                    .map { it.contact }
                    .distinctBy { it.id }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** Reloads only when the data is actually old; revisiting a tab should be instant. */
    fun refreshIfStale() {
        if (container.contactsRepository.isContactCacheFresh) return
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.contacts.isEmpty()) _state.update { it.copy(loading = true) }
            val contacts = container.contactsRepository.loadContacts(force = force)
            _state.update {
                it.copy(
                    contacts = contacts,
                    hasPermission = container.contactsRepository.hasPermission(),
                    loading = false,
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        query.value = value
    }

}