package com.sielo.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_country_cache")
data class ArtistCountryCacheEntity(
    @PrimaryKey val artistName: String,
    val country: String?,
    val cachedAt: Long = System.currentTimeMillis()
)
