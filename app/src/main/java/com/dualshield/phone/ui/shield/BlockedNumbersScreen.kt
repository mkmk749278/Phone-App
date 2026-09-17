package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.EmptyState
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.StatusPill
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.components.groupedItems
import com.dualshield.phone.ui.theme.LocalShieldColors
import com.dualshield.phone.ui.theme.Spacing

/**
 * The blocklist, as a flat list of patterns.
 *
 * This is the screen the previous version was missing: rules existed, but they were buried
 * two levels down inside a SIM, so "where are my blocking rules" had no good answer. Here
 * everything the user has blocked is in one place, shown as the pattern itself.
 *
 * Tapping the BLOCK/ALLOW pill flips the rule inline — previously that pill was decoration.
 */
@Composable
fun BlockedNumbersScreen(
    rules: List<CallRuleEntity>,
    sims: List<SimOption>,
    onBack: () -> Unit,
    onOpenRule: (Long) -> Unit,
    onToggleAction: (CallRuleEntity) -> Unit,
    onAdd: (AddRuleKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    val shieldColors = LocalShieldColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = "Blocked numbers",
                subtitle = if (rules.isEmpty()) null else "${rules.size} blocked numbers",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
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
            if (rules.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing blocked yet",
                        message = "Add a number or a prefix, and calls matching it will go " +
                            "straight to your blocked call logs instead of ringing.",
                    )
                }
            } else {
                groupedItems(items = rules, key = { it.id }, dividerInset = Spacing.gutter) { rule ->
                    val blocking = rule.action == RuleAction.BLOCK
                    AppListRow(
                        title = RuleDisplay.patternOrName(rule),
                        subtitle = RuleDisplay.subtitle(rule, sims),
                        trailing = {
                            StatusPill(
                                text = if (blocking) "BLOCK" else "ALLOW",
                                containerColor = if (blocking) {
                                    shieldColors.blockedContainer
                                } else {
                                    shieldColors.protectedContainer
                                },
                                contentColor = if (blocking) {
                                    shieldColors.blocked
                                } else {
                                    shieldColors.protected
                                },
                                onClick = { onToggleAction(rule) },
                                clickLabel = if (blocking) {
                                    "Blocking. Tap to allow instead."
                                } else {
                                    "Allowing. Tap to block instead."
                                },
                            )
                        },
                        contentDescription = "${RuleDisplay.patternOrName(rule)}, " +
                            RuleDisplay.subtitle(rule, sims),
                        onClick = { onOpenRule(rule.id) },
                    )
                }
            }

            item(key = "hint") {
                VerticalSpacer(Spacing.lg)
                Text(
                    text = "A prefix like 140 blocks every number that starts with it. " +
                        "Tap BLOCK or ALLOW to flip an entry without opening it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "bottom-space") { VerticalSpacer(Spacing.listBottomInset) }
        }
    }

    if (showAddSheet) {
        AddToListSheet(
            onPick = {
                showAddSheet = false
                onAdd(it)
            },
            onDismiss = { showAddSheet = false },
        )
    }
}
