package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.displayForSlot
import com.dualshield.phone.ui.components.groupedItems
import com.dualshield.phone.ui.theme.Spacing

/**
 * The allowlist.
 *
 * Presented as its own screen rather than buried in rules because it is the user's remedy
 * for a false positive, and the fastest possible route back from one.
 */
@Composable
fun AllowlistScreen(
    state: ShieldViewModel.UiState,
    sims: List<SimOption>,
    slotIndex: Int?,
    onBack: () -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = slotIndex?.let { state.allowRulesForSlot(it) } ?: state.allowRules

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = "Allowed numbers",
                subtitle = slotIndex
                    ?.let { slot -> sims.firstOrNull { it.slotIndex == slot }?.display }
                    ?: "Across all SIMs",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = "An allowed number always rings through, whatever any block rule says.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No allowed numbers",
                        message = "Allow a number from a blocked call or a contact to add it here.",
                    )
                }
            } else {
                groupedItems(items = entries, key = { it.id }) { entry ->
                    val scopeText = when (entry.simScope) {
                        SimScope.BOTH -> "Both SIMs"
                        SimScope.SIM1 -> sims.displayForSlot(0)
                        SimScope.SIM2 -> sims.displayForSlot(1)
                    }
                    AppListRow(
                        title = entry.displayName?.takeIf { it.isNotBlank() }
                            ?: Formatting.displayNumber(entry.normalizedNumber),
                        subtitle = scopeText,
                        leading = { ContactAvatar(entry.displayName, entry.normalizedNumber) },
                        trailing = {
                            IconButton(onClick = { onRemove(entry.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from allowlist")
                            }
                        },
                    )
                }
            }

            item { VerticalSpacer(Spacing.xl) }
        }
    }
}
