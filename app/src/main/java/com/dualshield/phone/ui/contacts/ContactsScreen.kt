package com.dualshield.phone.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.dualshield.phone.ui.theme.Spacing

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
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap),
        ) {
            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search contacts",
                )
                VerticalSpacer(Spacing.md)
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
                        // A contact with no name already shows its number as its name.
                        // Printing the number underneath it as well renders the same digits
                        // twice, one line above the other, which reads as two duplicate
                        // entries rather than one unnamed contact. What is useful on that
                        // second line instead is what kind of line it is.
                        subtitle = contactSubtitle(contact),
                        leading = {
                            ContactAvatar(
                                contact.displayName,
                                contact.primaryNumber,
                                photoUri = contact.photoUri,
                            )
                        },
                        onClick = { contact.primaryNumber?.let(onOpenContact) },
                    )
                }
            }

            item(key = "bottom-space") { VerticalSpacer(Spacing.xl) }
        }
    }
}

/**
 * The second line of a contact row.
 *
 * The number when there is a name above it, the line's type when the name *is* the number,
 * and a note when the contact has more numbers than the one being shown — a row that silently
 * picks one of three numbers is a row that sends someone to the wrong one.
 */
internal fun contactSubtitle(contact: Contact): String? {
    val extra = contact.phoneNumbers.size - 1
    val more = if (extra > 0) " · +$extra more" else ""
    return if (contact.hasName) {
        Formatting.displayNumber(contact.primaryNumber) + more
    } else {
        val type = contact.phoneNumbers.firstOrNull()?.typeLabel?.takeIf { it.isNotBlank() }
        when {
            type != null -> type + more
            extra > 0 -> more.removePrefix(" · ")
            else -> null
        }
    }
}
