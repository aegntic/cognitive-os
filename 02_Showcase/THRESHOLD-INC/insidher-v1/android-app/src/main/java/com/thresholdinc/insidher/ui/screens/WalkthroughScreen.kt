package com.thresholdinc.insidher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// ponytail: step index, not pager lib — 4 pages then name screen
private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page(
        "Insidher texts for you",
        "Clients SMS your phone. The agent replies like you — warm, human, never corporate.",
    ),
    Page(
        "You stay in control",
        "Deposit comes in → you get a one-tap approve / reject. No final yes without you.",
    ),
    Page(
        "Works on this phone",
        "SMS lands here, we talk to the backend, and replies go out when you allow it.",
    ),
)

@Composable
fun WalkthroughScreen(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val lastPage = pages.size // demo page index
    val isDemo = step == lastPage
    val isLast = step >= lastPage

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinished) { Text("Skip") }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isDemo) {
                DemoConversation()
            } else {
                val page = pages[step]
                Text(page.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${step + 1}/${pages.size + 1}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (isLast) {
                Button(onClick = onFinished) { Text("Set your name") }
            } else {
                Button(onClick = { step++ }) { Text("Next") }
            }
        }
    }
}

@Composable
private fun DemoConversation() {
    Column(Modifier.fillMaxWidth()) {
        Text("Live demo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "How a booking chat looks — no real SMS yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Bubble(inbound = true, "hey free thursday night?")
        Spacer(Modifier.height(8.dp))
        Bubble(inbound = false, "hey hun x thursday works — just a small hold to lock the time")
        Spacer(Modifier.height(8.dp))
        Bubble(inbound = true, "sent the deposit")
        Spacer(Modifier.height(8.dp))
        Bubble(inbound = false, "got it — waiting on my ok, then you’re confirmed")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("You: Approve  ·  Reject  ·  Escalate")
        }
    }
}

@Composable
private fun Bubble(inbound: Boolean, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (inbound) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (inbound) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                )
                .padding(12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
