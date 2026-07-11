package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
fun BookingsScreen(vault: EncryptedVault, activeUserEmail: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color(0xFF0C0C0E))
            .verticalScroll(rememberScrollState())
    ) {
        Text("BOOKINGS", color = Color(0xFFE8D9C0), fontSize = 28.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 4.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Upcoming, past & deposits", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.height(32.dp))

        // Filter tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Upcoming", "Past", "Deposits", "Payments").forEachIndexed { index, label ->
                val isSelected = index == 0 // placeholder
                Text(
                    text = label,
                    color = if (isSelected) Color(0xFFE8D9C0) else Color(0xFFF5F0E6).copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Normal else FontWeight.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(if (isSelected) Color(0xFFE8D9C0).copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { /* filter */ },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upcoming bookings placeholder
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("UPCOMING", color = Color(0xFFE8D9C0), fontSize = 12.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                    Text("3 bookings", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Jul 15 • 14:00 • Jane Smith • Confirmed • $500 deposit received",
                        "Jul 18 • 10:30 • Robert Chen • Pending deposit • $750",
                        "Jul 22 • 16:00 • Maria Santos • Confirmed • $1,200 paid"
                    ).forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item, color = Color(0xFFF5F0E6), fontSize = 13.sp)
                            IconButton(onClick = { /* details */ }) {
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Details", tint = Color(0xFFE8D9C0))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Past bookings
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("PAST BOOKINGS", color = Color(0xFFE8D9C0), fontSize = 12.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Jul 1 • 11:00 • David Park • Completed • $800",
                        "Jun 28 • 15:00 • Lisa Wong • Completed • $600",
                        "Jun 20 • 09:30 • James Taylor • Cancelled • Deposit refunded"
                    ).forEach { item ->
                        Text(item, color = Color(0xFFF5F0E6).copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Deposit / Payment receipts
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("DEPOSIT / PAYMENT RECEIPTS", color = Color(0xFFE8D9C0), fontSize = 12.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Jul 10 • $500 • Jane Smith • Card ending 4242 • Received",
                        "Jul 8 • $750 • Robert Chen • Bank transfer • Pending",
                        "Jul 5 • $1,200 • Maria Santos • Card ending 8811 • Received"
                    ).forEach { item ->
                        Text(item, color = Color(0xFFF5F0E6), fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}