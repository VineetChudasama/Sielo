package com.sielo.music.core.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sielo.music.core.auth.model.UserProfile
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylist
import com.sielo.music.core.playlist.UserPlaylistsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val userPlaylistsRepository: UserPlaylistsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    companion object {
        private const val COLLECTION_USERS = "sielo_users"
        private const val DOC_PROFILE = "profile"
        private const val DOC_LIBRARY = "library"
        private const val DOC_PLAYLISTS = "playlists"
    }

    /**
     * Backup user data to Firestore in the background.
     */
    fun backupUserData(user: UserProfile) {
        val cleanEmail = user.email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) {
            // Do not sync guest accounts to cloud
            return
        }

        scope.launch {
            try {
                val userRef = firestore.collection(COLLECTION_USERS).document(cleanEmail)

                // 1. Profile document
                val profileMap = hashMapOf(
                    "id" to user.id,
                    "name" to user.name,
                    "email" to user.email,
                    "photoUrl" to (user.photoUrl ?: ""),
                    "bio" to (user.bio ?: ""),
                    "username" to (user.username ?: ""),
                    "hasCompletedOnboarding" to user.hasCompletedOnboarding,
                    "favoriteArtists" to user.favoriteArtists,
                    "favoriteGenres" to user.favoriteGenres,
                    "artistTasteWeights" to user.artistTasteWeights,
                    "genreTasteWeights" to user.genreTasteWeights,
                    "createdAt" to user.createdAt,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                userRef.collection("data").document(DOC_PROFILE)
                    .set(profileMap, SetOptions.merge())
                    .await()

                // 2. Favorites & Library document
                val currentFavorites = favoriteTrackDao.getAllFavoritesList()
                val favoritesData = currentFavorites.map { fav ->
                    mapOf(
                        "id" to fav.id,
                        "title" to fav.title,
                        "artist" to fav.artist,
                        "thumbnailUrl" to (fav.thumbnailUrl ?: ""),
                        "addedAt" to fav.addedAt
                    )
                }
                userRef.collection("data").document(DOC_LIBRARY)
                    .set(
                        mapOf(
                            "favorites" to favoritesData,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                // 3. Custom Playlists document
                val currentPlaylists = userPlaylistsRepository.getAllPlaylists()
                val playlistsData = currentPlaylists.map { pl ->
                    mapOf(
                        "id" to pl.id,
                        "title" to pl.title,
                        "description" to pl.description,
                        "coverUrl" to (pl.coverUrl ?: ""),
                        "createdAt" to pl.createdAt,
                        "tracks" to pl.tracks.map { track ->
                            mapOf(
                                "id" to track.id,
                                "title" to track.title,
                                "artist" to track.artist,
                                "durationText" to track.durationText,
                                "thumbnailUrl" to (track.thumbnailUrl ?: "")
                            )
                        }
                    )
                }
                userRef.collection("data").document(DOC_PLAYLISTS)
                    .set(
                        mapOf(
                            "playlists" to playlistsData,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Restore user library, playlists, and profile from Firestore cloud.
     * Returns true if cloud data was restored, false otherwise.
     */
    suspend fun restoreUserData(email: String): UserProfile? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) return null

        return try {
            val userRef = firestore.collection(COLLECTION_USERS).document(cleanEmail)

            // 1. Fetch Profile document
            val profileDoc = userRef.collection("data").document(DOC_PROFILE).get().await()
            var cloudProfile: UserProfile? = null

            if (profileDoc.exists()) {
                val data = profileDoc.data ?: emptyMap<String, Any>()
                @Suppress("UNCHECKED_CAST")
                val favoriteArtists = (data["favoriteArtists"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val favoriteGenres = (data["favoriteGenres"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val artistTasteWeights = (data["artistTasteWeights"] as? Map<String, Any>)
                    ?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val genreTasteWeights = (data["genreTasteWeights"] as? Map<String, Any>)
                    ?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap()

                cloudProfile = UserProfile(
                    id = data["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                    name = data["name"] as? String ?: cleanEmail.substringBefore("@"),
                    email = cleanEmail,
                    photoUrl = (data["photoUrl"] as? String)?.takeIf { it.isNotBlank() },
                    provider = com.sielo.music.core.auth.model.AuthProvider.GOOGLE,
                    hasCompletedOnboarding = data["hasCompletedOnboarding"] as? Boolean ?: true,
                    favoriteArtists = favoriteArtists,
                    favoriteGenres = favoriteGenres,
                    artistTasteWeights = artistTasteWeights,
                    genreTasteWeights = genreTasteWeights,
                    bio = (data["bio"] as? String)?.takeIf { it.isNotBlank() },
                    username = (data["username"] as? String)?.takeIf { it.isNotBlank() },
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }

            // 2. Fetch Favorites document & restore into local database
            val libraryDoc = userRef.collection("data").document(DOC_LIBRARY).get().await()
            if (libraryDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val favoritesListRaw = libraryDoc.get("favorites") as? List<Map<String, Any>>
                if (!favoritesListRaw.isNullOrEmpty()) {
                    val entities = favoritesListRaw.mapNotNull { item ->
                        val id = item["id"] as? String ?: return@mapNotNull null
                        val title = item["title"] as? String ?: ""
                        val artist = item["artist"] as? String ?: ""
                        val thumb = item["thumbnailUrl"] as? String
                        val addedAt = (item["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        FavoriteTrackEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            thumbnailUrl = thumb?.takeIf { it.isNotBlank() },
                            addedAt = addedAt
                        )
                    }
                    if (entities.isNotEmpty()) {
                        favoriteTrackDao.insertFavorites(entities)
                    }
                }
            }

            // 3. Fetch Playlists document & restore into local storage
            val playlistsDoc = userRef.collection("data").document(DOC_PLAYLISTS).get().await()
            if (playlistsDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val playlistsListRaw = playlistsDoc.get("playlists") as? List<Map<String, Any>>
                if (!playlistsListRaw.isNullOrEmpty()) {
                    val restoredPlaylists = playlistsListRaw.mapNotNull { plMap ->
                        val id = plMap["id"] as? String ?: return@mapNotNull null
                        val title = plMap["title"] as? String ?: "Untitled"
                        val desc = plMap["description"] as? String ?: ""
                        val cover = plMap["coverUrl"] as? String
                        val createdAt = (plMap["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        @Suppress("UNCHECKED_CAST")
                        val tracksRaw = plMap["tracks"] as? List<Map<String, Any>> ?: emptyList()
                        val tracks = tracksRaw.mapNotNull { trMap ->
                            val tid = trMap["id"] as? String ?: return@mapNotNull null
                            val tTitle = trMap["title"] as? String ?: ""
                            val tArtist = trMap["artist"] as? String ?: ""
                            val dur = trMap["durationText"] as? String ?: ""
                            val tThumb = trMap["thumbnailUrl"] as? String
                            SieloTrack(
                                id = tid,
                                title = tTitle,
                                artist = tArtist,
                                durationText = dur,
                                thumbnailUrl = tThumb?.takeIf { it.isNotBlank() }
                            )
                        }
                        UserPlaylist(
                            id = id,
                            title = title,
                            description = desc,
                            coverUrl = cover?.takeIf { it.isNotBlank() },
                            createdAt = createdAt,
                            tracks = tracks
                        )
                    }
                    if (restoredPlaylists.isNotEmpty()) {
                        userPlaylistsRepository.restorePlaylists(restoredPlaylists)
                    }
                }
            }

            cloudProfile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
