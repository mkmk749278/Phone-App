package com.dualshield.phone.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.VerticalSpacer

private val PROMISES = listOf(
    "Works entirely on this device" to
        "No Internet permission. The build fails if any dependency adds one.",
    "Each SIM is separate" to
        "A rule you write for one line can never affect the other.",
    "Nothing disappears silently" to
        "Blocked calls are kept in your blocked call logs, and filtered messages are still saved.",
)

/**
 * The welcome screen.
 *
 * One page, then straight into Setup. The previous three-step flow asked for permissions
 * behind a "Continue" button and then dropped the user into an app that looked fine but
 * could not actually block anything if they had said no.
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Welcome to\nDualShieldPhone",
                style = MaterialTheme.typography.displaySmall,
            )
            VerticalSpacer(12.dp)
            Text(
                text = "Your calls and messages, your rules, your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VerticalSpacer(24.dp)
            SectionCard {
                PROMISES.forEach { (title, detail) ->
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

            VerticalSpacer(28.dp)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Set up")
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Look around first")
            }
        }
    }
}
