package com.sielo.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_history")
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val albumName: String? = null,
    val thumbnailUrl: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long = 0L,
    val songDurationMs: Long = 0L,
    val hourOfDay: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
    val dayOfWeek: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK),
    val month: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
    val year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val completed: Boolean = false
)