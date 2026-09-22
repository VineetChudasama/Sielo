package com.sielo.music.core.auth

import android.content.Context
import com.sielo.music.core.auth.model.AuthProvider
import com.sielo.music.core.auth.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseCleaner: com.sielo.music.core.database.DatabaseCleaner,
    private val playerManager: com.sielo.music.core.audio.PlayerManager
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
    }

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedJson = prefs.getString(KEY_CURRENT_USER, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val profile = json.decodeFromString<UserProfile>(savedJson)
                _currentUser.value = profile
                if (!profile.hasCompletedOnboarding) {
                    _isOnboardingOpen.value = true
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

    private fun persistUser(profile: UserProfile) {
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

    fun signInWithGoogle(name: String, email: String, photoUrl: String?) {
        val cleanEmail = email.trim().lowercase()
        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$cleanEmail", null)
        val profile = if (!existingJson.isNullOrBlank()) {
            try {
                val existing = json.decodeFromString<UserProfile>(existingJson)
                existing.copy(name = name, photoUrl = photoUrl ?: existing.photoUrl, provider = AuthProvider.GOOGLE)
            } catch (_: Exception) {
                createNewUserProfile(name, cleanEmail, photoUrl, AuthProvider.GOOGLE)
            }
        } else {
            createNewUserProfile(name, cleanEmail, photoUrl, AuthProvider.GOOGLE)
        }

        persistUser(profile)
        _isAuthDialogOpen.value = false
        if (!profile.hasCompletedOnboarding) {
            _isOnboardingOpen.value = true
        }
    }

    fun signInWithEmail(email: String, password: String):Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }

        val existingJson = prefs.getString("$KEY_ALL_USERS_PREFIX$cleanEmail", null)
        val profile = if (!existingJson.isNullOrBlank()) {
            try {
                json.decodeFromString<UserProfile>(existingJson)
            } catch (_: Exception) {
                val derivedName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                createNewUserProfile(derivedName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)
            }
        } else {
            val derivedName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            createNewUserProfile(derivedName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)
        }

        persistUser(profile)
        _isAuthDialogOpen.value = false
        if (!profile.hasCompletedOnboarding) {
            _isOnboardingOpen.value = true
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

        val profile = createNewUserProfile(cleanName, cleanEmail, null, AuthProvider.EMAIL_PASSWORD)
        persistUser(profile)
        _isAuthDialogOpen.value = false
        _isOnboardingOpen.value = true
        return Result.success(profile)
    }

    private fun createNewUserProfile(
        name: String,
        email: String,
        photoUrl: String?,
        provider: AuthProvider
    ): UserProfile {
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
            genreTasteWeights = emptyMap()
        )
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

    fun updateProfile(name: String, username: String? = null, bio: String? = null) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            name = name.trim().ifBlank { current.name },
            username = username?.trim()?.removePrefix("@")?.ifBlank { null },
            bio = bio?.trim()?.ifBlank { null }
        )
        persistUser(updated)
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
        persistUser(profile)
        _isAuthDialogOpen.value = false
        _isOnboardingOpen.value = true
        return profile
    }

    fun signOut() {
        _currentUser.value = null
        prefs.edit().remove(KEY_CURRENT_USER).apply()
        _isAuthDialogOpen.value = true
        _isOnboardingOpen.value = false
        scope.launch(Dispatchers.IO) {
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

        // 4. Background purge of database tables, feature cache, audio player, and disk cache
        scope.launch(Dispatchers.IO) {
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
