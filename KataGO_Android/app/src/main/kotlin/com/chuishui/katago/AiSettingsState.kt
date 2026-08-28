package com.chuishui.katago

import com.chuishui.katago.ai.config.AiGlobalSettings

/** One provider row as the settings UI renders it. */
data class AiProviderUi(
    val id: String,
    val name: String,
    val configured: Boolean,
    /** Localized status label, e.g. "可用" / "未配置" / "额度不足". */
    val statusLabel: String,
)

/** Immutable snapshot of the whole AI settings page. */
data class AiSettingsUiState(
    val providers: List<AiProviderUi> = emptyList(),
    val global: AiGlobalSettings = AiGlobalSettings(),
)