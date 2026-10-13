package com.orion.store.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** What a swipe reveals and does. [removes] rows slide out and fold away before [run]; the rest fire and spring back. */
class SwipeAction(val icon: ImageVector, val label: String, val bg: Color, val fg: Color, val removes: Boolean, val run: () -> Unit)

/**
 * Swipe a row right ([right]) or left ([left]). The row follows the finger 1:1, the action icon grows and turns in with the travel
 * (read in the draw layer, so dragging never recomposes), the colour fills with a tick once the swipe is armed, and release springs
 * on the expressive scheme. Content must paint its own opaque container.
 */
@Composable
fun SwipeRow(shape: Shape, modifier: Modifier = Modifier, right: SwipeAction? = null, left: SwipeAction? = null, hint: Boolean = false, content: @Composable () -> Unit) {
    val vm = LocalVm.current; val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme
    val trip = with(LocalDensity.current) { 88.dp.toPx() }
    // Plain state while dragging, a cancellable animate() only to settle: per-delta snapTo coroutines used to land after release
    // and cancel the spring-back, leaving rows stuck mid-swipe.
    var x by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun to(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>, then: () -> Unit = {}) {
        settle?.cancel(); settle = scope.launch { animate(x, target, animationSpec = spec) { v, _ -> x = v }; then() }
    }
    var width by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val shown = remember { MutableTransitionState(true) }
    var fired by remember { mutableStateOf<SwipeAction?>(null) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(); val fx = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val dir by remember { derivedStateOf { sign(x) } }
    val armed by remember { derivedStateOf { abs(x) >= trip } }
    LaunchedEffect(armed) { if (armed) vm.haptic(H.SELECTION) }
    LaunchedEffect(hint) { if (hint) { delay(420); to(trip * .7f, spring) { scope.launch { delay(450); to(0f, spring) } } } }
    LaunchedEffect(shown.isIdle, shown.currentState) { if (shown.isIdle && !shown.currentState) fired?.run?.invoke() }
    val drag = rememberDraggableState { d ->
        // Non-removing actions get rubber-band resistance past the trip point, so the row never looks like it is leaving.
        val a = if (x + d >= 0f) right else left
        val k = if (a?.removes == false && abs(x) > trip) .35f else 1f
        x = (x + d * k).coerceIn(if (left != null) -width else 0f, if (right != null) width else 0f)
    }
    AnimatedVisibility(shown, modifier, exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
        val a = if (dir > 0f) right else if (dir < 0f) left else null
        val bg by animateColorAsState(if (armed && a != null) a.bg else cs.surfaceContainerHighest, fx, label = "swipe-bg")
        val fg by animateColorAsState(if (armed && a != null) a.fg else cs.onSurfaceVariant, fx, label = "swipe-fg")
        Box(Modifier.clip(shape).onSizeChanged { width = it.width.toFloat() }.draggable(drag, Orientation.Horizontal, onDragStarted = { settle?.cancel() }, onDragStopped = { v ->
            val act = if (x > 0f) right else if (x < 0f) left else null
            val flung = abs(v) > 1600f && sign(v) == sign(x) && abs(x) > trip * .4f
            when {
                act == null || !(abs(x) >= trip || flung) -> to(0f, spring)
                act.removes -> { vm.haptic(H.MEDIUM); to(sign(x) * width, tween(160, easing = FastOutLinearInEasing)) { fired = act; shown.targetState = false } }
                else -> { vm.haptic(H.SUCCESS); act.run(); to(0f, spring) }
            }
        })) {
            Row(Modifier.matchParentSize().background(bg).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir < 0f) Arrangement.End else Arrangement.Start) {
                if (a != null) Icon(a.icon, a.label, Modifier.graphicsLayer {
                    val k = (abs(x) / trip).coerceIn(0f, 1f)
                    scaleX = .4f + .6f * k; scaleY = scaleX; alpha = k; rotationZ = (1f - k) * -45f * dir
                }, tint = fg)
            }
            Box(Modifier.graphicsLayer { translationX = x }) { content() }
        }
    }
}
