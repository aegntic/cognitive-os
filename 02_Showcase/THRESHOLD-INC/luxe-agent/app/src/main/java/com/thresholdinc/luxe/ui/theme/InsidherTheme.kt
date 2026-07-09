package com.thresholdinc.luxe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design tokens for premium liquid glass neumorphism
object InsidherTokens {
    val Obsidian = Color(0xFF0A0A0A)
    val WarmMarbleBase = Color(0xFF12110F)
    val AmberGold = Color(0xFFC5A26F)
    val Specular = Color(0xFFE8D9C0)
    val TextPrimary = Color(0xFFF5F0E6)
    val TextSecondary = Color(0xFF888888)
    val Etched = Color(0xFF0F0E0C)
    val RaisedGlass = Color(0xFF1C1B18)
    val InnerGlow = Color(0xFF3A2F1F).copy(alpha = 0.25f)
}

/**
 * Depth system:
 * - Raised (cards): positive elevation, outer drop shadow (dark bottom-right), specular top-left highlight.
 * - Etched (buttons default, inlays): inset effect via reversed shadows + darker base.
 * Hydraulic animation drives the transition between these states.
 */
@Composable
fun InsidherTheme(content: @Composable () -> Unit) {
    // Minimal override; full colors applied in components for physical feel over flat Material
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = InsidherTokens.Obsidian,
            surface = InsidherTokens.RaisedGlass,
            primary = InsidherTokens.AmberGold,
            onPrimary = InsidherTokens.Obsidian,
            onSurface = InsidherTokens.TextPrimary
        ),
        content = content
    )
}
