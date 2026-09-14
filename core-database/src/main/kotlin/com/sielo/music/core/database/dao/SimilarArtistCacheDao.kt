package com.sielo.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sielo.music.core.database.entity.SimilarArtistCacheEntity

@Dao
interface SimilarArtistCacheDao {
    @Query("SELECT * FROM similar_artist_cache WHERE seedArtistId = :seedArtistId LIMIT 1")
    suspend fun getCache(seedArtistId: String): SimilarArtistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: SimilarArtistCacheEntity)

    @Query("DELETE FROM similar_artist_cache WHERE seedArtistId = :seedArtistId")
    suspend fun deleteCache(seedArtistId: String)

    @Query("DELETE FROM similar_artist_cache WHERE cachedAt < :expiredThresholdMs")
    suspend fun deleteExpired(expiredThresholdMs: Long)
}
