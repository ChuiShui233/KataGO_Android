package com.chuishui.katago

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import io.github.composefluent.FluentTheme
import io.github.composefluent.ProvideTextStyle
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Slider
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.ChevronRight
import io.github.composefluent.icons.filled.Copy
import io.github.composefluent.icons.filled.Document
import io.github.composefluent.icons.filled.Flag
import io.github.composefluent.icons.filled.Flash
import io.github.composefluent.icons.filled.Search
import io.github.composefluent.icons.filled.Settings
import io.github.composefluent.surface.Card
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    enginePreference: Int,
    onEnginePreferenceChange: (Int) -> Unit,
    forceGpu: Boolean,
    onForceGpuChange: (Boolean) -> Unit,
    gpuDisabled: Boolean,
    clvkInstalled: Boolean,
    maxVisits: Int,
    onMaxVisitsChange: (Int) -> Unit,
    maxTimeSec: Float,
    onMaxTimeSecChange: (Float) -> Unit,
    numThreads: Int,
    onNumThreadsChange: (Int) -> Unit,
    analysisPVLen: Int,
    onAnalysisPVLenChange: (Int) -> Unit,
    nnMaxBatchSize: Int,
    onNnMaxBatchSizeChange: (Int) -> Unit,
    allowResignation: Boolean,
    onAllowResignationChange: (Boolean) -> Unit,
    ponderingEnabled: Boolean,
    onPonderingEnabledChange: (Boolean) -> Unit,
    autoCopyTuneFiles: Boolean,
    onAutoCopyTuneFilesChange: (Boolean) -> Unit,
    themePreference: Int,
    onThemePreferenceChange: (Int) -> Unit,
    languagePreference: String,
    onLanguagePreferenceChange: (String) -> Unit,
    languageChangeEnabled: Boolean,
    openClImportStatus: Pair<Boolean, String>?,
    settingsRefreshTick: Int,
    onSettingsRefreshTickChange: () -> Unit,
    onOpenClSetup: () -> Unit,
    recreate: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportConfig: (Context, Uri) -> Unit,
    onImportConfig: (Context, Uri) -> Unit,
    aiSettings: AiSettingsUiState,
    onAiGlobalChange: (com.chuishui.katago.ai.config.AiGlobalSettings) -> Unit,
    aiConfigOf: (String) -> com.chuishui.katago.ai.config.AiProviderConfig,
    onAiSaveProvider: (com.chuishui.katago.ai.config.AiProviderConfig) -> Unit,
    onAiTestProvider: (String) -> Unit,
    onAiTestDraft: (com.chuishui.katago.ai.config.AiProviderConfig) -> Unit,
    personalization: com.chuishui.katago.config.PersonalizationSettings,
    onPersonalizationChange: (com.chuishui.katago.config.PersonalizationSettings) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("katago", Context.MODE_PRIVATE) }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    fun saveBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    var selectedCategory by remember { mutableIntStateOf(-1) }
    var showReinstallConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedCategory != -1) {
        selectedCategory = -1
    }

    Box(Modifier.fillMaxSize()) {
        val transitionDuration = 300
        val forwardSpec = slideInHorizontally(tween(transitionDuration)) { it } + fadeIn(tween(transitionDuration)) togetherWith
                slideOutHorizontally(tween(transitionDuration)) { -it / 3 } + fadeOut(tween(transitionDuration))
        val backSpec = slideInHorizontally(tween(transitionDuration)) { -it / 3 } + fadeIn(tween(transitionDuration)) togetherWith
                slideOutHorizontally(tween(transitionDuration)) { it } + fadeOut(tween(transitionDuration))

        AnimatedContent(
            targetState = selectedCategory,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState > initialState) forwardSpec else backSpec
            },
            label = "settings-nav",
        ) { category ->
            when (category) {
                -1 -> SettingsMainPage(
                    onSelectCategory = { selectedCategory = it },
                )
                0 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_engine),
                    onBack = { selectedCategory = -1 },
                ) {
                    EngineSettingsContent(
                        enginePreference = enginePreference,
                        onEnginePreferenceChange = onEnginePreferenceChange,
                        forceGpu = forceGpu,
                        onForceGpuChange = onForceGpuChange,
                        gpuDisabled = gpuDisabled,
                        clvkInstalled = clvkInstalled,
                        autoCopyTuneFiles = autoCopyTuneFiles,
                        onAutoCopyTuneFilesChange = onAutoCopyTuneFilesChange,
                        saveInt = ::saveInt,
                        saveBool = ::saveBool,
                    )
                }
                1 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_ai),
                    onBack = { selectedCategory = -1 },
                ) {
                    AiSettingsContent(
                        aiSettings = aiSettings,
                        onAiGlobalChange = onAiGlobalChange,
                        aiConfigOf = aiConfigOf,
                        onAiSaveProvider = onAiSaveProvider,
                        onAiTestProvider = onAiTestProvider,
                        onAiTestDraft = onAiTestDraft,
                    )
                }
                2 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_analysis),
                    onBack = { selectedCategory = -1 },
                ) {
                    AnalysisSettingsContent(
                        enginePreference = enginePreference,
                        maxVisits = maxVisits,
                        onMaxVisitsChange = onMaxVisitsChange,
                        maxTimeSec = maxTimeSec,
                        onMaxTimeSecChange = onMaxTimeSecChange,
                        numThreads = numThreads,
                        onNumThreadsChange = onNumThreadsChange,
                        analysisPVLen = analysisPVLen,
                        onAnalysisPVLenChange = onAnalysisPVLenChange,
                        nnMaxBatchSize = nnMaxBatchSize,
                        onNnMaxBatchSizeChange = onNnMaxBatchSizeChange,
                        settingsRefreshTick = settingsRefreshTick,
                        onSettingsRefreshTickChange = onSettingsRefreshTickChange,
                        saveInt = ::saveInt,
                        saveFloat = ::saveFloat,
                    )
                }
                3 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_game),
                    onBack = { selectedCategory = -1 },
                ) {
                    GameSettingsContent(
                        allowResignation = allowResignation,
                        onAllowResignationChange = onAllowResignationChange,
                        ponderingEnabled = ponderingEnabled,
                        onPonderingEnabledChange = onPonderingEnabledChange,
                        showAiPlan = aiSettings.global.showAiPlan,
                        onShowAiPlanChange = { onAiGlobalChange(aiSettings.global.copy(showAiPlan = it)) },
                        saveBool = ::saveBool,
                    )
                }
                4 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_ui),
                    onBack = { selectedCategory = -1 },
                ) {
                    UiSettingsContent(
                        themePreference = themePreference,
                        onThemePreferenceChange = onThemePreferenceChange,
                        languagePreference = languagePreference,
                        onLanguagePreferenceChange = onLanguagePreferenceChange,
                        languageChangeEnabled = languageChangeEnabled,
                        recreate = recreate,
                        saveInt = ::saveInt,
                        saveString = ::saveString,
                        personalization = personalization,
                        onPersonalizationChange = onPersonalizationChange,
                    )
                }
                5 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_backup),
                    onBack = { selectedCategory = -1 },
                ) {
                    BackupSettingsContent(
                        onRestoreDefaults = onRestoreDefaults,
                        onExportConfig = onExportConfig,
                        onImportConfig = onImportConfig,
                        context = context,
                        onShowRestoreConfirm = { showRestoreConfirm = true },
                    )
                }
                6 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_opencl),
                    onBack = { selectedCategory = -1 },
                ) {
                    OpenClSettingsContent(
                        clvkInstalled = clvkInstalled,
                        openClImportStatus = openClImportStatus,
                        onOpenClSetup = onOpenClSetup,
                        onShowReinstallConfirm = { showReinstallConfirm = true },
                    )
                }
                7 -> SettingsSubPage(
                    title = stringResource(R.string.settings_cat_about),
                    onBack = { selectedCategory = -1 },
                ) {
                    Text(stringResource(R.string.about_text))
                }
            }
        }

        FluentDialog(
            visible = showReinstallConfirm,
            onDismiss = { showReinstallConfirm = false },
            title = stringResource(R.string.opencl_reinstall_title),
        ) {
            Text(stringResource(R.string.opencl_reinstall_message))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SubtleButton(onClick = { showReinstallConfirm = false }) {
                    Text(stringResource(R.string.opencl_reinstall_exit))
                }
                AccentButton(onClick = {
                    showReinstallConfirm = false
                    onOpenClSetup()
                }) {
                    Text(stringResource(R.string.opencl_reinstall_continue))
                }
            }
        }

        FluentDialog(
            visible = showRestoreConfirm,
            onDismiss = { showRestoreConfirm = false },
            title = stringResource(R.string.restore_defaults_title),
        ) {
            Text(stringResource(R.string.restore_defaults_confirm))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SubtleButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) }
                AccentButton(onClick = {
                    showRestoreConfirm = false
                    onRestoreDefaults()
                }) { Text(stringResource(R.string.restore_defaults_label)) }
            }
        }
    }
}

