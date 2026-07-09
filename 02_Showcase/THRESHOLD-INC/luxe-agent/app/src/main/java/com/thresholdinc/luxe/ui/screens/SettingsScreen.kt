package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vault: EncryptedVault,
    activeUserEmail: String,
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToLogout: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var userPromptLimit by remember { mutableStateOf("280 chars") }
    val scrollState = rememberScrollState()

    LaunchedEffect(activeUserEmail) {
        name = vault.getUserName(activeUserEmail)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", fontWeight = FontWeight.Light, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = Color(0xFFE8D9C0)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0C0E),
                    titleContentColor = Color(0xFFE8D9C0)
                )
            )
        },
        containerColor = Color(0xFF0C0C0E)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SECURE PROFILE",
                        color = Color(0xFFE8D9C0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = name.ifBlank { "Luxe Practitioner" },
                        color = Color(0xFFF5F0E6),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = activeUserEmail,
                        color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                }
            }

            // Cryptographic Status Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SECURITY VAULT STATUS",
                        color = Color(0xFFE8D9C0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 2.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Database engine", color = Color(0xFFF5F0E6).copy(alpha = 0.6f), fontSize = 13.sp)
                        Text("SQLCipher 4.5.4", color = Color(0xFFE8D9C0), fontSize = 13.sp, fontWeight = FontWeight.Light)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Key source", color = Color(0xFFF5F0E6).copy(alpha = 0.6f), fontSize = 13.sp)
                        Text("AndroidKeyStore AES-256", color = Color(0xFFE8D9C0), fontSize = 13.sp, fontWeight = FontWeight.Light)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Storage Mode", color = Color(0xFFF5F0E6).copy(alpha = 0.6f), fontSize = 13.sp)
                        Text("Encrypted at rest", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            // Options list
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsRow(title = "Privacy Policy", onClick = onNavigateToPrivacy)
                    Divider(color = Color(0xFFE8D9C0).copy(alpha = 0.1f))
                    SettingsRow(title = "Terms of Service", onClick = onNavigateToTerms)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Action
            Button(
                onClick = onNavigateToLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCF6679).copy(alpha = 0.15f),
                    contentColor = Color(0xFFCF6679)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("LOG OUT OF VAULT", fontWeight = FontWeight.Normal, letterSpacing = 1.5.sp)
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color(0xFFF5F0E6), fontSize = 14.sp, fontWeight = FontWeight.Light)
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFE8D9C0).copy(alpha = 0.4f)
        )
    }
}
