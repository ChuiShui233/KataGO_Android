package com.chuishui.katago

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.surface.Card
import io.github.composefluent.surface.CardColor
import io.github.composefluent.surface.CardDefaults
import io.github.composefluent.component.Text

@Composable
fun FluentDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .then(
                        if (dismissOnClickOutside) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
                ) {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    cardColors = CardDefaults.cardColors(
                        default = CardColor(
                            fillColor = FluentTheme.colors.background.solid.quaternary,
                            contentColor = FluentTheme.colors.text.text.primary,
                            borderBrush = SolidColor(FluentTheme.colors.stroke.card.defaultSolid),
                        ),
                    ),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                        content()
                    }
                }
            }
        }
    }
}

/**
 * True modal dialog opened in a real window layer (Compose
 * [androidx.compose.ui.window.Dialog]). Unlike [FluentDialog], which renders
 * inline in the composition and is thus clipped / scrolled by its parent
 * layout, this covers the entire screen no matter where it is invoked from.
 */
@Composable
fun FluentModalDialog(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                cardColors = CardDefaults.cardColors(
                    default = CardColor(
                        fillColor = FluentTheme.colors.background.solid.quaternary,
                        contentColor = FluentTheme.colors.text.text.primary,
                        borderBrush = SolidColor(FluentTheme.colors.stroke.card.defaultSolid),
                    ),
                ),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun EditableValueText(
    value: String,
    onClick: () -> Unit,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    Text(
        text = value + suffix,
        fontSize = 12.sp,
        modifier = modifier.clickable(onClick = onClick),
    )
}
