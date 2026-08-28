package com.chuishui.katago

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuishui.katago.save.GameSaveStore
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card

@Composable
fun SavePickerScreen(
    visible: Boolean,
    saves: List<GameSaveStore.SavedGame>,
    onPick: (GameSaveStore.SavedGame) -> Unit,
    onDelete: (GameSaveStore.SavedGame) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        BackHandler { onDismiss() }
        SavePickerContent(
            saves = saves,
            onPick = onPick,
            onDelete = onDelete,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SavePickerContent(
    saves: List<GameSaveStore.SavedGame>,
    onPick: (GameSaveStore.SavedGame) -> Unit,
    onDelete: (GameSaveStore.SavedGame) -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()) }
    var toDelete by remember { mutableStateOf<GameSaveStore.SavedGame?>(null) }

    if (toDelete != null) {
        FluentModalDialog(
            onDismiss = { toDelete = null },
            title = stringResource(R.string.delete),
        ) {
            Text(stringResource(R.string.delete_model_confirm, toDelete?.file?.name ?: ""))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SubtleButton(onClick = { toDelete = null }) { Text(stringResource(R.string.back)) }
                AccentButton(onClick = {
                    toDelete?.let { onDelete(it) }
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FluentTheme.colors.background.mica.base),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                stringResource(R.string.select_save),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            SubtleButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
            Spacer(Modifier.height(12.dp))

            if (saves.isEmpty()) {
                Text(stringResource(R.string.no_saved_game))
            } else {
                Card(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn {
                        itemsIndexed(saves, key = { _, s -> s.file.absolutePath }) { index, saved ->
                            val ts = fmt.format(java.util.Date(saved.file.lastModified()))
                            val label = stringResource(R.string.save_item, ts, saved.moves.size, "${saved.boardSize}")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onPick(saved) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                )
                                SubtleButton(onClick = { toDelete = saved }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                            if (index < saves.lastIndex) {
                                Divider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = FluentTheme.colors.stroke.card.defaultSolid,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
