package com.sielo.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sielo.music.core.database.entity.ListeningEventEntity
import kotlinx.coroutines.flow.Flow

data class ArtistStat(
    val artistName: String,
    val playCount: Int,
    val totalDurationMs: Long,
    val thumbnailUrl: String?
)

data class SongStat(
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val playCount: Int,
    val totalDurationMs: Long,
    val thumbnailUrl: String?
)

data class TimeOfDayStat(
    val timeSlot: String,
    val count: Int
)

@Dao
interface ListeningHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ListeningEventEntity): Long

    @Query("SELECT * FROM listening_history ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<ListeningEventEntity>>

    @Query("SELECT SUM(durationPlayedMs) FROM listening_history WHERE timestampMs >= :sinceMs")
    fun getTotalListeningTime(sinceMs: Long): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT songId) FROM listening_history WHERE timestampMs >= :sinceMs")
    fun getTotalUniqueSongs(sinceMs: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artistName) FROM listening_history WHERE timestampMs >= :sinceMs")
    fun getTotalUniqueArtists(sinceMs: Long): Flow<Int>

    @Query("""
        SELECT artistName, COUNT(*) as playCount, SUM(durationPlayedMs) as totalDurationMs, MAX(thumbnailUrl) as thumbnailUrl 
        FROM listening_history 
        WHERE timestampMs >= :sinceMs 
        GROUP BY artistName 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun getTopArtists(sinceMs: Long, limit: Int = 5): Flow<List<ArtistStat>>

    @Query("""
        SELECT songId, songTitle, artistName, COUNT(*) as playCount, SUM(durationPlayedMs) as totalDurationMs, MAX(thumbnailUrl) as thumbnailUrl 
        FROM listening_history 
        WHERE timestampMs >= :sinceMs 
        GROUP BY songId 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun getTopSongs(sinceMs: Long, limit: Int = 10): Flow<List<SongStat>>

    @Query("SELECT COUNT(*) FROM listening_history WHERE month = :month AND year = :year")
    suspend fun getPlayCountForMonth(month: Int, year: Int): Int

    @Query("SELECT SUM(durationPlayedMs) FROM listening_history WHERE month = :month AND year = :year")
    suspend fun getListeningDurationForMonth(month: Int, year: Int): Long?

    @Query("SELECT hourOfDay, COUNT(*) as count, SUM(durationPlayedMs) as totalDurationMs FROM listening_history WHERE timestampMs >= :sinceMs GROUP BY hourOfDay")
    fun getHourlyDistribution(sinceMs: Long): Flow<List<HourCount>>
}

data class HourCount(
    val hourOfDay: Int,
    val count: Int,
    val totalDurationMs: Long = 0L
)