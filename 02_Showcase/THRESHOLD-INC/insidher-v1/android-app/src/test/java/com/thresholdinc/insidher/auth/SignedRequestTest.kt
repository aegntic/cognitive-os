package com.thresholdinc.insidher.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@DisplayName("SignedRequest helpers")
class SignedRequestTest {

    @Test
    @DisplayName("sha256Hex is stable hex of UTF-8 body")
    fun bodyHash() {
        // echo -n '' | sha256sum
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SignedRequest.sha256Hex(""),
        )
        assertEquals(
            "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
            SignedRequest.sha256Hex("foo"),
        )
    }

    @Test
    @DisplayName("signed message matches backend wire format")
    fun messageFormat() {
        val msg = SignedRequest.buildSignedMessage(
            method = "post",
            path = "/api/threads",
            query = "?page=1",
            timestamp = "1700000000000",
            nonce = "n-1",
            bodyHash = "abc",
        )
        assertEquals(
            "POST\n/api/threads\n?page=1\n1700000000000\nn-1\nabc",
            msg,
        )
    }

    @Test
    @DisplayName("DER ECDSA signature converts to 64-byte P1363")
    fun derToRaw() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(kp.private)
        sig.update("hello".toByteArray())
        val der = sig.sign()
        val raw = SignedRequest.derToRawP1363(der)
        assertEquals(64, raw.size)
        // verify with soft key using DER still works; raw is for WebCrypto
        assertTrue(der.size > 64 || der.size >= 70)
    }

    @Test
    @DisplayName("AuthHeaders map uses X-Device-* keys")
    fun headerMap() {
        val h = SignedRequest.AuthHeaders("dev", "ts", "n", "sig")
        val m = h.toMap()
        assertEquals("dev", m["X-Device-Id"])
        assertEquals("ts", m["X-Timestamp"])
        assertEquals("n", m["X-Nonce"])
        assertEquals("sig", m["X-Signature"])
    }
}
