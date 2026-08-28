package com.chuishui.katago.goai

import com.chuishui.katago.ai.config.AiGlobalSettings

/**
 * Classifies a move's winrate swing into a [GoAiEventType] and decides whether
 * AI commentary should be triggered at all. Thresholds come from
 * [AiGlobalSettings] and are configurable — never hard-coded in the flow.
 *
 * The detector is fed the engine-reported winrate (which this app reports as
 * BLACK everywhere) plus which color made the move; it converts to the mover's
 * own perspective internally.
 */
class GoMistakeDetector(
    private val globalSettings: () -> AiGlobalSettings,
) {

    private data class Snapshot(
        val blackWinrate: Float?,
        val blackScoreLead: Float?,
    )

    private var snapshots = ArrayDeque<Snapshot>()

    fun reset() {
        snapshots.clear()
    }

    /**
     * Records the outcome of [moveNumber] by [player] and returns an event when
     * the drop crosses the active thresholds.
     *
     * @param blackWinrateAfter engine-reported black winrate after the move.
     */
    fun onMove(
        player: Char,
        moveNumber: Int,
        blackWinrateAfter: Float?,
        blackScoreLeadAfter: Float?,
        bestCoord: String? = null,
        pv: String? = null,
        phase: String? = null,
    ): GoAiEvent? {
        val settings = globalSettings()
        if (settings.autoAnalysisMode == AiGlobalSettings.AutoAnalysisMode.OFF) {
            snapshots.addLast(Snapshot(blackWinrateAfter, blackScoreLeadAfter))
            trim()
            return null
        }
        val now = Snapshot(blackWinrateAfter, blackScoreLeadAfter)
        val before = snapshots.lastOrNull()
        snapshots.addLast(now)
        trim()

        val wrBefore = before?.blackWinrate
        val wrAfter = blackWinrateAfter
        if (wrBefore == null || wrAfter == null) return null

        val ownBefore = ownWinrate(player, wrBefore)
        val ownAfter = ownWinrate(player, wrAfter)
        val lossPct = (ownBefore * 100f - ownAfter * 100f).coerceAtLeast(0f)
        val adjustedThreshold = when (settings.autoAnalysisMode) {
            AiGlobalSettings.AutoAnalysisMode.OFF -> Float.MAX_VALUE
            AiGlobalSettings.AutoAnalysisMode.MAJOR_MISTAKES -> settings.autoThresholdHigh
            AiGlobalSettings.AutoAnalysisMode.IMPORTANT_MISTAKES -> settings.autoThresholdLow
            AiGlobalSettings.AutoAnalysisMode.AGGRESSIVE -> 3f
        }
        if (lossPct < adjustedThreshold) return null
        val type = if (lossPct >= settings.autoThresholdHigh) GoAiEventType.BLUNDER
        else GoAiEventType.MISTAKE
        return GoAiEvent(
            type = type,
            moveNumber = moveNumber,
            player = player,
            moveCoord = "",
            bestCoord = bestCoord,
            winrateBeforePct = ownBefore * 100f,
            winrateAfterPct = ownAfter * 100f,
            deltaPct = lossPct,
            pv = pv,
            phase = phase,
            scoreLeadBefore = scoreLeadFromPerspective(player, before.blackScoreLead),
            scoreLeadAfter = scoreLeadFromPerspective(player, blackScoreLeadAfter),
        )
    }

    private fun ownWinrate(player: Char, blackWinrate: Float): Float =
        if (player == 'B') blackWinrate else 1f - blackWinrate

    private fun scoreLeadFromPerspective(player: Char, blackLead: Float?): Float? =
        if (player == 'B') blackLead else blackLead?.let { -it }

    private fun trim() {
        while (snapshots.size > 512) snapshots.removeFirst()
    }
}