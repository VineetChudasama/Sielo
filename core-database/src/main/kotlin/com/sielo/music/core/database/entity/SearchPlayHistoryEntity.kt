package com.sielo.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_play_history")
data class SearchPlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val albumName: String? = null,
    val thumbnailUrl: String? = null,
    val playedAtMs: Long = System.currentTimeMillis()
)
