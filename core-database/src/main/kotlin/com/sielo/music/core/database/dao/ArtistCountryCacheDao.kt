package com.sielo.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sielo.music.core.database.entity.ArtistCountryCacheEntity

@Dao
interface ArtistCountryCacheDao {
    @Query("SELECT * FROM artist_country_cache WHERE artistName = :artistName LIMIT 1")
    suspend fun getCountry(artistName: String): ArtistCountryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountry(countryCache: ArtistCountryCacheEntity)

    @Query("DELETE FROM artist_country_cache WHERE artistName = :artistName")
    suspend fun deleteCountry(artistName: String)

    @Query("DELETE FROM artist_country_cache WHERE cachedAt < :expiredThresholdMs")
    suspend fun deleteExpired(expiredThresholdMs: Long)
}
