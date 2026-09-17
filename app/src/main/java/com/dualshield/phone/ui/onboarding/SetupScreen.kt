package com.dualshield.phone.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.telecom.RoleStatus
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.SimSlotBadge
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/**
 * Setup, reachable both on first run and from Settings afterwards.
 *
 * Two things this fixes from the first version. Permissions and roles were a one-shot prompt
 * during onboarding with no way back and no way to see what had been granted — so a denied
 * prompt left the app quietly broken. And the SIM names were assumed ("SIM 1 is your duty
 * line"), which is only true for whoever wrote the spec: here the user names their own SIMs
 * and picks which one to protect.
 */
@Composable
fun SetupScreen(
    sims: List<SimOption>,
    roles: RoleStatus,
    hasPhonePermissions: Boolean,
    hasContactsPermission: Boolean,
    hasSmsPermission: Boolean,
    showDoneButton: Boolean,
    onSimLabelChange: (Int, String) -> Unit,
    onToggleProtection: (Int, Boolean) -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestScreeningRole: () -> Unit,
    onRequestDialerRole: () -> Unit,
    onRequestSmsRole: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = "Set up DualShieldPhone",
                subtitle = "Everything here can be changed later",
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
            item(key = "step-1") {
                StepHeading(1, "Let the app see your calls")
                SectionCard {
                    RequirementRow(
                        title = "Phone and call history",
                        detail = "Needed to identify which SIM a call arrived on.",
                        granted = hasPhonePermissions,
                        onGrant = onRequestPhonePermissions,
                    )
                    RequirementRow(
                        title = "Contacts",
                        detail = "Shows names instead of numbers, and keeps people you know " +
                            "out of the blocklist.",
                        granted = hasContactsPermission,
                        onGrant = onRequestContactsPermission,
                    )
                    RequirementRow(
                        title = "Messages",
                        detail = "Only needed if you want to use the Messages tab.",
                        granted = hasSmsPermission,
                        onGrant = onRequestSmsPermission,
                    )
                }
            }

            item(key = "step-2") {
                StepHeading(2, "Let Shield screen calls")
                SectionCard {
                    RequirementRow(
                        title = "Call screening",
                        detail = "The one permission Shield genuinely cannot work without.",
                        granted = roles.isCallScreener,
                        onGrant = onRequestScreeningRole,
                        required = true,
                    )
                    RequirementRow(
                        title = "Default phone app",
                        detail = "Optional. Gives you this app's dialer and in-call screen.",
                        granted = roles.isDefaultDialer,
                        onGrant = onRequestDialerRole,
                    )
                    RequirementRow(
                        title = "Default SMS app",
                        detail = "Optional. Needed to send messages and to filter them.",
                        granted = roles.isDefaultSmsApp,
                        onGrant = onRequestSmsRole,
                    )
                }
            }

            item(key = "step-3") {
                StepHeading(3, "Name your SIMs")
                Text(
                    text = "Give each SIM a name you will recognise, and choose which one " +
                        "Shield should filter. Rules never cross between them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(items = sims, key = { it.slotIndex }) { sim ->
                SimSetupCard(
                    sim = sim,
                    onLabelChange = { onSimLabelChange(sim.slotIndex, it) },
                    onToggleProtection = { onToggleProtection(sim.slotIndex, it) },
                )
            }

            if (sims.isEmpty()) {
                item(key = "no-sims") {
                    SectionCard {
                        Column(modifier = Modifier.padding(Spacing.rowPaddingH)) {
                            Text("No SIMs detected yet", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Grant the phone permission above and your SIMs will " +
                                    "appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (showDoneButton) {
                item(key = "done") {
                    VerticalSpacer(Spacing.sm)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now")
                    }
                }
            }

            item(key = "bottom-space") { VerticalSpacer(Spacing.xl) }
        }
    }
}

@Composable
private fun StepHeading(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "  $title",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RequirementRow(
    title: String,
    detail: String,
    granted: Boolean,
    onGrant: () -> Unit,
    required: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = if (granted) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (required && !granted) "$title · required" else title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            TextButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

@Composable
private fun SimSetupCard(
    sim: SimOption,
    onLabelChange: (String) -> Unit,
    onToggleProtection: (Boolean) -> Unit,
) {
    // Seeded from the stored label, then owned locally so typing does not fight the database.
    var label by remember(sim.slotIndex, sim.label) { mutableStateOf(sim.label) }

    SectionCard {
        Column(modifier = Modifier.padding(Spacing.rowPaddingH)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SimSlotBadge(slotIndex = sim.slotIndex)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = Formatting.slotName(sim.slotIndex),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (sim.present) "Detected" else "Not detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VerticalSpacer(Spacing.md)
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    onLabelChange(it)
                },
                label = { Text("What is this SIM for?") },
                placeholder = { Text("e.g. Personal, Work, Family") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VerticalSpacer(Spacing.sm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Filter calls on this SIM", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (sim.protectionEnabled) {
                            "Shield is protecting this line"
                        } else {
                            "Every call on this line rings"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sim.protectionEnabled, onCheckedChange = onToggleProtection)
            }
        }
    }
}
