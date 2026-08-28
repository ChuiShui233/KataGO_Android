package com.chuishui.katago.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Android-side watchdog for OpenCL autotuning crash recovery.
 *
 * Flow:
 * 1. Before GPU engine start: call prepareForGpuStart()
 * 2. If engine starts cleanly: call onGpuStartSucceeded()
 * 3. If process crashes during autotuning:
 *    - Next launch: hadCrashDuringAutotuning() returns true
 *    - Call handleAutotuneCrash() which:
 *      a. Increments recovery counter
 *      b. If counter < MAX_RECOVERY: writes autotune_recovery.marker
 *      c. If counter >= MAX_RECOVERY: writes use_safe_defaults.marker or disables GPU
 * 4. On clean autotuning completion: call onAutotuningCompleted()
 */
class AutotuneWatchdog(private val context: Context) {
    companion object {
        private const val TAG = "AutotuneWatchdog"
        private const val MAX_AUTOTUNE_RECOVERY = 3
        private const val PREFS_NAME = "autotune_watchdog"
        private const val KEY_RECOVERY_COUNT = "recovery_count"
        private const val KEY_LAST_CRASH_TIMESTAMP = "last_crash_timestamp"
        private const val KEY_GPU_PERMANENTLY_DISABLED = "gpu_permanently_disabled"

        const val RECOVERY_MARKER = "autotune_recovery.marker"
        const val SAFE_DEFAULTS_MARKER = "use_safe_defaults.marker"
        const val CHECKPOINT_FILE = "tuning_checkpoint.json"
        const val BLACKLIST_FILE = "tuning_blacklist.json"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The opencltuning directory where checkpoint/blacklist/marker files live */
    val tuningDir: File
        get() = File(context.cacheDir, "opencltuning")

    /** Prepare before starting the GPU engine. Sets the pending flag. */
    fun prepareForGpuStart() {
        val katagoPrefs = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        katagoPrefs.edit().putBoolean("gpu_attempt_pending", true).commit()
        tuningDir.mkdirs()
        Log.i(TAG, "Preparing for GPU start, recovery count=" + getRecoveryCount())
    }

    /** Call after the GPU engine starts cleanly. Clears pending flag and resets recovery count. */
    fun onGpuStartSucceeded() {
        val katagoPrefs = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        katagoPrefs.edit().putBoolean("gpu_attempt_pending", false).commit()
        prefs.edit().putInt(KEY_RECOVERY_COUNT, 0).apply()
        clearMarkers()
        Log.i(TAG, "GPU start succeeded, recovery count reset")
    }

    /** Check if the previous run crashed during autotuning (not during normal play) */
    fun hadCrashDuringAutotuning(): Boolean {
        val katagoPrefs = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        val attemptPending = katagoPrefs.getBoolean("gpu_attempt_pending", false)
        val hadNativeCrash = checkNativeCrashMarker()
        if (attemptPending || hadNativeCrash) {
            val checkpoint = File(tuningDir, CHECKPOINT_FILE)
            if (checkpoint.exists()) {
                try {
                    val json = JSONObject(checkpoint.readText())
                    val status = json.optString("status", "")
                    if (status == "in_progress") {
                        Log.i(TAG, "Detected crash during autotuning (checkpoint status=in_progress)")
                        return true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read checkpoint: " + e.message)
                }
            }
            if (attemptPending) {
                Log.i(TAG, "Detected crash before autotuning could save checkpoint")
                return true
            }
        }
        return false
    }

    /**
     * Handle a crash that occurred during autotuning.
     * Returns the action to take.
     */
    fun handleAutotuneCrash(): CrashRecoveryAction {
        val count = getRecoveryCount() + 1
        prefs.edit()
            .putInt(KEY_RECOVERY_COUNT, count)
            .putLong(KEY_LAST_CRASH_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.w(TAG, "Autotune crash detected, recovery attempt " + count + "/" + MAX_AUTOTUNE_RECOVERY)

        val katagoPrefs = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        katagoPrefs.edit().putBoolean("gpu_attempt_pending", false).commit()

        return if (count >= MAX_AUTOTUNE_RECOVERY) {
            Log.e(TAG, "Max recovery attempts reached")
            if (hasSuccessfulCheckpoint()) {
                writeMarker(SAFE_DEFAULTS_MARKER, "max_recovery_exceeded")
                CrashRecoveryAction.USE_SAFE_DEFAULTS
            } else {
                katagoPrefs.edit().putBoolean("gpu_disabled", true).apply()
                prefs.edit().putBoolean(KEY_GPU_PERMANENTLY_DISABLED, true).apply()
                CrashRecoveryAction.DISABLE_GPU
            }
        } else {
            writeMarker(RECOVERY_MARKER, "crash_recovery_attempt_" + count)
            CrashRecoveryAction.RETRY_WITH_RECOVERY
        }
    }

    /** Check if checkpoint has a lastSuccessfulConfig */
    private fun hasSuccessfulCheckpoint(): Boolean {
        val checkpoint = File(tuningDir, CHECKPOINT_FILE)
        if (!checkpoint.exists()) return false
        return try {
            val json = JSONObject(checkpoint.readText())
            json.has("lastSuccessfulConfig") && json.getString("lastSuccessfulConfig").isNotEmpty()
        } catch (e: Exception) { false }
    }

    /** Call when autotuning completes successfully */
    fun onAutotuningCompleted() {
        prefs.edit().putInt(KEY_RECOVERY_COUNT, 0).apply()
        clearMarkers()
        Log.i(TAG, "Autotuning completed successfully, recovery count reset")
    }

    /** Get current recovery count */
    fun getRecoveryCount(): Int = prefs.getInt(KEY_RECOVERY_COUNT, 0)

    /** Check if GPU is permanently disabled by the watchdog */
    fun isGpuPermanentlyDisabled(): Boolean = prefs.getBoolean(KEY_GPU_PERMANENTLY_DISABLED, false)

    /** Clear all marker files */
    fun clearMarkers() {
        File(tuningDir, RECOVERY_MARKER).delete()
        File(tuningDir, SAFE_DEFAULTS_MARKER).delete()
    }

    private fun writeMarker(name: String, reason: String) {
        try {
            val marker = File(tuningDir, name)
            marker.writeText(reason + "\n")
            Log.i(TAG, "Wrote marker: " + name + " reason=" + reason)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write marker " + name + ": " + e.message)
        }
    }

    private fun checkNativeCrashMarker(): Boolean {
        return try { File(context.filesDir, "gpu_crashed.marker").exists() }
        catch (e: Exception) { false }
    }

    enum class CrashRecoveryAction {
        RETRY_WITH_RECOVERY,
        USE_SAFE_DEFAULTS,
        DISABLE_GPU
    }
}
