package com.sielo.music.core.playlistimport.model

data class ImportedTrackCandidate(
    val title: String,
    val artistGuess: String,
    val sourcePlatform: String
)
