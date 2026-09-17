package com.dualshield.phone.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.search.DialerResult
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.DetailHeader
import com.dualshield.phone.ui.components.SectionCard
import com.dualshield.phone.ui.components.SimChipRow
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/**
 * The height of the search-results area above the keypad.
 *
 * Fixed on purpose. A results list that grows and shrinks with the query moves every key
 * underneath it, so the second digit of a number lands somewhere the first one did not —
 * which is the single most jarring thing a dialer can do.
 */
private val RESULTS_AREA_HEIGHT = 196.dp

private data class DialKey(val digit: Char, val letters: String? = null)

private val DIAL_KEYS = listOf(
    DialKey('1'),
    DialKey('2', "ABC"),
    DialKey('3', "DEF"),
    DialKey('4', "GHI"),
    DialKey('5', "JKL"),
    DialKey('6', "MNO"),
    DialKey('7', "PQRS"),
    DialKey('8', "TUV"),
    DialKey('9', "WXYZ"),
    DialKey('*'),
    DialKey('0', "+"),
    DialKey('#'),
)

/**
 * A conventional dialer.
 *
 * The only thing here that is not standard is the SIM chip row directly above the keypad:
 * the line a call will leave from is always on screen before the call button is pressed.
 */
@Composable
fun DialpadScreen(
    state: PhoneViewModel.UiState,
    suggestions: List<DialerResult>,
    onDigit: (Char) -> Unit,
    onZeroLongPress: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onCall: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSim = state.sims.firstOrNull { it.slotIndex == state.selectedSlot }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(title = "Dial", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerticalSpacer(Spacing.md)
            Text(
                text = state.dialInput.ifEmpty { " " },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .semantics {
                        contentDescription = if (state.dialInput.isEmpty()) {
                            "No number entered"
                        } else {
                            state.dialInput.toCharArray().joinToString(" ")
                        }
                    },
            )

            // The results area is a fixed slot, not a block that pushes the keypad around.
            // Its height never changes with the number of results, so the keys stay exactly
            // where the user's thumb left them while the list above fills and empties.
            VerticalSpacer(Spacing.sm)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RESULTS_AREA_HEIGHT),
            ) {
                if (suggestions.isNotEmpty()) {
                    SectionCard {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(
                                items = suggestions,
                                key = { "${it.contact.id}:${it.number.matchKey}" },
                            ) { result ->
                                AppListRow(
                                    // Same trap as the Contacts list: a contact with no
                                    // name is named after its number, and putting the
                                    // number underneath prints the same digits twice.
                                    title = result.contact.displayName,
                                    subtitle = if (result.contact.hasName) {
                                        result.number.display
                                    } else {
                                        result.number.typeLabel.takeIf { it.isNotBlank() }
                                    },
                                    leading = {
                                        ContactAvatar(
                                            result.contact.displayName,
                                            result.number.raw,
                                            photoUri = result.contact.photoUri,
                                            size = 38.dp,
                                        )
                                    },
                                    onClick = { onPickSuggestion(result.number.raw) },
                                )
                            }
                        }
                    }
                }
            }

            VerticalSpacer(Spacing.md)
            SimChipRow(
                options = state.sims,
                selectedSlot = state.selectedSlot,
                onSelect = onSelectSim,
            )

            VerticalSpacer(Spacing.lg)
            DialGrid(onDigit = onDigit, onZeroLongPress = onZeroLongPress)

            VerticalSpacer(Spacing.md)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(64.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .combinedClickableSafe(
                            enabled = state.dialInput.isNotEmpty(),
                            onClick = { onCall(state.dialInput) },
                        )
                        .semantics {
                            contentDescription = selectedSim
                                ?.let { "Call using ${it.display}" }
                                ?: "Call"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.dialInput.isNotEmpty()) {
                        IconButton(onClick = onBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete last digit",
                            )
                        }
                    }
                }
            }

            if (selectedSim != null && state.dialInput.isNotEmpty()) {
                VerticalSpacer(Spacing.sm)
                Text(
                    text = "Calling with ${selectedSim.display}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalSpacer(Spacing.lg)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialGrid(
    onDigit: (Char) -> Unit,
    onZeroLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DIAL_KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                onClick = { onDigit(key.digit) },
                                onLongClick = if (key.digit == '0') {
                                    { onDigit('0'); onZeroLongPress() }
                                } else {
                                    null
                                },
                            )
                            .semantics {
                                contentDescription = buildString {
                                    append(key.digit)
                                    if (key.digit == '0') append(", long press for plus")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = key.digit.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            if (key.letters != null) {
                                Text(
                                    text = key.letters,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = this.combinedClickable(enabled = enabled, onClick = onClick)
