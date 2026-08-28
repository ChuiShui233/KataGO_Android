package com.chuishui.katago

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.goai.AiCommentaryResult
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

/**
 * Commentary card for the game screen. Renders the automatic explanations and
 * the multi-turn Q&A dialogue as a single stream of bubbles, so the automatic
 * analysis is as easy to notice as the user's own questions.
 */
@Composable
fun AiCommentaryPanel(
    commentary: AiCommentaryResult?,
    loading: Boolean,
    visible: Boolean,
    offlineMode: Boolean,
    chatHistory: List<AiChatMessage>,
    pendingQuestion: String?,
    guideMode: Boolean,
    onToggleGuide: (Boolean) -> Unit,
    onAskQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
) {
    if (!visible) return
    var asking by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(true) }

    val inChat = chatHistory.isNotEmpty() || pendingQuestion != null
    val scrollState = rememberScrollState()
    LaunchedEffect(commentary) {
        scrollState.scrollTo(0)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color(0x14000000), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (inChat) stringResource(R.string.ai_chat_title) else stringResource(R.string.ai_commentary_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                if (!offlineMode) {
                    Switcher(
                        checked = guideMode,
                        onCheckStateChange = onToggleGuide,
                        text = stringResource(R.string.ai_guide_mode),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (inChat && expanded) {
                    SubtleButton(onClick = onClearChat) { Text(stringResource(R.string.ai_clear_chat)) }
                }
                SubtleButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.ai_collapse else R.string.ai_expand))
                }
                if (!offlineMode) {
                    SubtleButton(onClick = { expanded = true; asking = true }) { Text(stringResource(R.string.ai_ask)) }
                }
            }
            if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .verticalScroll(scrollState)
            ) {
                // Errors from auto-analysis are shown as a note; successful
                // explanations and the dialogue share the same bubble stream.
                commentary?.error?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_analysis_failed, err), fontSize = 12.sp)
                }
                // Unified transcript: automatic explanations and user Q&A.
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
                if (asking) {
                    Spacer(Modifier.height(8.dp))
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
                }
            }
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean, board: AiChatMessage? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .background(
                    if (isUser) Color(0x22000000) else Color(0x0D000000),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column {
                if (text.isNotBlank()) {
                    Text(
                        if (isUser) AnnotatedString(text) else parseMarkdown(text),
                        fontSize = 12.sp,
                    )
                }
                val tips = board?.tipVertices.orEmpty()
                val grid = board?.boardGrid
                val size = board?.boardSize
                if (tips.isNotEmpty() && grid != null && size != null) {
                    CroppedBoardView(boardSize = size, grid = grid, tipVertices = tips)
                }
            }
        }
    }
}