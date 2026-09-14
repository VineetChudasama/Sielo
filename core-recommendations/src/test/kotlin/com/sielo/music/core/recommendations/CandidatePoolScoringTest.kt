package com.sielo.music.core.recommendations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class CandidatePoolScoringTest {

    @Test
    fun testPoolARecencyDecayScoring() {
        val playCount = 10
        val halfLifeDays = 30.0

        // 0 days ago (played right now)
        val scoreNow = playCount * exp(-0.0 / halfLifeDays)
        assertEquals(10.0, scoreNow, 0.001)

        // 30 days ago
        val score30Days = playCount * exp(-30.0 / halfLifeDays)
        assertEquals(10.0 * exp(-1.0), score30Days, 0.001)
        assertTrue(score30Days < scoreNow)

        // 60 days ago
        val score60Days = playCount * exp(-60.0 / halfLifeDays)
        assertEquals(10.0 * exp(-2.0), score60Days, 0.001)
        assertTrue(score60Days < score30Days)
    }

    @Test
    fun testIsoLocalDateComparison() {
        val today = "2026-09-14"
        val yesterday = "2026-09-13"

        // Eligible if no row exists or lastRecommendedDate != today
        fun isEligible(lastRecommendedDate: String?, currentDay: String): Boolean {
            return lastRecommendedDate == null || lastRecommendedDate != currentDay
        }

        assertTrue("New song is eligible today", isEligible(null, today))
        assertTrue("Song recommended yesterday is eligible today", isEligible(yesterday, today))
        org.junit.Assert.assertFalse("Song recommended today is not eligible today", isEligible(today, today))
    }
}
