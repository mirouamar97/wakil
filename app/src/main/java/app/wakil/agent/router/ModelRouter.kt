package app.wakil.agent.router

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tries providers in priority order and falls back on rate limits, server errors, and network failures.
 * Only the first saved key for a provider is used, so additional accounts are never rotated to bypass limits.
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
    private val isKillSwitchActive: () -> Boolean = { false },
) {
    private val cooldownUntil = HashMap<String, Long>()   // "provider#keyIndex" -> epoch ms
    private val rejectedKeys = HashSet<String>()           // keys the provider refused (401/403)
    private val quota = HashMap<String, QuotaInfo>()       // provider id -> last known quota

    private fun slot(providerId: String, index: Int) = "$providerId#$index"

    suspend fun chat(messages: List<ChatMessage>): RouterResult =
        executeCall(messages, streamCallback = null, resetStreamCallback = null)

    suspend fun chatStream(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onAttemptReset: () -> Unit = {},
    ): RouterResult = executeCall(messages, streamCallback = onChunk, resetStreamCallback = onAttemptReset)

    private suspend fun executeCall(
        messages: List<ChatMessage>,
        streamCallback: ((String) -> Unit)?,
        resetStreamCallback: (() -> Unit)?,
    ): RouterResult {
        val attempts = ArrayList<AttemptLog>()
        val body = buildBody(messages)
        for (p in providers()) {
            val key = keysFor(p.id).firstOrNull()
            if (key == null) {
                attempts += AttemptLog(p.id, -1, Outcome.NO_KEY)
                continue
            }
            val index = 0
            val s = slot(p.id, index)
            val skipped = skipReason(s, key)
            if (skipped != null) {
                attempts += AttemptLog(p.id, index, skipped)
                continue
            }
            if (isKillSwitchActive()) {
                attempts += AttemptLog(p.id, index, Outcome.KILL_SWITCH_ACTIVE)
                return RouterResult.Failure(attempts)
            }

            val targetUrl = p.baseUrl.trimEnd('/') + "/chat/completions"
            val payload = bodyWithModel(body, p.model)
            val attemptChunks = StringBuilder()
            fun discardPartialStream() {
                if (attemptChunks.isNotEmpty()) {
                    attemptChunks.setLength(0)
                    resetStreamCallback?.invoke()
                }
            }

            val reply = try {
                if (streamCallback != null) {
                    transport.stream(targetUrl, key, payload, p.requestHeaders) { chunk ->
                        attemptChunks.append(chunk)
                        streamCallback.invoke(chunk)
                    }
                } else {
                    transport.post(targetUrl, key, payload, p.requestHeaders)
                }
            } catch (e: IOException) {
                discardPartialStream()
                rest(s, config.errorCooldownMs)
                attempts += AttemptLog(p.id, index, Outcome.NETWORK_ERROR)
                continue
            }

            val headers = RateLimit.lower(reply.headers)
            when {
                reply.status in 200..299 -> {
                    val text = extractText(reply.body)
                    RateLimit.parseQuota(headers, nowMs())?.let { q ->
                        synchronized(this) { quota[p.id] = q }
                        if (q.remainingRequests == 0L && q.resetAtMs != null) rest(s, q.resetAtMs - nowMs())
                    }
                    if (text == null) {
                        discardPartialStream()
                        attempts += AttemptLog(p.id, index, Outcome.BAD_RESPONSE)
                        continue
                    }
                    attempts += AttemptLog(p.id, index, Outcome.OK)
                    return RouterResult.Success(text, p.id, p.model, attempts)
                }
                reply.status == 429 -> {
                    discardPartialStream()
                    rest(s, RateLimit.cooldownFor429(headers) ?: config.defaultCooldownMs)
                    RateLimit.parseQuota(headers, nowMs())?.let { q -> synchronized(this) { quota[p.id] = q } }
                    attempts += AttemptLog(p.id, index, Outcome.RATE_LIMITED)
                }
                reply.status == 401 || reply.status == 403 -> {
                    discardPartialStream()
                    synchronized(this) { rejectedKeys += key }
                    attempts += AttemptLog(p.id, index, Outcome.AUTH_FAILED)
                }
                reply.status >= 500 -> {
                    discardPartialStream()
                    rest(s, config.errorCooldownMs)
                    attempts += AttemptLog(p.id, index, Outcome.SERVER_ERROR)
                }
                else -> {
                    discardPartialStream()
                    attempts += AttemptLog(p.id, index, Outcome.BAD_RESPONSE)
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
            val keys = keysFor(p.id).take(1)
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
