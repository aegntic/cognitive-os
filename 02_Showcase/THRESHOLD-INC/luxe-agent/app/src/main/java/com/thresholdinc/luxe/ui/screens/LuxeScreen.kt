package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.core.AnitaCore
import com.thresholdinc.luxe.domain.AnitaAction
import com.thresholdinc.luxe.ui.components.InsidherButton
import com.thresholdinc.luxe.ui.components.InsidherCard
import com.thresholdinc.luxe.ui.theme.InsidherTokens

// Separate from client messages. This is direct conversation with Anita.
data class ChatMessage(
    val role: String, // "user" or "anita"
    val text: String
)

@Composable
fun AnitaScreen(
    log: List<String>,
    onAskAnita: (String, String) -> AnitaAction,
    clientContextProvider: (String) -> String = { "" }
) {
    var input by remember { mutableStateOf("") }
    var selectedClient by remember { mutableStateOf("client@firm.com") }
    var chat by remember { mutableStateOf(listOf<ChatMessage>()) }
    var useOnDevice by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) {
            listState.animateScrollToItem(chat.lastIndex)
        }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Anita Simpson", color = InsidherTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Text("Direct chat with your sovereign agent • separate from client messages", color = InsidherTokens.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedTextField(
                value = selectedClient,
                onValueChange = { selectedClient = it },
                label = { Text("Context client", color = InsidherTokens.TextSecondary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsidherTokens.TextPrimary,
                    unfocusedTextColor = InsidherTokens.TextPrimary,
                    focusedContainerColor = InsidherTokens.Etched,
                    unfocusedContainerColor = InsidherTokens.Etched
                )
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("On-device", color = InsidherTokens.TextSecondary, fontSize = 12.sp)
                Switch(checked = useOnDevice, onCheckedChange = { useOnDevice = it })
            }
        }
        Spacer(Modifier.height(12.dp))

        // Chat area - clearly separate from client texts
        InsidherCard {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(8.dp)
            ) {
                items(chat) { msg ->
                    if (msg.role == "user") {
                        // User bubble (right aligned)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .background(InsidherTokens.AmberGold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(msg.text, color = InsidherTokens.TextPrimary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        // Anita response (left)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Anita", color = InsidherTokens.AmberGold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .background(InsidherTokens.Etched, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(msg.text, color = InsidherTokens.TextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Input - talking to Anita
        Row {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Message Anita...", color = InsidherTokens.TextSecondary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsidherTokens.TextPrimary,
                    unfocusedTextColor = InsidherTokens.TextPrimary,
                    focusedContainerColor = InsidherTokens.Etched,
                    unfocusedContainerColor = InsidherTokens.Etched
                )
            )
            Spacer(Modifier.width(8.dp))
            InsidherButton(
                onClick = {
                    if (input.isNotBlank()) {
                        val userText = input.trim()
                        // Add user message
                        chat = chat + ChatMessage("user", userText)

                        val action = onAskAnita(userText, selectedClient)
                        val anitaText = action.reply ?: action.reason

                        // Add Anita's response as natural chat
                        chat = chat + ChatMessage("anita", anitaText)

                        input = ""
                    }
                },
                isPrimary = true
            ) {
                Text("Send")
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("This chat is private to you and Anita. Client messages live in Who Remembers.", 
             color = InsidherTokens.TextSecondary, fontSize = 10.sp)
    }
}
