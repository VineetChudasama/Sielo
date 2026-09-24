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

        val COMPILATION_KEYWORDS = listOf(
        "best of", "greatest hits", "compilation", "the ultimate collection", "essential hits",
        "the very best of", "unplugged collection", "non stop hits", "mashup",
        "old hindi songs", "old is gold", "all time hits", "superhits", "blockbuster hits",
        "chillout", "heartbeats", "hot hits", "collection", "collections", "bollywood chillout",
        "heartbeats bollywood", "hot hits bollywood", "party hits", "dance hits", "love hits",
        "top 10", "top 20", "top 50", "top 100", "karaoke", "instrumental", "tribute", "cover",
        "jukebox", "audio jukebox", "video jukebox", "songs collection", "best romantic"
    )

    fun isCompilationAlbum(album: String?, artist: String? = null): Boolean {
        if (album.isNullOrBlank()) return false
        val lower = album.lowercase().trim()

        if (lower == "hits" || lower == "essentials" || lower == "collection" || lower == "classics") {
            return true
        }

        if (lower.contains("chillout") || lower.contains("heartbeats") || lower.contains("hot hits") ||
            lower.contains("best of") || lower.contains("greatest hits") || lower.contains("collection") ||
            lower.contains("jukebox") || lower.contains("non stop") || lower.contains("nonstop") ||
            lower.contains("mashup") || lower.contains("superhits") || lower.contains("blockbuster")
        ) {
            return true
        }

        if (!artist.isNullOrBlank()) {
            val aLower = artist.lowercase().trim()
            if (lower.contains(aLower) && (
                lower.contains("hits") || lower.contains("best") ||
                lower.contains("collection") || lower.contains("essentials") ||
                lower.contains("songs") || lower.contains("melodies")
            )) {
                return true
            }
        }
        return COMPILATION_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Strictly verifies whether an album was authentically made by the artist.
     * Rejects compilations, playlists, tribute collections, and albums created by other artists.
     */
    fun isAlbumMadeByArtist(
        albumTitle: String,
        albumArtist: String?,
        targetArtist: String,
        primaryArtists: String? = null,
        singers: String? = null,
        music: String? = null
    ): Boolean {
        val titleLower = albumTitle.lowercase().trim()
        val artistLower = targetArtist.lowercase().trim()

        // 1. Must not be a compilation or generic playlist
        if (isCompilationAlbum(titleLower, targetArtist)) {
            return false
        }

        // 2. Reject if the title explicitly attributes the work to other artists (e.g. "by Rafi & Lata")
        if (titleLower.contains(" by ") && !titleLower.contains(artistLower)) {
            return false
        }

        // 3. Check JioSaavn structured artist fields if provided
        val hasStructuredArtists = !primaryArtists.isNullOrBlank() || !singers.isNullOrBlank() || !music.isNullOrBlank()
        if (hasStructuredArtists) {
            val inPrimary = primaryArtists?.contains(artistLower, ignoreCase = true) == true
            val inSingers = singers?.contains(artistLower, ignoreCase = true) == true
            val inMusic = music?.contains(artistLower, ignoreCase = true) == true

            // For an original album, artist must be primary artist, singer, or composer
            if (!inPrimary && !inSingers && !inMusic) {
                return false
            }
        }

        // 4. If album artist string is provided (e.g. from YouTube Music)
        if (!albumArtist.isNullOrBlank()) {
            val albumArtistLower = albumArtist.lowercase().trim()
            if (albumArtistLower.contains("various artists", ignoreCase = true) ||
                albumArtistLower.contains("compilation", ignoreCase = true)
            ) {
                return false
            }
            // If the albumArtist does not match target artist and does not contain it
            if (!albumArtistLower.contains(artistLower) && !artistLower.contains(albumArtistLower)) {
                return false
            }
        }

        return true
    }

    fun isYouTubeChannelId(idOrName: String?): Boolean {
        if (idOrName.isNullOrBlank()) return false
        val clean = idOrName.trim()
        return (clean.startsWith("UC") && clean.length in 20..32 && clean.none { it.isWhitespace() }) ||
               (clean.startsWith("FE") && clean.length in 10..32 && clean.none { it.isWhitespace() }) ||
               (clean.startsWith("VL") && clean.length in 10..34 && clean.none { it.isWhitespace() })
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
        val groups = LinkedHashMap<String, MutableList<com.sielo.music.core.network.models.SieloTrack>>()
        for (track in tracks) {
            val key = canonicalTrackKey(track.title, track.artist)
            if (key.isBlank()) {
                groups.getOrPut(track.id) { ArrayList() }.add(track)
            } else {
                groups.getOrPut(key) { ArrayList() }.add(track)
            }
        }

        val result = ArrayList<com.sielo.music.core.network.models.SieloTrack>()
        for ((_, group) in groups) {
            // Sort each group so that:
            // 1. Original studio/movie releases come BEFORE compilation re-issues (prevents fake compilation art)
            // 2. High-res studio artwork (c.saavncdn.com) preferred over YouTube video thumbnails (i.ytimg.com)
            // 3. Audio stream URL availability preferred
            val bestTrack = group.minWithOrNull(
                compareBy<com.sielo.music.core.network.models.SieloTrack> { track ->
                    if (isCompilationAlbum(track.album, track.artist)) 1 else 0
                }.thenBy { track ->
                    val thumb = track.thumbnailUrl ?: ""
                    if (thumb.contains("c.saavncdn.com")) 0 else if (thumb.contains("i.ytimg.com")) 2 else 1
                }.thenBy { track ->
                    if (!track.streamUrl.isNullOrBlank()) 0 else 1
                }
            ) ?: group.first()
            result.add(bestTrack)
        }
        return result
    }

    fun isSongByOrFeaturingArtist(
        trackTitle: String?,
        trackArtist: String?,
        targetArtist: String?
    ): Boolean {
        if (targetArtist.isNullOrBlank()) return true
        val cleanTarget = targetArtist.trim()
        val targetNorm = cleanTarget.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (targetNorm.isBlank()) return true

        val artistStr = trackArtist.orEmpty()
        val titleStr = trackTitle.orEmpty()

        val artistNorm = artistStr.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (artistNorm.contains(targetNorm)) return true

        val titleNorm = titleStr.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (titleNorm.contains(targetNorm)) return true

        // Check if all meaningful tokens of targetArtist exist in track artist or title
        val tokens = cleanTarget.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        if (tokens.size >= 2) {
            val aLower = artistStr.lowercase()
            val tLower = titleStr.lowercase()
            val inArtist = tokens.all { aLower.contains(it) }
            val inTitle = tokens.all { tLower.contains(it) }
            if (inArtist || inTitle) return true
        }

        return false
    }
}

