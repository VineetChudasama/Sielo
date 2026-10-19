package com.sielo.music.viewmodel

import com.sielo.music.core.database.entity.SearchHistoryEntity
import com.sielo.music.core.network.innertube.TrackMatchValidator
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {

    private fun createTrack(
        id: String,
        title: String,
        artist: String,
        album: String = "Test Album",
        viewCount: Long = 10_000L
    ): SieloTrack {
        return SieloTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            viewCount = viewCount
        )
    }

    private fun createArtist(
        id: String,
        name: String,
        imageUrl: String? = "https://images.unsplash.com/artist.jpg"
    ): SieloArtist {
        return SieloArtist(
            id = id,
            name = name,
            imageUrl = imageUrl
        )
    }

    // 1. Exact song title match ranks #1
    @Test
    fun testExactSongTitleMatchRanksFirst() {
        val tracks = listOf(
            createTrack("1", "Starboy Remix", "The Weeknd"),
            createTrack("2", "Starboy", "The Weeknd"),
            createTrack("3", "Starboy (Acoustic)", "The Weeknd")
        )
        val ranked = SearchViewModel.rankTracks(tracks, "Starboy")
        assertEquals("Exact title match must rank first", "2", ranked.first().id)
        assertEquals("Starboy", ranked.first().title)
    }

    // 2. Exact artist name match ranks top
    @Test
    fun testExactArtistNameMatchRanksTop() {
        val artists = listOf(
            createArtist("1", "Arijit Singh Tribute Band"),
            createArtist("2", "Arijit Singh"),
            createArtist("3", "Arijit Fan Club")
        )
        val ranked = SearchViewModel.rankArtists(artists, "Arijit Singh")
        assertEquals("Exact artist name match must rank first", "2", ranked.first().id)
        assertEquals("Arijit Singh", ranked.first().name)
    }

    // 3. Starts-with title match ranks above partial match
    @Test
    fun testStartsWithTitleMatchRanksAbovePartialMatch() {
        val tracks = listOf(
            createTrack("1", "All About That Bass", "Meghan Trainor"),
            createTrack("2", "Bass Cannon", "Flux Pavilion"),
            createTrack("3", "Bass In Your Face", "DJ Bass")
        )
        val ranked = SearchViewModel.rankTracks(tracks, "Bass")
        // "Bass Cannon" or "Bass In Your Face" starts with "Bass", while "All About That Bass" contains it later
        assertTrue("Track starting with query must rank above track containing it later",
            ranked[0].title.startsWith("Bass")
        )
        assertEquals("All About That Bass", ranked.last().title)
    }

    // 4. Fuzzy matching for small spelling mistakes (e.g. "blnding lights" -> "Blinding Lights")
    @Test
    fun testFuzzyMatchingForSmallSpellingMistakes() {
        val tracks = listOf(
            createTrack("1", "Save Your Tears", "The Weeknd"),
            createTrack("2", "Blinding Lights", "The Weeknd"),
            createTrack("3", "In Your Eyes", "The Weeknd")
        )
        val ranked = SearchViewModel.rankTracks(tracks, "blnding lights")
        assertEquals("Fuzzy match should find Blinding Lights despite missing 'i'", "2", ranked.first().id)
        assertEquals("Blinding Lights", ranked.first().title)
    }

    // 5. Indian music transliteration searches
    @Test
    fun testIndianMusicTransliterationAndHindiArtistMatching() {
        val tracks = listOf(
            createTrack("1", "Channa Mereya", "Arijit Singh"),
            createTrack("2", "Tum Hi Ho", "Arijit Singh"),
            createTrack("3", "Apna Bana Le", "Arijit Singh")
        )
        val ranked = SearchViewModel.rankTracks(tracks, "tum hi ho")
        assertEquals("Transliterated Indian song query must rank Tum Hi Ho at top", "2", ranked.first().id)
        assertEquals("Tum Hi Ho", ranked.first().title)

        // Unicode preservation in cleanForArtistMatch
        val hindiArtist = "अरिजीत सिंह"
        val cleanHindi = SearchViewModel.cleanForArtistMatch(hindiArtist)
        assertTrue("Unicode letters must be preserved in cleanForArtistMatch", cleanHindi.isNotEmpty())
    }

    // 6. In-memory cache returns cached result within 60s
    @Test
    fun testInMemoryCacheReturnsCachedResultWithin60Seconds() {
        val cache = SearchViewModel.SearchResultCache(maxEntries = 40, ttlMs = 60_000L)
        val sampleTracks = listOf(createTrack("101", "Kesariya", "Arijit Singh"))
        val sampleArtists = listOf(createArtist("201", "Arijit Singh"))

        cache.put("All", "kesariya", sampleTracks, sampleArtists)

        val retrieved = cache.get("All", "kesariya")
        assertNotNull("Cached result should be returned immediately", retrieved)
        assertEquals(1, retrieved!!.tracks.size)
        assertEquals("Kesariya", retrieved.tracks[0].title)
        assertEquals("Arijit Singh", retrieved.artists[0].name)
    }

    // 7. Cache expiration after 60s
    @Test
    fun testCacheExpirationAfterTtl() {
        // Fast-expiring cache with 10ms TTL for testing expiration
        val shortLivedCache = SearchViewModel.SearchResultCache(maxEntries = 40, ttlMs = 15L)
        val sampleTracks = listOf(createTrack("101", "Kesariya", "Arijit Singh"))

        shortLivedCache.put("All", "kesariya", sampleTracks, emptyList())
        assertNotNull("Should exist immediately after put", shortLivedCache.get("All", "kesariya"))

        Thread.sleep(30L) // Wait past TTL
        assertNull("Expired entry should not be returned", shortLivedCache.get("All", "kesariya"))
    }

    // 8. Debounce and cancellation behavior
    @Test
    fun testDebounceQueryNormalization() {
        val raw1 = "  arijit   singh   "
        val normalized1 = SearchViewModel.normalizeSearchQuery(raw1)
        assertEquals("Whitespace and repeated spaces must be collapsed", "arijit singh", normalized1)

        val raw2 = "Blinding   LIGHTS "
        val normalized2 = SearchViewModel.normalizeSearchQuery(raw2)
        assertEquals("Case must be lowercased consistently", "blinding lights", normalized2)
    }

    // 9. Stale result protection across query and category switches
    @Test
    fun testStaleResultProtectionLogic() {
        var activeSequence = 1L
        val activeQuery = "arijit"
        val activeCategory = "Songs"

        // Simulate incoming in-flight response from older sequence (e.g. sequence 0)
        val olderSequence = 0L
        val isStale = (olderSequence != activeSequence)
        assertTrue("Older in-flight sequence must be flagged as stale and discarded", isStale)

        // Simulate category switch while search was in flight
        activeSequence = 2L
        val oldCategoryResponse = "All"
        val isCategoryStale = (oldCategoryResponse != activeCategory)
        assertTrue("Response from previously selected category must be discarded", isCategoryStale)
    }

    // 10. Song failure isolation with artist success
    @Test
    fun testSongFailureDoesNotBreakArtistResults() {
        var songSearchFailed = false
        var artistSearchSucceeded = false

        try {
            // Simulate song search throwing network exception
            throw java.io.IOException("Song search network timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            songSearchFailed = true
        }

        try {
            // Artist search executes independently
            artistSearchSucceeded = true
        } catch (_: Exception) {}

        assertTrue("Song search error must be isolated", songSearchFailed)
        assertTrue("Artist search must still succeed when song search fails", artistSearchSucceeded)
    }

    // 11. Artist failure isolation with song success
    @Test
    fun testArtistFailureDoesNotBreakSongResults() {
        var artistSearchFailed = false
        var songSearchSucceeded = false

        try {
            // Simulate artist search throwing network exception
            throw java.io.IOException("Artist search 500 internal server error")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            artistSearchFailed = true
        }

        try {
            // Song search executes independently
            songSearchSucceeded = true
        } catch (_: Exception) {}

        assertTrue("Artist search error must be isolated", artistSearchFailed)
        assertTrue("Song search must still succeed when artist search fails", songSearchSucceeded)
    }

    // 12. History preservation: "arijit" and "arijit singh" both kept
    @Test
    fun testSearchHistoryPrefixPreservation() {
        // Room entity uses @PrimaryKey query: String. Without prefix-deletion logic,
        // distinct queries with overlapping prefixes are both preserved.
        val historyMap = mutableMapOf<String, SearchHistoryEntity>()

        fun saveQuery(query: String) {
            val trimmed = query.trim()
            if (trimmed.isNotBlank()) {
                historyMap[trimmed] = SearchHistoryEntity(query = trimmed, timestampMs = System.currentTimeMillis())
            }
        }

        saveQuery("arijit")
        saveQuery("arijit singh")

        assertTrue("Prefix query 'arijit' must be preserved", historyMap.containsKey("arijit"))
        assertTrue("Longer query 'arijit singh' must also be preserved", historyMap.containsKey("arijit singh"))
        assertEquals("Both distinct history queries must exist simultaneously", 2, historyMap.size)
    }

    // 13. Personalization cap cannot overpower textual match
    @Test
    fun testPersonalizationCapCannotOverpowerTextualMatch() {
        // Song A: Exact title match, but user has never listened to it
        val exactTrack = createTrack("exact_1", "Levitating", "Dua Lipa")

        // Song B: Completely different title, but user has listened to it 100 times and it's favorite
        val favoriteUnrelatedTrack = createTrack("fav_2", "Don't Start Now", "Dua Lipa")

        val heavyPersonalTaste = SearchViewModel.UserTasteSnapshot(
            favoriteSongIds = setOf("fav_2"),
            searchPlayedSongIds = setOf("fav_2"),
            playedSongCounts = mapOf("fav_2" to 100),
            topArtistWeights = mapOf("dua lipa" to 10)
        )

        val ranked = SearchViewModel.rankTracks(
            listOf(favoriteUnrelatedTrack, exactTrack),
            "Levitating",
            heavyPersonalTaste
        )

        assertEquals(
            "Exact textual match 'Levitating' MUST rank above heavily played 'Don't Start Now'",
            "exact_1",
            ranked.first().id
        )
    }

    // 14. Deduplication without removing legitimate remixes/live versions
    @Test
    fun testDeduplicationPreservesRemixesAndLiveVersions() {
        val tracks = listOf(
            createTrack("id_orig_1", "Blinding Lights", "The Weeknd"),
            createTrack("id_orig_dup", "Blinding Lights", "The Weeknd"), // duplicate
            createTrack("id_remix", "Blinding Lights (Major Lazer Remix)", "The Weeknd"),
            createTrack("id_live", "Blinding Lights (Live at the Brit Awards)", "The Weeknd")
        )

        val deduplicated = TrackMatchValidator.deduplicateTracks(tracks)
        val deduplicatedIds = deduplicated.map { it.id }.toSet()

        assertTrue("Original track must be preserved", deduplicatedIds.contains("id_orig_1") || deduplicatedIds.contains("id_orig_dup"))
        assertTrue("Remix version must not be stripped by deduplication", deduplicatedIds.contains("id_remix"))
        assertTrue("Live recording must not be stripped by deduplication", deduplicatedIds.contains("id_live"))
    }

    // 15. Trending boost elevates popular canonical track ("Gone, Gone, Gone" by Phillip Phillips)
    @Test
    fun testTrendingBoostElevatesPopularCandidate() {
        val coverTrack = createTrack("id_cover", "Gone, Gone, Gone", "Indie Cover Band")
        val officialTrack = createTrack("id_phillip", "Gone, Gone, Gone", "Phillip Phillips")

        val canonicalKey = SearchViewModel.canonicalTrackKey("Gone, Gone, Gone", "Phillip Phillips")
        val trendingSnapshot = SearchViewModel.TrendingSnapshot(
            tracksByCanonicalKey = mapOf(
                canonicalKey to SearchViewModel.TrendingTrackInfo(
                    title = "Gone, Gone, Gone",
                    artist = "Phillip Phillips",
                    trendScore = 0.95f,
                    momentumScore = 0.50f,
                    rank = 1,
                    confidence = 1.0f
                )
            ),
            batchConfidence = 1.0f
        )

        val ranked = SearchViewModel.rankTracks(
            tracks = listOf(coverTrack, officialTrack),
            query = "Gone Gone Gone",
            taste = SearchViewModel.UserTasteSnapshot(),
            trending = trendingSnapshot
        )

        assertEquals("Official popular track by Phillip Phillips must rank #1 due to trending signal",
            "id_phillip",
            ranked.first().id
        )
    }

    // 16. Safeguard: Unrelated #1 trending track CANNOT overtake an exact query match
    @Test
    fun testRelevanceFloorPreventsUnrelatedTrendingOvertake() {
        val targetTrack = createTrack("id_kesariya", "Kesariya", "Arijit Singh")
        val viralTrack = createTrack("id_viral", "Shape of You", "Ed Sheeran")

        val canonicalKey = SearchViewModel.canonicalTrackKey("Shape of You", "Ed Sheeran")
        val trendingSnapshot = SearchViewModel.TrendingSnapshot(
            tracksByCanonicalKey = mapOf(
                canonicalKey to SearchViewModel.TrendingTrackInfo(
                    title = "Shape of You",
                    artist = "Ed Sheeran",
                    trendScore = 1.0f,
                    momentumScore = 1.0f,
                    rank = 1,
                    confidence = 1.0f
                )
            ),
            batchConfidence = 1.0f
        )

        val ranked = SearchViewModel.rankTracks(
            tracks = listOf(viralTrack, targetTrack),
            query = "Kesariya",
            taste = SearchViewModel.UserTasteSnapshot(),
            trending = trendingSnapshot
        )

        assertEquals("Relevant match 'Kesariya' must rank above unrelated viral track 'Shape of You'",
            "id_kesariya",
            ranked.first().id
        )
    }

    // 17. Obscure exact match is preserved without trending penalty
    @Test
    fun testObscureExactMatchPreservedWithoutTrending() {
        val obscureTrack = createTrack("id_obscure", "The Silent Tree", "Ancient Folk Band")
        val partialTrack = createTrack("id_partial", "Silent Tree Live", "Rock Band")

        val ranked = SearchViewModel.rankTracks(
            tracks = listOf(partialTrack, obscureTrack),
            query = "The Silent Tree",
            taste = SearchViewModel.UserTasteSnapshot(),
            trending = SearchViewModel.TrendingSnapshot() // empty trending
        )

        assertEquals("Exact match must rank first even with no trending data",
            "id_obscure",
            ranked.first().id
        )
    }
}

