package com.thresholdinc.insidher.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thresholdinc.insidher.InsidherApp
import com.thresholdinc.insidher.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ponytail: name + local avatar file; no cloud upload
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as InsidherApp
    var name by remember { mutableStateOf(app.prefs.personaName.orEmpty().ifBlank { "Insidher" }) }
    var avatarPath by remember { mutableStateOf(app.prefs.profilePicturePath) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val dest = withContext(Dispatchers.IO) {
                val out = File(context.filesDir, AppPrefs.AVATAR_FILE)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                out.absolutePath
            }
            if (dest != null) {
                avatarPath = dest
                app.prefs.profilePicturePath = dest
            } else {
                error = "Couldn’t save photo"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your profile", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Name and photo for your workspace. Used only on this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        ProfileAvatar(
            path = avatarPath,
            modifier = Modifier
                .size(96.dp)
                .clickable { pickPhoto.launch("image/*") },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (avatarPath == null) "Tap to add photo" else "Tap to change photo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { pickPhoto.launch("image/*") },
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
                    val displayName = name.trim()
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
                                val persona = try {
                                    client.createPersona(
                                        name = displayName,
                                        tone = DEFAULT_TONE,
                                        vocabulary = DEFAULT_VOCAB,
                                        offerings = DEFAULT_OFFERINGS,
                                        depositWording = DEFAULT_DEPOSIT,
                                        boundaries = DEFAULT_BOUNDARIES,
                                    )
                                } catch (e: Exception) {
                                    app.prefs.personaId = "local-${System.currentTimeMillis()}"
                                    app.prefs.personaName = displayName
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
                enabled = name.isNotBlank(),
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
fun ProfileAvatar(path: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        path?.let { p ->
            val f = File(p)
            if (f.isFile) BitmapFactory.decodeFile(p) else null
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = "Add photo",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ponytail: professional defaults for Play Store positioning
private const val DEFAULT_TONE = "clear, professional, friendly"
private val DEFAULT_VOCAB = listOf("thanks", "sounds good", "confirmed")
private val DEFAULT_OFFERINGS = listOf("consultations", "appointments", "follow-ups")
private const val DEFAULT_DEPOSIT = "To hold the slot, please send the booking deposit"
private val DEFAULT_BOUNDARIES = listOf("business hours only", "deposit required to confirm")
