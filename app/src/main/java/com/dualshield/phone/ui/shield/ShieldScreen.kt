package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.ShieldStatusCard
import com.dualshield.phone.ui.components.SimProtectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Shield home.
 *
 * Restructured after the first build: the rules themselves used to live two taps down inside
 * a SIM, which made "where are my blocking rules" a fair question with no good answer.
 * Blocked numbers, allowed numbers and India protection are now top-level rows, and the
 * per-SIM cards are about the SIM rather than about the rule list.
 */
@Composable
fun ShieldScreen(
    state: ShieldViewModel.UiState,
    onToggleSim: (Int, Boolean) -> Unit,
    onOpenSim: (Int) -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenIndiaProtection: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenTester: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protectedSims = state.sims.filter { it.protectionEnabled }

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
            item(key = "title") {
                Text("Shield", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "Protection is local and SIM-specific.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "status") {
                ShieldStatusCard(
                    headline = if (state.anyProtectionOn) "Shield active" else "Shield off",
                    detail = when {
                        protectedSims.isEmpty() -> "No SIM is being filtered"
                        protectedSims.size == 1 -> "${protectedSims.first().display} protected"
                        else -> "${protectedSims.size} SIMs protected"
                    },
                )
            }

            // The rules, front and centre.
            item(key = "rules") {
                SectionCard {
                    AppListRow(
                        title = "Blocked numbers",
                        subtitle = if (state.blockedCount == 0) {
                            "Nothing blocked yet"
                        } else {
                            "${state.blockedCount} numbers and prefixes"
                        },
                        leading = {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = onOpenBlockedNumbers,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Allowed numbers",
                        subtitle = if (state.allowRules.isEmpty()) {
                            "Nothing on the allowlist"
                        } else {
                            "${state.allowRules.size} always ring through"
                        },
                        leading = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = onOpenAllowlist,
                    )
                    RowDivider()
                    AppListRow(
                        title = "India protection",
                        subtitle = "Promotional, transactional, toll-free and premium-rate series",
                        leading = {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = onOpenIndiaProtection,
                    )
                }
            }

            item(key = "sims-heading") {
                Text("Your SIMs", style = MaterialTheme.typography.titleMedium)
            }

            items(items = state.sims, key = { it.slotIndex }) { sim ->
                SimProtectionCard(
                    slotIndex = sim.slotIndex,
                    label = sim.label,
                    protectionEnabled = sim.protectionEnabled,
                    summary = if (sim.protectionEnabled) {
                        "${state.rulesForSlot(sim.slotIndex).count { it.enabled }} active rules"
                    } else {
                        "Not filtered"
                    },
                    present = sim.present,
                    onToggle = { onToggleSim(sim.slotIndex, it) },
                    onOpen = { onOpenSim(sim.slotIndex) },
                )
            }

            item(key = "tools") {
                SectionCard {
                    AppListRow(
                        title = "Shield Vault",
                        subtitle = "${state.blockedCallCount} blocked calls · " +
                            "${state.blockedMessageCount} filtered messages",
                        onClick = onOpenVault,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Rule tester",
                        subtitle = "Check a number before you trust a rule",
                        leading = { Icon(Icons.Filled.Science, contentDescription = null) },
                        onClick = onOpenTester,
                    )
                }
            }

            item(key = "footnote") {
                Text(
                    text = "Allowing a number always wins over blocking it, and saved " +
                        "contacts get through whatever the rules say.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "bottom-space") { VerticalSpacer(24.dp) }
        }
    }
}
