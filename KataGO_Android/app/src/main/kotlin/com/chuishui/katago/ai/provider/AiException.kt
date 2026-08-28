package com.chuishui.katago.ai.provider

/**
 * Typed failures raised by providers and mapped from vendor HTTP errors.
 * The router catches these to decide failover.
 */
sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Provider has no API key configured. */
    class NotConfigured(message: String = "provider not configured") : AiException(message)

    /** HTTP 401 / 403 — bad or expired key. */
    class AuthFailed(message: String = "authentication failed") : AiException(message)

    /** HTTP 429 — rate limited. */
    class RateLimited(message: String = "rate limited", val retryAfterMs: Long? = null) : AiException(message)

    /** HTTP 429 with quota header, or provider-specific "out of quota". */
    class QuotaExceeded(message: String = "quota exceeded") : AiException(message)

    /** DNS / connect / read failures. */
    class NetworkError(message: String = "network error", cause: Throwable? = null) : AiException(message, cause)

    /** Request timed out. */
    class Timeout(message: String = "timeout", cause: Throwable? = null) : AiException(message, cause)

    /** HTTP 5xx or unexpected status. */
    class ServerError(message: String = "server error", val statusCode: Int? = null) : AiException(message)

    /** Response could not be parsed. */
    class ParseError(message: String = "parse error", cause: Throwable? = null) : AiException(message, cause)
}
