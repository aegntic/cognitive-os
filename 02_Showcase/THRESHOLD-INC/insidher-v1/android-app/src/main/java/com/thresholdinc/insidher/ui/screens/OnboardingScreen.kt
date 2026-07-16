package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as InsidherApp
    var name by remember { mutableStateOf(app.prefs.personaName.orEmpty().ifBlank { "Insidher" }) }
    var tone by remember { mutableStateOf("warm, discreet, confident") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Persona setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Creates your agent persona and registers this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tone,
            onValueChange = { tone = it },
            label = { Text("Tone") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                app.ensureDeviceRegistered()
                                val client = app.backendClient
                                    ?: error("Backend not ready")
                                // Re-register if online
                                try {
                                    client.registerDevice(
                                        app.keyStore.publicKeySpkiBase64(),
                                        android.os.Build.MODEL,
                                    )
                                } catch (_: Exception) { /* offline ok for local-only */ }
                                val persona = try {
                                    client.createPersona(name.trim(), tone.trim())
                                } catch (e: Exception) {
                                    // offline fallback: local-only onboarding
                                    app.prefs.personaId = "local-${System.currentTimeMillis()}"
                                    app.prefs.personaName = name.trim()
                                    app.prefs.onboarded = true
                                    throw e
                                }
                                app.prefs.personaId = persona.id
                                app.prefs.personaName = persona.name
                                app.prefs.onboarded = true
                            }
                            onDone()
                        } catch (e: Exception) {
                            // still allow proceed if prefs set offline
                            if (app.prefs.onboarded) {
                                onDone()
                            } else {
                                error = e.message ?: "Setup failed"
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && tone.isNotBlank(),
            ) {
                Text("Continue")
            }
        }
    }
}
