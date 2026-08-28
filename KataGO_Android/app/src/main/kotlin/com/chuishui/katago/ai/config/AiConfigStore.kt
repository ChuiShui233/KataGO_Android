package com.chuishui.katago.ai.config

import android.content.Context
import android.content.SharedPreferences
import com.chuishui.katago.ai.security.AiApiKeyStore

/**
 * SharedPreferences-backed [AiConfigSource]. API keys are encrypted with the
 * Android Keystore before being written (see [AiApiKeyStore]).
 *
 * Provider fields are stored under `ai.provider.<id>.<field>`; global AI
 * settings under `ai.<field>`.
 */
class AiConfigStore(context: Context) : AiConfigSource {

private val prefs: SharedPreferences =
        context.getSharedPreferences("katago_ai", Context.MODE_PRIVATE)

    override fun config(providerId: String): AiProviderConfig {
        val p = "ai.provider.$providerId."
        fun getStr(key: String): String = prefs.getString(p + key, "") ?: ""
        fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(p + key, def)
        fun getInt(key: String, def: Int): Int = prefs.getInt(p + key, def)
        fun getDouble(key: String, def: Double): Double =
            java.lang.Double.longBitsToDouble(prefs.getLong(p + key, java.lang.Double.doubleToLongBits(def)))
        return AiProviderConfig(
            id = providerId,
            enabled = getBool("enabled", false),
            apiKey = decryptKey(providerId) ?: "",
            baseUrl = getStr("baseUrl"),
            model = getStr("model"),
            maxTokens = getInt("maxTokens", 256),
            temperature = getDouble("temperature", 0.2),
            priority = getInt("priority", 100),
            enabledForAutoAnalysis = getBool("autoAnalysis", true),
            enabledForUserChat = getBool("userChat", true),
        )
    }

    override fun saveConfig(config: AiProviderConfig) {
        val p = "ai.provider.${config.id}."
        prefs.edit()
            .putBoolean(p + "enabled", config.enabled)
            .putString(p + "baseUrl", config.baseUrl)
            .putString(p + "model", config.model)
            .putInt(p + "maxTokens", config.maxTokens)
            .putLong(p + "temperature", java.lang.Double.doubleToLongBits(config.temperature))
            .putInt(p + "priority", config.priority)
            .putBoolean(p + "autoAnalysis", config.enabledForAutoAnalysis)
            .putBoolean(p + "userChat", config.enabledForUserChat)
            .apply()
        if (config.apiKey.isNotBlank()) encryptKey(config.id, config.apiKey)
    }

    override fun hasKey(providerId: String): Boolean = decryptKey(providerId)?.isNotBlank() == true

    // ---- global settings --------------------------------------------------

    override fun globalSettings(): AiGlobalSettings {
        val mode = AiGlobalSettings.AutoAnalysisMode.values()
            .getOrNull(prefs.getInt("ai.autoAnalysisMode", 1)) ?: AiGlobalSettings.AutoAnalysisMode.MAJOR_MISTAKES
        return AiGlobalSettings(
            autoAnalysisMode = mode,
            preferredProviderId = prefs.getString("ai.preferredProvider", "auto") ?: "auto",
            allowFallback = prefs.getBoolean("ai.allowFallback", true),
            offlineOnly = prefs.getBoolean("ai.offlineOnly", false),
            autoThresholdLow = prefs.getFloat("ai.thresholdLow", 5f),
            autoThresholdHigh = prefs.getFloat("ai.thresholdHigh", 10f),
            showCommentary = prefs.getBoolean("ai.showCommentary", true),
            showAiPlan = prefs.getBoolean("ai.showAiPlan", false),
            disableReasoning = prefs.getBoolean("ai.disableReasoning", false),
        )
    }

    fun saveGlobalSettings(settings: AiGlobalSettings) {
        prefs.edit()
            .putInt("ai.autoAnalysisMode", settings.autoAnalysisMode.ordinal)
            .putString("ai.preferredProvider", settings.preferredProviderId)
            .putBoolean("ai.allowFallback", settings.allowFallback)
            .putBoolean("ai.offlineOnly", settings.offlineOnly)
            .putFloat("ai.thresholdLow", settings.autoThresholdLow)
            .putFloat("ai.thresholdHigh", settings.autoThresholdHigh)
            .putBoolean("ai.showCommentary", settings.showCommentary)
            .putBoolean("ai.showAiPlan", settings.showAiPlan)
            .putBoolean("ai.disableReasoning", settings.disableReasoning)
            .apply()
    }

    // ---- key crypto -------------------------------------------------------

    private fun encryptKey(providerId: String, key: String) {
        runCatching { AiApiKeyStore.encrypt(key) }
            .onSuccess { prefs.edit().putString("ai.provider.$providerId.apiKey", it).apply() }
            .onFailure { /* keystore unavailable: key stays unpersisted, provider shows NOT_CONFIGURED */ }
    }

    private fun decryptKey(providerId: String): String? =
        prefs.getString("ai.provider.$providerId.apiKey", null)?.let { encoded ->
            runCatching { AiApiKeyStore.decrypt(encoded) }.getOrNull()
        }
}
