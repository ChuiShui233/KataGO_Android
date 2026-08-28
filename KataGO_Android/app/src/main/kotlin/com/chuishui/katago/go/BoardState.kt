package com.chuishui.katago.go

/**
 * Board mirror for the UI, implementing full Go rules locally.
 *
 * Rules (reference: writing-block.md / GoRules):
 *   - variable board size (9/13/19), black plays first
 *   - suicide forbidden
 *   - SIMPLE KO (单劫)
 *   - two consecutive passes end the game
 *   - Chinese AREA scoring, komi added to white
 *
 * The engine is the final authority on legality; [play] still returns false
 * for locally-illegal moves (occupied / ko / suicide) so the caller can bail
 * out before round-tripping to KataGo.
 *
 * Coordinates are flat vertex indices: `row * size + col`.
 */
class BoardState(var size: Int = 19) {

    data class Move(
        val vertex: Int,
        val color: Char,
        val pass: Boolean = false,
        val captures: Int = 0,
        val removed: List<Int> = emptyList(),
    )

    data class Score(
        val blackArea: Int,
        val whiteArea: Int,
        val blackScore: Double,
        val whiteScore: Double,
        /** 'B', 'W' or ' ' on a draw. */
        val winner: Char,
        val margin: Double,
        val blackTerritory: Int,
        val whiteTerritory: Int,
    )

    var grid = MutableList(size * size) { ' ' }
        private set

    val moves = mutableListOf<Move>()

    /** Stones captured by each side (提子数). */
    var blackCaptures = 0
        private set
    var whiteCaptures = 0
        private set

    /** Simple-ko forbidden point (null when none). */
    private var koVertex: Int? = null

    /** koVertex before each move, restored by [popLast]. */
    private val koStack = ArrayDeque<Int?>()

    val currentPlayer: Char
        get() = if (moves.size % 2 == 0) 'B' else 'W'

    // ------------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------------

    fun resize(newSize: Int) {
        size = newSize
        grid = MutableList(size * size) { ' ' }
        moves.clear()
        koStack.clear()
        koVertex = null
        blackCaptures = 0
        whiteCaptures = 0
    }

    fun clear() {
        for (i in grid.indices) grid[i] = ' '
        moves.clear()
        koStack.clear()
        koVertex = null
        blackCaptures = 0
        whiteCaptures = 0
    }

    fun isOccupied(vertex: Int): Boolean =
        vertex in grid.indices && grid[vertex] != ' '

    // ------------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------------

    /**
     * Whether [vertex] is a legal move for [color].
     * Checks: on the board, empty, not a ko point, not suicide.
     */
    fun isLegal(vertex: Int, color: Char): Boolean {
        if (vertex < 0 || vertex >= grid.size) return false
        if (grid[vertex] != ' ') return false
        if (vertex == koVertex) return false

        val test = grid.toMutableList()
        test[vertex] = color
        val opponent = opponent(color)
        for (n in neighbors(vertex)) {
            if (test[n] == opponent && liberties(test, n) == 0) {
                removeGroup(test, n)
            }
        }
        return liberties(test, vertex) > 0
    }

    /**
     * Places a stone, removes captured opponent groups (提子), updates the
     * counts and the simple-ko point.
     *
     * @return false if the move is locally illegal (nothing changes).
     */
    fun play(vertex: Int, color: Char, pass: Boolean = false): Boolean {
        if (pass || vertex < 0) {
            koStack.addLast(koVertex)
            koVertex = null
            moves.add(Move(vertex, color, pass = true))
            return true
        }
        if (!isLegal(vertex, color)) return false

        forcePlay(vertex, color)
        return true
    }

