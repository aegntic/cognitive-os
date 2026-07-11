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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.core.AnitaCore
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.domain.Client
import com.thresholdinc.luxe.domain.Inquiry
import com.thresholdinc.luxe.ui.components.GlassCard
import com.thresholdinc.luxe.ui.components.InsidherButton
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    clients: List<Client>,
    inquiries: List<Inquiry>,
    log: List<String>,
    vault: EncryptedVault,
    scope: CoroutineScope,
    useOnDevice: Boolean,
    onAddClient: (String, String) -> Unit,
    onLog: (String) -> Unit
) {
    var newClientName by remember { mutableStateOf("") }
    var newClientPhone by remember { mutableStateOf("") }
    var newClientEmail by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFF0C0C0E))
            .verticalScroll(rememberScrollState())
    ) {
        Text("CLIENTS", color = Color(0xFFE8D9C0), fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your client vault — private & sovereign", color = Color(0xFFF5F0E6).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.height(24.dp))

        // Add client
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add Client", color = Color(0xFFE8D9C0), fontSize = 16.sp, fontWeight = FontWeight.Light)
                OutlinedTextField(
                    value = newClientName,
                    onValueChange = { newClientName = it },
                    label = { Text("Name", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFE8D9C0), unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newClientPhone,
                    onValueChange = { newClientPhone = it },
                    label = { Text("Phone *", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFE8D9C0), unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newClientEmail,
                    onValueChange = { newClientEmail = it },
                    label = { Text("Email (optional)", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFE8D9C0), unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                InsidherButton(onClick = {
                    if (newClientName.isNotBlank() && newClientPhone.isNotBlank()) {
                        onAddClient(newClientName, newClientPhone)
                        newClientName = ""
                        newClientPhone = ""
                        newClientEmail = ""
                    }
                }, isPrimary = true) { Text("Add Client") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Client list
        Text("YOUR CLIENTS", color = Color(0xFFE8D9C0), fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn {
            items(clients) { client ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(client.name, color = Color(0xFFF5F0E6), fontSize = 16.sp, fontWeight = FontWeight.Normal)
                                Text(client.email, color = Color(0xFFF5F0E6).copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            // TODO: Add SMS button that opens conversation
                        }
                    }
                }
            }
        }
    }
}