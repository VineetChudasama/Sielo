package com.sielo.music.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseCleaner @Inject constructor(
    private val db: SieloDatabase
) {
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                db.clearAllTables()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
