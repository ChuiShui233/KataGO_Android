package com.chuishui.katago.ai.config

/**
 * Abstraction over where provider configs come from, so the router and
 * coach can be unit tested without Android. The real implementation is
 * [AiConfigStore] (SharedPreferences + encrypted keys).
 */
interface AiConfigSource {

    fun config(providerId: String): AiProviderConfig

    fun saveConfig(config: AiProviderConfig)

    /** Whether a usable API key exists for [providerId] (possibly encrypted). */
    fun hasKey(providerId: String): Boolean

    /** Global (non-provider) settings; defaults when not stored anywhere. */
    fun globalSettings(): AiGlobalSettings = AiGlobalSettings()
}
