package com.chuishui.katago

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card

/** A clickable feature card styled exactly like the gallery home cards. */
@Composable
fun OnboardingFeatureCard(
    icon: (@Composable () -> Unit)? = {},
    title: String,
    description: String,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) icon()
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = title,
                    style = FluentTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = FluentTheme.typography.body,
                    color = FluentTheme.colors.text.text.secondary,
                )
            }
        }
    }
}

/** Fills the banner with a stylized Go board: grid, star points and stones. */
@Composable
fun GoBoardBanner(darkTheme: Boolean, modifier: Modifier = Modifier) {
    val boardColor = if (darkTheme) Color(0xff4A3F2E) else Color(0xffE3C99A)
    val lineColor = if (darkTheme) Color(0xffB7A98C) else Color(0xff6B5A3E)
    val blackStone = Color(0xff1A1A1A)
    val whiteStone = Color(0xffF2F2F2)
    Canvas(modifier.fillMaxSize()) {
        val n = 9
        val pad = size.minDimension * 0.12f
        val step = (size.minDimension - pad * 2) / (n - 1)
        val x0 = (size.width - step * (n - 1)) / 2
        val y0 = (size.height - step * (n - 1)) / 2
        val stoneR = step * 0.42f

        // board background
        drawRoundRect(
            color = boardColor,
            topLeft = Offset(x0 - step * 0.55f, y0 - step * 0.55f),
            size = Size(step * (n - 1) + step * 1.1f, step * (n - 1) + step * 1.1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        )

        // grid lines
        for (i in 0 until n) {
            val p = x0 + i * step
            drawLine(lineColor, Offset(p, y0), Offset(p, y0 + step * (n - 1)), strokeWidth = 1.5f)
        }
        for (j in 0 until n) {
            val q = y0 + j * step
            drawLine(lineColor, Offset(x0, q), Offset(x0 + step * (n - 1), q), strokeWidth = 1.5f)
        }

        // star points (hoshi) on the 9x9 grid
        val hoshi = setOf(0 to 0, 0 to 8, 8 to 0, 8 to 8, 4 to 4)
        for ((i, j) in hoshi) {
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x0 + i * step, y0 + j * step),
            )
        }

        // a scattering of black and white stones
        val stones = listOf(
            2 to 2 to blackStone,
            4 to 2 to whiteStone,
            6 to 3 to blackStone,
            2 to 5 to whiteStone,
            5 to 5 to blackStone,
            7 to 6 to whiteStone,
            3 to 7 to blackStone,
            6 to 7 to whiteStone,
            1 to 4 to whiteStone,
            5 to 1 to blackStone,
        )
        for ((pos, color) in stones) {
            val (i, j) = pos
            drawCircle(
                color = color,
                radius = stoneR,
                center = Offset(x0 + i * step, y0 + j * step),
            )
            drawCircle(
                color = lineColor.copy(alpha = 0.35f),
                radius = stoneR,
                center = Offset(x0 + i * step, y0 + j * step),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Reads the app version name (e.g. "0.1.0") from the package manager. */
@Composable
fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
}