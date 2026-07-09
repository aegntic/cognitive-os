package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service", fontWeight = FontWeight.Light, fontSize = 20.sp) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Last updated: July 2026",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "1. Agreement to Terms",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "By establishing an encrypted cryptographic vault on this device and utilizing Luxe Threshold services, you unconditionally agree to comply with these terms.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "2. Cryptographic Responsibility",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You acknowledge that the decryption keys are stored in hardware-backed keystores on your device. Luxe has zero capability to reset passwords or recover lost data. All database contents are subject to local cryptographic wipe commands if multiple invalid login attempts occur.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "3. Limits of Automation",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Luxe acts as an advisory coordination agent. You retain full final accountability for client engagement, schedule overlaps, and sovereignty handoffs. The service is provided 'as-is' without warranty of any kind.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "4. System Integrity",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Any attempt to reverse-engineer, exploit, or extract key material from SQLCipher databases or modify the embedded on-device model schema boundaries will lead to immediate termination of access and localized key revocation.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
