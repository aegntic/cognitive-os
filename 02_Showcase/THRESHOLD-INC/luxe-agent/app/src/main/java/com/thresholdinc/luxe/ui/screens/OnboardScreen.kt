package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
fun OnboardScreen(vault: EncryptedVault, activeUserEmail: String) {
    var onDeviceOnly by remember { mutableStateOf(true) }
    var showAnalytics by remember { mutableStateOf(false) }
    var autoDraft by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color(0xFF0C0C0E))
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "ONBOARD",
            color = Color(0xFFE8D9C0),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Configure your private workspace",
            color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(modifier = Modifier.height(32.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("AI Processing", color = Color(0xFFE8D9C0), fontSize = 16.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("On-Device Only", color = Color(0xFFF5F0E6), fontSize = 14.sp)
                    Switch(checked = onDeviceOnly, onCheckedChange = { onDeviceOnly = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE8D9C0), checkedTrackColor = Color(0xFFE8D9C0).copy(alpha = 0.3f)))
                }
                Text("Keep all analysis local. No cloud calls.", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Auto-Draft Replies", color = Color(0xFFE8D9C0), fontSize = 16.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Generate drafts for inbound", color = Color(0xFFF5F0E6), fontSize = 14.sp)
                    Switch(checked = autoDraft, onCheckedChange = { autoDraft = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE8D9C0), checkedTrackColor = Color(0xFFE8D9C0).copy(alpha = 0.3f)))
                }
                Text("Anita prepares reply text you can send or edit.", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Anonymous Usage", color = Color(0xFFE8D9C0), fontSize = 16.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Help improve Insidher", color = Color(0xFFF5F0E6), fontSize = 14.sp)
                    Switch(checked = showAnalytics, onCheckedChange = { showAnalytics = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE8D9C0), checkedTrackColor = Color(0xFFE8D9C0).copy(alpha = 0.3f)))
                }
                Text("No personal data. Only feature usage patterns.", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}