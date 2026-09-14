package com.sielo.music.core.recommendations.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe, coroutine-based rate limiter ensuring execution intervals
 * respect target requests-per-second thresholds (e.g., MusicBrainz 1 req/sec, Last.fm 5 req/sec).
 */
class RateLimiter(
    requestsPerSecond: Double
) {
    private val minIntervalMs = (1000.0 / requestsPerSecond).toLong()
    private val mutex = Mutex()
    private var lastExecutionTimeMs = 0L

    suspend fun <T> acquire(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastExecutionTimeMs
            if (timeSinceLast < minIntervalMs) {
                delay(minIntervalMs - timeSinceLast)
            }
            lastExecutionTimeMs = System.currentTimeMillis()
        }
        return block()
    }
}
