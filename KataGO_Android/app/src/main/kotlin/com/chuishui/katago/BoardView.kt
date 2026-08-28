package com.chuishui.katago

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuishui.katago.go.BoardState
import kotlin.math.roundToInt

@Composable
fun BoardView(
    board: BoardState,
    lastMove: Int?,
    previewVertex: Int?,
    aiPlanVertex: Int?,
    onTap: (Int) -> Unit,
    tipVertices: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    BoardCanvas(board = board, lastMove = lastMove, previewVertex = previewVertex, aiPlanVertex = aiPlanVertex, onTap = onTap, tipVertices = tipVertices, modifier = modifier)
}

@Composable
private fun BoardCanvas(
    board: BoardState,
    lastMove: Int?,
    previewVertex: Int?,
    aiPlanVertex: Int?,
    onTap: (Int) -> Unit,
    tipVertices: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val n = board.size
    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
            .aspectRatio(1f)
            .pointerInput(n) {
                val gridSize = minOf(size.width, size.height)
                val cell = gridSize / (n + 1)
                val offset = cell.toFloat()
                detectTapGestures { pos ->
                    val col = ((pos.x - offset) / cell).roundToInt()
                    val row = ((pos.y - offset) / cell).roundToInt()
                    if (col in 0 until n && row in 0 until n) {
                        onTap(row * n + col)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / (n + 1)
            val offset = cell
            val wood = Color(0xFFD9A648)
            drawRect(wood)
            val line = Color(0xFF3A2C14)
            for (i in 0 until n) {
                val p = offset + i * cell
                drawLine(line, Offset(p, offset), Offset(p, offset + (n - 1) * cell), 1.5f)
                drawLine(line, Offset(offset, p), Offset(offset + (n - 1) * cell, p), 1.5f)
            }
            val starPoints = starPoints(n)
            starPoints.forEach { (r, c) ->
                drawCircle(line, radius = cell * 0.10f, center = Offset(offset + c * cell, offset + r * cell))
            }
            board.grid.forEachIndexed { idx, v ->
                if (v != ' ') {
                    val r = idx / n
                    val c = idx % n
                    val center = Offset(offset + c * cell, offset + r * cell)
                    val stoneR = cell * 0.46f
                    drawCircle(if (v == 'B') Color(0xFF101010) else Color(0xFFF5F2EA), radius = stoneR, center = center)
                    if (idx == lastMove) {
                        val mark = if (v == 'B') Color.White else Color(0xFF7A5A00)
                        drawCircle(mark, radius = stoneR * 0.22f, center = center)
                    }
                }
            }
            if (previewVertex != null && board.grid.getOrNull(previewVertex) == ' ') {
                val r = previewVertex / n
                val c = previewVertex % n
                val center = Offset(offset + c * cell, offset + r * cell)
                val stoneR = cell * 0.46f
                val ghost = if (board.currentPlayer == 'B') {
                    Color(0xFF101010).copy(alpha = 0.45f)
                } else {
                    Color(0xFFF5F2EA).copy(alpha = 0.55f)
                }
                drawCircle(ghost, radius = stoneR, center = center)
                drawCircle(line, radius = stoneR, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.04f))
            }
            if (aiPlanVertex != null && board.grid.getOrNull(aiPlanVertex) == ' ') {
                val r = aiPlanVertex / n
                val c = aiPlanVertex % n
                val center = Offset(offset + c * cell, offset + r * cell)
                val stoneR = cell * 0.46f
                val plan = Color(0xFF2E8B57)
                drawCircle(
                    plan.copy(alpha = 0.30f),
                    radius = stoneR,
                    center = center,
                )
                drawCircle(
                    plan,
                    radius = stoneR * 0.72f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.07f),
                )
            }
            tipVertices.forEach { idx ->
                if (board.grid.getOrNull(idx) == ' ') {
                    val r = idx / n
                    val c = idx % n
                    val center = Offset(offset + c * cell, offset + r * cell)
                    val stoneR = cell * 0.46f
                    val tip = Color(0xFF2E8B57)
                    drawCircle(
                        tip.copy(alpha = 0.35f),
                        radius = stoneR,
                        center = center,
                    )
                    drawCircle(
                        tip,
                        radius = stoneR * 0.30f,
                        center = center,
                    )
                    drawCircle(
                        Color.White,
                        radius = stoneR * 0.14f,
                        center = center,
                    )
                }
            }
            val labelStyle = TextStyle(
                color = line,
                fontSize = (cell * 0.24f).sp,
            )
            for (c in 0 until n) {
                val layout = textMeasurer.measure(columnLabel(c), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        offset + c * cell - layout.size.width / 2f,
                        offset - cell * 0.55f - layout.size.height / 2f,
                    ),
                )
            }
            for (r in 0 until n) {
                val layout = textMeasurer.measure("${n - r}", labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        offset - cell * 0.55f - layout.size.width / 2f,
                        offset + r * cell - layout.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** Standard Go column letter, skipping "I" (A..T). */
fun columnLabel(c: Int): String {
    val idx = c + if (c >= 8) 1 else 0
    return ('A'.code + idx).toChar().toString()
}

fun starPoints(n: Int): List<Pair<Int, Int>> {
    val star = when (n) {
        19 -> listOf(3, 9, 15) to listOf(3, 9, 15)
        13 -> listOf(3, 6, 9) to listOf(3, 6, 9)
        9 -> listOf(2, 4, 6) to listOf(2, 4, 6)
        else -> listOf(0) to listOf(0)
    }
    val rows = star.first
    val cols = star.second
    val points = mutableListOf<Pair<Int, Int>>()
    for (r in rows) for (c in cols) points.add(r to c)
    return points
}