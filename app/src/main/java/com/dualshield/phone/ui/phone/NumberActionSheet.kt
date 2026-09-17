package com.dualshield.phone.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.actions.ExternalActions
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/** Everything the sheet needs to know about the number it was opened for. */
data class NumberActionTarget(
    val rawNumber: String,
    val displayNumber: String,
    val displayName: String?,
    val photoUri: String?,
    val contactId: Long?,
) {
    val isSaved: Boolean get() = contactId != null

    /** The line shown at the top: a name when we have one, otherwise the number. */
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: displayNumber
}

/**
 * The compact action surface a call row opens.
 *
 * Tapping a row used to drop the user straight into a sparse details screen, which is a lot
 * of navigation for "call them back". The common actions live here, one tap from the list,
 * and the details screen is still a tap further in for the rest.
 *
 * Which actions appear depends on the number: WhatsApp only when it could work, Save only
 * when the number is not already a contact, View contact only when it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberActionSheet(
    target: NumberActionTarget,
    sims: List<SimOption>,
    defaultSlot: Int?,
    onDismiss: () -> Unit,
    onCall: (String, Int?) -> Unit,
    onMessage: (String) -> Unit,
    onBlock: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onActionFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val callableSims = sims.filter { it.present }
    val whatsAppAvailable = ExternalActions.canOpenWhatsApp(context, target.rawNumber)

    /** Every action closes the sheet: leaving it open over the thing it launched is noise. */
    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            AppListRow(
                title = target.title,
                subtitle = target.displayName?.let { target.displayNumber },
                leading = {
                    ContactAvatar(
                        target.displayName,
                        target.rawNumber,
                        photoUri = target.photoUri,
                    )
                },
            )
            HorizontalDivider()
            VerticalSpacer(Spacing.xs)

            // One call row per SIM when there are two, so the line is chosen before the call
            // rather than after it. With one SIM there is nothing to choose.
            if (callableSims.size > 1) {
                callableSims.forEach { sim ->
                    ActionRow(
                        icon = { Icon(Icons.Filled.Call, contentDescription = null) },
                        label = "Call with ${sim.display}",
                        onClick = { act { onCall(target.rawNumber, sim.slotIndex) } },
                    )
                }
            } else {
                ActionRow(
                    icon = { Icon(Icons.Filled.Call, contentDescription = null) },
                    label = "Call",
                    onClick = { act { onCall(target.rawNumber, defaultSlot) } },
                )
            }

            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                label = "Message",
                onClick = { act { onMessage(target.rawNumber) } },
            )

            if (whatsAppAvailable) {
                ActionRow(
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                    label = "WhatsApp",
                    onClick = {
                        act {
                            if (!ExternalActions.openWhatsApp(context, target.rawNumber)) {
                                onActionFailed("WhatsApp couldn't be opened for this number.")
                            }
                        }
                    },
                )
            }

            if (target.isSaved) {
                ActionRow(
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    label = "View contact",
                    onClick = {
                        act {
                            val id = target.contactId
                            if (id == null || !ExternalActions.viewContact(context, id)) {
                                onActionFailed("That contact couldn't be opened.")
                            }
                        }
                    },
                )
            } else {
                ActionRow(
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    label = "Create new contact",
                    onClick = {
                        act {
                            if (!ExternalActions.createContact(context, target.rawNumber)) {
                                onActionFailed("No contacts app was available.")
                            }
                        }
                    },
                )
                ActionRow(
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    label = "Add to existing contact",
                    onClick = {
                        act {
                            if (!ExternalActions.addToExistingContact(context, target.rawNumber)) {
                                onActionFailed("No contacts app was available.")
                            }
                        }
                    },
                )
            }

            ActionRow(
                icon = { Icon(Icons.Filled.Block, contentDescription = null) },
                label = "Block this number",
                onClick = { act { onBlock(target.rawNumber) } },
            )

            HorizontalDivider()
            ActionRow(
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                label = "Call details",
                onClick = { act { onOpenDetails(target.rawNumber) } },
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    AppListRow(
        title = label,
        subtitle = null,
        leading = icon,
        contentDescription = label,
        onClick = onClick,
    )
}

