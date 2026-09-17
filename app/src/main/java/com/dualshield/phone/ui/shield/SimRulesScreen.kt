package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.ProtectionRuleRow
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * One SIM's rules, and only that SIM's rules.
 *
 * The header repeats which SIM is being edited on every screen in this flow. That redundancy
 * is intentional — there must never be a moment where the user is unsure which line they are
 * changing.
 */
@Composable
fun SimRulesScreen(
    slotIndex: Int,
    state: ShieldViewModel.UiState,
    onBack: () -> Unit,
    onToggleProtection: (Boolean) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onOpenRule: (Long) -> Unit,
    onAddRule: () -> Unit,
    onOpenAllowlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sim = state.sim(slotIndex)
    val rules = state.rulesForSlot(slotIndex)
    val allowCount = state.allowRulesForSlot(slotIndex).size

    val official = rules.filter {
        it.provenance == Provenance.OFFICIAL ||
            it.provenance == Provenance.OFFICIAL_OR_ESTABLISHED
    }
    val userRules = rules.filter { it.provenance == Provenance.USER_DEFINED && !it.builtIn }
    val optional = rules.filter {
        it.confidence == Confidence.LOW ||
            it.patternType == PatternType.SPECIAL ||
            it.category == RuleCategory.BPO_COLLECTION_HEURISTIC
    }.distinctBy { it.id }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = sim?.display ?: "SIM ${slotIndex + 1}",
                subtitle = "Rules on this screen affect this SIM only",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add rule") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Protection", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (sim?.protectionEnabled == true) {
                                    "On · calls on this SIM are filtered"
                                } else {
                                    "Off · every call on this SIM rings"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = sim?.protectionEnabled == true,
                            onCheckedChange = onToggleProtection,
                        )
                    }
                }
            }

            item {
                SectionCard {
                    com.dualshield.phone.ui.components.AppListRow(
                        title = "Allowed numbers",
                        subtitle = "$allowCount numbers always ring through on this SIM",
                        onClick = onOpenAllowlist,
                    )
                }
            }

            if (userRules.isNotEmpty()) {
                item { SectionHeading("Your rules") }
                item {
                    SectionCard {
                        userRules.forEach { rule ->
                            RuleRow(rule, onToggleRule, onOpenRule)
                        }
                    }
                }
            }

            if (official.isNotEmpty()) {
                item { SectionHeading("India protection") }
                item {
                    SectionCard {
                        official.forEach { rule ->
                            RuleRow(rule, onToggleRule, onOpenRule)
                        }
                    }
                }
            }

            if (optional.isNotEmpty()) {
                item { SectionHeading("Optional heuristics") }
                item {
                    Text(
                        text = "Heuristic rules can occasionally match legitimate callers, " +
                            "so they start switched off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionCard {
                        optional.forEach { rule ->
                            RuleRow(rule, onToggleRule, onOpenRule)
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        title = "No rules on this SIM",
                        message = "Add a rule to start filtering calls on this line.",
                    )
                }
            }

            item { VerticalSpacer(90.dp) }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun RuleRow(
    rule: CallRuleEntity,
    onToggleRule: (Long, Boolean) -> Unit,
    onOpenRule: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtectionRuleRow(
            name = rule.name,
            detail = ruleDetail(rule),
            action = rule.action,
            enabled = rule.enabled,
            confidence = rule.confidence,
            provenance = rule.provenance,
            onClick = { onOpenRule(rule.id) },
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggleRule(rule.id, it) },
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

private fun ruleDetail(rule: CallRuleEntity): String = buildString {
    append(rule.category.displayName)
    if (rule.patternType != PatternType.SPECIAL) {
        append(" · ")
        append(
            when (rule.patternType) {
                PatternType.EXACT -> rule.pattern
                PatternType.PREFIX -> "starts with ${rule.pattern}"
                PatternType.CONTAINS -> "contains ${rule.pattern}"
                PatternType.REGEX -> "pattern ${rule.pattern}"
                else -> rule.pattern
            },
        )
    }
    if (rule.matchCount > 0) append(" · ${rule.matchCount} blocked")
}
