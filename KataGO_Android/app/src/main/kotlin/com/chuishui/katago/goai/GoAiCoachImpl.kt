package com.chuishui.katago.goai

import com.chuishui.katago.ai.config.AiConfigSource
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.ai.model.AiRequest
import com.chuishui.katago.ai.model.ResponseFormat
import com.chuishui.katago.ai.provider.AiException
import com.chuishui.katago.ai.queue.AiRequestQueue
import com.chuishui.katago.ai.router.AiRoutePolicy
import com.chuishui.katago.ai.router.AiRouter

/**
 * Default [GoAiCoach]. Routes every call through the router, maps vendor
 * responses into [AiCommentaryResult], and falls back to local templates when
 * offline mode is on or every provider is unavailable.
 */
class GoAiCoachImpl(
    private val router: AiRouter,
    private val configSource: AiConfigSource,
    private val queue: AiRequestQueue,
) : GoAiCoach {

    override suspend fun explain(event: GoAiEvent): AiCommentaryResult {
        val settings = configSource.globalSettings()
        if (settings.offlineOnly) return offlineResult(event)

        val (mode, prompt, policy) = when (event.type) {
            GoAiEventType.BLUNDER -> Triple(
                GoAiPromptBuilder.Mode.MISTAKE_EXPLANATION,
                GoAiPromptBuilder.userForMistake(event),
                AiRoutePolicy.BEST,
            )
            GoAiEventType.MISTAKE -> Triple(
                GoAiPromptBuilder.Mode.MISTAKE_EXPLANATION,
                GoAiPromptBuilder.userForMistake(event),
                AiRoutePolicy.AUTO,
            )
            GoAiEventType.TURNING_POINT -> Triple(
                GoAiPromptBuilder.Mode.TURNING_POINT,
                GoAiPromptBuilder.userForTurningPoint(event.toContext()),
                AiRoutePolicy.AUTO,
            )
            GoAiEventType.FIGHT -> Triple(
                GoAiPromptBuilder.Mode.FIGHT_ANALYSIS,
                GoAiPromptBuilder.userForFight(event.toContext()),
                AiRoutePolicy.AUTO,
            )
            GoAiEventType.GOOD_MOVE -> Triple(
                GoAiPromptBuilder.Mode.BEST_MOVE_EXPLANATION,
                GoAiPromptBuilder.userForBestMove(event.toContext()),
                AiRoutePolicy.CHEAPEST,
            )
            GoAiEventType.USER_QUESTION -> Triple(
                GoAiPromptBuilder.Mode.USER_QUESTION,
                GoAiPromptBuilder.userForQuestion(event.question ?: "", event.toContext()),
                AiRoutePolicy.MANUAL,
            )
            GoAiEventType.GAME_SUMMARY -> Triple(
                GoAiPromptBuilder.Mode.GAME_SUMMARY,
                GoAiPromptBuilder.userForSummary(GameAnalysis(boardSize = 19, moves = emptyList())),
                AiRoutePolicy.AUTO,
            )
        }
        return call(
            type = event.type,
            mode = mode,
            prompt = prompt,
            policy = policy,
            event = event,
        )
    }

    override suspend fun summarizeGame(game: GameAnalysis): AiCommentaryResult {
        val settings = configSource.globalSettings()
        if (settings.offlineOnly) {
            return AiCommentaryResult(
                eventType = GoAiEventType.GAME_SUMMARY,
                summary = "离线模式下已跳过 AI 复盘。",
            )
        }
        val mode = GoAiPromptBuilder.Mode.GAME_SUMMARY
        val prompt = GoAiPromptBuilder.userForSummary(game)
        return call(
            type = GoAiEventType.GAME_SUMMARY,
            mode = mode,
            prompt = prompt,
            policy = AiRoutePolicy.AUTO,
            event = null,
        )
    }

    override suspend fun answer(question: String, context: GoAiContext): AiCommentaryResult {
        val settings = configSource.globalSettings()
        if (settings.offlineOnly) {
            return AiCommentaryResult(
                eventType = GoAiEventType.USER_QUESTION,
                summary = "离线模式下无法回答提问。",
            )
        }
        val mode = GoAiPromptBuilder.Mode.USER_QUESTION
        var prompt = GoAiPromptBuilder.userForQuestion(question, context)
        var result = call(
            type = GoAiEventType.USER_QUESTION,
            mode = mode,
            prompt = prompt,
            policy = AiRoutePolicy.MANUAL,
            history = trimHistory(chatHistory),
            event = GoAiEvent(
                type = GoAiEventType.USER_QUESTION,
                moveNumber = context.moveNumber,
                player = context.player,
                question = question,
            ),
        )
        // GET_BOARD tool: if the model asked for the full board and we have
        // one, inject the dump and re-ask (once) so it can answer precisely.
        val firstText = result.suggestion.ifBlank { result.summary }
        if (firstText.contains(GoAiPromptBuilder.GET_BOARD_MARKER) &&
            !context.boardText.isNullOrBlank()
        ) {
            prompt = buildString {
                appendLine("以下是用户问题：")
                appendLine(question)
                appendLine()
                appendLine("你请求的完整棋盘数据：")
                appendLine(context.boardText)
                appendLine()
                appendLine("请基于这些数据回答用户问题，直接给出答案即可，不要 JSON。200 字以内。")
            }
            result = call(
                type = GoAiEventType.USER_QUESTION,
                mode = mode,
                prompt = prompt,
                policy = AiRoutePolicy.MANUAL,
                history = trimHistory(chatHistory),
                event = GoAiEvent(
                    type = GoAiEventType.USER_QUESTION,
                    moveNumber = context.moveNumber,
                    player = context.player,
                    question = question,
                ),
            )
        }
        if (result.error == null) {
            val answer = result.suggestion.ifBlank { result.summary }
            chatHistory += AiChatMessage("user", question)
            chatHistory += AiChatMessage("assistant", answer)
        }
        return result
    }

    override suspend fun answerGlobal(question: String, context: GoAiContext): AiCommentaryResult {
        val settings = configSource.globalSettings()
        if (settings.offlineOnly) {
            return AiCommentaryResult(
                eventType = GoAiEventType.USER_QUESTION,
                summary = "离线模式下无法进行全局分析。",
            )
        }
        val mode = GoAiPromptBuilder.Mode.GLOBAL_ANALYSIS
        val prompt = GoAiPromptBuilder.userForGlobalAnalysis(question, context)
        val result = call(
            type = GoAiEventType.USER_QUESTION,
            mode = mode,
            prompt = prompt,
            policy = AiRoutePolicy.MANUAL,
            history = trimHistory(chatHistory),
            event = GoAiEvent(
                type = GoAiEventType.USER_QUESTION,
                moveNumber = context.moveNumber,
                player = context.player,
                question = question,
            ),
            maxTokens = 6000,
            reasoningEffort = "high",
            timeoutMs = 120_000,
            temperature = 0.4,
        )
        if (result.error == null) {
            val answer = result.suggestion.ifBlank { result.summary }
            chatHistory += AiChatMessage("user", question)
            chatHistory += AiChatMessage("assistant", answer)
        }
        return result
    }

    /** Multi-turn chat history for the interactive Q&A (kept across moves). */
    private val chatHistory = mutableListOf<AiChatMessage>()

    /** Keeps the sent history bounded so the request fits the model's TPM. */
    private fun trimHistory(history: List<AiChatMessage>): List<AiChatMessage> =
        history.takeLast(6).map { m ->
            val text = m.content
            if (text.length <= 800) m
            else m.copy(content = text.take(800))
        }

    override fun enqueueExplain(event: GoAiEvent, onResult: (AiCommentaryResult) -> Unit) {
        val priority = when (event.type) {
            GoAiEventType.BLUNDER -> AiRequestQueue.Priority.BLUNDER
            GoAiEventType.TURNING_POINT -> AiRequestQueue.Priority.TURNING_POINT
            GoAiEventType.FIGHT -> AiRequestQueue.Priority.FIGHT
            GoAiEventType.GOOD_MOVE -> AiRequestQueue.Priority.GOOD_MOVE
            GoAiEventType.USER_QUESTION -> AiRequestQueue.Priority.USER_REQUEST
            else -> AiRequestQueue.Priority.MISTAKE
        }
        val eventNumber = event.moveNumber
        queue.enqueue(
            priority = priority,
            isStale = { eventNumber < latestMoveNumber - 2 },
            block = {
                val r = explain(event)
                r
            },
            onResult = { result ->
                onResult(
                    result.getOrElse { err ->
                        AiCommentaryResult(
                            eventType = event.type,
                            error = err.message ?: "AI 分析失败",
                        )
                    }
                )
            },
        )
    }

    /** Callers notify the coach of new move numbers so stale queue entries drop. */
    override fun onGameAdvance(moveNumber: Int) {
        latestMoveNumber = moveNumber
    }

    /** Last raw request payload sent to the AI, for debug inspection. */
    @Volatile
    override var lastRawRequest: String? = null
        private set

    /** Last raw response content received from the AI, for debug inspection. */
    @Volatile
    override var lastRawResponse: String? = null
        private set

    @Volatile
    private var latestMoveNumber: Int = 0

    private suspend fun call(
        type: GoAiEventType,
        mode: GoAiPromptBuilder.Mode,
        prompt: String,
        policy: AiRoutePolicy,
        event: GoAiEvent?,
        history: List<AiChatMessage> = emptyList(),
        maxTokens: Int? = null,
        reasoningEffort: String? = null,
        timeoutMs: Long? = null,
        temperature: Double? = null,
    ): AiCommentaryResult {
        val settings = configSource.globalSettings()
        val preferred = settings.preferredProviderId.takeIf { it != "auto" }
        val effectivePolicy = when {
            type == GoAiEventType.USER_QUESTION && preferred == null -> AiRoutePolicy.AUTO
            else -> policy
        }
        return try {
            val options = AiRouter.RouteOptions(
                policy = effectivePolicy,
                preferredProviderId = preferred,
                allowFallback = settings.allowFallback,
                offlineOnly = settings.offlineOnly,
            )
            val request = AiRequest(
                systemPrompt = GoAiPromptBuilder.systemFor(mode),
                userPrompt = prompt,
                maxTokens = maxTokens ?: if (type == GoAiEventType.USER_QUESTION) 1500 else 320,
                temperature = temperature ?: 0.2,
                responseFormat = if (type == GoAiEventType.USER_QUESTION) ResponseFormat.TEXT
                else ResponseFormat.JSON,
                history = history,
                timeoutMs = timeoutMs ?: 30_000,
                reasoningEffort = if (settings.disableReasoning) null else
                    reasoningEffort
                        ?: if (type == GoAiEventType.USER_QUESTION) null else "low",
            )
            lastRawRequest = buildString {
                appendLine("== 请求参数 ==")
                appendLine("mode: $mode")
                appendLine("policy: $effectivePolicy")
                appendLine("responseFormat: ${request.responseFormat}")
                appendLine("maxTokens: ${request.maxTokens}")
                appendLine("temperature: ${request.temperature}")
                appendLine("reasoningEffort: ${request.reasoningEffort}")
                appendLine("timeoutMs: ${request.timeoutMs}")
                appendLine()
                appendLine("== System Prompt ==")
                appendLine(request.systemPrompt)
                appendLine()
                appendLine("== User Prompt ==")
                appendLine(request.userPrompt)
                if (request.history.isNotEmpty()) {
                    appendLine()
                    appendLine("== 历史对话 ==")
                    request.history.forEach { m ->
                        appendLine("[${m.role}] ${m.content}")
                    }
                }
            }
            val response = try {
                router.chat(request, options)
            } catch (e: AiException) {
                // Groq (and similar) JSON mode rejects any non-strict-JSON
                // output with a 400, which happens occasionally even with a
                // good prompt. Retry once in plain text so the tolerant
                // field parser can still extract summary / reason / suggestion.
                if (request.responseFormat == ResponseFormat.JSON &&
                    (isJsonValidationError(e) || isEmptyContentError(e))
                ) {
                    router.chat(request.copy(responseFormat = ResponseFormat.TEXT), options)
                } else {
                    throw e
                }
            }
            if (type == GoAiEventType.USER_QUESTION) {
                lastRawResponse = response.content
                AiCommentaryResult(
                    eventType = type,
                    summary = "",
                    reason = "",
                    suggestion = response.content,
                    providerId = response.providerId,
                )
            } else {
                lastRawResponse = response.content
                parseJsonComment(response.content, type).copy(
                    providerId = response.providerId,
                )
            }
        } catch (e: AiException) {
            offlineResult(event ?: GoAiEvent(type = type)).copy(error = e.message)
        }
    }

    /** Extracts summary / reason / suggestion from the model's JSON answer. */
    private fun parseJsonComment(content: String, type: GoAiEventType): AiCommentaryResult {
        return parseJsonCommentInternal(content, type)
    }

    private fun extract(json: String, key: String): String {
        return extractJsonField(json, key)
    }

    private fun offlineResult(event: GoAiEvent): AiCommentaryResult {
        val loss = event.winrateBeforePct?.let { before ->
            event.winrateAfterPct?.let { after -> before - after }
        }
        val summary = buildString {
            append("本手导致胜率下降 ${String.format("%.1f", loss ?: event.deltaPct.coerceAtLeast(0f))}%")
        }
        val suggestion = event.bestCoord?.let { "KataGo 认为这手建议走 $it" }
            ?: "当前着与 KataGo 推荐不同，建议复盘此局部。"
        return AiCommentaryResult(
            eventType = event.type,
            summary = summary,
            reason = "KataGo 数据显示该着并非本局面最优。",
            suggestion = suggestion,
            error = null,
        )
    }
}

