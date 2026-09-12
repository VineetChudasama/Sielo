package com.sielo.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sielo.music.core.database.entity.SearchPlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchPlayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchPlay(entity: SearchPlayHistoryEntity): Long

    @Query("""
        SELECT * FROM search_play_history 
        WHERE id IN (
            SELECT MAX(id) FROM search_play_history GROUP BY songId
        )
        ORDER BY playedAtMs DESC 
        LIMIT :limit
    """)
    fun getRecentSearchPlays(limit: Int = 10): Flow<List<SearchPlayHistoryEntity>>

    @Query("DELETE FROM search_play_history")
    suspend fun clearSearchPlays()
}
