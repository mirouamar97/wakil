package app.wakil.agent.router

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {

    private class FakeTransport(
        private val script: (url: String, key: String) -> HttpReply
    ) : ChatTransport {
        val calls = ArrayList<Pair<String, String>>()
        val callHeaders = ArrayList<Map<String, String>>()
        override suspend fun post(url: String, apiKey: String, body: String): HttpReply {
            return post(url, apiKey, body, emptyMap())
        }

        override suspend fun post(
            url: String,
            apiKey: String,
            body: String,
            headers: Map<String, String>,
        ): HttpReply {
            calls += url to apiKey
            callHeaders += headers
            return script(url, apiKey)
        }

        override suspend fun stream(
            url: String,
            apiKey: String,
            body: String,
            onChunk: (String) -> Unit
        ): HttpReply {
            return stream(url, apiKey, body, emptyMap(), onChunk)
        }

        override suspend fun stream(
            url: String,
            apiKey: String,
            body: String,
            headers: Map<String, String>,
            onChunk: (String) -> Unit,
        ): HttpReply {
            calls += url to apiKey
            callHeaders += headers
            val reply = script(url, apiKey)
            if (reply.status in 200..299) {
                // simulate chunking
                onChunk("chunk1 ")
                onChunk("chunk2")
            }
            return reply
        }
    }

    private fun ok(text: String, headers: Map<String, String> = emptyMap()) =
        HttpReply(200, """{"choices":[{"message":{"role":"assistant","content":"$text"}}]}""", headers)

    private val groq = Provider("groq", "https://groq.example/v1", "m-groq")
    private val gemini = Provider("gemini", "https://gemini.example/v1/", "m-gemini")
    private val msgs = listOf(ChatMessage(Role.USER, "hi"))

    private var now = 1_000_000L
    private fun router(
        transport: ChatTransport,
        keys: Map<String, List<String>>,
        providers: List<Provider> = listOf(groq, gemini),
    ) = ModelRouter({ providers }, { keys[it] ?: emptyList() }, transport, RouterConfig(), { now })

    @Test fun firstProviderAnswers() = runBlocking {
        val t = FakeTransport { _, _ -> ok("hello") }
        val r = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2"))).chat(msgs)
        r as RouterResult.Success
        assertEquals("hello", r.text)
        assertEquals("groq", r.providerId)
        assertEquals(1, t.calls.size)
        assertEquals("https://groq.example/v1/chat/completions", t.calls[0].first)
    }

    @Test fun streamingEmitsChunksAndSucceeds() = runBlocking {
        val t = FakeTransport { _, _ -> ok("hello") }
        val chunks = mutableListOf<String>()
        val r = router(t, mapOf("groq" to listOf("k1"))).chatStream(
            messages = msgs,
            onChunk = { chunks.add(it) },
        )
        r as RouterResult.Success
        assertEquals("hello", r.text)
        assertEquals(listOf("chunk1 ", "chunk2"), chunks)
    }

    @Test fun trailingSlashInBaseUrlIsHandled() = runBlocking {
        val t = FakeTransport { _, _ -> ok("x") }
        router(t, mapOf("gemini" to listOf("k")), listOf(gemini)).chat(msgs)
        assertEquals("https://gemini.example/v1/chat/completions", t.calls[0].first)
    }

    @Test fun providerSpecificHeadersReachBothRegularAndStreamingRequests() = runBlocking {
        val cloudflare = Provider(
            "cloudflare",
            "https://cloudflare.example/accounts/account/ai/v1",
            "@cf/model",
            mapOf("cf-aig-gateway-id" to "default"),
        )
        val regular = FakeTransport { _, _ -> ok("regular") }
        router(regular, mapOf("cloudflare" to listOf("token")), listOf(cloudflare)).chat(msgs)
        assertEquals(mapOf("cf-aig-gateway-id" to "default"), regular.callHeaders.single())

        val streaming = FakeTransport { _, _ -> ok("streamed") }
        router(streaming, mapOf("cloudflare" to listOf("token")), listOf(cloudflare)).chatStream(msgs, {})
        assertEquals(mapOf("cf-aig-gateway-id" to "default"), streaming.callHeaders.single())
    }

    @Test fun movesToNextProviderOn429() = runBlocking {
        val t = FakeTransport { url, _ -> if ("groq" in url) HttpReply(429, "{}", emptyMap()) else ok("from gemini") }
        val r = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2"))).chat(msgs)
        r as RouterResult.Success
        assertEquals("gemini", r.providerId)
        assertEquals(Outcome.RATE_LIMITED, r.attempts[0].outcome)
    }

    @Test fun rateLimitedKeyRestsUntilRetryAfter() = runBlocking {
        val t = FakeTransport { url, _ ->
            if ("groq" in url) HttpReply(429, "{}", mapOf("Retry-After" to "120")) else ok("g")
        }
        val rt = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2")))
        rt.chat(msgs)
        assertEquals(2, t.calls.size)
        // Still resting: groq is skipped without a network call.
        now += 60_000
        rt.chat(msgs)
        assertEquals(3, t.calls.size)
        assertTrue(rt.status().first { it.providerId == "groq" }.coolingDownUntilMs != null)
        // After the rest period groq is tried again.
        now += 61_000
        rt.chat(msgs)
        assertEquals("groq", t.calls[3].first.substringAfter("//").substringBefore(".example"))
    }

    @Test fun doesNotRotateAccountsAfterRateLimit() = runBlocking {
        val t = FakeTransport { url, key ->
            if ("groq" in url) {
                assertEquals("first-account-key", key)
                HttpReply(429, "{}", emptyMap())
            } else {
                ok("fallback provider")
            }
        }
        val r = router(
            t,
            mapOf("groq" to listOf("first-account-key", "second-account-key"), "gemini" to listOf("fallback-key"))
        ).chat(msgs)
        r as RouterResult.Success
        assertEquals("gemini", r.providerId)
        assertEquals(listOf("first-account-key", "fallback-key"), t.calls.map { it.second })
        assertEquals(Outcome.RATE_LIMITED, r.attempts.first().outcome)
    }

    @Test fun failedStreamingAttemptsAreClearedBeforeFallback() = runBlocking {
        val providers = listOf(groq, gemini, Provider("cloudflare", "https://cloudflare.example/v1", "m-cf"))
        var visibleText = StringBuilder()
        val t = object : ChatTransport {
            override suspend fun post(url: String, apiKey: String, body: String): HttpReply =
                error("Streaming request expected")

            override suspend fun stream(
                url: String,
                apiKey: String,
                body: String,
                onChunk: (String) -> Unit
            ): HttpReply {
                when {
                    "groq" in url -> {
                        onChunk("partial from server error")
                        return HttpReply(503, "", emptyMap())
                    }
                    "gemini" in url -> {
                        onChunk("partial from network error")
                        throw IOException("connection closed")
                    }
                    else -> {
                        onChunk("clean ")
                        onChunk("response")
                        return ok("clean response")
                    }
                }
            }
        }
        val rt = ModelRouter(
            providers = { providers },
            keysFor = { listOf("test-key") },
            transport = t,
            nowMs = { now },
        )
        val result = rt.chatStream(
            messages = msgs,
            onChunk = { visibleText.append(it) },
            onAttemptReset = { visibleText = StringBuilder() },
        )

        result as RouterResult.Success
        assertEquals("clean response", result.text)
        assertEquals("clean response", visibleText.toString())
        assertEquals(listOf(Outcome.SERVER_ERROR, Outcome.NETWORK_ERROR, Outcome.OK), result.attempts.map { it.outcome })
    }

    @Test fun killSwitchIsCheckedBeforeEveryProviderAttempt() = runBlocking {
        var killSwitchActive = false
        val t = object : ChatTransport {
            val calls = mutableListOf<String>()
            override suspend fun post(url: String, apiKey: String, body: String): HttpReply {
                calls += url
                killSwitchActive = true
                return HttpReply(503, "", emptyMap())
            }
        }
        val rt = ModelRouter(
            providers = { listOf(groq, gemini) },
            keysFor = { listOf("test-key") },
            transport = t,
            nowMs = { now },
            isKillSwitchActive = { killSwitchActive },
        )

        val result = rt.chat(msgs) as RouterResult.Failure
        assertEquals(1, t.calls.size)
        assertEquals(Outcome.SERVER_ERROR, result.attempts[0].outcome)
        assertEquals(Outcome.KILL_SWITCH_ACTIVE, result.attempts[1].outcome)
    }

    @Test fun activeKillSwitchPreventsFirstNetworkAttempt() = runBlocking {
        val t = FakeTransport { _, _ -> ok("should not be sent") }
        val rt = ModelRouter(
            providers = { listOf(groq) },
            keysFor = { listOf("test-key") },
            transport = t,
            nowMs = { now },
            isKillSwitchActive = { true },
        )

        val result = rt.chat(msgs) as RouterResult.Failure
        assertTrue(t.calls.isEmpty())
        assertEquals(Outcome.KILL_SWITCH_ACTIVE, result.attempts.single().outcome)
    }

    @Test fun refusedKeyIsNotRetried() = runBlocking {
        val t = FakeTransport { url, _ -> if ("groq" in url) HttpReply(401, "{}", emptyMap()) else ok("g") }
        val rt = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2")))
        rt.chat(msgs)
        rt.chat(msgs)
        assertEquals(1, t.calls.count { "groq" in it.first })
        rt.clearRejectedKeys()
        rt.chat(msgs)
        assertEquals(2, t.calls.count { "groq" in it.first })
    }

    @Test fun networkErrorFallsOver() = runBlocking {
        val t = FakeTransport { url, _ -> if ("groq" in url) throw IOException("down") else ok("g") }
        val r = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2"))).chat(msgs)
        r as RouterResult.Success
        assertEquals(Outcome.NETWORK_ERROR, r.attempts[0].outcome)
    }

    @Test fun serverErrorFallsOver() = runBlocking {
        val t = FakeTransport { url, _ -> if ("groq" in url) HttpReply(503, "", emptyMap()) else ok("g") }
        val r = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2"))).chat(msgs)
        assertEquals(Outcome.SERVER_ERROR, (r as RouterResult.Success).attempts[0].outcome)
    }

    @Test fun malformedBodyFallsOver() = runBlocking {
        val t = FakeTransport { url, _ -> if ("groq" in url) HttpReply(200, "not json", emptyMap()) else ok("g") }
        val r = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2"))).chat(msgs)
        assertEquals(Outcome.BAD_RESPONSE, (r as RouterResult.Success).attempts[0].outcome)
    }

    @Test fun noKeysAnywhere() = runBlocking {
        val t = FakeTransport { _, _ -> ok("never") }
        val r = router(t, emptyMap()).chat(msgs)
        r as RouterResult.Failure
        assertTrue(r.noKeys)
        assertTrue(t.calls.isEmpty())
    }

    @Test fun allProvidersFail() = runBlocking {
        val t = FakeTransport { _, _ -> HttpReply(429, "{}", emptyMap()) }
        val r = router(t, mapOf("groq" to listOf("a"), "gemini" to listOf("b"))).chat(msgs)
        r as RouterResult.Failure
        assertFalse(r.noKeys)
        assertEquals(2, r.attempts.size)
    }

    @Test fun quotaIsReadFromHeaders() = runBlocking {
        val h = mapOf(
            "X-RateLimit-Limit-Requests" to "500",
            "X-RateLimit-Remaining-Requests" to "499",
            "x-ratelimit-reset-requests" to "2m30s",
        )
        val t = FakeTransport { _, _ -> ok("a", h) }
        val rt = router(t, mapOf("groq" to listOf("k")))
        rt.chat(msgs)
        val q = rt.status().first { it.providerId == "groq" }.quota!!
        assertEquals(500L, q.limitRequests)
        assertEquals(499L, q.remainingRequests)
        assertEquals(now + 150_000L, q.resetAtMs)
    }

    @Test fun providerWithoutHeadersHasNoQuota() = runBlocking {
        val t = FakeTransport { _, _ -> ok("a") }
        val rt = router(t, mapOf("gemini" to listOf("k")))
        rt.chat(msgs)
        assertNull(rt.status().first { it.providerId == "gemini" }.quota)
    }

    @Test fun exhaustedBudgetRestsKeyUntilReset() = runBlocking {
        val h = mapOf("x-ratelimit-remaining-requests" to "0", "x-ratelimit-reset-requests" to "10m")
        val t = FakeTransport { url, _ -> if ("groq" in url) ok("last one", h) else ok("g") }
        val rt = router(t, mapOf("groq" to listOf("k1"), "gemini" to listOf("k2")))
        assertEquals("groq", (rt.chat(msgs) as RouterResult.Success).providerId)
        assertEquals("gemini", (rt.chat(msgs) as RouterResult.Success).providerId)
    }

    @Test fun durationParsing() {
        assertEquals(7_660L, RateLimit.parseDurationMs("7.66s"))
        assertEquals(179_560L, RateLimit.parseDurationMs("2m59.56s"))
        assertEquals(3_723_000L, RateLimit.parseDurationMs("1h2m3s"))
        assertEquals(250L, RateLimit.parseDurationMs("250ms"))
        assertEquals(30_000L, RateLimit.parseDurationMs("30"))
        assertNull(RateLimit.parseDurationMs("soon"))
        assertNull(RateLimit.parseDurationMs(""))
        assertNull(RateLimit.parseDurationMs(null))
    }

    @Test fun requestBodyCarriesModelAndMessages() = runBlocking {
        var seen = ""
        val t = object : ChatTransport {
            override suspend fun post(url: String, apiKey: String, body: String): HttpReply {
                seen = body
                return ok("x")
            }
        }
        router(t, mapOf("groq" to listOf("k")), listOf(groq)).chat(msgs)
        val j = org.json.JSONObject(seen)
        assertEquals("m-groq", j.getString("model"))
        assertEquals("user", j.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("hi", j.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun everyProviderUsesItsOfficialEndpointAndOpenAiCompatibleRequestShape() = runBlocking {
        val accountId = "0123456789abcdef0123456789abcdef"
        val providers = app.wakil.agent.data.ProviderCatalog.createProviders(cloudflareAccountId = accountId)
        val urls = mutableListOf<String>()
        val keys = mutableListOf<String>()
        val models = mutableListOf<String>()
        val t = object : ChatTransport {
            override suspend fun post(url: String, apiKey: String, body: String): HttpReply {
                urls += url
                keys += apiKey
                models += org.json.JSONObject(body).getString("model")
                return HttpReply(
                    200,
                    """{"model":"provider-response","choices":[{"message":{"role":"assistant","content":"provider reply"}}]}""",
                    emptyMap(),
                )
            }
        }
        val expectedUrls = mapOf(
            "cloudflare" to "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/v1/chat/completions",
            "gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "groq" to "https://api.groq.com/openai/v1/chat/completions",
            "openrouter" to "https://openrouter.ai/api/v1/chat/completions",
            "mistral" to "https://api.mistral.ai/v1/chat/completions",
        )
        for (provider in providers) {
            val router = ModelRouter(
                providers = { listOf(provider) },
                keysFor = { listOf("mock-key-" + provider.id) },
                transport = t,
                nowMs = { now },
            )
            val result = router.chat(msgs) as RouterResult.Success
            assertEquals("provider reply", result.text)
            assertEquals(provider.id, result.providerId)
        }

        assertEquals(providers.map { expectedUrls.getValue(it.id) }, urls)
        assertEquals(providers.map { "mock-key-" + it.id }, keys)
        assertEquals(providers.map { it.model }, models)
    }
}
