package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = app.backendClient ?: return@withContext
                    val t = client.getThread(threadId)
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.backendClient?.submitHumanDecision(threadId, "APPROVE")
                                }
                                refresh()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Approve") }
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.backendClient?.submitHumanDecision(threadId, "REJECT")
                                }
                                refresh()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Reject") }
                }
            }
        }
    }
}
