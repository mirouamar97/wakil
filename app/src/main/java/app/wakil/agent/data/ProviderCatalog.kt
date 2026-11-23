package app.wakil.agent.data

import app.wakil.agent.router.Provider

/** Direct OpenAI-compatible provider endpoints and current default chat models. */
object ProviderCatalog {
    data class Entry(val id: String, val baseUrl: String, val defaultModel: String)

    val entries = listOf(
        Entry("cloudflare", "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1", "@cf/zai-org/glm-4.7-flash"),
        Entry("gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash"),
        Entry("groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b"),
        Entry("openrouter", "https://openrouter.ai/api/v1", "openrouter/free"),
        Entry("mistral", "https://api.mistral.ai/v1", "mistral-small-latest"),
    )

    /** Cloudflare → Gemini → Groq, followed by the remaining supported providers. */
    val defaultPriority: List<String> = entries.map { it.id }

    private val entriesById = entries.associateBy { it.id }

    fun normalizePriority(priority: List<String>): List<String> {
        val requested = priority.filter { it in entriesById }.distinct()
        return requested + defaultPriority.filterNot { it in requested }
    }

    /** Cloudflare account identifiers are 32 hexadecimal characters. */
    fun normalizeCloudflareAccountId(accountId: String?): String? =
        accountId?.trim()?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }

    /** Pure builder so endpoint and priority behavior can be tested on the JVM. */
    fun createProviders(
        priority: List<String> = defaultPriority,
        cloudflareAccountId: String? = null,
        modelOverrides: Map<String, String> = emptyMap(),
    ): List<Provider> {
        val accountId = normalizeCloudflareAccountId(cloudflareAccountId)
        return normalizePriority(priority).mapNotNull { id ->
            val entry = entriesById.getValue(id)
            val baseUrl = if (id == "cloudflare") {
                if (accountId == null) return@mapNotNull null
                entry.baseUrl.replace("{account_id}", accountId)
            } else {
                entry.baseUrl
            }
            val model = modelOverrides[id]?.trim()?.takeIf { it.isNotEmpty() } ?: entry.defaultModel
            val requestHeaders = if (id == "cloudflare") {
                mapOf("cf-aig-gateway-id" to "default")
            } else {
                emptyMap()
            }
            Provider(id, baseUrl, model, requestHeaders)
        }
    }

    fun providers(settingsStore: SettingsStore): List<Provider> = createProviders(
        priority = settingsStore.providerPriority(),
        cloudflareAccountId = settingsStore.cloudflareAccountId(),
        modelOverrides = entries.associate { it.id to settingsStore.model(it.id).orEmpty() },
    )
}
