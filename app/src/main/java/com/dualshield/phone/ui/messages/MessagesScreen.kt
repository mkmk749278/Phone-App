package com.dualshield.phone.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.StatusPill
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.LocalShieldColors

/** The conversation list. A plain SMS app, with one quiet Vault entry at the bottom. */
@Composable
fun MessagesScreen(
    state: MessagesViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onOpenThread: (Long, String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shieldColors = LocalShieldColors.current
    val threads = state.filteredThreads

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.Edit, contentDescription = "New message")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Messages", style = MaterialTheme.typography.displaySmall) }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search messages",
                )
            }

            if (threads.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.hasPermission) {
                            "No messages yet"
                        } else {
                            "Messages needs SMS access"
                        },
                        message = if (state.hasPermission) {
                            "Conversations will appear here."
                        } else {
                            "Allow SMS so DualShieldPhone can show your conversations."
                        },
                    )
                }
            } else {
                item {
                    SectionCard {
                        threads.forEachIndexed { index, thread ->
                            AppListRow(
                                title = thread.displayName?.takeIf { it.isNotBlank() }
                                    ?: Formatting.displayNumber(thread.address),
                                subtitle = thread.snippet,
                                leading = {
                                    ContactAvatar(thread.displayName, thread.address)
                                },
                                trailing = {
                                    Text(
                                        text = Formatting.listTimestamp(thread.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { onOpenThread(thread.threadId, thread.address) },
                            )
                            if (index != threads.lastIndex) RowDivider()
                        }
                    }
                }
            }

            item {
                SectionCard {
                    AppListRow(
                        title = "Shield Vault",
                        subtitle = if (state.blockedMessageCount == 0) {
                            "No messages filtered"
                        } else {
                            "${state.blockedMessageCount} filtered messages"
                        },
                        leading = {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = shieldColors.blocked,
                            )
                        },
                        trailing = {
                            if (state.blockedMessageCount > 0) {
                                StatusPill(
                                    text = state.blockedMessageCount.toString(),
                                    containerColor = shieldColors.blockedContainer,
                                    contentColor = shieldColors.blocked,
                                )
                            }
                        },
                        onClick = onOpenVault,
                    )
                }
            }

            item { VerticalSpacer(80.dp) }
        }
    }
}
