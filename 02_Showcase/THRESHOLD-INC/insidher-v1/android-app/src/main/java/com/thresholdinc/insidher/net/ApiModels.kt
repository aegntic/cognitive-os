package com.thresholdinc.insidher.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class RegisterDeviceBody(
    val deviceId: String,
    val publicKey: String,
    val deviceName: String? = null,
)

@Serializable
data class RegisterDeviceResult(
    val deviceId: String,
    val registered: Boolean = true,
)

@Serializable
data class CreatePersonaBody(
    val name: String,
    val tone: String,
    val vocabulary: List<String> = emptyList(),
    val offerings: List<String> = emptyList(),
    val depositWording: String? = null,
    val boundaries: List<String>? = null,
    val availabilityPolicy: JsonObject,
)

@Serializable
data class RemotePersona(
    val id: String,
    val name: String,
    val tone: String,
    val vocabulary: List<String> = emptyList(),
    val offerings: List<String> = emptyList(),
    val depositWording: String? = null,
    val boundaries: List<String>? = null,
)

@Serializable
data class RemoteThread(
    val id: String,
    val state: String,
    val revision: Int = 1,
    val personaId: String,
    val clientPhone: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastMessageAt: String? = null,
)

@Serializable
data class RemoteMessage(
    val id: String,
    val threadId: String,
    val direction: String,
    val body: String,
    val timestamp: String,
    val worker: String? = null,
    val confidence: Double? = null,
)

@Serializable
data class OutboundSms(
    val id: String,
    val threadId: String,
    val messageId: String,
    val deviceId: String,
    val body: String,
    val phoneNumber: String,
    val scheduledFor: String,
    val enqueuedAt: String? = null,
    val delivered: Int = 0,
    val deliveredAt: String? = null,
    val sequence: Int = 0,
)

@Serializable
data class InboundSmsBody(
    val from: String,
    val body: String,
    val timestamp: String,
)

/** Local UI models (not necessarily wire format). */
data class CachedThread(
    val id: String,
    val state: String,
    val personaId: String,
    val clientPhone: String,
    val updatedAt: String = "",
    val lastMessagePreview: String = "",
)

data class CachedMessage(
    val id: String,
    val threadId: String,
    val direction: String,
    val body: String,
    val timestamp: String,
)
