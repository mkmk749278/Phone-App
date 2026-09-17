package com.dualshield.phone.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualshield.phone.telecom.UiCall
import com.dualshield.phone.ui.components.ContactAvatar
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.LocalShieldColors
import kotlinx.coroutines.delay

/**
 * The incoming / ongoing call surface.
 *
 * Standard on purpose. Shield never decorates a legitimate call — if a call reaches this
 * screen, Shield has already decided it belongs here, and the user should see an ordinary
 * phone, not a security product.
 */
@Composable
fun InCallScreen(
    call: UiCall?,
    muted: Boolean,
    speakerOn: Boolean,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shieldColors = LocalShieldColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(call?.id, call?.isActive) {
        while (call?.isActive == true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(shieldColors.heroSurface),
    ) {
        if (call == null) {
            Text(
                text = "No active call",
                style = MaterialTheme.typography.titleLarge,
                color = shieldColors.onHeroSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerticalSpacer(56.dp)
            Text(
                text = when {
                    call.isRinging -> "Incoming call"
                    call.isDialing -> "Calling"
                    call.isOnHold -> "On hold"
                    else -> "Connected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = shieldColors.onHeroSurfaceMuted,
            )
            VerticalSpacer(20.dp)
            ContactAvatar(
                name = call.displayName,
                number = call.number,
                size = 92.dp,
                containerColor = shieldColors.onHeroSurface.copy(alpha = 0.12f),
                contentColor = shieldColors.onHeroSurface,
            )
            VerticalSpacer(18.dp)
            Text(
                text = call.displayName?.takeIf { it.isNotBlank() }
                    ?: Formatting.displayNumber(call.number),
                style = MaterialTheme.typography.displaySmall,
                color = shieldColors.onHeroSurface,
                textAlign = TextAlign.Center,
            )
            if (!call.displayName.isNullOrBlank()) {
                Text(
                    text = Formatting.displayNumber(call.number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = shieldColors.onHeroSurfaceMuted,
                )
            }
            VerticalSpacer(8.dp)
            Text(
                text = Formatting.simLabel(call.slotIndex, call.simLabel),
                style = MaterialTheme.typography.labelLarge,
                color = shieldColors.onHeroSurfaceMuted,
            )
            if (call.isActive) {
                VerticalSpacer(8.dp)
                Text(
                    text = Formatting.elapsed(call.connectTimeMillis, now),
                    style = MaterialTheme.typography.titleMedium,
                    color = shieldColors.onHeroSurface,
                )
            }

            VerticalSpacer(40.dp)

            if (call.isActive || call.isOnHold) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ControlButton(
                        active = muted,
                        onClick = onToggleMute,
                        contentDescription = if (muted) "Unmute" else "Mute",
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = shieldColors.onHeroSurface,
                        )
                    }
                    ControlButton(
                        active = speakerOn,
                        onClick = onToggleSpeaker,
                        contentDescription = if (speakerOn) "Speaker off" else "Speaker on",
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = shieldColors.onHeroSurface,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (call.isRinging) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.Center
                },
            ) {
                if (call.isRinging) {
                    CallActionButton(
                        color = MaterialTheme.colorScheme.error,
                        contentDescription = "Decline call",
                        onClick = onReject,
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                    }
                    CallActionButton(
                        color = shieldColors.protected,
                        contentDescription = "Accept call",
                        onClick = onAnswer,
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White)
                    }
                } else {
                    CallActionButton(
                        color = MaterialTheme.colorScheme.error,
                        contentDescription = "End call",
                        onClick = onHangUp,
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                    }
                }
            }
            VerticalSpacer(40.dp)
        }
    }
}

@Composable
private fun ControlButton(
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val shieldColors = LocalShieldColors.current
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(
                shieldColors.onHeroSurface.copy(alpha = if (active) 0.28f else 0.12f),
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun CallActionButton(
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}
