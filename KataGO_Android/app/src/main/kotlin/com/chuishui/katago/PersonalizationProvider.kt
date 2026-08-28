package com.chuishui.katago

import androidx.compose.runtime.compositionLocalOf
import com.chuishui.katago.config.PersonalizationSettings

val LocalPersonalization = compositionLocalOf { PersonalizationSettings() }
