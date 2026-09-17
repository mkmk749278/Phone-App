package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ConfirmationDialog
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SegmentedControl
import com.dualshield.phone.ui.components.VaultItem
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Shield Vault.
 *
 * Kept entirely separate from Recents, which is the whole point: the user can inspect what
 * was blocked without blocked entries polluting their call history.
 */
@Composable
fun VaultScreen(
    state: VaultViewModel.UiState,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onClearVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = "Shield Vault",
                subtitle = "Blocked calls are kept out of Recents",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Vault")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedControl(
                    options = listOf(
                        "Calls (${state.blockedCalls.size})",
                        "Messages (${state.blockedMessages.size})",
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == 0) {
                if (state.blockedCalls.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No blocked calls",
                            message = "Shield hasn't blocked anything yet. You're all clear.",
                        )
                    }
                } else {
                    item {
                        SectionCard {
                            state.blockedCalls.forEachIndexed { index, record ->
                                VaultItem(
                                    number = record.displayName?.takeIf { it.isNotBlank() }
                                        ?: Formatting.displayNumber(record.rawNumber),
                                    ruleName = record.matchedRuleName,
                                    simLabel = Formatting.simLabel(
                                        record.simSlot.takeIf { it >= 0 },
                                        record.simLabel,
                                    ),
                                    timestamp = Formatting.listTimestamp(record.timestamp),
                                    onClick = { onOpenRecord(record.id) },
                                )
                                if (index != state.blockedCalls.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            } else {
                if (state.blockedMessages.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No blocked messages",
                            message = "Message filtering arrives in a later release. " +
                                "When it does, filtered messages appear here and the " +
                                "original SMS is never deleted.",
                        )
                    }
                } else {
                    item {
                        SectionCard {
                            state.blockedMessages.forEachIndexed { index, record ->
                                AppListRow(
                                    title = record.displayName
                                        ?: Formatting.displayNumber(record.rawNumber),
                                    subtitle = record.body.take(80),
                                )
                                if (index != state.blockedMessages.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Clearing the Vault removes this history only. Your rules and " +
                        "allowed numbers are not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { VerticalSpacer(24.dp) }
        }
    }

    if (confirmClear) {
        ConfirmationDialog(
            title = "Clear Shield Vault?",
            message = "This deletes the record of blocked calls. Your rules, allowed " +
                "numbers and SIM settings stay exactly as they are.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                onClearVault()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}
