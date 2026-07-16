package com.thresholdinc.insidher.net

import com.thresholdinc.insidher.auth.DeviceKeyStore
import com.thresholdinc.insidher.auth.SignedRequest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp client for Workers backend. Signs protected routes with [DeviceKeyStore].
 */
class BackendClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val deviceId: String,
    private val keyStore: DeviceKeyStore? = null,
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    /** Injected signer for JVM tests (path, method, body) → headers. */
    private val signer: ((method: String, path: String, query: String, body: String) -> Map<String, String>)? = null,
) {

    fun health(): Boolean {
        val req = Request.Builder().url("$baseUrl/health").get().build()
        return http.newCall(req).execute().use { it.isSuccessful }
    }

    fun registerDevice(publicKey: String, deviceName: String? = null): RegisterDeviceResult {
        val body = json.encodeToString(
            RegisterDeviceBody.serializer(),
            RegisterDeviceBody(deviceId, publicKey, deviceName),
        )
        // public register — no auth
        val raw = request("POST", "/api/auth/register", body, signed = false)
        val env = json.decodeFromString(ApiEnvelope.serializer(RegisterDeviceResult.serializer()), raw)
        return env.data ?: error(env.error?.message ?: "register failed")
    }

    fun listThreads(page: Int = 1, pageSize: Int = 50): List<RemoteThread> {
        val path = "/api/threads"
        val query = "?page=$page&pageSize=$pageSize"
        val raw = request("GET", path, "", signed = true, query = query)
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(RemoteThread.serializer())), raw)
        return env.data.orEmpty()
    }

    fun getThread(threadId: String): RemoteThread {
        val raw = request("GET", "/api/threads/$threadId", "", signed = true)
        val env = json.decodeFromString(ApiEnvelope.serializer(RemoteThread.serializer()), raw)
        return env.data ?: error(env.error?.message ?: "thread not found")
    }

    fun listMessages(threadId: String, page: Int = 1, pageSize: Int = 100): List<RemoteMessage> {
        val path = "/api/threads/$threadId/messages"
        val query = "?page=$page&pageSize=$pageSize"
        val raw = request("GET", path, "", signed = true, query = query)
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(RemoteMessage.serializer())), raw)
        return env.data.orEmpty()
    }

    fun listPersonas(): List<RemotePersona> {
        val raw = request("GET", "/api/personas", "", signed = true)
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(RemotePersona.serializer())), raw)
        return env.data.orEmpty()
    }

    fun submitInboundSms(from: String, body: String, timestampIso: String): String {
        val payload = json.encodeToString(
            InboundSmsBody.serializer(),
            InboundSmsBody(from = from, body = body, timestamp = timestampIso),
        )
        // webhook is public
        return request("POST", "/webhook/sms", payload, signed = false)
    }

    fun pollOutbound(): List<OutboundSms> {
        val raw = request("GET", "/api/devices/$deviceId/outbound", "", signed = true)
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(OutboundSms.serializer())), raw)
        return env.data.orEmpty()
    }

    fun markDelivered(smsId: String) {
        request(
            "POST",
            "/api/devices/$deviceId/outbound/$smsId/delivered",
            "{}",
            signed = true,
        )
    }

    fun listDeposits(threadId: String): List<RemoteDeposit> {
        val raw = request("GET", "/api/threads/$threadId/deposits", "", signed = true)
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(RemoteDeposit.serializer())), raw)
        return env.data.orEmpty()
    }

    fun submitHumanDecision(
        threadId: String,
        decision: String,
        note: String? = null,
        expectedRevision: Int? = null,
    ) {
        val body = buildJsonObject {
            put("decision", JsonPrimitive(decision))
            if (note != null) put("note", JsonPrimitive(note))
            if (expectedRevision != null) put("expectedRevision", JsonPrimitive(expectedRevision))
        }.toString()
        request("POST", "/api/threads/$threadId/decision", body, signed = true)
    }

    fun createPersona(
        name: String,
        tone: String,
        vocabulary: List<String> = emptyList(),
        offerings: List<String> = emptyList(),
        depositWording: String? = null,
        boundaries: List<String>? = null,
    ): RemotePersona {
        val policy = buildJsonObject {
            put("timezone", JsonPrimitive("Australia/Sydney"))
            put("weeklyWindows", JsonObject(emptyMap()))
            put("dndPeriods", kotlinx.serialization.json.JsonArray(emptyList()))
            put("dateOverrides", JsonObject(emptyMap()))
        }
        val body = json.encodeToString(
            CreatePersonaBody.serializer(),
            CreatePersonaBody(
                name = name,
                tone = tone,
                vocabulary = vocabulary,
                offerings = offerings,
                depositWording = depositWording,
                boundaries = boundaries,
                availabilityPolicy = policy,
            ),
        )
        val raw = request("POST", "/api/personas", body, signed = true)
        val env = json.decodeFromString(ApiEnvelope.serializer(RemotePersona.serializer()), raw)
        return env.data ?: error(env.error?.message ?: "create persona failed")
    }

    private fun request(
        method: String,
        path: String,
        body: String,
        signed: Boolean,
        query: String = "",
    ): String {
        val url = baseUrl.trimEnd('/') + path + query
        val builder = Request.Builder().url(url)
        if (signed) {
            authHeaders(method, path, query, body).forEach { (k, v) -> builder.header(k, v) }
        }
        val media = "application/json; charset=utf-8".toMediaType()
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body.toRequestBody(media))
            "PATCH" -> builder.patch(body.toRequestBody(media))
            "PUT" -> builder.put(body.toRequestBody(media))
            "DELETE" -> builder.delete(body.toRequestBody(media))
            else -> error("unsupported method $method")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: $text")
            }
            return text
        }
    }

    private fun authHeaders(
        method: String,
        path: String,
        query: String,
        body: String,
    ): Map<String, String> {
        signer?.let { return it(method, path, query, body) }
        val ks = keyStore ?: error("DeviceKeyStore or signer required for signed requests")
        return ks.buildAuthHeaders(method, path, query, body, deviceId).toMap()
    }

    companion object {
        /** Emulator loopback to host machine. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8788"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** Test helper: body hash used by signing. */
        fun bodyHashForTest(body: String): String = SignedRequest.sha256Hex(body)
    }
}
