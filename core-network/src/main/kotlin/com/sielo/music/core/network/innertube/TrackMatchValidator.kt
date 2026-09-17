package com.sielo.music.core.network.innertube

/**
 * Validates whether candidate tracks and metadata from external providers (e.g. JioSaavn)
 * actually match the user's requested song or search query.
 *
 * Prevents issues where unrelated popular songs (like "Tum Hi Ho") get falsely returned
 * or streamed for queries like "Yo Ho Ho".
 */
object TrackMatchValidator {

    fun isFuzzyMatch(
        requestedTitle: String,
        candidateTitle: String,
        requestedArtist: String? = null,
        candidateArtist: String? = null
    ): Boolean {
        val reqClean = cleanTitle(requestedTitle)
        val candClean = cleanTitle(candidateTitle)

        if (reqClean.isBlank() || candClean.isBlank()) return false
        if (reqClean.equals(candClean, ignoreCase = true)) return true

        val reqTokens = reqClean.lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
        val candTokens = candClean.lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }

        if (reqTokens.isEmpty() || candTokens.isEmpty()) {
            return reqClean.contains(candClean, ignoreCase = true) || candClean.contains(reqClean, ignoreCase = true)
        }

        val reqUnique = reqTokens.toSet()
        val matchingTokens = reqUnique.count { reqToken -> candTokens.contains(reqToken) }

        // Short queries / titles (1 or 2 distinct words, e.g. "yo ho" in "Yo Ho Ho"):
        // Every single keyword MUST be present in the candidate title
        if (reqUnique.size <= 2) {
            if (matchingTokens < reqUnique.size) {
                return false
            }
        } else {
            // For longer titles (3+ words), require >= 60% token match
            val matchRatio = matchingTokens.toDouble() / reqUnique.size
            if (matchRatio < 0.60) {
                return false
            }
        }

        // Artist check: if both artists are specified and have tokens, they MUST share at least one keyword
        if (!requestedArtist.isNullOrBlank() && !candidateArtist.isNullOrBlank()) {
            val reqArtistTokens = cleanArtist(requestedArtist).lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
            val candArtistTokens = cleanArtist(candidateArtist).lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
            if (reqArtistTokens.isNotEmpty() && candArtistTokens.isNotEmpty()) {
                val hasArtistOverlap = reqArtistTokens.any { reqTok ->
                    candArtistTokens.any { candTok -> reqTok == candTok || reqTok.contains(candTok) || candTok.contains(reqTok) }
                }
                if (!hasArtistOverlap) {
                    return false
                }
            }
        }

        return true
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(video.*?\\)|\\[video.*?\\]|\\(audio.*?\\)|\\[audio.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(remix.*?\\)|\\[remix.*?\\]"), "")
            .replace(Regex("(?i)\\b(official music video|official video|official audio|full video|hd 4k|4k|audio|lyric video|remix)\\b"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("(?i)\\b(topic|vevo|official|channel|music|records)\\b"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
    }

    /**
     * Generates a canonical deduplication key by normalizing title noise (like "(From ...)")
     * and sorting artist names alphabetically so swapped artist orders collapse together.
     */
    fun canonicalTrackKey(title: String, artist: String): String {
        val cleanT = title
            .replace(Regex("(?i)\\((from|official|audio|video|lyric|original|movie|full song|deluxe|remaster|best of).*?\\)"), "")
            .replace(Regex("(?i)\\[(from|official|audio|video|lyric|original|movie|full song|deluxe|remaster|best of).*?\\]"), "")
            .replace(Regex("(?i)\\b(from \"[^\"]+\"|from '[^']+'|official music video|official video|official audio|full video|hd 4k|4k|audio|lyric video|remix)\\b"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

        val splitArtists = artist.lowercase()
            .split(Regex("[,&/|;]|\\b(ft|feat|featuring)\\b"))
            .map { a ->
                a.replace(Regex("(?i)\\b(topic|vevo|official|channel|music|records)\\b"), "")
                    .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString("+")

        return "$cleanT|$splitArtists"
    }

    fun deduplicateTracks(tracks: List<com.sielo.music.core.network.models.SieloTrack>): List<com.sielo.music.core.network.models.SieloTrack> {
        val seen = HashSet<String>()
        val result = ArrayList<com.sielo.music.core.network.models.SieloTrack>()
        for (track in tracks) {
            val key = canonicalTrackKey(track.title, track.artist)
            if (key.isBlank() || seen.add(key)) {
                result.add(track)
            }
        }
        return result
    }
}
