package com.dualshield.phone.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.data.system.CallDirection
import com.dualshield.phone.data.system.RecentCall
import com.dualshield.phone.ui.actions.ExternalActions
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.shield.ScopeChoiceDialog

/**
 * Everything about one number: who it is, how to reach them, and what happened.
 *
 * The layout follows the final interaction model rather than the earlier draft, and the
 * differences are deliberate:
 *
 *  - A SIM button per line is the call action. There is no separate SIM selector to set
 *    first and then forget — pressing the button *is* choosing the line.
 *  - A saved contact's details are shown directly; there is no "View contact" button
 *    restating what is already on screen.
 *  - Allowlisting is not here. It is a Shield concept and belongs with the Shield settings,
 *    not next to a call log entry.
 *  - Recordings are not here either. They live under Settings.
 */
@Composable
fun CallDetailsScreen(
    number: String,
    displayName: String?,
    photoUri: String?,
    contactId: Long?,
    isBlocked: Boolean,
    history: List<RecentCall>,
    sims: List<SimOption>,
    defaultSlot: Int?,
    onBack: () -> Unit,
    onCall: (String, Int?) -> Unit,
    onMessage: (String) -> Unit,
    onBlock: (SimScope) -> Unit,
    onUnblock: () -> Unit,
    onMessageShown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmingBlock by remember { mutableStateOf(false) }
    val displayNumber = PhoneNumberFormatter.display(number)
    val callableSims = sims.filter { it.present }
    val isSaved = contactId != null
    val whatsAppAvailable = ExternalActions.canOpenWhatsApp(context, number)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Call details", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerticalSpacer(14.dp)
            ContactAvatar(displayName, number, photoUri = photoUri, size = 72.dp)
            VerticalSpacer(10.dp)
            Text(
                text = displayName?.takeIf { it.isNotBlank() } ?: displayNumber,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (!displayName.isNullOrBlank()) {
                Text(
                    text = displayNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VerticalSpacer(16.dp)

            // The call actions. Two SIMs means two buttons, each of which places the call on
            // that line; one SIM means one plain Call button, since there is nothing to pick.
            if (callableSims.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    callableSims.forEach { sim ->
                        FilledTonalButton(
                            onClick = { onCall(number, sim.slotIndex) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                            Text("  ${sim.display}", maxLines = 1)
                        }
                    }
                }
            } else {
                FilledTonalButton(onClick = { onCall(number, defaultSlot) }) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Text("  Call")
                }
            }

            VerticalSpacer(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onMessage(number) }) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Text("  Message")
                }
                if (whatsAppAvailable) {
                    FilledTonalButton(
                        onClick = {
                            if (!ExternalActions.openWhatsApp(context, number)) {
                                onMessageShown("WhatsApp couldn't be opened for this number.")
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null)
                        Text("  WhatsApp")
                    }
                }
            }

            VerticalSpacer(20.dp)
            SectionCard {
                if (!isSaved) {
                    AppListRow(
                        title = "Create new contact",
                        subtitle = "Save $displayNumber to your contacts",
                        leading = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        onClick = {
                            if (!ExternalActions.createContact(context, number)) {
                                onMessageShown("No contacts app was available.")
                            }
                        },
                    )
                    RowDivider()
                    AppListRow(
                        title = "Add to existing contact",
                        subtitle = null,
                        leading = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        onClick = {
                            if (!ExternalActions.addToExistingContact(context, number)) {
                                onMessageShown("No contacts app was available.")
                            }
                        },
                    )
                    RowDivider()
                }
                AppListRow(
                    title = if (isBlocked) "Unblock this number" else "Block this number",
                    subtitle = if (isBlocked) {
                        "Calls from this number are being blocked"
                    } else {
                        "Choose which SIM the block applies to"
                    },
                    leading = {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            tint = if (isBlocked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                    onClick = { if (isBlocked) onUnblock() else confirmingBlock = true },
                )
            }

            VerticalSpacer(18.dp)
            Text(
                text = "Call history",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            VerticalSpacer(8.dp)
            if (history.isEmpty()) {
                Text(
                    text = "No calls recorded with this number yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SectionCard {
                    history.forEachIndexed { index, call ->
                        val direction = when (call.direction) {
                            CallDirection.OUTGOING -> "Outgoing"
                            CallDirection.MISSED -> "Missed"
                            CallDirection.REJECTED -> "Declined"
                            else -> "Incoming"
                        }
                        val sim = sims.firstOrNull { it.slotIndex == call.simSlot }?.display
                        AppListRow(
                            title = Formatting.fullTimestamp(call.timestamp),
                            subtitle = buildString {
                                append(direction)
                                if (sim != null) append(" · $sim")
                                Formatting.duration(call.durationSeconds)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { append(" · $it") }
                            },
                        )
                        if (index != history.lastIndex) RowDivider()
                    }
                }
            }
            VerticalSpacer(32.dp)
        }
    }

    if (confirmingBlock) {
        ScopeChoiceDialog(
            title = "Block $displayNumber?",
            message = "Blocked calls go to your blocked history. They will not appear in Recents.",
            confirmLabel = "Block",
            sims = sims,
            defaultSlot = defaultSlot,
            destructive = true,
            onConfirm = { scope ->
                onBlock(scope)
                confirmingBlock = false
            },
            onDismiss = { confirmingBlock = false },
        )
    }
}
