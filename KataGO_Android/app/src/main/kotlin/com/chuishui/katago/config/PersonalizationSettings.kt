package com.chuishui.katago.config

import android.content.Context
import android.net.Uri

data class PersonalizationSettings(
    val backgroundUri: String = "",
    val overlayAlpha: Float = 0.55f,
    val boardAlpha: Float = 1f,
    val blurRadius: Float = 0f,
) {
    val hasBackground: Boolean get() = backgroundUri.isNotEmpty()

    fun save(context: Context) {
        context.getSharedPreferences("katago", Context.MODE_PRIVATE).edit()
            .putString("personalization_bg_uri", backgroundUri)
            .putFloat("personalization_overlay_alpha", overlayAlpha)
            .putFloat("personalization_board_alpha", boardAlpha)
            .putFloat("personalization_blur_radius", blurRadius)
            .apply()
    }

    companion object {
        fun load(context: Context): PersonalizationSettings {
            val prefs = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
            return PersonalizationSettings(
                backgroundUri = prefs.getString("personalization_bg_uri", "") ?: "",
                overlayAlpha = prefs.getFloat("personalization_overlay_alpha", 0.55f),
                boardAlpha = prefs.getFloat("personalization_board_alpha", 1f),
                blurRadius = prefs.getFloat("personalization_blur_radius", 0f),
            )
        }
    }
}
