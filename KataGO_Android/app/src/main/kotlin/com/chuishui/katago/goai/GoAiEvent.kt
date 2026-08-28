package com.chuishui.katago.goai

/** What kind of AI commentary is requested. */
enum class GoAiEventType {
    MISTAKE,
    BLUNDER,
    TURNING_POINT,
    FIGHT,
    GOOD_MOVE,
    USER_QUESTION,
    GAME_SUMMARY,
}

/**
 * Compact description of one board event. Following the token-compression
 * rules, it never carries the full board / SGF / ownership data.
 */
data class GoAiEvent(
    val type: GoAiEventType,
    val moveNumber: Int = 0,
    val player: Char = 'B',
    val moveCoord: String = "",
    val bestCoord: String? = null,
    val winrateBeforePct: Float? = null,
    val winrateAfterPct: Float? = null,
    val deltaPct: Float = 0f,
    val pv: String? = null,
    val phase: String? = null,
    val question: String? = null,
    val scoreLeadBefore: Float? = null,
    val scoreLeadAfter: Float? = null,
)

/** Slim board context passed to the coach for user questions. */
data class GoAiContext(
    val moveNumber: Int = 0,
    val player: Char = 'B',
    val moveCoord: String = "",
    val bestCoord: String? = null,
    val winrateBeforePct: Float? = null,
    val winrateAfterPct: Float? = null,
    val pv: String? = null,
    val phase: String? = null,
    val scoreLead: Float? = null,
    /** Full board dump (grid + move record) used by the GET_BOARD tool. */
    val boardText: String? = null,
    /** Full board serialized as JSON (board data only) for global analysis. */
    val boardJson: String? = null,
) {
    fun toEvent(type: GoAiEventType): GoAiEvent = GoAiEvent(
        type = type,
        moveNumber = moveNumber,
        player = player,
        moveCoord = moveCoord,
        bestCoord = bestCoord,
        winrateBeforePct = winrateBeforePct,
        winrateAfterPct = winrateAfterPct,
        deltaPct = (winrateBeforePct ?: 0f) - (winrateAfterPct ?: 0f),
        pv = pv,
        phase = phase,
        scoreLeadBefore = scoreLead,
        scoreLeadAfter = scoreLead,
    )
}

/** A single annotated move for game summarization. */
data class GoMoveSummary(
    val number: Int,
    val player: Char,
    val coord: String,
    val winratePct: Float?,
    val winrateAfterPct: Float? = null,
)

/** Enough game-level data for a summary prompt. */
data class GameAnalysis(
    val boardSize: Int,
    val moves: List<GoMoveSummary>,
    val result: String? = null,
    val komi: Float = 7.5f,
)

/** Outcome of one coaching call. */
data class AiCommentaryResult(
    val eventType: GoAiEventType,
    val summary: String = "",
    val reason: String = "",
    val suggestion: String = "",
    val providerId: String? = null,
    val error: String? = null,
) {
    val text: String
        get() {
            val parts = listOf(summary, reason, suggestion).filter { it.isNotBlank() }
            return parts.joinToString("\n")
        }
}