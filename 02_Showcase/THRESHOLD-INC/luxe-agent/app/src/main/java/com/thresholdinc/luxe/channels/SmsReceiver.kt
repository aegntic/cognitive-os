package com.thresholdinc.luxe.channels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.thresholdinc.luxe.core.AnitaCore
import com.thresholdinc.luxe.domain.AnitaAction
import com.thresholdinc.luxe.domain.Inquiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Receives inbound SMS messages and routes them into the Insidher vault as inquiries.
 * Runs on the main thread briefly; hands off to IO for vault operations.
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "InsidherSmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val pdus = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in pdus) {
            val originatingAddress = sms.originatingAddress ?: continue
            val messageBody = sms.messageBody ?: continue
            val timestampMillis = sms.timestampMillis

            Log.d(TAG, "Inbound SMS from $originatingAddress: $messageBody")

            // Route to vault + AnitaCore on background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Create inquiry object (vault integration will be added when EncryptedVault methods are ready)
                    val inquiry = Inquiry(
                        id = "sms_${timestampMillis}_${originatingAddress.hashCode()}",
                        text = messageBody,
                        from = originatingAddress,
                        date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(timestampMillis)),
                        channel = "sms"
                    )
                    
                    // Optional: trigger AnitaCore auto-draft if enabled for this client
                    // (requires vault integration for client lookup)
                    val action = AnitaCore.decideWithContext(messageBody, originatingAddress, useOnDevice = true)
                    if (action.reply != null) {
                        // Store draft reply for owner review (requires vault integration)
                        Log.d(TAG, "Auto-draft generated for $originatingAddress: ${action.reply}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process inbound SMS", e)
                }
            }
        }
    }
}