    /**
     * Places a stone without local legality validation. Used to mirror engine
     * moves / replayed saved games: the engine already accepted the move, so
     * suicide/ko checks may disagree with the engine and must not block sync.
     */
    fun forcePlay(vertex: Int, color: Char, pass: Boolean = false) {
        if (pass || vertex < 0) {
            koStack.addLast(koVertex)
            koVertex = null
            moves.add(Move(vertex, color, pass = true))
            return
        }
        if (vertex in grid.indices && grid[vertex] == ' ') {
            koStack.addLast(koVertex)
            grid[vertex] = color
            val removed = capturedNeighbors(vertex, color)
            for (g in removed) grid[g] = ' '
            val captured = removed.size
            if (color == 'B') blackCaptures += captured else whiteCaptures += captured
            koVertex = computeKo(vertex, color, removed)
            moves.add(Move(vertex, color, captures = captured, removed = removed))
        }
    }

    /**
     * Chinese AREA scoring (数子). Dead stones should be passed in by the
     * UI/AI; komi defaults per board size and is added to white.
     */
    fun score(deadStones: Set<Int> = emptySet(), komi: Double = defaultKomi()): Score {
        val scoring = grid.toMutableList()
        for (v in deadStones) {
            if (v in scoring.indices) scoring[v] = ' '
        }

        var blackStones = 0
        var whiteStones = 0
        for (v in scoring.indices) {
            when (scoring[v]) {
                'B' -> blackStones++
                'W' -> whiteStones++
            }
        }

        val visited = BooleanArray(size * size)
        var blackTerritory = 0
        var whiteTerritory = 0
        for (start in scoring.indices) {
            if (scoring[start] != ' ' || visited[start]) continue
            val region = emptyRegion(scoring, start, visited)
            val bordering = mutableSetOf<Char>()
            for (v in region) {
                for (n in neighbors(v)) {
                    if (scoring[n] == 'B' || scoring[n] == 'W') bordering.add(scoring[n])
                }
            }
            when {
                bordering == setOf('B') -> blackTerritory += region.size
                bordering == setOf('W') -> whiteTerritory += region.size
                // touches both sides: shared / neutral, counts for neither
            }
        }

        val blackArea = blackStones + blackTerritory
        val whiteArea = whiteStones + whiteTerritory
        val blackScore = blackArea.toDouble()
        val whiteScore = whiteArea.toDouble() + komi

        val winner: Char
        val margin: Double
        when {
            blackScore > whiteScore -> { winner = 'B'; margin = blackScore - whiteScore }
            whiteScore > blackScore -> { winner = 'W'; margin = whiteScore - blackScore }
            else -> { winner = ' '; margin = 0.0 }
        }

        return Score(
            blackArea = blackArea,
            whiteArea = whiteArea,
            blackScore = blackScore,
            whiteScore = whiteScore,
            winner = winner,
            margin = margin,
            blackTerritory = blackTerritory,
            whiteTerritory = whiteTerritory,
        )
    }

    // ------------------------------------------------------------------------
    // Undo
    // ------------------------------------------------------------------------

    fun lastMove(): Move? = moves.lastOrNull()

    fun popLast() {
        if (moves.isEmpty()) return
        val m = moves.removeAt(moves.size - 1)
        koVertex = koStack.removeLastOrNull() ?: null
        if (!m.pass) {
            grid[m.vertex] = ' '
            val restored = opponent(m.color)
            for (g in m.removed) grid[g] = restored
        }
        if (m.captures > 0) {
            if (m.color == 'B') blackCaptures -= m.captures else whiteCaptures -= m.captures
        }
    }

    /** Removes the last two plies (one human + one engine) and rebuilds the board. */
    fun popTwo() {
        repeat(2) { popLast() }
    }

    // ------------------------------------------------------------------------
    // Capture / liberties
    // ------------------------------------------------------------------------

    /** Opponent groups neighbouring [vertex] that lost all liberties. */
    private fun capturedNeighbors(vertex: Int, color: Char): List<Int> {
        val opponent = opponent(color)
        val removed = LinkedHashSet<Int>()
        for (n in neighbors(vertex)) {
            if (grid[n] == opponent && liberties(grid, n) == 0) {
                removed.addAll(groupOf(grid, n))
            }
        }
        return removed.toList()
    }

