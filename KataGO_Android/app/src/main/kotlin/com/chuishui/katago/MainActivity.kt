package com.chuishui.katago

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.chuishui.katago.engine.AutotuneWatchdog
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.text.KeyboardOptions
import io.github.composefluent.component.TextField
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.InfoBar
import io.github.composefluent.component.InfoBarDefaults
import io.github.composefluent.component.InfoBarSeverity
import com.chuishui.katago.engine.GtpEngine
import com.chuishui.katago.engine.ModelStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chuishui.katago.engine.NetworkModel
import com.chuishui.katago.config.ConfigExport
import com.chuishui.katago.engine.NetworkStore
import com.chuishui.katago.go.BoardState
import com.chuishui.katago.game.AiMoveOutcome
import com.chuishui.katago.game.GameSetup
import com.chuishui.katago.ai.AiContainer
import com.chuishui.katago.ai.ProviderFactory
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.provider.ProviderStatus
import com.chuishui.katago.goai.AiCommentaryResult
import com.chuishui.katago.goai.GoAiContext
import com.chuishui.katago.goai.GoAiPromptBuilder
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DialogSize
import io.github.composefluent.component.NavigationDisplayMode
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.menuItem
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Home
import io.github.composefluent.icons.filled.Person
import io.github.composefluent.icons.filled.Settings
import io.github.composefluent.component.Icon
import io.github.composefluent.surface.Card
import io.github.composefluent.surface.CardColor
import io.github.composefluent.surface.CardDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.chuishui.katago.save.GameSaveStore

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("katago", Context.MODE_PRIVATE)
        val code = prefs.getString("language", "system") ?: "system"
        super.attachBaseContext(applyLanguage(newBase, code))
    }

    private fun applyLanguage(base: Context, code: String): Context {
        if (code == "system") return base
        val locale = when (code) {
            "en" -> Locale("en")
            "zh" -> Locale("zh", "CN")
            "zh-rTW" -> Locale("zh", "TW")
            "ja" -> Locale("ja")
            "ko" -> Locale("ko")
            "de" -> Locale("de")
            else -> return base
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    val engine = GtpEngine()
    val board = BoardState(19)

    val engineBusy = MutableStateFlow(false)
    val infoLine = MutableStateFlow("")

    var engineMode by mutableStateOf("cpu")
        private set

    var enginePreference by mutableStateOf(0)

    /** Force the OpenCL engine, overriding the crash lockout (gpuDisabled). */
    var forceGpu by mutableStateOf(false)
    var maxVisits by mutableStateOf(300)
    var maxTimeSec by mutableStateOf(5f)
    var numThreads by mutableStateOf(4)
    var allowResignation by mutableStateOf(false)
    var ponderingEnabled by mutableStateOf(false)
    var analysisPVLen by mutableStateOf(0)

    /** Result of the last clvk libOpenCL.so import: (ok, status message). */
    var openClImportStatus by mutableStateOf<Pair<Boolean, String>?>(null)
        private set

    /** True when a clvk libOpenCL.so has been imported into filesDir. */
    var clvkInstalled by mutableStateOf(false)
        private set

    /** Progress (0..1) of an ongoing clvk import/download, or null when idle. */
    var openClTransferProgress by mutableStateOf<Float?>(null)
        private set

    /** 0 = idle, 1 = downloading, 2 = installing (copying into filesDir). */
    var openClTransferPhase by mutableStateOf(0)
        private set

    /** Current download speed in bytes per second, or null when not downloading. */
    var openClTransferSpeed by mutableStateOf<Float?>(null)
        private set

    /** True while a cloud download is in progress (not a local file import). */
    var openClDownloading by mutableStateOf(false)
        private set

    /** Confirm dialog for cancelling an in-flight cloud download. */
    var showCancelOpenClDialog by mutableStateOf(false)

    /** Battery-optimization exemption prompt shown at startup and again if the
     *  user returns from the system settings without granting it. */
    var showBatteryPrompt by mutableStateOf(false)
    var batteryPromptJustRequested by mutableStateOf(false)

    /** 0 = auto (≈ search threads). Only relevant for GPU engines. */
    var nnMaxBatchSize by mutableStateOf(0)

    /** When true, copy tuning from existing models instead of full autotune. */
    var autoCopyTuneFiles by mutableStateOf(true)

    /** Personalization: background, overlay, card alpha. */
    var personalization by mutableStateOf(com.chuishui.katago.config.PersonalizationSettings())

    /** Engine loading screen state. */
    var engineLoading by mutableStateOf(false)
        private set
    var engineLoadingMessage by mutableStateOf("")
        private set

    /** Jobs for the engine-loading coroutines, so the user can cancel a stuck load. */
    private var loadingJob: Job? = null

    /** Error from the last engine-start attempt; shown once the retry also fails. */
    private var lastEngineError = ""

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themePreference by mutableStateOf(0)

    /** Language code: "system", "en", "zh", "zh-rTW", "ja", "ko", "de". */
    var languagePreference by mutableStateOf("system")

    /** Bump to force the settings screen to refresh after a dialog confirm. */
    var settingsRefreshTick by mutableStateOf(0)
        private set

    /** GPU permanently disabled because a previous GPU start crashed. */
    var gpuDisabled by mutableStateOf(false)
        private set

    /** Whether the first-run welcome screen should be shown. */
    var showOnboarding by mutableStateOf(false)

    /** Current step of the onboarding flow (hoisted so the settings page can reopen it). */
    internal var onboardingStep by mutableStateOf(OnboardingStep.Welcome)
        private set

    /** True when the onboarding driver page was opened from the settings screen. */
    var onboardingFromSettings by mutableStateOf(false)

    /** Watchdog for OpenCL autotuning crash recovery. */
    private val autotuneWatchdog by lazy { AutotuneWatchdog(this) }

    var engineColor: Char by mutableStateOf('W')
        private set

    var boardSize by mutableStateOf(19)
        private set

    var lastMove by mutableStateOf<Int?>(null)
        private set

    /** Ghost-stone preview of the move the human is about to confirm. */
    var pendingHumanMove by mutableStateOf<Int?>(null)
        private set

    var statusText by mutableStateOf("")
        private set

    var winrate by mutableStateOf<Float?>(null)
        private set

    var scoreLead by mutableStateOf<Float?>(null)
        private set

    /** Per-AI-move snapshots of winrate/scoreLead, restored on undo. */
    private val winrateHistory = ArrayDeque<Float?>()
    private val scoreLeadHistory = ArrayDeque<Float?>()

    var capturedBlack by mutableStateOf(0)
        private set

    var capturedWhite by mutableStateOf(0)
        private set

    var gpuDiag by mutableStateOf<String?>(null)
        private set

    var gameActive by mutableStateOf(false)
        private set

    /** True while the game-over dialog (win/loss/draw) is on screen. */
    var showGameOverDialog by mutableStateOf(false)
        private set

    /** Game-over dialog title ("You won" / "You lost" / "Draw"). */
    var gameOverTitle by mutableStateOf("")
        private set

    /** Game-over dialog body (final score / resignation reason). */
    var gameOverMessage by mutableStateOf("")
        private set

    /** The last game's parameters, so "play again" can restart it as-is. */
    private var lastGameSetup: GameSetup? = null

    /** Cumulative clock: milliseconds spent thinking on each side. */
    var humanThinkMs by mutableStateOf(0L)
        private set
    var aiThinkMs by mutableStateOf(0L)
        private set

    /** Whose clock currently runs: 'H' = human, 'A' = AI. Used to reset per turn. */
    var currentThinker by mutableStateOf<Char?>(null)
        private set

    /** Total game elapsed time since the game started. */
    var gameElapsedMs by mutableStateOf(0L)
        private set

    var models by mutableStateOf(listOf<File>())
        private set

    // ---- AI subsystem -----------------------------------------------

    lateinit var aiContainer: AiContainer
        private set

    var aiSettingsUi by mutableStateOf(AiSettingsUiState())
        private set

    var aiCommentary by mutableStateOf<AiCommentaryResult?>(null)
        private set

    var aiAnalyzing by mutableStateOf(false)
        private set

    var aiShowCommentary by mutableStateOf(true)
        private set

    var aiOfflineOnly by mutableStateOf(false)
        private set

    /** Whether to draw a marker at the engine's current planned move while it thinks. */
    var aiShowAiPlan by mutableStateOf(false)
        private set

    /** KataGo's currently favoured move (order 0 of the latest `info move`
     *  batch), parsed from the engine's analysis stream. Displayed on the board
     *  while the engine is thinking, when [aiShowAiPlan] is enabled. */
    var aiPlanVertex by mutableStateOf<Int?>(null)
        private set

    /** Multi-turn transcript shown in the commentary panel. Holds both the
     *  user's questions and the automatic explanations, so auto analysis and
     *  the dialogue render as the same bubble stream. */
    var aiChatHistory by mutableStateOf(listOf<AiChatMessage>())
        private set

    /** Question currently awaiting an AI answer (shows a bubble while loading). */
    var pendingAiQuestion by mutableStateOf<String?>(null)
        private set

    /** Raw AI request payload from the last coach call, for debug inspection. */
    var aiRawRequest by mutableStateOf<String?>(null)
        private set

    /** Raw AI response content from the last coach call, for debug inspection. */
    var aiRawResponse by mutableStateOf<String?>(null)
        private set

    /** Suggested move vertices from the latest AI answer, shown on the board. */
    var aiTipVertices by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Last "info" line from the engine, used to extract best move / PV. */
    @Volatile
    private var lastMistakeInfoLine = ""

    /**
     * KataGo's recommended move for the *human's* turn, captured before the
     * human plays. Requires pondering so the engine keeps analysing the
     * player-facing position; otherwise only the engine's own move is known.
     */
    @Volatile
    private var lastHumanBestMove: String? = null

    /** True when the last player move was the human's (enables mistake detection). */
    private var humanJustMoved = false

    /** True while a saved game's moves are being replayed by the engine; the
     *  board must not accept any human input during this time. */
    private var replayingSave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("katago", MODE_PRIVATE)
        enginePreference = prefs.getInt("engine", 0)
        forceGpu = prefs.getBoolean("force_gpu", false)
        maxVisits = prefs.getInt("visits", 300)
        maxTimeSec = prefs.getFloat("max_time", 5f)
        numThreads = prefs.getInt("num_threads", 4).coerceIn(1, 8)
        allowResignation = prefs.getBoolean("allow_resignation", false)
        ponderingEnabled = prefs.getBoolean("pondering", true)
        analysisPVLen = prefs.getInt("analysis_pv_len", 0)
        nnMaxBatchSize = prefs.getInt("nn_max_batch", 0).coerceIn(0, 32)
        autoCopyTuneFiles = prefs.getBoolean("auto_copy_tune", true)
        personalization = com.chuishui.katago.config.PersonalizationSettings.load(this)
        themePreference = prefs.getInt("theme", 0)
        languagePreference = prefs.getString("language", "system") ?: "system"
        clvkInstalled = File(filesDir, "libOpenCL.so").exists()
        showOnboarding = !prefs.getBoolean("onboarded", false)
        enableEdgeToEdge()
        // First launch: if the system can still kill the background download,
        // ask for a battery-optimization exemption right away.
        val pm = getSystemService(PowerManager::class.java)
        showBatteryPrompt = !pm.isIgnoringBatteryOptimizations(packageName)
        // If the previous run left a native crash record from the GPU engine,
        // the process died in OpenCL init. Use the autotune watchdog to decide
        // whether to retry (with recovery markers), use safe defaults, or
        // permanently disable GPU. The watchdog handles up to 3 autotuning
        // crash retries before falling back. Crashes outside autotuning
        // (during normal play) still permanently disable GPU.
        val crashRecorded = hadNativeGpuCrash()
        val attemptAborted = prefs.getBoolean("gpu_attempt_pending", false)
        val permanentlyDisabled = prefs.getBoolean("gpu_disabled", false) ||
            autotuneWatchdog.isGpuPermanentlyDisabled()

        if ((crashRecorded || attemptAborted) && !permanentlyDisabled && !forceGpu) {
            // Check if this was an autotuning crash (recoverable)
            if (autotuneWatchdog.hadCrashDuringAutotuning()) {
                when (autotuneWatchdog.handleAutotuneCrash()) {
                    AutotuneWatchdog.CrashRecoveryAction.RETRY_WITH_RECOVERY -> {
                        Log.i("MainActivity", "Autotune crash recovery: retrying with recovery markers")
                        // Don't set gpuDisabled - we'll retry GPU with recovery
                    }
                    AutotuneWatchdog.CrashRecoveryAction.USE_SAFE_DEFAULTS -> {
                        Log.i("MainActivity", "Autotune crash recovery: using safe defaults")
                    }
                    AutotuneWatchdog.CrashRecoveryAction.DISABLE_GPU -> {
                        gpuDisabled = true
                        prefs.edit().putBoolean("gpu_disabled", true).putBoolean("gpu_attempt_pending", false).apply()
                        if (enginePreference == 1) {
                            enginePreference = 2
                            prefs.edit().putInt("engine", 2).apply()
                        }
                    }
                }
            } else {
                // Crash during normal play or non-autotune GPU crash: permanent disable
                gpuDisabled = true
                prefs.edit().putBoolean("gpu_disabled", true).putBoolean("gpu_attempt_pending", false).apply()
                if (enginePreference == 1) {
                    enginePreference = 2
                    prefs.edit().putInt("engine", 2).apply()
                }
            }
        } else if (permanentlyDisabled && !forceGpu) {
            gpuDisabled = true
            prefs.edit().putBoolean("gpu_disabled", true).putBoolean("gpu_attempt_pending", false).apply()
            if (enginePreference == 1) {
                enginePreference = 2
                prefs.edit().putInt("engine", 2).apply()
            }
        }
        engine.onInfo = { line ->
            infoLine.value = line
            lastMistakeInfoLine = line
            parseWinrate(line)?.let { winrate = it }
            parseScoreLead(line)?.let { scoreLead = it }
            parseBestCoord(line)?.let { coord -> aiPlanVertex = board.fromGtpCoord(coord) }
        }
        aiContainer = AiContainer(this)
        saveStore = GameSaveStore()
        aiContainer.refreshProviders()
        refreshAiSettingsUi()
        refreshModels()
        // Mirror the foreground-service download state into the UI state, so the
        // onboarding page / InfoBar keep working even when the download runs
        // in the background service.
        lifecycleScope.launch {
            launch { OpenClDownloadWorker.progress.collect { openClTransferProgress = it } }
            launch { OpenClDownloadWorker.speed.collect { openClTransferSpeed = it } }
            launch { OpenClDownloadWorker.phase.collect { openClTransferPhase = it } }
            launch { OpenClDownloadWorker.downloading.collect { openClDownloading = it } }
            launch {
                OpenClDownloadWorker.status.collect { s ->
                    if (s != null) {
                        openClImportStatus = s
                        if (s.first) clvkInstalled = File(filesDir, "libOpenCL.so").exists()
                        Toast.makeText(this@MainActivity, s.second, Toast.LENGTH_LONG).show()
                        // Ask for the battery-optimization exemption only after
                        // the download finished: requesting it at download start
                        // opens a system page that pushes the app to the
                        // background, which makes Android 12+ refuse the
                        // foreground-service promotion.
                        if (s.first) requestBatteryOptimizationExemption()
                    }
                }
            }
        }
        setContent { Root() }
    }

    /** When the user returns from the "All files access" settings screen
     *  (or the runtime-permission dialog) the storage permission may have
     *  just been granted. Re-read the model list on every resume so the home
     *  page picks up the newly accessible models without needing a manual
     *  refresh. */
    override fun onResume() {
        super.onResume()
        if (ModelStore.hasStorageAccess(this)) refreshModels()
        // Returned from the battery-optimization settings page without granting
        // the exemption: re-prompt so the background download stays reliable.
        if (batteryPromptJustRequested) {
            batteryPromptJustRequested = false
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) showBatteryPrompt = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Engine is shutting down normally; clear the watchdog pending flag
        // so next launch does not falsely detect a crash.
        autotuneWatchdog.onGpuStartSucceeded()
        engine.stop()
    }

    /** Restores the engine config to its fresh-install defaults: clears the
     *  matching prefs and resets the in-memory state so the next engine start
     *  writes a default gtp.cfg. UI-only prefs (theme, language) are kept. */
    fun restoreDefaultConfig() {
        val prefs = getSharedPreferences("katago", Context.MODE_PRIVATE)
        enginePreference = 0
        forceGpu = false
        gpuDisabled = false
        maxVisits = 300
        maxTimeSec = 5f
        numThreads = 4
        allowResignation = false
        ponderingEnabled = true
        analysisPVLen = 0
        nnMaxBatchSize = 0
        autoCopyTuneFiles = true
        prefs.edit()
            .remove("engine")
            .remove("force_gpu")
            .remove("gpu_disabled")
            .remove("gpu_attempt_pending")
            .remove("visits")
            .remove("max_time")
            .remove("num_threads")
            .remove("allow_resignation")
            .remove("pondering")
            .remove("analysis_pv_len")
            .remove("nn_max_batch")
            .remove("auto_copy_tune")
            .apply()
        settingsRefreshTick++
        Toast.makeText(this, getString(R.string.restore_defaults_done), Toast.LENGTH_SHORT).show()
    }

    // ---- OpenCL library import (clvk) --------------------------------------

    private fun importOpenClLib(context: Context, uri: Uri) {
        openClImportStatus = true to getString(R.string.opencl_importing)
        openClTransferPhase = 3
        openClTransferProgress = 0f
        openClTransferSpeed = null
        lifecycleScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                try {
                    val cache = context.cacheDir
                    val raw = File(cache, "opencl-import.bin")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext false to getString(R.string.opencl_import_bad)
                    input.use { i ->
                        raw.outputStream().use { o ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var done = 0L
                            var n = i.read(buf)
                            val t0 = System.currentTimeMillis()
                            while (n >= 0) {
                                o.write(buf, 0, n)
                                done += n
                                openClTransferProgress = if (done > 0) 0.5f else 0f
                                val dt = System.currentTimeMillis() - t0
                                if (dt > 0) {
                                    openClTransferSpeed = done * 1000f / dt
                                }
                                n = i.read(buf)
                            }
                        }
                    }

                    openClTransferPhase = 2
                    openClTransferSpeed = null
                    val candidate = extractLibOpenClSo(raw)
                    val valid = candidate != null &&
                        candidate.length() > 1_000_000L &&
                        isAarch64Elf(candidate)
                    if (!valid) {
                        raw.delete()
                        return@withContext false to getString(R.string.opencl_import_bad)
                    }
                    openClTransferProgress = 0.9f
                    val dst = File(context.filesDir, "libOpenCL.so")
                    val tmp = File(context.filesDir, "libOpenCL.so.tmp")
                    candidate.copyTo(tmp, overwrite = true)
                    dst.delete()
                    if (!tmp.renameTo(dst)) {
                        tmp.copyTo(dst, overwrite = true)
                        tmp.delete()
                    }
                    raw.delete()
                    if (candidate !== raw) candidate.delete()
                    true to getString(R.string.opencl_import_ok)
                } catch (e: Exception) {
                    false to "Import failed: ${e.message}"
                }
            }
            openClTransferProgress = null
            openClTransferPhase = 0
            openClTransferSpeed = null
            openClImportStatus = ok to msg
            if (ok) clvkInstalled = File(context.filesDir, "libOpenCL.so").exists()
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Starts the cloud download of libOpenCL.so via WorkManager so it keeps
     *  running (with a progress notification) even if the UI is closed or the
     *  process is killed. Also asks for a battery-optimization exemption. */
    fun downloadOpenClLib() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        if (OpenClDownloadWorker.isDownloading()) return
        openClImportStatus = true to getString(R.string.opencl_importing)
        openClTransferPhase = 1
        openClTransferProgress = 0f
        openClTransferSpeed = null
        openClDownloading = true
        val request = OneTimeWorkRequestBuilder<OpenClDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "opencl_download",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Asks the system to whitelist the app against battery optimization. */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "battery optimization request failed", e)
        }
    }

    /** Cancels an in-flight cloud download, discarding any partial file.
     *  The worker's companion state flows are reset too, otherwise the
     *  collectors mirroring them back into the UI immediately re-assert the
     *  "downloading" state and the cancel appears to have no effect. */
    fun cancelOpenClDownload() {
        WorkManager.getInstance(this).cancelUniqueWork("opencl_download")
        OpenClDownloadWorker.downloading.value = false
        OpenClDownloadWorker.progress.value = null
        OpenClDownloadWorker.speed.value = null
        OpenClDownloadWorker.phase.value = 0
        OpenClDownloadWorker.status.value = null
        openClDownloading = false
        openClTransferProgress = null
        openClTransferPhase = 0
        openClTransferSpeed = null
    }

    // ---- model management -------------------------------------------------

    fun refreshModels() {
        models = ModelStore.listModels()
    }

    /** Dismisses the first-run welcome screen and remembers the choice. */
    fun finishOnboarding() {
        showOnboarding = false
        onboardingFromSettings = false
        getSharedPreferences("katago", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarded", true)
            .apply()
    }

    fun exportConfig(context: Context, uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val zip = ConfigExport.buildZip(this@MainActivity, aiContainer)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(zip) }
                        ?: error("cannot open output stream")
                }
            }
            result.onSuccess {
                Toast.makeText(this@MainActivity, getString(R.string.config_export_done), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, getString(R.string.config_export_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importConfig(context: Context, uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("cannot open input stream")
                    ConfigExport.restoreFromZip(this@MainActivity, aiContainer, bytes)
                }
            }
            result.onSuccess {
                Toast.makeText(this@MainActivity, getString(R.string.config_import_done), Toast.LENGTH_SHORT).show()
                recreate()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, getString(R.string.config_import_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun chooseModel(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { ModelStore.importModel(this@MainActivity, uri) }
            if (file != null) {
                Toast.makeText(this@MainActivity, getString(R.string.imported, file.name), Toast.LENGTH_LONG).show()
                refreshModels()
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.import_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- game save / load -------------------------------------------------

    private lateinit var saveStore: GameSaveStore

    private fun saveGame() {
        saveStore.save(
            boardSize = boardSize,
            engineColor = engineColor,
            moves = board.moves,
            humanThinkMs = humanThinkMs,
            aiThinkMs = aiThinkMs,
            gameElapsedMs = gameElapsedMs,
            chatHistory = aiChatHistory,
        ).onSuccess {
            Toast.makeText(this, getString(R.string.game_saved), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(this, getString(R.string.save_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    // ---- game -------------------------------------------------------------

    /**
     * Komi per board size. The bundled models evaluate small boards badly at
     * 7.5 (empty 9x9 "black to move" drops to ~6% winrate), which makes the AI
     * look unbeatable from the first move. On 9x9/13x13, 7.0 keeps the opening
     * winrate near even; 19x19 keeps the standard 7.5.
     */
    fun komiFor(size: Int): Float = when {
        size <= 13 -> 7.0f
        else -> 7.5f
    }

    /**
     * Cancels a stuck engine-loading sequence from the loading screen's cancel
     * button. Stops the engine and returns to the home screen.
     */
    private fun cancelEngineLoading() {
        engineLoading = false
        loadingJob?.cancel()
        loadingJob = null
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                engine.stop()
            }
        }
    }

    fun startGame(
        modelFile: File,
        size: Int,
        humanColor: String,
        replay: List<BoardState.Move>? = null,
        humanThinkMs0: Long = 0,
        aiThinkMs0: Long = 0,
        gameElapsedMs0: Long = 0,
        chatHistory0: List<AiChatMessage> = emptyList(),
    ) {
        loadingJob?.cancel()
        lastGameSetup = GameSetup(modelFile, size, humanColor)
        pendingHumanMove = null
        aiPlanVertex = null
        aiTipVertices = emptyList()
        boardSize = size
        board.resize(size)
        lastMove = null
        winrate = null
        scoreLead = null
        winrateHistory.clear()
        scoreLeadHistory.clear()
        aiCommentary = null
        aiAnalyzing = false
        aiChatHistory = chatHistory0
        pendingAiQuestion = null
        humanJustMoved = false
        lastHumanBestMove = null
        aiContainer.mistakeDetector.reset()
        humanThinkMs = humanThinkMs0
        aiThinkMs = aiThinkMs0
        currentThinker = null
        gameElapsedMs = gameElapsedMs0
        syncCaptures()
        engineColor = if (humanColor == "black") 'W' else 'B'
        engineLoading = true
        engineLoadingMessage = getString(R.string.loading_engine)
        statusText = getString(R.string.loading_engine)
        loadingJob = lifecycleScope.launch {
            val tuneDir = File(cacheDir, "opencltuning")
            val activeMarker = File(tuneDir, "tuning_active.marker")
            val copyMarker = File(tuneDir, "tuning_copy.marker")
            while (engineLoading) {
                val msg = withContext(Dispatchers.IO) {
                    when {
                        activeMarker.exists() -> getString(R.string.loading_autotune)
                        copyMarker.exists() -> getString(R.string.loading_tune_file)
                        else -> getString(R.string.loading_engine)
                    }
                }
                engineLoadingMessage = msg
                delay(200)
            }
        }
        loadingJob = lifecycleScope.launch {
            gameActive = false
            var ok = startEngineOnce(modelFile, size, replay)
            if (!ok) {
                engineLoadingMessage = getString(R.string.engine_retrying)
                delay(600)
                ok = startEngineOnce(modelFile, size, replay)
            }
            if (!ok) {
                engineLoading = false
                gameActive = false
                statusText = lastEngineError
            }
        }
    }

    /** One engine start attempt: writes gtp.cfg, picks the engine binary and
     *  starts it with a GTP handshake. Returns true on success; on failure it
     *  stops the engine, stores the error in [lastEngineError] and returns
     *  false so [startGame] can retry once before giving up. */
    private suspend fun startEngineOnce(
        modelFile: File,
        size: Int,
        replay: List<BoardState.Move>?,
    ): Boolean {
        return try {
            val config = File(cacheDir, "gtp.cfg")
            withContext(Dispatchers.IO) {
                engine.stop()
                val asset = runCatching {
                    assets.open("gtp.cfg").bufferedReader().use { it.readText() }
                }.getOrNull()
                val overrides = buildString {
                    appendLine("maxVisits = ${maxVisits}")
                    appendLine("maxTime = ${maxTimeSec}")
                    appendLine("numSearchThreads = ${numThreads}")
                    appendLine("allowResignation = $allowResignation")
                    // Default KataGo resigns only below a 5% winrate for 3
                    // consecutive turns; by then it already "gives up" by
                    // passing. Trigger resignation earlier (winrate < 25%
                    // for 2 turns) so a hopeless AI resigns instead.
                    appendLine("resignThreshold = -0.50")
                    appendLine("resignConsecTurns = 2")
                    appendLine("ponderingEnabled = $ponderingEnabled")
                    if (analysisPVLen > 0) appendLine("analysisPVLen = ${analysisPVLen}")
                    if (nnMaxBatchSize > 0) appendLine("nnMaxBatchSize = ${nnMaxBatchSize}")
                    appendLine("openclAutoCopyTuneFile = ${autoCopyTuneFiles}")
                    appendLine("logToStderr = false")
                    appendLine("logAllGTPCommunication = false")
                    appendLine("logDir = ${cacheDir.absolutePath}")
                    appendLine("homeDataDir = ${cacheDir.absolutePath}")
                    appendLine("reportAnalysisWinratesAs = BLACK")
                }
                config.writeText(dedupConfig((asset ?: defaultConfig()) + "\n" + overrides))
            }
            val gpuSo = File(applicationInfo.nativeLibraryDir, "libkatago_opencl.so")
            val cpuSo = File(applicationInfo.nativeLibraryDir, "libkatago.so")
            val prefs = getSharedPreferences("katago", Context.MODE_PRIVATE)
            val candidates = LinkedHashSet<File>()
            // GPU mode is only valid when a clvk libOpenCL.so has been
            // installed; otherwise OpenCL cannot compile kernels at runtime
            // and every GPU start would fail or crash. Never let the app
            // select the GPU engine without it.
            if (forceGpu || enginePreference < 2) {
                if (!clvkInstalled) {
                    enginePreference = 2
                    forceGpu = false
                    prefs.edit().putBoolean("force_gpu", false).apply()
                    prefs.edit().putInt("engine", 2).apply()
                }
            }
            when {
                forceGpu -> if (gpuSo.exists() && clvkInstalled) candidates.add(gpuSo)
                enginePreference == 1 -> if (gpuSo.exists() && !gpuDisabled && clvkInstalled) candidates.add(gpuSo)
                enginePreference == 2 -> if (cpuSo.exists()) candidates.add(cpuSo)
                else -> {
                    // auto: GPU first, CPU fallback -- but never touch GPU
                    // again once a previous GPU start crashed the process.
                    if (gpuSo.exists() && !gpuDisabled && clvkInstalled) candidates.add(gpuSo)
                    if (cpuSo.exists()) candidates.add(cpuSo)
                }
            }
            if (candidates.isEmpty()) {
                val msg = when {
                    forceGpu -> "libkatago_opencl.so not found"
                    gpuDisabled -> "GPU disabled after crash; libkatago.so not found"
                    else -> "libkatago.so not found"
                }
                throw IllegalStateException(msg)
            }

            var lastErr: Exception? = null
            var started = false
            var gpuFailed: Exception? = null
            val gpuAttempted = candidates.any { isGpuEngine(it.absolutePath) }
            // Crash watchdog: if the in-process GPU engine takes the process
            // down (even during System.loadLibrary, before native crash
            // handlers are installed), the watchdog detects this on next
            // launch and can retry with recovery markers (up to 3 times).
            val prefs2 = getSharedPreferences("katago", Context.MODE_PRIVATE)
            if (gpuAttempted) {
                autotuneWatchdog.prepareForGpuStart()
            }
            var gpuOk = false
            for (so in candidates) {
                try {
                    engine.stop()
                    engine.start(so.absolutePath, config.absolutePath, modelFile.absolutePath, filesDir.absolutePath)
                    checkReply("boardsize $size", engine.send("boardsize $size"))
                    val komi = komiFor(size)
                    checkReply("komi $komi", engine.send("komi $komi"))
                    checkReply("clear_board", engine.send("clear_board"))
                    engineMode = if (isGpuEngine(so.absolutePath)) "gpu" else "cpu"
                    if (isGpuEngine(so.absolutePath)) gpuOk = true
                    started = true
                    break
                } catch (e: Exception) {
                    if (isGpuEngine(so.absolutePath)) gpuFailed = e
                    lastErr = e
                    engine.stop()
                }
            }
            if (gpuAttempted && gpuOk) {
                // GPU started its engine thread; leave gpu_attempt_pending true so
                // the watchdog can detect a crash during autotuning.  The flag is
                // cleared when the engine finishes normally (quit/exit).
            } else if (gpuAttempted) {
                // GPU failed with catchable exception; clear the pending flag
                // and let the watchdog handle recovery on next launch if needed.
                prefs2.edit().putBoolean("gpu_attempt_pending", false).commit()
            }
            if (!started) {
                engine.stop()
                throw lastErr ?: IllegalStateException("engine failed to start")
            }
            engineLoading = false
            gameActive = true
            if (engineMode != "gpu" && gpuFailed != null) {
                val log = engine.stderrLog()
                gpuDiag = getString(R.string.gpu_unavailable, engineFailureHint(gpuFailed)) +
                    "\n\n---- stderr ----\n" + log
            }
            if (replay != null) {
                replayingSave = true
                try {
                    for (m in replay) {
                        if (m.pass) {
                            checkReply("play ${m.color} pass", engine.send("play ${m.color} pass"))
                            board.forcePlay(-1, m.color, pass = true)
                        } else {
                            checkReply("play ${m.color} ${board.toGtpCoord(m.vertex)}", engine.send("play ${m.color} ${board.toGtpCoord(m.vertex)}"))
                            board.forcePlay(m.vertex, m.color)
                        }
                    }
                } finally {
                    replayingSave = false
                }
                syncCaptures()
                lastMove = board.moves.lastOrNull()?.takeIf { !it.pass }?.vertex
                if (board.currentPlayer == engineColor) {
                    statusText = getString(R.string.thinking)
                    engineMove()
                } else {
                    statusText = getString(R.string.your_move, side())
                }
            } else if (engineColor == 'B') {
                statusText = getString(R.string.you_play_white)
                engineMove()
            } else {
                statusText = getString(R.string.you_play_black)
            }
            true
        } catch (e: Exception) {
            engine.stop()
            val detail = engine.stderrLog().lines().takeLast(3).joinToString("\n")
            lastEngineError = getString(R.string.engine_error, (e.message ?: "") + "\n" + detail)
            false
        }
    }

    private fun checkReply(cmd: String, reply: String) {
        val first = reply.lines().firstOrNull() ?: "= ERR empty reply"
        if (first.startsWith("? ") ||
            first.startsWith("= timeout") ||
            first.startsWith("= ERR") ||
            reply.contains("ERR", ignoreCase = true)
        ) {
            throw IllegalStateException("$cmd -> $reply")
        }
    }

    private fun defaultConfig(): String = buildString {
        appendLine("logToStderr = false")
        appendLine("gtpMaxVisits = 400")
        appendLine("gtpSimulatedEarlyTermMin = 0")
        appendLine("analysisPVLen = 0")
        appendLine("reportAnalysisWinratesAs = BLACK")
    }

    private fun dedupConfig(text: String): String {
        val lines = text.lines()
        val seen = HashSet<String>()
        val kept = ArrayList<String>()
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                kept.add(line)
                continue
            }
            val eq = trimmed.indexOf('=')
            if (eq < 0) {
                kept.add(line)
                continue
            }
            val key = trimmed.substring(0, eq).trim()
            if (seen.add(key)) kept.add(line)
        }
        return kept.asReversed().joinToString("\n")
    }

    private fun hasOpenCL(): Boolean {
        val paths = listOf(
            "/system/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/libexec/qcom/libOpenCL.so",
        )
        return paths.any { File(it).exists() }
    }

    private fun Context.saveDiagnosticLog(text: String) {
        val file = File(getExternalFilesDir(null) ?: cacheDir, "engine_diag.txt")
        try {
            file.writeText(text + "\n")
            Toast.makeText(this, getString(R.string.log_saved, file.absolutePath), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun engineFailureHint(e: Exception): String {
        val reason = e.message ?: ""
        val base = if (hasOpenCL()) "engine init" else "no libOpenCL.so on system"
        val err = engine.stderrLog().lines().filter { it.isNotBlank() }.takeLast(8).joinToString(" / ")
        return (listOf(base, reason, err).filter { it.isNotBlank() }.distinct()).joinToString(" · ")
    }

    /** True if the previous GPU run left a native crash record behind,
     *  or the autotune watchdog detected a crash during autotuning. */
    private fun hadNativeGpuCrash(): Boolean {
        return try {
            // Marker written by the native crash handler in-engine.
            if (File(filesDir, "gpu_crashed.marker").exists()) return true
            val candidates = listOf("engine_native.log", "engine_native.prev.log")
            for (name in candidates) {
                val logFile = File(filesDir, name)
                if (!logFile.exists()) continue
                // CRASH lines are appended by the native crash handler only
                // when a fatal signal was raised inside the in-process engine.
                val tail = logFile.readLines().takeLast(20)
                if (tail.any { it.startsWith("CRASH signal=") }) return true
            }
            // Also check if the autotune watchdog detected an autotuning crash
            // (based on checkpoint state and pending flag).
            if (autotuneWatchdog.hadCrashDuringAutotuning()) return true
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun parseWinrate(line: String): Float? {
        val m = Regex("""winrate (\d+\.\d+)""").find(line) ?: return null
        return m.groupValues[1].toFloatOrNull()?.coerceIn(0f, 1f)
    }

    private fun parseScoreLead(line: String): Float? {
        val m = Regex("""scoreLead (-?\d+\.\d+)""").find(line) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    private fun formatWinratePct(frac: Float): String {
        if (frac.isNaN()) return "0%"
        val pct = frac * 100f
        if (pct <= 0f) return "0%"
        var decimals = when {
            pct >= 10f -> 0
            pct >= 1f -> 1
            else -> 2
        }
        var s = String.format("%.${decimals}f", pct)
        while (s.toDoubleOrNull() == 0.0 && decimals < 6) {
            decimals++
            s = String.format("%.${decimals}f", pct)
        }
        return "$s%"
    }

    private fun syncCaptures() {
        capturedBlack = board.blackCaptures
        capturedWhite = board.whiteCaptures
    }

    fun isGpuEngine(so: String?): Boolean =
        so?.endsWith("libkatago_opencl.so") == true

    suspend fun send(cmd: String): String = engine.send(cmd)

    private suspend fun engineMove() {
        pendingHumanMove = null
        engineBusy.value = true
        winrate = null
        scoreLead = null
        try {
            var outcome = applyAiMove()
            if (outcome == AiMoveOutcome.FAILED && gameActive) {
                // Engine and local mirror drifted (e.g. the engine never got the
                // human's last move). Rebuild the engine position from the local
                // move list and ask again instead of leaving the game locked.
                syncEngineToLocal()
                outcome = applyAiMove()
            }
            if (outcome == AiMoveOutcome.FAILED) {
                gameActive = false
                statusText = getString(R.string.engine_error, "board out of sync with engine")
                return
            }
            if (outcome == AiMoveOutcome.RESIGNED) return
            syncCaptures()
            statusText = getString(R.string.your_move, side())
            winrateHistory.addLast(winrate)
            scoreLeadHistory.addLast(scoreLead)
            maybeFinishAfterMove()
            if (humanJustMoved) {
                humanJustMoved = false
                detectHumanMistake()
            }
            startHumanPositionAnalysis()
            } catch (e: Exception) {
                engineLoading = false
                gameActive = false
            statusText = getString(R.string.engine_error, e.message ?: "")
        } finally {
            engineBusy.value = false
        }
    }

    /**
     * Asks the engine for one move and applies it to the local mirror.
     *
     * @return [AiMoveOutcome.FAILED] if the engine's reply is an error, or its
     *         point is already occupied locally (mirror drift). [engineMove]
     *         resyncs and retries on that instead of silently locking the game.
     */
    private suspend fun applyAiMove(): AiMoveOutcome {
        return try {
            val reply = engine.send("kata-genmove_analyze ${engineColor.lowercaseChar()} 50")
            if (isErrorReply(reply)) return AiMoveOutcome.FAILED
            val coord = parseCoord(reply)
            when {
                coord == "resign" -> {
                    lastMove = null
                    finishAsWin()
                    AiMoveOutcome.RESIGNED
                }
                coord == "pass" -> {
                    board.forcePlay(-1, engineColor, pass = true)
                    lastMove = null
                    AiMoveOutcome.PASSED
                }
                else -> {
                    val v = fromGtp(coord)
                    if (v == null || board.isOccupied(v)) return AiMoveOutcome.FAILED
                    board.forcePlay(v, engineColor)
                    lastMove = v
                    AiMoveOutcome.PLAYED
                }
            }
        } catch (e: Exception) {
            AiMoveOutcome.FAILED
        }
    }

    /** Ends the game because the engine resigned: the human won. */
    private fun finishAsWin() {
        gameActive = false
        statusText = getString(R.string.you_won)
        gameOverTitle = getString(R.string.you_won)
        gameOverMessage = getString(R.string.opponent_resigned)
        showGameOverDialog = true
    }

    /** Restarts the game with the same model, size and colour as before. */
    fun rematch() {
        showGameOverDialog = false
        lastGameSetup?.let { startGame(it.modelFile, it.size, it.humanColor) }
    }

    /** Replays the whole local move list into the engine so both stay in sync. */
    private suspend fun syncEngineToLocal() {
        engine.send("clear_board")
        for (m in board.moves) {
            val r = engine.send("play ${m.color} ${board.toGtpCoord(m.vertex)}")
            if (isErrorReply(r)) throw IllegalStateException(r.trimEnd())
        }
    }

    private fun isErrorReply(reply: String): Boolean {
        val lines = reply.lines().map { it.trim() }
        val first = lines.firstOrNull { it.isNotEmpty() } ?: return true
        return first.startsWith("? ") || first.contains("ERR", ignoreCase = true)
    }

    fun colorName(): String = engineColor.lowercaseChar().toString()

    fun side(): String = if (engineColor == 'W') getString(R.string.side_black) else getString(R.string.side_white)

    private fun parseCoord(reply: String): String {
        val lines = reply.lines().map { it.trim() }
        // Standard GTP: "= D4". kata-genmove_analyze: header "=" already
        // consumed, reply body is "play D4" (gtp.cpp prefixes "play " when
        // args.analyzing) or just "D4".
        val body = lines.firstOrNull { it.startsWith("= ") || it.startsWith("? ") }
            ?.removePrefix("= ")
            ?.removePrefix("? ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: lines.firstOrNull { it.isNotEmpty() && !it.startsWith("info ") && it != "=" }
                ?: return "pass"
        return body.removePrefix("play ").trim().ifEmpty { "pass" }
    }

    /** Board tap handler with a two-step confirm: the first tap on an empty
     *  intersection shows a ghost-stone preview, the second tap on the same
     *  intersection places the stone (prevents accidental moves). */
    fun onBoardTap(vertex: Int) {
        if (replayingSave || !gameActive || engineBusy.value) {
            pendingHumanMove = null
            return
        }
        if (board.currentPlayer == engineColor) {
            pendingHumanMove = null
            return
        }
        if (board.grid.getOrNull(vertex) != ' ') {
            pendingHumanMove = null
            return
        }
        if (pendingHumanMove == vertex) {
            pendingHumanMove = null
            humanPlay(vertex)
        } else {
            pendingHumanMove = vertex
        }
    }

    fun humanPlay(vertex: Int) {
        pendingHumanMove = null
        if (replayingSave || !gameActive || engineBusy.value) return
        if (board.currentPlayer == engineColor) return
        val color = board.currentPlayer
        lastHumanBestMove = parseBestCoord(lastMistakeInfoLine)
        if (!board.play(vertex, color)) {
            statusText = getString(R.string.illegal_move, "illegal")
            return
        }
        lastMove = vertex
        syncCaptures()
        lifecycleScope.launch {
            engineBusy.value = true
            try {
                val reply = engine.send("play $color ${board.toGtpCoord(vertex)}")
                if (isErrorReply(reply)) {
                    board.popLast()
                    lastMove = board.moves.lastOrNull()?.takeIf { !it.pass }?.vertex
                    syncCaptures()
                    statusText = getString(R.string.illegal_move, reply.trimEnd())
                    return@launch
                }
                humanJustMoved = true
                if (gameActive) engineMove()
            } catch (e: Exception) {
                gameActive = false
                statusText = getString(R.string.engine_error, e.message ?: "")
            } finally {
                engineBusy.value = false
            }
        }
    }

    fun humanPass() {
        pendingHumanMove = null
        if (!gameActive || engineBusy.value) return
        if (board.currentPlayer == engineColor) return
        val color = board.currentPlayer
        board.play(-1, color)
        lastMove = null
        syncCaptures()
        lifecycleScope.launch {
            engineBusy.value = true
            try {
                engine.send("play $color pass")
                maybeFinishAfterMove()
                humanJustMoved = true
                if (gameActive) engineMove()
            } catch (e: Exception) {
                gameActive = false
                statusText = getString(R.string.engine_error, e.message ?: "")
            } finally {
                engineBusy.value = false
            }
        }
    }

    fun undo() {
        pendingHumanMove = null
        if (!gameActive || engineBusy.value) return
        lifecycleScope.launch {
            engineBusy.value = true
            try {
                engine.send("undo")
                engine.send("undo")
                board.popTwo()
                lastMove = board.moves.lastOrNull()?.takeIf { !it.pass }?.vertex
                syncCaptures()
                // Drop automatic explanations tied to the undone moves; the
                // user's own Q&A (moveNumber == null) is kept.
                val currentMoveNumber = board.moves.size
                aiChatHistory = aiChatHistory.filter {
                    it.moveNumber == null || it.moveNumber <= currentMoveNumber
                }
                aiCommentary = null
                winrateHistory.removeLastOrNull()
                scoreLeadHistory.removeLastOrNull()
                winrate = winrateHistory.lastOrNull()
                scoreLead = scoreLeadHistory.lastOrNull()
                if (gameActive && board.currentPlayer == engineColor) {
                    engineMove()
                } else {
                    statusText = getString(R.string.your_move, side())
                }
            } finally {
                engineBusy.value = false
            }
        }
    }

    fun newGame() {
        gameActive = false
        board.clear()
        lastMove = null
        winrate = null
        scoreLead = null
        winrateHistory.clear()
        scoreLeadHistory.clear()
        aiCommentary = null
        aiAnalyzing = false
        aiChatHistory = emptyList()
        pendingAiQuestion = null
        humanJustMoved = false
        aiContainer.mistakeDetector.reset()
        humanThinkMs = 0
        aiThinkMs = 0
        currentThinker = null
        gameElapsedMs = 0
        syncCaptures()
        statusText = ""
        engine.stop()
    }

    private suspend fun maybeFinishAfterMove() {
        val lastTwo = board.moves.takeLast(2)
        if (lastTwo.size == 2 && lastTwo.all { it.pass }) {
            val raw = engine.send("final_score")
            val v = raw.substringAfter("= ").trim()
            gameActive = false
            presentFinalResult(v)
        }
    }

    /** Shows the win/loss/draw dialog from KataGo's `final_score` reply
     *  ("B+7.5" / "W+3.5" black/white wins by points, or "0" for a draw),
     *  judged from the human's perspective. */
    private fun presentFinalResult(score: String) {
        val human = humanColor()
        val won: Boolean? = when {
            score.startsWith("B+") -> human == 'B'
            score.startsWith("W+") -> human == 'W'
            else -> null
        }
        statusText = getString(R.string.game_over) + " ($score)"
        gameOverTitle = when (won) {
            true -> getString(R.string.you_won)
            false -> getString(R.string.you_lost)
            null -> getString(R.string.game_draw)
        }
        val margin = score.removePrefix("B+").removePrefix("W+")
        gameOverMessage = when (won) {
            true -> getString(R.string.you_lead_by, margin)
            false -> getString(R.string.ai_leads_by, margin)
            null -> getString(R.string.game_draw_detail)
        }
        showGameOverDialog = true
    }

    fun fromGtp(coord: String): Int? {
        if (coord.length < 2) return null
        val col = "ABCDEFGHJKLMNOPQRST".indexOf(coord[0].uppercaseChar())
        if (col < 0 || col >= boardSize) return null
        val row = coord.substring(1).toIntOrNull() ?: return null
        val r = boardSize - row
        if (r < 0 || r >= boardSize) return null
        return r * boardSize + col
    }

    // ---- AI subsystem - UI helpers --------------------------------------

    private val aiProviderNames = mapOf(
        "groq" to "Groq",
        "google" to "Google Gemini",
        "openai" to "OpenAI",
        "anthropic" to "Claude",
        "deepseek" to "DeepSeek",
        "moonshot" to "Kimi",
        "qwen" to "Qwen",
        "openrouter" to "OpenRouter",
    )

    private fun refreshAiSettingsUi() {
        val s = aiContainer.globalSettings()
        aiSettingsUi = AiSettingsUiState(
            providers = ProviderFactory.KNOWN_PROVIDER_IDS.map { id ->
                val p = aiContainer.registry.get(id)
                val configured = aiContainer.configStore.hasKey(id)
                AiProviderUi(
                    id = id,
                    name = aiProviderNames[id] ?: id,
                    configured = configured,
                    statusLabel = providerStatusLabel(p?.health?.status, configured),
                )
            },
            global = s,
        )
        aiShowCommentary = s.showCommentary
        aiOfflineOnly = s.offlineOnly
        aiShowAiPlan = s.showAiPlan
    }

    private fun providerStatusLabel(status: ProviderStatus?, configured: Boolean): String =
        when {
            !configured -> getString(R.string.ai_status_not_configured)
            status == null -> getString(R.string.ai_status_not_configured)
            else -> when (status) {
                ProviderStatus.RATE_LIMITED -> getString(R.string.ai_status_rate_limited)
                ProviderStatus.QUOTA_EXCEEDED -> getString(R.string.ai_status_quota)
                ProviderStatus.AUTH_FAILED -> getString(R.string.ai_status_auth_failed)
                ProviderStatus.NETWORK_ERROR -> getString(R.string.ai_status_network)
                ProviderStatus.NOT_CONFIGURED -> getString(R.string.ai_status_not_configured)
                ProviderStatus.ERROR -> getString(R.string.ai_status_error)
                ProviderStatus.AVAILABLE -> getString(R.string.ai_status_available)
            }
        }

    private fun updateAiProvider(cfg: AiProviderConfig) {
        aiContainer.saveProviderConfig(cfg)
        refreshAiSettingsUi()
    }

    private fun updateAiGlobalSettings(settings: AiGlobalSettings) {
        aiContainer.saveGlobalSettings(settings)
        refreshAiSettingsUi()
    }

    private fun testAiProvider(providerId: String) {
        val cfg = aiContainer.config(providerId)
        if (cfg.apiKey.isBlank()) {
            Toast.makeText(this, R.string.ai_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        testAiDraft(cfg)
    }

    private fun testAiDraft(cfg: AiProviderConfig) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val provider = ProviderFactory.create(cfg) ?: throw IllegalStateException("unknown provider")
                    provider.testConnection()
                }
            }
            outcome.onSuccess { model ->
                aiContainer.registry.get(cfg.id)?.health?.onSuccess(0)
                refreshAiSettingsUi()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.ai_test_ok, model, "OK"),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { err ->
                val status = when (err) {
                    is AiException.AuthFailed -> ProviderStatus.AUTH_FAILED
                    is AiException.RateLimited -> ProviderStatus.RATE_LIMITED
                    is AiException.QuotaExceeded -> ProviderStatus.QUOTA_EXCEEDED
                    is AiException.NetworkError, is AiException.Timeout -> ProviderStatus.NETWORK_ERROR
                    else -> ProviderStatus.ERROR
                }
                aiContainer.registry.get(cfg.id)?.health?.onFailure(status)
                refreshAiSettingsUi()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.ai_test_fail, err.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun askAiQuestion(question: String) {
        val moveRecord = board.moves.joinToString(" ") {
            "${it.color} ${board.toGtpCoord(it.vertex)}"
        }
        val context = GoAiContext(
            moveNumber = board.moves.size,
            player = board.currentPlayer,
            moveCoord = lastMove?.let { board.toGtpCoord(it) } ?: "",
            bestCoord = parseBestCoord(lastMistakeInfoLine),
            pv = parsePv(lastMistakeInfoLine),
            winrateAfterPct = winrate?.let { it * 100f },
            scoreLead = scoreLead,
            boardText = GoAiPromptBuilder.boardToText(
                boardSize = board.size,
                grid = board.grid.toList(),
                moves = moveRecord.split(" ").filter { it.isNotBlank() },
                currentPlayer = board.currentPlayer,
                humanColor = humanColor(),
            ),
            boardJson = GoAiPromptBuilder.boardToJson(
                boardSize = board.size,
                grid = board.grid.toList(),
                moves = moveRecord.split(" ").filter { it.isNotBlank() },
                currentPlayer = board.currentPlayer,
                humanColor = humanColor(),
            ),
        )
        aiChatHistory = aiChatHistory + AiChatMessage("user", question)
        aiAnalyzing = true
        pendingAiQuestion = question
        lifecycleScope.launch {
            try {
                val result = aiContainer.coach.answer(question, context)
                aiRawRequest = aiContainer.coach.lastRawRequest
                aiRawResponse = aiContainer.coach.lastRawResponse
                val answer = if (result.error == null) {
                    result.suggestion.ifBlank { result.summary }
                } else {
                    result.error
                }
                val tips = extractTipVertices(answer, board)
                val clean = GoAiPromptBuilder.stripTips(answer)
                aiTipVertices = tips
                aiChatHistory = aiChatHistory + AiChatMessage(
                    role = "assistant",
                    content = clean,
                    boardSize = board.size,
                    boardGrid = board.grid.toList(),
                    tipVertices = tips,
                )
            } finally {
                aiAnalyzing = false
                pendingAiQuestion = null
            }
        }
    }

    /** Global analysis (guide mode): sends the full board as JSON and lets the
     *  model reason autonomously with no token / reasoning limits. */
    private fun askGlobalQuestion(question: String) {
        val moveRecord = board.moves.joinToString(" ") {
            "${it.color} ${board.toGtpCoord(it.vertex)}"
        }
        val context = GoAiContext(
            moveNumber = board.moves.size,
            player = board.currentPlayer,
            moveCoord = lastMove?.let { board.toGtpCoord(it) } ?: "",
            boardJson = GoAiPromptBuilder.boardToJson(
                boardSize = board.size,
                grid = board.grid.toList(),
                moves = moveRecord.split(" ").filter { it.isNotBlank() },
                currentPlayer = board.currentPlayer,
                humanColor = humanColor(),
            ),
        )
        aiChatHistory = aiChatHistory + AiChatMessage("user", question)
        aiAnalyzing = true
        pendingAiQuestion = question
        lifecycleScope.launch {
            try {
                val result = aiContainer.coach.answerGlobal(question, context)
                aiRawRequest = aiContainer.coach.lastRawRequest
                aiRawResponse = aiContainer.coach.lastRawResponse
                val answer = if (result.error == null) {
                    result.suggestion.ifBlank { result.summary }
                } else {
                    result.error
                }
                val tips = extractTipVertices(answer, board)
                val clean = GoAiPromptBuilder.stripTips(answer)
                aiTipVertices = tips
                aiChatHistory = aiChatHistory + AiChatMessage(
                    role = "assistant",
                    content = clean,
                    boardSize = board.size,
                    boardGrid = board.grid.toList(),
                    tipVertices = tips,
                )
            } finally {
                aiAnalyzing = false
                pendingAiQuestion = null
            }
        }
    }

    /**
     * Detects a human mistake from the winrate swing and schedules an AI
     * explanation through the coach. The recorded *after* winrate is the
     * engine's re-evaluation after replying to the human's move, which is a
     * practical approximation of the position winrate after the human move.
     */
    private fun detectHumanMistake() {
        val s = aiContainer.globalSettings()
        if (s.autoAnalysisMode == AiGlobalSettings.AutoAnalysisMode.OFF) return
        val mover = humanColor()
        val moveNumber = board.moves.size
        val wr = winrate ?: return
        if (!aiShowCommentary) return
        val event = aiContainer.mistakeDetector.onMove(
            player = mover,
            moveNumber = moveNumber,
            blackWinrateAfter = wr,
            blackScoreLeadAfter = scoreLead,
            bestCoord = lastHumanBestMove,
            pv = parsePv(lastMistakeInfoLine),
            phase = phaseFor(moveNumber),
        ) ?: run { return }
        aiAnalyzing = true
        aiContainer.coach.onGameAdvance(moveNumber)
        aiContainer.coach.enqueueExplain(event) { result ->
            aiCommentary = result
            aiAnalyzing = false
            appendCommentaryToChat(result, moveNumber)
        }
    }

    /** Appends an automatic explanation as an assistant bubble in the transcript. */
    private fun appendCommentaryToChat(result: AiCommentaryResult, moveNumber: Int) {
        if (result.error != null) return
        val raw = buildString {
            result.summary.takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
            result.reason.takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
            result.suggestion.takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim()
        val tips = extractTipVertices(raw, board)
        val clean = GoAiPromptBuilder.stripTips(raw)
        if (clean.isNotEmpty()) {
            aiTipVertices = tips
            aiChatHistory = aiChatHistory + AiChatMessage(
                role = "assistant",
                content = clean,
                moveNumber = moveNumber,
                boardSize = board.size,
                boardGrid = board.grid.toList(),
                tipVertices = tips,
            )
        }
    }

    private fun humanColor(): Char = if (engineColor == 'W') 'B' else 'W'

    /** In-flight background-analysis cancellation timer, so the GPU is not kept
     *  busy indefinitely while the human thinks. */
    private var humanAnalysisJob: Job? = null

    /**
     * Re-analyzes the current position (the human is about to move) so the
     * info stream reflects the human's best move rather than the engine's
     * pondering of its own reply. KataGo's pondering searches for *its* next
     * move on the human's turn, so its `info move` is the engine's own move;
     * a fresh analysis of the same position instead reports the best move
     * for the side to move (the human).
     *
     * Uses the cancellable variant so it never blocks the player: the search
     * runs in the background, and the moment the player makes a move the
     * engine preempts it with the "play" command and returns "play cancelled"
     * for the analysis, whose reply is never awaited. The search is also
     * interrupted after a short timeout so an idle human does not keep the
     * GPU crunching forever.
     */
    private fun startHumanPositionAnalysis() {
        if (!gameActive) return
        if (!aiShowCommentary) return
        if (aiContainer.globalSettings().autoAnalysisMode == AiGlobalSettings.AutoAnalysisMode.OFF) return
        humanAnalysisJob?.cancel()
        engine.sendAsync("kata-search_analyze_cancellable ${humanColor()} 50")
        humanAnalysisJob = lifecycleScope.launch {
            delay(HUMAN_ANALYSIS_TIMEOUT_MS)
            engine.interrupt()
        }
    }

    companion object {
        /** How long the background human-position analysis runs before being
         *  interrupted (a bare newline), so an idle player does not keep the
         *  GPU computing indefinitely. */
        private const val HUMAN_ANALYSIS_TIMEOUT_MS = 5_000L
    }

    private fun parseBestCoord(line: String): String? {
        if (line.isBlank()) return null
        val m = Regex("""info move\s+([A-Za-z][\dA-Za-z]*)""").find(line) ?: return null
        val c = m.groupValues[1].uppercase()
        return c.takeIf { it != "PASS" && it != "RESIGN" }
    }

    private fun parsePv(line: String): String? {
        if (line.isBlank()) return null
        val m = Regex("""pv\s+(\S+(?:\s+\S+){0,5})""").find(line) ?: return null
        return m.groupValues[1].uppercase()
    }

    /** Extracts suggested move vertices from an AI answer: first from [tip] markers,
     *  then falling back to bare GTP coordinates (e.g. "D4", "Q16") scattered in the text. */
    private fun extractTipVertices(text: String, board: BoardState): List<Int> {
        val marked = GoAiPromptBuilder.parseTips(text).mapNotNull { board.fromGtpCoord(it) }
        if (marked.isNotEmpty()) return marked
        // Fallback: scan for bare coordinates like D4 / Q16 / K10.
        val coordRegex = Regex("""\b([A-HJ-T])([1-9]|1[0-9])\b""")
        val found = coordRegex.findAll(text)
            .map { it.groupValues[1] + it.groupValues[2] }
            .mapNotNull { board.fromGtpCoord(it) }
            .distinct()
            .toList()
        return found
    }

    private fun phaseFor(moveNumber: Int): String = when {
        moveNumber < 20 -> getString(R.string.ai_phase_opening)
        moveNumber < 80 -> getString(R.string.ai_phase_middle)
        else -> getString(R.string.ai_phase_endgame)
    }

    // ---- UI ---------------------------------------------------------------

    @Composable
    fun Root() {
        val context = LocalContext.current
        val darkTheme = when (themePreference) {
            2 -> true
            1 -> false
            else -> isSystemInDarkTheme()
        }
        val view = LocalView.current
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
        FluentTheme(colors = if (darkTheme) darkColors() else lightColors()) {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Mica(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                androidx.compose.runtime.CompositionLocalProvider(LocalPersonalization provides personalization) {
                if (personalization.hasBackground) {
                    val uri = try { Uri.parse(personalization.backgroundUri) } catch (_: Exception) { null }
                    if (uri != null) {
                        val bitmap = remember(uri) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }?.asImageBitmap()
                            } catch (_: Exception) { null }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (personalization.blurRadius > 0f)
                                            Modifier.blur(radius = personalization.blurRadius.dp)
                                        else Modifier
                                    ),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = personalization.overlayAlpha)),
                            )
                        }
                    }
                }
                // Every time the Vulkan driver page is opened, if the
                // background-download exemption is still missing, remind the
                // user so the download does not get killed in the background.
                LaunchedEffect(showOnboarding && onboardingStep == OnboardingStep.Vulkan) {
                    if (showOnboarding && onboardingStep == OnboardingStep.Vulkan) {
                        val pm = getSystemService(PowerManager::class.java)
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) showBatteryPrompt = true
                    }
                }
                if (!showOnboarding) {
                    if (gameActive) {
                        GameScreen()
                    } else {
                    val pagerState = rememberPagerState(pageCount = { 2 })
                    val coroutineScope = rememberCoroutineScope()
                    NavigationView(
                        menuItems = {
                            menuItem(
                                selected = pagerState.currentPage == 0,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                text = { Text(stringResource(R.string.tab_play)) },
                                icon = { Icon(Icons.Filled.Home, null) },
                            )
                            menuItem(
                                selected = pagerState.currentPage == 1,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                text = { Text(stringResource(R.string.tab_settings)) },
                                icon = { Icon(Icons.Filled.Settings, null) },
                            )
                        },
                        modifier = Modifier.statusBarsPadding(),
                        displayMode = NavigationDisplayMode.Top,
                    ) {
                        HorizontalPager(state = pagerState) { page ->
                            when (page) {
                                0 -> HomeScreen(statusText)
                                else -> SettingsScreen(
                                    enginePreference = enginePreference,
                                    onEnginePreferenceChange = { enginePreference = it },
                                    forceGpu = forceGpu,
                                    onForceGpuChange = { forceGpu = it },
                                    gpuDisabled = gpuDisabled,
                                    clvkInstalled = clvkInstalled,
                                    maxVisits = maxVisits,
                                    onMaxVisitsChange = { maxVisits = it },
                                    maxTimeSec = maxTimeSec,
                                    onMaxTimeSecChange = { maxTimeSec = it },
                                    numThreads = numThreads,
                                    onNumThreadsChange = { numThreads = it },
                                    analysisPVLen = analysisPVLen,
                                    onAnalysisPVLenChange = { analysisPVLen = it },
                                    nnMaxBatchSize = nnMaxBatchSize,
                                    onNnMaxBatchSizeChange = { nnMaxBatchSize = it },
                                    allowResignation = allowResignation,
                                    onAllowResignationChange = { allowResignation = it },
                                    ponderingEnabled = ponderingEnabled,
                                    onPonderingEnabledChange = { ponderingEnabled = it },
                                    autoCopyTuneFiles = autoCopyTuneFiles,
                                    onAutoCopyTuneFilesChange = { autoCopyTuneFiles = it },
                                    themePreference = themePreference,
                                    onThemePreferenceChange = { themePreference = it },
                                    languagePreference = languagePreference,
                                    onLanguagePreferenceChange = { languagePreference = it },
                                    languageChangeEnabled = !(openClDownloading && !(showOnboarding && onboardingStep == OnboardingStep.Vulkan)),
                                    openClImportStatus = openClImportStatus,
                                    settingsRefreshTick = settingsRefreshTick,
                                    onSettingsRefreshTickChange = { settingsRefreshTick++ },
                                    onOpenClSetup = {
                                        showOnboarding = true
                                        onboardingFromSettings = true
                                        onboardingStep = OnboardingStep.Vulkan
                                    },
                                    recreate = ::recreate,
                                    onRestoreDefaults = ::restoreDefaultConfig,
                                    onExportConfig = ::exportConfig,
                                    onImportConfig = ::importConfig,
                                    aiSettings = aiSettingsUi,
                                    onAiGlobalChange = ::updateAiGlobalSettings,
                                    aiConfigOf = { id -> aiContainer.config(id) },
                                    onAiSaveProvider = ::updateAiProvider,
                                    onAiTestProvider = ::testAiProvider,
                                    onAiTestDraft = ::testAiDraft,
                                    personalization = personalization,
                                    onPersonalizationChange = { personalization = it },
                                )
                            }
                        }
                    }
                    val lastDiag = remember { mutableStateOf(gpuDiag) }
                    if (gpuDiag != null) lastDiag.value = gpuDiag
                    FluentDialog(
                        visible = gpuDiag != null,
                        onDismiss = { gpuDiag = null },
                        title = getString(R.string.engine_error_title),
                    ) {
                        lastDiag.value?.let { diag ->
                            Text(
                                diag,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SubtleButton(onClick = {
                                    context.saveDiagnosticLog(diag)
                                }) { Text(stringResource(R.string.save_log)) }
                                AccentButton(onClick = { gpuDiag = null }, modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text(stringResource(R.string.back))
                                }
                            }
                        }
                    }
                }
                }
                }
                AnimatedVisibility(
                    visible = showOnboarding,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(durationMillis = 300),
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 300),
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OnboardingScreen(
                        darkTheme = darkTheme,
                        clvkInstalled = clvkInstalled,
                        openClTransferProgress = openClTransferProgress,
                        openClTransferPhase = openClTransferPhase,
                        openClTransferSpeed = openClTransferSpeed,
                        openClDownloading = openClDownloading,
                        step = onboardingStep,
                        onStepChange = { onboardingStep = it },
                        fromSettings = onboardingFromSettings,
                        onImportOpenCl = ::importOpenClLib,
                        onDownloadOpenCl = ::downloadOpenClLib,
                        onCancelDownload = ::cancelOpenClDownload,
                        onFinished = ::finishOnboarding,
                    )
                }
                LoadingScreen(visible = engineLoading, message = engineLoadingMessage, onCancel = ::cancelEngineLoading)
                GameOverDialog(
                    visible = showGameOverDialog,
                    title = gameOverTitle,
                    message = gameOverMessage,
                    onPlayAgain = ::rematch,
                    onBackHome = {
                        showGameOverDialog = false
                        newGame()
                    },
                    onDismiss = { showGameOverDialog = false },
                )
                // top-right InfoBar: a cloud download is running outside the
                // Vulkan install step (including after onboarding was finished).
                if (openClDownloading && !(showOnboarding && onboardingStep == OnboardingStep.Vulkan)) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 8.dp, end = 8.dp)
                            .widthIn(max = 320.dp),
                    ) {
                        InfoBar(
                            title = {
                                Text(stringResource(R.string.onboarding_download_running))
                            },
                            message = {
                                val progress = openClTransferProgress ?: 0f
                                Text(
                                    text = stringResource(
                                        R.string.onboarding_transfer_percent,
                                        (progress * 100).toInt(),
                                    ),
                                    color = FluentTheme.colors.text.text.secondary,
                                )
                                val speed = openClTransferSpeed
                                if (openClTransferPhase == 1 && speed != null) {
                                    Text(
                                        text = formatSpeed(speed),
                                        color = FluentTheme.colors.text.text.secondary,
                                    )
                                }
                            },
                            severity = InfoBarSeverity.Informational,
                            closeAction = {
                                InfoBarDefaults.CloseActionButton(onClick = { showCancelOpenClDialog = true })
                            },
                        )
                    }
                }
                if (showCancelOpenClDialog) {
                    CancelDownloadDialog(
                        onDismiss = { showCancelOpenClDialog = false },
                        onConfirm = {
                            showCancelOpenClDialog = false
                            cancelOpenClDownload()
                        },
                    )
                }
                // Battery-optimization exemption prompt. The "Grant again"
                // flavour is shown when the user came back from the system
                // settings without actually granting the exemption.
                if (showBatteryPrompt) {
                    FluentDialog(
                        visible = showBatteryPrompt,
                        onDismiss = { showBatteryPrompt = false },
                        title = stringResource(
                            if (batteryPromptJustRequested) R.string.battery_permission_again_title
                            else R.string.battery_permission_title
                        ),
                    ) {
                        Text(
                            stringResource(
                                if (batteryPromptJustRequested) R.string.battery_permission_again_message
                                else R.string.battery_permission_message
                            )
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SubtleButton(onClick = { showBatteryPrompt = false }) {
                                Text(stringResource(R.string.battery_permission_later))
                            }
                            AccentButton(onClick = {
                                batteryPromptJustRequested = true
                                showBatteryPrompt = false
                                requestBatteryOptimizationExemption()
                            }) {
                                Text(
                                    stringResource(
                                        if (batteryPromptJustRequested) R.string.battery_permission_again_confirm
                                        else R.string.battery_permission_request
                                    )
                                )
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }

    @Composable
    fun StorageAccessDialog(
        visible: Boolean,
        onDismiss: () -> Unit,
        onGranted: () -> Unit = {},
    ) {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= 30) {
            FluentDialog(
                visible = visible,
                onDismiss = onDismiss,
                title = stringResource(R.string.storage_denied),
            ) {
                Text(stringResource(R.string.import_title))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SubtleButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                    AccentButton(onClick = {
                        onDismiss()
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text(stringResource(R.string.grant_access)) }
                }
            }
        } else {
            val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) onGranted()
            }
            FluentDialog(
                visible = visible,
                onDismiss = onDismiss,
                title = stringResource(R.string.storage_denied),
            ) {
                Text(stringResource(R.string.import_title))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SubtleButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                    AccentButton(onClick = {
                        onDismiss()
                        perms.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }) { Text(stringResource(R.string.grant_access)) }
                }
            }
        }
    }

    @Composable
    fun HomeScreen(hint: String) {
        val context = LocalContext.current
        var showImport by rememberSaveable { mutableStateOf(false) }
        var showNetImport by rememberSaveable { mutableStateOf(false) }
        var showPlayFor by remember { mutableStateOf<File?>(null) }
        var showSavePicker by remember { mutableStateOf(false) }
        var saveListTick by remember { mutableStateOf(0) }
        var fileToDelete by remember { mutableStateOf<File?>(null) }
        val deleteFileName = remember { mutableStateOf("") }
        LaunchedEffect(fileToDelete) {
            if (fileToDelete != null) deleteFileName.value = fileToDelete?.name ?: ""
        }

        val hasStorage = ModelStore.hasStorageAccess(context)
        LaunchedEffect(hasStorage) {
            if (hasStorage) refreshModels()
        }

        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) chooseModel(uri)
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (hint.isNotEmpty()) {
                    Text(hint)
                    Spacer(Modifier.height(12.dp))
                }
                if (models.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.models_empty))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showImport = true }) { Text(stringResource(R.string.action_import)) }
                            Button(onClick = { showNetImport = true }) { Text(stringResource(R.string.network_import)) }
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.models_title), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        models.forEach { file ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .combinedClickable(
                                        onClick = { showPlayFor = file },
                                        onLongClick = { fileToDelete = file },
                                    )
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(formatSize(file.length()))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { showPlayFor = file }) { Text(stringResource(R.string.action_play)) }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { showImport = true }) { Text(stringResource(R.string.action_import)) }
                            Button(onClick = { showNetImport = true }) { Text(stringResource(R.string.network_import)) }
                            SubtleButton(onClick = { refreshModels() }) { Text(stringResource(R.string.action_refresh)) }
                        }
                    }
                }
            }

            val needsAccess = !ModelStore.hasStorageAccess(this@MainActivity)
            if (showImport && !needsAccess) {
                showImport = false
                importPicker.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream", "text/plain"))
            }
            StorageAccessDialog(
                visible = showImport,
                onDismiss = { showImport = false },
                onGranted = ::refreshModels,
            )

            PlayDialog(
                visible = showPlayFor != null,
                model = showPlayFor?.name ?: "",
                onDismiss = { showPlayFor = null },
                onConfirm = { size, color ->
                    showPlayFor?.let { model ->
                        startGame(model, size, color)
                    }
                    showPlayFor = null
                },
                onLoad = {
                    saveListTick++
                    showSavePicker = true
                },
            )

            if (showSavePicker) BackHandler { showSavePicker = false }

            SavePickerScreen(
                visible = showSavePicker,
                saves = remember(saveListTick) { saveStore.list() },
                onPick = { saved ->
                    showSavePicker = false
                    showPlayFor?.let { model ->
                        val humanColor = if (saved.engineColor == 'W') "black" else "white"
                        startGame(
                            model,
                            saved.boardSize,
                            humanColor,
                            replay = saved.moves,
                            humanThinkMs0 = saved.humanThinkMs,
                            aiThinkMs0 = saved.aiThinkMs,
                            gameElapsedMs0 = saved.gameElapsedMs,
                            chatHistory0 = saved.chatHistory,
                        )
                        Toast.makeText(this@MainActivity, getString(R.string.game_loaded), Toast.LENGTH_SHORT).show()
                    }
                    showPlayFor = null
                },
                onDelete = { saved ->
                    saveStore.delete(saved)
                    saveListTick++
                },
                onDismiss = { showSavePicker = false },
            )

            FluentDialog(
                visible = fileToDelete != null,
                onDismiss = { fileToDelete = null },
                title = stringResource(R.string.delete_model_title),
            ) {
                Text(stringResource(R.string.delete_model_confirm, deleteFileName.value))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SubtleButton(onClick = { fileToDelete = null }) { Text(stringResource(R.string.back)) }
                    AccentButton(onClick = {
                        fileToDelete?.let { file ->
                            if (file.delete()) {
                                refreshModels()
                            }
                        }
                        fileToDelete = null
                    }) { Text(stringResource(R.string.delete)) }
                }
            }

            NetImportScreen(
                visible = showNetImport,
                onBack = { showNetImport = false },
            )
        }
    }

    @Composable
    fun NetImportScreen(visible: Boolean, onBack: () -> Unit) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            NetImportContent(onBack = onBack)
        }
    }

    @Composable
    private fun NetImportContent(onBack: () -> Unit) {
        BackHandler { onBack() }
        val context = LocalContext.current
        var models by remember { mutableStateOf(listOf<NetworkModel>()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf("") }
        var downloadedTick by remember { mutableIntStateOf(0) }
        val progress = remember { mutableStateMapOf<String, Float>() }
        val active = remember { mutableStateMapOf<String, Boolean>() }
        val downloadedBytes = remember { mutableStateMapOf<String, Long>() }
        val totalBytes = remember { mutableStateMapOf<String, Long>() }
        val jobs = remember { mutableStateMapOf<String, Job>() }
        var toDelete by remember { mutableStateOf<NetworkModel?>(null) }
        var selectedTag by remember { mutableStateOf("") }
        var showStorageDialog by remember { mutableStateOf(false) }
        var pendingDownload by remember { mutableStateOf<NetworkModel?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            models = NetworkStore.loadCache(context)
            loading = true
            try {
                val list = withContext(Dispatchers.IO) { NetworkStore.fetchModels(context) }
                if (list.isNotEmpty()) models = list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = getString(R.string.net_fetch_failed)
            }
            loading = false
        }

        fun startDownload(model: NetworkModel) {
            if (!ModelStore.hasStorageAccess(context)) {
                pendingDownload = model
                showStorageDialog = true
                return
            }
            if (jobs.containsKey(model.url)) return
            if (NetworkStore.installedFile(model).exists()) {
                downloadedTick++
                return
            }
            val job = scope.launch {
                active[model.url] = true
                var last = -1f
                try {
                    val file = NetworkStore.download(context, model) { done, total ->
                        downloadedBytes[model.url] = done
                        totalBytes[model.url] = total
                        val frac = if (total > 0) done.toFloat() / total.toFloat() else 0f
                        if (frac >= 1f || frac - last >= 0.005f) {
                            last = frac
                            progress[model.url] = frac
                        }
                    }
                    if (file != null) {
                        downloadedTick++
                        refreshModels()
                        Toast.makeText(this@MainActivity, getString(R.string.net_download_ok, file.name), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.net_download_fail, model.name), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.net_download_fail, model.name), Toast.LENGTH_SHORT).show()
                } finally {
                    active[model.url] = false
                    progress.remove(model.url)
                    downloadedBytes.remove(model.url)
                    totalBytes.remove(model.url)
                    jobs.remove(model.url)
                }
            }
            jobs[model.url] = job
        }

        fun cancelDownload(model: NetworkModel) {
            jobs[model.url]?.cancel()
            jobs.remove(model.url)
            active[model.url] = false
            progress.remove(model.url)
            downloadedBytes.remove(model.url)
            totalBytes.remove(model.url)
        }

        Box(Modifier.fillMaxSize().background(FluentTheme.colors.background.mica.base)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.net_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    SubtleButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    models.isEmpty() && loading -> Text(stringResource(R.string.net_refreshing))
                    models.isEmpty() && error.isNotEmpty() -> Text(error)
                    models.isEmpty() -> Text(stringResource(R.string.net_no_models))
                    else -> {
                        val tags = remember(models) {
                            models.map { it.tag }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .sortedBy { it.substring(1).toIntOrNull() ?: Int.MAX_VALUE }
                        }
                        Column {
                            val allTags = listOf("") + tags
                            LiteFilter(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                allTags.forEach { tag ->
                                    val label = if (tag.isEmpty()) stringResource(R.string.net_all) else tag
                                    val selected = selectedTag == tag
                                    PillButton(
                                        selected = selected,
                                        onSelectedChanged = { selectedTag = if (selectedTag != tag) tag else "" },
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val filtered = remember(models, selectedTag) {
                                if (selectedTag.isEmpty()) models
                                else models.filter { it.tag == selectedTag }
                            }
                            val listState = rememberLazyListState()
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(filtered, key = { it.url }) { model ->
                                        val isDownloaded = remember(model.name, downloadedTick) {
                                            NetworkStore.installedFile(model).exists()
                                        }
                                        val isActive = active[model.url] == true
                                        val p = progress[model.url] ?: 0f
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            model.name,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            stringResource(R.string.net_rating, model.rating.toInt(), model.date.take(10)),
                                                            fontSize = 12.sp,
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    if (isDownloaded) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Text(
                                                                stringResource(R.string.net_downloaded),
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.align(Alignment.CenterVertically),
                                                            )
                                                            SubtleButton(onClick = { toDelete = model }) {
                                                                Text(stringResource(R.string.delete))
                                                            }
                                                        }
                                                    } else if (isActive) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            SubtleButton(onClick = { cancelDownload(model) }) {
                                                                Text(stringResource(R.string.net_cancel))
                                                            }
                                                            Button(
                                                                onClick = {},
                                                                disabled = true,
                                                            ) {
                                                                Text(stringResource(R.string.net_downloading, (p * 100).roundToInt()))
                                                            }
                                                        }
                                                    } else {
                                                        Button(onClick = { startDownload(model) }) {
                                                            Text(stringResource(R.string.net_download))
                                                        }
                                                    }
                                                }
                                                if (isActive) {
                                                    Spacer(Modifier.height(8.dp))
                                                    ProgressBar(
                                                        progress = p,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    val dl = downloadedBytes[model.url] ?: 0L
                                                    val tot = totalBytes[model.url] ?: 0L
                                                    Text(
                                                        if (tot > 0) "${formatSize(dl)} / ${formatSize(tot)}"
                                                        else formatSize(dl),
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                FluentVScrollbar(listState)
                            }
                        }
                    }
                }
            }

            FluentDialog(
                visible = toDelete != null,
                onDismiss = { toDelete = null },
                title = stringResource(R.string.delete_model_title),
            ) {
                Text(stringResource(R.string.delete_model_confirm, toDelete?.name ?: ""))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SubtleButton(onClick = { toDelete = null }) { Text(stringResource(R.string.back)) }
                    AccentButton(onClick = {
                        toDelete?.let { model ->
                            val file = NetworkStore.installedFile(model)
                            if (file.delete()) {
                                downloadedTick++
                                refreshModels()
                                Toast.makeText(this@MainActivity, getString(R.string.net_deleted, file.name), Toast.LENGTH_SHORT).show()
                            }
                        }
                        toDelete = null
                    }) { Text(stringResource(R.string.delete)) }
                }
            }

            StorageAccessDialog(
                visible = showStorageDialog,
                onDismiss = { showStorageDialog = false },
                onGranted = {
                    pendingDownload?.let { startDownload(it) }
                    pendingDownload = null
                },
            )
        }
    }

    @Composable
    fun FluentVScrollbar(state: LazyListState) {
        BoxWithConstraints(Modifier.fillMaxHeight().width(8.dp)) {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            if (vis.isEmpty() || info.totalItemsCount <= 1) return@BoxWithConstraints
            val firstIndex = vis.first().index
            val lastIndex = vis.last().index
            val viewportItems = (lastIndex - firstIndex + 1).coerceAtLeast(1)
            if (info.totalItemsCount <= viewportItems) return@BoxWithConstraints
            val thumbHeight = (maxHeight * viewportItems / info.totalItemsCount).coerceAtLeast(16.dp)
            val scrollFraction = firstIndex.toFloat() /
                (info.totalItemsCount - viewportItems).toFloat().coerceAtLeast(1f)
            val thumbTop = (maxHeight - thumbHeight) * scrollFraction.coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset { IntOffset(0, thumbTop.roundToPx()) }
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FluentTheme.colors.controlAlt.secondary)
            )
        }
    }

    @Composable
    fun FluentHScrollbar(state: ScrollState) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
            if (state.maxValue <= 0) return@BoxWithConstraints
            val viewport = maxWidth.value
            val content = viewport + state.maxValue.toFloat()
            val thumbWidth = maxWidth.times(viewport / content).coerceAtLeast(16.dp)
            val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
            val thumbLeft = (maxWidth - thumbWidth) * scrollFraction.coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset { IntOffset(thumbLeft.roundToPx(), 0) }
                    .fillMaxHeight()
                    .width(thumbWidth)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FluentTheme.colors.controlAlt.secondary)
            )
        }
    }

    @Composable
    fun FluentDialog(
        visible: Boolean,
        onDismiss: () -> Unit,
        title: String,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Use the explicit-color Card overload: the no-onClick overload paints
                // with background.layer.default, which is translucent (designed to sit
                // on Mica/Acrylic). A dialog must be opaque so it doesn't bleed through.
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
                ) {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        cardColors = CardDefaults.cardColors(
                            default = CardColor(
                                fillColor = FluentTheme.colors.background.solid.quaternary,
                                contentColor = FluentTheme.colors.text.text.primary,
                                borderBrush = SolidColor(FluentTheme.colors.stroke.card.defaultSolid),
                            ),
                        ),
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            content()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PlayDialog(visible: Boolean, model: String, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit, onLoad: () -> Unit) {
        var size by remember { mutableIntStateOf(19) }
        var color by remember { mutableStateOf("black") }
        val lastModel = remember { mutableStateOf(model) }
        if (model.isNotEmpty()) lastModel.value = model
        FluentDialog(visible = visible, onDismiss = onDismiss, title = stringResource(R.string.board_size)) {
            Text(stringResource(R.string.model_colon, lastModel.value))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(9, 13, 19).forEach { s ->
                    if (size == s) {
                        AccentButton(onClick = { size = s }) { Text(s.toString()) }
                    } else {
                        SubtleButton(onClick = { size = s }) { Text(s.toString()) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SubtleButton(onClick = { color = if (color == "black") "white" else "black" }) {
                Text(if (color == "black") stringResource(R.string.you_play_black) else stringResource(R.string.you_play_white))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SubtleButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtleButton(onClick = onLoad) { Text(stringResource(R.string.load_game)) }
                    AccentButton(onClick = { onConfirm(size, color) }) { Text(stringResource(R.string.action_play)) }
                }
            }
        }
    }

    @Composable
    fun GameScreen() {
        val busy by engineBusy.collectAsState()
        var showExitConfirm by remember { mutableStateOf(false) }
        var guideMode by remember { mutableStateOf(false) }
        val pagerState = rememberPagerState(pageCount = { 2 })

        BackHandler(enabled = gameActive) {
            if (guideMode) {
                guideMode = false
            } else if (!busy) {
                showExitConfirm = true
            }
        }
        LaunchedEffect(guideMode) {
            pagerState.animateScrollToPage(
                if (guideMode) 1 else 0,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
        }

        LaunchedEffect(gameActive) {
            while (gameActive) {
                delay(200)
                gameElapsedMs += 200
                val thinker = if (engineBusy.value) 'A' else 'H'
                if (thinker != currentThinker) {
                    currentThinker = thinker
                    if (thinker == 'A') aiThinkMs = 0 else humanThinkMs = 0
                }
                if (thinker == 'A') aiThinkMs += 200 else humanThinkMs += 200
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubtleButton(onClick = { if (!busy) showExitConfirm = true }, disabled = busy) { Text(stringResource(R.string.back)) }
                Text("${boardSize} ${if (engineMode == "gpu") "· GPU" else "· CPU"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtleButton(onClick = { saveGame() }, disabled = busy) { Text(stringResource(R.string.save_game)) }
                    SubtleButton(onClick = { newGame() }, disabled = busy) { Text(stringResource(R.string.new_game)) }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = guideMode,
            ) { page ->
                if (page == 0) {
                    Column(Modifier.fillMaxSize()) {
                        Text(statusText, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.cap_black, capturedBlack))
                            Text(stringResource(R.string.cap_white, capturedWhite))
                        }
                        Spacer(Modifier.height(8.dp))
                        val plan = aiPlanVertex
                        BoardView(
                            board = board,
                            lastMove = lastMove,
                            previewVertex = pendingHumanMove,
                            aiPlanVertex = if (aiShowAiPlan && engineBusy.value &&
                                board.currentPlayer == engineColor &&
                                plan != null && board.grid[plan] == ' '
                            ) plan else null,
                            onTap = { vertex -> onBoardTap(vertex) },
                            tipVertices = if (guideMode) aiTipVertices else emptyList(),
                            modifier = if (personalization.hasBackground) Modifier.graphicsLayer { alpha = personalization.boardAlpha } else Modifier,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { humanPass() }, modifier = Modifier.weight(1f), disabled = busy) {
                                Text(stringResource(R.string.pass))
                            }
                            SubtleButton(onClick = { undo() }, modifier = Modifier.weight(1f), disabled = busy) {
                                Text(stringResource(R.string.undo))
                            }
                        }
                        if (busy) {
                            Text(
                                stringResource(R.string.thinking),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (gameActive) {
                            AnimatedVisibility(
                                visible = !guideMode && aiShowCommentary,
                                enter = fadeIn(tween(450, easing = FastOutSlowInEasing)) + slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 2 },
                                exit = fadeOut(tween(350, easing = FastOutSlowInEasing)) + slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it / 2 },
                            ) {
                                AiCommentaryPanel(
                                    commentary = aiCommentary,
                                    loading = aiAnalyzing,
                                    visible = true,
                                    offlineMode = aiOfflineOnly,
                                    chatHistory = aiChatHistory,
                                    pendingQuestion = pendingAiQuestion,
                                    guideMode = guideMode,
                                    onToggleGuide = { guideMode = it },
                                    onAskQuestion = ::askAiQuestion,
                                    onClearChat = { aiChatHistory = emptyList() },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            PkWinrateBar()
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    AiGuideScreen(
                        commentary = aiCommentary,
                        loading = aiAnalyzing,
                        offlineMode = aiOfflineOnly,
                        chatHistory = aiChatHistory,
                        pendingQuestion = pendingAiQuestion,
                        guideMode = guideMode,
                        onToggleGuide = { guideMode = it },
                        onAskQuestion = ::askGlobalQuestion,
                        onClearChat = { aiChatHistory = emptyList() },
                        rawRequest = aiRawRequest,
                        rawResponse = aiRawResponse,
                    )
                }
            }
        }

        ContentDialog(
            title = stringResource(R.string.exit_game_confirm_title),
            visible = showExitConfirm,
            size = DialogSize.Max,
            primaryButtonText = stringResource(R.string.exit_game_confirm_primary),
            closeButtonText = stringResource(R.string.exit_game_confirm_close),
            onButtonClick = { button ->
                showExitConfirm = false
                when (button) {
                    ContentDialogButton.Primary -> newGame()
                    else -> {}
                }
            },
            content = {
                Text(stringResource(R.string.exit_game_confirm_message))
            },
        )
    }

    @Composable
    private fun PkWinrateBar() {
        val humanColor = if (engineColor == 'W') 'B' else 'W'
        val aiColor = engineColor
        val dark = when (themePreference) {
            2 -> true
            1 -> false
            else -> isSystemInDarkTheme()
        }
        val humanWin = when {
            winrate == null -> 0.5f
            humanColor == 'B' -> winrate!!.coerceIn(0f, 1f)
            else -> 1f - winrate!!.coerceIn(0f, 1f)
        }
        val humanPctText = formatWinratePct(humanWin)
        val aiPctText = formatWinratePct(1f - humanWin)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PkSide(
                color = humanColor,
                name = getString(R.string.player_name),
                thinkMs = humanThinkMs,
                dark = dark,
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1.6f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(humanPctText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(aiPctText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(10.dp)
                        .background(Color(0x20000000), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier.fillMaxWidth(humanWin.coerceIn(0f, 1f)).fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(stoneColor(humanColor), RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    )
                    Box(
                        Modifier.fillMaxWidth((1f - humanWin).coerceIn(0f, 1f)).fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .background(stoneColor(aiColor), RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                    )
                }
                Spacer(Modifier.height(4.dp))
                val leadText = scoreLead?.let { lead ->
                    val humanLead = if (humanColor == 'B') lead else -lead
                    val absLead = String.format("%.1f", kotlin.math.abs(humanLead))
                    when {
                        humanLead > 0.05f -> stringResource(R.string.lead_ahead, absLead)
                        humanLead < -0.05f -> stringResource(R.string.lead_behind, absLead)
                        else -> stringResource(R.string.lead_even)
                    }
                } ?: "-"
                Text(
                    stringResource(R.string.ai_moves_score, board.moves.size, leadText),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    stringResource(R.string.game_time, formatClock(gameElapsedMs)),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            PkSide(
                color = aiColor,
                name = getString(R.string.ai_name),
                thinkMs = aiThinkMs,
                dark = dark,
                modifier = Modifier.weight(1f),
            )
        }
    }

    @Composable
    private fun PkSide(color: Char, name: String, thinkMs: Long, dark: Boolean, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(stoneColor(color))
                    .border(2.dp, stoneBorder(color, dark), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (color == 'B') Color.White else Color(0xFF444444),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape)
                    .background(stoneColor(color))
                    .border(1.5.dp, stoneBorder(color, dark), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (color == 'B') R.string.side_black else R.string.side_white), fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(formatClock(thinkMs), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    private fun stoneColor(color: Char): Color =
        if (color == 'B') Color(0xFF1B1B1B) else Color(0xFFE8E8E8)

    /** Contrast ring so stones stay visible: dark theme outlines black, light theme outlines white. */
    private fun stoneBorder(color: Char, dark: Boolean): Color =
        if (dark) {
            if (color == 'B') Color(0xFFFFFFFF) else Color(0x22000000)
        } else {
            if (color == 'B') Color(0x44000000) else Color(0xFF666666)
        }

    private fun formatClock(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun formatSize(bytes: Long): String =
        when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
            else -> "%.1f KB".format(bytes.toDouble() / 1024)
        }
}