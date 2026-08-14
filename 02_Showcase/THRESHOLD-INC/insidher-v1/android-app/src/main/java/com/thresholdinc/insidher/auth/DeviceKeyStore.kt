package com.thresholdinc.insidher.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * ECDSA P-256 keypair in Android Keystore + signing for [SignedRequest].
 */
class DeviceKeyStore(
    private val alias: String = KEY_ALIAS,
) {
    fun ensureKey(): String {
        val ks = keyStore()
        if (!ks.containsAlias(alias)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
        return deviceId()
    }

    /** Stable device id derived from key alias (UUID stored separately by app prefs). */
    fun deviceId(): String = alias

    fun publicKeySpkiBase64(): String {
        ensureKey()
        val entry = keyStore().getCertificate(alias).publicKey as ECPublicKey
        val encoded = entry.encoded // X.509 SPKI
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    fun sign(payload: ByteArray): ByteArray {
        ensureKey()
        val privateKey = (keyStore().getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(payload)
        val der = sig.sign()
        return SignedRequest.derToRawP1363(der)
    }

    fun signBase64(payload: ByteArray): String =
        Base64.encodeToString(sign(payload), Base64.NO_WRAP)

    fun buildAuthHeaders(
        method: String,
        path: String,
        query: String,
        body: String,
        deviceId: String,
        timestamp: String = SignedRequest.nowMillis(),
        nonce: String = SignedRequest.newNonce(),
    ): SignedRequest.AuthHeaders {
        val bodyHash = SignedRequest.sha256Hex(body)
        val message = SignedRequest.buildSignedMessage(method, path, query, timestamp, nonce, bodyHash)
        val signature = signBase64(message.toByteArray(Charsets.UTF_8))
        return SignedRequest.AuthHeaders(deviceId, timestamp, nonce, signature)
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "insidher_device_p256"
    }
}
