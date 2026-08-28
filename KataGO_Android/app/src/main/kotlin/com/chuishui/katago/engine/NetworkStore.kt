package com.chuishui.katago.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class NetworkModel(
    val name: String,
    val url: String,
    val rating: Double,
    val games: Int,
    val date: String,
    val tag: String,
)

/** Crawls https://katagotraining.org/networks/ and caches the model list as JSON. */
object NetworkStore {

    private const val PAGE_URL = "https://katagotraining.org/networks/"
    private const val USER_AGENT = "Mozilla/5.0 (KataGO-Android)"

    fun cacheFile(context: Context): File = File(context.filesDir, "networks_cache.json")

    fun loadCache(context: Context): List<NetworkModel> {
        val f = cacheFile(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NetworkModel(
                            name = o.getString("name"),
                            url = o.getString("url"),
                            rating = o.optDouble("rating", 0.0),
                            games = o.optInt("games", 0),
                            date = o.optString("date", ""),
                            tag = o.optString("tag", ""),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCache(context: Context, models: List<NetworkModel>) {
        runCatching {
            val arr = JSONArray()
            for (m in models) {
                val o = JSONObject()
                o.put("name", m.name)
                o.put("url", m.url)
                o.put("rating", m.rating)
                o.put("games", m.games)
                o.put("date", m.date)
                o.put("tag", m.tag)
                arr.put(o)
            }
            cacheFile(context).writeText(arr.toString())
        }
    }

    /** Fetches and parses the network list, refreshing the local JSON cache. */
    fun fetchModels(context: Context): List<NetworkModel> {
        val conn = URL(PAGE_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept-Encoding", "identity")
        val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val models = parse(html)
        if (models.isNotEmpty()) saveCache(context, models)
        return models
    }

    private fun parse(html: String): List<NetworkModel> {
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tdRegex = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val hrefRegex = Regex("href\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val stripTags = Regex("<[^>]+>")
        val ratingRegex = Regex("([\\d.]+)\\s*±")
        val gamesRegex = Regex("\\((\\d[\\d,]*)\\s*games\\)")
        val tagRegex = Regex("b\\d+", RegexOption.IGNORE_CASE)
        val models = buildList {
            for (rowMatch in rowRegex.findAll(html)) {
                val row = rowMatch.groupValues[1]
                if ("bin.gz" !in row && "txt.gz" !in row) continue
                val tds = tdRegex.findAll(row).map { it.groupValues[1] }.toList()
                if (tds.size < 4) continue
                val name = stripTags.replace(tds[0], "").trim()
                if (name.isEmpty()) continue
                val date = stripTags.replace(tds[1], "").trim()
                val rating = ratingRegex.find(tds[2])?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val games = gamesRegex.find(tds[2])?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
                val modelUrl = hrefRegex.findAll(tds[3])
                    .map { it.groupValues[1] }
                    .firstOrNull { it.endsWith(".bin.gz") || it.endsWith(".txt.gz") }
                    ?: continue
                add(
                    NetworkModel(
                        name = name,
                        url = modelUrl,
                        rating = rating,
                        games = games,
                        date = date,
                        tag = tagRegex.find(name)?.value ?: "",
                    )
                )
            }
        }
        return models
    }

    /** Local file name for a model, preserving the extension from its download URL. */
    fun fileName(model: NetworkModel): String =
        model.url.substringAfterLast('/').takeIf { it.endsWith(".bin.gz") || it.endsWith(".txt.gz") }
            ?: model.name + ".bin.gz"

    /** Downloads a model into the models folder. Returns the file, or null on failure. */
    suspend fun download(context: Context, model: NetworkModel, onProgress: (Long, Long) -> Unit): File? =
        withContext(Dispatchers.IO) {
            if (!ModelStore.ensureDir()) return@withContext null
            val finalFile = File(ModelStore.modelDir, fileName(model))
            if (finalFile.exists()) return@withContext finalFile
            val partFile = File(ModelStore.modelDir, fileName(model) + ".part")
            val conn = URL(model.url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept-Encoding", "identity")
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                if (partFile.renameTo(finalFile)) {
                    onProgress(done, total)
                    finalFile
                } else {
                    partFile.delete()
                    null
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                partFile.delete()
                throw e
            } catch (e: Exception) {
                partFile.delete()
                null
            } finally {
                conn.disconnect()
            }
        }

    fun installedFile(model: NetworkModel): File = File(ModelStore.modelDir, fileName(model))
}
