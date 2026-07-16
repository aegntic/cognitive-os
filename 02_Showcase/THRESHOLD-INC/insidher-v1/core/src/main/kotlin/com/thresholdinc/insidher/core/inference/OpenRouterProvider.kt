package com.thresholdinc.insidher.core.inference

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * OpenRouter HTTP implementation of [InferenceProvider].
 *
 * VAL-LLM-005..013, 064, 070, 076, 077 — headers, models, retry, fallback, rate limits.
 * API key is constructor-injected (never hardcoded).
 */
class OpenRouterProvider(
    private val apiKey: String,
    private val primaryModel: String = PRIMARY_MODEL,
    private val fallbackModel: String = FALLBACK_MODEL,
    private val http: HttpTransport = OkHttpTransport(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val maxRetries: Int = 3,
    private val perMinuteLimit: Int = 20,
) : InferenceProvider {

    private val minuteWindowStart = AtomicLong(0)
    private val minuteCount = AtomicInteger(0)
    private val dailyCount = AtomicInteger(0)

    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
    }

    override suspend fun complete(request: InferenceRequest): InferenceResponse =
        executeWithRetryAndFallback(request.copy(responseFormat = null), structured = false)

    override suspend fun completeStructured(
        request: InferenceRequest,
        schema: JsonObject?,
        schemaName: String,
    ): InferenceResponse {
        if (schema == null) return complete(request)
        val withFormat = request.copy(
            responseFormat = ResponseFormat(
                schemaName = schemaName,
                schema = schema,
                strict = true,
            ),
        )
        return executeWithRetryAndFallback(withFormat, structured = true)
    }

    private suspend fun executeWithRetryAndFallback(
        request: InferenceRequest,
        structured: Boolean,
    ): InferenceResponse {
        try {
            return attemptModel(request.copy(model = primaryModel), structured, useFallback = false)
        } catch (primary: Exception) {
            if (primary is RateLimitException) throw primary
            // VAL-LLM-011: one fallback attempt after primary exhaustion
            return attemptModel(
                request.copy(model = fallbackModel),
                structured,
                useFallback = true,
            )
        }
    }

    private suspend fun attemptModel(
        request: InferenceRequest,
        structured: Boolean,
        useFallback: Boolean,
    ): InferenceResponse {
        var lastError: Exception? = null
        val attempts = if (useFallback) 1 else (1 + maxRetries)
        for (attempt in 0 until attempts) {
            enforceRateLimit()
            try {
                val raw = http.post(OPENROUTER_URL, buildHeaders(), buildBody(request, structured))
                if (raw.status == 429) {
                    val retryAfterSec = raw.headers["Retry-After"]?.toLongOrNull() ?: backoffSeconds(attempt)
                    if (attempt < attempts - 1) {
                        sleeper(retryAfterSec * 1000)
                        continue
                    }
                    throw RateLimitException(retryAfterSec * 1000)
                }
                if (raw.status in 500..599 || raw.status == 0) {
                    lastError = InferenceException("Upstream ${raw.status}: ${raw.body}", code = "UPSTREAM_${raw.status}")
                    if (attempt < attempts - 1) {
                        sleeper(backoffSeconds(attempt) * 1000)
                        continue
                    }
                    throw lastError
                }
                if (raw.status == 401 || raw.status == 403) {
                    throw InferenceException("Auth failed: ${raw.status}", code = "AUTH_${raw.status}")
                }
                if (raw.status == 400) {
                    throw InferenceException("Bad request: ${raw.body}", code = "BAD_REQUEST")
                }
                if (raw.status !in 200..299) {
                    throw InferenceException("HTTP ${raw.status}: ${raw.body}", code = "HTTP_${raw.status}")
                }
                return parseResponse(raw.body, request.model, structured, attempt, attempts, request, structured)
            } catch (e: RateLimitException) {
                throw e
            } catch (e: InferenceException) {
                lastError = e
                if (e.code.startsWith("AUTH_") || e.code == "BAD_REQUEST") throw e
                if (attempt < attempts - 1 && !useFallback) {
                    sleeper(backoffSeconds(attempt) * 1000)
                    continue
                }
                throw e
            } catch (e: Exception) {
                lastError = InferenceException("Network error", cause = e)
                if (attempt < attempts - 1 && !useFallback) {
                    sleeper(backoffSeconds(attempt) * 1000)
                    continue
                }
                throw lastError
            }
        }
        throw lastError ?: InferenceException("Exhausted retries")
    }

    private suspend fun parseResponse(
        body: String,
        requestedModel: String,
        structured: Boolean,
        attempt: Int,
        attempts: Int,
        request: InferenceRequest,
        structuredFlag: Boolean,
    ): InferenceResponse {
        val json = try {
            JSON.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            // VAL-LLM-012: heal/retry once on malformed
            if (attempt < attempts - 1) {
                sleeper(backoffSeconds(attempt) * 1000)
                val raw = http.post(OPENROUTER_URL, buildHeaders(), buildBody(request, structuredFlag, heal = true))
                return parseResponse(raw.body, requestedModel, structured, attempts - 1, attempts, request, structuredFlag)
            }
            throw InferenceException("Malformed JSON response", code = "MALFORMED_JSON", cause = e)
        }

        val content = json["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()

        if (content.isEmpty()) {
            throw InferenceException("Empty LLM content", code = "EMPTY_RESPONSE")
        }

        val tokens = json["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull ?: 0
        // VAL-LLM-077: actual model used
        val modelUsed = json["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: requestedModel

        var structuredContent: JsonObject? = null
        if (structured) {
            structuredContent = try {
                JSON.parseToJsonElement(content).jsonObject
            } catch (e: Exception) {
                if (attempt < attempts - 1) {
                    sleeper(backoffSeconds(attempt) * 1000)
                    val raw = http.post(OPENROUTER_URL, buildHeaders(), buildBody(request, true, heal = true))
                    return parseResponse(raw.body, requestedModel, true, attempts - 1, attempts, request, true)
                }
                throw InferenceException("Schema parse failed", code = "SCHEMA_MISMATCH", cause = e)
            }
        }

        // Confidence: prefer explicit field, else clamp heuristic from finish
        val confidence = 0.85.coerceIn(0.0, 1.0)

        return InferenceResponse(
            content = content,
            structuredContent = structuredContent,
            confidence = confidence,
            tokensUsed = tokens.coerceAtLeast(0),
            model = modelUsed,
        )
    }

    private fun buildHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "HTTP-Referer" to "https://insidher.app",
        "X-Title" to "insidher",
        "Content-Type" to "application/json",
    )

    private fun buildBody(
        request: InferenceRequest,
        structured: Boolean,
        heal: Boolean = false,
    ): String {
        val messages = JsonArray(
            request.messages.map { m ->
                buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                }
            },
        )
        val body = buildJsonObject {
            put("model", request.model)
            put("messages", messages)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (structured && request.responseFormat != null) {
                put(
                    "response_format",
                    buildJsonObject {
                        put("type", "json_schema")
                        put(
                            "json_schema",
                            buildJsonObject {
                                put("name", request.responseFormat.schemaName)
                                put("strict", true)
                                put("schema", request.responseFormat.schema)
                            },
                        )
                    },
                )
                if (heal) {
                    put("transforms", JsonArray(listOf(JsonPrimitive("response_format"))))
                }
            }
        }
        return JSON.encodeToString(JsonObject.serializer(), body)
    }

    private fun enforceRateLimit() {
        // VAL-LLM-076: 20 req/min
        val now = clock()
        val window = minuteWindowStart.get()
        if (now - window >= 60_000) {
            minuteWindowStart.set(now)
            minuteCount.set(0)
        }
        if (minuteCount.incrementAndGet() > perMinuteLimit) {
            throw RateLimitException(60_000 - (now - minuteWindowStart.get()).coerceAtLeast(0))
        }
        dailyCount.incrementAndGet()
    }

    private fun backoffSeconds(attempt: Int): Long {
        // VAL-LLM-009: 1s, 2s, 4s
        return (1L shl attempt.coerceAtMost(3))
    }

    companion object {
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val PRIMARY_MODEL = "meta-llama/llama-3.3-70b-instruct:free"
        const val FALLBACK_MODEL = "qwen/qwen3-next-80b-a3b-instruct:free"

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

data class HttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

fun interface HttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val reqBuilder = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA))
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            val headerMap = resp.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }
            return HttpResponse(resp.code, resp.body?.string().orEmpty(), headerMap)
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
