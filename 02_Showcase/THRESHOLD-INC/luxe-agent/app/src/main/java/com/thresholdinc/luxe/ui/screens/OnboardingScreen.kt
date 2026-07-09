package com.thresholdinc.luxe.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
            OnboardingPage(
                title = "WHO REMEMBERS",
                description = "Anita Simpson remembers every client preference, so you don't have to. Your client details, preferences, history — always at hand."
            ),
            OnboardingPage(
                title = "ENCRYPTED MEMORY VAULT",
                description = "All client data and conversations are protected with AES-256 and SQLCipher local encryption. Sovereign keys."
            ),
            OnboardingPage(
                title = "SOVEREIGN COORDINATION",
                description = "Enforce custom JSON structures and sovereignty levels. Anita escalates only when human intervention is vital."
            )
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "I N S I D H E R",
                    color = Color(0xFFE8D9C0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = 6.sp
                )
            }

            // Animated Card
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn() with fadeOut()
                },
                label = "OnboardingContent"
            ) { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = steps[currentStep].title,
                                color = Color(0xFFE8D9C0),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = steps[currentStep].description,
                                color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Step Indicator
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(width = if (index == currentStep) 24.dp else 8.dp, height = 8.dp)
                                    .background(
                                        color = if (index == currentStep) Color(0xFFE8D9C0) else Color(0xFFE8D9C0).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // Navigation Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step < steps.size - 1) {
                    Button(
                        onClick = { step++ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A1A1F),
                            contentColor = Color(0xFFE8D9C0)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Next", fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1A1F),
                                contentColor = Color(0xFFE8D9C0)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                        }

                        Button(
                            onClick = onNavigateToSignUp,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8D9C0),
                                contentColor = Color(0xFF0C0C0E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Register", fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onNavigateToLogin
                ) {
                    Text(
                        text = "Skip to Sign In",
                        color = Color(0xFFE8D9C0).copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

data class OnboardingPage(
    val title: String,
    val description: String
)
