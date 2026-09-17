package com.dualshield.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * Rule pack management.
 *
 * Import and export are manual and file-based. There is no auto-update channel, because an
 * auto-update channel is a network connection, and the app does not have one.
 */
@Composable
fun RulePacksScreen(
    state: SettingsViewModel.UiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
    onShareExport: (String) -> Unit,
    onConsumeExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var importText by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Rule packs", onBack = onBack) },
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
            Text("Installed packs", style = MaterialTheme.typography.titleMedium)

            if (state.packs.isEmpty()) {
                Text(
                    "No rule packs installed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SectionCard {
                    state.packs.forEach { pack ->
                        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                            Text(
                                text = "${pack.packId} · v${pack.packVersion}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${pack.ruleCount} rules · installed " +
                                    Formatting.fullTimestamp(pack.installedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (pack.source.isNotBlank()) {
                                Text(
                                    text = "Source: ${pack.source}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text("Export your rules", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Exports only the rules you created. Built-in packs are not included, " +
                    "because they ship with the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export to JSON")
            }

            state.exportedJson?.let { json ->
                SectionCard {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text(
                            text = "${json.length} characters ready to share.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        VerticalSpacer(10.dp)
                        Button(
                            onClick = { onShareExport(json) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Share")
                        }
                        OutlinedButton(
                            onClick = onConsumeExport,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }

            Text("Import a rule pack", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                label = { Text("Paste pack JSON") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    onImport(importText)
                    importText = ""
                },
                enabled = importText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import")
            }
            Text(
                text = "Rules that fail validation are skipped and reported rather than " +
                    "imported in a broken state.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VerticalSpacer(32.dp)
        }
    }
}
