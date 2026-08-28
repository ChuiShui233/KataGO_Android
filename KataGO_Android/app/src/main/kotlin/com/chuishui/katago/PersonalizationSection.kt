package com.chuishui.katago

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Slider
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import com.chuishui.katago.config.PersonalizationSettings
import io.moyuru.cropify.Cropify
import io.moyuru.cropify.CropifyOption
import io.moyuru.cropify.CropifySize
import io.moyuru.cropify.rememberCropifyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PersonalizationSection(
    settings: PersonalizationSettings,
    onSettingsChange: (PersonalizationSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    if (pendingBitmap != null) {
        val cropState = rememberCropifyState()
        val config = LocalConfiguration.current
        val screenAspectRatio = maxOf(config.screenWidthDp, config.screenHeightDp).toFloat() / minOf(config.screenWidthDp, config.screenHeightDp).toFloat()
        Dialog(
            onDismissRequest = { pendingBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.personalization_bg_crop),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF2D2D2D)),
                ) {
                    Cropify(
                        bitmap = pendingBitmap!!,
                        state = cropState,
                        onImageCropped = { cropped ->
                            pendingBitmap = null
                            scope.launch {
                                val savedUri = withContext(Dispatchers.IO) {
                                    val file = File(context.cacheDir, "cropped_bg_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(file).use { out ->
                                        cropped.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    Uri.fromFile(file).toString()
                                }
                                onSettingsChange(settings.copy(backgroundUri = savedUri))
                                settings.copy(backgroundUri = savedUri).save(context)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        option = CropifyOption(frameSize = CropifySize.FixedAspectRatio(screenAspectRatio)),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SubtleButton(onClick = { pendingBitmap = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                    AccentButton(onClick = { cropState.crop() }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }

    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {}
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                if (bitmap != null) {
                    pendingBitmap = bitmap.asImageBitmap()
                }
            }
        }
    }

    fun launchCropExisting() {
        val currentUri = settings.backgroundUri.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(currentUri)
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) { null }
            }
            if (bitmap != null) {
                pendingBitmap = bitmap.asImageBitmap()
            }
        }
    }

    Text(stringResource(R.string.personalization_title), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Text(stringResource(R.string.personalization_hint), fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.personalization_bg), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentButton(onClick = { bgPicker.launch(arrayOf("image/*")) }) {
            Text(stringResource(R.string.personalization_bg_pick))
        }
        if (settings.hasBackground) {
            SubtleButton(onClick = { launchCropExisting() }) {
                Text(stringResource(R.string.personalization_bg_crop))
            }
            SubtleButton(onClick = {
                onSettingsChange(settings.copy(backgroundUri = ""))
                settings.copy(backgroundUri = "").save(context)
            }) {
                Text(stringResource(R.string.personalization_bg_clear), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (settings.hasBackground) {
        Text(stringResource(R.string.personalization_bg_set), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.personalization_overlay), fontWeight = FontWeight.SemiBold)
    Text(String.format("%.0f%%", settings.overlayAlpha * 100), fontSize = 12.sp)
    Slider(
        value = settings.overlayAlpha,
        onValueChange = { onSettingsChange(settings.copy(overlayAlpha = it)) },
        onValueChangeFinished = { settings.save(context) },
        valueRange = 0f..0.95f,
    )
    Spacer(Modifier.height(16.dp))
    if (settings.hasBackground) {
        Text(stringResource(R.string.personalization_board_alpha), fontWeight = FontWeight.SemiBold)
        Text(String.format("%.0f%%", settings.boardAlpha * 100), fontSize = 12.sp)
        Slider(
            value = settings.boardAlpha,
            onValueChange = { onSettingsChange(settings.copy(boardAlpha = it)) },
            onValueChangeFinished = { settings.save(context) },
            valueRange = 0.1f..1f,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.personalization_blur), fontWeight = FontWeight.SemiBold)
        Text(String.format("%.0f dp", settings.blurRadius), fontSize = 12.sp)
        Slider(
            value = settings.blurRadius,
            onValueChange = { onSettingsChange(settings.copy(blurRadius = it)) },
            onValueChangeFinished = { settings.save(context) },
            valueRange = 0f..25f,
            steps = 24,
        )
    }
}
