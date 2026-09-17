package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.SimScopeSelector

/**
 * Asks which SIM an action applies to.
 *
 * The default is always the SIM the user is currently working with, never "Both SIMs" —
 * crossing the isolation boundary has to be something the user chose on purpose.
 */
@Composable
fun ScopeChoiceDialog(
    title: String,
    message: String,
    confirmLabel: String,
    sims: List<SimOption>,
    defaultSlot: Int?,
    onConfirm: (SimScope) -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val initial = defaultSlot?.let { SimScope.forSlot(it) } ?: SimScope.SIM2
    var scope by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Applies to",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SimScopeSelector(
                    options = sims,
                    selected = scope,
                    onSelect = { scope = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(scope) }) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
