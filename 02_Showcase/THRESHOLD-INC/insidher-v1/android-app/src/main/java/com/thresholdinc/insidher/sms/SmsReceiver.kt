package com.thresholdinc.insidher.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.thresholdinc.insidher.InsidherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Incoming SMS: PDU → [SmsParser.batchByOrigin] → backend webhook.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdus = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val parts = pdus.mapNotNull { sms ->
                    val from = sms.originatingAddress ?: return@mapNotNull null
                    val body = sms.messageBody ?: return@mapNotNull null
                    ParsedSms(from, body, sms.timestampMillis)
                }
                val batched = SmsParser.batchByOrigin(parts)
                val app = context.applicationContext as? InsidherApp
                val client = app?.backendClient
                if (client == null) {
                    Log.w(TAG, "BackendClient not ready; dropped ${batched.size} SMS")
                    return@launch
                }
                for (sms in batched) {
                    val body = SmsParser.truncateBody(sms.body)
                    val ts = Instant.ofEpochMilli(sms.timestampMillis).toString()
                    try {
                        client.submitInboundSms(SmsParser.normalizePhone(sms.from), body, ts)
                        Log.d(TAG, "forwarded SMS from ${sms.from}")
                    } catch (e: Exception) {
                        Log.e(TAG, "forward failed for ${sms.from}", e)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "InsidherSmsReceiver"
    }
}
