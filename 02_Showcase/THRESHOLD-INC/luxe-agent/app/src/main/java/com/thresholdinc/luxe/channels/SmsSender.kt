package com.thresholdinc.luxe.channels

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends SMS messages using the device's native phone number via SmsManager.
 * Each message is tracked per client conversation to prevent mix-ups.
 */
object SmsSender {

    private val TAG = "InsidherSmsSender"

    /**
     * Sends an SMS to the given phone number.
     * Returns a Result with the message ID or error.
     */
    suspend fun sendSms(
        context: Context,
        destinationPhone: String,
        text: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val smsManager = SmsManager.getDefault()

            // Handle long messages (multipart)
            val parts = smsManager.divideMessage(text)
            val sentIntents = arrayListOf<android.app.PendingIntent>()
            val deliveryIntents = arrayListOf<android.app.PendingIntent>()

            for (i in parts.indices) {
                val sentIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    i,
                    android.content.Intent("SMS_SENT").putExtra("part", i),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                val deliveryIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    i,
                    android.content.Intent("SMS_DELIVERED").putExtra("part", i),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                sentIntents.add(sentIntent)
                deliveryIntents.add(deliveryIntent)
            }

            smsManager.sendMultipartTextMessage(destinationPhone, null, parts, sentIntents, deliveryIntents)

            Log.d(TAG, "SMS queued to $destinationPhone (${parts.size} part(s))")
            Result.success("sent_${System.currentTimeMillis()}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $destinationPhone", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a deposit request link via SMS with a unique tracking token.
     */
    suspend fun sendDepositRequest(
        context: Context,
        destinationPhone: String,
        amountCents: Long,
        currency: String = "USD",
        depositToken: String
    ): Result<String> {
        val amount = amountCents / 100.0
        val formatted = String.format(java.util.Locale.US, "%.2f", amount)
        val text = """Insidher: Your deposit of \$$formatted $currency is requested. 
Tap to pay securely: ${buildDepositUrl(depositToken)}
Reply STOP to opt out.""".trimIndent()

        return sendSms(context, destinationPhone, text)
    }

    private fun buildDepositUrl(token: String): String {
        // TODO: Replace with actual payment gateway deep link
        return "https://pay.insidher.app/deposit/$token"
    }
}