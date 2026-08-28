package com.chuishui.katago.game

/** Result of asking the engine for one move, as seen by the local game flow. */
enum class AiMoveOutcome {
    /** A real stone was played. */
    PLAYED,

    /** The engine passed. */
    PASSED,

    /** The engine resigned, so the game is over and the human won. */
    RESIGNED,

    /** The engine replied with an error or the move could not be applied. */
    FAILED,
}
