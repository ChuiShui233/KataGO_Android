package com.chuishui.katago

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuishui.katago.ai.config.AiProviderConfig
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

/**
 * Edit dialog for a single provider: API key, base URL, model, limits,
 * priorities and the auto/user chat toggles.
 */
@Composable
fun AiProviderConfigDialog(
    providerId: String?,
    initial: AiProviderConfig,
    onDismiss: () -> Unit,
    onSave: (AiProviderConfig) -> Unit,
    onTest: (AiProviderConfig) -> Unit,
) {
    if (providerId == null) return
    var apiKey by remember(providerId) { mutableStateOf(initial.apiKey) }
    var baseUrl by remember(providerId) { mutableStateOf(initial.baseUrl) }
    var model by remember(providerId) { mutableStateOf(initial.model) }
    var maxTokens by remember(providerId) { mutableIntStateOf(initial.maxTokens) }
    var temperature by remember(providerId) { mutableDoubleStateOf(initial.temperature) }
    var priority by remember(providerId) { mutableIntStateOf(initial.priority) }
    var enabled by remember(providerId) { mutableStateOf(initial.enabled) }
    var autoAnalysis by remember(providerId) { mutableStateOf(initial.enabledForAutoAnalysis) }
    var userChat by remember(providerId) { mutableStateOf(initial.enabledForUserChat) }

    FluentModalDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.ai_config_title),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Switcher(
                checked = enabled,
                onCheckStateChange = { enabled = it },
                text = stringResource(R.string.ai_enable_provider),
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_apikey_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_apikey_placeholder)) },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_model_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_model_placeholder)) },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_baseurl_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_baseurl_placeholder)) },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_max_tokens_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = if (maxTokens == 0) "" else "$maxTokens",
                onValueChange = { maxTokens = it.toIntOrNull() ?: 0 },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_max_tokens_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_temperature_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = String.format("%.1f", temperature),
                onValueChange = { temperature = it.toDoubleOrNull() ?: 0.2 },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_temperature_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_priority_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = "$priority",
                onValueChange = { priority = it.toIntOrNull() ?: 100 },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_priority_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(8.dp))
            Switcher(
                checked = autoAnalysis,
                onCheckStateChange = { autoAnalysis = it },
                text = stringResource(R.string.ai_allow_auto_analysis),
            )
            Spacer(Modifier.height(4.dp))
            Switcher(
                checked = userChat,
                onCheckStateChange = { userChat = it },
                text = stringResource(R.string.ai_allow_user_chat),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SubtleButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                SubtleButton(onClick = {
                    onTest(
                        AiProviderConfig(
                            id = initial.id,
                            enabled = enabled,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            maxTokens = maxTokens.takeIf { it > 0 } ?: 256,
                            temperature = temperature.coerceIn(0.0, 2.0),
                            priority = priority,
                            enabledForAutoAnalysis = autoAnalysis,
                            enabledForUserChat = userChat,
                        )
                    )
                }) { Text(stringResource(R.string.ai_test_connection)) }
                AccentButton(onClick = {
                    onSave(
                        AiProviderConfig(
                            id = initial.id,
                            enabled = enabled,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            maxTokens = maxTokens.takeIf { it > 0 } ?: 256,
                            temperature = temperature.coerceIn(0.0, 2.0),
                            priority = priority,
                            enabledForAutoAnalysis = autoAnalysis,
                            enabledForUserChat = userChat,
                        )
                    )
                }) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ai_key_security_note),
                fontSize = 12.sp,
            )
        }
    }
}