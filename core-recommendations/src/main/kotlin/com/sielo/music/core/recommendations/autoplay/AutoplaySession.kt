package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ReseedContext(
    val seedSong: SieloTrack,
    val excludedSongIds: Set<String>,
    val reentryCandidates: List<SieloTrack>
)

@Singleton
class AutoplaySession @Inject constructor() {

    private val _currentSeed = MutableStateFlow<SieloTrack?>(null)
    val currentSeed: StateFlow<SieloTrack?> = _currentSeed.asStateFlow()

    private val playedSongIds = mutableSetOf<String>()
    private val generatedQueue = mutableListOf<SieloTrack>()

    private val lock = Any()

    /**
     * Initializes or reseeds an autoplay session.
     * When user manually skips to an unpredicted song outside the generated queue,
     * the previously PLAYED songs become excludedSongIds (hard exclusion), and the
     * remaining UNPLAYED recommendations become reentryCandidates.
     */
    fun startOrReseedSession(newSeed: SieloTrack): ReseedContext = synchronized(lock) {
        val excludedSongIds = playedSongIds.toSet()
        val reentryCandidates = generatedQueue.filter { it.id !in excludedSongIds && it.id != newSeed.id }

        playedSongIds.clear()
        playedSongIds.add(newSeed.id)
        generatedQueue.clear()
        _currentSeed.value = newSeed

        ReseedContext(
            seedSong = newSeed,
            excludedSongIds = excludedSongIds,
            reentryCandidates = reentryCandidates
        )
    }

    /**
     * Called when a track from the queue starts playback.
     */
    fun onTrackPlaying(track: SieloTrack) = synchronized(lock) {
        playedSongIds.add(track.id)
        generatedQueue.removeAll { it.id == track.id }
    }

    /**
     * Appends newly generated batch recommendations to the active queue.
     */
    fun appendBatch(batch: List<SieloTrack>) = synchronized(lock) {
        val newItems = batch.filter { it.id !in playedSongIds && it.id !in generatedQueue.map { g -> g.id } }
        generatedQueue.addAll(newItems)
    }

    /**
     * Returns true if the track was already part of the active generated autoplay queue.
     */
    fun isTrackInGeneratedQueue(trackId: String): Boolean = synchronized(lock) {
        generatedQueue.any { it.id == trackId }
    }

    /**
     * Returns remaining unplayed items in the session queue.
     */
    fun getRemainingGeneratedQueue(): List<SieloTrack> = synchronized(lock) {
        generatedQueue.toList()
    }

    /**
     * Clears all session state.
     */
    fun reset() = synchronized(lock) {
        playedSongIds.clear()
        generatedQueue.clear()
        _currentSeed.value = null
    }
}
