package com.sielo.music.core.recommendations

import com.sielo.music.core.recommendations.util.RateLimiter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun testRateLimiterEnforcesMinInterval() = runBlocking {
        // 2 requests per second -> ~500ms min interval
        val limiter = RateLimiter(requestsPerSecond = 2.0)
        val start = System.currentTimeMillis()

        limiter.acquire { "first" }
        limiter.acquire { "second" }

        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected at least ~450ms elapsed, got $elapsed ms", elapsed >= 450)
    }
}
