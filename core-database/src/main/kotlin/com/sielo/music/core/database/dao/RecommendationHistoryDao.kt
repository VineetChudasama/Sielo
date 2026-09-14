package com.sielo.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sielo.music.core.database.entity.RecommendationHistoryEntity

@Dao
interface RecommendationHistoryDao {

    @Query("SELECT COUNT(*) FROM recommendation_history WHERE songId = :songId AND lastRecommendedDate = :today")
    suspend fun countRecommendedToday(songId: String, today: String): Int

    suspend fun isEligibleToday(songId: String, today: String): Boolean {
        return countRecommendedToday(songId, today) == 0
    }

    @Query("SELECT songId FROM recommendation_history WHERE songId IN (:songIds) AND lastRecommendedDate = :today")
    suspend fun getIneligibleSongIds(songIds: List<String>, today: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RecommendationHistoryEntity>)

    suspend fun markRecommended(songIds: List<String>, today: String) {
        if (songIds.isEmpty()) return
        val entities = songIds.distinct().map { RecommendationHistoryEntity(songId = it, lastRecommendedDate = today) }
        upsertAll(entities)
    }
}
