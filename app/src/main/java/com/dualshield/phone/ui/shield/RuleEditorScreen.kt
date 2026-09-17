package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SegmentedControl
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.SimScopeSelector
import com.dualshield.phone.ui.components.StatusPill
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.LocalShieldColors

private val BASIC_TYPES = listOf(PatternType.EXACT, PatternType.PREFIX)
private val ADVANCED_TYPES = listOf(PatternType.CONTAINS, PatternType.REGEX, PatternType.SPECIAL)

/**
 * Create or edit a rule.
 *
 * Two things here are load-bearing: the scope selector, which is never pre-set to "Both
 * SIMs", and the tester, which lets the user watch the rule work before it goes live.
 */
@Composable
fun RuleEditorScreen(
    draft: ShieldViewModel.RuleDraft,
    sims: List<SimOption>,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onPattern: (String) -> Unit,
    onPatternType: (PatternType) -> Unit,
    onScope: (com.dualshield.phone.core.model.SimScope) -> Unit,
    onAction: (RuleAction) -> Unit,
    onDescription: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onBlocksCalls: (Boolean) -> Unit,
    onBlocksSms: (Boolean) -> Unit,
    onTestNumber: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var advancedOpen by remember { mutableStateOf(draft.patternType in ADVANCED_TYPES) }
    val shieldColors = LocalShieldColors.current

    LaunchedEffect(draft.saved) {
        if (draft.saved) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(
                title = if (draft.id > 0) "Edit rule" else "Add rule",
                subtitle = if (draft.builtIn) "Built-in rule" else null,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VerticalSpacer(4.dp)

            OutlinedTextField(
                value = draft.name,
                onValueChange = onName,
                label = { Text("Rule name") },
                placeholder = { Text("Promotional calls") },
                singleLine = true,
                enabled = !draft.builtIn,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("What to match", style = MaterialTheme.typography.titleMedium)
            SegmentedControl(
                options = BASIC_TYPES.map { it.label() } + "Advanced",
                selectedIndex = when {
                    draft.patternType in BASIC_TYPES -> BASIC_TYPES.indexOf(draft.patternType)
                    else -> BASIC_TYPES.size
                },
                onSelect = { index ->
                    if (index < BASIC_TYPES.size) {
                        advancedOpen = false
                        onPatternType(BASIC_TYPES[index])
                    } else {
                        advancedOpen = true
                        onPatternType(PatternType.CONTAINS)
                    }
                },
            )

            if (advancedOpen) {
                SectionCard {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) {
                        Text(
                            "Advanced matching",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        VerticalSpacer(4.dp)
                        Text(
                            "These can match more numbers than you expect. Test before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSpacer(10.dp)
                        SegmentedControl(
                            options = ADVANCED_TYPES.map { it.label() },
                            selectedIndex = ADVANCED_TYPES.indexOf(draft.patternType)
                                .coerceAtLeast(0),
                            onSelect = { onPatternType(ADVANCED_TYPES[it]) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.pattern,
                onValueChange = onPattern,
                label = { Text(draft.patternType.fieldLabel()) },
                placeholder = { Text(draft.patternType.placeholder()) },
                singleLine = true,
                isError = draft.patternError != null,
                supportingText = draft.patternError?.let { { Text(it) } },
                enabled = !draft.builtIn,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (draft.patternType == PatternType.REGEX) {
                        KeyboardType.Text
                    } else {
                        KeyboardType.Phone
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Applies to", style = MaterialTheme.typography.titleMedium)
            SimScopeSelector(options = sims, selected = draft.scope, onSelect = onScope)

            Text("Action", style = MaterialTheme.typography.titleMedium)
            SegmentedControl(
                options = listOf("Block", "Always allow"),
                selectedIndex = if (draft.action == RuleAction.BLOCK) 0 else 1,
                onSelect = { onAction(if (it == 0) RuleAction.BLOCK else RuleAction.ALLOW) },
            )

            if (draft.action == RuleAction.BLOCK) {
                Text("Block what?", style = MaterialTheme.typography.titleMedium)
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Calls", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = draft.blocksCalls, onCheckedChange = onBlocksCalls)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Messages", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Filtered messages are still saved, just not announced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = draft.blocksSms, onCheckedChange = onBlocksSms)
                    }
                }
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescription,
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rule is active", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (draft.enabled) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.enabled, onCheckedChange = onEnabled)
            }

            Text("Test this rule", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = draft.testNumber,
                onValueChange = onTestNumber,
                label = { Text("Number to test") },
                placeholder = { Text("1401234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            draft.testOutcome?.let { outcome ->
                SectionCard {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                        StatusPill(
                            text = outcome.headline,
                            containerColor = if (outcome.matched) {
                                shieldColors.blockedContainer
                            } else {
                                shieldColors.protectedContainer
                            },
                            contentColor = if (outcome.matched) {
                                shieldColors.blocked
                            } else {
                                shieldColors.protected
                            },
                        )
                        VerticalSpacer(8.dp)
                        Text(outcome.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Text("Test")
                }
                Button(
                    onClick = onSave,
                    enabled = !draft.builtIn,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }

            if (draft.builtIn) {
                Text(
                    text = "Built-in rules can be switched on or off and pointed at a " +
                        "different SIM, but their pattern stays fixed so an update can " +
                        "keep them accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VerticalSpacer(32.dp)
        }
    }
}

private fun PatternType.label(): String = when (this) {
    PatternType.EXACT -> "Number"
    PatternType.PREFIX -> "Prefix"
    PatternType.CONTAINS -> "Contains"
    PatternType.REGEX -> "Regex"
    PatternType.SPECIAL -> "Caller type"
    PatternType.REPEATED_CALL -> "Repeated"
}

private fun PatternType.fieldLabel(): String = when (this) {
    PatternType.EXACT -> "Phone number"
    PatternType.PREFIX -> "Prefix"
    PatternType.CONTAINS -> "Digits anywhere in the number"
    PatternType.REGEX -> "Regular expression"
    PatternType.SPECIAL -> "Caller type"
    PatternType.REPEATED_CALL -> "Not available yet"
}

private fun PatternType.placeholder(): String = when (this) {
    PatternType.EXACT -> "9876543210"
    PatternType.PREFIX -> "140"
    PatternType.CONTAINS -> "8035"
    PatternType.REGEX -> "^140[0-9]{7}$"
    PatternType.SPECIAL -> "UNKNOWN_CALLER"
    PatternType.REPEATED_CALL -> ""
}
