package com.chuishui.katago.ai.log

/**
 * Structured log channel for the AI subsystem. Providers and router depend on
 * this interface so unit tests can inject a no-op logger (no Android deps).
 */
interface AiLogger {

    fun log(event: String, details: String)

    fun warn(event: String, details: String)

    fun error(event: String, details: String, throwable: Throwable? = null)

    /** Strips anything that looks like a bearer key out of a message. */
    fun sanitize(message: String): String {
        return message
            .replace(Regex("""(?i)Bearer\s+\S+"""), "Bearer [REDACTED]")
            .replace(Regex("""(?i)api[_-]?key["']?\s*[:=]\s*["']?\S+"""), "apiKey=[REDACTED]")
            .replace(Regex("""(?i)x-api-key["']?\s*:\s*\S+"""), "x-api-key=[REDACTED]")
    }
}

/** No-op logger for unit tests / non-Android environments. */
object NoopAiLogger : AiLogger {
    override fun log(event: String, details: String) {}
    override fun warn(event: String, details: String) {}
    override fun error(event: String, details: String, throwable: Throwable?) {}
}

/** Logcat-backed logger for the app. Never logs API keys or secrets. */
object LogcatAiLogger : AiLogger {
    private const val TAG = "AI"

    override fun log(event: String, details: String) {
        android.util.Log.d(TAG, "[AI] event=$event $details")
    }

    override fun warn(event: String, details: String) {
        android.util.Log.w(TAG, "[AI] event=$event $details")
    }

    override fun error(event: String, details: String, throwable: Throwable?) {
        if (throwable != null) android.util.Log.e(TAG, "[AI] event=$event $details", throwable)
        else android.util.Log.e(TAG, "[AI] event=$event $details")
    }
}