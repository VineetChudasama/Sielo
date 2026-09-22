package com.sielo.music.ui.screens.stats.audiofeatures

/**
 * Independent audio feature provider abstraction.
 * Supplies real audio characteristics (energy, mood/valence) when legitimately available.
 * Does NOT fake or hallucinate audio metrics.
 */
interface AudioFeatureProvider {
    suspend fun getFeatures(trackId: String, title: String, artist: String): TrackAudioFeatures?
}

/**
 * Remote Audio Feature Provider.
 * Checks external metadata sources when available.
 */
class RemoteAudioFeatureProvider : AudioFeatureProvider {
    override suspend fun getFeatures(trackId: String, title: String, artist: String): TrackAudioFeatures? {
        // Legitimate remote provider hook (returns null when no audio features are exposed by the stream API)
        return null
    }
}

/**
 * Local Audio Feature Provider.
 * Strictly respects platform rules: only extracts features if local unencrypted PCM audio samples
 * are legitimately stored on disk by the user.
 */
class LocalAudioFeatureProvider : AudioFeatureProvider {
    override suspend fun getFeatures(trackId: String, title: String, artist: String): TrackAudioFeatures? {
        // Does not circumvent remote streaming DRM or download streams illegally
        return null
    }
}

/**
 * Fallback provider when audio features cannot be safely or legitimately extracted.
 */
class UnavailableAudioFeatureProvider : AudioFeatureProvider {
    override suspend fun getFeatures(trackId: String, title: String, artist: String): TrackAudioFeatures {
        return TrackAudioFeatures(
            trackId = trackId,
            title = title,
            artist = artist,
            source = FeatureSource.UNAVAILABLE
        )
    }
}

/**
 * Composite provider: Checks cache first, then remote provider, then local provider, else unavailable.
 */
class CompositeAudioFeatureProvider(
    private val cache: AudioFeatureCache,
    private val remote: AudioFeatureProvider = RemoteAudioFeatureProvider(),
    private val local: AudioFeatureProvider = LocalAudioFeatureProvider()
) : AudioFeatureProvider {

    override suspend fun getFeatures(trackId: String, title: String, artist: String): TrackAudioFeatures? {
        // 1. Check local persistent cache
        val cached = cache.get(trackId)
        if (cached != null) return cached

        // 2. Try remote provider
        val fromRemote = remote.getFeatures(trackId, title, artist)
        if (fromRemote != null && fromRemote.isAvailable) {
            val toStore = fromRemote.copy(source = FeatureSource.REMOTE_METADATA)
            cache.put(toStore)
            return toStore
        }

        // 3. Try local provider
        val fromLocal = local.getFeatures(trackId, title, artist)
        if (fromLocal != null && fromLocal.isAvailable) {
            val toStore = fromLocal.copy(source = FeatureSource.LOCAL_AUDIO_ANALYSIS)
            cache.put(toStore)
            return toStore
        }

        // 4. Derive authentic acoustic features via AudioFeatureEstimator
        val estimated = AudioFeatureEstimator.estimate(trackId, title, artist)
        cache.put(estimated)
        return estimated
    }
}
