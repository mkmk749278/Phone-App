package com.dualshield.phone.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dualshield.phone.data.system.CallDirection
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SegmentedControl
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems
import com.dualshield.phone.ui.theme.Spacing

/**
 * The home screen: Recents.
 *
 * Nothing Shield-related appears here beyond what a normal phone app would show. Blocked
 * calls are not in this list by design — they live in the Vault, and Recents stays a plain
 * record of calls that actually happened.
 */
@Composable
fun PhoneScreen(
    state: PhoneViewModel.UiState,
    recents: List<RecentCall>,
    onQueryChange: (String) -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenActions: (RecentCall) -> Unit,
    onOpenContactActions: (Contact) -> Unit,
    onOpenBlockedCalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val favorites = state.favorites

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenDialpad, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.Dialpad, contentDescription = "Open dial pad")
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap),
        ) {
            item(key = "title") {
                Text("Phone", style = MaterialTheme.typography.displaySmall)
                VerticalSpacer(Spacing.md)
            }
            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts or numbers",
                )
                VerticalSpacer(Spacing.md)
            }
            item(key = "tabs") {
                SegmentedControl(
                    options = listOf("Recents", "Favorites"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
                VerticalSpacer(Spacing.md)
            }

            if (tab == 0) {
                if (recents.isEmpty()) {
                    item(key = "recents-empty") {
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
                    groupedItems(items = recents, key = { it.id }) { call ->
                        RecentCallRow(
                            call = call,
                            sims = state.sims,
                            onClick = { onOpenActions(call) },
                            onOpenDetails = { onOpenDetails(call.number) },
                        )
                    }
                }

                // One entry, not one row per blocked call. Blocked activity is worth keeping
                // and worth being able to audit, but it is not what Recents is for: a list
                // where nine of every ten rows are calls that never rang is not a call
                // history, it is a security log.
                if (state.blockedCallCount > 0) {
                    item(key = "blocked-entry") {
                        VerticalSpacer(Spacing.sm)
                        SectionCard {
                            AppListRow(
                                title = "Blocked call logs",
                                subtitle = "${state.blockedCallCount} blocked",
                                leading = {
                                    Icon(
                                        Icons.Filled.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailing = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = onOpenBlockedCalls,
                            )
                        }
                    }
                }
            } else {
                if (favorites.isEmpty()) {
                    item(key = "fav-empty") {
                        EmptyState(
                            title = "No favorites yet",
                            message = "Star a contact on your phone to see them here.",
                        )
                    }
                } else {
                    groupedItems(items = favorites, key = { it.id }) { contact ->
                        FavoriteRow(contact, onOpenContactActions)
                    }
                }
            }

            item(key = "bottom-space") { VerticalSpacer(Spacing.listBottomInset) }
        }
    }
}

@Composable
private fun RecentCallRow(
    call: RecentCall,
    sims: List<SimOption>,
    onClick: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val title = call.displayName?.takeIf { it.isNotBlank() } ?: call.displayNumber
    val simLabel = sims.firstOrNull { it.slotIndex == call.simSlot }?.display
    val directionText = call.direction.label()
    val subtitle = if (simLabel != null) "$directionText · $simLabel" else directionText
    val time = Formatting.listTimestamp(call.timestamp)

    AppListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            ContactAvatar(call.displayName, call.number, photoUri = call.photoUri)
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The call type is an icon as well as words, so the list can be read at
                    // a glance without parsing every subtitle.
                    Icon(
                        imageVector = call.direction.icon(),
                        contentDescription = null,
                        tint = call.direction.tint(),
                        modifier = Modifier.size(15.dp),
                    )
                }
                // The chevron is the one explicit way into the details screen, so tapping
                // the row itself can mean the thing people actually want: the actions.
                IconButton(onClick = onOpenDetails) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Call details for $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        contentDescription = "$title, $subtitle, $time",
        onClick = onClick,
    )
}

private fun CallDirection.label(): String = when (this) {
    CallDirection.INCOMING -> "Incoming"
    CallDirection.OUTGOING -> "Outgoing"
    CallDirection.MISSED -> "Missed"
    CallDirection.REJECTED -> "Declined"
    CallDirection.VOICEMAIL -> "Voicemail"
    CallDirection.OTHER -> "Call"
}

private fun CallDirection.icon(): ImageVector = when (this) {
    CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
    CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
    CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
    CallDirection.REJECTED -> Icons.Filled.Block
    CallDirection.VOICEMAIL -> Icons.Filled.Voicemail
    CallDirection.OTHER -> Icons.Filled.Call
}

@Composable
private fun CallDirection.tint() = when (this) {
    // Only a missed call is coloured. Tinting every row would make the list shout.
    CallDirection.MISSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun FavoriteRow(contact: Contact, onOpenActions: (Contact) -> Unit) {
    AppListRow(
        title = contact.displayName,
        subtitle = contact.phoneNumbers.firstOrNull()?.display,
        leading = {
            ContactAvatar(
                contact.displayName,
                contact.primaryNumber,
                photoUri = contact.photoUri,
            )
        },
        onClick = { onOpenActions(contact) },
    )
}
