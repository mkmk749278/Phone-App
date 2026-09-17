package com.dualshield.phone.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.SimScope

/** A SIM as the UI needs it: a slot, the user's label, and whether it is physically present. */
@Immutable
data class SimOption(
    val slotIndex: Int,
    val label: String,
    val present: Boolean,
    val protectionEnabled: Boolean,
    val allowContacts: Boolean = true,
) {
    /**
     * What to call this line on screen.
     *
     * A fresh profile has no label at all — the app does not name the user's SIMs for them —
     * so the separator has to go with it. "SIM 1 · " with nothing after it reads as a missing
     * value rather than an unnamed line.
     */
    val display: String
        get() = if (label.isBlank()) "SIM ${slotIndex + 1}" else "SIM ${slotIndex + 1} · $label"
}

/**
 * Horizontal SIM chips, used on the dialpad and above the message composer.
 *
 * The selected SIM is never implicit. Everywhere the user can act on a SIM, the chip row is
 * visible, because silently switching lines is the failure mode this product exists to avoid.
 */
@Composable
fun SimChipRow(
    options: List<SimOption>,
    selectedSlot: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { option ->
            val selected = option.slotIndex == selectedSlot
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(99.dp),
                    )
                    .clickable(enabled = option.present) { onSelect(option.slotIndex) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics {
                        contentDescription = buildString {
                            append(option.display)
                            append(if (selected) ", selected" else ", not selected")
                            if (!option.present) append(", not available")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.display,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !option.present -> MaterialTheme.colorScheme.onSurfaceVariant
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/**
 * "Applies to" selector for rules.
 *
 * Both SIMs is last and never preselected — it is the one choice that can reach across the
 * isolation boundary, so it has to be a deliberate tap.
 */
@Composable
fun SimScopeSelector(
    options: List<SimOption>,
    selected: SimScope,
    onSelect: (SimScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = buildList {
        options.sortedBy { it.slotIndex }.forEach { option ->
            SimScope.forSlot(option.slotIndex)?.let { scope -> add(scope to option.display) }
        }
        add(SimScope.BOTH to "Both SIMs")
    }

    Column(modifier = modifier.selectableGroup()) {
        entries.forEach { (scope, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelect(scope) }
                    .heightIn(min = MinTouchTarget)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = scope == selected, onClick = { onSelect(scope) })
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    if (scope == SimScope.BOTH) {
                        Text(
                            "This rule will apply to both lines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The numbered square that identifies a SIM at a glance on the Shield screen. */
@Composable
fun SimSlotBadge(
    slotIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(containerColor)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${slotIndex + 1}",
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}
