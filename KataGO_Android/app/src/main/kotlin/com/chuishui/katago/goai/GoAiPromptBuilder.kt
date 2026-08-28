package com.chuishui.katago.goai

/**
 * Builds the prompts used by [GoAiCoach]. Prompts live here, fully separated
 * from providers — a provider only sends bytes, it never decides what to ask.
 *
 * Follows the token-compression rules: only the current move, winrate change,
 * best move, short PV and phase are sent, never a board dump or ownership.
 */
object GoAiPromptBuilder {

    enum class Mode {
        MISTAKE_EXPLANATION,
        BEST_MOVE_EXPLANATION,
        TURNING_POINT,
        FIGHT_ANALYSIS,
        GAME_SUMMARY,
        USER_QUESTION,
        GLOBAL_ANALYSIS,
    }

    /** Marker the model outputs when it needs the full board. */
    const val GET_BOARD_MARKER = "[[GET_BOARD]]"

    /** Tip markers: the model wraps a suggested move coordinate like [tip]D4[/tip]. */
    const val TIP_START = "[tip]"
    const val TIP_END = "[/tip]"

    private const val SYSTEM_BASE =
        "你是一名围棋教练与解说。判断棋力必须以 KataGo 数据为准：不要自行计算胜率，" +
            "不要修改 KataGo 给出的最佳着手。你的任务是用人话解释棋理。\n" +
            "\n" +
            "若你推荐具体的落子点，请用以下格式包裹坐标（不要加空格、不要省略标记）：$TIP_START" +
            "坐标$TIP_END（例如 $TIP_START" +
            "D4$TIP_END、$TIP_START" +
            "Q16$TIP_END" +
            "）。系统会自动在棋盘上高亮这些点，并在你的回答下方展示该区域。每个推荐的落子点都必须用标记单独包裹，不要遗漏。"

    fun systemFor(mode: Mode): String = when (mode) {
        Mode.USER_QUESTION -> SYSTEM_BASE +
            "\n\n玩家（人类）执黑或执白由棋盘数据中的「玩家执」指明。回答时以玩家视角给出建议。" +
            "\n\n如果回答需要完整的棋盘落子情况，请在回答中输出一行：$GET_BOARD_MARKER" +
            "（单独一行）。系统检测到后会自动把当前棋盘数据注入，你再继续回答。"
        Mode.GLOBAL_ANALYSIS -> GLOBAL_SYSTEM_BASE
        else -> SYSTEM_BASE
    }

    /**
     * System prompt for global analysis (guide mode). The model is free to
     * think for itself instead of deferring to KataGo numbers: it receives
     * the full board as JSON and must reason about the whole position, point
     * out plans, and suggest concrete moves. It may draw a board example.
     */
    private const val GLOBAL_SYSTEM_BASE =
        "你是一名顶尖围棋教练。当前处于「全局分析」模式：系统会给你当前棋盘的完整 JSON 数据（仅棋盘），" +
            "你不再需要依赖任何引擎数据，请完全自主地思考全局。\n" +
            "\n" +
            "要求：\n" +
            "1. 回复必须简短高效，每条回答控制在3-5句话内，不要使用表格、不要分点列举、不要长篇大论。\n" +
            "2. 直接推荐具体的落子点（用围棋坐标，如 D4、Q16），并用一两句话说明理由。\n" +
            "3. 对于每个推荐落子点，请严格用以下格式包裹坐标（不要加空格、不要省略标记）：$TIP_START" +
            "坐标$TIP_END（例如 $TIP_START" +
            "D4$TIP_END、$TIP_START" +
            "Q16$TIP_END" +
            "）。系统会自动在棋盘上高亮这些点，并在你的回答下方展示该区域。每个推荐的落子点都必须用标记单独包裹，不要遗漏。\n" +
            "4. 语气自然、直接，像朋友聊天一样给出建议，不要说'让我分析'、'首先'、'其次'等套话。"

