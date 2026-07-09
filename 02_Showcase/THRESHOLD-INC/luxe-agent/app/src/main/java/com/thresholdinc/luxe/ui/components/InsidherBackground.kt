package com.thresholdinc.luxe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.thresholdinc.luxe.ui.theme.InsidherTokens

/**
 * Marble + obsidian background with subtle veining and amber-gold ambient lighting.
 * Top-left and bottom-right lighting as per direction.
 * No external assets — physical simulation via gradients + paths.
 */
@Composable
fun InsidherBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(InsidherTokens.Obsidian)
            .fillMaxSize()
    ) {
        // Ambient lighting gradients (amber gold top-left + bottom-right)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top-left amber wash
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        InsidherTokens.AmberGold.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.6f, size.height * 0.4f)
                )
            )
            // Bottom-right amber wash
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        InsidherTokens.AmberGold.copy(alpha = 0.05f)
                    ),
                    start = Offset(size.width * 0.4f, size.height * 0.6f),
                    end = Offset(size.width, size.height)
                )
            )

            // Subtle marble veining (warm low-alpha paths)
            val veinColor = InsidherTokens.Specular.copy(alpha = 0.035f)
            val path1 = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.2f)
                cubicTo(
                    size.width * 0.3f, size.height * 0.15f,
                    size.width * 0.5f, size.height * 0.35f,
                    size.width * 0.75f, size.height * 0.18f
                )
            }
            drawPath(path1, veinColor, style = Stroke(width = 1.5.dp.toPx()))

            val path2 = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.55f)
                cubicTo(
                    size.width * 0.4f, size.height * 0.48f,
                    size.width * 0.65f, size.height * 0.7f,
                    size.width * 0.85f, size.height * 0.52f
                )
            }
            drawPath(path2, veinColor, style = Stroke(width = 1.dp.toPx()))

            // Fine grain texture simulation (sparse)
            for (i in 0..18) {
                val x = (i * 47 + 13) % size.width.toInt()
                val y = (i * 31 + 7) % size.height.toInt()
                drawCircle(
                    color = InsidherTokens.Specular.copy(alpha = 0.015f),
                    radius = 0.6f,
                    center = Offset(x.toFloat(), y.toFloat())
                )
            }
        }
        content()
    }
}
