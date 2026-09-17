package com.dualshield.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SectionHeading
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Settings. Short by design: SIM profiles, Shield, notifications, rule packs, privacy.
 *
 * Shield is reached from here rather than from the bottom bar. It is configuration, not a
 * destination — and a phone app whose main navigation includes a security console is a
 * security console with a phone attached.
 */
@Composable
fun SettingsScreen(
    state: SettingsViewModel.UiState,
    versionName: String,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onSimLabelChange: (Int, String) -> Unit,
    onNotifyChange: (Boolean) -> Unit,
    onOpenShield: () -> Unit,
    onOpenCallRecording: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenRulePacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Settings", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "setup") {
                SectionCard {
                    AppListRow(
                        title = "Setup",
                        subtitle = if (state.readyToProtect) {
                            "Shield can screen calls"
                        } else {
                            "Shield cannot block calls yet — finish setup"
                        },
                        onClick = onOpenSetup,
                    )
                }
            }

            item(key = "sims-heading") { SectionHeading("SIM profiles") }
            items(items = state.sims, key = { it.slotIndex }) { sim ->
                SimLabelEditor(
                    slotIndex = sim.slotIndex,
                    label = sim.label,
                    protectionEnabled = sim.protectionEnabled,
                    present = sim.present,
                    onLabelChange = { onSimLabelChange(sim.slotIndex, it) },
                )
            }

            item(key = "calling-heading") { SectionHeading("Calling") }
            item(key = "calling-card") {
                SectionCard {
                    AppListRow(
                        title = "Call recording",
                        subtitle = "What this device allows, and why",
                        onClick = onOpenCallRecording,
                    )
                }
            }

            item(key = "shield-heading") { SectionHeading("Shield & blocking") }
            item(key = "shield-card") {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Notify when a call is blocked",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Off by default — the point of blocking is not being " +
                                    "interrupted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.notifyOnBlockedCall,
                            onCheckedChange = onNotifyChange,
                        )
                    }
                    RowDivider()
                    AppListRow(
                        title = "Shield",
                        subtitle = "Protection, SIM rules, blocked numbers and allowlist",
                        onClick = onOpenShield,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Blocked call logs",
                        subtitle = "Inspect and clear blocked-call history",
                        onClick = onOpenVault,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Rule packs",
                        subtitle = "Import and export rules as JSON",
                        onClick = onOpenRulePacks,
                    )
                }
            }

            item(key = "about-heading") { SectionHeading("About") }
            item(key = "about-card") {
                SectionCard {
                    AppListRow(
                        title = "Privacy",
                        subtitle = "How DualShieldPhone handles your data",
                        onClick = onOpenPrivacy,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Version",
                        subtitle = versionName,
                    )
                }
            }

            item(key = "bottom-space") { VerticalSpacer(24.dp) }
        }
    }
}

@Composable
private fun SimLabelEditor(
    slotIndex: Int,
    label: String,
    protectionEnabled: Boolean,
    present: Boolean,
    onLabelChange: (String) -> Unit,
) {
    var value by remember(label) { mutableStateOf(label) }
    SectionCard {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
            Text("SIM ${slotIndex + 1}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(if (protectionEnabled) "Protection on" else "Protection off")
                    if (!present) append(" · not detected")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerticalSpacer(10.dp)
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    onLabelChange(it)
                },
                label = { Text("Label") },
                placeholder = { Text("Duty") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


