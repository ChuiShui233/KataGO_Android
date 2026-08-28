package com.chuishui.katago.ai.router

/** How the router picks a provider for a request. */
enum class AiRoutePolicy {
    /** Follow the user's explicit provider choice. */
    MANUAL,

    /** Priority: user choice → free → cheap → fast → high quality. */
    AUTO,

    /** Cheapest provider first. */
    CHEAPEST,

    /** Lowest observed latency first. */
    FASTEST,

    /** Highest configured priority (quality) first. */
    BEST,

    /** Try providers in priority order until one succeeds. */
    FAILOVER,

    /** Rotate providers evenly. */
    ROUND_ROBIN,
}