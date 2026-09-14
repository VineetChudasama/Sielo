package com.sielo.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "similar_artist_cache")
data class SimilarArtistCacheEntity(
    @PrimaryKey val seedArtistId: String,
    val resultsJson: String,
    val cachedAt: Long = System.currentTimeMillis()
)
