package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.ConfidenceBadge
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.SimChipRow
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems
import com.dualshield.phone.ui.theme.Spacing

/**
 * The built-in India rule pack, grouped by what each series actually is.
 *
 * Categories are named for how the numbering is allocated — promotional, transactional,
 * toll-free, premium-rate — rather than lumping everything unwanted under "spam". A user who
 * knows 1600 is their bank's service line can make a different decision from one who is
 * getting collection calls on it.
 */
@Composable
fun IndiaProtectionScreen(
    rules: List<CallRuleEntity>,
    sims: List<SimOption>,
    selectedSlot: Int?,
    onSelectSlot: (Int) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onOpenRule: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoped = selectedSlot
        ?.let { slot -> rules.filter { it.simScope.coversSlot(slot) } }
        ?: rules
    val selected = selectedSlot?.let { slot -> sims.firstOrNull { it.slotIndex == slot } }

    // Exactly one bucket per rule. Three independent filters would let a rule land in two
    // sections of the same lazy list, and a duplicate key there is a crash, not a cosmetic
    // bug — so the partition is exhaustive and ordered rather than three `filter` calls.
    val callerTypeCategories = setOf(
        RuleCategory.UNKNOWN_CALLER,
        RuleCategory.PRIVATE_CALLER,
        RuleCategory.INTERNATIONAL,
    )
    val official = ArrayList<CallRuleEntity>()
    val callerTypes = ArrayList<CallRuleEntity>()
    val heuristic = ArrayList<CallRuleEntity>()
    val other = ArrayList<CallRuleEntity>()
    for (rule in scoped) {
        when {
            rule.category in callerTypeCategories -> callerTypes += rule
            rule.confidence == Confidence.LOW -> heuristic += rule
            rule.provenance == Provenance.OFFICIAL ||
                rule.provenance == Provenance.OFFICIAL_OR_ESTABLISHED -> official += rule
            else -> other += rule
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = "India blocklist",
                subtitle = "Built-in rules for Indian numbering series",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap),
        ) {
            item(key = "sim-picker") {
                Text(
                    text = "Showing rules for",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VerticalSpacer(Spacing.sm)
                SimChipRow(options = sims, selectedSlot = selectedSlot, onSelect = onSelectSlot)
                VerticalSpacer(Spacing.lg)
            }

            // Every switch below is per-SIM, and this screen is the easiest place in the app
            // to read a row of "on" toggles as protection. Say otherwise when it is otherwise.
            if (selected != null && !selected.protectionEnabled) {
                item(key = "protection-off") {
                    ProtectionOffNotice(
                        sim = selected,
                        detail = "These built-in rules stay as you set them, but nothing on " +
                            "this screen is applied to calls on ${selected.display} until " +
                            "protection is turned on for it.",
                    )
                    VerticalSpacer(Spacing.lg)
                }
            }

            if (official.isNotEmpty()) {
                section("Regulated series", "Allocated by India's numbering plan.")
                groupedItems(items = official, key = { it.id }, dividerInset = Spacing.gutter) { rule ->
                    IndiaRuleRow(rule, onToggleRule, onOpenRule)
                }
            }

            if (callerTypes.isNotEmpty()) {
                section(
                    "Caller types",
                    "Off by default — each of these also catches legitimate callers.",
                )
                groupedItems(items = callerTypes, key = { it.id }, dividerInset = Spacing.gutter) { rule ->
                    IndiaRuleRow(rule, onToggleRule, onOpenRule)
                }
            }

            if (other.isNotEmpty()) {
                section("Other rules", "Built-in rules that do not fit the groups above.")
                groupedItems(items = other, key = { it.id }, dividerInset = Spacing.gutter) { rule ->
                    IndiaRuleRow(rule, onToggleRule, onOpenRule)
                }
            }

            if (heuristic.isNotEmpty()) {
                section(
                    "Optional heuristics",
                    "Community observations, not telecom classifications. They can match " +
                        "real businesses, so they ship switched off.",
                )
                groupedItems(items = heuristic, key = { it.id }, dividerInset = Spacing.gutter) { rule ->
                    IndiaRuleRow(rule, onToggleRule, onOpenRule)
                }
            }

            item(key = "bottom-space") { VerticalSpacer(Spacing.xl) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    caption: String,
) {
    item(key = "section-$title") {
        VerticalSpacer(Spacing.lg)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VerticalSpacer(Spacing.sm)
    }
}

@Composable
private fun IndiaRuleRow(
    rule: CallRuleEntity,
    onToggleRule: (Long, Boolean) -> Unit,
    onOpenRule: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // The rule's name already carries its series — "1600 Transactional Service" —
            // so the row needs no second line repeating the pattern. What it did carry was
            // the raw expression, which said nothing to anyone who had not written it.
            Text(rule.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = RuleDisplay.status(rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.description.isNotBlank()) {
                VerticalSpacer(Spacing.xs)
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalSpacer(Spacing.sm)
            ConfidenceBadge(confidence = rule.confidence, provenance = rule.provenance)
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggleRule(rule.id, it) },
            modifier = Modifier.semantics {
                contentDescription = "${rule.name}, ${if (rule.enabled) "on" else "off"}"
            },
        )
    }
}
