package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.model.ResponseFormat
import com.chuishui.katago.ai.provider.AiHttpClient
import com.chuishui.katago.ai.provider.AiHttpResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingHttpClient : AiHttpClient {
    var lastUrl: String? = null
    var lastHeaders: Map<String, String> = emptyMap()
    var lastBody: String = ""
    var statusCode: Int = 200
    var responseBody: String = ""

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): AiHttpResponse {
        lastUrl = url
        lastHeaders = headers
        lastBody = body
        return AiHttpResponse(statusCode, responseBody)
    }
}

/** Exposes the protected [buildBody] so we can assert the wire schema. */
private class TestableProvider(config: OpenAiCompatibleConfig, client: AiHttpClient) :
    OpenAiCompatibleProvider(config, AiCapabilities(), client) {
    fun bodyFor(request: AiRequest, model: String): JSONObject = JSONObject(buildBody(request, model))
}

class OpenAiCompatibleProviderTest {

    private val groqOkBody =
        """
        {
          "id": "chatcmpl-f51b2cd2",
          "object": "chat.completion",
          "created": 1730241104,
          "model": "llama-3.3-70b-versatile",
          "choices": [{
            "index": 0,
            "message": { "role": "assistant", "content": "fast models are great" },
            "logprobs": null,
            "finish_reason": "stop"
          }],
          "usage": { "prompt_tokens": 18, "completion_tokens": 556, "total_tokens": 574 },
          "system_fingerprint": "fp_179b0f92c9",
          "x_groq": { "id": "req_01" }
        }
        """.trimIndent()

    // ---- Groq-specific: conforms to the grop.md reference ------------------

    @Test
    fun groqUsesMaxCompletionTokensNotDeprecatedMaxTokens() = runBlocking {
        val client = RecordingHttpClient().apply { responseBody = groqOkBody }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        provider.chat(AiRequest(userPrompt = "hi", maxTokens = 512))
        val root = JSONObject(client.lastBody)
        assertTrue(root.has("max_completion_tokens"))
        assertEquals(512, root.getInt("max_completion_tokens"))
        assertFalse(root.has("max_tokens"))
    }

