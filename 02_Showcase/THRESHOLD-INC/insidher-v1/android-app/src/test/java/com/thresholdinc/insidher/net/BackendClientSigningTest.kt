package com.thresholdinc.insidher.net

import com.thresholdinc.insidher.auth.SignedRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("BackendClient signing helpers")
class BackendClientSigningTest {

    @Test
    fun bodyHashDelegatesToSignedRequest() {
        assertEquals(
            SignedRequest.sha256Hex("{\"a\":1}"),
            BackendClient.bodyHashForTest("{\"a\":1}"),
        )
    }

    @Test
    fun defaultBaseUrlIsEmulatorLoopback() {
        assertEquals("http://10.0.2.2:8788", BackendClient.DEFAULT_BASE_URL)
    }

    @Test
    fun signedMessageUsesEmptyBodyHashForGet() {
        val hash = SignedRequest.sha256Hex("")
        val msg = SignedRequest.buildSignedMessage(
            "GET",
            "/api/devices/dev/outbound",
            "",
            "1",
            "n",
            hash,
        )
        assertEquals(
            "GET\n/api/devices/dev/outbound\n\n1\nn\n$hash",
            msg,
        )
    }
}
