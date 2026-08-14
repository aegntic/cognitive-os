package com.thresholdinc.insidher.sms

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Outbound SMS via [SmsManager], with optional delay hooks for human-like pacing.
 */
class SmsSender(
    private val context: Context,
    private val delayHook: suspend (delayMs: Long) -> Unit = { ms -> if (ms > 0) delay(ms) },
) {
    suspend fun send(
        destination: String,
        text: String,
        delayMs: Long = 0L,
    ): Result<String> {
        return try {
            delayHook(delayMs)
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            val parts = smsManager.divideMessage(text)
            if (parts.size == 1) {
                smsManager.sendTextMessage(destination, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            }
            Log.d(TAG, "sent to $destination (${parts.size} parts, delay=${delayMs}ms)")
            Result.success("sent_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "send failed to $destination", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "InsidherSmsSender"
    }
}
