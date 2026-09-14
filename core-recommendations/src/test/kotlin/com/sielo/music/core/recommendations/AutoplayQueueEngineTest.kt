package com.sielo.music.core.recommendations

import com.sielo.music.core.database.dao.RecommendationHistoryDao
import com.sielo.music.core.database.entity.RecommendationHistoryEntity
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.recommendations.autoplay.AutoplayQueueEngine
import com.sielo.music.core.recommendations.autoplay.CandidatePoolBuilder
import com.sielo.music.core.recommendations.autoplay.CandidatePools
import com.sielo.music.core.recommendations.repository.SimilarArtistsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRecommendationHistoryDao : RecommendationHistoryDao {
    val recorded = mutableListOf<RecommendationHistoryEntity>()
    var ineligibleIds = mutableSetOf<String>()

    override suspend fun countRecommendedToday(songId: String, today: String): Int {
        return if (recorded.any { it.songId == songId && it.lastRecommendedDate == today }) 1 else 0
    }

    override suspend fun getIneligibleSongIds(songIds: List<String>, today: String): List<String> {
        return songIds.filter { it in ineligibleIds || recorded.any { r -> r.songId == it && r.lastRecommendedDate == today } }
    }

    override suspend fun upsertAll(entities: List<RecommendationHistoryEntity>) {
        recorded.addAll(entities)
    }
}

