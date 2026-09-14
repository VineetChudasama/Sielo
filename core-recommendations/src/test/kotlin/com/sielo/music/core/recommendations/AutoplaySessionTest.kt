package com.sielo.music.core.recommendations

import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.recommendations.autoplay.AutoplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplaySessionTest {

    private fun createTrack(id: String, title: String, artist: String): SieloTrack {
        return SieloTrack(id = id, title = title, artist = artist)
    }

    @Test
    fun testSessionReseedContextSeparatesPlayedAndUnplayedSongs() {
        val session = AutoplaySession()

        val seed1 = createTrack("seed1", "Song 1", "Artist 1")
        session.startOrReseedSession(seed1)

        val queue1 = listOf(
            createTrack("q1", "Queued 1", "Artist Q1"),
            createTrack("q2", "Queued 2", "Artist Q2"),
            createTrack("q3", "Queued 3", "Artist Q3")
        )
        session.appendBatch(queue1)

        // Simulate playing q1
        session.onTrackPlaying(queue1[0])

        // User manually selects a completely different seed track
        val seed2 = createTrack("manual_seed", "Manual Pick", "Artist Manual")
        val reseedContext = session.startOrReseedSession(seed2)

        // Verify seedSong
        assertEquals("manual_seed", reseedContext.seedSong.id)

        // Verify excludedSongIds has actually played songs: seed1 and q1
        assertTrue("seed1 should be in excludedSongIds", reseedContext.excludedSongIds.contains("seed1"))
        assertTrue("q1 should be in excludedSongIds", reseedContext.excludedSongIds.contains("q1"))
        assertFalse("q2 should NOT be in excludedSongIds", reseedContext.excludedSongIds.contains("q2"))
        assertFalse("q3 should NOT be in excludedSongIds", reseedContext.excludedSongIds.contains("q3"))

        // Verify reentryCandidates has unplayed songs: q2 and q3
        val reentryIds = reseedContext.reentryCandidates.map { it.id }.toSet()
        assertTrue("q2 should be a reentryCandidate", reentryIds.contains("q2"))
        assertTrue("q3 should be a reentryCandidate", reentryIds.contains("q3"))
        assertFalse("q1 should NOT be a reentryCandidate", reentryIds.contains("q1"))
    }

    @Test
    fun testTrackInGeneratedQueueDetection() {
        val session = AutoplaySession()
        val seed = createTrack("seed", "Seed", "Artist")
        session.startOrReseedSession(seed)

        val trackA = createTrack("a", "Track A", "Artist A")
        val trackB = createTrack("b", "Track B", "Artist B")
        session.appendBatch(listOf(trackA, trackB))

        assertTrue(session.isTrackInGeneratedQueue("a"))
        assertTrue(session.isTrackInGeneratedQueue("b"))
        assertFalse(session.isTrackInGeneratedQueue("c"))

        session.onTrackPlaying(trackA)
        assertFalse("Played track is removed from generatedQueue", session.isTrackInGeneratedQueue("a"))
        assertTrue("Remaining track is still in generatedQueue", session.isTrackInGeneratedQueue("b"))
    }
}
