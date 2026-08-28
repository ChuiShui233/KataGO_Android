package com.chuishui.katago.ai.provider

/** Human readable status of a provider. */
enum class ProviderStatus {
    NOT_CONFIGURED,
    AVAILABLE,
    AUTH_FAILED,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    NETWORK_ERROR,
    ERROR,
}

/**
 * Live health of a provider, mutated by the provider implementation and read
 * by the router. Thread-safe.
 */
class ProviderHealth {

    @Volatile
    var status: ProviderStatus = ProviderStatus.NOT_CONFIGURED
        private set

    @Volatile
    var lastSuccess: Long? = null
        private set

    @Volatile
    var lastFailure: Long? = null
        private set

    @Volatile
    var consecutiveFailures: Int = 0
        private set

    @Volatile
    var latencyMs: Long? = null
        private set

    @Volatile
    var rateLimited: Boolean = false
        private set

    @Volatile
    var quotaExceeded: Boolean = false
        private set

    @Synchronized
    fun onConfigured() {
        if (status == ProviderStatus.NOT_CONFIGURED) status = ProviderStatus.AVAILABLE
    }

    @Synchronized
    fun markNotConfigured() {
        status = ProviderStatus.NOT_CONFIGURED
    }

    @Synchronized
    fun onSuccess(latencyMs: Long) {
        lastSuccess = System.currentTimeMillis()
        this.latencyMs = latencyMs
        consecutiveFailures = 0
        rateLimited = false
        quotaExceeded = false
        status = ProviderStatus.AVAILABLE
    }

    @Synchronized
    fun onFailure(status: ProviderStatus, markRateLimited: Boolean = false, markQuota: Boolean = false) {
        lastFailure = System.currentTimeMillis()
        consecutiveFailures++
        if (markRateLimited) rateLimited = true
        if (markQuota) quotaExceeded = true
        when (status) {
            ProviderStatus.RATE_LIMITED -> rateLimited = true
            ProviderStatus.QUOTA_EXCEEDED -> quotaExceeded = true
            else -> {}
        }
        this.status = status
    }

    @Synchronized
    fun reset() {
        status = ProviderStatus.AVAILABLE
        consecutiveFailures = 0
        rateLimited = false
        quotaExceeded = false
        latencyMs = null
    }
}
