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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.VerticalSpacer

/**
 * First run: three short steps, no permission checklist.
 *
 * Each permission is requested at the point it is needed instead, which is both better UX
 * and a smaller ask — a user who never opens Messages is never asked for SMS.
 */
@Composable
fun OnboardingScreen(
    sims: List<SimOption>,
    onRequestScreeningRole: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }

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
            when (step) {
                0 -> {
                    Text(
                        "Welcome to\nDualShieldPhone",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    VerticalSpacer(12.dp)
                    Text(
                        "Your calls and messages, your rules, your device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerticalSpacer(28.dp)
                    Button(
                        onClick = {
                            onRequestPhonePermissions()
                            step = 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue")
                    }
                }

                1 -> {
                    Text("Your SIMs", style = MaterialTheme.typography.displaySmall)
                    VerticalSpacer(12.dp)
                    Text(
                        "Each SIM keeps its own rules. Nothing you set up on one line can " +
                            "affect the other.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerticalSpacer(20.dp)
                    SectionCard {
                        val display = sims.ifEmpty {
                            listOf(
                                SimOption(0, "Duty", present = false, protectionEnabled = false),
                                SimOption(1, "Personal", present = false, protectionEnabled = true),
                            )
                        }
                        display.forEach { sim ->
                            Column(
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                            ) {
                                Text(sim.display, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (sim.protectionEnabled) {
                                        "Protection ON"
                                    } else {
                                        "Protection OFF"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    VerticalSpacer(12.dp)
                    Text(
                        "You can change this anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerticalSpacer(24.dp)
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                }

                else -> {
                    Text("Let Shield screen calls", style = MaterialTheme.typography.displaySmall)
                    VerticalSpacer(12.dp)
                    Text(
                        "Android only lets one app screen incoming calls. Granting this is " +
                            "what allows Shield to stop an unwanted call before it rings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerticalSpacer(24.dp)
                    Button(
                        onClick = onRequestScreeningRole,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Allow call screening")
                    }
                    VerticalSpacer(6.dp)
                    TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now")
                    }
                }
            }

            if (step > 0) {
                VerticalSpacer(4.dp)
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(if (step == 2) "Done" else "Skip setup")
                }
            }
        }
    }
}
