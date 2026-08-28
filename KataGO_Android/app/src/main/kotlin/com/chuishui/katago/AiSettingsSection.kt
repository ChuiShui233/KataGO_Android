package com.chuishui.katago

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.Slider
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text

/**
 * AI settings block embedded at the bottom of the settings screen: automatic
 * analysis policy, preferred provider, free/offline modes, and the list of
 * providers with their live status.
 */
@Composable
fun AiSettingsSection(
    state: AiSettingsUiState,
    onGlobalChange: (AiGlobalSettings) -> Unit,
    configOf: (String) -> AiProviderConfig,
    onSaveProvider: (AiProviderConfig) -> Unit,
    onTestProvider: (String) -> Unit,
    onTestDraft: (AiProviderConfig) -> Unit,
) {
    val global = state.global
    var editingProvider by remember { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.ai_section_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.ai_section_subtitle),
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.ai_auto_analysis), fontWeight = FontWeight.SemiBold)
    val modeNames = listOf(
        stringResource(R.string.ai_auto_mode_off),
        stringResource(R.string.ai_auto_mode_major),
        stringResource(R.string.ai_auto_mode_important),
        stringResource(R.string.ai_auto_mode_active),
    )
    ComboBox(
        items = modeNames,
        selected = global.autoAnalysisMode.ordinal,
        onSelectionChange = { index, _ ->
            onGlobalChange(
                global.copy(
                    autoAnalysisMode = AiGlobalSettings.AutoAnalysisMode.values()
                        .getOrElse(index) { AiGlobalSettings.AutoAnalysisMode.MAJOR_MISTAKES }
                )
            )
        },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(
            R.string.ai_auto_threshold,
            String.format("%.0f", global.autoThresholdLow),
            String.format("%.0f", global.autoThresholdHigh),
        ),
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.ai_preferred_provider), fontWeight = FontWeight.SemiBold)
    val available = state.providers.filter { it.configured }
    val prefItems = listOf(stringResource(R.string.ai_preferred_auto)) + available.map { it.name }
    val prefIndex = available.indexOfFirst { it.id == global.preferredProviderId }.let { if (it < 0) 0 else it + 1 }
    ComboBox(
        items = prefItems,
        selected = prefIndex,
        onSelectionChange = { index, _ ->
            val id = if (index <= 0) "auto" else available[index - 1].id
            onGlobalChange(global.copy(preferredProviderId = id))
        },
    )
    Spacer(Modifier.height(8.dp))

    Switcher(
        checked = global.allowFallback,
        onCheckStateChange = { onGlobalChange(global.copy(allowFallback = it)) },
        text = stringResource(R.string.ai_allow_fallback),
    )
    Spacer(Modifier.height(4.dp))
    Switcher(
        checked = global.offlineOnly,
        onCheckStateChange = { onGlobalChange(global.copy(offlineOnly = it)) },
        text = stringResource(R.string.ai_offline_only),
    )
    Spacer(Modifier.height(4.dp))
    Switcher(
        checked = global.showCommentary,
        onCheckStateChange = { onGlobalChange(global.copy(showCommentary = it)) },
        text = stringResource(R.string.ai_show_commentary),
    )
    Spacer(Modifier.height(4.dp))
    Switcher(
        checked = global.disableReasoning,
        onCheckStateChange = { onGlobalChange(global.copy(disableReasoning = it)) },
        text = stringResource(R.string.ai_disable_reasoning),
    )
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.ai_disable_reasoning_hint), fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.ai_provider_list), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    state.providers.forEach { provider ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontWeight = FontWeight.SemiBold)
                Text(provider.statusLabel, fontSize = 12.sp)
            }
            SubtleButton(onClick = { onTestProvider(provider.id) }, disabled = !provider.configured) {
                Text(stringResource(R.string.test))
            }
            SubtleButton(onClick = { editingProvider = provider.id }) { Text(stringResource(R.string.config)) }
        }
    }

    AiProviderConfigDialog(
        providerId = editingProvider,
        initial = editingProvider?.let { configOf(it) } ?: AiProviderConfig(id = editingProvider ?: ""),
        onDismiss = { editingProvider = null },
        onSave = { cfg ->
            onSaveProvider(cfg)
            editingProvider = null
        },
        onTest = { cfg ->
            onTestDraft(cfg)
        },
    )
    Spacer(Modifier.height(8.dp))
}