/**
 * Extracts summary / reason / suggestion from the model's JSON answer.
 * Values may be wrapped in straight or full-width (Chinese) quotes.
 */
internal fun parseJsonCommentInternal(content: String, type: GoAiEventType): AiCommentaryResult {
    val summary = extractJsonField(content, "summary")
    val reason = extractJsonField(content, "reason")
    val suggestion = extractJsonField(content, "suggestion")
    val have = summary.isNotBlank() || reason.isNotBlank() || suggestion.isNotBlank()
    if (!have) {
        return AiCommentaryResult(eventType = type, summary = content.trim().take(500))
    }
    return AiCommentaryResult(
        eventType = type,
        summary = summary,
        reason = reason,
        suggestion = suggestion,
    )
}

/** True when a provider rejected a JSON-mode request because the model output was not strict JSON. */
internal fun isJsonValidationError(e: AiException): Boolean {
    val m = e.message.orEmpty()
    return m.contains("validate JSON", ignoreCase = true) ||
        m.contains("failed_generation", ignoreCase = true) ||
        m.contains("invalid json", ignoreCase = true)
}

/** True when the model returned no usable text (e.g. a reasoning model exhausted its budget). */
internal fun isEmptyContentError(e: AiException): Boolean {
    return e.message.orEmpty().contains("empty content", ignoreCase = true)
}

/** Reads one JSON string field, tolerating straight/full-width quotes and missing leading quotes. */
internal fun extractJsonField(json: String, key: String): String {
    val regex = Regex(
        """(?:")?$key"\s*[:：]\s*["“](.*?)["”]""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val m = regex.find(json) ?: return ""
    return m.groupValues[1]
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .trim()
}