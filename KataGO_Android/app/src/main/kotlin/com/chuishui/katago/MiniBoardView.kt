package com.chuishui.katago

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Paint.Style
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Renders a cropped window of the board around the suggested move points from
 * an AI answer. Generates a [Bitmap] snapshot via Android Canvas, then displays
 * it as an [Image]. This avoids Compose Canvas measurement issues inside
 * scrollable containers.
 */
@Composable
fun CroppedBoardView(
    boardSize: Int,
    grid: List<Char>,
    tipVertices: List<Int>,
    modifier: Modifier = Modifier,
) {
    if (tipVertices.isEmpty()) return
    val n = boardSize

    // Compute the bounding box of the tips.
    var minR = Int.MAX_VALUE
    var maxR = Int.MIN_VALUE
    var minC = Int.MAX_VALUE
    var maxC = Int.MIN_VALUE
    tipVertices.forEach { idx ->
        if (idx in 0 until n * n) {
            val r = idx / n
            val c = idx % n
            if (r < minR) minR = r
            if (r > maxR) maxR = r
            if (c < minC) minC = c
            if (c > maxC) maxC = c
        }
    }
    if (maxR < minR || maxC < minC) return
    val margin = 2
    var top = (minR - margin).coerceIn(0, n - 1)
    var bottom = (maxR + margin).coerceIn(0, n - 1)
    var left = (minC - margin).coerceIn(0, n - 1)
    var right = (maxC + margin).coerceIn(0, n - 1)
    // Make it square-ish (grow along the smaller axis).
    val h = bottom - top
    val w = right - left
    val target = maxOf(h, w)
    val growH = target - h
    val growW = target - w
    top = (top - growH / 2).coerceIn(0, n - 1)
    bottom = (top + target).coerceIn(0, n - 1)
    left = (left - growW / 2).coerceIn(0, n - 1)
    right = (left + target).coerceIn(0, n - 1)
    if (bottom - top < target) top = (bottom - target).coerceIn(0, n - 1)
    if (right - left < target) left = (right - target).coerceIn(0, n - 1)
    val rows = bottom - top + 1
    val cols = right - left + 1
    val tipSet = tipVertices.toSet()

    val px = 320
    val snapshot = remember(boardSize, grid, tipVertices, top, left, rows, cols) {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val cell = px.toFloat() / (rows + 1)
        val offset = cell
        // Wood background.
        canvas.drawColor(0xFFD9A648.toInt())
        // Grid lines.
        val linePaint = Paint().apply {
            color = 0xFF3A2C14.toInt()
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        for (i in 0 until rows) {
            val p = offset + i * cell
            canvas.drawLine(p, offset, p, offset + (cols - 1) * cell, linePaint)
            canvas.drawLine(offset, p, offset + (rows - 1) * cell, p, linePaint)
        }
        // Stones and tip markers.
        val stoneR = cell * 0.42f
        val tipR = cell * 0.46f
        val tipStroke = cell * 0.20f
        val tipStrokeW = cell * 0.06f
        val blackPaint = Paint().apply {
            color = 0xFF101010.toInt()
            isAntiAlias = true
        }
        val whitePaint = Paint().apply {
            color = 0xFFF5F2EA.toInt()
            isAntiAlias = true
        }
        val tipPaint = Paint().apply {
            color = 0xFF2E8B57.toInt()
            isAntiAlias = true
        }
        val tipStrokePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Style.STROKE
            strokeWidth = tipStrokeW
            isAntiAlias = true
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = (top + r) * n + (left + c)
                val cx = offset + c * cell
                val cy = offset + r * cell
                if (v in tipSet) {
                    canvas.drawCircle(cx, cy, tipR, tipPaint)
                    tipStrokePaint.style = Style.STROKE
                    canvas.drawCircle(cx, cy, tipStroke, tipStrokePaint)
                    continue
                }
                val stone = grid.getOrNull(v)
                if (stone != null && stone != ' ') {
                    canvas.drawCircle(cx, cy, stoneR, if (stone == 'B') blackPaint else whitePaint)
                }
            }
        }
        bmp.asImageBitmap()
    }
    Image(
        bitmap = snapshot,
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .aspectRatio(1f),
        contentScale = ContentScale.Fit,
    )
}
