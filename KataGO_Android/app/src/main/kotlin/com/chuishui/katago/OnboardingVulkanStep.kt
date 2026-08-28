package com.chuishui.katago

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Icon
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Add
import io.github.composefluent.icons.filled.ArrowLeft
import io.github.composefluent.icons.filled.Cloud

/**
 * Vulkan-driver install step: a big title, two square buttons (local import
 * and cloud download), and a bottom-right "skip" / "next" pair. The next
 * button stays disabled until a clvk library has been installed.
 */
@Composable
internal fun OnboardingVulkanStep(
    darkTheme: Boolean,
    clvkInstalled: Boolean,
    openClTransferProgress: Float?,
    openClTransferPhase: Int,
    openClTransferSpeed: Float?,
    openClDownloading: Boolean,
    showBack: Boolean = true,
    onBack: () -> Unit,
    onImportClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipClick: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        // top-left: back button (icon only), hidden when opened from settings
        if (showBack) {
            SubtleButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowLeft, stringResource(R.string.onboarding_back))
            }
        } else {
            Spacer(Modifier.height(44.dp))
        }
        Spacer(Modifier.height(16.dp))

        // big centered title
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.onboarding_vulkan_title),
                    style = FluentTheme.typography.title,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.text.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_vulkan_subtitle),
                    style = FluentTheme.typography.body,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.text.secondary,
                )

                Spacer(Modifier.height(48.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                ) {
                    SquareActionButton(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.onboarding_import_local),
                        onClick = onImportClick,
                    )
                    SquareActionButton(
                        icon = Icons.Filled.Cloud,
                        label = stringResource(R.string.onboarding_download_cloud),
                        onClick = onDownloadClick,
                    )
                }

                Spacer(Modifier.height(12.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(
                    text = stringResource(R.string.opencl_download_text),
                    style = FluentTheme.typography.body,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.accent.primary,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.opencl_download_url)),
                            )
                            runCatching { context.startActivity(intent) }
                                .onFailure { e ->
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.opencl_download_fail, e.message ?: ""),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_vulkan_keep_foreground),
                    style = FluentTheme.typography.body,
                    textAlign = TextAlign.Center,
                    color = FluentTheme.colors.text.text.secondary,
                )

                Spacer(Modifier.height(32.dp))
                // live transfer progress, when an import/download is running
                if (openClTransferProgress != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        // phase title: downloading vs installing
                        val phaseTitle = when (openClTransferPhase) {
                            1 -> stringResource(R.string.onboarding_downloading)
                            2 -> stringResource(R.string.onboarding_installing)
                            3 -> stringResource(R.string.onboarding_importing)
                            else -> stringResource(R.string.onboarding_transferring)
                        }
                        Text(
                            text = phaseTitle,
                            style = FluentTheme.typography.body,
                            color = FluentTheme.colors.text.text.secondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(progress = openClTransferProgress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.onboarding_transfer_percent,
                                    (openClTransferProgress * 100).toInt(),
                                ),
                                style = FluentTheme.typography.body,
                                color = FluentTheme.colors.text.text.secondary,
                            )
                            // live transfer speed, shown during the download phase and the
                            // local-import phase
                            if ((openClTransferPhase == 1 || openClTransferPhase == 3) && openClTransferSpeed != null) {
                                Text(
                                    text = formatSpeed(openClTransferSpeed),
                                    style = FluentTheme.typography.body,
                                    color = FluentTheme.colors.text.text.secondary,
                                )
                            }
                        }
                        // cancel button while a cloud download is in progress
                        if (openClDownloading) {
                            Spacer(Modifier.height(8.dp))
                            SubtleButton(onClick = onCancelDownload) {
                                Text(stringResource(R.string.onboarding_skip_cancel))
                            }
                        }
                    }
                }
            }
        }

        // bottom-right: skip / next. Skip is pointless once the driver is
        // already installed, so it is hidden in that case.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!clvkInstalled) {
                SubtleButton(onClick = onSkipClick) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            AccentButton(
                onClick = onNext,
                disabled = !clvkInstalled,
            ) {
                Text(stringResource(R.string.onboarding_next))
            }
        }
    }
}

/** A square tappable tile with an icon on top and a label below. */
@Composable
private fun SquareActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                FluentTheme.colors.stroke.card.default,
                shape = RoundedCornerShape(12.dp),
            )
            .background(FluentTheme.colors.control.secondary)
            .clickable(onClick = onClick),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(48.dp),
            tint = FluentTheme.colors.text.text.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = label,
            style = FluentTheme.typography.body,
            fontSize = 14.sp,
            color = FluentTheme.colors.text.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/** Formats a byte/second rate as a human-readable string, e.g. "12.5 MB/s". */
internal fun formatSpeed(bytesPerSec: Float): String {
    val kb = bytesPerSec / 1024f
    return when {
        kb >= 1024f -> String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024f)
        else -> String.format(java.util.Locale.US, "%.1f KB/s", kb)
    }
}