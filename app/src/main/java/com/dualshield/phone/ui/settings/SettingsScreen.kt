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
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SectionHeading
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

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
    onOpenBlockedNumbers: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenIndiaBlocklist: () -> Unit,
    onOpenRecovery: () -> Unit,
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
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Setup only earns the top of the screen while something is actually wrong.
            // A permanent "Setup" row at the top of Settings is a checklist the user has
            // already completed, sitting above the things they came here to change.
            if (!state.readyToProtect) {
                item(key = "setup") {
                    SectionCard {
                        AppListRow(
                            title = "Finish setup",
                            subtitle = "Shield cannot block calls yet",
                            onClick = onOpenSetup,
                        )
                    }
                }
            }

            item(key = "sims-heading") { SectionHeading("SIM & calling") }
            items(items = state.sims, key = { it.slotIndex }) { sim ->
                SimLabelEditor(
                    slotIndex = sim.slotIndex,
                    label = sim.label,
                    protectionEnabled = sim.protectionEnabled,
                    present = sim.present,
                    onLabelChange = { onSimLabelChange(sim.slotIndex, it) },
                )
            }
            item(key = "calling-card") {
                SectionCard {
                    AppListRow(
                        title = "Call recording",
                        subtitle = "What this device allows, and why",
                        onClick = onOpenCallRecording,
                    )
                    if (state.readyToProtect) {
                        RowDivider()
                        AppListRow(
                            title = "Setup",
                            subtitle = "Default apps and permissions",
                            onClick = onOpenSetup,
                        )
                    }
                }
            }

            // Everything Shield can do, reachable from one place. These screens all existed
            // but could only be found by going into Shield and then into a SIM, which is
            // two levels of navigation to reach a list of blocked numbers.
            item(key = "shield-heading") { SectionHeading("Shield & blocking") }
            item(key = "shield-card") {
                SectionCard {
                    AppListRow(
                        title = "Shield status",
                        subtitle = shieldSummary(state),
                        onClick = onOpenShield,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Blocked numbers",
                        subtitle = "Numbers you have blocked yourself",
                        onClick = onOpenBlockedNumbers,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Allowed numbers",
                        subtitle = "Always ring, whatever a rule says",
                        onClick = onOpenAllowlist,
                    )
                    RowDivider()
                    AppListRow(
                        title = "India blocklist",
                        subtitle = "Built-in rules for Indian numbering series",
                        onClick = onOpenIndiaBlocklist,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Blocked call logs",
                        subtitle = "What Shield has turned away",
                        onClick = onOpenVault,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Recovery Call Protection",
                        subtitle = "Act on numbers that call unusually often",
                        onClick = onOpenRecovery,
                    )
                }
            }

            item(key = "notify-card") {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md),
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
                }
            }

            // Last, and on its own, because importing a rule file replaces what is there.
            item(key = "advanced-heading") { SectionHeading("Advanced") }
            item(key = "advanced-card") {
                SectionCard {
                    AppListRow(
                        title = "Advanced rules",
                        subtitle = "Back up or restore your rules",
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

            item(key = "bottom-space") { VerticalSpacer(Spacing.xl) }
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
        Column(modifier = Modifier.padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md)) {
            Text(Formatting.slotName(slotIndex), style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(if (protectionEnabled) "Protection on" else "Protection off")
                    if (!present) append(" · not detected")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerticalSpacer(Spacing.md)
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    onLabelChange(it)
                },
                label = { Text("Label") },
                placeholder = { Text("e.g. Personal, Work, Family") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}




/**
 * What the Shield row says, in one line.
 *
 * Names the lines that are protected rather than counting rules. A rule count is a number
 * about the app's internals; which of your two lines is being filtered is the thing you
 * came to Settings to check.
 */
private fun shieldSummary(state: SettingsViewModel.UiState): String {
    val protectedSims = state.sims.filter { it.protectionEnabled }
    return when {
        state.sims.isEmpty() -> "Protection, rules and blocked numbers"
        protectedSims.isEmpty() -> "No line is being filtered"
        protectedSims.size == state.sims.size -> "Every line is protected"
        else -> "${protectedSims.joinToString { it.display }} protected"
    }
}
