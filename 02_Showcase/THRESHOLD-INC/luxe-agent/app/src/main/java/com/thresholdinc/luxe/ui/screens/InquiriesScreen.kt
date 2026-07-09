package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.core.AnitaCore
import com.thresholdinc.luxe.domain.Client
import com.thresholdinc.luxe.domain.Inquiry
import com.thresholdinc.luxe.ui.components.InsidherButton
import com.thresholdinc.luxe.ui.components.InsidherCard
import com.thresholdinc.luxe.ui.theme.InsidherTokens

@Composable
fun InquiriesScreen(
    clients: List<Client>,
    inquiries: List<Inquiry>,
    log: List<String>,
    onProcess: (Inquiry, String) -> Unit,
    onAddClient: (String, String) -> Unit,
    onLog: (String) -> Unit = {}   // optional: to record the actual reply text
) {
    var newClientName by remember { mutableStateOf("") }
    var newClientEmail by remember { mutableStateOf("") }
    val replies = remember { mutableStateMapOf<String, String>() }  // inquiry.text -> generated reply
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Who Remembers", color = InsidherTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Text("Client text messages • one-tap auto reply to clients (separate from Anita chat)", color = InsidherTokens.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        // Add client
        Row {
            OutlinedTextField(
                value = newClientName,
                onValueChange = { newClientName = it },
                label = { Text("Name", color = InsidherTokens.TextSecondary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsidherTokens.TextPrimary,
                    unfocusedTextColor = InsidherTokens.TextPrimary,
                    focusedContainerColor = InsidherTokens.Etched,
                    unfocusedContainerColor = InsidherTokens.Etched
                )
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = newClientEmail,
                onValueChange = { newClientEmail = it },
                label = { Text("Email", color = InsidherTokens.TextSecondary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = InsidherTokens.TextPrimary,
                    unfocusedTextColor = InsidherTokens.TextPrimary,
                    focusedContainerColor = InsidherTokens.Etched,
                    unfocusedContainerColor = InsidherTokens.Etched
                )
            )
            Spacer(Modifier.width(8.dp))
            InsidherButton(onClick = {
                if (newClientEmail.isNotBlank()) {
                    onAddClient(newClientName.ifBlank { newClientEmail.substringBefore("@") }, newClientEmail)
                    newClientName = ""
                    newClientEmail = ""
                }
            }, isPrimary = false) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("Clients in vault", color = InsidherTokens.TextPrimary, fontSize = 14.sp)
        LazyColumn(modifier = Modifier.height(90.dp)) {
            items(clients) { client ->
                InsidherCard {
                    Column {
                        Text("${client.name} <${client.email}>", color = InsidherTokens.TextPrimary, fontSize = 14.sp)
                        Text("Sovereignty: ${client.sovereigntyLevel}", color = InsidherTokens.TextSecondary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Incoming client messages — generate reply text to send back to them", color = InsidherTokens.TextPrimary, fontSize = 14.sp)
        LazyColumn {
            items(inquiries) { inq ->
                InsidherCard {
                    Column {
                        Text(inq.text, color = InsidherTokens.TextPrimary, fontSize = 14.sp)
                        Text("${inq.from} • ${inq.date}", color = InsidherTokens.TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))

                        InsidherButton(
                            onClick = {
                                val clientContext = inq.from
                                val action = AnitaCore.decideWithContext(inq.text, clientContext, useOnDevice = false)

                                val replyText = action.reply ?: "Thank you for reaching out. This has been logged."

                                replies[inq.text] = replyText

                                // original processing for vault / memory
                                onProcess(inq, inq.from)

                                // auto-log the actual reply text
                                onLog("Auto-reply to ${inq.from}: $replyText")
                            },
                            isPrimary = true
                        ) {
                            Text("Generate reply to client")
                        }

                        replies[inq.text]?.let { reply ->
                            Spacer(Modifier.height(10.dp))
                            Text("Reply to send to client:", color = InsidherTokens.AmberGold, fontSize = 11.sp)
                            Text(reply, color = InsidherTokens.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                InsidherButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(reply))
                                }, isPrimary = false) {
                                    Text("Copy")
                                }
                                Spacer(Modifier.width(8.dp))
                                InsidherButton(onClick = {
                                    onLog("Sent to client: $reply")
                                    replies.remove(inq.text)
                                }, isPrimary = false) {
                                    Text("Mark as sent")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Audit Log (immutable in prod, SQLCipher encrypted)", color = InsidherTokens.TextPrimary, fontSize = 12.sp)
        LazyColumn(modifier = Modifier.height(100.dp)) {
            items(log.takeLast(5)) { entry ->
                Text(entry, color = InsidherTokens.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
