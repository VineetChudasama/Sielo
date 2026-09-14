package com.sielo.music.core.recommendations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarArtistsScoringTest {

    @Test
    fun testJaccardTagOverlapCalculation() {
        val seedTags = listOf("edm", "electronic", "dance", "house", "progressive house")
        val candidateTags = listOf("dance", "electronic", "pop", "club")

        val shared = seedTags.intersect(candidateTags.toSet())
        val union = (seedTags + candidateTags).distinct()

        val score = shared.size.toDouble() / union.size.toDouble()

        // Shared: ["dance", "electronic"] (2)
        // Union: ["edm", "electronic", "dance", "house", "progressive house", "pop", "club"] (7)
        // Score: 2 / 7 = ~0.2857
        assertEquals(2, shared.size)
        assertEquals(7, union.size)
        assertEquals(2.0 / 7.0, score, 0.001)
    }

    @Test
    fun testNormalWeightedScoreCalculation() {
        val lastFmScore = 0.90
        val tagOverlap = 0.50
        val originBonus = 1.0 // Same country

        val finalScore = (0.6 * lastFmScore) + (0.25 * tagOverlap) + (0.15 * originBonus)
        // 0.6 * 0.90 = 0.54
        // 0.25 * 0.50 = 0.125
        // 0.15 * 1.0 = 0.15
        // Total = 0.815
        assertEquals(0.815, finalScore, 0.0001)
    }

    @Test
    fun testFallbackWeightedScoreCalculation() {
        val tagOverlap = 0.80
        val originBonus = 1.0

        val fallbackScore = (0.7 * tagOverlap) + (0.3 * originBonus)
        // 0.7 * 0.80 = 0.56
        // 0.3 * 1.0 = 0.30
        // Total = 0.86
        assertEquals(0.86, fallbackScore, 0.0001)
    }
}
