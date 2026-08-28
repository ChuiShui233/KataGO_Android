package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.model.ResponseFormat
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.provider.AiHttpClient
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.ProviderHealth
import com.chuishui.katago.ai.provider.ProviderStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * One HTTP implementation shared by every vendor that exposes a
 * `POST {baseUrl}/chat/completions` endpoint (DeepSeek, Groq, OpenRouter,
 * Qwen, Moonshot/Kimi, OpenAI, ...). New compatible vendors only need a new
 * [OpenAiCompatibleConfig] — no HTTP code is duplicated.
 */
open class OpenAiCompatibleProvider(
    private val config: OpenAiCompatibleConfig,
    override val capabilities: AiCapabilities = AiCapabilities(),
    private val httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
) : AiProvider {

    override val id: String get() = config.providerId
    override val name: String get() = config.name
    override val health: ProviderHealth = ProviderHealth()

    override suspend fun isAvailable(): Boolean =
        config.apiKey.isNotBlank() && (health.status == ProviderStatus.AVAILABLE ||
            health.consecutiveFailures < 3)

    protected open val endpointPath: String = "/chat/completions"

    /** Full request URL; never double-append the endpoint path. */
    protected fun endpointUrl(): String {
        val base = config.baseUrl.trimEnd('/')
        return if (base.endsWith(endpointPath, ignoreCase = true)) base else base + endpointPath
    }

    override suspend fun testConnection(): String {
        if (config.apiKey.isBlank()) throw AiException.NotConfigured("$id: no api key configured")
        val model = config.defaultModel
        val http = httpClient.post(
            endpointUrl(),
            buildMap {
                put("Authorization", "Bearer ${config.apiKey}")
                put("Accept", "application/json")
                putAll(config.extraHeaders)
            },
            buildBody(AiRequest(userPrompt = "ping", maxTokens = 4), model),
            20_000,
        )
        if (http.statusCode !in 200..299) throw httpError(http)
        return model
    }

    protected open fun buildBody(request: AiRequest, model: String): String {
        val root = JSONObject()
        root.put("model", model)
        root.put(if (config.useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", request.maxTokens)
        if (config.supportsTemperature) root.put("temperature", request.temperature)
        request.reasoningEffort?.let { root.put("reasoning_effort", it) }

        val messages = JSONArray()
        if (request.systemPrompt.isNotBlank() && capabilities.supportsSystemPrompt) {
            messages.put(JSONObject().put("role", "system").put("content", request.systemPrompt))
        }
        request.history.forEach { turn ->
            if (turn.content.isNotBlank()) {
                messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
        }
        messages.put(JSONObject().put("role", "user").put("content", request.userPrompt))
        root.put("messages", messages)

        if (request.responseFormat == ResponseFormat.JSON && capabilities.supportsJson) {
            root.put("response_format", JSONObject().put("type", "json_object"))
        }
        return root.toString()
    }

    override suspend fun chat(request: AiRequest): AiResponse {
        if (config.apiKey.isBlank()) throw AiException.NotConfigured("$id: no api key configured")
        health.onConfigured()
        val model = request.model ?: config.defaultModel
        val started = System.currentTimeMillis()
        val url = endpointUrl()
        val headers = buildMap {
            put("Authorization", "Bearer ${config.apiKey}")
            put("Accept", "application/json")
            putAll(config.extraHeaders)
        }
        val http = httpClient.post(url, headers, buildBody(request, model), request.timeoutMs)
        val latency = System.currentTimeMillis() - started
        val response = mapResponse(http, model, latency)
        health.onSuccess(latency)
        return response
    }

    protected fun mapResponse(http: com.chuishui.katago.ai.provider.AiHttpResponse, model: String, latency: Long): AiResponse {
        val code = http.statusCode
        if (code !in 200..299) {
            throw httpError(http)
        }
        return try {
            val root = JSONObject(http.body)
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
            val finish = choice?.optString("finish_reason", null)
            var content = choice?.optJSONObject("message")?.optString("content", "")?.trim() ?: ""
            // Vendors sometimes return content as an array of text parts
            // instead of a single string (works with our chat-completions
            // mapping as well).
            if (content.isEmpty()) {
                content = choice?.optJSONObject("message")?.optJSONArray("content")
                    ?.let { arr ->
                        buildString {
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { append(it.optString("text", "")) }
                            }
                        }.trim()
                    } ?: ""
            }
            if (content.isEmpty()) {
                // Reasoning models (e.g. openai/gpt-oss on Groq) can exhaust
                // maxTokens inside the hidden reasoning field and never emit a
                // final content. Fall back to that text so tolerant parsers can
                // still extract what they need instead of failing outright.
                content = choice?.optJSONObject("message")?.optString("reasoning_content", "")?.trim().orEmpty()
                if (content.isEmpty()) {
                    content = choice?.optJSONObject("message")?.optString("reasoning", "")?.trim().orEmpty()
                }
            }
            if (content.isEmpty()) {
                val snippet = http.body.take(300).trim()
                throw AiException.ParseError(
                    "$id: empty content" +
                        (snippet.takeIf { it.isNotEmpty() }?.let { " (body=$it)" } ?: "") +
                        (finish?.let { ", finish_reason=$it" } ?: "")
                )
            }
            val usage = root.optJSONObject("usage")
            val input = usage?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }
            val output = usage?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }
            val total = usage?.optInt("total_tokens", -1)?.takeIf { it >= 0 }
            AiResponse(
                providerId = id,
                model = model,
                content = content,
                inputTokens = input,
                outputTokens = output,
                totalTokens = total,
                latencyMs = latency,
                finishReason = choice?.optString("finish_reason", null),
            )
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.ParseError("$id: bad response: ${e.message}", e)
        }
    }

    protected open fun httpError(http: com.chuishui.katago.ai.provider.AiHttpResponse): AiException {
        val message = try {
            JSONObject(http.body).optJSONArray("error")?.optJSONObject(0)?.optString("message", "")
                ?: JSONObject(http.body).optJSONObject("error")?.optString("message", "")
                ?: http.body.take(300)
        } catch (e: Exception) {
            http.body.take(300)
        }
        return when (http.statusCode) {
            401, 403 -> {
                health.onFailure(ProviderStatus.AUTH_FAILED)
                AiException.AuthFailed("$id: ${message.ifBlank { "auth failed" }}")
            }
            429 -> {
                val body = http.body
                val quota = body.contains("quota", ignoreCase = true) ||
                    body.contains("insufficient_quota", ignoreCase = true)
                if (quota) {
                    health.onFailure(ProviderStatus.QUOTA_EXCEEDED, markQuota = true)
                    AiException.QuotaExceeded("$id: $message")
                } else {
                    health.onFailure(ProviderStatus.RATE_LIMITED, markRateLimited = true)
                    AiException.RateLimited("$id: $message")
                }
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
