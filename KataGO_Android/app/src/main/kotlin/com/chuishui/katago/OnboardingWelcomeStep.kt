package com.chuishui.katago

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.ChatSparkle

/** Welcome step: gradient banner, feature cards and a "next" button. */
@Composable
internal fun OnboardingWelcomeStep(
    darkTheme: Boolean,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val gradient = if (darkTheme) {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xff1A212C),
                        Color(0xff2C343C),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xffCCD7E8),
                        Color(0xffDAE9F7),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp)
                    .border(1.dp, FluentTheme.colors.stroke.card.default, shape = FluentTheme.shapes.control)
                    .clip(FluentTheme.shapes.control)
                    .background(gradient),
            ) {
                GoBoardBanner(darkTheme = darkTheme, modifier = Modifier.blur(radius = 10.dp).alpha(0.5f))
                Column(
                    Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = FluentTheme.typography.title,
                        textAlign = TextAlign.End,
                        color = FluentTheme.colors.text.text.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "v${rememberVersionName()}",
                        style = FluentTheme.typography.body,
                        textAlign = TextAlign.End,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }

            val cards = listOf(
                Triple(
                    painterResource(R.drawable.jetpack_compose_logo),
                    "KataGo for Android",
                    stringResource(R.string.onboarding_feature_play_desc),
                ),
                Triple(
                    painterResource(R.drawable.github_logo),
                    stringResource(R.string.onboarding_feature_engine_title),
                    stringResource(R.string.onboarding_feature_engine_desc),
                ),
                Triple(
                    Icons.Filled.ChatSparkle,
                    stringResource(R.string.onboarding_feature_ai_title),
                    stringResource(R.string.onboarding_feature_ai_desc),
                ),
            )
            val transition = remember { MutableTransitionState(false) }
            LaunchedEffect(Unit) { transition.targetState = true }
            val context = LocalContext.current
            val githubUrl = stringResource(R.string.onboarding_feature_engine_url)
            val failMsg = stringResource(R.string.opencl_download_fail, "")
            cards.forEachIndexed { index, (icon, title, description) ->
                AnimatedVisibility(
                    visibleState = transition,
                    enter = fadeIn(tween(400, delayMillis = index * 180)) +
                        slideInVertically(tween(400, delayMillis = index * 180)) { it / 2 },
                ) {
                    OnboardingFeatureCard(
                        icon = {
                            when (icon) {
                                is androidx.compose.ui.graphics.painter.Painter ->
                                    Icon(
                                        painter = icon,
                                        tint = Color.Unspecified,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (index == 0) 52.dp else 40.dp),
                                    )
                                is androidx.compose.ui.graphics.vector.ImageVector ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = FluentTheme.colors.text.text.primary,
                                        modifier = Modifier.size(34.dp),
                                    )
                            }
                        },
                        title = title,
                        description = description,
                        onClick = {
                            if (index == 1) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        failMsg,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    )
                }
            }
        }

        // fixed bottom button, matching the ready step
        AccentButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}