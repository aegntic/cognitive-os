package com.thresholdinc.luxe.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.thresholdinc.luxe.domain.Client
import com.thresholdinc.luxe.domain.DepositInfo
import com.thresholdinc.luxe.domain.SmsConversation
import com.thresholdinc.luxe.domain.SmsMessage
import com.thresholdinc.luxe.domain.SmsSendResult
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Repository for SMS conversations.
 * Uses the EncryptedVault for per-client isolation and Android's SMS ContentProvider for native send/receive.
 * CRITICAL: Every operation is scoped to a single clientId - zero cross-client leakage.
 */
class SmsRepository(private val context: Context, private val vault: EncryptedVault) {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val SMS_URI = Telephony.Sms.CONTENT_URI
    private val MMS_URI = Telephony.Mms.CONTENT_URI

    // Column projections
    private val SMS_PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.STATUS
    )

    /**
     * Get or create a conversation for a specific client.
     * Creates the thread if it doesn't exist in vault.
     */
    suspend fun getOrCreateConversation(client: Client): SmsConversation {
        val phone = normalizePhone(client.email) // For MVP, email doubles as phone lookup
        // Try load from vault first
        val saved = vault.getSmsConversation(client.id)
        if (saved != null) return saved

        // Otherwise create new from native SMS history for this phone
        val nativeMessages = loadNativeMessagesForPhone(phone)
        val conversation = SmsConversation(
            clientId = client.id,
            clientName = client.name,
            clientPhone = phone,
            messages = nativeMessages,
            unreadCount = nativeMessages.count { it.direction == SmsMessage.Direction.INBOUND && it.status == SmsMessage.SmsStatus.RECEIVED }
        )
        vault.saveSmsConversation(conversation)
        return conversation
    }

    /**
     * Send an SMS to a specific client via native SMS manager.
     * Returns result with message ID for tracking.
     */
    suspend fun sendSms(client: Client, body: String, depositInfo: DepositInfo? = null): SmsSendResult {
        val phone = normalizePhone(client.email)
        val messageId = UUID.randomUUID().toString()

        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            val sentIntent = android.app.PendingIntent.getBroadcast(
                context, 0,
                android.content.Intent("SMS_SENT").putExtra("message_id", messageId),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val deliveredIntent = android.app.PendingIntent.getBroadcast(
                context, 0,
                android.content.Intent("SMS_DELIVERED").putExtra("message_id", messageId),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // Split long messages
            val parts = smsManager.divideMessage(body)
            val sentIntents = arrayListOf<android.app.PendingIntent>()
            val deliveryIntents = arrayListOf<android.app.PendingIntent>()
            for (i in parts.indices) {
                val sentIntent = android.app.PendingIntent.getBroadcast(
                    context, i,
                    android.content.Intent("SMS_SENT").putExtra("message_id", messageId).putExtra("part", i),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                val deliveredIntent = android.app.PendingIntent.getBroadcast(
                    context, i,
                    android.content.Intent("SMS_DELIVERED").putExtra("message_id", messageId).putExtra("part", i),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                sentIntents.add(sentIntent)
                deliveryIntents.add(deliveredIntent)
            }
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveryIntents)

            // Persist outbound message to vault immediately
            val outboundMsg = SmsMessage(
                id = messageId,
                clientId = client.id,
                direction = SmsMessage.Direction.OUTBOUND,
                body = body,
                timestamp = System.currentTimeMillis(),
                status = SmsMessage.SmsStatus.SENT,
                depositInfo = depositInfo
            )
            val conversation = vault.getSmsConversation(client.id)
                ?: SmsConversation(client.id, client.name, phone)
            vault.saveSmsConversation(conversation.withMessage(outboundMsg))

            return SmsSendResult(true, messageId)
        } catch (e: Exception) {
            return SmsSendResult(false, messageId, e.message)
        }
    }

    /**
     * Load all native SMS messages for a specific phone number from the device SMS database.
     */
    private fun loadNativeMessagesForPhone(phone: String): List<SmsMessage> {
        val normalized = normalizePhone(phone)
        val selection = "${Telephony.Sms.ADDRESS} = ?"
        val args = arrayOf(normalized)
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        val cursor = context.contentResolver.query(SMS_URI, SMS_PROJECTION, selection, args, sortOrder)
        val messages = mutableListOf<SmsMessage>()

        cursor?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val status = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.STATUS))

                val direction = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsMessage.Direction.INBOUND
                    Telephony.Sms.MESSAGE_TYPE_SENT,
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> SmsMessage.Direction.OUTBOUND
                    else -> SmsMessage.Direction.INBOUND
                }

                val smsStatus = when (status) {
                    Telephony.Sms.STATUS_COMPLETE -> SmsMessage.SmsStatus.SENT
                    Telephony.Sms.STATUS_FAILED -> SmsMessage.SmsStatus.FAILED
                    Telephony.Sms.STATUS_PENDING -> SmsMessage.SmsStatus.PENDING
                    Telephony.Sms.STATUS_NONE -> SmsMessage.SmsStatus.RECEIVED
                    else -> SmsMessage.SmsStatus.SENT
                }

                messages.add(SmsMessage(
                    id = UUID.randomUUID().toString(),
                    clientId = "", // Will be set by caller based on client
                    direction = direction,
                    body = body ?: "",
                    timestamp = date,
                    status = smsStatus
                ))
            }
        }

        return messages
    }

    /**
     * Mark conversation as read for a client.
     */
    suspend fun markConversationRead(clientId: String) {
        val conversation = vault.getSmsConversation(clientId)
        conversation?.let { vault.saveSmsConversation(it.markRead()) }
    }

    /**
     * Get unread count across all clients (for badge).
     */
    suspend fun getTotalUnreadCount(): Int {
        return vault.getAllSmsConversations().sumOf { it.unreadCount }
    }

    /**
     * Normalize phone to E.164 format for matching.
     * MVP: assumes US/Canada +1 if 10 digits.
     */
    private fun normalizePhone(input: String): String {
        val digits = input.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("1") && digits.length == 11 -> "+$digits"
            digits.length == 10 -> "+1$digits"
            digits.startsWith("+") -> digits
            else -> "+$digits"
        }
    }

    /**
     * Close resources.
     */
    fun close() {
        ioExecutor.shutdown()
    }
}