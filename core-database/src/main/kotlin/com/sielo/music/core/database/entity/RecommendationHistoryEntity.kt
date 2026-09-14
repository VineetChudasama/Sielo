package com.sielo.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_history")
data class RecommendationHistoryEntity(
    @PrimaryKey val songId: String,
    val lastRecommendedDate: String // ISO LocalDate (e.g. "2026-09-14") derived from ZoneId.systemDefault()
)
