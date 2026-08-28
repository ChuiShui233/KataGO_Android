package com.chuishui.katago

import com.chuishui.katago.ai.config.AiConfigSource
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.ProviderHealth

/** Configurable fake provider for router/registry tests. */
class FakeProvider(
    override val id: String,
    override val name: String = id,
    override val capabilities: AiCapabilities = AiCapabilities(),
    private val failure: Throwable? = null,
    private val responseContent: String = "ok",
) : AiProvider {
    override val health = ProviderHealth()
    val calls = mutableListOf<AiRequest>()

    override suspend fun chat(request: AiRequest): AiResponse {
        calls += request
        failure?.let { if (it is Exception) throw it else throw RuntimeException(it.message) }
        return AiResponse(
            providerId = id,
            model = request.model ?: "m",
            content = responseContent,
            inputTokens = 10,
            outputTokens = 5,
            totalTokens = 15,
            latencyMs = 42,
        )
    }

    override suspend fun isAvailable(): Boolean = true
}

/** In-memory [AiConfigSource] for tests. */
class FakeConfigSource : AiConfigSource {
    val configs = mutableMapOf<String, AiProviderConfig>()
    val keys = mutableSetOf<String>()
    var global = AiGlobalSettings()

    override fun config(providerId: String): AiProviderConfig =
        configs[providerId] ?: AiProviderConfig(id = providerId)

    override fun saveConfig(config: AiProviderConfig) {
        configs[config.id] = config
        if (config.apiKey.isNotBlank()) keys += config.id
    }

    override fun hasKey(providerId: String): Boolean = providerId in keys

    override fun globalSettings(): AiGlobalSettings = global
}