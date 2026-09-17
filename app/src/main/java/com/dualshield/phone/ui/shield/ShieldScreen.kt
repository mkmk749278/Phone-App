package com.dualshield.phone.ui.shield

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
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ProtectionRuleRow
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.ShieldStatusCard
import com.dualshield.phone.ui.components.SimProtectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Shield home.
 *
 * This is the one place protection becomes the subject rather than the background, and even
 * here it stays a short list of plain statements: which SIM, on or off, what it does.
 */
@Composable
fun ShieldScreen(
    state: ShieldViewModel.UiState,
    onToggleSim: (Int, Boolean) -> Unit,
    onOpenSim: (Int) -> Unit,
    onOpenVault: () -> Unit,
    onOpenTester: () -> Unit,
    onOpenAllowlist: () -> Unit,
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
            item {
                Text("Shield", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "Protection is local and SIM-specific.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                ShieldStatusCard(
                    headline = if (state.anyProtectionOn) "Shield active" else "Shield off",
                    detail = when {
                        protectedSims.isEmpty() -> "No SIM is being filtered"
                        protectedSims.size == 1 -> "${protectedSims.first().display} protected"
                        else -> "${protectedSims.size} SIMs protected"
                    },
                )
            }

            items(count = state.sims.size, key = { state.sims[it].slotIndex }) { index ->
                val sim = state.sims[index]
                val rules = state.rulesForSlot(sim.slotIndex)
                SimProtectionCard(
                    slotIndex = sim.slotIndex,
                    label = sim.label,
                    protectionEnabled = sim.protectionEnabled,
                    summary = "Independent rules",
                    present = sim.present,
                    onToggle = { onToggleSim(sim.slotIndex, it) },
                    onOpen = { onOpenSim(sim.slotIndex) },
                    content = if (sim.protectionEnabled && rules.isNotEmpty()) {
                        {
                            rules.take(6).forEach { rule ->
                                ProtectionRuleRow(
                                    name = rule.name,
                                    detail = null,
                                    action = rule.action,
                                    enabled = rule.enabled,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            item {
                SectionCard {
                    AppListRow(
                        title = "Blocked calls",
                        subtitle = "${state.blockedCallCount} in Shield Vault",
                        onClick = onOpenVault,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Allowed numbers",
                        subtitle = "${state.allowRules.size} numbers always ring through",
                        onClick = onOpenAllowlist,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Rule tester",
                        subtitle = "Check a number before you trust a rule",
                        onClick = onOpenTester,
                    )
                }
            }

            item {
                Text(
                    text = "Rules that allow a number always win over rules that block it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { VerticalSpacer(24.dp) }
        }
    }
}

/** Kept close to the screen it serves: the wording for a rule's ALLOW/BLOCK pill. */
fun actionLabel(action: RuleAction): String =
    if (action == RuleAction.ALLOW) "ALLOW" else "BLOCK"
