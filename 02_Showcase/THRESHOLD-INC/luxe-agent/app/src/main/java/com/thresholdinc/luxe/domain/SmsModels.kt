package com.thresholdinc.luxe.domain

import java.util.UUID

/**
 * A single SMS message in a per-client conversation thread.
 * Every message is bound to exactly one client (by clientId).
 */
data class SmsMessage(
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,           // FK to Client - NEVER null, enforces isolation
    val direction: Direction,       // INBOUND from client / OUTBOUND to client
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: SmsStatus = SmsStatus.SENT,
    val depositInfo: DepositInfo? = null
) {
    enum class Direction { INBOUND, OUTBOUND }
    enum class SmsStatus { PENDING, SENT, DELIVERED, FAILED, RECEIVED }
}

/**
 * Deposit details attached to a message when a payment link or confirmation is sent/received.
 */
data class DepositInfo(
    val amountCents: Long,
    val currency: String = "USD",
    val paymentLink: String? = null,      // e.g., Stripe payment link
    val paymentMethod: String? = null,    // "card", "bank", "cash", "zelle", etc.
    val confirmed: Boolean = false,
    val confirmedAt: Long? = null,
    val reference: String? = null         // provider transaction ID
)

/**
 * Conversation thread for ONE client. Immutable list of messages.
 * Guarantees zero cross-client leakage.
 */
data class SmsConversation(
    val clientId: String,
    val clientName: String,
    val clientPhone: String,              // E.164 format
    val messages: List<SmsMessage> = emptyList(),
    val unreadCount: Int = 0,
    val lastActivity: Long = System.currentTimeMillis()
) {
    fun withMessage(message: SmsMessage): SmsConversation {
        val newMessages = messages + message
        return copy(
            messages = newMessages,
            unreadCount = if (message.direction == SmsMessage.Direction.INBOUND) unreadCount + 1 else unreadCount,
            lastActivity = message.timestamp
        )
    }

    fun markRead(): SmsConversation = copy(unreadCount = 0)

    fun latestMessage(): SmsMessage? = messages.lastOrNull()
}

/**
 * Result of sending an SMS via the native SMS manager.
 */
data class SmsSendResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

/**
 * Configuration for the native SMS gateway.
 */
data class SmsGatewayConfig(
    val enabled: Boolean = true,
    val defaultCountryCode: String = "1",      // US/Canada default
    val maxMessageLength: Int = 1600,          // concat SMS
    val requireDepositConfirmation: Boolean = true,
    val depositPaymentLinkBase: String = "https://pay.insidher.app/deposit/", // replace with real
    val autoReplyOnDepositReceived: Boolean = true
)