    /** Sets koVertex if the last capture formed a single-stone single-liberty ko. */
    private fun computeKo(vertex: Int, color: Char, removed: List<Int>): Int? {
        if (removed.size != 1) return null
        if (groupOf(grid, vertex).size != 1) return null
        if (liberties(grid, vertex) != 1) return null
        return removed[0]
    }

    /** Adjacent vertices on the board (no row/col wrap-around). */
    private fun neighbors(v: Int): IntArray {
        val row = v / size
        val col = v % size
        val out = ArrayList<Int>(4)
        if (row > 0) out.add(v - size)
        if (row < size - 1) out.add(v + size)
        if (col > 0) out.add(v - 1)
        if (col < size - 1) out.add(v + 1)
        return out.toIntArray()
    }

    /** Flood-fill the group containing [v] on [board], all same-colored stones. */
    private fun groupOf(board: List<Char>, v: Int): List<Int> {
        val color = board[v]
        if (color == ' ') return emptyList()
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>().apply { add(v) }
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            if (!seen.add(s)) continue
            for (n in neighbors(s)) {
                if (board[n] == color && n !in seen) stack.add(n)
            }
        }
        return seen.toList()
    }

    /** Number of empty liberties of the group containing [v] on [board]. */
    private fun liberties(board: List<Char>, v: Int): Int {
        val color = board[v]
        if (color == ' ') return 0
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>().apply { add(v) }
        var libs = 0
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            if (seen.contains(s)) continue
            seen.add(s)
            for (n in neighbors(s)) {
                when (board[n]) {
                    ' ' -> libs++
                    color -> stack.add(n)
                }
            }
        }
        return libs
    }

    private fun removeGroup(board: MutableList<Char>, v: Int) {
        for (g in groupOf(board, v)) board[g] = ' '
    }

    /** Flood-fill an empty region on [board], marking [visited]. */
    private fun emptyRegion(
        board: List<Char>,
        start: Int,
        visited: BooleanArray,
    ): List<Int> {
        if (board[start] != ' ' || visited[start]) return emptyList()
        val region = mutableListOf<Int>()
        val stack = ArrayDeque<Int>().apply { add(start) }
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            if (visited[s]) continue
            if (board[s] != ' ') continue
            visited[s] = true
            region.add(s)
            for (n in neighbors(s)) {
                if (!visited[n]) stack.add(n)
            }
        }
        return region
    }

    private fun opponent(color: Char): Char =
        if (color == 'B') 'W' else 'B'

    private fun defaultKomi(): Double =
        if (size <= 13) 7.0 else 7.5

    // ------------------------------------------------------------------------
    // GTP
    // ------------------------------------------------------------------------

    fun toGtpCoord(vertex: Int): String {
        if (vertex < 0) return "pass"
        val row = vertex / size
        val col = vertex % size
        val colLetter = "ABCDEFGHJKLMNOPQRST"[col]
        val rowNum = size - row
        return "$colLetter$rowNum"
    }

    /** Convert a GTP vertex (e.g. "D4") back to a flat index, or null. */
    fun fromGtpCoord(coord: String): Int? {
        val value = coord.trim().uppercase()
        if (value == "PASS") return -1
        if (value.length < 2) return null
        val col = "ABCDEFGHJKLMNOPQRST".indexOf(value[0])
        if (col < 0 || col >= size) return null
        val row = value.substring(1).toIntOrNull() ?: return null
        val r = size - row
        if (r < 0 || r >= size) return null
        return r * size + col
    }

    /** GTP "play" commands for the whole game so far. */
    fun buildGtpPosition(): String = buildString {
        for (m in moves) {
            val color = if (m.color == 'B') "B" else "W"
            appendLine("play $color ${toGtpCoord(m.vertex)}")
        }
    }
}
