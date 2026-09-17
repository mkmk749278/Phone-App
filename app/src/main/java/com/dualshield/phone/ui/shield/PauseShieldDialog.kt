package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.dualshield.phone.core.shield.PauseDuration
import com.dualshield.phone.ui.components.SimOption

/**
 * Chooses how long to pause Shield, and for which line.
 *
 * Defaults to a short, timed pause on both SIMs: the common case is "let me take calls for
 * the next hour", and the default should not be the one that leaves protection off
 * indefinitely because the user forgot to come back.
 */
@Composable
fun PauseShieldDialog(
    sims: List<SimOption>,
    onDismiss: () -> Unit,
    onConfirm: (PauseDuration, SimScope) -> Unit,
) {
    var duration by remember { mutableStateOf(PauseDuration.ONE_HOUR) }
    var scope by remember { mutableStateOf(SimScope.BOTH) }
    val presentSims = sims.filter { it.present }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause Shield") },
        text = {
            Column {
                Text(
                    text = "Your rules stay exactly as they are. Nothing is blocked while " +
                        "the pause is on, and protection comes back by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionLabel("For how long")
                PauseDuration.entries.forEach { option ->
                    ChoiceRow(
                        label = option.label,
                        selected = duration == option,
                        onSelect = { duration = option },
                    )
                }

                // Only worth asking which line when there is more than one to choose from.
                if (presentSims.size > 1) {
                    SectionLabel("On which SIM")
                    ChoiceRow(
                        label = "Both SIMs",
                        selected = scope == SimScope.BOTH,
                        onSelect = { scope = SimScope.BOTH },
                    )
                    presentSims.forEach { sim ->
                        val simScope = if (sim.slotIndex == 0) SimScope.SIM1 else SimScope.SIM2
                        ChoiceRow(
                            label = sim.display,
                            selected = scope == simScope,
                            onSelect = { scope = simScope },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(duration, scope) }) { Text("Pause") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
