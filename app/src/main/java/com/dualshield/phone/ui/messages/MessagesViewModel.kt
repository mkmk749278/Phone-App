package com.dualshield.phone.ui.messages

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.data.system.SmsMessage
import com.dualshield.phone.data.system.SmsThread
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

/** Messages tab: the conversation list, one open conversation, and sending. */
@OptIn(FlowPreview::class)
class MessagesViewModel(private val container: AppContainer) : ViewModel() {

    @Immutable
    data class UiState(
        val threads: List<SmsThread> = emptyList(),
        val sims: List<SimOption> = emptyList(),
        val selectedSlot: Int? = null,
        val query: String = "",
        val hasPermission: Boolean = true,
        val blockedMessageCount: Int = 0,
        val loading: Boolean = true,
        val message: String? = null,
    )

    data class ConversationState(
        val threadId: Long = -1,
        val address: String = "",
        val displayName: String? = null,
        val messages: List<SmsMessage> = emptyList(),
        val draft: String = "",
        val sending: Boolean = false,
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            threads = container.smsRepository.cachedThreads,
            loading = container.smsRepository.cachedThreads.isEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _conversation = MutableStateFlow(ConversationState())
    val conversation: StateFlow<ConversationState> = _conversation.asStateFlow()

    private val query = MutableStateFlow("")


    /** Search results, computed off the main thread once the query settles. */
    val filteredThreads: StateFlow<List<SmsThread>> =
        combine(
            _state.map { it.threads }.distinctUntilChanged(),
            query.debounce(120L).distinctUntilChanged(),
        ) { threads, text ->
            val trimmed = text.trim().lowercase()
            if (trimmed.isEmpty()) {
                threads
            } else {
                threads.filter {
                    it.address.contains(trimmed) ||
                        it.snippet.lowercase().contains(trimmed) ||
                        it.displayName?.lowercase()?.contains(trimmed) == true
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            container.vaultRepository.observeBlockedMessageCount().collect { count ->
                _state.update { it.copy(blockedMessageCount = count) }
            }
        }
        refresh()
    }

    /** Reloads only when the data is actually old; revisiting a tab should be instant. */
    fun refreshIfStale() {
        if (container.smsRepository.isThreadCacheFresh) return
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.threads.isEmpty()) _state.update { it.copy(loading = true) }
            val threads = container.smsRepository.threads(force = force) { subId ->
                container.simRepository.slotForSubscriptionId(subId)
            }
            // One cached index, rather than a content-provider query per conversation.
            val nameIndex = container.contactsRepository.cachedNameIndex()
            val named = threads.map { thread ->
                thread.copy(
                    displayName = nameIndex[container.contactsRepository.matchKey(thread.address)],
                )
            }
            _state.update {
                it.copy(
                    threads = named,
                    hasPermission = container.smsRepository.hasReadPermission(),
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

    fun openThread(threadId: Long, addressHint: String?) {
        viewModelScope.launch {
            val known = _state.value.threads.firstOrNull { it.threadId == threadId }
            _conversation.value = ConversationState(
                threadId = threadId,
                address = known?.address ?: addressHint.orEmpty(),
                displayName = known?.displayName,
                loading = threadId >= 0,
            )
            if (threadId < 0) return@launch
            val messages = container.smsRepository.messages(threadId)
            _conversation.update { it.copy(messages = messages, loading = false) }
            container.smsRepository.markThreadRead(threadId)
            refresh()
        }
    }

    fun onDraftChange(value: String) = _conversation.update { it.copy(draft = value) }

    fun onRecipientChange(value: String) = _conversation.update { it.copy(address = value) }

    fun send() {
        val current = _conversation.value
        val body = current.draft.trim()
        if (body.isEmpty() || current.sending) return

        val slot = _state.value.selectedSlot
        val subscriptionId = slot?.let { container.simRepository.subscriptionIdForSlot(it) } ?: -1

        viewModelScope.launch {
            _conversation.update { it.copy(sending = true) }
            val error = container.smsRepository.send(current.address, body, subscriptionId)
            if (error == null) {
                container.smsRepository.invalidateThreadCache()
                val simLabel = _state.value.sims.firstOrNull { it.slotIndex == slot }?.display
                _conversation.update { it.copy(draft = "", sending = false) }
                _state.update {
                    it.copy(message = simLabel?.let { name -> "Sent from $name" })
                }
                if (current.threadId >= 0) openThread(current.threadId, current.address)
            } else {
                _conversation.update { it.copy(sending = false) }
                _state.update { it.copy(message = error) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

}
