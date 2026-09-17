package com.dualshield.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.ui.theme.LocalShieldColors

/**
 * The single "is Shield doing anything" summary.
 *
 * It states protection in words as well as colour, so the state is readable without relying
 * on a green dot — which also means it survives TalkBack and colour-blindness.
 */
@Composable
fun ShieldStatusCard(
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val shieldColors = LocalShieldColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(shieldColors.heroSurface)
            .padding(19.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Overall protection",
                    style = MaterialTheme.typography.labelMedium,
                    color = shieldColors.onHeroSurfaceMuted,
                )
                VerticalSpacer(3.dp)
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    color = shieldColors.onHeroSurface,
                )
                VerticalSpacer(4.dp)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelLarge,
                    color = shieldColors.protected,
                )
            }
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(shieldColors.onHeroSurface.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = shieldColors.onHeroSurface,
                )
            }
        }
    }
}

/**
 * One SIM's protection card.
 *
 * The switch is labelled by the SIM it belongs to, so a screen reader announces
 * "SIM 2, Personal, protection on" rather than an anonymous toggle.
 */
@Composable
fun SimProtectionCard(
    slotIndex: Int,
    label: String,
    protectionEnabled: Boolean,
    summary: String,
    present: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SimSlotBadge(slotIndex = slotIndex)
            Column(modifier = Modifier.weight(1f)) {
                // An unnamed SIM is named by its slot rather than left as an empty line.
                // The app does not name the user's lines for them, and a card whose title
                // is blank looks like a value that failed to load.
                val named = label.isNotBlank()
                Text(
                    text = if (named) label else "SIM ${slotIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (named) "SIM ${slotIndex + 1} · $summary" else summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (protectionEnabled) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = protectionEnabled,
                onCheckedChange = onToggle,
                enabled = present,
                modifier = Modifier.semantics {
                    contentDescription = buildString {
                        append("Protection for SIM ${slotIndex + 1}")
                        if (label.isNotBlank()) append(", $label")
                        append(if (protectionEnabled) ", on" else ", off")
                    }
                },
            )
        }
        content?.invoke()
    }
}

/** One line in a SIM's rule list: what the rule is, and what it does. */
@Composable
fun ProtectionRuleRow(
    name: String,
    detail: String?,
    action: RuleAction,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    confidence: Confidence? = null,
    provenance: Provenance? = null,
    onClick: (() -> Unit)? = null,
    onToggleAction: (() -> Unit)? = null,
) {
    val shieldColors = LocalShieldColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (confidence != null && provenance != null) {
                VerticalSpacer(4.dp)
                ConfidenceBadge(confidence = confidence, provenance = provenance)
            }
        }
        val stateText = when {
            !enabled -> "OFF"
            action == RuleAction.ALLOW -> "ALLOW"
            else -> "BLOCK"
        }
        val (container, content) = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
            action == RuleAction.ALLOW -> shieldColors.protectedContainer to shieldColors.protected
            else -> shieldColors.blockedContainer to shieldColors.blocked
        }
        StatusPill(
            text = stateText,
            containerColor = container,
            contentColor = content,
            onClick = onToggleAction,
            clickLabel = onToggleAction?.let {
                if (action == RuleAction.ALLOW) {
                    "$name is allowed. Tap to block instead."
                } else {
                    "$name is blocked. Tap to allow instead."
                }
            },
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Says where a rule came from, in the user's language.
 *
 * A community heuristic is never dressed up as an official telecom classification — that
 * distinction is the difference between a tool the user can trust and one they cannot.
 */
@Composable
fun ConfidenceBadge(
    confidence: Confidence,
    provenance: Provenance,
    modifier: Modifier = Modifier,
) {
    val label = when (provenance) {
        Provenance.OFFICIAL, Provenance.OFFICIAL_OR_ESTABLISHED ->
            "${confidence.name.lowercase().replaceFirstChar { it.uppercase() }} confidence · Official"
        Provenance.COMMUNITY -> "Low confidence · Community heuristic"
        Provenance.USER_DEFINED -> "Your rule"
        Provenance.ON_DEVICE_OBSERVATION -> "Observed on this device"
    }
    StatusPill(
        text = label,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A Vault entry. Shows the matched rule so a false positive is instantly explainable. */
@Composable
fun VaultItem(
    number: String,
    ruleName: String,
    simLabel: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shieldColors = LocalShieldColors.current
    AppListRow(
        title = number,
        subtitle = "$ruleName · $simLabel · $timestamp",
        modifier = modifier,
        leading = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(shieldColors.blockedContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = shieldColors.blocked,
                )
            }
        },
        trailing = {
            StatusPill(
                text = "BLOCKED",
                containerColor = shieldColors.blockedContainer,
                contentColor = shieldColors.blocked,
            )
        },
        contentDescription = "Blocked call from $number on $simLabel, $timestamp, $ruleName",
        onClick = onClick,
    )
}
