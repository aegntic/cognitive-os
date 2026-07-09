package com.thresholdinc.luxe.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Legacy alias for InsidherCard (raised liquid glass).
 * All new usage should prefer InsidherCard directly.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    InsidherCard(modifier = modifier, content = content)
}
