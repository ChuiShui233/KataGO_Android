package com.chuishui.katago

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Icon
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.ArrowLeft
import io.github.composefluent.icons.filled.Checkmark
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Final "ready" step. Tapping the button (or swiping down anywhere) slides the
 * whole onboarding flow off the bottom of the screen, then calls [onFinished].
 */
@Composable
internal fun OnboardingReadyStep(
    darkTheme: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetY = remember { Animatable(0f) }
    val screenHeightPx = with(density) { 1080.dp.toPx() }

    fun dismiss() {
        scope.launch {
            offsetY.animateTo(screenHeightPx, animationSpec = tween(280))
            onFinished()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > screenHeightPx * 0.15f) dismiss()
                        else scope.launch { offsetY.animateTo(0f, animationSpec = tween(180)) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val target = (offsetY.value + dragAmount).coerceAtLeast(0f)
                    scope.launch { offsetY.snapTo(target) }
                }
            },
    ) {
        // top-left: back button (icon only)
        SubtleButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowLeft, stringResource(R.string.onboarding_back))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Checkmark,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = FluentTheme.colors.fillAccent.default,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_ready_title),
                    style = FluentTheme.typography.title,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.text.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_ready_subtitle),
                    style = FluentTheme.typography.body,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.text.secondary,
                )
            }
        }
        AccentButton(
            onClick = { dismiss() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Text(stringResource(R.string.onboarding_ready_cta))
        }
    }
}