package com.chuishui.katago.ai

import android.content.Context
import com.chuishui.katago.ai.config.AiConfigStore
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import com.chuishui.katago.ai.log.AiLogger
import com.chuishui.katago.ai.log.LogcatAiLogger
import com.chuishui.katago.ai.queue.AiRequestQueue
import com.chuishui.katago.ai.registry.AiProviderRegistry
import com.chuishui.katago.ai.router.AiRouter
import com.chuishui.katago.ai.usage.AiUsageManager
import com.chuishui.katago.goai.GoAiCoach
import com.chuishui.katago.goai.GoAiCoachImpl
import com.chuishui.katago.goai.GoMistakeDetector

/**
 * Manual dependency container: wires the whole AI subsystem together and
 * exposes a single entry point for the Activity / UI.
 */
class AiContainer(context: Context) {

    val configStore: AiConfigStore = AiConfigStore(context.applicationContext)

    val registry: AiProviderRegistry = AiProviderRegistry()

    val usage: AiUsageManager = AiUsageManager()

    val queue: AiRequestQueue = AiRequestQueue()

    val logger: AiLogger = LogcatAiLogger

    val router: AiRouter by lazy {
        AiRouter(registry, configStore, usage, logger)
    }

    val coach: GoAiCoach by lazy {
        GoAiCoachImpl(router, configStore, queue)
    }

    val mistakeDetector: GoMistakeDetector by lazy {
        GoMistakeDetector { globalSettings() }
    }

    /**
     * Rebuilds providers from persisted config. Call after any setting change.
     * Always registers every known provider so the router can decide; health
     * reflects NOT_CONFIGURED when no key is present.
     */
    fun refreshProviders() {
        val oldKeys = registry.getAll().map { it.id }
        val wanted = ProviderFactory.KNOWN_PROVIDER_IDS
        // Unregister removed ids first, then re-create all wanted providers.
        oldKeys.filterNot { it in wanted }.forEach { registry.unregister(it) }
        for (id in wanted) {
            val config = configStore.config(id)
            val provider = ProviderFactory.create(config)
            if (provider != null) {
                registry.register(provider)
                if (!configStore.hasKey(id)) {
                    provider.health.markNotConfigured()
                } else {
                    provider.health.onConfigured()
                }
            }
        }
    }

    fun hasConfiguredProvider(): Boolean =
        ProviderFactory.KNOWN_PROVIDER_IDS.any { configStore.hasKey(it) }

    fun config(providerId: String): AiProviderConfig = configStore.config(providerId)

    fun globalSettings(): AiGlobalSettings = configStore.globalSettings()

    fun saveGlobalSettings(settings: AiGlobalSettings) {
        configStore.saveGlobalSettings(settings)
    }

    fun saveProviderConfig(config: AiProviderConfig) {
        configStore.saveConfig(config)
        refreshProviders()
    }
}