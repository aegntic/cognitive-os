package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thresholdinc.insidher.InsidherApp
import com.thresholdinc.insidher.net.CachedMessage
import com.thresholdinc.insidher.net.CachedThread
import com.thresholdinc.insidher.net.RemoteDeposit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    threadId: String,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as InsidherApp
    var thread by remember { mutableStateOf(app.repository.getThread(threadId)) }
    var messages by remember { mutableStateOf(app.repository.getMessages(threadId)) }
    var deposits by remember { mutableStateOf<List<RemoteDeposit>>(emptyList()) }
    var revision by remember { mutableStateOf(1) }
    var escalateNote by remember { mutableStateOf("") }
    var showEscalate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = app.backendClient ?: return@withContext
                    val t = client.getThread(threadId)
                    revision = t.revision
                    val mapped = CachedThread(
                        id = t.id,
                        state = t.state,
                        personaId = t.personaId,
                        clientPhone = t.clientPhone,
                        updatedAt = t.updatedAt.orEmpty(),
                    )
                    app.repository.putThread(mapped)
                    val msgs = client.listMessages(threadId).map {
                        CachedMessage(
                            id = it.id,
                            threadId = it.threadId,
                            direction = it.direction,
                            body = it.body,
                            timestamp = it.timestamp,
                        )
                    }
                    app.repository.putMessages(threadId, msgs)
                    deposits = try {
                        client.listDeposits(threadId)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                thread = app.repository.getThread(threadId)
                messages = app.repository.getMessages(threadId)
                status = null
            } catch (e: Exception) {
                status = e.message
                thread = app.repository.getThread(threadId)
                messages = app.repository.getMessages(threadId)
            }
        }
    }

    fun decide(decision: String, note: String? = null) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.backendClient?.submitHumanDecision(
                        threadId = threadId,
                        decision = decision,
                        note = note,
                        expectedRevision = revision,
                    ) ?: error("Backend offline")
                }
                showEscalate = false
                refresh()
            } catch (e: Exception) {
                status = e.message
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(threadId) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(thread?.clientPhone ?: threadId)
                        Text(
                            thread?.state ?: "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (status != null) {
                Text(
                    status!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (deposits.isNotEmpty()) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Deposit", style = MaterialTheme.typography.titleSmall)
                        deposits.forEach { d ->
                            Text(
                                "${d.currency} ${d.amount} · ${d.status}" +
                                    (d.evidenceType?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                m.direction.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(m.body, style = MaterialTheme.typography.bodyMedium)
                            Text(m.timestamp, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (thread?.state == "HUMAN_REVIEW") {
                // m2-approval-ui: one-tap gate
                if (showEscalate) {
                    OutlinedTextField(
                        value = escalateNote,
                        onValueChange = { escalateNote = it },
                        label = { Text("Escalation note") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showEscalate = false },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) { Text("Cancel") }
                        Button(
                            onClick = { decide("ESCALATE", escalateNote.ifBlank { null }) },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) { Text("Confirm escalate") }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { decide("APPROVE") },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) { Text("Approve") }
                        OutlinedButton(
                            onClick = { decide("REJECT") },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) { Text("Reject") }
                        OutlinedButton(
                            onClick = { showEscalate = true },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) { Text("Escalate") }
                    }
                }
            }
        }
    }
}
