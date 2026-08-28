package com.chuishui.katago

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DialogSize
import io.github.composefluent.component.Text

/** Confirmation dialog shown when the user taps "skip" on the Vulkan step. */
@Composable
internal fun SkipConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    ContentDialog(
        title = stringResource(R.string.onboarding_skip_title),
        visible = visible,
        size = DialogSize.Max,
        primaryButtonText = stringResource(R.string.onboarding_skip_confirm),
        closeButtonText = stringResource(R.string.onboarding_skip_cancel),
        onButtonClick = { button ->
            visible = false
            when (button) {
                ContentDialogButton.Primary -> onConfirm()
                else -> onDismiss()
            }
        },
        content = {
            Text(stringResource(R.string.onboarding_skip_message))
        },
    )
}

/** Confirmation dialog asking whether to cancel an in-flight cloud download. */
@Composable
internal fun CancelDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    ContentDialog(
        title = stringResource(R.string.onboarding_cancel_download_title),
        visible = visible,
        size = DialogSize.Max,
        primaryButtonText = stringResource(R.string.onboarding_cancel_download_confirm),
        closeButtonText = stringResource(R.string.onboarding_cancel_download_keep),
        onButtonClick = { button ->
            visible = false
            when (button) {
                ContentDialogButton.Primary -> onConfirm()
                else -> onDismiss()
            }
        },
        content = {
            Text(stringResource(R.string.onboarding_cancel_download_message))
        },
    )
}