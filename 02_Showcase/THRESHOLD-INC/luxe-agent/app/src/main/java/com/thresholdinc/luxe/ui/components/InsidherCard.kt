package com.thresholdinc.luxe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thresholdinc.luxe.ui.theme.InsidherTokens

/**
 * InsidherCard — strongly raised liquid glass.
 * Depth system:
 * - Outer drop shadow (strong layered for raised feel above marble).
 * - Specular top-left highlight (ambient gold lighting).
 * - Inner liquid thickness via gradient overlay + inner glow.
 * - Base is warm marble-tinted glass.
 * Cards sit clearly above background plane.
 */
@Composable
fun InsidherCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .background(
                color = InsidherTokens.RaisedGlass,
                shape = shape
            )
            .drawBehind {
                // Inner liquid glow + thickness
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            InsidherTokens.InnerGlow,
                            Color.Transparent,
                            Color.Transparent
                        )
                    ),
                    size = size
                )
                // Specular top-left highlight
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            InsidherTokens.Specular.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.35f)
                    ),
                    size = size
                )
            }
            .padding(16.dp)
    ) {
        content()
    }
}
