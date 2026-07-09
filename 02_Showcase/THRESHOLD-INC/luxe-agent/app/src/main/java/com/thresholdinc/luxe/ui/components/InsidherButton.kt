package com.thresholdinc.luxe.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.ui.theme.InsidherTokens
import kotlinx.coroutines.launch

/**
 * InsidherButton — hydraulic premium interaction (exact spec).
 *
 * Depth + animation logic:
 * - Default (raised): floating above plane with outer shadow + specular.
 * - Press (60-80ms): sinks/etches into surface (fast tween to positive depth).
 * - Release (~1150ms): slow damped hydraulic rise back to raised (LinearOutSlowIn).
 * - Never snaps. Uses Animatable for continuous control.
 * - 11.11° isometric tilt applied on press via graphicsLayer (subtle perspective).
 * - Primary: warm amber-gold fill.
 * - Ghost: etched glass (inset look by default).
 *
 * State hoisted via internal press tracking + pointerInput for precise timing.
 */
@Composable
fun InsidherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val sink = remember { Animatable(0f) }

    // Hydraulic timing
    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Fast etch/sink
            sink.animateTo(
                targetValue = 9f,
                animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)
            )
        } else {
            // Slow hydraulic rise
            sink.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1150, easing = LinearOutSlowInEasing)
            )
        }
    }

    val tilt = if (isPressed) 11.11f else 0f
    val shape = RoundedCornerShape(10.dp)

    val baseColor = if (isPrimary) InsidherTokens.AmberGold else InsidherTokens.Etched

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = sink.value
                rotationX = tilt * 0.65f
                rotationY = -tilt * 0.35f
                cameraDistance = 12f * density
            }
            .shadow(
                elevation = if (isPressed) 2.dp else 10.dp,
                shape = shape,
                ambientColor = if (isPressed) Color.Black.copy(0.3f) else Color.Black.copy(0.5f),
                spotColor = Color.Black.copy(0.4f)
            )
            .background(baseColor, shape)
            .drawBehind {
                // Etched inset effect when sunk (reversed lighting)
                if (isPressed || !isPrimary) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height * 0.5f)
                        )
                    )
                } else {
                    // Raised specular when not pressed (primary)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                InsidherTokens.Specular.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.5f, size.height * 0.3f)
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.changes.any { it.pressed } && !isPressed -> {
                                isPressed = true
                            }
                            !event.changes.any { it.pressed } && isPressed -> {
                                isPressed = false
                                // trigger click on release (hydraulic rise already animating)
                                scope.launch { /* click fired via clickable below */ }
                            }
                        }
                    }
                }
            }
            .clickable(
                onClick = onClick,
                indication = null, // custom hydraulic instead of ripple
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
