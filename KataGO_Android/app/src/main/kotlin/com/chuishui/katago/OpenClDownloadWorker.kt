package com.chuishui.katago

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Unzip the official clvk artifact or return [raw] if it already is a .so. */
internal fun extractLibOpenClSo(raw: File): File? {
    val head = ByteArray(4)
    raw.inputStream().use { ins ->
        val n = ins.read(head)
        if (n < 4) return null
    }
    val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
        head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
    if (!isZip) return raw
    val out = File(raw.parentFile, "opencl-extracted.so")
    java.util.zip.ZipInputStream(java.io.BufferedInputStream(raw.inputStream())).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.endsWith("libOpenCL.so")) {
                out.outputStream().use { o -> zip.copyTo(o) }
                zip.closeEntry()
                return out
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return null
}

/** True for a 64-bit aarch64 ELF shared object (e_machine == 0xB7). */
internal fun isAarch64Elf(f: File): Boolean {
    return try {
        java.io.RandomAccessFile(f, "r").use { raf ->
            if (raf.length() < 20) return false
            val b = ByteArray(20)
            raf.readFully(b)
            b[0] == 0x7f.toByte() && b[1] == 'E'.code.toByte() &&
                b[2] == 'L'.code.toByte() && b[3] == 'F'.code.toByte() &&
                ((b[18].toInt() and 0xff) or ((b[19].toInt() and 0xff) shl 8)) == 183
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * WorkManager worker that downloads libOpenCL.so in the background. WorkManager
 * schedules the worker through the system and keeps it alive while a foreground
 * notification with the transfer percentage is shown. If the process is killed,
 * WorkManager restarts the worker and the transfer resumes from the partial
 * file (Range request). Progress is published through the companion
 * [MutableStateFlow]s and collected by [MainActivity].
 */
class OpenClDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "opencl_download"
        private const val NOTIFICATION_ID = 1

        /** Progress (0..1) of the download, or null when idle. */
        val progress = MutableStateFlow<Float?>(null)

        /** Current download speed in bytes per second, or null when idle. */
        val speed = MutableStateFlow<Float?>(null)

        /** 0 = idle, 1 = downloading, 2 = installing. */
        val phase = MutableStateFlow(0)

        /** True while a cloud download is running. */
        val downloading = MutableStateFlow(false)

        /** Final (ok, message) result of the download, or null while running. */
        val status = MutableStateFlow<Pair<Boolean, String>?>(null)

        fun isDownloading() = downloading.value
    }

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override suspend fun doWork(): Result {
        downloading.value = true
        phase.value = 1
        progress.value = 0f
        speed.value = null
        status.value = null

createChannel()

        // When the work is stopped (user cancel or system stop) the job is
        // cancelled; drop the in-flight connection so a blocking read returns
        // right away instead of waiting out the 60s read timeout.
        kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion { activeConnection?.disconnect() }

        // Promote to a foreground service so the download survives the app
        // going to the background. On Android 12+ starting a foreground
        // service is only allowed while the app is visible; if the platform
        // denies it (app was backgrounded when the worker started), degrade
        // to a plain background worker instead of failing the work.
        var foreground = true
        try {
            setForeground(createForegroundInfo(0))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            foreground = false
            Log.w("OpenClDownloadWorker", "foreground denied, running as background worker", e)
        } catch (e: Exception) {
            foreground = false
            Log.w("OpenClDownloadWorker", "foreground promotion failed", e)
        }

        val outcome: Pair<Boolean, String>? = withContext(Dispatchers.IO) {
            val cache = File(applicationContext.cacheDir, "opencl-download.bin")
            try {
                val url = URL(applicationContext.getString(R.string.onboarding_opencl_download_url))
                val t0 = System.currentTimeMillis()
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = -1L
                var done = 0L
                var silentRounds = 0
                var lastPercent = -1
                while (true) {
                    if (!isActive) throw CancellationException()
                    val conn = url.openConnection() as HttpURLConnection
                    activeConnection = conn
                    try {
                        conn.connectTimeout = 20_000
                        conn.readTimeout = 60_000
                        conn.setRequestProperty("User-Agent", "KataGO")
                        conn.instanceFollowRedirects = true
                        val partial = if (cache.exists()) cache.length() else 0L
                        if (partial > 0) conn.setRequestProperty("Range", "bytes=$partial-")
                        val code = conn.responseCode
                        when (code) {
                            HttpURLConnection.HTTP_PARTIAL -> {
                                total = if (conn.contentLengthLong > 0) conn.contentLengthLong + partial else -1L
                                done = partial
                            }
                            HttpURLConnection.HTTP_OK -> {
                                total = conn.contentLengthLong
                                done = 0L
                                cache.delete()
                            }
                            else -> {
                                conn.disconnect()
                                if (++silentRounds >= 10) {
                                    return@withContext false to "Download failed: HTTP $code"
                                }
                                delay(3_000)
                                continue
                            }
                        }
                        val roundStart = done
                        conn.inputStream.use { i ->
                            // Append when resuming from a partial file, otherwise
                            // FileOutputStream would truncate the existing bytes
                            // and corrupt the file.
                            java.io.FileOutputStream(cache, done > 0).use { o ->
                                var n = i.read(buf)
                                while (n >= 0) {
                                    if (!isActive) throw CancellationException()
                                    o.write(buf, 0, n)
                                    done += n
                                    val p = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                                    progress.value = p
                                    val dt = System.currentTimeMillis() - t0
                                    if (dt > 0) speed.value = done * 1000f / dt
                                    val percent = (p * 100).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        if (foreground) {
                                            try {
                                                setForeground(createForegroundInfo(percent))
                                            } catch (e: Exception) {
                                                Log.w("OpenClDownloadWorker", "foreground update failed", e)
                                            }
                                        }
                                    }
                                    n = i.read(buf)
                                }
                            }
                        }
                        silentRounds = if (done > roundStart) 0 else silentRounds + 1
                        if (total <= 0 || done >= total) break
                        delay(3_000)
                    } catch (e: CancellationException) {
                        conn.disconnect()
                        throw e
                    } catch (e: Exception) {
                        conn.disconnect()
                        if (++silentRounds >= 10) {
                            return@withContext false to "Download failed: ${e.message}"
                        }
                        delay(3_000)
                    }
                }

                phase.value = 2
                speed.value = null
                val candidate = extractLibOpenClSo(cache)
                val valid = candidate != null &&
                    candidate.length() > 1_000_000L &&
                    isAarch64Elf(candidate)
                if (!valid) {
                    cache.delete()
                    return@withContext false to applicationContext.getString(R.string.opencl_import_bad)
                }
                progress.value = 0.95f
                if (foreground) {
                    try {
                        setForeground(createForegroundInfo(95))
                    } catch (e: Exception) {
                        Log.w("OpenClDownloadWorker", "foreground update failed", e)
                    }
                }
                val dst = File(applicationContext.filesDir, "libOpenCL.so")
                val tmp = File(applicationContext.filesDir, "libOpenCL.so.tmp")
                candidate.copyTo(tmp, overwrite = true)
                dst.delete()
                if (!tmp.renameTo(dst)) {
                    tmp.copyTo(dst, overwrite = true)
                    tmp.delete()
                }
                cache.delete()
                if (candidate !== cache) candidate.delete()
                true to applicationContext.getString(R.string.opencl_import_ok)
            } catch (e: CancellationException) {
                null
            } catch (e: Exception) {
                false to "Download failed: ${e.message}"
            }
        }

        // When the worker was stopped by the system (rather than finished),
        // preserve the partial file and return retry so WorkManager re-runs
        // the worker and the download resumes via the Range request.
        if (outcome == null) {
            status.value = false to applicationContext.getString(R.string.opencl_import_cancelled)
            downloading.value = false
            progress.value = null
            phase.value = 0
            speed.value = null
            return Result.retry()
        }
        val (ok, msg) = outcome

        status.value = ok to msg
        downloading.value = false
        progress.value = null
        phase.value = 0
        speed.value = null
        return if (ok) Result.success() else Result.failure()
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.opencl_download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(percent: Int): ForegroundInfo {
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                applicationContext.getString(R.string.opencl_download_notification_title, percent)
            )
            .setContentText(
                applicationContext.getString(R.string.onboarding_transfer_percent, percent)
            )
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}