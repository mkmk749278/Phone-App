package com.dualshield.phone.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.data.system.SmsMessage
import com.dualshield.phone.data.system.SmsThread
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Messages tab: the conversation list, one open conversation, and sending. */
class MessagesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val threads: List<SmsThread> = emptyList(),
        val sims: List<SimOption> = emptyList(),
        val selectedSlot: Int? = null,
        val query: String = "",
        val hasPermission: Boolean = true,
        val blockedMessageCount: Int = 0,
        val loading: Boolean = true,
        val message: String? = null,
    ) {
        val filteredThreads: List<SmsThread>
            get() {
                val trimmed = query.trim().lowercase()
                if (trimmed.isEmpty()) return threads
                return threads.filter {
                    it.address.contains(trimmed) ||
                        it.snippet.lowercase().contains(trimmed) ||
                        it.displayName?.lowercase()?.contains(trimmed) == true
                }
            }
    }

    data class ConversationState(
        val threadId: Long = -1,
        val address: String = "",
        val displayName: String? = null,
        val messages: List<SmsMessage> = emptyList(),
        val draft: String = "",
        val sending: Boolean = false,
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _conversation = MutableStateFlow(ConversationState())
    val conversation: StateFlow<ConversationState> = _conversation.asStateFlow()

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

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val threads = container.smsRepository.threads { subId ->
                container.simRepository.slotForSubscriptionId(subId)
            }
            val named = threads.map { thread ->
                thread.copy(
                    displayName = container.contactsRepository.displayNameFor(thread.address),
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

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

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
