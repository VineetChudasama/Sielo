package com.sielo.music.core.audio

import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.network.models.SieloTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQueueTest {

    private fun createTrack(id: String, title: String, artist: String): SieloTrack {
        return SieloTrack(id = id, title = title, artist = artist)
    }

    @Test
    fun testAppendTracksToExistingQueue() {
        val initialTracks = listOf(
            createTrack("1", "Song 1", "Artist 1"),
            createTrack("2", "Song 2", "Artist 2")
        )
        val state = PlaybackState(
            currentTrack = initialTracks[0],
            queue = initialTracks,
            queueIndex = 0
        )

        val newTracks = listOf(
            createTrack("3", "Song 3", "Artist 3"),
            createTrack("4", "Song 4", "Artist 4")
        )

        val updatedQueue = state.queue + newTracks
        val updatedState = state.copy(queue = updatedQueue)

        assertEquals(4, updatedState.queue.size)
        assertEquals("3", updatedState.queue[2].id)
        assertEquals("4", updatedState.queue[3].id)
    }

    @Test
    fun testAppendEmptyListKeepsQueueUnchanged() {
        val initialTracks = listOf(createTrack("1", "Song 1", "Artist 1"))
        val state = PlaybackState(
            currentTrack = initialTracks[0],
            queue = initialTracks,
            queueIndex = 0
        )

        val newTracks = emptyList<SieloTrack>()
        val updatedQueue = if (newTracks.isEmpty()) state.queue else state.queue + newTracks

        assertEquals(1, updatedQueue.size)
        assertEquals("1", updatedQueue[0].id)
    }
}
