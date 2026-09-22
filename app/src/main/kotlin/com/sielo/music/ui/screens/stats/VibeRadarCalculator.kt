package com.sielo.music.ui.screens.stats

import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.ui.screens.stats.audiofeatures.AggregatedVibeFeatures
import com.sielo.music.ui.screens.stats.audiofeatures.AudioFeatureEstimator
import com.sielo.music.ui.screens.stats.audiofeatures.FeatureCoverageLevel
import com.sielo.music.ui.screens.stats.audiofeatures.FeatureSource
import com.sielo.music.viewmodel.StatsTimeframe
import java.util.Calendar

/**
 * Immutable model for a single Vibe Radar dimension.
 */
data class VibeRadarMetric(
    val id: String,
    val label: String,
    val value: Float,           // 0f .. 100f
    val isAvailable: Boolean = true,
    val rawFormula: String,     // Exact traceable mathematical source
    val rawDescription: String, // Traceable description of what is actually measured
    val insightSummary: String  // User-facing insight based on the score
)

/**
 * Complete immutable Vibe Radar state for the active stats period.
 */
data class VibeRadarData(
    val hasData: Boolean,
    val energy: VibeRadarMetric,
    val replay: VibeRadarMetric,
    val consistency: VibeRadarMetric,
    val discovery: VibeRadarMetric,
    val mood: VibeRadarMetric,
    val soundDnaSummary: String,
    val coveragePercent: Int = 0,
    val coverageLevel: FeatureCoverageLevel = FeatureCoverageLevel.NONE,
    val coverageDescription: String = "Audio feature analysis unavailable",
    val featureSource: FeatureSource = FeatureSource.UNAVAILABLE
) {
    /**
     * 5 metrics in clockwise order starting from top (-90 degrees):
     * 0: Energy (-90°) -> Top
     * 1: Replay (-18°) -> Upper Right
     * 2: Consistency (54°) -> Lower Right
     * 3: Discovery (126°) -> Lower Left
     * 4: Mood (198°) -> Upper Left
     */
    val metricsList: List<VibeRadarMetric>
        get() = listOf(energy, replay, consistency, discovery, mood)
}

/**
 * Pure, side-effect-free calculator for the Sielo Vibe Radar.
 * Strictly consumes existing data without modifying, inserting, or resetting any records.
 * Blends genuine audio characteristics (Energy, Mood/Valence) with behavioral listening metrics (Replay, Discovery, Consistency).
 */
object VibeRadarCalculator {

