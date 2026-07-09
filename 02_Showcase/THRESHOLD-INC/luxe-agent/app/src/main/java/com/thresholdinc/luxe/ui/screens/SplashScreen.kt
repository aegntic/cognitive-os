package com.thresholdinc.luxe.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.data.EncryptedVault
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    vault: EncryptedVault,
    onNavigateNext: (isLoggedIn: Boolean) -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        android.util.Log.d("LuxeSplash", "LaunchedEffect started")
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200)
        )

        // Check session in encrypted vault
        val activeUser = try {
            android.util.Log.d("LuxeSplash", "Calling vault.getConfig")
            val result = vault.getConfig("active_user")
            android.util.Log.d("LuxeSplash", "vault.getConfig returned: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("LuxeSplash", "Error getting config", e)
            null
        }
        android.util.Log.d("LuxeSplash", "Calling onNavigateNext with: ${activeUser != null}")
        onNavigateNext(activeUser != null)

        // Fallback: force navigation after 3s if callback didn't fire
        delay(3000)
        val activeUser2 = try {
            vault.getConfig("active_user")
        } catch (e: Exception) {
            null
        }
        if (activeUser2 == null) {
            android.util.Log.d("LuxeSplash", "Fallback: calling onNavigateNext(false)")
            onNavigateNext(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            Text(
                text = "I N S I D H E R",
                color = Color(0xFFE8D9C0),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ANITA SIMPSON",
                color = Color(0xFFE8D9C0).copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = Color(0xFFE8D9C0),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
