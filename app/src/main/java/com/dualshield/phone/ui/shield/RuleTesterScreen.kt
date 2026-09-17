package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimChipRow
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.components.StatusPill
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.LocalShieldColors

/**
 * Runs the real engine against a number, changing nothing.
 *
 * This is the app's honesty mechanism. Whatever this screen says is exactly what the
 * screening service would do with the next call from that number on that SIM.
 */
@Composable
fun RuleTesterScreen(
    tester: ShieldViewModel.TesterState,
    sims: List<SimOption>,
    onNumberChange: (String) -> Unit,
    onSlotChange: (Int) -> Unit,
    onRun: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shieldColors = LocalShieldColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Rule tester", onBack = onBack) },
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
            Text(
                text = "Nothing is saved or changed while you test.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = tester.number,
                onValueChange = onNumberChange,
                label = { Text("Number") },
                placeholder = { Text("1401234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Arriving on", style = MaterialTheme.typography.titleMedium)
            SimChipRow(
                options = sims,
                selectedSlot = tester.slotIndex,
                onSelect = onSlotChange,
            )

            Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text("Test this number")
            }

            tester.outcome?.let { outcome ->
                SectionCard {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
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
                        VerticalSpacer(10.dp)
                        Text(outcome.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            VerticalSpacer(32.dp)
        }
    }
}
