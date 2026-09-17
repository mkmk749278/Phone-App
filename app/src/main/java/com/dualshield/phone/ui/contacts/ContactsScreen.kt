package com.dualshield.phone.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.dualshield.phone.ui.components.SearchField
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems

/**
 * The contact list.
 *
 * Search runs entirely on device, including T9 keypad matching — there is no lookup service
 * behind this screen, and the app has no permission to reach one.
 *
 * Rows are real lazy items: a 2000-contact phone composes a screenful, not the phone book.
 */
@Composable
fun ContactsScreen(
    state: ContactsViewModel.UiState,
    contacts: List<Contact>,
    onQueryChange: (String) -> Unit,
    onOpenContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                Text("Contacts", style = MaterialTheme.typography.displaySmall)
                VerticalSpacer(10.dp)
            }
            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts",
                )
                VerticalSpacer(10.dp)
            }

            if (contacts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = if (state.hasPermission) "No contacts found" else "Contacts needs access",
                        message = if (state.hasPermission) {
                            "Contacts saved on this phone will appear here."
                        } else {
                            "Allow contacts so DualShieldPhone can show names instead of numbers."
                        },
                    )
                }
            } else {
                groupedItems(items = contacts, key = { it.id }) { contact ->
                    AppListRow(
                        title = contact.displayName,
                        subtitle = Formatting.displayNumber(contact.primaryNumber),
                        leading = { ContactAvatar(contact.displayName, contact.primaryNumber) },
                        onClick = { contact.primaryNumber?.let(onOpenContact) },
                    )
                }
            }

            item(key = "bottom-space") { VerticalSpacer(80.dp) }
        }
    }
}
