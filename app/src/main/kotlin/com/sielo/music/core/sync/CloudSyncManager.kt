package com.sielo.music.core.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sielo.music.core.auth.model.AuthProvider
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    @Volatile var isRestoring: Boolean = false
    var currentUserProvider: (() -> UserProfile?)? = null

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val COLLECTION_USERS = "sielo_users"
        private const val DOC_PROFILE = "profile"
        private const val DOC_LIBRARY = "library"
        private const val DOC_PLAYLISTS = "playlists"
    }

    init {
        // Automatically sync favorites to cloud when favorites change
        scope.launch {
            favoriteTrackDao.getAllFavorites()
                .collectLatest { favorites ->
                    if (isRestoring) return@collectLatest
                    val user = currentUserProvider?.invoke() ?: return@collectLatest
                    val email = user.email.trim().lowercase()
                    if (email.isBlank() || email.endsWith("@sielo.local")) return@collectLatest
                    if (favorites.isEmpty()) return@collectLatest

                    delay(2000L) // debounce rapid taps
                    try {
                        ensureAuthenticated()
                        val favoritesData = favorites.map { fav ->
                            mapOf(
                                "id" to fav.id,
                                "title" to fav.title,
                                "artist" to fav.artist,
                                "thumbnailUrl" to (fav.thumbnailUrl ?: ""),
                                "addedAt" to fav.addedAt
                            )
                        }
                        val userRef = firestore.collection(COLLECTION_USERS).document(email)
                        userRef.collection("data").document(DOC_LIBRARY)
                            .set(
                                mapOf(
                                    "favorites" to favoritesData,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .await()
                        Log.d(TAG, "Auto-synced ${favorites.size} favorites for $email")
                    } catch (e: Exception) {
                        Log.w(TAG, "Live favorites auto-sync error: ${e.message}")
                    }
                }
        }

        // Automatically sync custom playlists to cloud when playlists change
        scope.launch {
            userPlaylistsRepository.playlists
                .collectLatest { playlists ->
                    if (isRestoring) return@collectLatest
                    val user = currentUserProvider?.invoke() ?: return@collectLatest
                    val email = user.email.trim().lowercase()
                    if (email.isBlank() || email.endsWith("@sielo.local")) return@collectLatest
                    val customPlaylists = playlists.filterNot { it.id.startsWith("sielo_starter_") }
                    if (customPlaylists.isEmpty()) return@collectLatest

                    delay(2000L) // debounce rapid edits
                    try {
                        ensureAuthenticated()
                        val playlistsData = customPlaylists.map { pl ->
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
                        val userRef = firestore.collection(COLLECTION_USERS).document(email)
                        userRef.collection("data").document(DOC_PLAYLISTS)
                            .set(
                                mapOf(
                                    "playlists" to playlistsData,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .await()
                        Log.d(TAG, "Auto-synced ${customPlaylists.size} playlists for $email")
                    } catch (e: Exception) {
                        Log.w(TAG, "Live playlists auto-sync error: ${e.message}")
                    }
                }
        }
    }

    /**
     * Guarantees that Firebase Auth is authenticated before performing Firestore queries.
     * Prevents Firestore PERMISSION_DENIED security rule errors.
     */
    suspend fun ensureAuthenticated() {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d(TAG, "Authenticated anonymously with Firebase Auth: ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase auth ensure check warning: ${e.message}")
        }
    }

    /**
     * Backup user data to Firestore in a blocking suspend call (used on signOut or manual sync).
     */
    suspend fun backupUserDataBlocking(user: UserProfile) {
        val cleanEmail = user.email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) {
            return
        }

        try {
            ensureAuthenticated()
            val userRef = firestore.collection(COLLECTION_USERS).document(cleanEmail)

            // 1. Profile document
            val profileMap = hashMapOf(
                "id" to user.id,
                "name" to user.name,
                "email" to user.email,
                "photoUrl" to (user.photoUrl ?: ""),
                "bio" to (user.bio ?: ""),
                "username" to (user.username ?: ""),
                "provider" to user.provider.name,
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

            // 2. Favorites & Library document (only push if non-empty to prevent accidental wipe)
            val currentFavorites = favoriteTrackDao.getAllFavoritesList()
            if (currentFavorites.isNotEmpty()) {
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
            }

            // 3. Custom Playlists document (filter out starter playlists)
            val currentPlaylists = userPlaylistsRepository.getAllPlaylists()
                .filterNot { it.id.startsWith("sielo_starter_") }
            if (currentPlaylists.isNotEmpty()) {
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
            }
            Log.i(TAG, "Successfully backed up data for $cleanEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup data for $cleanEmail: ${e.message}", e)
        }
    }

    /**
     * Backup user data to Firestore in the background.
     */
    fun backupUserData(user: UserProfile) {
        scope.launch {
            backupUserDataBlocking(user)
        }
    }

    /**
     * Restore user library, playlists, and profile from Firestore cloud.
     * Returns true if cloud data was restored, false otherwise.
     */
    suspend fun restoreUserData(email: String): UserProfile? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) return null

        isRestoring = true
        return try {
            ensureAuthenticated()
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

                val providerStr = data["provider"] as? String ?: "GOOGLE"
                val provider = try {
                    AuthProvider.valueOf(providerStr)
                } catch (_: Exception) {
                    AuthProvider.GOOGLE
                }

                cloudProfile = UserProfile(
                    id = data["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                    name = data["name"] as? String ?: cleanEmail.substringBefore("@"),
                    email = cleanEmail,
                    photoUrl = (data["photoUrl"] as? String)?.takeIf { it.isNotBlank() },
                    provider = provider,
                    hasCompletedOnboarding = data["hasCompletedOnboarding"] as? Boolean ?: true,
                    favoriteArtists = favoriteArtists,
                    favoriteGenres = favoriteGenres,
                    artistTasteWeights = artistTasteWeights,
                    genreTasteWeights = genreTasteWeights,
                    bio = (data["bio"] as? String)?.takeIf { it.isNotBlank() },
                    username = (data["username"] as? String)?.takeIf { it.isNotBlank() },
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                Log.i(TAG, "Restored profile document for $cleanEmail: ${cloudProfile.name}, artists: ${favoriteArtists.size}")
            } else {
                Log.i(TAG, "No cloud profile document found for $cleanEmail")
            }

            // 2. Fetch Favorites document & restore into local database
            try {
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
                            Log.i(TAG, "Restored ${entities.size} favorites for $cleanEmail into local database")
                        }
                    }
                }
            } catch (libErr: Exception) {
                Log.w(TAG, "Error restoring favorites: ${libErr.message}")
            }

            // 3. Fetch Playlists document & restore into local storage
            try {
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
                            Log.i(TAG, "Restored ${restoredPlaylists.size} playlists for $cleanEmail")
                        }
                    }
                }
            } catch (plErr: Exception) {
                Log.w(TAG, "Error restoring playlists: ${plErr.message}")
            }

            cloudProfile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore user data for $cleanEmail: ${e.message}", e)
            null
        } finally {
            isRestoring = false
        }
    }
}