private enum class SettingCategory(
    val titleRes: Int,
    val hintRes: Int,
    val icon: @Composable () -> Unit,
) {
    ENGINE(R.string.settings_cat_engine, R.string.settings_cat_engine_hint, { Icon(Icons.Filled.Flash, null) }),
    AI(R.string.settings_cat_ai, R.string.settings_cat_ai_hint, { Icon(Icons.Filled.Search, null) }),
    ANALYSIS(R.string.settings_cat_analysis, R.string.settings_cat_analysis_hint, { Icon(Icons.Filled.Document, null) }),
    GAME(R.string.settings_cat_game, R.string.settings_cat_game_hint, { Icon(Icons.Filled.Flag, null) }),
    UI(R.string.settings_cat_ui, R.string.settings_cat_ui_hint, { Icon(Icons.Filled.Settings, null) }),
    BACKUP(R.string.settings_cat_backup, R.string.settings_cat_backup_hint, { Icon(Icons.Filled.Copy, null) }),
    OPENCL(R.string.settings_cat_opencl, R.string.settings_cat_opencl_hint, { Icon(Icons.Filled.Flash, null) }),
    ABOUT(R.string.settings_cat_about, R.string.settings_cat_about_hint, { Icon(Icons.Filled.Document, null) }),
}

@Composable
private fun SettingsMainPage(onSelectCategory: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .overscroll(rememberOverscrollEffect())
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        SettingCategory.entries.forEachIndexed { index, category ->
            CategoryCard(
                title = stringResource(category.titleRes),
                hint = stringResource(category.hintRes),
                icon = category.icon,
                onClick = { onSelectCategory(index) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    hint: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .heightIn(min = 60.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .widthIn(min = 48.dp)
                    .defaultMinSize(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                ProvideTextStyle(FluentTheme.typography.caption.copy(FluentTheme.colors.text.text.secondary)) {
                    Text(hint, fontSize = 12.sp)
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(16.dp),
                tint = FluentTheme.colors.text.text.secondary,
            )
        }
    }
}

@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .overscroll(rememberOverscrollEffect())
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun EngineSettingsContent(
    enginePreference: Int,
    onEnginePreferenceChange: (Int) -> Unit,
    forceGpu: Boolean,
    onForceGpuChange: (Boolean) -> Unit,
    gpuDisabled: Boolean,
    clvkInstalled: Boolean,
    autoCopyTuneFiles: Boolean,
    onAutoCopyTuneFilesChange: (Boolean) -> Unit,
    saveInt: (String, Int) -> Unit,
    saveBool: (String, Boolean) -> Unit,
) {
    Text(stringResource(R.string.engine_label), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    val engineNames = listOf(
        stringResource(R.string.engine_auto),
        stringResource(R.string.engine_gpu),
        stringResource(R.string.engine_cpu),
    )
    ComboBox(
        items = engineNames,
        selected = enginePreference,
        disabled = forceGpu,
        onSelectionChange = { index, _ ->
            if (index == 1 && (!clvkInstalled || (gpuDisabled && !forceGpu))) return@ComboBox
            onEnginePreferenceChange(index)
            saveInt("engine", index)
        },
    )
    Spacer(Modifier.height(4.dp))
    if (!clvkInstalled && !forceGpu) {
        Text(
            stringResource(R.string.engine_hint_gpu_requires_clvk),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
    }
    if (gpuDisabled && !forceGpu) {
        Text(
            stringResource(R.string.engine_hint_gpu_disabled),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
    }
    if (enginePreference != 1) {
        Text(
            stringResource(
                when (enginePreference) {
                    2 -> R.string.engine_hint_cpu
                    else -> R.string.engine_hint_auto
                }
            ),
            fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    SwitchSettingCard(
        heading = { Text(stringResource(R.string.force_gpu_label)) },
        caption = { Text(stringResource(R.string.force_gpu_hint), fontSize = 12.sp) },
        icon = { Icon(Icons.Filled.Flash, null) },
        trailing = {
            Box(
                Modifier
                    .width(64.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Switcher(
                    checked = forceGpu,
                    onCheckStateChange = { on ->
                        if (on && !clvkInstalled) return@Switcher
                        onForceGpuChange(on)
                        if (on) { onEnginePreferenceChange(1); saveInt("engine", 1) }
                        saveBool("force_gpu", on)
                    },
                )
            }
        },
    )
    Spacer(Modifier.height(16.dp))

    SwitchSettingCard(
        heading = { Text(stringResource(R.string.auto_copy_tune_label)) },
        caption = { Text(stringResource(R.string.auto_copy_tune_hint), fontSize = 12.sp) },
        icon = { Icon(Icons.Filled.Copy, null) },
        trailing = {
            Box(
                Modifier
                    .width(64.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Switcher(
                    checked = autoCopyTuneFiles,
                    onCheckStateChange = { on ->
                        onAutoCopyTuneFilesChange(on)
                        saveBool("auto_copy_tune", on)
                    },
                )
            }
        },
    )
}

@Composable
private fun AiSettingsContent(
    aiSettings: AiSettingsUiState,
    onAiGlobalChange: (com.chuishui.katago.ai.config.AiGlobalSettings) -> Unit,
    aiConfigOf: (String) -> com.chuishui.katago.ai.config.AiProviderConfig,
    onAiSaveProvider: (com.chuishui.katago.ai.config.AiProviderConfig) -> Unit,
    onAiTestProvider: (String) -> Unit,
    onAiTestDraft: (com.chuishui.katago.ai.config.AiProviderConfig) -> Unit,
) {
    AiSettingsSection(
        state = aiSettings,
        onGlobalChange = onAiGlobalChange,
        configOf = aiConfigOf,
        onSaveProvider = onAiSaveProvider,
        onTestProvider = onAiTestProvider,
        onTestDraft = onAiTestDraft,
    )
}

@Composable
private fun AnalysisSettingsContent(
    enginePreference: Int,
    maxVisits: Int,
    onMaxVisitsChange: (Int) -> Unit,
    maxTimeSec: Float,
    onMaxTimeSecChange: (Float) -> Unit,
    numThreads: Int,
    onNumThreadsChange: (Int) -> Unit,
    analysisPVLen: Int,
    onAnalysisPVLenChange: (Int) -> Unit,
    nnMaxBatchSize: Int,
    onNnMaxBatchSizeChange: (Int) -> Unit,
    settingsRefreshTick: Int,
    onSettingsRefreshTickChange: () -> Unit,
    saveInt: (String, Int) -> Unit,
    saveFloat: (String, Float) -> Unit,
) {
    val fieldPositions = remember { mutableMapOf<String, Int>() }
    var lastEditedField by remember { mutableStateOf<String?>(null) }
    var scrollTarget by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    val visitsInput = rememberSaveable { mutableStateOf("") }
    val maxTimeInput = rememberSaveable { mutableStateOf("") }
    val threadsInput = rememberSaveable { mutableStateOf("") }
    val pvLenInput = rememberSaveable { mutableStateOf("") }
    val nnBatchInput = rememberSaveable { mutableStateOf("") }
    var editingField by remember { mutableStateOf<String?>(null) }

    fun openField(field: String) {
        editingField = field
        when (field) {
            "visits" -> visitsInput.value = "$maxVisits"
            "max_time" -> maxTimeInput.value = String.format("%.1f", maxTimeSec)
            "threads" -> threadsInput.value = "$numThreads"
            "pv_len" -> pvLenInput.value = "$analysisPVLen"
            "nn_batch" -> nnBatchInput.value = "$nnMaxBatchSize"
        }
    }

    fun applyField(field: String) {
        when (field) {
            "visits" -> visitsInput.value.toIntOrNull()?.coerceIn(10, 2000)?.let { onMaxVisitsChange(it); saveInt("visits", it) }
            "max_time" -> maxTimeInput.value.toFloatOrNull()?.let { val v = ((it.coerceIn(0.5f, 30f)) * 2).roundToInt() / 2f; onMaxTimeSecChange(v); saveFloat("max_time", v) }
            "threads" -> threadsInput.value.toIntOrNull()?.coerceIn(1, 8)?.let { onNumThreadsChange(it); saveInt("num_threads", it) }
            "pv_len" -> pvLenInput.value.toIntOrNull()?.coerceIn(0, 30)?.let { onAnalysisPVLenChange(it); saveInt("analysis_pv_len", it) }
            "nn_batch" -> nnBatchInput.value.toIntOrNull()?.coerceIn(0, 32)?.let { onNnMaxBatchSizeChange(it); saveInt("nn_max_batch", it) }
        }
        editingField = null
        lastEditedField = field
        scrollTarget = fieldPositions[field] ?: scrollState.value
        onSettingsRefreshTickChange()
    }

    @Composable
    fun EditDialog(title: String, value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit) {
        if (editingField == null) return
        FluentModalDialog(
            onDismiss = { editingField = null },
            title = title,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(title) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SubtleButton(onClick = { editingField = null }) { Text(stringResource(R.string.back)) }
                AccentButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) }
            }
        }
    }

    key(settingsRefreshTick) {
    Column {
        Text(stringResource(R.string.visits_label), fontWeight = FontWeight.SemiBold)
        Slider(
            value = maxVisits.toFloat(),
            onValueChange = { onMaxVisitsChange(it.toInt()) },
            onValueChangeFinished = { saveInt("visits", maxVisits) },
            valueRange = 10f..2000f,
        )
        EditableValueText(
            value = "$maxVisits",
            onClick = { openField("visits") },
            modifier = Modifier.onGloballyPositioned { fieldPositions["visits"] = it.positionInParent().y.toInt() },
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.max_time_label), fontWeight = FontWeight.SemiBold)
        Slider(
            value = maxTimeSec,
            onValueChange = { onMaxTimeSecChange(((it.coerceIn(0.5f, 30f)) * 2).roundToInt() / 2f) },
            onValueChangeFinished = { saveFloat("max_time", maxTimeSec) },
            valueRange = 0.5f..30f,
        )
        EditableValueText(
            value = String.format("%.1f", maxTimeSec),
            onClick = { openField("max_time") },
            suffix = " s",
            modifier = Modifier.onGloballyPositioned { fieldPositions["max_time"] = it.positionInParent().y.toInt() },
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.threads_label), fontWeight = FontWeight.SemiBold)
        Slider(
            value = numThreads.toFloat(),
            onValueChange = { onNumThreadsChange(it.toInt().coerceIn(1, 8)) },
            onValueChangeFinished = { saveInt("num_threads", numThreads) },
            valueRange = 1f..8f,
            steps = 6,
        )
        EditableValueText(
            value = "$numThreads",
            onClick = { openField("threads") },
            modifier = Modifier.onGloballyPositioned { fieldPositions["threads"] = it.positionInParent().y.toInt() },
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.pv_len_label), fontWeight = FontWeight.SemiBold)
        Slider(
            value = analysisPVLen.toFloat(),
            onValueChange = { onAnalysisPVLenChange(it.toInt().coerceIn(0, 30)) },
            onValueChangeFinished = { saveInt("analysis_pv_len", analysisPVLen) },
            valueRange = 0f..30f,
            steps = 29,
        )
        EditableValueText(
            value = if (analysisPVLen == 0) "0 (off)" else "$analysisPVLen",
            onClick = { openField("pv_len") },
            modifier = Modifier.onGloballyPositioned { fieldPositions["pv_len"] = it.positionInParent().y.toInt() },
        )
        Spacer(Modifier.height(16.dp))

        if (enginePreference != 2) {
            Text(stringResource(R.string.nn_batch_label), fontWeight = FontWeight.SemiBold)
            Slider(
                value = nnMaxBatchSize.toFloat(),
                onValueChange = { onNnMaxBatchSizeChange(it.toInt().coerceIn(0, 32)) },
                onValueChangeFinished = { saveInt("nn_max_batch", nnMaxBatchSize) },
                valueRange = 0f..32f,
                steps = 31,
            )
            EditableValueText(
                value = if (nnMaxBatchSize == 0) "0 (auto)" else "$nnMaxBatchSize",
                onClick = { openField("nn_batch") },
                modifier = Modifier.onGloballyPositioned { fieldPositions["nn_batch"] = it.positionInParent().y.toInt() },
            )
            Text(stringResource(R.string.nn_batch_hint), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
    }

    when (val field = editingField) {
        "visits" -> EditDialog(stringResource(R.string.visits_label), visitsInput.value, { visitsInput.value = it }) { applyField(field) }
        "max_time" -> EditDialog(stringResource(R.string.max_time_label), maxTimeInput.value, { maxTimeInput.value = it }) { applyField(field) }
        "threads" -> EditDialog(stringResource(R.string.threads_label), threadsInput.value, { threadsInput.value = it }) { applyField(field) }
        "pv_len" -> EditDialog(stringResource(R.string.pv_len_label), pvLenInput.value, { pvLenInput.value = it }) { applyField(field) }
        "nn_batch" -> EditDialog(stringResource(R.string.nn_batch_label), nnBatchInput.value, { nnBatchInput.value = it }) { applyField(field) }
    }

    LaunchedEffect(settingsRefreshTick) {
        if (settingsRefreshTick > 0 && lastEditedField != null) {
            val target = scrollTarget
            withFrameNanos { }
            withFrameNanos { }
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }
}

@Composable
private fun GameSettingsContent(
    allowResignation: Boolean,
    onAllowResignationChange: (Boolean) -> Unit,
    ponderingEnabled: Boolean,
    onPonderingEnabledChange: (Boolean) -> Unit,
    showAiPlan: Boolean,
    onShowAiPlanChange: (Boolean) -> Unit,
    saveBool: (String, Boolean) -> Unit,
) {
    SwitchSettingCard(
        heading = { Text(stringResource(R.string.resign_label)) },
        caption = { Text(stringResource(R.string.resign_hint), fontSize = 12.sp) },
        icon = { Icon(Icons.Filled.Flag, null) },
        trailing = {
            Box(
                Modifier
                    .width(64.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Switcher(
                    checked = allowResignation,
                    onCheckStateChange = { onAllowResignationChange(it); saveBool("allow_resignation", it) },
                )
            }
        },
    )
    Spacer(Modifier.height(12.dp))

    SwitchSettingCard(
        heading = { Text(stringResource(R.string.pondering_label)) },
        caption = { Text(stringResource(R.string.pondering_hint), fontSize = 12.sp) },
        icon = { Icon(Icons.Filled.Search, null) },
        trailing = {
            Box(
                Modifier
                    .width(64.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Switcher(
                    checked = ponderingEnabled,
                    onCheckStateChange = { onPonderingEnabledChange(it); saveBool("pondering", it) },
                )
            }
        },
    )
    Spacer(Modifier.height(12.dp))

    SwitchSettingCard(
        heading = { Text(stringResource(R.string.ai_show_ai_plan)) },
        caption = { Text(stringResource(R.string.ai_show_ai_plan_hint), fontSize = 12.sp) },
        icon = { Icon(Icons.Filled.Document, null) },
        trailing = {
            Box(
                Modifier
                    .width(64.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Switcher(
                    checked = showAiPlan,
                    onCheckStateChange = { onShowAiPlanChange(it) },
                )
            }
        },
    )
}

@Composable
private fun UiSettingsContent(
    themePreference: Int,
    onThemePreferenceChange: (Int) -> Unit,
    languagePreference: String,
    onLanguagePreferenceChange: (String) -> Unit,
    languageChangeEnabled: Boolean,
    recreate: () -> Unit,
    saveInt: (String, Int) -> Unit,
    saveString: (String, String) -> Unit,
    personalization: com.chuishui.katago.config.PersonalizationSettings,
    onPersonalizationChange: (com.chuishui.katago.config.PersonalizationSettings) -> Unit,
) {
    PersonalizationSection(
        settings = personalization,
        onSettingsChange = onPersonalizationChange,
    )
    Spacer(Modifier.height(24.dp))

    Text(stringResource(R.string.theme_label), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    val themeNames = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
    )
    ComboBox(
        items = themeNames,
        selected = themePreference,
        onSelectionChange = { index, _ ->
            onThemePreferenceChange(index)
            saveInt("theme", index)
        },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(
            when (themePreference) {
                2 -> R.string.theme_hint_dark
                1 -> R.string.theme_hint_light
                else -> R.string.theme_hint_system
            }
        ),
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(16.dp))

    val languageCodes = listOf("system", "en", "zh", "zh-rTW", "ja", "ko", "de")
    val languageNames = listOf(
        stringResource(R.string.language_system),
        "English",
        "\u7b80\u4f53\u4e2d\u6587",
        "\u7e41\u9ad4\u4e2d\u6587",
        "\u65e5\u672c\u8a9e",
        "\ud55c\uad6d\uc5b4",
        "Deutsch",
    )
    ComboBox(
        header = stringResource(R.string.language_label),
        placeholder = stringResource(R.string.language_system),
        items = languageNames,
        selected = languageCodes.indexOf(languagePreference).takeIf { it >= 0 },
        disabled = !languageChangeEnabled,
        onSelectionChange = { index, _ ->
            onLanguagePreferenceChange(languageCodes[index])
            saveString("language", languageCodes[index])
            recreate()
        },
    )
}

@Composable
private fun BackupSettingsContent(
    onRestoreDefaults: () -> Unit,
    onExportConfig: (Context, Uri) -> Unit,
    onImportConfig: (Context, Uri) -> Unit,
    context: Context,
    onShowRestoreConfirm: () -> Unit,
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onExportConfig(context, uri)
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportConfig(context, uri)
    }

    Text(stringResource(R.string.restore_defaults_label), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtleButton(onClick = { onShowRestoreConfirm() }) {
            Text(stringResource(R.string.restore_defaults_label), color = MaterialTheme.colorScheme.error)
        }
    }
    Text(stringResource(R.string.restore_defaults_hint), fontSize = 12.sp)
    Spacer(Modifier.height(24.dp))

    Text(stringResource(R.string.config_backup_title), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentButton(onClick = { exportPicker.launch("katago-config.zip") }) {
            Text(stringResource(R.string.config_export_button))
        }
        SubtleButton(onClick = { importPicker.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.config_import_button))
        }
    }
    Text(stringResource(R.string.config_backup_hint), fontSize = 12.sp)
}

@Composable
private fun OpenClSettingsContent(
    clvkInstalled: Boolean,
    openClImportStatus: Pair<Boolean, String>?,
    onOpenClSetup: () -> Unit,
    onShowReinstallConfirm: () -> Unit,
) {
    Text(stringResource(R.string.opencl_import_label), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.opencl_import_hint), fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentButton(onClick = {
            if (clvkInstalled) {
                onShowReinstallConfirm()
            } else {
                onOpenClSetup()
            }
        }) { Text(stringResource(R.string.opencl_import_button)) }
    }
    openClImportStatus?.let { (ok, msg) ->
        Spacer(Modifier.height(8.dp))
        Text(
            msg,
            fontSize = 12.sp,
            color = if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SwitchSettingCard(
    heading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = {},
    caption: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .heightIn(min = 62.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .widthIn(min = 48.dp)
                        .defaultMinSize(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 13.dp)) {
                heading()
                ProvideTextStyle(FluentTheme.typography.caption.copy(FluentTheme.colors.text.text.secondary)) {
                    caption()
                }
            }
            Row(
                Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing()
            }
        }
    }
}
