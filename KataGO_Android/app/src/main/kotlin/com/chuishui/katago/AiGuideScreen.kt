package com.chuishui.katago

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.goai.AiCommentaryResult
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

/**
 * Full-screen conversation page shown when guide mode is active. It replaces
 * the compact commentary card with the same transcript but more room to read
 * and ask, and offers a capsule toggle to switch back to commentary mode.
 */
@Composable
fun AiGuideScreen(
    commentary: AiCommentaryResult?,
    loading: Boolean,
    offlineMode: Boolean,
    chatHistory: List<AiChatMessage>,
    pendingQuestion: String?,
    guideMode: Boolean,
    onToggleGuide: (Boolean) -> Unit,
    onAskQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
    rawRequest: String?,
    rawResponse: String?,
) {
    var asking by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var showRawRequest by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    LaunchedEffect(commentary, chatHistory.size, pendingQuestion) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.ai_guide_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (BuildConfig.DEBUG) {
                SubtleButton(onClick = { showRawRequest = true }) {
                    Text("Debug", fontSize = 12.sp)
                }
            }
            if (!offlineMode) {
                Switcher(
                    checked = guideMode,
                    onCheckStateChange = onToggleGuide,
                    text = stringResource(R.string.ai_commentary_mode),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0x14000000), RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                commentary?.error?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_analysis_failed, err), fontSize = 12.sp)
                }
                chatHistory.forEach { msg ->
                    Spacer(Modifier.height(6.dp))
                    ChatBubble(text = msg.content, isUser = msg.role == "user", board = msg)
                }
                pendingQuestion?.let { q ->
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_analyzing), fontSize = 12.sp)
                }
                if (loading && chatHistory.isEmpty() && pendingQuestion == null && commentary?.error == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_analyzing), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (asking) {
            TextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_ask_placeholder)) },
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SubtleButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) }
                SubtleButton(onClick = {
                    if (question.isNotBlank()) {
                        onAskQuestion(question)
                        question = ""
                        asking = false
                    }
                }) { Text(stringResource(R.string.send)) }
            }
            Spacer(Modifier.height(4.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (chatHistory.isNotEmpty()) {
                    SubtleButton(onClick = onClearChat) { Text(stringResource(R.string.ai_clear_chat)) }
                }
                if (!offlineMode) {
                    SubtleButton(onClick = { asking = true }) { Text(stringResource(R.string.ai_ask)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showRawRequest && BuildConfig.DEBUG) {
        FluentModalDialog(
            onDismiss = { showRawRequest = false },
            title = "Debug: AI 请求/响应",
        ) {
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            val content = buildString {
                appendLine("== 请求 ==")
                appendLine(rawRequest ?: "（尚无请求）")
                appendLine()
                appendLine("== 响应 ==")
                appendLine(rawResponse ?: "（尚无响应）")
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(360.dp)
                        .background(Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        content,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (rawResponse != null || rawRequest != null) {
                        SubtleButton(onClick = {
                            clipboard.setText(AnnotatedString(content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.ai_copy)) }
                    }
                    SubtleButton(onClick = { showRawRequest = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}