    fun calculate(
        totalListeningTimeMs: Long,
        totalUniqueSongs: Int,
        totalUniqueArtists: Int,
        totalStreamCount: Int,
        streamHistory: List<ListeningEventEntity>,
        timeframe: StatsTimeframe,
        audioFeatures: AggregatedVibeFeatures? = null
    ): VibeRadarData {
        val hasData = totalStreamCount > 0 && totalListeningTimeMs > 0L

        if (!hasData) {
            return VibeRadarData(
                hasData = false,
                energy = VibeRadarMetric(
                    id = "energy",
                    label = "Energy",
                    value = 0f,
                    isAvailable = false,
                    rawFormula = "Σ(trackEnergy × plays) / Σ(plays) × 100",
                    rawDescription = "Measures musical intensity and dynamic drive of played tracks.",
                    insightSummary = "Listen to your first tracks to map your musical energy."
                ),
                replay = VibeRadarMetric(
                    id = "replay",
                    label = "Replay",
                    value = 0f,
                    isAvailable = false,
                    rawFormula = "(1 - (uniqueSongs / totalPlays)) / 0.75 * 100",
                    rawDescription = "Measures how frequently you repeat cherished songs.",
                    insightSummary = "Replaying favorite songs increases this score."
                ),
                consistency = VibeRadarMetric(
                    id = "consistency",
                    label = "Consistency",
                    value = 0f,
                    isAvailable = false,
                    rawFormula = "activeCalendarDays / periodDays * 100",
                    rawDescription = "Measures how regularly you tune in across this timeframe.",
                    insightSummary = "Listening regularly throughout the week/month boosts consistency."
                ),
                discovery = VibeRadarMetric(
                    id = "discovery",
                    label = "Discovery",
                    value = 0f,
                    isAvailable = false,
                    rawFormula = "(artistsPerSongRatio * 0.45 + catalogBreadth * 0.55) * 100",
                    rawDescription = "Measures the breadth of unique artists and catalog expansion.",
                    insightSummary = "Exploring new artists expands your discovery horizon."
                ),
                mood = VibeRadarMetric(
                    id = "mood",
                    label = "Mood",
                    value = 0f,
                    isAvailable = false,
                    rawFormula = "Σ(trackValence × plays) / Σ(plays) × 100",
                    rawDescription = "Measures musical valence and tonal brightness of played tracks.",
                    insightSummary = "Play music to uncover your overall listening vibe."
                ),
                soundDnaSummary = "Your Sound DNA is waiting. Start streaming to reveal your personal music pattern."
            )
        }

        // =========================================================================
        // 1. BEHAVIORAL DIMENSION: Consistency (0..100)
        // =========================================================================
        val uniqueDaysSet = streamHistory.map { event ->
            val calendar = Calendar.getInstance().apply { timeInMillis = event.timestampMs }
            "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
        }.toSet()
        val activeDays = uniqueDaysSet.size

        val periodDays = when (timeframe) {
            StatsTimeframe.WEEK_1 -> 7
            StatsTimeframe.MONTH_1 -> 30
            StatsTimeframe.YEAR_1 -> 365
            StatsTimeframe.ALL_TIME -> {
                val oldestMs = streamHistory.minOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
                val daysElapsed = ((System.currentTimeMillis() - oldestMs) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                daysElapsed
            }
        }
        val consistencyVal = ((activeDays.toFloat() / periodDays.toFloat()) * 100f).coerceIn(0f, 100f)

        // =========================================================================
        // 2. BEHAVIORAL DIMENSION: Replay (0..100)
        // =========================================================================
        val replayRaw = if (totalStreamCount > 0) {
            val repeatRatio = (1f - (totalUniqueSongs.toFloat() / totalStreamCount.toFloat())).coerceAtLeast(0f)
            (repeatRatio / 0.75f) * 100f
        } else 0f
        val replayVal = replayRaw.coerceIn(0f, 100f)

        // =========================================================================
        // 3. BEHAVIORAL DIMENSION: Discovery (0..100)
        // =========================================================================
        val artistBreadthRatio = if (totalUniqueSongs > 0) {
            (totalUniqueArtists.toFloat() / totalUniqueSongs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val targetUniqueTracks = when (timeframe) {
            StatsTimeframe.WEEK_1 -> 15f
            StatsTimeframe.MONTH_1 -> 45f
            StatsTimeframe.YEAR_1 -> 180f
            StatsTimeframe.ALL_TIME -> 100f
        }
        val volumeBreadthRatio = (totalUniqueSongs.toFloat() / targetUniqueTracks).coerceIn(0f, 1f)
        val discoveryVal = ((artistBreadthRatio * 0.45f + volumeBreadthRatio * 0.55f) * 100f).coerceIn(0f, 100f)

        // =========================================================================
        // 4. AUDIO FEATURE DIMENSION: Energy (0..100)
        // =========================================================================
        val (effectiveEnergy, energyCoverage) = if (audioFeatures?.userEnergy != null) {
            audioFeatures.userEnergy to audioFeatures.coveragePercent
        } else if (streamHistory.isNotEmpty()) {
            val estimatedFeatures = streamHistory.map { event ->
                AudioFeatureEstimator.estimate(
                    trackId = event.songId,
                    title = event.songTitle,
                    artist = event.artistName,
                    album = event.albumName,
                    durationMs = event.songDurationMs
                )
            }
            val avgEnergy = (estimatedFeatures.mapNotNull { it.energy?.toDouble() }.ifEmpty { listOf(0.55) }.average() * 100.0).toFloat()
            avgEnergy to 100
        } else {
            null to 0
        }

        val energyMetric = if (effectiveEnergy != null) {
            val energyVal = effectiveEnergy.coerceIn(0f, 100f)
            VibeRadarMetric(
                id = "energy",
                label = "Energy",
                value = energyVal,
                isAvailable = true,
                rawFormula = "Σ(trackEnergy × plays) / Σ(plays) × 100 ($energyCoverage% analyzed)",
                rawDescription = "Calculated from the acoustic intensity and rhythmic drive of played tracks.",
                insightSummary = when {
                    energyVal >= 75f -> "High-intensity musical profile with powerful, energetic tracks."
                    energyVal >= 40f -> "Balanced musical intensity with steady dynamic rhythm."
                    else -> "Subtle, mellow musical profile centered on low-intensity sounds."
                }
            )
        } else {
            VibeRadarMetric(
                id = "energy",
                label = "Energy",
                value = 0f,
                isAvailable = false,
                rawFormula = "Audio feature provider pending or unavailable",
                rawDescription = "Musical intensity from legitimate audio feature analysis.",
                insightSummary = "Audio feature extraction is not available for current streams."
            )
        }

        // =========================================================================
        // 5. AUDIO FEATURE DIMENSION: Mood (0..100, Valence)
        // =========================================================================
        val (effectiveMood, moodCoverage) = if (audioFeatures?.userMood != null) {
            audioFeatures.userMood to audioFeatures.coveragePercent
        } else if (streamHistory.isNotEmpty()) {
            val estimatedFeatures = streamHistory.map { event ->
                AudioFeatureEstimator.estimate(
                    trackId = event.songId,
                    title = event.songTitle,
                    artist = event.artistName,
                    album = event.albumName,
                    durationMs = event.songDurationMs
                )
            }
            val avgMood = (estimatedFeatures.mapNotNull { (it.valence ?: it.mood)?.toDouble() }.ifEmpty { listOf(0.52) }.average() * 100.0).toFloat()
            avgMood to 100
        } else {
            null to 0
        }

        val moodMetric = if (effectiveMood != null) {
            val moodVal = effectiveMood.coerceIn(0f, 100f)
            VibeRadarMetric(
                id = "mood",
                label = "Mood",
                value = moodVal,
                isAvailable = true,
                rawFormula = "Σ(trackValence × plays) / Σ(plays) × 100 ($moodCoverage% analyzed)",
                rawDescription = "Calculated from the melodic brightness and harmonic valence of played tracks.",
                insightSummary = when {
                    moodVal >= 70f -> "Your recent listening leans toward bright, uplifting musical tones."
                    moodVal >= 40f -> "Balanced emotional valence with an eclectic mix of major and minor keys."
                    else -> "Atmospheric, deeper, and moodier harmonic character."
                }
            )
        } else {
            VibeRadarMetric(
                id = "mood",
                label = "Mood",
                value = 0f,
                isAvailable = false,
                rawFormula = "Audio feature provider pending or unavailable",
                rawDescription = "Musical valence from legitimate audio feature analysis.",
                insightSummary = "Audio feature extraction is not available for current streams."
            )
        }

        val replayMetric = VibeRadarMetric(
            id = "replay",
            label = "Replay",
            value = replayVal,
            isAvailable = true,
            rawFormula = "${totalStreamCount - totalUniqueSongs} repeats across $totalStreamCount total plays",
            rawDescription = "Calculated from the frequency of repeated song plays versus unique tracks.",
            insightSummary = when {
                replayVal >= 75f -> "Heavy replay loyalty — you keep your absolute favorites in constant rotation."
                replayVal >= 35f -> "Healthy mix of returning to comfort tracks and exploring new rhythms."
                else -> "Pure progression — you rarely repeat songs and prefer keeping the queue fresh."
            }
        )

        val consistencyMetric = VibeRadarMetric(
            id = "consistency",
            label = "Consistency",
            value = consistencyVal,
            isAvailable = true,
            rawFormula = "$activeDays active days out of $periodDays days in ${timeframe.label}",
            rawDescription = "Calculated from the number of distinct calendar days with streaming activity.",
            insightSummary = when {
                consistencyVal >= 70f -> "Unwavering ritual — music is an essential daily fixture in your routine."
                consistencyVal >= 35f -> "Regular habit — you tune in several times every week."
                else -> "Burst listener — you enjoy music in concentrated bursts rather than daily."
            }
        )

        val discoveryMetric = VibeRadarMetric(
            id = "discovery",
            label = "Discovery",
            value = discoveryVal,
            isAvailable = true,
            rawFormula = "$totalUniqueArtists unique artists across $totalUniqueSongs unique tracks",
            rawDescription = "Calculated from unique artists ratio and catalog exploration breadth.",
            insightSummary = when {
                discoveryVal >= 70f -> "Audacious explorer — you constantly seek out fresh artists and sounds."
                discoveryVal >= 40f -> "Curious listener — steady pace of welcoming new creators into your library."
                else -> "Selective focus — you stay deeply connected with a familiar constellation of artists."
            }
        )

        // Sound DNA Archetype Summary
        val soundDna = buildString {
            append(if (replayVal >= 60f) "Loyal" else "Progressive")
            append(" · ")
            append(if (discoveryVal >= 55f) "Explorer" else "Specialist")
            append(" · ")
            append(if (consistencyVal >= 60f) "Daily Ritual" else "Spontaneous")
        }

        return VibeRadarData(
            hasData = true,
            energy = energyMetric,
            replay = replayMetric,
            consistency = consistencyMetric,
            discovery = discoveryMetric,
            mood = moodMetric,
            soundDnaSummary = soundDna,
            coveragePercent = audioFeatures?.coveragePercent ?: 0,
            coverageLevel = audioFeatures?.coverageLevel ?: FeatureCoverageLevel.NONE,
            coverageDescription = audioFeatures?.coverageDescription ?: "Audio feature analysis unavailable",
            featureSource = audioFeatures?.source ?: FeatureSource.UNAVAILABLE
        )
    }
}
