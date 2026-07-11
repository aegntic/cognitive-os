package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsScreen(
    activeUserEmail: String
) {
    var showActive by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFF0C0C0E))
            .verticalScroll(rememberScrollState())
    ) {
        Text(if (showActive) "ACTIVE ADS" else "DRAFT ADS", color = Color(0xFFE8D9C0), fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(if (showActive) "Live advertisements" else "Saved drafts", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.height(16.dp))

        // Segmented control
        Row(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.TextButton(onClick = { showActive = true }, modifier = Modifier.weight(1f)) {
                Text("Active", color = if (showActive) Color(0xFFE8D9C0) else Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = if (showActive) FontWeight.Normal else FontWeight.Light)
            }
            androidx.compose.material3.TextButton(onClick = { showActive = false }, modifier = Modifier.weight(1f)) {
                Text("Drafts", color = if (!showActive) Color(0xFFE8D9C0) else Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = if (!showActive) FontWeight.Normal else FontWeight.Light)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(if (showActive) activeAds else draftAds) { ad ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ad.title, color = Color(0xFFF5F0E6), fontSize = 16.sp, fontWeight = FontWeight.Normal)
                            Text(if (showActive) "LIVE" else "DRAFT", color = if (showActive) Color(0xFF4CAF50) else Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).background(if (showActive) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ad.description, color = Color(0xFFF5F0E6).copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ad.views} views • ${ad.inquiries} inquiries", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showActive) {
                                    androidx.compose.material3.TextButton(onClick = { /* edit */ }) { Text("Edit", color = Color(0xFFE8D9C0), fontSize = 12.sp) }
                                    androidx.compose.material3.TextButton(onClick = { /* pause */ }) { Text("Pause", color = Color(0xFFFF9800), fontSize = 12.sp) }
                                } else {
                                    androidx.compose.material3.TextButton(onClick = { /* publish */ }) { Text("Publish", color = Color(0xFF4CAF50), fontSize = 12.sp) }
                                    androidx.compose.material3.TextButton(onClick = { /* delete */ }) { Text("Delete", color = Color(0xFFCF6679), fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class AdItem(
    val id: String,
    val title: String,
    val description: String,
    val views: Int,
    val inquiries: Int,
    val createdAt: String
)

val activeAds = listOf(
    AdItem("1", "Elite Companion — NYC", "Discreet, professional companionship for discerning clients. Available for dinner, events, travel.", 1247, 23, "2026-06-15"),
    AdItem("2", "Private Dinner Hostess — LA", "Elegant hostess for private dinners and corporate events. Multilingual.", 892, 11, "2026-06-20"),
    AdItem("3", "Weekend Travel Companion", "Join me for curated weekend getaways. Luxury arrangements included.", 2156, 41, "2026-07-01")
)

val draftAds = listOf(
    AdItem("d1", "VIP Event Companion — Miami", "High-end event companion for Art Basel week. Limited availability.", 0, 0, "2026-07-08"),
    AdItem("d2", "Corporate Retreat Hostess", "Professional hostess for executive retreats. References available.", 0, 0, "2026-07-10")
)