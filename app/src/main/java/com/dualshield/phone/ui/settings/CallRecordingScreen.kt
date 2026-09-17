package com.dualshield.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.recording.RecordingCapability
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.RowDivider
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SectionHeading
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/**
 * What this device will and will not let the app record.
 *
 * Most of this screen exists to give an honest answer to a reasonable question. Every other
 * phone app seems to offer call recording, so a user who does not find it here deserves to
 * know why rather than to assume it was forgotten.
 */
@Composable
fun CallRecordingScreen(
    capability: RecordingCapability,
    onRecheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Call recording", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = Spacing.gutter,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "status") {
                SectionCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            text = if (capability.supportsCallRecording) {
                                "Available on this device"
                            } else {
                                "Not available on this device"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        VerticalSpacer(Spacing.sm)
                        Text(
                            text = capability.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (capability.supportsCallRecording) {
                item(key = "recordings-heading") { SectionHeading("Recordings") }
                item(key = "recordings") {
                    SectionCard {
                        AppListRow(
                            title = "No recordings yet",
                            subtitle = "Recordings you make will be listed here",
                        )
                    }
                }
                item(key = "storage-note") {
                    Text(
                        text = "Recordings are kept on this device only. Nothing is uploaded, " +
                            "and nothing is shared unless you share it yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "why-heading") { SectionHeading("Why this is the answer") }
            item(key = "why") {
                SectionCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            text = "Android reserves call audio for the system and for apps " +
                                "that came with the phone. An app you install — this one " +
                                "included — is not given access to the other person's voice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSpacer(Spacing.md)
                        Text(
                            text = "Some apps record the microphone instead and call it call " +
                                "recording. That captures your side and, at best, a faint " +
                                "trace of theirs. This app will not do that: a file that turns " +
                                "out to be half a conversation is worse than knowing in " +
                                "advance that there is no recording.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSpacer(Spacing.md)
                        Text(
                            text = "If your phone's own dialer records calls, it can do so " +
                                "because the manufacturer gave it permissions no third-party " +
                                "app can hold.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSpacer(Spacing.md)
                        Text(
                            text = "This app also does not ask for microphone access. Asking " +
                                "would not make call recording work, and a permission that " +
                                "buys you nothing is not one worth granting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "legal-heading") { SectionHeading("Before you record") }
            item(key = "legal") {
                SectionCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            text = "Whether you may record a call, and whether you must tell " +
                                "the other person, depends on where you and they are. Some " +
                                "phones and networks also announce recording automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSpacer(Spacing.md)
                        Text(
                            text = "This app does not suppress those announcements and does " +
                                "not work around carrier or device restrictions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "recheck") {
                SectionCard {
                    AppListRow(
                        title = "Check again",
                        subtitle = "Re-test what this device allows",
                        onClick = onRecheck,
                    )
                    RowDivider()
                    AppListRow(
                        title = "Detected capability",
                        subtitle = capability.name.lowercase().replace('_', ' '),
                    )
                }
            }

            item(key = "bottom-space") { VerticalSpacer(Spacing.xl) }
        }
    }
}