class FakeCandidatePoolBuilder : CandidatePoolBuilder(
    listeningHistoryDao = object : com.sielo.music.core.database.dao.ListeningHistoryDao {
        override suspend fun insertEvent(event: com.sielo.music.core.database.entity.ListeningEventEntity) = 0L
        override fun getRecentHistory(limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.entity.ListeningEventEntity>>()
        override fun getTotalListeningTime(sinceMs: Long) = kotlinx.coroutines.flow.emptyFlow<Long?>()
        override fun getTotalUniqueSongs(sinceMs: Long) = kotlinx.coroutines.flow.emptyFlow<Int>()
        override fun getTotalUniqueArtists(sinceMs: Long) = kotlinx.coroutines.flow.emptyFlow<Int>()
        override fun getTopArtists(sinceMs: Long, limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.dao.ArtistStat>>()
        override fun getTopSongs(sinceMs: Long, limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.dao.SongStat>>()
        override suspend fun getPlayCountForMonth(month: Int, year: Int) = 0
        override suspend fun getListeningDurationForMonth(month: Int, year: Int) = 0L
        override fun getHourlyDistribution(sinceMs: Long) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.dao.HourCount>>()
        override fun getRediscoveredFavorites(twoDaysAgoMs: Long, limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.entity.ListeningEventEntity>>()
        override suspend fun updateDurationPlayed(eventId: Long, durationMs: Long) {}
        override suspend fun incrementDurationPlayed(eventId: Long, deltaMs: Long) {}
        override fun getStreamHistory(sinceMs: Long, limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.entity.ListeningEventEntity>>()
        override fun getTotalStreamCount(sinceMs: Long) = kotlinx.coroutines.flow.emptyFlow<Int>()
        override suspend fun sanitizeLegacyRecords() {}
        override fun getRecentUniquePlayedSongs(limit: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.sielo.music.core.database.entity.ListeningEventEntity>>()
        override suspend fun getSongsByArtists(artistNames: List<String>) = emptyList<com.sielo.music.core.database.dao.CandidateSongStat>()
        override suspend fun getSongsByPlayCountRange(sinceMs: Long, minPlays: Int, maxPlays: Int) = emptyList<com.sielo.music.core.database.dao.CandidateSongStat>()
        override suspend fun getTopArtistsSnapshot(sinceMs: Long, limit: Int) = emptyList<com.sielo.music.core.database.dao.ArtistStat>()
    },
    recommendationHistoryDao = FakeRecommendationHistoryDao(),
    similarArtistsRepository = FakeSimilarArtistsRepository(),
    lastFmApiService = object : com.sielo.music.core.recommendations.api.LastFmApiService {
        override suspend fun getSimilarArtists(artist: String, apiKey: String, limit: Int, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmSimilarArtistsResponse()
        override suspend fun getTopTags(artist: String, apiKey: String, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmTopTagsResponse()
        override suspend fun getTagTopArtists(tag: String, apiKey: String, limit: Int) = com.sielo.music.core.recommendations.model.LastFmTagTopArtistsResponse()
        override suspend fun getTopTracks(artist: String, apiKey: String, limit: Int, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmTopTracksResponse()
    },
    lastFmRateLimiter = com.sielo.music.core.recommendations.util.RateLimiter(100.0),
    innerTubeClient = com.sielo.music.core.network.innertube.InnerTubeClient()
) {
    var poolsToReturn = CandidatePools(emptyList(), emptyList(), emptyList())

    override suspend fun buildCandidatePools(seedSong: SieloTrack, today: String): CandidatePools {
        return poolsToReturn
    }
}

class FakeSimilarArtistsRepository : SimilarArtistsRepository(
    lastFmApiService = object : com.sielo.music.core.recommendations.api.LastFmApiService {
        override suspend fun getSimilarArtists(artist: String, apiKey: String, limit: Int, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmSimilarArtistsResponse()
        override suspend fun getTopTags(artist: String, apiKey: String, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmTopTagsResponse()
        override suspend fun getTagTopArtists(tag: String, apiKey: String, limit: Int) = com.sielo.music.core.recommendations.model.LastFmTagTopArtistsResponse()
        override suspend fun getTopTracks(artist: String, apiKey: String, limit: Int, autocorrect: Int) = com.sielo.music.core.recommendations.model.LastFmTopTracksResponse()
    },
    musicBrainzApiService = object : com.sielo.music.core.recommendations.api.MusicBrainzApiService {
        override suspend fun searchArtist(query: String, fmt: String, limit: Int) = com.sielo.music.core.recommendations.model.MusicBrainzSearchResponse()
    },
    similarArtistCacheDao = object : com.sielo.music.core.database.dao.SimilarArtistCacheDao {
        override suspend fun getCache(seedArtistId: String) = null
        override suspend fun insertCache(cache: com.sielo.music.core.database.entity.SimilarArtistCacheEntity) {}
        override suspend fun deleteCache(seedArtistId: String) {}
        override suspend fun deleteExpired(expiredThresholdMs: Long) {}
    },
    artistCountryCacheDao = object : com.sielo.music.core.database.dao.ArtistCountryCacheDao {
        override suspend fun getCountry(artistName: String) = null
        override suspend fun insertCountry(countryCache: com.sielo.music.core.database.entity.ArtistCountryCacheEntity) {}
        override suspend fun deleteCountry(artistName: String) {}
        override suspend fun deleteExpired(expiredThresholdMs: Long) {}
    },
    lastFmRateLimiter = com.sielo.music.core.recommendations.util.RateLimiter(100.0),
    musicBrainzRateLimiter = com.sielo.music.core.recommendations.util.RateLimiter(100.0),
    json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
) {
    val artistTagsMap = mutableMapOf<String, List<String>>()

    override suspend fun getArtistTags(artistName: String): List<String> {
        return artistTagsMap[artistName] ?: emptyList()
    }
}

class AutoplayQueueEngineTest {

    private val fakeDao = FakeRecommendationHistoryDao()
    private val fakePoolBuilder = FakeCandidatePoolBuilder()
    private val fakeSimilarRepo = FakeSimilarArtistsRepository()

    private val engine = AutoplayQueueEngine(
        candidatePoolBuilder = fakePoolBuilder,
        recommendationHistoryDao = fakeDao,
        similarArtistsRepository = fakeSimilarRepo
    )

    private fun createTrack(id: String, title: String, artist: String): SieloTrack {
        return SieloTrack(id = id, title = title, artist = artist)
    }

    @Test
    fun testArtistInterleavingDistanceConstraint() {
        val tracks = listOf(
            createTrack("1", "Song 1", "Artist A"),
            createTrack("2", "Song 2", "Artist A"),
            createTrack("3", "Song 3", "Artist B"),
            createTrack("4", "Song 4", "Artist B"),
            createTrack("5", "Song 5", "Artist C"),
            createTrack("6", "Song 6", "Artist D")
        )

        val interleaved = engine.interleaveWithArtistSpacing(tracks, minDistance = 3)
        assertEquals(6, interleaved.size)

        for (i in 0 until interleaved.size - 1) {
            val currentArtist = interleaved[i].artist
            val nextArtist = interleaved[i + 1].artist
            assertFalse(
                "Adjacent songs cannot have same artist: index $i and ${i + 1} both have $currentArtist",
                currentArtist.equals(nextArtist, ignoreCase = true)
            )
        }
    }

    @Test
    fun testHardExclusionOfPlayedSongs() = runBlocking {
        val seed = createTrack("seed1", "Blinding Lights", "The Weeknd")
        val played1 = createTrack("played1", "Starboy", "The Weeknd")
        val fresh1 = createTrack("fresh1", "Die For You", "The Weeknd")
        val disco1 = createTrack("disco1", "Levitating", "Dua Lipa")
        val mod1 = createTrack("mod1", "As It Was", "Harry Styles")

        fakePoolBuilder.poolsToReturn = CandidatePools(
            poolA = listOf(played1, fresh1),
            poolB = listOf(disco1),
            poolC = listOf(mod1)
        )

        val result = engine.generateBatch(
            seedSong = seed,
            excludedSongIds = setOf("played1"),
            reentryCandidates = emptyList(),
            batchSize = 10
        )

        assertFalse("Played song must be hard-excluded", result.any { it.id == "played1" })
        assertFalse("Seed song must be excluded", result.any { it.id == "seed1" })
        assertTrue("Fresh song should be included", result.any { it.id == "fresh1" })
    }

    @Test
    fun testReentryCandidatesTagOverlapFolding() = runBlocking {
        val seed = createTrack("seed1", "Blinding Lights", "The Weeknd")
        val qualifyingReentry = createTrack("reentry_qual", "Physical", "Dua Lipa")
        val nonQualifyingReentry = createTrack("reentry_non", "Country Road", "John Denver")

        fakePoolBuilder.poolsToReturn = CandidatePools(poolA = emptyList(), poolB = emptyList(), poolC = emptyList())

        fakeSimilarRepo.artistTagsMap["The Weeknd"] = listOf("pop", "synthpop", "rnb", "electronic")
        fakeSimilarRepo.artistTagsMap["Dua Lipa"] = listOf("pop", "synthpop", "electronic", "dance")
        fakeSimilarRepo.artistTagsMap["John Denver"] = listOf("country", "folk", "acoustic")

        val result = engine.generateBatch(
            seedSong = seed,
            excludedSongIds = emptySet(),
            reentryCandidates = listOf(qualifyingReentry, nonQualifyingReentry),
            batchSize = 5
        )

        assertTrue("Qualifying re-entry candidate must be folded into batch", result.any { it.id == "reentry_qual" })
        assertFalse("Non-qualifying re-entry candidate must not be included", result.any { it.id == "reentry_non" })
    }

    @Test
    fun testPoolAllocationProportions() = runBlocking {
        val seed = createTrack("seed1", "Seed", "Artist S")
        val poolA = (1..20).map { createTrack("a$it", "Song A$it", "Artist A$it") }
        val poolB = (1..20).map { createTrack("b$it", "Song B$it", "Artist B$it") }
        val poolC = (1..20).map { createTrack("c$it", "Song C$it", "Artist C$it") }

        fakePoolBuilder.poolsToReturn = CandidatePools(poolA = poolA, poolB = poolB, poolC = poolC)

        val result = engine.generateBatch(
            seedSong = seed,
            excludedSongIds = emptySet(),
            reentryCandidates = emptyList(),
            batchSize = 20
        )

        assertEquals(20, result.size)
        // 60% of 20 = 12 from Pool A
        val countA = result.count { it.id.startsWith("a") }
        // 25% of 20 = 5 from Pool B
        val countB = result.count { it.id.startsWith("b") }
        // 15% of 20 = 3 from Pool C
        val countC = result.count { it.id.startsWith("c") }

        assertEquals(12, countA)
        assertEquals(5, countB)
        assertEquals(3, countC)

        // Verify marked in recommendation history
        assertEquals(20, fakeDao.recorded.size)
    }
}
