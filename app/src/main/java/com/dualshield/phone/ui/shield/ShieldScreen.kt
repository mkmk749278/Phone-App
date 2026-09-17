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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.shield.PauseDuration
import com.dualshield.phone.core.shield.ShieldPause
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.Formatting
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
    onOpenRecovery: () -> Unit,
    onOpenTester: () -> Unit,
    onPause: (PauseDuration, SimScope) -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protectedSims = state.sims.filter { it.protectionEnabled }
    var choosingPause by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Shield is reached from Settings rather than from a tab, so it needs a way back.
        topBar = { DetailHeader(title = "Shield & blocking", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Protection is local and SIM-specific.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "status") {
                val pause = state.pause
                ShieldStatusCard(
                    headline = when {
                        pause != null -> "Shield paused"
                        state.anyProtectionOn -> "Shield active"
                        else -> "Shield off"
                    },
                    detail = when {
                        pause != null -> pausedDetail(pause, state)
                        protectedSims.isEmpty() -> "No SIM is being filtered"
                        protectedSims.size == 1 -> "${protectedSims.first().display} protected"
                        else -> "${protectedSims.size} SIMs protected"
                    },
                )
            }

            // Pausing is a first-class action, not something buried in Advanced: opening a
            // short call window is a thing this user does most weeks. The treatment stays
            // quiet on purpose — a paused Shield should be obvious, not alarming.
            item(key = "pause") {
                SectionCard {
                    if (state.isPaused) {
                        AppListRow(
                            title = "Resume Shield",
                            subtitle = "Turn protection back on now",
                            leading = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            onClick = onResume,
                        )
                    } else {
                        AppListRow(
                            title = "Pause Shield",
                            subtitle = "Take calls you would normally block, then let " +
                                "protection come back on its own",
                            leading = { Icon(Icons.Filled.Pause, contentDescription = null) },
                            onClick = { choosingPause = true },
                        )
                    }
                }
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
                        title = "Repeated callers",
                        subtitle = "Act on numbers that call this phone unusually often",
                        leading = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                        onClick = onOpenRecovery,
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

    if (choosingPause) {
        PauseShieldDialog(
            sims = state.sims,
            onDismiss = { choosingPause = false },
            onConfirm = { duration, scope ->
                onPause(duration, scope)
                choosingPause = false
            },
        )
    }
}

/**
 * What the status card says while a pause is in force.
 *
 * A timed pause names the time it ends, because the promise the feature makes is that the
 * user does not have to remember to switch protection back on.
 */
private fun pausedDetail(pause: ShieldPause, state: ShieldViewModel.UiState): String {
    val where = when (pause.scope) {
        SimScope.BOTH -> "Calls and messages are not being blocked"
        SimScope.SIM1 ->
            "${state.sim(0)?.display ?: "SIM 1"} is not being filtered"
        SimScope.SIM2 ->
            "${state.sim(1)?.display ?: "SIM 2"} is not being filtered"
    }
    val until = pause.expiresAtMillis
        ?.let { " · resumes at ${Formatting.timeOfDay(it)}" }
        .orEmpty()
    return where + until
}
