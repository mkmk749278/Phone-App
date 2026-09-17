package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.shield.RecoveryEvaluator
import com.dualshield.phone.core.shield.RecoveryMode
import com.dualshield.phone.core.shield.RecoverySettings
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SectionHeading
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Behavioural protection, per SIM.
 *
 * The screen is explicit about what this feature is and is not, because the difference
 * matters to whoever is on the other end of a silenced call. It reasons about how often and
 * how insistently a number has called *this* phone. It does not know who anyone is, and the
 * wording never suggests otherwise.
 */
@Composable
fun RecoveryProtectionScreen(
    sims: List<SimOption>,
    selectedSlot: Int?,
    settings: RecoverySettings,
    onSelectSlot: (Int) -> Unit,
    onChange: (RecoverySettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentSims = sims.filter { it.present }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Repeated callers", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "Shield can notice when a number calls this phone unusually often " +
                        "and act on it. It works only from calls this device has seen — " +
                        "there is no list of known numbers, and nothing leaves the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (presentSims.size > 1) {
                item(key = "sim-picker") {
                    SectionCard {
                        presentSims.forEachIndexed { index, sim ->
                            ChoiceRow(
                                label = sim.display,
                                selected = sim.slotIndex == selectedSlot,
                                onSelect = { onSelectSlot(sim.slotIndex) },
                            )
                            if (index != presentSims.lastIndex) RowDivider()
                        }
                    }
                }
            }

            item(key = "mode-heading") { SectionHeading("What to do") }
            item(key = "mode") {
                SectionCard {
                    RecoveryMode.entries.forEachIndexed { index, mode ->
                        ChoiceRow(
                            label = mode.label,
                            supporting = mode.description,
                            selected = settings.mode == mode,
                            onSelect = { onChange(settings.copy(mode = mode)) },
                        )
                        if (index != RecoveryMode.entries.lastIndex) RowDivider()
                    }
                }
            }

            if (settings.isActive) {
                item(key = "signals-heading") { SectionHeading("What counts") }
                item(key = "signals") {
                    SectionCard {
                        ToggleRow(
                            label = "Repeated callers",
                            supporting = "Calls on ${RecoveryEvaluator.REPEATED_DAYS_PER_WEEK} " +
                                "or more separate days in a week",
                            checked = settings.repeatedCallers,
                            onChange = { onChange(settings.copy(repeatedCallers = it)) },
                        )
                        RowDivider()
                        ToggleRow(
                            label = "High-frequency callers",
                            supporting = "${RecoveryEvaluator.HIGH_FREQUENCY_PER_DAY} or more " +
                                "calls in a single day",
                            checked = settings.highFrequencyCallers,
                            onChange = { onChange(settings.copy(highFrequencyCallers = it)) },
                        )
                        RowDivider()
                        ToggleRow(
                            label = "Rapid repeat calls",
                            supporting = "Calling straight back, several times in a few minutes",
                            checked = settings.rapidRepeat,
                            onChange = { onChange(settings.copy(rapidRepeat = it)) },
                        )
                        RowDivider()
                        ToggleRow(
                            label = "Note unknown callers",
                            supporting = "Adds a note when the caller is not in your contacts. " +
                                "On its own this never silences or blocks anyone.",
                            checked = settings.unknownCallers,
                            onChange = { onChange(settings.copy(unknownCallers = it)) },
                        )
                    }
                }
            }

            item(key = "caveat") {
                VerticalSpacer(4.dp)
                Text(
                    text = "Saved contacts are never affected by this, however they call. " +
                        "These are observations about calling patterns, not judgements about " +
                        "who is calling — a number that calls often may simply be a clinic, a " +
                        "courier or someone trying to reach you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "bottom-space") { VerticalSpacer(24.dp) }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    supporting: String? = null,
) {
    AppListRow(
        title = label,
        subtitle = supporting,
        leading = { RadioButton(selected = selected, onClick = null) },
        contentDescription = label,
        onClick = onSelect,
    )
}

@Composable
private fun ToggleRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
