package com.dualshield.phone.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * The contact list.
 *
 * Search runs entirely on device, including the T9 keypad matching — there is no lookup
 * service behind this screen, and the app has no permission to reach one.
 */
@Composable
fun ContactsScreen(
    state: ContactsViewModel.UiState,
    contacts: List<Contact>,
    onQueryChange: (String) -> Unit,
    onOpenContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = contacts.groupBy { contact ->
        contact.displayName.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
    }.toSortedMap()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Contacts", style = MaterialTheme.typography.displaySmall) }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts",
                )
            }

            if (contacts.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.hasPermission) {
                            "No contacts found"
                        } else {
                            "Contacts needs access"
                        },
                        message = if (state.hasPermission) {
                            "Contacts saved on this phone will appear here."
                        } else {
                            "Allow contacts so DualShieldPhone can show names instead of numbers."
                        },
                    )
                }
            } else {
                grouped.forEach { (letter, group) ->
                    item(key = "header-$letter") {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(key = "group-$letter") {
                        SectionCard {
                            group.forEachIndexed { index, contact ->
                                AppListRow(
                                    title = contact.displayName,
                                    subtitle = Formatting.displayNumber(contact.primaryNumber),
                                    leading = {
                                        ContactAvatar(contact.displayName, contact.primaryNumber)
                                    },
                                    onClick = {
                                        contact.primaryNumber?.let(onOpenContact)
                                    },
                                )
                                if (index != group.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }

            item { VerticalSpacer(80.dp) }
        }
    }
}
