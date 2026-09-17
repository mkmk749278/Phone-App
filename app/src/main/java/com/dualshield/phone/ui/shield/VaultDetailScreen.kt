package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.StatusPill
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.LocalShieldColors
import com.dualshield.phone.ui.theme.Spacing

/**
 * One blocked call, explained.
 *
 * Every field the engine used is shown — the number, the SIM, the exact rule and its
 * category — because a user who cannot see *why* a call was blocked cannot correct a false
 * positive, and correcting false positives quickly is what keeps the allowlist trustworthy.
 */
@Composable
fun VaultDetailScreen(
    record: BlockedCallEntity?,
    sims: List<SimOption>,
    onBack: () -> Unit,
    onAllow: (SimScope) -> Unit,
    onBlockAlways: (SimScope) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<VaultAction?>(null) }
    val shieldColors = LocalShieldColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Blocked call", onBack = onBack) },
    ) { padding ->
        if (record == null) {
            EmptyState(
                title = "Record not found",
                message = "This blocked call is no longer in your history.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            VerticalSpacer(Spacing.sm)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = Formatting.displayNumber(record.rawNumber),
                    style = MaterialTheme.typography.headlineMedium,
                )
                record.displayName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                VerticalSpacer(Spacing.sm)
                StatusPill(
                    text = "BLOCKED",
                    containerColor = shieldColors.blockedContainer,
                    contentColor = shieldColors.blocked,
                )
            }

            SectionCard {
                DetailRow("SIM", Formatting.simLabel(record.simSlot.takeIf { it >= 0 }, record.simLabel))
                DetailRow("Blocked by", record.matchedRuleName)
                DetailRow("Category", record.category.displayName)
                DetailRow("When", Formatting.fullTimestamp(record.timestamp))
                DetailRow("Reason", record.reason)
            }

            Button(
                onClick = { pending = VaultAction.Allow },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow this number")
            }
            OutlinedButton(
                onClick = { pending = VaultAction.Block },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Always block this number")
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete this record", color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = "Allowing a number always wins over every block rule, so this is " +
                    "easy to undo if Shield got it wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VerticalSpacer(Spacing.xxl)
        }
    }

    pending?.let { action ->
        ScopeChoiceDialog(
            title = when (action) {
                VaultAction.Allow -> "Always allow this number?"
                VaultAction.Block -> "Always block this number?"
            },
            message = when (action) {
                VaultAction.Allow ->
                    "Calls from this number will ring through, even if a rule would block them."
                VaultAction.Block ->
                    "An exact block rule will be created for this number."
            },
            confirmLabel = when (action) {
                VaultAction.Allow -> "Allow"
                VaultAction.Block -> "Block"
            },
            sims = sims,
            defaultSlot = record?.simSlot?.takeIf { it >= 0 },
            destructive = action == VaultAction.Block,
            onConfirm = { scope ->
                when (action) {
                    VaultAction.Allow -> onAllow(scope)
                    VaultAction.Block -> onBlockAlways(scope)
                }
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

private enum class VaultAction { Allow, Block }

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
