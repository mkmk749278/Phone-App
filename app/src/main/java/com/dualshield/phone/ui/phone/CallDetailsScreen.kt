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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.data.system.CallDirection
import com.dualshield.phone.data.system.RecentCall
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
 * Everything about one number: who it is, what happened, and the two Shield actions.
 *
 * Block and Allow both open a scope dialog defaulting to "this SIM only", because a rule
 * that quietly applies to both lines is exactly the mistake this product is built to prevent.
 */
@Composable
fun CallDetailsScreen(
    number: String,
    displayName: String?,
    photoUri: String?,
    history: List<RecentCall>,
    sims: List<SimOption>,
    defaultSlot: Int?,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBlock: (SimScope) -> Unit,
    onAllow: (SimScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingAction by remember { mutableStateOf<PendingScopeAction?>(null) }

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
                text = displayName?.takeIf { it.isNotBlank() }
                    ?: Formatting.displayNumber(number),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (!displayName.isNullOrBlank()) {
                Text(
                    text = Formatting.displayNumber(number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VerticalSpacer(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onCall(number) }) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Text("  Call")
                }
                FilledTonalButton(onClick = { onMessage(number) }) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Text("  Message")
                }
            }

            VerticalSpacer(20.dp)
            SectionCard {
                AppListRow(
                    title = "Block this number",
                    subtitle = "Choose which SIM the block applies to",
                    leading = {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { pendingAction = PendingScopeAction.Block },
                )
                RowDivider()
                AppListRow(
                    title = "Always allow this number",
                    subtitle = "Allow always wins over every block rule",
                    leading = {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { pendingAction = PendingScopeAction.Allow },
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

    pendingAction?.let { action ->
        ScopeChoiceDialog(
            title = when (action) {
                PendingScopeAction.Block -> "Block ${Formatting.displayNumber(number)}?"
                PendingScopeAction.Allow -> "Always allow ${Formatting.displayNumber(number)}?"
            },
            message = when (action) {
                PendingScopeAction.Block ->
                    "Blocked calls go to Shield Vault. They will not appear in Recents."
                PendingScopeAction.Allow ->
                    "This number will ring through even if another rule would block it."
            },
            confirmLabel = when (action) {
                PendingScopeAction.Block -> "Block"
                PendingScopeAction.Allow -> "Allow"
            },
            sims = sims,
            defaultSlot = defaultSlot,
            destructive = action == PendingScopeAction.Block,
            onConfirm = { scope ->
                when (action) {
                    PendingScopeAction.Block -> onBlock(scope)
                    PendingScopeAction.Allow -> onAllow(scope)
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
}

private enum class PendingScopeAction { Block, Allow }
