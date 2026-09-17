package com.dualshield.phone.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.data.system.SmsMessage
import com.dualshield.phone.ui.actions.ExternalActions
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SimChipRow
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/**
 * A single conversation.
 *
 * The SIM chips sit directly above the composer, so the line a reply will leave from is on
 * screen at the moment the user hits send — not hidden in a menu.
 */
@Composable
fun ConversationScreen(
    conversation: MessagesViewModel.ConversationState,
    sims: List<SimOption>,
    selectedSlot: Int?,
    onSelectSim: (Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onSend: () -> Unit,
    onCall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onActionFailed: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedSim = sims.firstOrNull { it.slotIndex == selectedSlot }
    val isNew = conversation.threadId < 0

    // A sender ID like AXISBK is a real sender but not a reachable line.
    val isCallable = PhoneNumberFormatter.internationalDigits(conversation.address) != null ||
        PhoneNumberNormalizer.normalize(conversation.address).hasDigits

    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = conversation.displayName?.takeIf { it.isNotBlank() }
                    ?: if (isNew) "New message" else Formatting.displayNumber(conversation.address),
                subtitle = selectedSim?.let { "SMS · ${it.display}" },
                onBack = onBack,
                actions = {
                    // The same party, reachable the same ways from wherever you meet them.
                    // Offered only for a number you could actually call: a bank's sender ID
                    // has nothing to dial, and a call button that fails is worse than none.
                    if (!isNew && isCallable) {
                        IconButton(onClick = { onCall(conversation.address) }) {
                            Icon(Icons.Filled.Call, contentDescription = "Call")
                        }
                        if (ExternalActions.canOpenWhatsApp(context, conversation.address)) {
                            IconButton(
                                onClick = {
                                    if (!ExternalActions.openWhatsApp(context, conversation.address)) {
                                        onActionFailed("WhatsApp couldn't be opened for this number.")
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = "WhatsApp")
                            }
                        }
                        IconButton(onClick = { onOpenDetails(conversation.address) }) {
                            Icon(Icons.Filled.Info, contentDescription = "Details")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (isNew) {
                OutlinedTextField(
                    value = conversation.address,
                    onValueChange = onRecipientChange,
                    label = { Text("To") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
            }

            if (conversation.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = if (isNew) "New conversation" else "No messages yet",
                        message = if (isNew) {
                            "Type a number above and write your message."
                        } else {
                            "Messages in this conversation will appear here."
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(
                        items = conversation.messages,
                        key = { it.id },
                        contentType = { "bubble" },
                    ) { message ->
                        MessageBubble(message)
                    }
                }
            }

            SimChipRow(
                options = sims,
                selectedSlot = selectedSlot,
                onSelect = onSelectSim,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = conversation.draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Message") },
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onSend,
                        enabled = conversation.draft.isNotBlank() && !conversation.sending,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = selectedSim
                                ?.let { "Send from ${it.display}" }
                                ?: "Send",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            VerticalSpacer(Spacing.xs)
        }
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    val outgoing = message.outgoing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 17.dp,
                        topEnd = 17.dp,
                        bottomStart = if (outgoing) 17.dp else 5.dp,
                        bottomEnd = if (outgoing) 5.dp else 17.dp,
                    ),
                )
                .background(
                    if (outgoing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .border(
                    width = if (outgoing) 0.dp else 1.dp,
                    color = if (outgoing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(17.dp),
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outgoing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = Formatting.listTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
