package com.dualshield.phone.ui.shield

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.theme.Spacing

/**
 * Says, on any screen that lists rules, that the rules on it are not being applied.
 *
 * Shield is off for one line by default, and a screen full of switches gives every impression
 * that they are doing something. The gap between "this rule is on" and "this rule is being
 * enforced" is the one place this app can mislead someone into thinking they are protected
 * when they are not, so it is stated on the screen rather than left to be inferred from a
 * toggle further up the navigation stack.
 *
 * Deliberately not a destructive-looking error: nothing is broken, and the rules are kept
 * exactly as saved. Turning protection back on enforces them again with nothing to redo.
 */
@Composable
fun ProtectionOffNotice(
    sim: SimOption?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val line = sim?.display ?: "this SIM"
    val body = detail
        ?: "These rules are saved, but they are not enforced on $line. Every call on this " +
        "line rings."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md)
            // One announcement, not a heading followed by an orphaned sentence.
            .clearAndSetSemantics {
                contentDescription = "Shield protection is off for $line. $body"
            },
    ) {
        Text(
            text = "Shield protection is off for $line",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
