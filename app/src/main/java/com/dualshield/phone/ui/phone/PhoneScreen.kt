package com.dualshield.phone.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.dualshield.phone.data.system.CallDirection
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.SegmentedControl
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems

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
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "title") {
                Text("Phone", style = MaterialTheme.typography.displaySmall)
                VerticalSpacer(10.dp)
            }
            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts or numbers",
                )
                VerticalSpacer(10.dp)
            }
            item(key = "tabs") {
                SegmentedControl(
                    options = listOf("Recents", "Favorites"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
                VerticalSpacer(10.dp)
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
                            onClick = { onOpenDetails(call.number) },
                        )
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
                        FavoriteRow(contact, onOpenDetails)
                    }
                }
            }

            item(key = "bottom-space") { VerticalSpacer(80.dp) }
        }
    }
}

@Composable
private fun RecentCallRow(
    call: RecentCall,
    sims: List<SimOption>,
    onClick: () -> Unit,
) {
    val title = call.displayName?.takeIf { it.isNotBlank() }
        ?: Formatting.displayNumber(call.number)
    val simLabel = sims.firstOrNull { it.slotIndex == call.simSlot }?.display
    val directionText = when (call.direction) {
        CallDirection.INCOMING -> "Incoming"
        CallDirection.OUTGOING -> "Outgoing"
        CallDirection.MISSED -> "Missed"
        CallDirection.REJECTED -> "Declined"
        CallDirection.VOICEMAIL -> "Voicemail"
        CallDirection.OTHER -> "Call"
    }
    val subtitle = if (simLabel != null) "$directionText · $simLabel" else directionText
    val time = Formatting.listTimestamp(call.timestamp)

    AppListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            ContactAvatar(call.displayName, call.number, photoUri = call.photoUri)
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        contentDescription = "$title, $subtitle, $time",
        onClick = onClick,
    )
}

@Composable
private fun FavoriteRow(contact: Contact, onOpenDetails: (String) -> Unit) {
    AppListRow(
        title = contact.displayName,
        subtitle = Formatting.displayNumber(contact.primaryNumber),
        leading = {
            ContactAvatar(
                contact.displayName,
                contact.primaryNumber,
                photoUri = contact.photoUri,
            )
        },
        onClick = { contact.primaryNumber?.let(onOpenDetails) },
    )
}
