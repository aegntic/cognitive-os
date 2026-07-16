package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thresholdinc.insidher.InsidherApp
import com.thresholdinc.insidher.net.CachedThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(onOpenThread: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as InsidherApp
    var threads by remember { mutableStateOf(app.repository.listThreads()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            status = "Loading…"
            try {
                withContext(Dispatchers.IO) {
                    val client = app.backendClient ?: return@withContext
                    val remote = client.listThreads()
                    val mapped = remote.map {
                        CachedThread(
                            id = it.id,
                            state = it.state,
                            personaId = it.personaId,
                            clientPhone = it.clientPhone,
                            updatedAt = it.updatedAt.orEmpty(),
                        )
                    }
                    app.repository.putThreads(mapped)
                }
                threads = app.repository.listThreads()
                status = if (threads.isEmpty()) "No conversations yet" else null
            } catch (e: Exception) {
                threads = app.repository.listThreads()
                status = e.message ?: "Refresh failed"
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.prefs.personaName ?: "Insidher") },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (threads.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(status ?: "No conversations")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(threads, key = { it.id }) { t ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(t.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.clientPhone, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (t.state == "HUMAN_REVIEW") "⚠ Needs approval · HUMAN_REVIEW" else t.state,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (t.state == "HUMAN_REVIEW") {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (t.lastMessagePreview.isNotBlank()) {
                                Text(t.lastMessagePreview, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
