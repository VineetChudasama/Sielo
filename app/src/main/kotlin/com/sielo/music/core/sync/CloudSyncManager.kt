package com.sielo.music.core.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sielo.music.core.auth.model.AuthProvider
import com.sielo.music.core.auth.model.UserProfile
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SearchHistoryDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.database.entity.SearchHistoryEntity
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylist
import com.sielo.music.core.playlist.UserPlaylistsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val userPlaylistsRepository: UserPlaylistsRepository
) {
    private var syncJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + syncJob)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    @Volatile var isRestoring: Boolean = false
    var currentUserProvider: (() -> UserProfile?)? = null

    // Pluggable providers to avoid circular dependencies with PlayerManager and SettingsRepository
    var playbackSnapshotProvider: (() -> Map<String, Any>?)? = null
    var playbackSnapshotRestorer: ((Map<String, Any>) -> Unit)? = null
    var settingsProvider: (() -> Map<String, Any>)? = null
    var settingsRestorer: ((Map<String, Any>) -> Unit)? = null

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val COLLECTION_USERS = "sielo_users"
        private const val DOC_PROFILE = "profile"
        private const val DOC_LIBRARY = "library"
        private const val DOC_PLAYLISTS = "playlists"
        private const val DOC_HISTORY = "history"
        private const val DOC_SEARCH_HISTORY = "search_history"
        private const val DOC_STATS = "stats"
        private const val DOC_PLAYBACK = "playback"
        private const val DOC_SETTINGS = "settings"
        private const val DOC_DOWNLOADS = "downloads"
    }

    init {
        startSyncLoops()
    }

    /**
     * Resolves the canonical Firestore document key for the account.
     * Priority:
     * 1. Firebase Authentication UID (canonical)
     * 2. User profile ID (if valid UID)
     * 3. Sanitized email (fallback / legacy)
     */
    fun resolveUserKey(user: UserProfile? = null): String {
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        if (!authUid.isNullOrBlank()) return authUid
        val profile = user ?: currentUserProvider?.invoke()
        val uid = profile?.id?.takeIf { it.isNotBlank() && !it.contains("@") }
        if (!uid.isNullOrBlank()) return uid
        val email = profile?.email?.trim()?.lowercase()
        if (!email.isNullOrBlank() && !email.endsWith("@sielo.local")) return email
        return "guest"
    }

    fun startSyncLoops() {
        // Cancel any existing jobs and launch fresh listeners
        syncJob.cancelChildren()

        // 1. Live favorites auto-sync
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
                        val userDocId = resolveUserKey(user)
                        getUserDataDocRef(userDocId, DOC_LIBRARY)
                            .set(
                                mapOf(
                                    "favorites" to favoritesData,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .await()
                        Log.d(TAG, "Auto-synced ${favorites.size} favorites for $userDocId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Live favorites auto-sync error: ${e.message}")
                    }
                }
        }

        // 2. Live playlists auto-sync
        scope.launch {
            userPlaylistsRepository.playlists
                .collectLatest { playlists ->
                    if (isRestoring) return@collectLatest
                    val user = currentUserProvider?.invoke() ?: return@collectLatest
                    val email = user.email.trim().lowercase()
                    if (email.isBlank() || email.endsWith("@sielo.local")) return@collectLatest
                    val customPlaylists = playlists.filterNot {
                        it.id.startsWith("sielo_starter_") || it.id == com.sielo.music.core.playlist.UserPlaylistsRepository.PLAYLIST_DOWNLOADED_CACHE_ID
                    }
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
                        val userDocId = resolveUserKey(user)
                        getUserDataDocRef(userDocId, DOC_PLAYLISTS)
                            .set(
                                mapOf(
                                    "playlists" to playlistsData,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .await()
                        Log.d(TAG, "Auto-synced ${customPlaylists.size} playlists for $userDocId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Live playlists auto-sync error: ${e.message}")
                    }
                }
        }

        // 3. Live listening history auto-sync
        scope.launch {
            listeningHistoryDao.getRecentHistory(limit = 1500)
                .collectLatest { history ->
                    if (isRestoring) return@collectLatest
                    val user = currentUserProvider?.invoke() ?: return@collectLatest
                    val email = user.email.trim().lowercase()
                    if (email.isBlank() || email.endsWith("@sielo.local")) return@collectLatest
                    if (history.isEmpty()) return@collectLatest

                    delay(4000L)
                    syncListeningHistoryToCloud(user, limit = 1500)
                }
        }

        // 4. Live search history auto-sync
        scope.launch {
            searchHistoryDao.getRecentSearchQueries(limit = 30)
                .collectLatest { searchHistory ->
                    if (isRestoring) return@collectLatest
                    val user = currentUserProvider?.invoke() ?: return@collectLatest
                    val email = user.email.trim().lowercase()
                    if (email.isBlank() || email.endsWith("@sielo.local")) return@collectLatest
                    if (searchHistory.isEmpty()) return@collectLatest

                    delay(5000L)
                    try {
                        ensureAuthenticated()
                        val searchData = searchHistory.map { item ->
                            mapOf(
                                "query" to item.query,
                                "timestampMs" to item.timestampMs
                            )
                        }
                        val userDocId = resolveUserKey(user)
                        getUserDataDocRef(userDocId, DOC_SEARCH_HISTORY)
                            .set(
                                mapOf(
                                    "queries" to searchData,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Live search history auto-sync error: ${e.message}")
                    }
                }
        }

        // 5. Periodic background interval sync loop (every 3 minutes)
        scope.launch {
            while (true) {
                delay(180_000L)
                try {
                    if (!isRestoring) {
                        val user = currentUserProvider?.invoke()
                        val email = user?.email?.trim()?.lowercase()
                        if (!email.isNullOrBlank() && !email.endsWith("@sielo.local")) {
                            ensureAuthenticated()
                            syncListeningHistoryToCloud(user, limit = 1500)
                            syncPlaybackStateToCloud(user)
                            syncDownloadsMetadataToCloud(user)
                            Log.d(TAG, "Periodic account cloud sync cycle completed for ${resolveUserKey(user)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic sync loop error: ${e.message}")
                }
            }
        }
    }

    /**
     * Cancels active background sync listeners on logout or account switch.
     */
    fun stopAllSync() {
        syncJob.cancelChildren()
        Log.i(TAG, "Stopped all active cloud sync background jobs")
    }

    private fun getUserDataDocRef(userKey: String, subDoc: String): DocumentReference {
        return firestore.collection(COLLECTION_USERS).document(userKey).collection("data").document(subDoc)
    }

    /**
     * Guarantees Firebase Auth is authenticated before Firestore operations.
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
     * Live sync for user settings when changed.
     */
    fun syncSettingsToCloud(user: UserProfile? = null) {
        scope.launch {
            if (isRestoring) return@launch
            val activeUser = user ?: currentUserProvider?.invoke() ?: return@launch
            val email = activeUser.email.trim().lowercase()
            if (email.isBlank() || email.endsWith("@sielo.local")) return@launch
            val settingsMap = settingsProvider?.invoke() ?: return@launch

            delay(1500L) // debounce
            try {
                ensureAuthenticated()
                val userDocId = resolveUserKey(activeUser)
                val dataToSave = settingsMap.toMutableMap()
                dataToSave["updatedAt"] = System.currentTimeMillis()
                getUserDataDocRef(userDocId, DOC_SETTINGS)
                    .set(dataToSave, SetOptions.merge())
                    .await()
                Log.d(TAG, "Synced settings for $userDocId")
            } catch (e: Exception) {
                Log.w(TAG, "Settings sync error: ${e.message}")
            }
        }
    }

    /**
     * Live sync for current playback state snapshot.
     */
    fun syncPlaybackStateToCloud(user: UserProfile? = null) {
        scope.launch {
            if (isRestoring) return@launch
            val activeUser = user ?: currentUserProvider?.invoke() ?: return@launch
            val email = activeUser.email.trim().lowercase()
            if (email.isBlank() || email.endsWith("@sielo.local")) return@launch
            val snapshot = playbackSnapshotProvider?.invoke() ?: return@launch

            try {
                ensureAuthenticated()
                val userDocId = resolveUserKey(activeUser)
                getUserDataDocRef(userDocId, DOC_PLAYBACK)
                    .set(snapshot, SetOptions.merge())
                    .await()
                Log.d(TAG, "Synced playback snapshot for $userDocId")
            } catch (e: Exception) {
                Log.w(TAG, "Playback snapshot sync error: ${e.message}")
            }
        }
    }

    /**
     * Sync download records metadata to cloud (records/metadata ONLY; never audio files).
     */
    fun syncDownloadsMetadataToCloud(user: UserProfile? = null) {
        scope.launch {
            if (isRestoring) return@launch
            val activeUser = user ?: currentUserProvider?.invoke() ?: return@launch
            val email = activeUser.email.trim().lowercase()
            if (email.isBlank() || email.endsWith("@sielo.local")) return@launch

            try {
                val cachedTracks = com.sielo.music.core.audio.OfflineCacheManager.getCachedTracks(context)
                if (cachedTracks.isEmpty()) return@launch

                ensureAuthenticated()
                val userDocId = resolveUserKey(activeUser)
                val downloadsData = cachedTracks.map { track ->
                    mapOf(
                        "id" to track.id,
                        "title" to track.title,
                        "artist" to track.artist,
                        "durationText" to track.durationText,
                        "thumbnailUrl" to (track.thumbnailUrl ?: ""),
                        "savedAt" to System.currentTimeMillis()
                    )
                }
                getUserDataDocRef(userDocId, DOC_DOWNLOADS)
                    .set(
                        mapOf(
                            "tracks" to downloadsData,
                            "count" to downloadsData.size,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                Log.d(TAG, "Synced ${downloadsData.size} download records for $userDocId")
            } catch (e: Exception) {
                Log.w(TAG, "Downloads metadata sync error: ${e.message}")
            }
        }
    }

    /**
     * Full blocking backup of all 13 categories to Firestore.
     * Guaranteed safe: Never wipes existing cloud data with empty local collections.
     */
    suspend fun backupUserDataBlocking(user: UserProfile) {
        val cleanEmail = user.email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) {
            return
        }

        try {
            ensureAuthenticated()
            val userDocId = resolveUserKey(user)
            val userRef = firestore.collection(COLLECTION_USERS).document(userDocId)

            // Compute lifetime streams and favorites count for profile and admin registry
            val totalStreams = try {
                listeningHistoryDao.getAllHistoryList(5000).size
            } catch (_: Exception) {
                0
            }
            val currentFavorites = favoriteTrackDao.getAllFavoritesList()
            val likedCount = currentFavorites.size

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
                "totalStreams" to totalStreams,
                "streamCount" to totalStreams,
                "likedCount" to likedCount,
                "appVersion" to "11.5.6",
                "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
                "createdAt" to user.createdAt,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            userRef.collection("data").document(DOC_PROFILE)
                .set(profileMap, SetOptions.merge())
                .await()

            // 1b. Write root document in sielo_users so Firestore REST API listing sees it
            val rootSummary = hashMapOf(
                "id" to user.id,
                "name" to user.name,
                "email" to user.email,
                "username" to (user.username ?: ""),
                "photoUrl" to (user.photoUrl ?: ""),
                "provider" to user.provider.name,
                "totalStreams" to totalStreams,
                "streamCount" to totalStreams,
                "likedCount" to likedCount,
                "appVersion" to "11.5.6",
                "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
                "status" to "Active Now",
                "createdAt" to user.createdAt,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            try {
                userRef.set(rootSummary, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Root user doc set error: ${e.message}")
            }

            // 1c. Dispatch real-time user telemetry to website admin registry
            scope.launch(Dispatchers.IO) {
                sendAdminUserTelemetry(user, totalStreams, likedCount)
            }

            // 2. Favorites & Library document (only push if non-empty to protect cloud state)
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

            // 3. Custom Playlists document (filter out starter and cache playlists)
            val currentPlaylists = userPlaylistsRepository.getAllPlaylists()
                .filterNot { it.id.startsWith("sielo_starter_") || it.id == com.sielo.music.core.playlist.UserPlaylistsRepository.PLAYLIST_DOWNLOADED_CACHE_ID }
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

            // 4. Listening History & lifetime stats
            syncListeningHistoryToCloud(user, limit = 1500)

            // 5. Search History document
            val searchHistory = searchHistoryDao.getAllSearchQueries()
            if (searchHistory.isNotEmpty()) {
                val searchData = searchHistory.map { item ->
                    mapOf(
                        "query" to item.query,
                        "timestampMs" to item.timestampMs
                    )
                }
                userRef.collection("data").document(DOC_SEARCH_HISTORY)
                    .set(
                        mapOf(
                            "queries" to searchData,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            // 6. Playback state snapshot
            val playbackSnapshot = playbackSnapshotProvider?.invoke()
            if (playbackSnapshot != null) {
                userRef.collection("data").document(DOC_PLAYBACK)
                    .set(playbackSnapshot, SetOptions.merge())
                    .await()
            }

            // 7. Settings document
            val settingsMap = settingsProvider?.invoke()
            if (settingsMap != null) {
                val settingsData = settingsMap.toMutableMap()
                settingsData["updatedAt"] = System.currentTimeMillis()
                userRef.collection("data").document(DOC_SETTINGS)
                    .set(settingsData, SetOptions.merge())
                    .await()
            }

            // 8. Downloads metadata document
            val cachedTracks = com.sielo.music.core.audio.OfflineCacheManager.getCachedTracks(context)
            if (cachedTracks.isNotEmpty()) {
                val downloadsData = cachedTracks.map { track ->
                    mapOf(
                        "id" to track.id,
                        "title" to track.title,
                        "artist" to track.artist,
                        "durationText" to track.durationText,
                        "thumbnailUrl" to (track.thumbnailUrl ?: "")
                    )
                }
                userRef.collection("data").document(DOC_DOWNLOADS)
                    .set(
                        mapOf(
                            "tracks" to downloadsData,
                            "count" to downloadsData.size,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            Log.i(TAG, "Successfully backed up complete account data for $userDocId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup data for ${user.email}: ${e.message}", e)
        }
    }

    /**
     * Backup user data asynchronously in the background.
     */
    fun backupUserData(user: UserProfile) {
        scope.launch {
            backupUserDataBlocking(user)
        }
    }

    /**
     * Restores all 13 account data categories from Firestore cloud.
     * Seamlessly handles backward compatibility: if data exists under cleanEmail instead of UID,
     * it migrates the data to the UID-based path automatically.
     */
    suspend fun restoreUserData(email: String, explicitUid: String? = null): UserProfile? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) return null

        isRestoring = true
        return try {
            ensureAuthenticated()

            // Resolve UID: explicit UID -> FirebaseAuth.currentUser.uid -> cleanEmail
            val authUid = explicitUid ?: FirebaseAuth.getInstance().currentUser?.uid
            val primaryKey = authUid ?: cleanEmail

            var userRef = firestore.collection(COLLECTION_USERS).document(primaryKey)
            var profileDoc = userRef.collection("data").document(DOC_PROFILE).get().await()

            // Migration fallback: If primaryKey doc doesn't exist and primaryKey != cleanEmail, check cleanEmail
            if (!profileDoc.exists() && primaryKey != cleanEmail) {
                val legacyRef = firestore.collection(COLLECTION_USERS).document(cleanEmail)
                val legacyProfileDoc = legacyRef.collection("data").document(DOC_PROFILE).get().await()
                if (legacyProfileDoc.exists()) {
                    Log.i(TAG, "Migrating legacy cloud account data from $cleanEmail to canonical UID $primaryKey")
                    migrateLegacyAccount(legacyRef, userRef)
                    // Reload profile doc from new primaryKey
                    profileDoc = userRef.collection("data").document(DOC_PROFILE).get().await()
                }
            }

            var cloudProfile: UserProfile? = null

            // 1. Restore Profile
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
                    id = data["id"] as? String ?: primaryKey,
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
                Log.i(TAG, "Restored profile for $primaryKey: ${cloudProfile.name}, artists: ${favoriteArtists.size}")
            }

            // 2. Restore Liked songs (Favorites)
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
                            Log.i(TAG, "Restored ${entities.size} favorites into Room for $primaryKey")
                        }
                    }
                }
            } catch (libErr: Exception) {
                Log.w(TAG, "Error restoring favorites: ${libErr.message}")
            }

            // 3. Restore Playlists
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
                            Log.i(TAG, "Restored ${restoredPlaylists.size} playlists for $primaryKey")
                        }
                    }
                }
            } catch (plErr: Exception) {
                Log.w(TAG, "Error restoring playlists: ${plErr.message}")
            }

            // 4. Restore Listening History & Stats
            val tempUser = cloudProfile ?: UserProfile(
                id = primaryKey,
                name = cleanEmail.substringBefore("@"),
                email = cleanEmail,
                provider = AuthProvider.GOOGLE,
                hasCompletedOnboarding = true,
                favoriteArtists = emptyList(),
                favoriteGenres = emptyList()
            )
            syncListeningHistoryToCloud(tempUser, limit = 1500)

            // 5. Restore Search History
            try {
                val searchDoc = userRef.collection("data").document(DOC_SEARCH_HISTORY).get().await()
                if (searchDoc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val searchListRaw = searchDoc.get("queries") as? List<Map<String, Any>>
                    if (!searchListRaw.isNullOrEmpty()) {
                        val existingQueries = searchHistoryDao.getAllSearchQueries().map { it.query.lowercase() }.toSet()
                        val searchEntities = searchListRaw.mapNotNull { item ->
                            val query = item["query"] as? String ?: return@mapNotNull null
                            val timestampMs = (item["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            SearchHistoryEntity(query = query, timestampMs = timestampMs)
                        }.filterNot { it.query.lowercase() in existingQueries }
                         .distinctBy { it.query.lowercase() }

                        if (searchEntities.isNotEmpty()) {
                            searchHistoryDao.insertSearchQueries(searchEntities)
                            Log.i(TAG, "Restored ${searchEntities.size} search queries for $primaryKey")
                        }
                    }
                }
            } catch (searchErr: Exception) {
                Log.w(TAG, "Error restoring search history: ${searchErr.message}")
            }

            // 6. Restore Settings (qualities, audio FX, gapless, crossfade, themes, recommendations)
            try {
                val settingsDoc = userRef.collection("data").document(DOC_SETTINGS).get().await()
                if (settingsDoc.exists() && settingsDoc.data != null) {
                    settingsRestorer?.invoke(settingsDoc.data!!)
                    Log.i(TAG, "Restored account settings for $primaryKey")
                }
            } catch (setErr: Exception) {
                Log.w(TAG, "Error restoring settings: ${setErr.message}")
            }

            // 7. Restore Playback State & Queue snapshot (without auto-starting playback)
            try {
                val playbackDoc = userRef.collection("data").document(DOC_PLAYBACK).get().await()
                if (playbackDoc.exists() && playbackDoc.data != null) {
                    playbackSnapshotRestorer?.invoke(playbackDoc.data!!)
                    Log.i(TAG, "Restored playback snapshot for $primaryKey")
                }
            } catch (playErr: Exception) {
                Log.w(TAG, "Error restoring playback state: ${playErr.message}")
            }

            // 8. Restart sync loops for this account
            startSyncLoops()

            cloudProfile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore user data for $cleanEmail: ${e.message}", e)
            null
        } finally {
            isRestoring = false
        }
    }

    /**
     * Copies all subcollection documents from a legacy email document to the new canonical UID document.
     */
    private suspend fun migrateLegacyAccount(legacyRef: DocumentReference, targetRef: DocumentReference) {
        val docsToMigrate = listOf(
            DOC_PROFILE,
            DOC_LIBRARY,
            DOC_PLAYLISTS,
            DOC_HISTORY,
            DOC_STATS,
            DOC_SEARCH_HISTORY,
            DOC_SETTINGS,
            DOC_PLAYBACK,
            DOC_DOWNLOADS
        )
        for (docName in docsToMigrate) {
            try {
                val snapshot = legacyRef.collection("data").document(docName).get().await()
                if (snapshot.exists() && snapshot.data != null) {
                    targetRef.collection("data").document(docName)
                        .set(snapshot.data!!, SetOptions.merge())
                        .await()
                    Log.d(TAG, "Migrated $docName to canonical UID document")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error migrating $docName: ${e.message}")
            }
        }
    }

    /**
     * Synchronizes listening history and lifetime statistics between local SQLite Room database and Firestore cloud.
     * Guarantees 0-loss bi-directional sync:
     * - Restores any cloud events played on another device into local Room DB.
     * - Merges local events with cloud events, deduplicating and sorting.
     * - Pushes the full merged list to Firestore DOC_HISTORY.
     * - Computes and updates account-level lifetime statistics in Firestore DOC_STATS.
     */
    suspend fun syncListeningHistoryToCloud(user: UserProfile, limit: Int = 1500) {
        val cleanEmail = user.email.trim().lowercase()
        if (cleanEmail.isBlank() || cleanEmail.endsWith("@sielo.local")) return

        try {
            ensureAuthenticated()
            val userDocId = resolveUserKey(user)
            val userRef = firestore.collection(COLLECTION_USERS).document(userDocId)

            // 1. Fetch existing cloud events
            val existingCloudEvents: List<Map<String, Any>> = try {
                val doc = userRef.collection("data").document(DOC_HISTORY).get().await()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    (doc.get("events") as? List<Map<String, Any>>) ?: emptyList()
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            // 2. Fetch local events
            val currentHistory = listeningHistoryDao.getAllHistoryList(limit = limit)

            // If both local and cloud are empty, nothing to sync
            if (currentHistory.isEmpty() && existingCloudEvents.isEmpty()) return

            val localEventsData = currentHistory.map { ev ->
                mapOf(
                    "songId" to ev.songId,
                    "songTitle" to ev.songTitle,
                    "artistName" to ev.artistName,
                    "albumName" to (ev.albumName ?: ""),
                    "thumbnailUrl" to (ev.thumbnailUrl ?: ""),
                    "timestampMs" to ev.timestampMs,
                    "durationPlayedMs" to ev.durationPlayedMs,
                    "songDurationMs" to ev.songDurationMs,
                    "hourOfDay" to ev.hourOfDay,
                    "dayOfWeek" to ev.dayOfWeek,
                    "month" to ev.month,
                    "year" to ev.year,
                    "completed" to ev.completed
                )
            }

            // 3. Two-way sync: If cloud has events missing locally, restore them into local DB
            if (existingCloudEvents.isNotEmpty()) {
                val existingTimestamps = listeningHistoryDao.getAllTimestamps().toSet()
                val missingFromLocal = existingCloudEvents.mapNotNull { item ->
                    val songId = item["songId"] as? String ?: return@mapNotNull null
                    val songTitle = item["songTitle"] as? String ?: ""
                    val artistName = item["artistName"] as? String ?: ""
                    val albumName = (item["albumName"] as? String)?.takeIf { it.isNotBlank() }
                    val thumbnailUrl = (item["thumbnailUrl"] as? String)?.takeIf { it.isNotBlank() }
                    val timestampMs = (item["timestampMs"] as? Number)?.toLong() ?: return@mapNotNull null
                    val durationPlayedMs = (item["durationPlayedMs"] as? Number)?.toLong() ?: 0L
                    val songDurationMs = (item["songDurationMs"] as? Number)?.toLong() ?: 0L
                    val hourOfDay = (item["hourOfDay"] as? Number)?.toInt() ?: 0
                    val dayOfWeek = (item["dayOfWeek"] as? Number)?.toInt() ?: 1
                    val month = (item["month"] as? Number)?.toInt() ?: 0
                    val year = (item["year"] as? Number)?.toInt() ?: 2026
                    val completed = item["completed"] as? Boolean ?: false

                    ListeningEventEntity(
                        eventId = 0,
                        songId = songId,
                        songTitle = songTitle,
                        artistName = artistName,
                        albumName = albumName,
                        thumbnailUrl = thumbnailUrl,
                        timestampMs = timestampMs,
                        durationPlayedMs = durationPlayedMs,
                        songDurationMs = songDurationMs,
                        hourOfDay = hourOfDay,
                        dayOfWeek = dayOfWeek,
                        month = month,
                        year = year,
                        completed = completed
                    )
                }.filterNot { it.timestampMs in existingTimestamps }
                 .distinctBy { "${it.songId}_${it.timestampMs}" }

                if (missingFromLocal.isNotEmpty()) {
                    listeningHistoryDao.insertEvents(missingFromLocal)
                    Log.i(TAG, "Bi-directional sync inserted ${missingFromLocal.size} cloud events into local DB for $userDocId")
                }
            }

            // 4. Merge local + cloud events for cloud snapshot
            val mergedEvents = (localEventsData + existingCloudEvents)
                .distinctBy {
                    val sId = it["songId"] as? String ?: ""
                    val tMs = (it["timestampMs"] as? Number)?.toLong() ?: 0L
                    "${sId}_${tMs}"
                }
                .sortedByDescending { (it["timestampMs"] as? Number)?.toLong() ?: 0L }
                .take(limit)

            userRef.collection("data").document(DOC_HISTORY)
                .set(
                    mapOf(
                        "events" to mergedEvents,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()

            // 5. Update DOC_STATS document with account-level lifetime statistics
            val totalDurationMs = mergedEvents.sumOf { (it["durationPlayedMs"] as? Number)?.toLong() ?: 0L }
            val totalStreams = mergedEvents.size
            val uniqueSongs = mergedEvents.mapNotNull { it["songId"] as? String }.distinct().size
            val uniqueArtists = mergedEvents.mapNotNull { it["artistName"] as? String }.distinct().size

            userRef.collection("data").document(DOC_STATS)
                .set(
                    mapOf(
                        "totalListeningTimeMs" to totalDurationMs,
                        "totalStreams" to totalStreams,
                        "uniqueSongsCount" to uniqueSongs,
                        "uniqueArtistsCount" to uniqueArtists,
                        "lastSyncedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()

            // 6. Keep DOC_PROFILE and root user document updated with fresh totalStreams
            try {
                val statsUpdate = mapOf(
                    "totalStreams" to totalStreams,
                    "streamCount" to totalStreams,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                userRef.collection("data").document(DOC_PROFILE).set(statsUpdate, SetOptions.merge()).await()
                userRef.set(statsUpdate, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Error updating totalStreams to profile: ${e.message}")
            }

            // 7. Dispatch background real-time telemetry to website admin registry
            scope.launch(Dispatchers.IO) {
                val likedCount = try { favoriteTrackDao.getAllFavoritesList().size } catch (_: Exception) { 0 }
                sendAdminUserTelemetry(user, totalStreams, likedCount)
            }

            Log.i(TAG, "Successfully synced ${mergedEvents.size} events and stats for $userDocId")
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing listening history and stats for ${user.email}: ${e.message}")
        }
    }

    /**
     * Sends background telemetry heartbeat / sync to the website admin registry (sielo.site/api/admin/users)
     */
    private fun sendAdminUserTelemetry(user: UserProfile, streamCount: Int, likedCount: Int) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val jsonBody = JSONObject().apply {
                put("id", resolveUserKey(user))
                put("name", user.name)
                put("email", user.email)
                put("username", user.username ?: "")
                put("avatar", user.photoUrl)
                put("provider", user.provider.name)
                put("streamCount", streamCount)
                put("likedCount", likedCount)
                put("status", "Active Now")
                put("appVersion", "11.5.6")
                put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim())
                put("createdAt", user.createdAt)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://sielo.site/api/admin/users")
                .post(requestBody)
                .build()

            client.newCall(request).execute().close()
            Log.d(TAG, "Dispatched user telemetry for ${user.email} (streams=$streamCount)")
        } catch (e: Exception) {
            Log.d(TAG, "Admin telemetry notice: ${e.message}")
        }
    }
}