    /**
     * Serializes the current board into a compact JSON payload (board only —
     * no engine data). Grid rows go top→bottom, matching the board grid.
     */
    fun boardToJson(
        boardSize: Int,
        grid: List<Char>,
        moves: List<String>,
        currentPlayer: Char,
        humanColor: Char,
    ): String = buildString {
        appendLine("{")
        appendLine("  \"boardSize\": $boardSize,")
        appendLine("  \"currentPlayer\": \"$currentPlayer\",")
        appendLine("  \"humanColor\": \"$humanColor\",")
        appendLine("  \"grid\": [")
        for (r in 0 until boardSize) {
            val row = (0 until boardSize).joinToString("") { c ->
                val g = grid[r * boardSize + c]
                if (g == ' ') "." else g.toString()
            }
            append("    \"$row\"")
            append(if (r < boardSize - 1) "," else "")
            appendLine()
        }
        appendLine("  ],")
        append("  \"moves\": [${moves.joinToString(", ") { "\"$it\"" }}]")
        appendLine()
        append("}")
    }

    fun userForGlobalAnalysis(question: String, context: GoAiContext): String = buildString {
        appendLine("以下是用户的问题，请基于提供的棋盘 JSON 数据回答：")
        appendLine()
        appendLine(question)
        appendLine()
        context.boardJson?.takeIf { it.isNotBlank() }?.let { json ->
            appendLine("当前棋盘（JSON，仅棋盘数据）：")
            appendLine(json)
        } ?: run {
            appendLine("（当前无棋盘数据）")
        }
        appendLine()
        appendLine("请按全局分析模式的要求回答。")
    }

    /** Extracts all tip coordinates wrapped in [TIP_START]...[TIP_END]. */
    fun parseTips(text: String): List<String> {
        val start = TIP_START
        val end = TIP_END
        val result = mutableListOf<String>()
        var from = 0
        while (true) {
            val s = text.indexOf(start, from)
            if (s < 0) break
            val e = text.indexOf(end, s + start.length)
            if (e < 0) break
            val tip = text.substring(s + start.length, e).trim()
            if (tip.isNotEmpty()) result.add(tip)
            from = e + end.length
        }
        return result
    }

    /** Removes all tip markers and thinking blocks, leaving the plain text for display. */
    fun stripTips(text: String): String =
        text.replace(Regex("\\[tip\\](.*?)\\[/tip\\]", setOf(RegexOption.DOT_MATCHES_ALL)), "**$1**")
            .replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Serializes the current board into a compact text dump for the GET_BOARD tool. */
    fun boardToText(
        boardSize: Int,
        grid: List<Char>,
        moves: List<String>,
        currentPlayer: Char,
        humanColor: Char,
    ): String = buildString {
        appendLine("棋盘：${boardSize} 路")
        appendLine("玩家执：$humanColor；AI 执：${opponent(humanColor)}")
        appendLine("轮到：$currentPlayer")
        appendLine("坐标：左上角为 A${boardSize}，向下行号递减，向右列字母递增（跳过 I）。")
        append("   ")
        for (c in 0 until boardSize) {
            append("${"ABCDEFGHJKLMNOPQRST"[c]} ")
        }
        appendLine()
        for (r in 0 until boardSize) {
            append("${(boardSize - r).toString().padStart(2)} ")
            for (c in 0 until boardSize) {
                val g = grid[r * boardSize + c]
                append(if (g == ' ') ". " else "$g ")
            }
            appendLine()
        }
        appendLine("落子记录：${moves.joinToString(" ") { it }}")
    }

    private fun opponent(color: Char): Char = if (color == 'B') 'W' else 'B'

    /** Compact representation of one event, shared by several modes. */
    fun compactContext(context: GoAiContext): String = buildString {
        appendLine("当前着手：第${context.moveNumber}手 ${context.player} ${context.moveCoord.ifEmpty { "(未知)" }}")
        context.bestCoord?.let { appendLine("KataGo 最佳着：$it") }
        context.winrateBeforePct?.let { appendLine("落子前胜率：${fmt(it)}%") }
        context.winrateAfterPct?.let { appendLine("落子后胜率：${fmt(it)}%") }
        context.pv?.takeIf { it.isNotBlank() }?.let { appendLine("最佳着变化(PV)：$it") }
        context.phase?.takeIf { it.isNotBlank() }?.let { appendLine("阶段：$it") }
        context.scoreLead?.let { appendLine("目差：${fmt(it)}") }
    }

    /**
     * Complete prompt for a mistake explanation, asking the model to return a
     * tiny JSON object so the UI can render summary/reason/suggestion boxes.
     */
    fun userForMistake(event: GoAiEvent, limitChars: Int = 100): String = buildString {
        appendLine("请解释这一步失误。")
        appendLine()
        append(compactContext(event.toContext()))
        appendLine()
        appendLine("输出严格 JSON（不要其他文字）：")
        appendLine("{\"summary\":\"一句话概述\",\"reason\":\"失误原因\",\"suggestion\":\"改进建议\"}")
        appendLine("总计 ${limitChars} 字以内。")
    }

    fun userForBestMove(context: GoAiContext, limitChars: Int = 80): String = buildString {
        appendLine("请解释为什么 KataGo 推荐这个最佳着。")
        appendLine()
        append(compactContext(context))
        appendLine()
        appendLine("输出严格 JSON：{\"summary\":\"\",\"reason\":\"\",\"suggestion\":\"\"}")
        appendLine("总计 ${limitChars} 字以内。")
    }

    fun userForTurningPoint(context: GoAiContext, limitChars: Int = 120): String = buildString {
        appendLine("这一手是局面的重大转折点，请分析形势如何扭转。")
        appendLine()
        append(compactContext(context))
        appendLine()
        appendLine("输出严格 JSON：{\"summary\":\"\",\"reason\":\"\",\"suggestion\":\"\"}")
        appendLine("总计 ${limitChars} 字以内。")
    }

    fun userForFight(context: GoAiContext, limitChars: Int = 120): String = buildString {
        appendLine("请分析这一带的战斗情况与下一步要点。")
        appendLine()
        append(compactContext(context))
        appendLine()
        appendLine("输出严格 JSON：{\"summary\":\"\",\"reason\":\"\",\"suggestion\":\"\"}")
        appendLine("总计 ${limitChars} 字以内。")
    }

    fun userForSummary(game: GameAnalysis, limitChars: Int = 200): String = buildString {
        appendLine("请用通俗语言复盘这盘棋，按时间顺序讲清关键转折与胜负处（不含棋谱细节）。")
        appendLine()
        appendLine("棋盘：${game.boardSize} 路 · 共 ${game.moves.size} 手" +
            " · komi=${game.komi}")
        game.result?.takeIf { it.isNotBlank() }?.let { appendLine("结果：$it") }
        val highlights = game.moves
            .filter { it.winrateAfterPct != null }
            .sortedByDescending {
                val d = kotlin.math.abs(
                    (it.winrateAfterPct ?: 0f) - (it.winratePct ?: 0f)
                )
                d
            }
            .take(8)
        if (highlights.isNotEmpty()) {
            appendLine()
            appendLine("关键时刻摘要（每行一个）：")
            highlights.forEach { m ->
                val delta = ((m.winrateAfterPct ?: 0f) - (m.winratePct ?: 0f)).let {
                    if (m.player == 'B') -it else it
                }
                appendLine("第${m.number}手 ${m.player} ${m.coord} 胜率${fmt(m.winrateAfterPct ?: 0f)}% 波动${fmt(delta)}%")
            }
        }
        appendLine()
        appendLine("输出严格 JSON：{\"summary\":\"整体复盘\",\"reason\":\"关键处分析\",\"suggestion\":\"对局建议\"}")
        appendLine("总计 ${limitChars} 字以内。")
    }

    fun userForQuestion(question: String, context: GoAiContext, limitChars: Int = 200): String = buildString {
        appendLine("以下是用户关于当前棋局的提问，请结合给定数据回答：")
        appendLine()
        appendLine(question)
        appendLine()
        context.let {
            if (it.moveNumber > 0) {
                appendLine("参考上下文：")
                append(compactContext(it))
            }
        }
        appendLine()
        appendLine("请用简体中文回答，直接给出答案即可，不要 JSON。${limitChars} 字以内。")
    }

    private fun fmt(v: Float): String =
        if (kotlin.math.abs(v - v.toInt()) < 0.005f) v.toInt().toString() else "%.1f".format(v)
}

/** Shared event→context conversion used by the builder and the coach. */
fun GoAiEvent.toContext(): GoAiContext = GoAiContext(
    moveNumber = moveNumber,
    player = player,
    moveCoord = moveCoord,
    bestCoord = bestCoord,
    winrateBeforePct = winrateBeforePct,
    winrateAfterPct = winrateAfterPct,
    pv = pv,
    phase = phase,
    scoreLead = scoreLeadBefore ?: scoreLeadAfter,
    boardText = null,
    boardJson = null,
)