package com.sielo.music.core.auth.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider {
    GOOGLE,
    EMAIL_PASSWORD,
    GUEST
}

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.EMAIL_PASSWORD,
    val hasCompletedOnboarding: Boolean = false,
    val favoriteArtists: List<String> = emptyList(),
    val favoriteGenres: List<String> = emptyList(),
    val artistTasteWeights: Map<String, Int> = emptyMap(),
    val genreTasteWeights: Map<String, Int> = emptyMap(),
    val bio: String? = null,
    val username: String? = null
)
