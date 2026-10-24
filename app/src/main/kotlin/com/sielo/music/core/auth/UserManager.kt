package com.sielo.music.core.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sielo.music.core.auth.model.AuthProvider
import com.sielo.music.core.auth.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseCleaner: com.sielo.music.core.database.DatabaseCleaner,
    private val playerManager: com.sielo.music.core.audio.PlayerManager,
    private val cloudSyncManager: com.sielo.music.core.sync.CloudSyncManager,
    private val userPlaylistsRepository: com.sielo.music.core.playlist.UserPlaylistsRepository,
    private val listeningEventDispatcher: com.sielo.music.core.recommendations.events.ListeningEventDispatcher
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isAuthDialogOpen = MutableStateFlow(false)
    val isAuthDialogOpen: StateFlow<Boolean> = _isAuthDialogOpen.asStateFlow()

    private val _isOnboardingOpen = MutableStateFlow(false)
    val isOnboardingOpen: StateFlow<Boolean> = _isOnboardingOpen.asStateFlow()

    companion object {
        private const val PREFS_NAME = "sielo_auth_prefs"
        private const val KEY_CURRENT_USER = "current_user_profile"
        private const val KEY_ALL_USERS_PREFIX = "user_account_"
        private const val COLLECTION_USERNAMES = "sielo_usernames"
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    fun getUserByEmail(email: String): UserProfile? {
        val clean = email.trim().lowercase()
        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$clean", null) ?: return null
        return try {
            json.decodeFromString<UserProfile>(existingJson)
        } catch (_: Exception) {
            null
        }
    }

    fun isValidUsername(raw: String): Boolean {
        val clean = raw.trim().removePrefix("@").lowercase()
        if (clean.length !in 3..30) return false
        if (clean.startsWith(".") || clean.endsWith(".")) return false
        if (clean.contains("..")) return false
        return clean.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
    }

    fun sanitizeUsername(raw: String): String {
        return raw.trim().removePrefix("@").lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
    }

    suspend fun checkUsernameAvailability(raw: String, currentEmail: String? = null): Pair<Boolean, String> {
        val clean = sanitizeUsername(raw)
        if (clean.length < 3) {
            return Pair(false, "Username must be at least 3 characters")
        }
        if (clean.length > 30) {
            return Pair(false, "Username cannot exceed 30 characters")
        }
        if (clean.startsWith(".") || clean.endsWith(".")) {
            return Pair(false, "Username cannot start or end with a dot")
        }
        if (clean.contains("..")) {
            return Pair(false, "Username cannot have consecutive dots")
        }
        if (!clean.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }) {
            return Pair(false, "Only lowercase letters, numbers, '.', and '_' allowed")
        }

        return try {
            cloudSyncManager.ensureAuthenticated()
            val doc = firestore.collection(COLLECTION_USERNAMES).document(clean).get().await()
            if (!doc.exists()) {
                Pair(true, "@$clean is available!")
            } else {
                val ownerEmail = doc.getString("email")?.trim()?.lowercase()
                val currentCleanEmail = currentEmail?.trim()?.lowercase()
                if (!currentCleanEmail.isNullOrBlank() && ownerEmail == currentCleanEmail) {
                    Pair(true, "Your current username")
                } else {
                    Pair(false, "@$clean is already taken. Please choose another.")
                }
            }
        } catch (e: Exception) {
            Log.w("UserManager", "Availability check error: ${e.message}")
            Pair(true, "Username available")
        }
    }

    suspend fun claimUsername(raw: String, user: UserProfile): Boolean {
        val clean = sanitizeUsername(raw)
        if (!isValidUsername(clean)) return false
        return try {
            cloudSyncManager.ensureAuthenticated()
            val userKey = cloudSyncManager.resolveUserKey(user)
            val docRef = firestore.collection(COLLECTION_USERNAMES).document(clean)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val ownerEmail = existing.getString("email")?.trim()?.lowercase()
                val ownerUid = existing.getString("uid")
                if (ownerEmail != null && ownerEmail != user.email.trim().lowercase() && ownerUid != userKey && ownerUid != user.id) {
                    Log.w("UserManager", "Username $clean taken by another user ($ownerEmail)")
                    return false
                }
            }

            val data = hashMapOf(
                "username" to clean,
                "email" to user.email.trim().lowercase(),
                "uid" to userKey,
                "name" to user.name,
                "claimedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()

            val oldUsername = user.username?.let { sanitizeUsername(it) }
            if (!oldUsername.isNullOrBlank() && oldUsername != clean) {
                try {
                    firestore.collection(COLLECTION_USERNAMES).document(oldUsername).delete().await()
                } catch (e: Exception) {
                    Log.w("UserManager", "Failed to release old handle $oldUsername: ${e.message}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("UserManager", "Failed to claim username $clean: ${e.message}", e)
            false
        }
    }

    init {
        listeningEventDispatcher.currentUserIdProvider = {
            _currentUser.value?.id ?: "guest"
        }
        cloudSyncManager.currentUserProvider = {
            _currentUser.value
        }
        cloudSyncManager.playbackSnapshotProvider = {
            playerManager.getPlaybackSnapshot()
        }
        cloudSyncManager.playbackSnapshotRestorer = {
            playerManager.restoreFromCloudSnapshot(it)
        }
        scope.launch {
            cloudSyncManager.ensureAuthenticated()
        }
        restoreSession()
    }

    private fun restoreSession() {
        val savedJson = prefs.getString(KEY_CURRENT_USER, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                var profile = json.decodeFromString<UserProfile>(savedJson)
                if (profile.createdAt <= 0L) {
                    val storedCreated = prefs.getLong("account_created_timestamp", System.currentTimeMillis())
                    profile = profile.copy(createdAt = storedCreated)
                    persistUser(profile, backupToCloud = false)
                }
                _currentUser.value = profile
                if (!profile.hasCompletedOnboarding) {
                    _isOnboardingOpen.value = true
                }
                // Background refresh from cloud on launch to keep multi-device data synchronized
                val cleanEmail = profile.email.trim().lowercase()
                if (cleanEmail.isNotBlank() && !cleanEmail.endsWith("@sielo.local")) {
                    scope.launch {
                        cloudSyncManager.ensureAuthenticated()
                        val restored = cloudSyncManager.restoreUserData(cleanEmail)
                        if (restored != null) {
                            val merged = (_currentUser.value ?: profile).copy(
                                id = restored.id.takeIf { it.isNotBlank() } ?: profile.id,
                                name = restored.name.takeIf { it.isNotBlank() } ?: profile.name,
                                username = restored.username ?: profile.username,
                                bio = restored.bio ?: profile.bio,
                                photoUrl = restored.photoUrl ?: profile.photoUrl,
                                favoriteArtists = if (restored.favoriteArtists.isNotEmpty()) restored.favoriteArtists else profile.favoriteArtists,
                                favoriteGenres = if (restored.favoriteGenres.isNotEmpty()) restored.favoriteGenres else profile.favoriteGenres,
                                artistTasteWeights = if (restored.artistTasteWeights.isNotEmpty()) restored.artistTasteWeights else profile.artistTasteWeights,
                                genreTasteWeights = if (restored.genreTasteWeights.isNotEmpty()) restored.genreTasteWeights else profile.genreTasteWeights,
                                hasCompletedOnboarding = true,
                                createdAt = if (restored.createdAt > 0L) restored.createdAt else profile.createdAt
                            )
                            persistUser(merged, backupToCloud = false)
                        } else {
                            // Seed newly created cloud database with existing local account data
                            android.util.Log.i("UserManager", "Cloud has no record for $cleanEmail yet. Backing up existing local account to Firestore...")
                            cloudSyncManager.backupUserData(profile)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentUser.value = null
                _isAuthDialogOpen.value = true
            }
        } else {
            // First time launch: immediately open auth flow for new user
            _isAuthDialogOpen.value = true
        }
    }

    fun persistUser(profile: UserProfile, backupToCloud: Boolean = true) {
        _currentUser.value = profile
        try {
            val userJson = json.encodeToString(profile)
            prefs.edit()
                .putString(KEY_CURRENT_USER, userJson)
                .putString("$KEY_ALL_USERS_PREFIX${profile.email.trim().lowercase()}", userJson)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (backupToCloud) {
            cloudSyncManager.backupUserData(profile)
        }
    }

    fun openAuthDialog() {
        _isAuthDialogOpen.value = true
    }

    fun closeAuthDialog() {
        _isAuthDialogOpen.value = false
    }

    fun openOnboarding() {
        _isOnboardingOpen.value = true
    }

    fun closeOnboarding() {
        _isOnboardingOpen.value = false
    }

    fun signInWithGoogle(name: String, email: String, photoUrl: String?, idToken: String? = null, username: String? = null) {
        val cleanEmail = email.trim().lowercase()
        val cleanUsername = username?.let { sanitizeUsername(it) }?.ifBlank { null }
        _isAuthDialogOpen.value = false

        // Detect if user is switching accounts
        val currentEmail = _currentUser.value?.email?.trim()?.lowercase()
        val isAccountSwitch = !currentEmail.isNullOrBlank() && currentEmail != cleanEmail
        val oldUserToBackup = if (isAccountSwitch) _currentUser.value else null

        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$cleanEmail", null)
        val localProfile = if (!existingJson.isNullOrBlank()) {
            try {
                val existing = json.decodeFromString<UserProfile>(existingJson)
                val finalPhoto = existing.photoUrl?.takeIf { it.isNotBlank() } ?: photoUrl
                val finalName = existing.name.takeIf { it.isNotBlank() } ?: name
                existing.copy(
                    name = finalName,
                    username = cleanUsername ?: existing.username,
                    photoUrl = finalPhoto,
                    provider = AuthProvider.GOOGLE,
                    hasCompletedOnboarding = true
                )
            } catch (_: Exception) {
                null
            }
        } else null

        if (localProfile != null) {
            persistUser(localProfile, backupToCloud = false)
            _isOnboardingOpen.value = !localProfile.hasCompletedOnboarding
        } else {
            // Tentative initial profile while restoring; keep onboarding closed until cloud lookup completes
            val tentative = createNewUserProfile(name, cleanEmail, photoUrl, AuthProvider.GOOGLE).copy(username = cleanUsername)
            persistUser(tentative, backupToCloud = false)
            _isOnboardingOpen.value = false
        }

        // Trigger Firebase Auth credential bridge & cloud restore in background
        scope.launch {
            if (isAccountSwitch) {
                try {
                    if (oldUserToBackup != null) {
                        cloudSyncManager.backupUserDataBlocking(oldUserToBackup)
                    }
                    databaseCleaner.clearAll()
                    userPlaylistsRepository.resetToDefault()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!idToken.isNullOrBlank()) {
                try {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential).await()
                    Log.d("UserManager", "FirebaseAuth signed in with Google Credential for $cleanEmail")
                } catch (e: Exception) {
                    Log.w("UserManager", "FirebaseAuth Google sign-in failed: ${e.message}")
                    cloudSyncManager.ensureAuthenticated()
                }
            } else {
                cloudSyncManager.ensureAuthenticated()
            }

            try {
                val restored = cloudSyncManager.restoreUserData(cleanEmail)
                val current = _currentUser.value ?: localProfile ?: createNewUserProfile(name, cleanEmail, photoUrl, AuthProvider.GOOGLE)
                if (restored != null) {
                    val merged = current.copy(
                        id = restored.id.takeIf { it.isNotBlank() } ?: current.id,
                        name = restored.name.takeIf { it.isNotBlank() } ?: current.name,
                        username = cleanUsername ?: (restored.username ?: current.username),
                        bio = restored.bio ?: current.bio,
                        photoUrl = restored.photoUrl ?: current.photoUrl,
                        favoriteArtists = if (restored.favoriteArtists.isNotEmpty()) restored.favoriteArtists else current.favoriteArtists,
                        favoriteGenres = if (restored.favoriteGenres.isNotEmpty()) restored.favoriteGenres else current.favoriteGenres,
                        artistTasteWeights = if (restored.artistTasteWeights.isNotEmpty()) restored.artistTasteWeights else current.artistTasteWeights,
                        genreTasteWeights = if (restored.genreTasteWeights.isNotEmpty()) restored.genreTasteWeights else current.genreTasteWeights,
                        hasCompletedOnboarding = true,
                        createdAt = if (restored.createdAt > 0L) restored.createdAt else current.createdAt
                    )
                    if (!cleanUsername.isNullOrBlank()) {
                        claimUsername(cleanUsername, merged)
                    }
                    persistUser(merged, backupToCloud = true)
                    _isOnboardingOpen.value = false
                } else {
                    if (localProfile == null) {
                        _isOnboardingOpen.value = true
                    }
                    persistUser(_currentUser.value ?: current, backupToCloud = true)
                }
            } catch (e: Exception) {
                Log.e("UserManager", "Error during cloud restore in signInWithGoogle: ${e.message}", e)
                if (localProfile == null) {
                    _isOnboardingOpen.value = true
                }
            }
        }
    }

    fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }

        // Detect if user is switching accounts
        val currentEmail = _currentUser.value?.email?.trim()?.lowercase()
        val isAccountSwitch = !currentEmail.isNullOrBlank() && currentEmail != cleanEmail
        val oldUserToBackup = if (isAccountSwitch) _currentUser.value else null

        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$cleanEmail", null)
        val localProfile = if (!existingJson.isNullOrBlank()) {
            try {
                json.decodeFromString<UserProfile>(existingJson).copy(hasCompletedOnboarding = true)
            } catch (_: Exception) {
                null
            }
        } else null

        val derivedName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        val profile = localProfile ?: createNewUserProfile(derivedName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)

        persistUser(profile, backupToCloud = false)
        _isAuthDialogOpen.value = false

        if (localProfile != null) {
            _isOnboardingOpen.value = !localProfile.hasCompletedOnboarding
        } else {
            _isOnboardingOpen.value = false
        }

        // Trigger cloud restore in background
        scope.launch {
            if (isAccountSwitch) {
                try {
                    if (oldUserToBackup != null) {
                        cloudSyncManager.backupUserDataBlocking(oldUserToBackup)
                    }
                    databaseCleaner.clearAll()
                    userPlaylistsRepository.resetToDefault()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            cloudSyncManager.ensureAuthenticated()
            try {
                val restored = cloudSyncManager.restoreUserData(cleanEmail)
                val current = _currentUser.value ?: profile
                if (restored != null) {
                    val merged = current.copy(
                        id = restored.id.takeIf { it.isNotBlank() } ?: current.id,
                        name = restored.name.takeIf { it.isNotBlank() } ?: current.name,
                        username = restored.username ?: current.username,
                        bio = restored.bio ?: current.bio,
                        photoUrl = restored.photoUrl ?: current.photoUrl,
                        favoriteArtists = if (restored.favoriteArtists.isNotEmpty()) restored.favoriteArtists else current.favoriteArtists,
                        favoriteGenres = if (restored.favoriteGenres.isNotEmpty()) restored.favoriteGenres else current.favoriteGenres,
                        artistTasteWeights = if (restored.artistTasteWeights.isNotEmpty()) restored.artistTasteWeights else current.artistTasteWeights,
                        genreTasteWeights = if (restored.genreTasteWeights.isNotEmpty()) restored.genreTasteWeights else current.genreTasteWeights,
                        hasCompletedOnboarding = true,
                        createdAt = if (restored.createdAt > 0L) restored.createdAt else current.createdAt
                    )
                    persistUser(merged, backupToCloud = true)
                    _isOnboardingOpen.value = false
                } else {
                    if (localProfile == null) {
                        _isOnboardingOpen.value = true
                    }
                    persistUser(_currentUser.value ?: current, backupToCloud = true)
                }
            } catch (e: Exception) {
                Log.e("UserManager", "Error restoring user data on email sign in: ${e.message}", e)
                if (localProfile == null) {
                    _isOnboardingOpen.value = true
                }
            }
        }

        return Result.success(profile)
    }

    fun registerWithEmail(name: String, email: String, password: String): Result<UserProfile> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your name."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }

        // Detect if user is switching accounts
        val currentEmail = _currentUser.value?.email?.trim()?.lowercase()
        val isAccountSwitch = !currentEmail.isNullOrBlank() && currentEmail != cleanEmail
        val oldUserToBackup = if (isAccountSwitch) _currentUser.value else null

        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$cleanEmail", null)
        val profile = if (!existingJson.isNullOrBlank()) {
            try {
                json.decodeFromString<UserProfile>(existingJson).copy(name = cleanName)
            } catch (_: Exception) {
                createNewUserProfile(cleanName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)
            }
        } else {
            createNewUserProfile(cleanName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)
        }

        persistUser(profile, backupToCloud = false)
        _isAuthDialogOpen.value = false

        scope.launch {
            if (isAccountSwitch) {
                try {
                    if (oldUserToBackup != null) {
                        cloudSyncManager.backupUserDataBlocking(oldUserToBackup)
                    }
                    databaseCleaner.clearAll()
                    userPlaylistsRepository.resetToDefault()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            cloudSyncManager.ensureAuthenticated()
            try {
                val restored = cloudSyncManager.restoreUserData(cleanEmail)
                val current = _currentUser.value ?: profile
                if (restored != null) {
                    val merged = current.copy(
                        id = restored.id.takeIf { it.isNotBlank() } ?: current.id,
                        name = cleanName.ifBlank { restored.name },
                        username = restored.username ?: current.username,
                        bio = restored.bio ?: current.bio,
                        photoUrl = restored.photoUrl ?: current.photoUrl,
                        favoriteArtists = if (restored.favoriteArtists.isNotEmpty()) restored.favoriteArtists else current.favoriteArtists,
                        favoriteGenres = if (restored.favoriteGenres.isNotEmpty()) restored.favoriteGenres else current.favoriteGenres,
                        artistTasteWeights = if (restored.artistTasteWeights.isNotEmpty()) restored.artistTasteWeights else current.artistTasteWeights,
                        genreTasteWeights = if (restored.genreTasteWeights.isNotEmpty()) restored.genreTasteWeights else current.genreTasteWeights,
                        hasCompletedOnboarding = true,
                        createdAt = if (restored.createdAt > 0L) restored.createdAt else current.createdAt
                    )
                    persistUser(merged, backupToCloud = true)
                    _isOnboardingOpen.value = false
                } else {
                    _isOnboardingOpen.value = !profile.hasCompletedOnboarding
                    persistUser(_currentUser.value ?: profile, backupToCloud = true)
                }
            } catch (e: Exception) {
                _isOnboardingOpen.value = !profile.hasCompletedOnboarding
            }
        }

        return Result.success(profile)
    }

    private fun createNewUserProfile(
        name: String,
        email: String,
        photoUrl: String?,
        provider: AuthProvider
    ): UserProfile {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("account_created_timestamp", now).apply()
        return UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            photoUrl = photoUrl,
            provider = provider,
            hasCompletedOnboarding = false,
            favoriteArtists = emptyList(),
            favoriteGenres = emptyList(),
            artistTasteWeights = emptyMap(),
            genreTasteWeights = emptyMap(),
            createdAt = now
        )
    }

    fun getAccountCreationTime(): Long {
        val userCreated = _currentUser.value?.createdAt
        if (userCreated != null && userCreated > 0L) return userCreated
        val stored = prefs.getLong("account_created_timestamp", 0L)
        if (stored > 0L) return stored
        val now = System.currentTimeMillis()
        prefs.edit().putLong("account_created_timestamp", now).apply()
        return now
    }

    fun completeOnboarding(selectedArtists: List<String>, selectedGenres: List<String>) {
        val current = _currentUser.value ?: createNewUserProfile("Music Fan", "user@sielo.app", null, AuthProvider.EMAIL_PASSWORD)
        val initialArtistWeights = current.artistTasteWeights.toMutableMap()
        selectedArtists.forEach { artist ->
            initialArtistWeights[artist.trim().lowercase()] = (initialArtistWeights[artist.trim().lowercase()] ?: 0) + 10
        }

        val initialGenreWeights = current.genreTasteWeights.toMutableMap()
        selectedGenres.forEach { genre ->
            initialGenreWeights[genre.trim().lowercase()] = (initialGenreWeights[genre.trim().lowercase()] ?: 0) + 10
        }

        val updated = current.copy(
            hasCompletedOnboarding = true,
            favoriteArtists = selectedArtists.distinct(),
            favoriteGenres = selectedGenres.distinct(),
            artistTasteWeights = initialArtistWeights,
            genreTasteWeights = initialGenreWeights
        )

        persistUser(updated)
        _isOnboardingOpen.value = false
    }

    fun updateProfile(
        name: String,
        username: String? = null,
        bio: String? = null,
        photoUrl: String? = null,
        clearPhoto: Boolean = false
    ) {
        val current = _currentUser.value ?: return
        val newPhotoUrl = when {
            clearPhoto -> null
            photoUrl != null -> photoUrl
            else -> current.photoUrl
        }
        val cleanUser = username?.let { sanitizeUsername(it) }?.ifBlank { null }
        val updated = current.copy(
            name = name.trim().ifBlank { current.name },
            username = cleanUser ?: current.username,
            bio = bio?.trim()?.ifBlank { null },
            photoUrl = newPhotoUrl
        )
        persistUser(updated)
        if (!cleanUser.isNullOrBlank() && cleanUser != current.username?.let { sanitizeUsername(it) }) {
            scope.launch {
                claimUsername(cleanUser, updated)
            }
        }
    }

    suspend fun updateProfileWithUsernameCheck(
        name: String,
        newUsername: String?,
        bio: String? = null,
        photoUrl: String? = null,
        clearPhoto: Boolean = false
    ): Pair<Boolean, String> {
        val current = _currentUser.value ?: return Pair(false, "No active user")
        val cleanUser = newUsername?.let { sanitizeUsername(it) }?.ifBlank { null }

        if (!cleanUser.isNullOrBlank()) {
            if (!isValidUsername(cleanUser)) {
                return Pair(false, "Username must be 3-30 lowercase letters, numbers, '.', or '_'")
            }
            val currentUsername = current.username?.let { sanitizeUsername(it) }
            if (cleanUser != currentUsername) {
                val (available, message) = checkUsernameAvailability(cleanUser, current.email)
                if (!available) {
                    return Pair(false, message)
                }
                val claimed = claimUsername(cleanUser, current)
                if (!claimed) {
                    return Pair(false, "Could not reserve username. Please try again.")
                }
            }
        }

        val newPhotoUrl = when {
            clearPhoto -> null
            photoUrl != null -> photoUrl
            else -> current.photoUrl
        }
        val updated = current.copy(
            name = name.trim().ifBlank { current.name },
            username = cleanUser ?: current.username,
            bio = bio?.trim()?.ifBlank { null },
            photoUrl = newPhotoUrl
        )
        persistUser(updated, backupToCloud = true)
        return Pair(true, "Profile updated successfully")
    }

    /**
     * Dynamically updates user taste weights as they listen to music.
     */
    fun recordSongPlayed(artist: String, genre: String? = null) {
        val current = _currentUser.value ?: return
        val cleanArtist = artist.split(Regex("(?i)\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|/|;|\\bx\\b|\\bwith\\b)\\s*")).firstOrNull()?.trim()?.lowercase()
            ?: artist.trim().lowercase()

        val updatedArtistWeights = current.artistTasteWeights.toMutableMap()
        updatedArtistWeights[cleanArtist] = (updatedArtistWeights[cleanArtist] ?: 0) + 3

        val updatedGenreWeights = current.genreTasteWeights.toMutableMap()
        if (!genre.isNullOrBlank()) {
            val cleanGenre = genre.trim().lowercase()
            updatedGenreWeights[cleanGenre] = (updatedGenreWeights[cleanGenre] ?: 0) + 2
        }

        val updated = current.copy(
            artistTasteWeights = updatedArtistWeights,
            genreTasteWeights = updatedGenreWeights
        )
        persistUser(updated)
    }

    fun getFavoriteArtists(): List<String> {
        return _currentUser.value?.favoriteArtists ?: emptyList()
    }

    fun isArtistFollowed(artistName: String): Boolean {
        val clean = artistName.trim()
        return _currentUser.value?.favoriteArtists?.any { it.equals(clean, ignoreCase = true) } == true
    }

    fun toggleFollowArtist(artistName: String): Boolean {
        val current = _currentUser.value ?: return false
        val clean = artistName.trim()
        val isFollowed = current.favoriteArtists.any { it.equals(clean, ignoreCase = true) }
        val updatedList = if (isFollowed) {
            current.favoriteArtists.filterNot { it.equals(clean, ignoreCase = true) }
        } else {
            current.favoriteArtists + clean
        }
        val updatedWeights = current.artistTasteWeights.toMutableMap()
        if (!isFollowed) {
            updatedWeights[clean.lowercase()] = (updatedWeights[clean.lowercase()] ?: 0) + 15
        }
        val updated = current.copy(
            favoriteArtists = updatedList.distinct(),
            artistTasteWeights = updatedWeights
        )
        persistUser(updated)
        return !isFollowed
    }

    fun getFavoriteGenres(): List<String> {
        return _currentUser.value?.favoriteGenres ?: emptyList()
    }

    fun getTopTasteArtists(limit: Int = 5): List<String> {
        val user = _currentUser.value ?: return emptyList()
        val fromWeights = user.artistTasteWeights.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)

        if (fromWeights.isNotEmpty()) return fromWeights
        return user.favoriteArtists.take(limit)
    }

    fun signInAsGuest(): UserProfile {
        val guestId = UUID.randomUUID().toString().take(6)
        val profile = createNewUserProfile(
            name = "Guest $guestId",
            email = "guest_$guestId@sielo.local",
            photoUrl = null,
            provider = AuthProvider.GUEST
        )
        scope.launch(Dispatchers.IO) {
            try {
                databaseCleaner.clearAll()
                userPlaylistsRepository.resetToDefault()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        persistUser(profile, backupToCloud = false)
        _isAuthDialogOpen.value = false
        _isOnboardingOpen.value = true
        return profile
    }

    fun signOut() {
        val userToBackup = _currentUser.value
        _currentUser.value = null
        prefs.edit().remove(KEY_CURRENT_USER).apply()
        _isAuthDialogOpen.value = true
        _isOnboardingOpen.value = false
        scope.launch(Dispatchers.IO) {
            try {
                if (userToBackup != null) {
                    cloudSyncManager.backupUserDataBlocking(userToBackup)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                cloudSyncManager.stopAllSync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                playerManager.resetPlayer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                databaseCleaner.clearAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                userPlaylistsRepository.resetToDefault()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteAccount() {
        val email = _currentUser.value?.email?.trim()?.lowercase()
        _currentUser.value = null

        // 1. Wipe current user and email entry from auth preferences
        val authEditor = prefs.edit().remove(KEY_CURRENT_USER)
        if (!email.isNullOrBlank()) {
            authEditor.remove("$KEY_ALL_USERS_PREFIX$email")
        }
        authEditor.apply()

        // 2. Wipe related preferences (room sessions, player state, lyric offsets)
        try {
            context.getSharedPreferences("sielo_player_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("sielo_room_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("sielo_user_lyric_offsets", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("sielo_room_session", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Reset app flow to fresh auth
        _isOnboardingOpen.value = false
        _isAuthDialogOpen.value = true

        // 4. Background purge of database tables, feature cache, audio player, playlists, and disk cache
        scope.launch(Dispatchers.IO) {
            try {
                cloudSyncManager.stopAllSync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                playerManager.resetPlayer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                databaseCleaner.clearAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                userPlaylistsRepository.resetToDefault()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val featureFile = java.io.File(context.filesDir, "track_audio_features_cache.json")
                if (featureFile.exists()) featureFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                context.cacheDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
