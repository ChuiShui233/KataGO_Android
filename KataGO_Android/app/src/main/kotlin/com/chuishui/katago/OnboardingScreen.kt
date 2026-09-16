package com.chuishui.katago

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.composefluent.FluentTheme

/** The three steps of the first-run flow. */
internal enum class OnboardingStep { Welcome, Vulkan, Ready }

/**
 * First-run flow: welcome cards, then a Vulkan-driver install step (local
 * import or cloud download with real-time progress), then a "ready" screen
 * that dismisses by sliding down.
 */
@Composable
internal fun OnboardingScreen(
    darkTheme: Boolean,
    clvkInstalled: Boolean,
    isBuiltinClvk: Boolean = false,
    openClTransferProgress: Float?,
    openClTransferPhase: Int,
    openClTransferSpeed: Float?,
    openClDownloading: Boolean,
    step: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    fromSettings: Boolean,
    onImportOpenCl: (Context, Uri) -> Unit,
    onDownloadOpenCl: () -> Unit,
    onCancelDownload: () -> Unit,
    onFinished: () -> Unit,
) {
    var vulkanReady by remember { mutableStateOf(clvkInstalled) }
    var showSkipDialog by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    // Whether the driver was already installed when this flow started. When it
    // is, opening the install page from settings must not auto-leave it; only a
    // fresh install completed during this flow should advance the flow.
    var wasAlreadyInstalled by remember { mutableStateOf(clvkInstalled) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportOpenCl(context, uri)
    }

    // once a clvk transfer finishes successfully, unlock the next button
    if (clvkInstalled) vulkanReady = true

    // after a successful install, leave the flow: jump to "ready" on first run,
    // or straight back to settings when opened from the settings screen.
    // Skip when the driver was already installed before this flow was opened.
    LaunchedEffect(clvkInstalled) {
        if (clvkInstalled && !wasAlreadyInstalled && step == OnboardingStep.Vulkan && !dismissing) {
            if (fromSettings) {
                onFinished()
            } else {
                onStepChange(OnboardingStep.Ready)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(FluentTheme.colors.background.mica.base)) {
        if (!fromSettings) {
            AnimatedVisibility(
                visible = step == OnboardingStep.Welcome && !dismissing,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)) + slideOutVertically { it },
            ) {
                OnboardingWelcomeStep(
                    darkTheme = darkTheme,
                    onNext = {
                        if (isBuiltinClvk) onStepChange(OnboardingStep.Ready)
                        else onStepChange(OnboardingStep.Vulkan)
                    },
                )
            }
        }
        // Drive the driver page via a transition state so the slide-up animation
        // also plays when it is opened directly from the settings screen.
        // Built-in driver hides the Vulkan step entirely.
        val showVulkan = step == OnboardingStep.Vulkan && !dismissing && !isBuiltinClvk
        val vulkanTransition = remember { MutableTransitionState(false) }
        LaunchedEffect(showVulkan) {
            vulkanTransition.targetState = showVulkan
        }
        AnimatedVisibility(
            visibleState = vulkanTransition,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
        ) {
            OnboardingVulkanStep(
                darkTheme = darkTheme,
                clvkInstalled = vulkanReady,
                openClTransferProgress = openClTransferProgress,
                openClTransferPhase = openClTransferPhase,
                openClTransferSpeed = openClTransferSpeed,
                openClDownloading = openClDownloading,
                showBack = !fromSettings,
                onBack = { onStepChange(OnboardingStep.Welcome) },
                onImportClick = { importPicker.launch(arrayOf("*/*")) },
                onDownloadClick = onDownloadOpenCl,
                onCancelDownload = onCancelDownload,
                onSkipClick = {
                    if (fromSettings) {
                        onFinished()
                    } else {
                        showSkipDialog = true
                    }
                },
                onNext = {
                    if (fromSettings) {
                        onFinished()
                    } else {
                        onStepChange(OnboardingStep.Ready)
                    }
                },
            )
        }
        if (!fromSettings) {
            AnimatedVisibility(
                visible = step == OnboardingStep.Ready && !dismissing,
                enter = fadeIn(tween(250)) + slideInVertically { it },
                exit = fadeOut(tween(200)) + slideOutVertically { it },
            ) {
                OnboardingReadyStep(
                    darkTheme = darkTheme,
                    onBack = {
                        if (isBuiltinClvk) onStepChange(OnboardingStep.Welcome)
                        else onStepChange(OnboardingStep.Vulkan)
                    },
                    onFinished = {
                        dismissing = true
                        onFinished()
                    },
                )
            }
        }
    }

    if (showSkipDialog) {
        SkipConfirmationDialog(
            onDismiss = { showSkipDialog = false },
            onConfirm = {
                showSkipDialog = false
                onStepChange(OnboardingStep.Ready)
            },
        )
    }

    // system back / gesture: step back through the flow instead of exiting.
    // From settings there is no back step, so back returns to settings.
    BackHandler(enabled = !dismissing && (fromSettings || step != OnboardingStep.Welcome)) {
        when {
            showSkipDialog -> showSkipDialog = false
            fromSettings -> onFinished()
            step == OnboardingStep.Ready -> if (isBuiltinClvk) onStepChange(OnboardingStep.Welcome) else onStepChange(OnboardingStep.Vulkan)
            else -> onStepChange(OnboardingStep.Welcome)
        }
    }
}