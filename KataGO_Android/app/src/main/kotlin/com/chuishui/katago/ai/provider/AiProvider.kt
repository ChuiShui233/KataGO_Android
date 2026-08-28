package com.chuishui.katago.ai.provider

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse

/**
 * Unified vendor abstraction.
 *
 * Upper layers only ever see [AiRequest] and [AiResponse]. HTTP,
 * Authorization headers, API URLs and vendor JSON schemas are encapsulated
 * inside each implementation.
 */
interface AiProvider {

    val id: String

    val name: String

    val capabilities: AiCapabilities

    /** Live health snapshot used by the router for failover decisions. */
    val health: ProviderHealth

    /** Calls the model and maps every vendor quirk into [AiResponse]. */
    suspend fun chat(request: AiRequest): AiResponse

    /** True when the provider is configured (has a key) and reachable. */
    suspend fun isAvailable(): Boolean

    /**
     * Lightweight connectivity check used by the "test connection" UI.
     *
     * A successful HTTP round-trip (2xx) counts as success even if the model
     * returns an empty or odd body — for a connection test only the
     * URL/key/model round trip matters, not the reply content.
     */
    suspend fun testConnection(): String {
        return try {
            chat(AiRequest(userPrompt = "ping", maxTokens = 4)).model ?: id
        } catch (e: AiException.ParseError) {
            id // 2xx round-trip succeeded but the reply couldn't be parsed
        }
    }
}
