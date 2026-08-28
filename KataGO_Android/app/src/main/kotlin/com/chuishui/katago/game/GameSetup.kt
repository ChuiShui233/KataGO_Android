package com.chuishui.katago.game

import java.io.File

/** The parameters a game was started with, kept for an immediate rematch. */
data class GameSetup(
    val modelFile: File,
    val size: Int,
    val humanColor: String,
)
