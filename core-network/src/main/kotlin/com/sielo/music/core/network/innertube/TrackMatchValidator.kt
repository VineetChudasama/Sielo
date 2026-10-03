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
        "cover version", "jukebox", "audio jukebox", "video jukebox", "songs collection", "best romantic",
        "mix", "bollywood mix", "long drive", "heart touching", "romantic songs", "love songs",
        "sad songs", "party songs", "driving mix", "drive mix", "workout mix", "gym songs",
        "hits of", "voice of", "magic of", "selected songs", "golden hits", "evergreen hits",
        "all time favorite", "all time favourite", "lo-fi mix", "lofi mix", "lo-fi", "lofi",
        "slowed + reverb", "slowed and reverb", "club mix", "dj mix", "remix collection",
        "dance mix", "playlist", "top songs", "audio songs", "video songs", "non-stop", "non stop",
        "type beat", "type-beat", "reprod", "prod by", "produced by", "self-titled", "self titled",
        "popular songs", "popular hits", "top tracks", "all songs", "super hit songs",
        "ringtone", "status video", "status song", "speed up", "sped up", "nightcore",
        "unheard", "gems", "unheard gems", "yours truly", "your's truly",
        "through the years", "over the years", "across the years", "down the years",
        "tour hits", "sad love songs", "romantic hits", "chartbusters",
        "fan favorites", "fan favourites", "anthology", "retrospective", "the collection",
        "definitive collection", "ultimate collection", "anniversary edition", "anniversary collection",
        "platinum collection", "gold collection", "celebration", "spotlight",
        "rare", "vol.", "vol ", "volume", "vol-", "flash back", "flashback", "coffee with", "coffee aur",
        "memoirs", "legends", "legend", "maestros", "maestro", "masterworks", "inimitable", "portrait",
        "revival", "radio hits", "fm hits", "classic hits", "golden melodies", "finest moments", "audiobiography",
        "ek aur baar", "once more", "nostalgia", "duets of", "duets with", "tribute to", "50 original hits",
        "100 original hits", "the prodigy", "old songs", "rare songs"
    )

    fun isSoundtrackRelease(title: String, type: String? = null): Boolean {
        if (type?.equals("Soundtrack", ignoreCase = true) == true) return true
        val lower = title.lowercase().trim()
        return lower.contains("original motion picture") ||
                lower.contains("motion picture") ||
                lower.contains("soundtrack") ||
                lower.contains(" ost") ||
                lower.contains("(ost)") ||
                lower.contains("film score") ||
                lower.contains("original score") ||
                lower.contains("from \"") ||
                lower.contains("from '") ||
                lower.contains("movie songs")
    }

    fun isCompilationAlbum(album: String?, artist: String? = null): Boolean {
        if (album.isNullOrBlank()) return false
        val lower = album.lowercase().trim()

        // 1. Definite bootleg / beat / instrumental / karaoke markers
        if (lower.contains("type beat") || lower.contains("type-beat") ||
            lower.endsWith(" beat") || lower.contains(" beat ") || lower.endsWith(" beats") ||
            lower.contains("prod by") || lower.contains("reprod") ||
            lower.contains("karaoke") || lower.contains("instrumental") ||
            lower.contains("tribute") || lower.contains("cover version") ||
            lower.contains("slowed") || lower.contains("reverb") ||
            lower.contains("nightcore") || lower.contains("sped up") || lower.contains("speed up") ||
            lower.contains("ringtone") || lower.contains("status video") || lower.contains("status song") ||
            lower.contains("self-titled") || lower.contains("self titled")
        ) {
            return true
        }

        // 2. Compilation / playlist keywords
        if (lower == "hits" || lower == "essentials" || lower == "collection" || lower == "classics" ||
            lower == "popular songs" || lower == "popular hits" || lower == "top songs" ||
            lower == "best songs" || lower == "all songs" ||
            lower == "heart touching songs" || lower.startsWith("heart touching") ||
            lower.contains("long drive") || lower.contains("bollywood mix") || lower.contains(" dj ") ||
            lower.endsWith(" mix") || lower.endsWith(" - mix") || lower.contains(" mix - ")
        ) {
            return true
        }

        if (lower.contains("chillout") || lower.contains("heartbeats") || lower.contains("hot hits") ||
            lower.contains("best of") || lower.contains("greatest hits") || lower.contains("collection") ||
            lower.contains("jukebox") || lower.contains("non stop") || lower.contains("nonstop") ||
            lower.contains("mashup") || lower.contains("superhits") || lower.contains("blockbuster") ||
            lower.contains("heart touching") || lower.contains("romantic songs") || lower.contains("sad songs") ||
            lower.contains("party hits") || lower.contains("selected songs") ||
            lower.contains("popular songs") || lower.contains("popular hits") ||
            lower.contains("audio songs") || lower.contains("video songs") ||
            lower.contains("unheard") || lower.contains("gems") || lower.contains("yours truly") ||
            lower.contains("your's truly") || lower.contains("through the years") ||
            lower.contains("tour hits") || lower.contains("sad love songs") ||
            lower.startsWith("songs of ") || lower == "songs of love" || lower.contains("songs of love") ||
            lower.contains("love songs") || lower.contains("golden songs") || lower.contains("film songs") ||
            lower.contains("movie hits") || lower.contains("evergreen hits") || lower.contains("anthology") ||
            lower.contains("the essential") || lower.contains("the very best") || lower.contains("memories of")
        ) {
            return true
        }

        // 3. Artist-specific compilation and bootleg checks
        if (!artist.isNullOrBlank()) {
            val aLower = artist.lowercase().trim()
            val tokens = aLower.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }

            // Common generic words that form bootlegs/compilations when combined with an artist name
            // e.g., "Desi Arijit Singh", "Emotional Arijit", "Arijit Singh Hits", "Your's Truly Arijit", "Arijit Singh - Unheard Gems"
            val genericWords = listOf(
                "desi", "emotional", "romantic", "sad", "party", "hits", "popular", "best", "golden",
                "essential", "essentials", "classics", "collection", "tribute", "unplugged", "melodies",
                "voice of", "magic of", "superhit", "superhits", "all time", "evergreen", "favorite", "favourite",
                "nonstop", "non-stop", "jukebox", "compilation", "mashup", "special", "drive", "touching",
                "self-titled", "self titled", "gems", "unheard", "truly", "years", "tour", "vault",
                "unheard gems", "yours truly", "your's truly", "rare", "vol", "volume", "flash back", "flashback",
                "coffee", "memoirs", "legends", "legend", "maestros", "maestro", "masterworks", "inimitable",
                "portrait", "revival", "radio", "finest", "audiobiography", "ek aur baar", "nostalgia", "duets",
                "prodigy", "old songs"
            )

            for (w in genericWords) {
                if (lower.contains(w)) {
                    if (lower.contains(aLower) || (tokens.isNotEmpty() && tokens.all { lower.contains(it) }) || (tokens.size >= 2 && tokens.any { lower.contains(it) && it.length >= 4 })) {
                        return true
                    }
                }
            }

            if (lower.contains(aLower) && (
                lower.contains("hits") || lower.contains("best") ||
                lower.contains("collection") || lower.contains("essentials") ||
                lower.contains("songs") || lower.contains("melodies") ||
                lower.contains("mix") || lower.contains("drive") || lower.contains("touching") ||
                lower.contains("self-titled") || lower.contains("self titled") ||
                lower.contains("popular") || lower.contains("gems") || lower.contains("unheard") ||
                lower.contains("truly") || lower.contains("years") || lower.contains("tour")
            )) {
                return true
            }
        }
        return COMPILATION_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Strictly verifies whether an album was authentically made by the artist.
     * Rejects compilations, playlists, tribute collections, type beats, and albums created by other artists.
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

        // 3. Reject beats, instrumentals, or cover songs passed off as albums
        if (titleLower.contains("type beat") || titleLower.contains("type-beat") ||
            titleLower.endsWith(" beat") || titleLower.contains(" beat ") ||
            titleLower.contains("instrumental") || titleLower.contains("karaoke") ||
            titleLower.contains("tribute") || titleLower.contains("cover") ||
            ((titleLower == "songs of love" || titleLower.startsWith("songs of love")) && artistLower != "adele")
        ) {
            return false
        }

        // 4. Check JioSaavn structured artist fields if provided
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

        // 5. If album artist string is provided (e.g. from YouTube Music, iTunes, Deezer)
        if (!albumArtist.isNullOrBlank()) {
            val albumArtistLower = albumArtist.lowercase().trim()
            if (albumArtistLower.contains("various artists", ignoreCase = true) ||
                albumArtistLower.contains("compilation", ignoreCase = true)
            ) {
                return false
            }

            // Reject third-party tribute / karaoke / instrumental / cover artists
            val spamArtistKeywords = listOf("tribute", "karaoke", "sing2piano", "sing2guitar", "instrumental", "quartet", "orchestra", "renditions")
            if (spamArtistKeywords.any { albumArtistLower.contains(it) }) {
                return false
            }

            // Word-boundary / exact token matching:
            // Prevents "sombrerobeach" or "sombrero" from matching "sombr"
            val wordBoundaryRegex = Regex("""\b${Regex.escape(artistLower)}\b""", RegexOption.IGNORE_CASE)
            val splitArtists = albumArtistLower.split(Regex("[,&/|;]|\\b(ft|feat|featuring|with)\\b")).map { it.trim() }

            val matchesArtist = splitArtists.any { part ->
                part.equals(artistLower, ignoreCase = true) ||
                (part.length > artistLower.length && wordBoundaryRegex.containsMatchIn(part))
            }

            if (!matchesArtist) {
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
        val cleanTarget = targetArtist.trim().lowercase()
        if (cleanTarget.isBlank()) return true

        val aLower = trackArtist.orEmpty().trim().lowercase()
        val tLower = trackTitle.orEmpty().trim().lowercase()

        val wordBoundaryRegex = Regex("""\b${Regex.escape(cleanTarget)}\b""", RegexOption.IGNORE_CASE)

        if (wordBoundaryRegex.containsMatchIn(aLower)) return true
        if (wordBoundaryRegex.containsMatchIn(tLower)) return true

        val splitArtists = aLower.split(Regex("[,&/|;]|\\b(ft|feat|featuring|with)\\b")).map { it.trim() }
        if (splitArtists.any { it == cleanTarget || (it.length > cleanTarget.length && wordBoundaryRegex.containsMatchIn(it)) }) {
            return true
        }

        return false
    }

    fun isStrictArtistMatch(candidate: String, target: String): Boolean {
        val c = candidate.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        val t = target.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        if (c.isBlank() || t.isBlank()) return false
        return c == t
    }
}

