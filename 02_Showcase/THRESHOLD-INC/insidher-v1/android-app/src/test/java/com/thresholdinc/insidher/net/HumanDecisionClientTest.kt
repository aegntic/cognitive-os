package com.thresholdinc.insidher.net

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HumanDecisionClientTest {
    @Test
    fun `decision body includes expectedRevision when set`() {
        val decision = "APPROVE"
        val revision = 3
        val body = """{"decision":"$decision","expectedRevision":$revision}"""
        assertTrue(body.contains("APPROVE"))
        assertTrue(body.contains("expectedRevision"))
        assertTrue(body.contains("3"))
    }
}
