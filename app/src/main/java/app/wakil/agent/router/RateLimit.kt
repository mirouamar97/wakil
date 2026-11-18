package app.wakil.agent.router

/** Parsing of rate-limit response headers (`x-ratelimit-*`, `retry-after`). Pure functions. */
object RateLimit {

    private val unit = Regex("""(\d+(?:\.\d+)?)(ms|h|m|s)""")
    private val plainNumber = Regex("""\d+(?:\.\d+)?""")

    /** "1h2m3.5s", "250ms", "7.66s", "30" (seconds) -> milliseconds. Null if unparseable. */
    fun parseDurationMs(raw: String?): Long? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        if (plainNumber.matches(s)) return (s.toDouble() * 1000).toLong()
        var total = 0.0
        var consumed = 0
        for (m in unit.findAll(s)) {
            val v = m.groupValues[1].toDouble()
            total += when (m.groupValues[2]) {
                "ms" -> v
                "s" -> v * 1000
                "m" -> v * 60_000
                else -> v * 3_600_000
            }
            consumed += m.value.length
        }
        return if (consumed == s.length && consumed > 0) total.toLong() else null
    }

    /** Headers must be lower-cased by the caller (see [lower]). */
    fun parseQuota(headers: Map<String, String>, nowMs: Long): QuotaInfo? {
        fun long(name: String) = headers[name]?.trim()?.toLongOrNull()
        val limitReq = long("x-ratelimit-limit-requests")
        val remReq = long("x-ratelimit-remaining-requests")
        val limitTok = long("x-ratelimit-limit-tokens")
        val remTok = long("x-ratelimit-remaining-tokens")
        val resets = listOfNotNull(
            parseDurationMs(headers["x-ratelimit-reset-requests"]),
            parseDurationMs(headers["x-ratelimit-reset-tokens"]),
        )
        val resetAt = resets.maxOrNull()?.let { nowMs + it }
        if (limitReq == null && remReq == null && limitTok == null && remTok == null && resetAt == null) return null
        return QuotaInfo(limitReq, remReq, limitTok, remTok, resetAt)
    }

    /** How long to rest a key after HTTP 429: `retry-after` first, else the provider's reset hints, else null. */
    fun cooldownFor429(headers: Map<String, String>): Long? {
        parseDurationMs(headers["retry-after"])?.let { return it }
        return listOfNotNull(
            parseDurationMs(headers["x-ratelimit-reset-requests"]),
            parseDurationMs(headers["x-ratelimit-reset-tokens"]),
        ).maxOrNull()
    }

    fun lower(headers: Map<String, String>): Map<String, String> =
        headers.entries.associate { it.key.lowercase() to it.value }
}
