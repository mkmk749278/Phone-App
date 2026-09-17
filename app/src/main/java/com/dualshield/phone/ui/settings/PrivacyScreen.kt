package com.dualshield.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

private val GUARANTEES = listOf(
    "No Internet permission" to
        "The app does not declare one. The build fails if any dependency adds it.",
    "No cloud processing" to
        "Every filtering decision is made on this device, from rules stored on this device.",
    "No analytics or telemetry" to
        "Nothing about your calls, messages or contacts is measured or reported.",
    "No advertising" to "There are no ad SDKs in the app.",
    "No cloud backup" to
        "Your rules and Vault are excluded from cloud backup and device transfer.",
    "Contacts stay local" to
        "Contact lookup uses the phone's own contact database and nothing else.",
)

/**
 * The privacy page.
 *
 * Written as verifiable statements rather than reassurance: each line corresponds to
 * something a reader can confirm in the manifest, the build script or the dependency list.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Privacy", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VerticalSpacer(4.dp)
            Text(
                text = "DualShieldPhone works entirely on this device.",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Protection is local. Rules are stored here, evaluated here, and " +
                    "never leave the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard {
                GUARANTEES.forEach { (title, detail) ->
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "What the app cannot promise",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Android decides whether a blocked call still leaves an entry in the " +
                    "system call log, and the behaviour differs between manufacturers. " +
                    "DualShieldPhone asks the platform to skip it, but never edits the " +
                    "system call log to force the outcome. The Shield Vault is always the " +
                    "complete record.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VerticalSpacer(32.dp)
        }
    }
}
