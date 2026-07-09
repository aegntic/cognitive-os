package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.ui.components.InsidherCard
import com.thresholdinc.luxe.ui.theme.InsidherTokens

@Composable
fun ScheduleScreen(log: List<String> = emptyList()) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Schedule • Sovereign Coordination", color = InsidherTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))

        InsidherCard {
            Column {
                Text("Coordination proposals pulled from vault memories and Anita decisions.", color = InsidherTokens.TextSecondary, fontSize = 12.sp)
                Text("Sovereignty hooks active: conflict detection, preference enforcement, audit.", color = InsidherTokens.TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Recent coordination signals", color = InsidherTokens.TextPrimary, fontSize = 14.sp)
        LazyColumn {
            items(log.takeLast(8)) { entry ->
                InsidherCard {
                    Text(entry, color = InsidherTokens.TextPrimary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
