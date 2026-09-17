package com.dualshield.phone.ui.incall

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.incall.SwipeCommit
import com.dualshield.phone.core.incall.SwipeOutcome
import com.dualshield.phone.ui.theme.LocalShieldColors
import kotlin.math.roundToInt

/**
 * Answer or reject a ringing call by dragging, never by a single tap.
 *
 * A phone spends its ringing seconds against a leg, a palm or a table. A tap target that
 * answers a call is a target a pocket can hit, and the cost is not cosmetic: on this product
 * it is a duty call taken silently in someone's bag, or an important one declined by a
 * knuckle. So both actions require a deliberate horizontal drag past a threshold.
 *
 * What that means concretely, and why each part is here:
 *
 *  - **Only the handle is draggable.** Not the whole card, not the caller's name. An area
 *    that reacts to any touch is the thing being avoided, so widening the grab area would
 *    undo the point of it. `detectHorizontalDragGestures` also waits for touch slop, so a
 *    stationary press is not a drag no matter how long it lasts.
 *  - **The threshold is over half the available travel.** Far enough that a brush cannot
 *    reach it, short enough to do one-handed with a thumb.
 *  - **A short drag does nothing at all.** The handle springs back and the call keeps
 *    ringing. There is no "nearly answered" state.
 *  - **It commits exactly once.** [committed] latches, so a second finger, a bounce, or a
 *    recomposition cannot fire the action twice. Telecom would refuse the duplicate anyway,
 *    but the guard belongs where the gesture is rather than two layers away.
 *
 * Screen readers cannot drag. TalkBack users get the same two actions through the
 * accessibility actions menu, which takes a deliberate focus-and-choose of its own — that is
 * not the one-tap button being excluded here, and locking those users out of answering their
 * own phone would be a worse failure than the one this guards against.
 *
 * The handle position is plain state, updated synchronously as the finger moves, with the
 * animation only on the way back. Driving it through an `Animatable` from the drag callback
 * looks tidier and is wrong: each `snapTo` cancels the one before it, so fast drags lose
 * deltas and the handle trails the finger.
 */
@Composable
fun CallAnswerSwipe(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shieldColors = LocalShieldColors.current
    val density = LocalDensity.current

    var dragPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }

    val handlePx by animateFloatAsState(
        targetValue = dragPx,
        // Following a finger must not be animated at all; letting go must be.
        animationSpec = if (dragging) snap() else tween(RETURN_MILLIS),
        label = "callSwipeHandle",
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
        val travelPx = with(density) {
            ((maxWidth - HANDLE_SIZE) / 2 - TRACK_PADDING).coerceAtLeast(0.dp).toPx()
        }
        val thresholdPx = SwipeCommit.thresholdPx(
            travelPx = travelPx,
            minDistancePx = with(density) { MIN_COMMIT_DISTANCE.toPx() },
        )

        // How far along the user is, each way, for the directional feedback. Zero until they
        // move, so a resting handle gives neither target any emphasis.
        val progress = if (travelPx <= 0f) 0f else (handlePx / travelPx).coerceIn(-1f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .clip(CircleShape)
                .background(shieldColors.onHeroSurface.copy(alpha = 0.12f)),
        ) {
            SwipeTarget(
                alignment = Alignment.CenterStart,
                color = MaterialTheme.colorScheme.error,
                emphasis = (-progress).coerceAtLeast(0f),
                label = "Reject",
                icon = Icons.Filled.CallEnd,
            )
            SwipeTarget(
                alignment = Alignment.CenterEnd,
                color = shieldColors.protected,
                emphasis = progress.coerceAtLeast(0f),
                label = "Answer",
                icon = Icons.Filled.Call,
            )

            Text(
                text = when {
                    committed -> ""
                    progress > HINT_PROGRESS -> "Keep going to answer"
                    progress < -HINT_PROGRESS -> "Keep going to reject"
                    else -> "Swipe right to answer, left to reject"
                },
                style = MaterialTheme.typography.labelMedium,
                color = shieldColors.onHeroSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = HANDLE_SIZE),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(handlePx.roundToInt(), 0)
                        }
                    }
                    .size(HANDLE_SIZE)
                    .clip(CircleShape)
                    .background(shieldColors.onHeroSurface)
                    .semantics {
                        contentDescription =
                            "Swipe right to answer, left to reject. Drag past halfway."
                        customActions = listOf(
                            CustomAccessibilityAction("Answer") {
                                if (committed) {
                                    false
                                } else {
                                    committed = true
                                    onAnswer()
                                    true
                                }
                            },
                            CustomAccessibilityAction("Reject") {
                                if (committed) {
                                    false
                                } else {
                                    committed = true
                                    onReject()
                                    true
                                }
                            },
                        )
                    }
                    .pointerInput(travelPx, thresholdPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { if (!committed) dragging = true },
                            onDragEnd = {
                                dragging = false
                                when (SwipeCommit.outcome(dragPx, thresholdPx, committed)) {
                                    SwipeOutcome.ANSWER -> {
                                        committed = true
                                        onAnswer()
                                    }
                                    SwipeOutcome.REJECT -> {
                                        committed = true
                                        onReject()
                                    }
                                    // Ended short, or something already committed. Nothing
                                    // happened, and the handle says so by going back where
                                    // it started — there is no partial state to recover.
                                    SwipeOutcome.NONE -> if (!committed) dragPx = 0f
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                if (!committed) dragPx = 0f
                            },
                        ) { change, dragAmount ->
                            if (committed) return@detectHorizontalDragGestures
                            change.consume()
                            dragPx = (dragPx + dragAmount).coerceIn(-travelPx, travelPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (progress < -HINT_PROGRESS) {
                        Icons.Filled.CallEnd
                    } else {
                        Icons.Filled.Call
                    },
                    contentDescription = null,
                    tint = shieldColors.heroSurface,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SwipeTarget(
    alignment: Alignment,
    color: Color,
    emphasis: Float,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = TRACK_PADDING)
            .size(TARGET_SIZE)
            .clip(CircleShape)
            // The target lights up as the handle approaches it, so the direction of travel
            // is legible before the threshold rather than only after it.
            .background(color.copy(alpha = RESTING_ALPHA + emphasis * (1f - RESTING_ALPHA)))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}

private val TRACK_HEIGHT = 84.dp
private val HANDLE_SIZE = 68.dp
private val TARGET_SIZE = 52.dp
private val TRACK_PADDING = 8.dp

/**
 * The shortest drag that can commit, whatever the screen width.
 *
 * The fraction of the travel lives in [SwipeCommit] with the rest of the decision; this is
 * the floor in real millimetres, which only the UI layer knows how to express.
 */
private val MIN_COMMIT_DISTANCE = 56.dp
private const val RESTING_ALPHA = 0.35f
private const val RETURN_MILLIS = 180

/** Movement before the wording changes, so the hint does not flicker under a resting thumb. */
private const val HINT_PROGRESS = 0.1f
