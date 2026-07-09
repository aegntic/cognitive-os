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
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Light, fontSize = 20.sp) },
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
                        text = "1. Cryptographic Security & Sovereignty",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Luxe Threshold operates on a sovereignty-first architecture. All user data, client inquiries, and decisions are stored locally in an encrypted SQLCipher database, protected by keys generated in your device's Android Keystore.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "2. On-Device AI Execution",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When using the 'On-Device' decision pathway, inference is executed strictly inside your local runtime sandbox using quantized models. Inquiries are never transmitted to cloud servers in this mode.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "3. Third-Party Connections",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cloud-based inferences transmit anonymized inquiry text payloads only to the designated private LLM gateway. No personally identifiable details or database keys are ever shared.",
                        color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "4. Data Control",
                        color = Color(0xFFE8D9C0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Because your database is cryptographic and completely localized, we hold zero capability to retrieve, reset, or transfer your vault. If you uninstall the app or wipe data, the vault is permanently unrecoverable.",
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
