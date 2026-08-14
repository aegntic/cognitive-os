package com.thresholdinc.insidher.auth

import java.security.MessageDigest
import java.util.UUID

/**
 * Pure helpers for ECDSA request signing (no Android Keystore).
 * Message format matches workers-backend device-auth:
 *   METHOD\npath\nquery\ntimestamp\nnonce\nbodyHash
 */
object SignedRequest {

    data class AuthHeaders(
        val deviceId: String,
        val timestamp: String,
        val nonce: String,
        val signatureBase64: String,
    ) {
        fun toMap(): Map<String, String> = mapOf(
            "X-Device-Id" to deviceId,
            "X-Timestamp" to timestamp,
            "X-Nonce" to nonce,
            "X-Signature" to signatureBase64,
        )
    }

    fun sha256Hex(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildSignedMessage(
        method: String,
        path: String,
        query: String,
        timestamp: String,
        nonce: String,
        bodyHash: String,
    ): String = listOf(
        method.uppercase(),
        path,
        query,
        timestamp,
        nonce,
        bodyHash,
    ).joinToString("\n")

    fun newNonce(): String = UUID.randomUUID().toString()

    fun nowMillis(): String = System.currentTimeMillis().toString()

    /**
     * Convert DER-encoded ECDSA signature to IEEE P1363 raw (r||s) for WebCrypto.
     * P-256 → 64 bytes.
     */
    fun derToRawP1363(der: ByteArray, size: Int = 32): ByteArray {
        // SEQUENCE { INTEGER r, INTEGER s }
        var offset = 0
        require(der[offset++] == 0x30.toByte()) { "not a DER SEQUENCE" }
        // skip sequence length (short or long form)
        val seqLen = der[offset].toInt() and 0xff
        if (seqLen and 0x80 != 0) {
            val n = seqLen and 0x7f
            offset += 1 + n
        } else {
            offset += 1
        }

        fun readInt(): ByteArray {
            require(der[offset++] == 0x02.toByte()) { "expected INTEGER" }
            val len = der[offset++].toInt() and 0xff
            val bytes = der.copyOfRange(offset, offset + len)
            offset += len
            // strip leading zero padding used for positive sign bit
            var start = 0
            while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
            val stripped = bytes.copyOfRange(start, bytes.size)
            return when {
                stripped.size == size -> stripped
                stripped.size < size -> ByteArray(size - stripped.size) + stripped
                else -> stripped.copyOfRange(stripped.size - size, stripped.size)
            }
        }

        val r = readInt()
        val s = readInt()
        return r + s
    }
}
