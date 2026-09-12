package com.sielo.music.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SearchHistoryDao
import com.sielo.music.core.database.dao.SearchPlayHistoryDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.database.entity.SearchHistoryEntity
import com.sielo.music.core.database.entity.SearchPlayHistoryEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        FavoriteTrackEntity::class,
        ListeningEventEntity::class,
        SearchHistoryEntity::class,
        SearchPlayHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SieloDatabase : RoomDatabase() {
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun searchPlayHistoryDao(): SearchPlayHistoryDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SieloDatabase {
        return Room.databaseBuilder(
            context,
            SieloDatabase::class.java,
            "sielo_music.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideFavoriteTrackDao(db: SieloDatabase): FavoriteTrackDao {
        return db.favoriteTrackDao()
    }

    @Provides
    fun provideListeningHistoryDao(db: SieloDatabase): ListeningHistoryDao {
        return db.listeningHistoryDao()
    }

    @Provides
    fun provideSearchHistoryDao(db: SieloDatabase): SearchHistoryDao {
        return db.searchHistoryDao()
    }

    @Provides
    fun provideSearchPlayHistoryDao(db: SieloDatabase): SearchPlayHistoryDao {
        return db.searchPlayHistoryDao()
    }
}