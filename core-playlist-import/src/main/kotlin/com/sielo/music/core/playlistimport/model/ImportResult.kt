package com.sielo.music.core.playlistimport.model

data class ImportResult(
    val albumId: String,
    val totalCandidates: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val unimportedTracks: List<ImportedTrackCandidate> = emptyList()
)

enum class ImportSource {
    YOUTUBE,
    SPOTIFY_OAUTH,
    SPOTIFY_MANUAL_PASTE
}
