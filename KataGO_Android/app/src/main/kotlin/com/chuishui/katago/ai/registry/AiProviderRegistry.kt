package com.chuishui.katago.ai.registry

import com.chuishui.katago.ai.provider.AiProvider

/**
 * Registry of every [AiProvider], keyed by provider id. Business code must
 * look providers up here instead of hard-coding vendor names.
 *
 * Insertion order is preserved so the router's candidate ordering (and its
 * stable priority tie-break) is deterministic across runs.
 */
class AiProviderRegistry {

    private val providers = LinkedHashMap<String, AiProvider>()

    @Synchronized
    fun register(provider: AiProvider) {
        providers[provider.id] = provider
    }

    @Synchronized
    fun unregister(providerId: String) {
        providers.remove(providerId)
    }

    @Synchronized
    fun get(providerId: String): AiProvider? = providers[providerId]

    /** All registered providers in insertion order. */
    @Synchronized
    fun getAll(): List<AiProvider> = providers.values.toList()

    suspend fun isAvailable(providerId: String): Boolean =
        get(providerId)?.isAvailable() == true

    @Synchronized
    fun clear() = providers.clear()
}