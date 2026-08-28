package com.chuishui.katago.goai

/**
 * Natural-language coaching layer. It talks only to the
 * [com.chuishui.katago.ai.router.AiRouter] — never to a specific vendor —
 * keeping KataGo analysis, AI providers and routing fully decoupled.
 */
interface GoAiCoach {

    /** Explains a single board event (mistake / blunder / good move / ...). */
    suspend fun explain(event: GoAiEvent): AiCommentaryResult

    /** Summarizes a whole game. */
    suspend fun summarizeGame(game: GameAnalysis): AiCommentaryResult

    /** Answers a user's free-form question about the current position. */
    suspend fun answer(question: String, context: GoAiContext): AiCommentaryResult

    /**
     * Global analysis (guide mode): the model reasons autonomously from the
     * full board JSON, not constrained by KataGo numbers, with no token or
     * reasoning limits.
     */
    suspend fun answerGlobal(question: String, context: GoAiContext): AiCommentaryResult

    /**
     * Schedules auto analysis through the request queue so it never blocks
     * the game loop. [onResult] runs on the queue worker.
     */
    fun enqueueExplain(event: GoAiEvent, onResult: (AiCommentaryResult) -> Unit)

    /** Lets the coach track the current move number so stale entries drop. */
    fun onGameAdvance(moveNumber: Int) = Unit

    /** Last raw request payload sent to the AI, for debug inspection. */
    val lastRawRequest: String?
        get() = null

    /** Last raw response content received from the AI, for debug inspection. */
    val lastRawResponse: String?
        get() = null
}