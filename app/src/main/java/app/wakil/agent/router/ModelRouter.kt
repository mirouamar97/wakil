package app.wakil.agent.router

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tries providers in order and moves to the next one when a key is rate limited (429),
 * rejected (401/403), or the provider fails. Several keys per provider are rotated the same way.
 *
 * Quota numbers are never hard-coded: they come from the provider's `x-ratelimit-*` headers.
 * Everything here is pure Kotlin (time and HTTP are injected), so it is unit-tested on the JVM.
 *
 * @param providers  priority order (first = preferred)
 * @param keysFor    returns the current keys of a provider id (re-read on every call, so Settings changes apply at once)
 */
class ModelRouter(
    private val providers: () -> List<Provider>,
    private val keysFor: (String) -> List<String>,
    private val transport: ChatTransport,
    private val config: RouterConfig = RouterConfig(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val cooldownUntil = HashMap<String, Long>()   // "provider#keyIndex" -> epoch ms
    private val rejectedKeys = HashSet<String>()           // keys the provider refused (401/403)
    private val quota = HashMap<String, QuotaInfo>()       // provider id -> last known quota

    private fun slot(providerId: String, index: Int) = "$providerId#$index"

    suspend fun chat(messages: List<ChatMessage>): RouterResult {
        val attempts = ArrayList<AttemptLog>()
        val body = buildBody(messages)
        for (p in providers()) {
            val keys = keysFor(p.id)
            if (keys.isEmpty()) {
                attempts += AttemptLog(p.id, -1, Outcome.NO_KEY)
                continue
            }
            for ((i, key) in keys.withIndex()) {
                val s = slot(p.id, i)
                val skipped = skipReason(s, key)
                if (skipped != null) {
                    attempts += AttemptLog(p.id, i, skipped)
                    continue
                }

                val reply = try {
                    transport.post(p.baseUrl.trimEnd('/') + "/chat/completions", key, bodyWithModel(body, p.model))
                } catch (e: IOException) {
                    rest(s, config.errorCooldownMs)
                    attempts += AttemptLog(p.id, i, Outcome.NETWORK_ERROR)
                    continue
                }

                val headers = RateLimit.lower(reply.headers)
                when {
                    reply.status in 200..299 -> {
                        val text = extractText(reply.body)
                        RateLimit.parseQuota(headers, nowMs())?.let { q ->
                            synchronized(this) { quota[p.id] = q }
                            // The provider says the budget is spent: rest this key until its reset.
                            if (q.remainingRequests == 0L && q.resetAtMs != null) rest(s, q.resetAtMs - nowMs())
                        }
                        if (text == null) {
                            attempts += AttemptLog(p.id, i, Outcome.BAD_RESPONSE)
                            continue
                        }
                        attempts += AttemptLog(p.id, i, Outcome.OK)
                        return RouterResult.Success(text, p.id, p.model, attempts)
                    }
                    reply.status == 429 -> {
                        rest(s, RateLimit.cooldownFor429(headers) ?: config.defaultCooldownMs)
                        RateLimit.parseQuota(headers, nowMs())?.let { q -> synchronized(this) { quota[p.id] = q } }
                        attempts += AttemptLog(p.id, i, Outcome.RATE_LIMITED)
                    }
                    reply.status == 401 || reply.status == 403 -> {
                        synchronized(this) { rejectedKeys += key }
                        attempts += AttemptLog(p.id, i, Outcome.AUTH_FAILED)
                    }
                    reply.status >= 500 -> {
                        rest(s, config.errorCooldownMs)
                        attempts += AttemptLog(p.id, i, Outcome.SERVER_ERROR)
                    }
                    else -> attempts += AttemptLog(p.id, i, Outcome.BAD_RESPONSE)
                }
            }
        }
        return RouterResult.Failure(attempts)
    }

    /** Forget a refused key (e.g. after the user replaced keys in Settings). */
    fun clearRejectedKeys() = synchronized(this) { rejectedKeys.clear() }

    fun status(): List<ProviderStatus> = synchronized(this) {
        val now = nowMs()
        providers().map { p ->
            val keys = keysFor(p.id)
            val live = keys.withIndex().filter { (_, k) -> k !in rejectedKeys }
            val allResting = live.isNotEmpty() && live.all { (i, _) -> (cooldownUntil[slot(p.id, i)] ?: 0L) > now }
            val until = if (allResting) live.minOf { (i, _) -> cooldownUntil[slot(p.id, i)]!! } else null
            ProviderStatus(p.id, live.size, until, quota[p.id])
        }
    }

    private fun skipReason(slot: String, key: String): Outcome? = synchronized(this) {
        val until = cooldownUntil[slot]
        when {
            key in rejectedKeys -> Outcome.AUTH_FAILED
            until != null && until > nowMs() -> Outcome.COOLING_DOWN
            else -> null
        }
    }

    private fun rest(slot: String, forMs: Long) = synchronized(this) {
        cooldownUntil[slot] = nowMs() + forMs.coerceAtLeast(0L)
    }

    private fun buildBody(messages: List<ChatMessage>): JSONObject {
        val arr = JSONArray()
        for (m in messages) arr.put(JSONObject().put("role", m.role.wire).put("content", m.content))
        return JSONObject().put("messages", arr).put("stream", false)
    }

    private fun bodyWithModel(base: JSONObject, model: String): String =
        JSONObject(base.toString()).put("model", model).toString()

    private fun extractText(json: String): String? = try {
        val choices = JSONObject(json).optJSONArray("choices")
        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
        content?.takeIf { it.isNotBlank() }
    } catch (e: org.json.JSONException) {
        null
    }
}