    @Test
    fun groqUsesBearerAuthAndChatCompletionsEndpoint() = runBlocking {
        val client = RecordingHttpClient().apply { responseBody = groqOkBody }
        val provider = GroqProvider.factory(
            apiKey = "sk-test",
            baseUrl = "https://api.groq.com/openai/v1",
            httpClient = client,
        )
        provider.chat(AiRequest(userPrompt = "hi"))
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            client.lastUrl,
        )
        assertEquals("Bearer sk-test", client.lastHeaders["Authorization"])
    }

    @Test
    fun baseUrlAlreadyEndingWithEndpointIsNotDoubled() = runBlocking {
        val client = RecordingHttpClient().apply { responseBody = groqOkBody }
        val provider = GroqProvider.factory(
            apiKey = "sk-test",
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            httpClient = client,
        )
        provider.chat(AiRequest(userPrompt = "hi"))
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            client.lastUrl,
        )
    }

    @Test
    fun groqJsonModeSendsJsonObject() = runBlocking {
        val client = RecordingHttpClient().apply { responseBody = groqOkBody }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        provider.chat(AiRequest(userPrompt = "hi", responseFormat = ResponseFormat.JSON))
        val root = JSONObject(client.lastBody)
        assertEquals("json_object", root.getJSONObject("response_format").getString("type"))
    }

    @Test
    fun groqParsesContentAndUsageFromResponse() = runBlocking {
        val client = RecordingHttpClient().apply { responseBody = groqOkBody }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        val response: AiResponse = provider.chat(AiRequest(userPrompt = "hi"))
        assertEquals("fast models are great", response.content)
        assertEquals("groq", response.providerId)
        assertEquals("stop", response.finishReason)
        assertEquals(18, response.inputTokens)
        assertEquals(556, response.outputTokens)
        assertEquals(574, response.totalTokens)
    }

    @Test
    fun emptyContentSurfacesResponseInfo() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody =
                """{"choices":[{"message":{"role":"assistant","content":null},"finish_reason":"length"}],"usage":{}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        val e = runCatching { provider.chat(AiRequest(userPrompt = "hi")) }.exceptionOrNull()
        val m = e?.message ?: ""
        assertTrue(m.contains("empty content"))
        assertTrue(m.contains("finish_reason=length"))
        assertTrue(m.contains("choices"))
    }

    @Test
    fun testConnectionSucceedsOn200EvenWithEmptyBody() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody = """{"choices":[],"usage":{}}"""
        }
        val provider = GroqProvider.factory(
            apiKey = "sk-test",
            baseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            httpClient = client,
        )
        val model = provider.testConnection()
        assertEquals("llama-3.3-70b-versatile", model)
        assertTrue(client.lastBody.contains("max_completion_tokens"))
    }

    @Test
    fun testConnectionFailsOnAuthError() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 401
            responseBody = """{"error":{"message":"Invalid API Key"}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-bad", httpClient = client)
        val e = runCatching { provider.testConnection() }.exceptionOrNull()
        assertTrue(e is com.chuishui.katago.ai.provider.AiException.AuthFailed)
    }

    @Test
    fun reasoningOnlyResponseFallsBackToReasoningText() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody =
                """{"choices":[{"index":0,"message":{"role":"assistant","content":null,"reasoning":"We need to output JSON with keys summary, reason, suggestion"},"finish_reason":"length"}],"usage":{}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        val resp = provider.chat(AiRequest(userPrompt = "hi"))
        assertTrue(resp.content.contains("output JSON"))
        assertEquals("length", resp.finishReason)
    }

    @Test
    fun chatIncludesReasoningEffortWhenRequested() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody = """{"choices":[{"message":{"content":"answer"}}],"usage":{}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        provider.chat(AiRequest(userPrompt = "hi", reasoningEffort = "low"))
        val root = JSONObject(client.lastBody)
        assertEquals("low", root.getString("reasoning_effort"))
    }

    @Test
    fun chatOmitsReasoningEffortByDefault() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody = """{"choices":[{"message":{"content":"answer"}}],"usage":{}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        provider.chat(AiRequest(userPrompt = "hi"))
        val root = JSONObject(client.lastBody)
        assertFalse(root.has("reasoning_effort"))
    }

    @Test
    fun chatIncludesHistoryMessages() = runBlocking {
        val client = RecordingHttpClient().apply {
            statusCode = 200
            responseBody = """{"choices":[{"message":{"content":"answer"}}],"usage":{}}"""
        }
        val provider = GroqProvider.factory(apiKey = "sk-test", httpClient = client)
        provider.chat(
            AiRequest(
                userPrompt = "next",
                history = listOf(
                    AiChatMessage("user", "q1"),
                    AiChatMessage("assistant", "a1"),
                ),
            )
        )
        val root = JSONObject(client.lastBody)
        val messages = root.getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("q1", messages.getJSONObject(0).getString("content"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("a1", messages.getJSONObject(1).getString("content"))
        assertEquals("user", messages.getJSONObject(2).getString("role"))
        assertEquals("next", messages.getJSONObject(2).getString("content"))
    }

    // ---- default OpenAI-compatible behavior keeps legacy field -------------

    @Test
    fun defaultConfigKeepsMaxTokensField() {
        val cfg = OpenAiCompatibleConfig(
            providerId = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "k",
            defaultModel = "gpt-5",
        )
        val provider = TestableProvider(cfg, RecordingHttpClient())
        val root = provider.bodyFor(AiRequest(userPrompt = "hi", maxTokens = 256), "gpt-5")
        assertEquals(256, root.getInt("max_tokens"))
        assertFalse(root.has("max_completion_tokens"))
    }

    @Test
    fun bodyIncludesSystemAndUserMessages() {
        val cfg = OpenAiCompatibleConfig(
            providerId = "x", name = "X", baseUrl = "http://u", apiKey = "k", defaultModel = "m",
        )
        val provider = TestableProvider(cfg, RecordingHttpClient())
        val root = provider.bodyFor(AiRequest(systemPrompt = "coach", userPrompt = "why?"), "m")
        val messages = root.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
    }
}