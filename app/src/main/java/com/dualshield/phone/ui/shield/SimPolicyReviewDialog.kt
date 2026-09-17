package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.data.repository.SimRepository
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/**
 * Asked once, on the first launch after the shipped SIM policy changed.
 *
 * The app now ships with the first line filtered and the second ringing for everything. An
 * install that predates that keeps whatever it already had, because a per-SIM protection
 * setting is a decision a user may have made deliberately and is not the app's to overwrite
 * behind their back — a line they believe is filtered silently going quiet, or a duty line
 * they believe rings silently rejecting calls, are both worse than the wrong default.
 *
 * So this changes nothing on its own. It says what shipped, shows what this phone is
 * currently set to, and waits. Both answers are real answers, and the one that changes
 * nothing is the one that needs no explanation, so it sits on the left where a dismissal
 * normally does.
 */
@Composable
fun SimPolicyReviewDialog(
    sims: List<SimOption>,
    onKeepCurrent: () -> Unit,
    onApply: (Map<Int, Boolean>) -> Unit,
) {
    // Seeded from the recommendation, then owned here: the switches are a proposal the user
    // can adjust before accepting, not a preview of something already applied.
    val proposed = remember(sims) {
        mutableStateMapOf<Int, Boolean>().apply {
            sims.forEach { put(it.slotIndex, SimRepository.defaultFilteringForSlot(it.slotIndex)) }
        }
    }
    var touched by remember(sims) { mutableStateOf(false) }

    val differs = sims.any { proposed[it.slotIndex] != it.protectionEnabled }

    AlertDialog(
        // No dismiss on a back press or an outside tap. Not to trap anyone — "Keep current
        // settings" is right there and costs one tap — but because a prompt that can be
        // dismissed by accident is one the user never knowingly answered, and it would then
        // never be shown again.
        onDismissRequest = {},
        title = { Text("Which line should Shield protect?") },
        text = {
            Column {
                Text(
                    text = "The app now starts with SIM 1 protected and SIM 2 ringing for " +
                        "everything, including unknown numbers. Your phone kept the settings " +
                        "you already had.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                VerticalSpacer(Spacing.lg)
                sims.forEach { sim ->
                    SimProposalRow(
                        sim = sim,
                        proposed = proposed[sim.slotIndex] ?: sim.protectionEnabled,
                        onChange = {
                            proposed[sim.slotIndex] = it
                            touched = true
                        },
                    )
                }
                VerticalSpacer(Spacing.md)
                Text(
                    text = if (differs) {
                        "Your rules are not affected either way. Switching a line off keeps " +
                            "its rules exactly as saved — they simply stop being applied."
                    } else {
                        "This is already how your phone is set up."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepCurrent) { Text("Keep current settings") }
        },
        confirmButton = {
            TextButton(onClick = { onApply(proposed.toMap()) }) {
                Text(if (touched) "Use these" else "Use recommended")
            }
        },
    )
}

@Composable
private fun SimProposalRow(
    sim: SimOption,
    proposed: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sim.display, style = MaterialTheme.typography.bodyLarge)
            Text(
                // What this line does today, so the change is legible as a change rather
                // than as a switch that was always in that position.
                text = buildString {
                    append(if (sim.protectionEnabled) "Now: protected" else "Now: unprotected")
                    if (proposed != sim.protectionEnabled) {
                        append(if (proposed) " → protected" else " → unprotected")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = proposed, onCheckedChange = onChange, enabled = sim.present)
    }
}
