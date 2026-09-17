package com.dualshield.phone.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dualshield.phone.data.system.CallDirection
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SegmentedControl
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * The home screen: Recents.
 *
 * Nothing Shield-related appears here beyond what a normal phone app would show. Blocked
 * calls are not in this list by design — they live in the Vault, and the Recents list stays
 * a plain record of calls that actually happened.
 */
@Composable
fun PhoneScreen(
    state: PhoneViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialpad,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Dialpad, contentDescription = "Open dial pad")
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
            item {
                Text("Phone", style = MaterialTheme.typography.displaySmall)
            }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts or numbers",
                )
            }
            item {
                SegmentedControl(
                    options = listOf("Recents", "Favorites"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == 0) {
                val recents = state.filteredRecents
                if (recents.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (state.hasCallLogPermission) {
                                "No recent calls"
                            } else {
                                "Recents needs call history access"
                            },
                            message = if (state.hasCallLogPermission) {
                                "Calls you make and receive will show up here."
                            } else {
                                "Allow call history so DualShieldPhone can show your recent calls."
                            },
                        )
                    }
                } else {
                    item {
                        SectionCard {
                            recents.forEachIndexed { index, call ->
                                RecentCallRow(
                                    call = call,
                                    simLabel = state.sims
                                        .firstOrNull { it.slotIndex == call.simSlot }
                                        ?.display,
                                    onClick = { onOpenDetails(call.number) },
                                )
                                if (index != recents.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            } else {
                val favorites = state.favorites
                if (favorites.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No favorites yet",
                            message = "Star a contact on your phone to see them here.",
                        )
                    }
                } else {
                    item {
                        SectionCard {
                            favorites.forEachIndexed { index, contact ->
                                AppListRow(
                                    title = contact.displayName,
                                    subtitle = Formatting.displayNumber(contact.primaryNumber),
                                    leading = {
                                        ContactAvatar(contact.displayName, contact.primaryNumber)
                                    },
                                    onClick = {
                                        contact.primaryNumber?.let(onOpenDetails)
                                    },
                                )
                                if (index != favorites.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }

            item { VerticalSpacer(80.dp) }
        }
    }
}

@Composable
private fun RecentCallRow(
    call: RecentCall,
    simLabel: String?,
    onClick: () -> Unit,
) {
    val title = call.displayName?.takeIf { it.isNotBlank() }
        ?: Formatting.displayNumber(call.number)
    val directionText = when (call.direction) {
        CallDirection.INCOMING -> "Incoming"
        CallDirection.OUTGOING -> "Outgoing"
        CallDirection.MISSED -> "Missed"
        CallDirection.REJECTED -> "Declined"
        CallDirection.VOICEMAIL -> "Voicemail"
        CallDirection.OTHER -> "Call"
    }
    val subtitle = buildString {
        append(directionText)
        if (simLabel != null) append(" · $simLabel")
    }

    AppListRow(
        title = title,
        subtitle = subtitle,
        leading = { ContactAvatar(call.displayName, call.number) },
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = Formatting.listTimestamp(call.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        contentDescription = "$title, $subtitle, ${Formatting.listTimestamp(call.timestamp)}",
        onClick = onClick,
    )
}

/** Direction glyph, exposed for reuse on the call-details history list. */
@Composable
fun directionIcon(direction: CallDirection): ImageVector = remember(direction) {
    when (direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.MISSED, CallDirection.REJECTED -> Icons.AutoMirrored.Filled.CallMissed
        else -> Icons.AutoMirrored.Filled.CallReceived
    }
}
