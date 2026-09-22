package com.sielo.music.core.playlistimport.repository

import com.sielo.music.core.network.models.SieloTrack

interface PlaylistCreator {
    suspend fun createPlaylist(name: String, tracks: List<SieloTrack>): String
}
