package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
fun ProfileScreen(
    vault: EncryptedVault,
    activeUserEmail: String,
    onNavigateToSettings: () -> Unit
) {
    val userName = vault.getUserName(activeUserEmail)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color(0xFF0C0C0E))
            .verticalScroll(rememberScrollState())
    ) {
        Text("PROFILE", color = Color(0xFFE8D9C0), fontSize = 28.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 4.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Identity, display, gallery", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.height(32.dp))

        // Display pic + name
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFE8D9C0).copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color(0xFFE8D9C0),
                        modifier = Modifier.align(Alignment.Center).size(40.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(userName.ifBlank { "Professional" }, color = Color(0xFFF5F0E6), fontSize = 22.sp, fontWeight = FontWeight.Light)
                    Text(activeUserEmail, color = Color(0xFFF5F0E6).copy(alpha = 0.6f), fontSize = 13.sp)
                }
                androidx.compose.material3.TextButton(onClick = { /* change pic */ }) {
                    Text("Change Display Picture", color = Color(0xFFE8D9C0), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gallery
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("GALLERY", color = Color(0xFFE8D9C0), fontSize = 12.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No media uploaded yet", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = { /* upload */ }) {
                    Text("Upload Images", color = Color(0xFFE8D9C0), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick actions
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.TextButton(onClick = onNavigateToSettings) {
                    Text("Settings", color = Color(0xFFE8D9C0), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                androidx.compose.material3.TextButton(onClick = { /* logout */ }) {
                    Text("Sign Out", color = Color(0xFFCF6679), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}