package com.thresholdinc.insidher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFE8A0BF),
    secondary = Color(0xFFB8A9C9),
    background = Color(0xFF121018),
    surface = Color(0xFF1C1824),
    onPrimary = Color(0xFF1A1014),
    onBackground = Color(0xFFF5EAF0),
    onSurface = Color(0xFFF5EAF0),
)

@Composable
fun InsidherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
