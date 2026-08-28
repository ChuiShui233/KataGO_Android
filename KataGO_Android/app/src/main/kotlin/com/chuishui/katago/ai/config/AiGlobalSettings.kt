package com.chuishui.katago.ai.config

/** Global AI behaviour settings (not per-provider). */
data class AiGlobalSettings(
    val autoAnalysisMode: AutoAnalysisMode = AutoAnalysisMode.MAJOR_MISTAKES,
    val preferredProviderId: String = "auto",
    val allowFallback: Boolean = true,
    val offlineOnly: Boolean = false,
    /** Winrate-drop thresholds (percentage points) used by the mistake detector. */
    val autoThresholdLow: Float = 5f,
    val autoThresholdHigh: Float = 10f,
    val showCommentary: Boolean = true,
    /** While the engine is thinking, draw a marker at its current planned move. */
    val showAiPlan: Boolean = false,
    /** When true, never ask the model to reason (no reasoning_effort sent). */
    val disableReasoning: Boolean = false,
) {
    enum class AutoAnalysisMode(val label: String) {
        OFF("off"),
        MAJOR_MISTAKES("major"),
        IMPORTANT_MISTAKES("important"),
        AGGRESSIVE("aggressive"),
    }
}
