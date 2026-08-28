package com.chuishui.katago.ai.router

import com.chuishui.katago.ai.config.AiConfigSource
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import com.chuishui.katago.ai.log.AiLogger
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.AiResponse
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.ProviderStatus
import com.chuishui.katago.ai.registry.AiProviderRegistry
import com.chuishui.katago.ai.usage.AiUsageManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decides "which AI should answer this request", following [AiRoutePolicy],
 * per-provider config and health. Handles automatic failover unless the user
 * pinned a single provider, and free-only routing.
 */
class AiRouter(
    private val registry: AiProviderRegistry,
    private val configSource: AiConfigSource,
    private val usageManager: AiUsageManager,
    private val logger: AiLogger,
) {

    private val rotateIndex = AtomicInteger(0)

    data class RouteOptions(
        val policy: AiRoutePolicy = AiRoutePolicy.AUTO,
        val preferredProviderId: String? = null,
        val allowFallback: Boolean = true,
        /** When true no provider is called at all. */
        val offlineOnly: Boolean = false,
    )

    /**
     * Routes the request and returns the first successful response.
     *
     * @throws AiException if every candidate failed (last failure propagated)
     *         or AiException.NotConfigured when no candidate is usable.
     */
    suspend fun chat(request: AiRequest, options: RouteOptions = RouteOptions()): AiResponse {
        if (options.offlineOnly) {
            throw AiException.NotConfigured("offline mode: AI providers disabled")
        }
        val globalSettings = configSource.globalSettings()
        val candidates = resolveCandidates(options, globalSettings)
        if (candidates.isEmpty()) {
            throw AiException.NotConfigured("no usable AI provider")
        }
        val ordered = order(candidates, options.policy, options.preferredProviderId)

        var lastError: AiException = AiException.NotConfigured("all providers failed")
        val enforcedSingle = options.policy == AiRoutePolicy.MANUAL ||
            (options.preferredProviderId != null &&
                (!options.allowFallback || !globalSettings.allowFallback))
        val attempts = if (!enforcedSingle) ordered else ordered.take(1)

        for (candidate in attempts) {
            val provider = candidate.provider
            val config = candidate.config
            val started = System.currentTimeMillis()
            try {
                val response = provider.chat(request.copy(model = config.model))
                usageManager.recordResponse(
                    providerId = provider.id,
                    model = config.model,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    totalTokens = response.totalTokens,
                    latencyMs = response.latencyMs,
                    success = true,
                    cost = null,
                )
                logger.log(
                    "chat",
                    "provider=${provider.id} model=${config.model} " +
                        "tokens=${response.totalTokens ?: "?"} " +
                        "latency=${response.latencyMs}ms status=SUCCESS",
                )
                return response
            } catch (e: AiException) {
                lastError = e
                usageManager.recordResponse(
                    providerId = provider.id,
                    model = config.model,
                    inputTokens = null,
                    outputTokens = null,
                    totalTokens = null,
                    latencyMs = System.currentTimeMillis() - started,
                    success = false,
                    cost = 0.0,
                )
                logger.log(
                    "chat",
                    "provider=${provider.id} model=${config.model} status=FAILED error=${errorClass(e)}",
                )
                if (e is AiException.NotConfigured) break
            } catch (e: Exception) {
                lastError = AiException.ServerError("${provider.id}: ${e.message}")
                logger.log("chat", "provider=${provider.id} status=FAILED error=${e.message}")
            }
        }
        throw lastError
    }

    /** Eligible providers for the current settings (enabled, keyed, healthy, within limits). */
    private fun resolveCandidates(
        options: RouteOptions,
        globalSettings: AiGlobalSettings,
    ): List<RankedProvider> {
        val all = registry.getAll()
        return all.mapNotNull { provider ->
            val config = configSource.config(provider.id)
            if (!config.enabled) return@mapNotNull null
            if (config.apiKey.isBlank() && !configSource.hasKey(provider.id)) return@mapNotNull null
            if (provider.health.status == ProviderStatus.AUTH_FAILED) return@mapNotNull null
            if (provider.health.rateLimited || provider.health.quotaExceeded) return@mapNotNull null
            if (provider.health.consecutiveFailures >= 5 &&
                provider.health.status != ProviderStatus.AVAILABLE
            ) return@mapNotNull null
            RankedProvider(provider, config)
        }
    }

    private fun order(
        candidates: List<RankedProvider>,
        policy: AiRoutePolicy,
        preferredProviderId: String?,
    ): List<RankedProvider> {
        if (preferredProviderId != null && candidates.any { it.provider.id == preferredProviderId }) {
            val preferred = candidates.filter { it.provider.id == preferredProviderId }
            val rest = candidates.filter { it.provider.id != preferredProviderId }
            return when (policy) {
                AiRoutePolicy.MANUAL -> preferred + rest
                else -> preferred + sort(rest, policy)
            }
        }
        if (policy == AiRoutePolicy.MANUAL) {
            return listOfNotNull(candidates.firstOrNull())
        }
        return sort(candidates, policy)
    }

    private fun sort(candidates: List<RankedProvider>, policy: AiRoutePolicy): List<RankedProvider> {
        return when (policy) {
            AiRoutePolicy.CHEAPEST -> candidates.sortedBy { cheapScore(it) }
            AiRoutePolicy.FASTEST -> candidates.sortedBy {
                it.provider.health.latencyMs ?: Long.MAX_VALUE
            }
            AiRoutePolicy.ROUND_ROBIN -> {
                val start = rotateIndex.getAndIncrement().toInt() % candidates.size
                candidates.drop(start) + candidates.take(start)
            }
            else -> candidates.sortedByDescending { it.config.priority }
        }
    }

    private fun cheapScore(c: RankedProvider): Double {
        val cost = com.chuishui.katago.ai.usage.AiCostTable.estimate(
            c.config.model,
            inputTokens = 300,
            outputTokens = 100,
        )
        return cost
    }

    private fun errorClass(e: AiException): String = when (e) {
        is AiException.AuthFailed -> "AUTH_FAILED"
        is AiException.RateLimited -> "RATE_LIMITED"
        is AiException.QuotaExceeded -> "QUOTA_EXCEEDED"
        is AiException.NetworkError -> "NETWORK_ERROR"
        is AiException.Timeout -> "TIMEOUT"
        is AiException.NotConfigured -> "NOT_CONFIGURED"
        is AiException.ParseError -> "PARSE_ERROR"
        is AiException.ServerError -> "SERVER_ERROR"
    }

    private data class RankedProvider(
        val provider: AiProvider,
        val config: AiProviderConfig,
    )
}