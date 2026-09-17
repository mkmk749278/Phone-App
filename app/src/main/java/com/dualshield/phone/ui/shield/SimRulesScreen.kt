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
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.ProtectionRuleRow
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems

/**
 * One SIM's protection, and only that SIM's.
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
    onToggleAllowContacts: (Boolean) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onToggleRuleAction: (CallRuleEntity) -> Unit,
    onOpenRule: (Long) -> Unit,
    onAddRule: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenIndiaProtection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sim = state.sim(slotIndex)
    val rules = state.rulesForSlot(slotIndex)
    val userRules = rules.filterNot { it.builtIn }
    val enabledBuiltIns = rules.filter { it.builtIn && it.enabled }
    val allowCount = state.allowRulesForSlot(slotIndex).size
    val protectionOn = sim?.protectionEnabled == true

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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "switches") {
                SectionCard {
                    SwitchRow(
                        title = "Protection",
                        detail = if (protectionOn) {
                            "On · calls on this SIM are filtered"
                        } else {
                            "Off · every call on this SIM rings"
                        },
                        checked = protectionOn,
                        onCheckedChange = onToggleProtection,
                    )
                    RowDivider(insetStart = 15.dp)
                    SwitchRow(
                        title = "Always allow saved contacts",
                        detail = "Anyone in your contacts gets through, whatever a rule says",
                        checked = sim?.allowContacts != false,
                        enabled = protectionOn,
                        onCheckedChange = onToggleAllowContacts,
                    )
                }
                VerticalSpacer(12.dp)
            }

            item(key = "links") {
                SectionCard {
                    AppListRow(
                        title = "Allowed numbers",
                        subtitle = "$allowCount numbers always ring through on this SIM",
                        onClick = onOpenAllowlist,
                    )
                    RowDivider()
                    AppListRow(
                        title = "India protection",
                        subtitle = "${enabledBuiltIns.size} built-in rules active on this SIM",
                        onClick = onOpenIndiaProtection,
                    )
                }
                VerticalSpacer(14.dp)
            }

            item(key = "your-rules-heading") {
                Text("Your rules on this SIM", style = MaterialTheme.typography.titleMedium)
                VerticalSpacer(8.dp)
            }

            if (userRules.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No rules of your own",
                        message = "Add a number or prefix to filter calls on this line.",
                    )
                }
            } else {
                groupedItems(items = userRules, key = { it.id }, dividerInset = 15.dp) { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProtectionRuleRow(
                            name = RuleDisplay.pattern(rule),
                            detail = RuleDisplay.subtitle(rule, state.sims),
                            action = rule.action,
                            enabled = rule.enabled,
                            onClick = { onOpenRule(rule.id) },
                            onToggleAction = { onToggleRuleAction(rule) },
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggleRule(rule.id, it) },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }

            item(key = "bottom-space") { VerticalSpacer(90.dp) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
