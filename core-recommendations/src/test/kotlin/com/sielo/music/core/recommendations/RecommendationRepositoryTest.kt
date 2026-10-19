package com.sielo.music.core.recommendations

import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.network.recommendations.ClientListeningEvent
import com.sielo.music.core.network.recommendations.RecommendationApiClient
import com.sielo.music.core.recommendations.events.ListeningEventDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRepositoryTest {

    private val sampleTrack = SieloTrack(
        id = "track_123",
        title = "Kesariya",
        artist = "Arijit Singh",
        album = "Brahmastra",
        durationSeconds = 268
    )

    @Test
    fun testListeningEventDispatcherQueueAndFlush() {
        var dispatchedBatchSize = 0
        val fakeClient = object : RecommendationApiClient() {
            override suspend fun sendBatchEvents(events: List<ClientListeningEvent>): Boolean {
                dispatchedBatchSize = events.size
                return true
            }
        }

        val dispatcher = ListeningEventDispatcher(fakeClient)
        dispatcher.currentUserIdProvider = { "user_abc" }

        dispatcher.recordPlayStarted(sampleTrack, "autoplay")
        dispatcher.recordProgressCheckpoint(sampleTrack, 45_000L, "autoplay")
        dispatcher.recordPlayCompleted(sampleTrack, 268_000L, "autoplay")
        dispatcher.recordLiked(sampleTrack)
        dispatcher.recordSkipped(sampleTrack, 12_000L, "autoplay")

        runBlocking {
            dispatcher.flush()
        }

        assertEquals(5, dispatchedBatchSize)
    }

    @Test
    fun testPrivateListeningSuppressesEvents() {
        var eventCount = 0
        val fakeClient = object : RecommendationApiClient() {
            override suspend fun sendBatchEvents(events: List<ClientListeningEvent>): Boolean {
                eventCount = events.size
                return true
            }
        }

        val dispatcher = ListeningEventDispatcher(fakeClient)
        dispatcher.isPrivateListeningEnabled = true

        dispatcher.recordPlayStarted(sampleTrack)
        dispatcher.recordLiked(sampleTrack)

        runBlocking {
            dispatcher.flush()
        }

        assertEquals(0, eventCount)
    }
}
