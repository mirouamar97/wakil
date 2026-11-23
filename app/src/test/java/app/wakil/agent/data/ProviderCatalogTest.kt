package app.wakil.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    private val accountId = "0123456789abcdef0123456789abcdef"

    @Test fun constructsEachOfficialEndpointAndCurrentDefaultModel() {
        val providers = ProviderCatalog.createProviders(cloudflareAccountId = accountId)

        assertEquals(listOf("cloudflare", "gemini", "groq", "openrouter", "mistral"), providers.map { it.id })
        assertEquals(
            listOf(
                "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/v1",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "https://api.groq.com/openai/v1",
                "https://openrouter.ai/api/v1",
                "https://api.mistral.ai/v1",
            ),
            providers.map { it.baseUrl },
        )
        assertEquals(
            listOf(
                "@cf/zai-org/glm-4.7-flash",
                "gemini-3.8-flash",
                "openai/gpt-oss-120b",
                "openrouter/free",
                "mistral-small-latest",
            ),
            providers.map { it.model },
        )
        assertEquals("default", providers.first { it.id == "cloudflare" }.requestHeaders["cf-aig-gateway-id"])
        assertTrue(providers.filterNot { it.id == "cloudflare" }.all { it.requestHeaders.isEmpty() })
    }

    @Test fun omitsCloudflareUntilValidAccountIdIsConfigured() {
        val withoutAccount = ProviderCatalog.createProviders()
        assertFalse(withoutAccount.any { it.id == "cloudflare" })
        assertNull(ProviderCatalog.normalizeCloudflareAccountId("not-an-account-id"))
        assertTrue(ProviderCatalog.createProviders(cloudflareAccountId = "bad/../id").none { it.id == "cloudflare" })
    }

    @Test fun userPriorityIsRespectedAndMissingProvidersAreAppendedOnce() {
        val providers = ProviderCatalog.createProviders(
            priority = listOf("groq", "groq", "unknown", "mistral"),
            cloudflareAccountId = accountId,
        )

        assertEquals(listOf("groq", "mistral", "cloudflare", "gemini", "openrouter"), providers.map { it.id })
        assertEquals(
            listOf("groq", "mistral", "cloudflare", "gemini", "openrouter"),
            ProviderCatalog.normalizePriority(listOf("groq", "groq", "unknown", "mistral")),
        )
    }

    @Test fun blankModelOverrideUsesCatalogDefaultAndValidOverrideIsApplied() {
        val providers = ProviderCatalog.createProviders(
            cloudflareAccountId = accountId,
            modelOverrides = mapOf("gemini" to " custom-model ", "groq" to " "),
        )

        assertEquals("custom-model", providers.first { it.id == "gemini" }.model)
        assertEquals("openai/gpt-oss-120b", providers.first { it.id == "groq" }.model)
    }
}
