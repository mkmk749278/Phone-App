package com.dualshield.phone.ui.phone

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.core.search.DialerIndex
import com.dualshield.phone.core.search.DialerResult
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.telecom.CallPlacer
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
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

/** Phone tab: Recents, Favorites, the dial pad, and call details. */
@OptIn(FlowPreview::class)
class PhoneViewModel(private val container: AppContainer) : ViewModel() {

    @Immutable
    data class UiState(
        val recents: List<RecentCall> = emptyList(),
        val contacts: List<Contact> = emptyList(),
        val sims: List<SimOption> = emptyList(),
        val selectedSlot: Int? = null,
        val query: String = "",
        val dialInput: String = "",
        val hasCallLogPermission: Boolean = true,
        /** How many calls Shield has rejected. Drives the single Recents entry. */
        val blockedCallCount: Int = 0,
        val loading: Boolean = true,
        val message: String? = null,
    ) {
        val favorites: List<Contact> get() = contacts.filter { it.starred }
    }

    // Seeded from the shared cache so the first frame paints real rows, not an empty state.
    private val _state = MutableStateFlow(
        UiState(
            recents = container.callLogRepository.cachedRecents,
            contacts = container.contactsRepository.cachedContacts,
            loading = container.callLogRepository.cachedRecents.isEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    private val dialInput = MutableStateFlow("")
    private val detailNumber = MutableStateFlow("")

    /**
     * Bumped after a block or unblock so the details screen re-reads the rule state.
     *
     * The rules live in Room and the details flow is a plain combine over in-memory state;
     * this is the nudge that keeps the Block/Unblock row honest without observing the whole
     * rule table for one number.
     */
    private val blockedRefresh = MutableStateFlow(0)


    /**
     * Recents filtered by the search box.
     *
     * Filtering is a background job keyed off a debounced query rather than a getter on
     * UiState: as a getter it re-scanned the whole call log every time Compose read the
     * state, which is several times per frame while typing.
     */
    val filteredRecents: StateFlow<List<RecentCall>> =
        combine(
            _state.map { it.recents }.distinctUntilChanged(),
            query.debounce(120L).distinctUntilChanged(),
        ) { recents, text ->
            val trimmed = text.trim().lowercase()
            if (trimmed.isEmpty()) {
                recents
            } else {
                recents.filter {
                    it.displayName?.lowercase()?.contains(trimmed) == true ||
                        it.number.contains(trimmed)
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The search index, rebuilt only when the address book itself changes.
     *
     * Rebuilding per keystroke is what made the old dialer feel heavy; the index does all
     * the string work once so that typing a digit is a scan of precomputed values.
     */
    private val dialerIndex: StateFlow<DialerIndex> =
        _state.map { it.contacts }
            .distinctUntilChanged()
            .map(DialerIndex::build)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialerIndex.EMPTY)

    /**
     * Contact matches for whatever is currently typed on the keypad.
     *
     * Search starts at the first digit rather than the second: the whole point of T9 is that
     * the list narrows as you go, and waiting for a second keypress makes the first one feel
     * like it did nothing.
     */
    val dialSuggestions: StateFlow<List<DialerResult>> =
        combine(
            dialerIndex,
            dialInput.debounce(60L).distinctUntilChanged(),
        ) { index, input ->
            if (input.isEmpty()) emptyList() else index.search(input, limit = DIAL_RESULT_LIMIT)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything the call-details screen needs for one number. */
    data class CallDetails(
        val number: String = "",
        val contact: Contact? = null,
        val history: List<RecentCall> = emptyList(),
        /** True when the user's own block covers this number on at least one SIM. */
        val isBlocked: Boolean = false,
    ) {
        val photoUri: String? get() = contact?.photoUri
        val contactId: Long? get() = contact?.id
    }

    /**
     * Resolved off the main thread, instead of scanning the call log inside the composable
     * body on every recomposition as the first version did.
     */
    val callDetails: StateFlow<CallDetails> =
        combine(_state, detailNumber, blockedRefresh) { state, number, _ ->
            if (number.isBlank()) return@combine CallDetails()
            val key = PhoneNumberFormatter.matchKey(number)
            if (key.isEmpty()) return@combine CallDetails(number)
            CallDetails(
                number = number,
                contact = state.contacts.firstOrNull { contact ->
                    contact.phoneNumbers.any { it.matchKey == key }
                },
                history = state.recents.filter { it.canonicalNumber.isNotEmpty() && it.matches(key) },
                isBlocked = container.ruleRepository.userBlockRulesFor(number).isNotEmpty(),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallDetails())

    private fun RecentCall.matches(key: String): Boolean =
        PhoneNumberFormatter.matchKey(number) == key

    fun openDetails(number: String) {
        detailNumber.value = number
    }

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
        viewModelScope.launch {
            container.vaultRepository.observeBlockedCallCount().collect { count ->
                _state.update { it.copy(blockedCallCount = count) }
            }
        }
        refresh()
    }

    /**
     * Reloads only when the data is actually old.
     *
     * Every visit to the Phone tab used to trigger a full call-log and contacts read, which
     * is a visible stall on a phone with a long history and no benefit when nothing changed.
     */
    fun refreshIfStale() {
        if (container.callLogRepository.isCacheFresh &&
            container.contactsRepository.isContactCacheFresh
        ) {
            return
        }
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            // Only show the loading state when there is nothing cached to show instead.
            if (_state.value.recents.isEmpty()) _state.update { it.copy(loading = true) }
            // Contacts first: Recents resolves each caller's name and photo through this
            // index as it is built, so a row never renders with a number and then pops to a
            // name a frame later.
            val contactIndex = container.contactsRepository.contactIndex(force = force)
            val contacts = container.contactsRepository.cachedContacts
            val recents = container.callLogRepository.recentCalls(
                force = force,
                contacts = contactIndex,
                slotForAccountId = { accountId ->
                    container.simRepository.slotForPhoneAccountId(accountId)
                },
                subscriptionIdForSlot = { slot ->
                    container.simRepository.subscriptionIdForSlot(slot).takeIf { it >= 0 }
                },
            )
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

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        query.value = value
    }

    fun onSelectSim(slotIndex: Int) = _state.update { it.copy(selectedSlot = slotIndex) }

    // ------------------------------------------------------------------ dial pad

    fun onDigit(digit: Char) = setDialInput(_state.value.dialInput + digit)

    fun onBackspace() = setDialInput(_state.value.dialInput.dropLast(1))

    fun onClearDial() = setDialInput("")

    /** Long-pressing zero types the international prefix, as every dialer does. */
    fun onZeroLongPress() = setDialInput(_state.value.dialInput.dropLast(1) + "+")

    fun setDialInput(value: String) {
        _state.update { it.copy(dialInput = value) }
        dialInput.value = value
    }

    fun call(number: String) = call(number, _state.value.selectedSlot)

    /**
     * Places a call on a specific SIM.
     *
     * The slot is passed explicitly rather than read from the selection, because the action
     * sheet and Call details offer a line per SIM: the user picked a line by pressing that
     * button, and it must be the one used.
     */
    fun call(number: String, slot: Int?) {
        when (val result = container.callPlacer.placeCall(number, slot)) {
            is CallPlacer.Result.Placed -> {
                // The new call will not be in the log yet; make sure we re-read once it is.
                container.callLogRepository.invalidateCache()
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
                blockedRefresh.update { it + 1 }
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

    /** Removes the user's own block on a number. Broader rules are left alone. */
    fun unblockNumber(number: String) {
        viewModelScope.launch {
            runCatching { container.ruleRepository.unblockNumber(number) }
                .onSuccess { removed ->
                    blockedRefresh.update { it + 1 }
                    _state.update {
                        it.copy(
                            message = if (removed > 0) {
                                "Unblocked"
                            } else {
                                "This number is blocked by a Shield rule. Manage it in Settings."
                            },
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(message = "That number couldn't be unblocked.") }
                }
        }
    }

    /** Shows a one-off message raised by a screen, such as a failed hand-off to another app. */
    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }


    private fun scopeLabel(scope: SimScope): String {
        val sims = _state.value.sims
        return when (scope) {
            SimScope.BOTH -> "both SIMs"
            SimScope.SIM1 -> sims.firstOrNull { it.slotIndex == 0 }?.display ?: "SIM 1"
            SimScope.SIM2 -> sims.firstOrNull { it.slotIndex == 1 }?.display ?: "SIM 2"
        }
    }

    private companion object {
        /**
         * How many dialer results to keep.
         *
         * Matched to the fixed-height results area above the keypad: enough that scrolling
         * finds a deeper match, few enough that the list is never a wall of near-misses.
         */
        const val DIAL_RESULT_LIMIT = 12
    }
}
