package com.chuishui.katago.ai.provider.gemini

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.model.ResponseFormat
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.provider.AiHttpClient
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.HttpUrlConnectionClient
import com.chuishui.katago.ai.provider.ProviderHealth
import com.chuishui.katago.ai.provider.ProviderStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini — uses the native `generateContent` REST schema, not
 * OpenAI's chat completions.
 */
class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val defaultModel: String = DEFAULT_MODEL,
    override val capabilities: AiCapabilities = AiCapabilities(supportsVision = true),
    private val httpClient: AiHttpClient = HttpUrlConnectionClient(),
) : AiProvider {

    override val id: String = "google"
    override val name: String = "Google Gemini"
    override val health: ProviderHealth = ProviderHealth()

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    override suspend fun isAvailable(): Boolean =
        apiKey.isNotBlank() && (health.status == ProviderStatus.AVAILABLE ||
            health.consecutiveFailures < 3)

    private fun buildBody(request: AiRequest): String {
        val root = JSONObject()
        if (request.systemPrompt.isNotBlank()) {
            root.put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemPrompt))),
            )
        }
        val contents = JSONArray().put(
            JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", request.userPrompt)),
            )
        )
        root.put("contents", contents)
        val genConfig = JSONObject()
        genConfig.put("temperature", request.temperature)
        genConfig.put("maxOutputTokens", request.maxTokens)
        if (request.responseFormat == ResponseFormat.JSON) {
            genConfig.put("responseMimeType", "application/json")
        }
        root.put("generationConfig", genConfig)
        return root.toString()
    }

    override suspend fun chat(request: AiRequest): AiResponse {
        if (apiKey.isBlank()) throw AiException.NotConfigured("$id: no api key configured")
        health.onConfigured()
        val model = request.model ?: defaultModel
        val started = System.currentTimeMillis()
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent?key=$apiKey"
        val http = httpClient.post(
            url,
            mapOf("Accept" to "application/json"),
            buildBody(request),
            request.timeoutMs,
        )
        val latency = System.currentTimeMillis() - started
        if (http.statusCode !in 200..299) throw httpError(http)
        val response = try {
            val root = JSONObject(http.body)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                ?.trim() ?: ""
            if (text.isEmpty()) throw AiException.ParseError("$id: empty content")
            val meta = root.optJSONObject("usageMetadata")
            val input = meta?.optInt("promptTokenCount", -1)?.takeIf { it >= 0 }
            val output = meta?.optInt("candidatesTokenCount", -1)?.takeIf { it >= 0 }
            val total = meta?.optInt("totalTokenCount", -1)?.takeIf { it >= 0 }
            AiResponse(
                providerId = id,
                model = model,
                content = text,
                inputTokens = input,
                outputTokens = output,
                totalTokens = total,
                latencyMs = latency,
                finishReason = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optString("finishReason", null),
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
            JSONObject(http.body).optJSONArray("error")
                ?.optJSONObject(0)
                ?.optString("message", "") ?: http.body.take(300)
        } catch (e: Exception) {
            http.body.take(300)
        }
        val body = http.body
        val quota = body.contains("quota", ignoreCase = true) ||
            body.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
        return when {
            http.statusCode == 401 || http.statusCode == 403 -> {
                health.onFailure(ProviderStatus.AUTH_FAILED)
                AiException.AuthFailed("$id: $message")
            }
            http.statusCode == 429 -> {
                if (quota) {
                    health.onFailure(ProviderStatus.QUOTA_EXCEEDED, markQuota = true)
                    AiException.QuotaExceeded("$id: $message")
                } else {
                    health.onFailure(ProviderStatus.RATE_LIMITED, markRateLimited = true)
                    AiException.RateLimited("$id: $message")
                }
            }
            http.statusCode in 500..599 -> {
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
