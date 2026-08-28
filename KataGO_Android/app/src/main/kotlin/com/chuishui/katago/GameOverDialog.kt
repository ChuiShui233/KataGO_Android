package com.chuishui.katago

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text

/**
 * Game-over dialog shown when the game ends (engine resigns or the game is
 * finished by two passes): reports win/loss/draw from the human's
 * perspective. Pure UI, all behaviour is delegated through
 * [onPlayAgain] / [onBackHome].
 */
@Composable
fun GameOverDialog(
    visible: Boolean,
    title: String,
    message: String,
    onPlayAgain: () -> Unit,
    onBackHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    FluentDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        dismissOnClickOutside = false,
        content = {
            Text(message)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SubtleButton(onClick = onBackHome) { Text(stringResource(R.string.back_home)) }
                AccentButton(onClick = onPlayAgain) { Text(stringResource(R.string.play_again)) }
            }
        },
    )
}
