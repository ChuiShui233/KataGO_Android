package com.chuishui.katago.ai.provider.anthropic

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.provider.AiHttpClient
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.HttpUrlConnectionClient
import com.chuishui.katago.ai.provider.ProviderHealth
import com.chuishui.katago.ai.provider.ProviderStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic Claude — uses the native `/v1/messages` schema.
 */
class ClaudeProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val defaultModel: String = DEFAULT_MODEL,
    private val apiVersion: String = "2023-06-01",
    override val capabilities: AiCapabilities = AiCapabilities(supportsToolCalling = true),
    private val httpClient: AiHttpClient = HttpUrlConnectionClient(),
) : AiProvider {

    override val id: String = "anthropic"
    override val name: String = "Anthropic Claude"
    override val health: ProviderHealth = ProviderHealth()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val DEFAULT_MODEL = "claude-sonnet-4"
    }

    override suspend fun isAvailable(): Boolean =
        apiKey.isNotBlank() && (health.status == ProviderStatus.AVAILABLE ||
            health.consecutiveFailures < 3)

    private fun buildBody(request: AiRequest): String {
        val root = JSONObject()
        root.put("model", request.model ?: defaultModel)
        root.put("max_tokens", request.maxTokens)
        root.put("temperature", request.temperature)
        if (request.systemPrompt.isNotBlank()) root.put("system", request.systemPrompt)
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", request.userPrompt)
        )
        root.put("messages", messages)
        return root.toString()
    }

    override suspend fun chat(request: AiRequest): AiResponse {
        if (apiKey.isBlank()) throw AiException.NotConfigured("$id: no api key configured")
        health.onConfigured()
        val model = request.model ?: defaultModel
        val started = System.currentTimeMillis()
        val url = "${baseUrl.trimEnd('/')}/v1/messages"
        val headers = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to apiVersion,
            "Accept" to "application/json",
        )
        val http = httpClient.post(url, headers, buildBody(request), request.timeoutMs)
        val latency = System.currentTimeMillis() - started
        if (http.statusCode !in 200..299) throw httpError(http)
        val response = try {
            val root = JSONObject(http.body)
            val text = buildString {
                val arr = root.optJSONArray("content") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val part = arr.optJSONObject(i) ?: continue
                    if (part.optString("type") == "text") append(part.optString("text", ""))
                }
            }.trim()
            if (text.isEmpty()) throw AiException.ParseError("$id: empty content")
            val usage = root.optJSONObject("usage")
            val input = usage?.optInt("input_tokens", -1)?.takeIf { it >= 0 }
            val output = usage?.optInt("output_tokens", -1)?.takeIf { it >= 0 }
            AiResponse(
                providerId = id,
                model = model,
                content = text,
                inputTokens = input,
                outputTokens = output,
                totalTokens = input?.let { i -> output?.let { o -> i + o } },
                latencyMs = latency,
                finishReason = root.optString("stop_reason", null),
            )
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.ParseError("$id: bad response: ${e.message}", e)
        }
        health.onSuccess(latency)
        return response
    }

    private fun httpError(http: com.chuishui.katago.ai.provider.AiHttpResponse): AiException {
        val message = try {
            JSONObject(http.body).optJSONObject("error")?.optString("message", "") ?: http.body.take(300)
        } catch (e: Exception) {
            http.body.take(300)
        }
        return when (http.statusCode) {
            401, 403 -> {
                health.onFailure(ProviderStatus.AUTH_FAILED)
                AiException.AuthFailed("$id: $message")
            }
            429 -> {
                health.onFailure(ProviderStatus.RATE_LIMITED, markRateLimited = true)
                AiException.RateLimited("$id: $message")
            }
            in 500..599 -> {
                health.onFailure(ProviderStatus.ERROR)
                AiException.ServerError("$id: $message", http.statusCode)
            }
            else -> {
                health.onFailure(ProviderStatus.ERROR)
                AiException.ServerError("$id: $message", http.statusCode)
            }
        }
    }
}
