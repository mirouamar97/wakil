package app.wakil.agent.data

import app.wakil.agent.router.Provider

/** Built-in provider order. Base URLs are OpenAI-compatible; model names are only defaults and can be edited in Settings. */
object ProviderCatalog {
    data class Entry(val id: String, val baseUrl: String, val defaultModel: String)

    val entries = listOf(
        Entry("groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b"),
        Entry("gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        Entry("openrouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
        Entry("mistral", "https://api.mistral.ai/v1", "mistral-small-latest"),
    )

    fun providers(modelOverride: (String) -> String?): List<Provider> =
        entries.map { Provider(it.id, it.baseUrl, modelOverride(it.id)?.takeIf { m -> m.isNotBlank() } ?: it.defaultModel) }
}
