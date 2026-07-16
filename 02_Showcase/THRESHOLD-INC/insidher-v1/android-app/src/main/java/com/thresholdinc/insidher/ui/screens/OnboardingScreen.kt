package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** m1-android-persona: richer persona capture (still ~60s setup). */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as InsidherApp
    var name by remember { mutableStateOf(app.prefs.personaName.orEmpty().ifBlank { "Insidher" }) }
    var tone by remember { mutableStateOf("warm, discreet, confident") }
    var vocabulary by remember { mutableStateOf("babe, hun, x") }
    var offerings by remember { mutableStateOf("companionship, dinner dates") }
    var depositWording by remember { mutableStateOf("Just a small hold to lock the time") }
    var boundaries by remember { mutableStateOf("no freebies, deposit first") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Persona setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tone, offerings, deposit wording, and boundaries for this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tone,
            onValueChange = { tone = it },
            label = { Text("Tone") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vocabulary,
            onValueChange = { vocabulary = it },
            label = { Text("Vocabulary (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = offerings,
            onValueChange = { offerings = it },
            label = { Text("Offerings") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = depositWording,
            onValueChange = { depositWording = it },
            label = { Text("Deposit wording") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = boundaries,
            onValueChange = { boundaries = it },
            label = { Text("Boundaries") },
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
                                try {
                                    client.registerDevice(
                                        app.keyStore.publicKeySpkiBase64(),
                                        android.os.Build.MODEL,
                                    )
                                } catch (_: Exception) { /* offline ok */ }
                                val vocab = vocabulary.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val offers = offerings.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val bounds = boundaries.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val persona = try {
                                    client.createPersona(
                                        name = name.trim(),
                                        tone = tone.trim(),
                                        vocabulary = vocab,
                                        offerings = offers,
                                        depositWording = depositWording.trim().ifBlank { null },
                                        boundaries = bounds.ifEmpty { null },
                                    )
                                } catch (e: Exception) {
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
