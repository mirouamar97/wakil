package app.wakil.agent.router

import java.io.IOException

/** Pure data types for the router. No Android classes here, so everything runs in JVM unit tests. */

enum class Role(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }

data class ChatMessage(val role: Role, val content: String)

/** An OpenAI-compatible endpoint. [baseUrl] is the part before `/chat/completions`. */
data class Provider(val id: String, val baseUrl: String, val model: String)

data class HttpReply(val status: Int, val body: String, val headers: Map<String, String>)

/** Sends one chat-completions POST. Implementations throw [IOException] on network failure. */
interface ChatTransport {
    @Throws(IOException::class)
    suspend fun post(url: String, apiKey: String, body: String): HttpReply
}

/** Quota as reported by the provider's response headers. Null = the provider did not say. */
data class QuotaInfo(
    val limitRequests: Long? = null,
    val remainingRequests: Long? = null,
    val limitTokens: Long? = null,
    val remainingTokens: Long? = null,
    val resetAtMs: Long? = null,
)

enum class Outcome { OK, RATE_LIMITED, AUTH_FAILED, SERVER_ERROR, NETWORK_ERROR, BAD_RESPONSE, COOLING_DOWN, NO_KEY }

data class AttemptLog(val providerId: String, val keyIndex: Int, val outcome: Outcome)

sealed interface RouterResult {
    data class Success(
        val text: String,
        val providerId: String,
        val model: String,
        val attempts: List<AttemptLog>,
    ) : RouterResult

    data class Failure(val attempts: List<AttemptLog>) : RouterResult {
        /** True when nothing was even tried because no provider has a key. */
        val noKeys: Boolean get() = attempts.isNotEmpty() && attempts.all { it.outcome == Outcome.NO_KEY }
    }
}

/** What the UI shows for one provider. */
data class ProviderStatus(
    val providerId: String,
    val keyCount: Int,
    val coolingDownUntilMs: Long?,
    val quota: QuotaInfo?,
)

data class RouterConfig(
    /** Rest time after a 429 when the provider gives no reset hint. */
    val defaultCooldownMs: Long = 60_000L,
    /** Rest time after a 5xx / network error. */
    val errorCooldownMs: Long = 30_000L,
)
