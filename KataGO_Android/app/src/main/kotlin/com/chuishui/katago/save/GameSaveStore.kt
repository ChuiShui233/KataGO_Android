package com.chuishui.katago.save

import android.os.Environment
import com.chuishui.katago.ai.model.AiChatMessage
import com.chuishui.katago.go.BoardState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistence for saved games. Writes each save as a timestamped JSON file
 * under Download/KataGO-AOS/saves/ on shared storage, keeps at most
 * [MAX_SAVES] slots (oldest overwritten first), and migrates the legacy
 * single-file format on first use.
 *
 * The store is deliberately UI-free: the activity reads/writes through this
 * class and never touches the JSON format or the save directory directly.
 */
class GameSaveStore {

    companion object {
        const val MAX_SAVES = 20
    }

    private val baseDir =
        File(Environment.getExternalStorageDirectory(), "Download/KataGO-AOS/saves")
            .apply { mkdirs() }

    /** A parsed save. The [file] points at the on-disk slot backing it. */
    data class SavedGame(
        val file: File,
        val boardSize: Int,
        val engineColor: Char,
        val moves: List<BoardState.Move>,
        val humanThinkMs: Long,
        val aiThinkMs: Long,
        val gameElapsedMs: Long,
        val chatHistory: List<AiChatMessage>,
    )

    /** Serialises a snapshot into a new save slot, enforcing the slot cap. */
    fun save(
        boardSize: Int,
        engineColor: Char,
        moves: List<BoardState.Move>,
        humanThinkMs: Long,
        aiThinkMs: Long,
        gameElapsedMs: Long,
        chatHistory: List<AiChatMessage>,
    ): Result<Unit> {
        return runCatching {
            val root = JSONObject()
            root.put("boardSize", boardSize)
            root.put("engineColor", engineColor.toString())
            root.put("humanThinkMs", humanThinkMs)
            root.put("aiThinkMs", aiThinkMs)
            root.put("gameElapsedMs", gameElapsedMs)
            val arr = JSONArray()
            for (m in moves) {
                val o = JSONObject()
                o.put("color", m.color.toString())
                o.put("vertex", m.vertex)
                o.put("pass", m.pass)
                arr.put(o)
            }
            root.put("moves", arr)
            val chatArr = JSONArray()
            for (msg in chatHistory) {
                val o = JSONObject()
                o.put("role", msg.role)
                o.put("content", msg.content)
                if (msg.moveNumber != null) o.put("moveNumber", msg.moveNumber)
                chatArr.put(o)
            }
            root.put("chatHistory", chatArr)
            val file = File(baseDir, "game_${System.currentTimeMillis()}.json")
            file.writeText(root.toString())
            trim()
        }
    }

    /** All saves, newest first. Triggers one-time legacy migration. */
    fun list(): List<SavedGame> {
        migrateLegacySave()
        return slots()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { parse(it) }
    }

    /** Deletes the given slot file. */
    fun delete(saved: SavedGame) {
        saved.file.delete()
    }

    private fun slots(): List<File> =
        (baseDir.listFiles { f -> f.name.startsWith("game_") && f.name.endsWith(".json") } ?: emptyArray())
            .toList()

    private fun trim() {
        val files = slots().sortedBy { it.lastModified() }
        if (files.size <= MAX_SAVES) return
        files.take(files.size - MAX_SAVES).forEach { it.delete() }
    }

    /** Moves a pre-multi-save single file (saved_game.json) into the save folder. */
    private fun migrateLegacySave() {
        val legacy = File(baseDir.parentFile ?: baseDir, "saved_game.json")
        if (!legacy.exists()) return
        runCatching {
            val target = File(baseDir, "game_${legacy.lastModified()}.json")
            if (!target.exists()) legacy.copyTo(target)
            legacy.delete()
            trim()
        }
    }

    private fun parse(file: File): SavedGame? {
        return runCatching {
            val root = JSONObject(file.readText())
            val size = root.optInt("boardSize", 19)
            val engineColor = root.optString("engineColor", "W").firstOrNull() ?: 'W'
            val arr = root.optJSONArray("moves") ?: JSONArray()
            val moves = mutableListOf<BoardState.Move>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val color = o.optString("color", "B").firstOrNull() ?: 'B'
                val pass = o.optBoolean("pass", false)
                val vertex = if (pass) -1 else o.optInt("vertex", -1)
                moves.add(BoardState.Move(vertex, color, pass = pass))
            }
            val chatArr = root.optJSONArray("chatHistory") ?: JSONArray()
            val chatHistory = mutableListOf<AiChatMessage>()
            for (i in 0 until chatArr.length()) {
                val o = chatArr.getJSONObject(i)
                val role = o.optString("role", "assistant")
                val content = o.optString("content", "")
                val moveNumber = if (o.has("moveNumber")) o.optInt("moveNumber", 0) else null
                chatHistory.add(AiChatMessage(role, content, moveNumber))
            }
            SavedGame(
                file = file,
                boardSize = size,
                engineColor = engineColor,
                moves = moves,
                humanThinkMs = root.optLong("humanThinkMs", 0),
                aiThinkMs = root.optLong("aiThinkMs", 0),
                gameElapsedMs = root.optLong("gameElapsedMs", 0),
                chatHistory = chatHistory,
            )
        }.getOrNull()
    }
}
