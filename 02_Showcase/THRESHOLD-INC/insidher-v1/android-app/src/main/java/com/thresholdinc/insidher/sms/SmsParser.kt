package com.thresholdinc.insidher.sms

/**
 * Pure SMS parsing / batching helpers (no Android APIs).
 * Multipart SMS arrives as multiple PDUs with the same origin; we merge bodies.
 */
data class ParsedSms(
    val from: String,
    val body: String,
    val timestampMillis: Long,
)

object SmsParser {

    /**
     * Batch PDU parts by originating address (preserves first-seen order).
     * Parts for the same sender are concatenated in arrival order.
     * Empty origin/body parts are dropped.
     */
    fun batchByOrigin(parts: List<ParsedSms>): List<ParsedSms> {
        if (parts.isEmpty()) return emptyList()
        val order = linkedMapOf<String, MutableList<ParsedSms>>()
        for (p in parts) {
            val from = p.from.trim()
            if (from.isEmpty() || p.body.isEmpty()) continue
            order.getOrPut(from) { mutableListOf() }.add(p)
        }
        return order.map { (from, group) ->
            ParsedSms(
                from = from,
                body = group.joinToString("") { it.body },
                timestampMillis = group.minOf { it.timestampMillis },
            )
        }
    }

    /** Truncate to backend max body length (1600). */
    fun truncateBody(body: String, max: Int = 1600): String =
        if (body.length <= max) body else body.substring(0, max)

    fun normalizePhone(raw: String): String = raw.trim().replace(" ", "")
}
