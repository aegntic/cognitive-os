package com.thresholdinc.insidher.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SmsParser")
class SmsParserTest {

    @Test
    @DisplayName("batches multipart PDUs by origin")
    fun batchMultipart() {
        val parts = listOf(
            ParsedSms("+15551212", "Hello ", 1000),
            ParsedSms("+15559999", "Other", 1001),
            ParsedSms("+15551212", "world", 1002),
        )
        val batched = SmsParser.batchByOrigin(parts)
        assertEquals(2, batched.size)
        assertEquals("+15551212", batched[0].from)
        assertEquals("Hello world", batched[0].body)
        assertEquals(1000L, batched[0].timestampMillis)
        assertEquals("+15559999", batched[1].from)
    }

    @Test
    @DisplayName("drops empty origin/body")
    fun dropsEmpty() {
        val parts = listOf(
            ParsedSms("", "x", 1),
            ParsedSms("+1", "", 2),
            ParsedSms("+1", "ok", 3),
        )
        assertEquals(listOf(ParsedSms("+1", "ok", 3)), SmsParser.batchByOrigin(parts))
    }

    @Test
    @DisplayName("truncates long body to 1600")
    fun truncate() {
        val long = "a".repeat(2000)
        assertEquals(1600, SmsParser.truncateBody(long).length)
        assertEquals("hi", SmsParser.truncateBody("hi"))
    }

    @Test
    @DisplayName("normalizes phone spacing")
    fun phone() {
        assertEquals("+15551212", SmsParser.normalizePhone(" +1555 1212 "))
    }
}
