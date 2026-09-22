package com.sielo.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sielo.music.R
import kotlin.math.roundToInt
import com.sielo.music.core.database.dao.ArtistStat
import com.sielo.music.core.database.dao.HourCount
import com.sielo.music.core.database.dao.SongStat
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.ui.components.SieloArtistPhoto
import com.sielo.music.ui.components.SieloSongArtwork
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderHighlight
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.ui.screens.stats.VibeRadarCalculator
import com.sielo.music.ui.screens.stats.VibeRadarSection
import com.sielo.music.viewmodel.StatsTimeframe
import com.sielo.music.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class StatsSheetType {
    TRACKS,
    ARTISTS,
    PLAYS
}

enum class CircadianPeriod(
    val title: String,
    val timeLabel: String,
    val icon: ImageVector,
    val hourRange: IntRange
) {
    MORNING("Morning", "6 AM – 12 PM", Icons.Default.WbTwilight, 6..11),
    AFTERNOON("Afternoon", "12 PM – 5 PM", Icons.Default.WbSunny, 12..16),
    EVENING("Evening", "5 PM – 10 PM", Icons.Default.BrightnessMedium, 17..21),
    NIGHT("Night", "10 PM – 6 AM", Icons.Default.Nightlight, 22..23)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val totalTimeMs by viewModel.totalListeningTime.collectAsState()
    val uniqueSongs by viewModel.totalUniqueSongs.collectAsState()
    val uniqueArtists by viewModel.totalUniqueArtists.collectAsState()
    val totalStreams by viewModel.totalStreamCount.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val allArtists by viewModel.allArtists.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val streamHistory by viewModel.streamHistory.collectAsState()
    val hourlyDistribution by viewModel.hourlyDistribution.collectAsState()

    var isTimeframeDropdownExpanded by remember { mutableStateOf(false) }
    var activeDetailSheet by remember { mutableStateOf<StatsSheetType?>(null) }
    var selectedCircadianPeriod by remember { mutableStateOf(CircadianPeriod.MORNING) }

    val persona = remember(hourlyDistribution) { viewModel.computeListenerPersona(hourlyDistribution) }
    val peakHours = remember(hourlyDistribution) { viewModel.computePeakHourSlot(hourlyDistribution) }

    val totalMinutes = (totalTimeMs ?: 0L) / (1000 * 60)
    val totalHours = totalMinutes / 60
    val remainingMinutes = totalMinutes % 60
    val formattedDuration = if (totalHours > 0) "${totalHours}h ${remainingMinutes}m" else "${remainingMinutes}m"

    val timeframeLabel = when (selectedTimeframe) {
        StatsTimeframe.WEEK_1 -> "This Week"
        StatsTimeframe.MONTH_1 -> "This Month"
        StatsTimeframe.YEAR_1 -> "This Year"
        StatsTimeframe.ALL_TIME -> "All Time"
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioFeatureCache = remember { com.sielo.music.ui.screens.stats.audiofeatures.AudioFeatureCache(context) }
    var cachedFeaturesMap by remember { mutableStateOf<Map<String, com.sielo.music.ui.screens.stats.audiofeatures.TrackAudioFeatures>>(emptyMap()) }

    androidx.compose.runtime.LaunchedEffect(streamHistory) {
        val loaded = mutableMapOf<String, com.sielo.music.ui.screens.stats.audiofeatures.TrackAudioFeatures>()
        val toSave = mutableListOf<com.sielo.music.ui.screens.stats.audiofeatures.TrackAudioFeatures>()
        streamHistory.forEach { event ->
            val cached = audioFeatureCache.get(event.songId)
            if (cached != null) {
                loaded[event.songId] = cached
            } else {
                val estimated = com.sielo.music.ui.screens.stats.audiofeatures.AudioFeatureEstimator.estimate(
                    trackId = event.songId,
                    title = event.songTitle,
                    artist = event.artistName,
                    album = event.albumName,
                    durationMs = event.songDurationMs
                )
                toSave.add(estimated)
                loaded[event.songId] = estimated
            }
        }
        if (toSave.isNotEmpty()) {
            audioFeatureCache.putAll(toSave)
        }
        cachedFeaturesMap = loaded
    }

    val audioFeatures = remember(streamHistory, cachedFeaturesMap) {
        com.sielo.music.ui.screens.stats.audiofeatures.VibeAggregator.aggregate(streamHistory) { songId ->
            cachedFeaturesMap[songId]
        }
    }

    // Pure real-data Vibe Radar calculation for active timeframe
    val vibeRadarData = remember(totalTimeMs, uniqueSongs, uniqueArtists, totalStreams, streamHistory, selectedTimeframe, audioFeatures) {
        VibeRadarCalculator.calculate(
            totalListeningTimeMs = totalTimeMs ?: 0L,
            totalUniqueSongs = uniqueSongs,
            totalUniqueArtists = uniqueArtists,
            totalStreamCount = totalStreams,
            streamHistory = streamHistory,
            timeframe = selectedTimeframe,
            audioFeatures = audioFeatures
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        // Ambient Cosmic Starry Glow Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B2A4A).copy(alpha = 0.45f),
                        Color(0xFF0F1826).copy(alpha = 0.7f),
                        PaletteDarkNavy
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.width * 0.95f
                )
            )
            val starPositions = listOf(
                Offset(size.width * 0.12f, size.height * 0.08f) to 1.8f,
                Offset(size.width * 0.88f, size.height * 0.11f) to 2.2f,
                Offset(size.width * 0.25f, size.height * 0.28f) to 1.4f,
                Offset(size.width * 0.78f, size.height * 0.32f) to 1.6f,
                Offset(size.width * 0.94f, size.height * 0.26f) to 2.0f,
                Offset(size.width * 0.08f, size.height * 0.45f) to 1.5f,
                Offset(size.width * 0.92f, size.height * 0.65f) to 2.4f,
                Offset(size.width * 0.15f, size.height * 0.78f) to 1.6f
            )
            starPositions.forEach { (pos, r) ->
                drawCircle(
                    color = PaletteSand.copy(alpha = 0.55f),
                    radius = r,
                    center = pos
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp)
        ) {
            // 1. TOP HEADER SECTION WITH TIMEFRAME DROPDOWN
            StatsHeaderSection(
                selectedTimeframe = selectedTimeframe,
                isDropdownExpanded = isTimeframeDropdownExpanded,
                onToggleDropdown = { isTimeframeDropdownExpanded = it },
                onSelectTimeframe = {
                    viewModel.selectTimeframe(it)
                    isTimeframeDropdownExpanded = false
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 2. HERO — PERSONAL MUSIC UNIVERSE
                item {
                    MusicUniverseHero(
                        formattedDuration = formattedDuration,
                        timeframeLabel = timeframeLabel,
                        uniqueSongs = uniqueSongs,
                        uniqueArtists = uniqueArtists,
                        totalStreams = totalStreams,
                        hasData = totalMinutes > 0 || totalStreams > 0,
                        onOpenTracks = { activeDetailSheet = StatsSheetType.TRACKS },
                        onOpenArtists = { activeDetailSheet = StatsSheetType.ARTISTS },
                        onOpenPlays = { activeDetailSheet = StatsSheetType.PLAYS }
                    )
                }

                // 3. VIBE RADAR — PERSONAL SOUND DNA (Data-Driven 5-Axis Radar)
                item {
                    VibeRadarSection(
                        vibeRadarData = vibeRadarData,
                        timeframe = selectedTimeframe
                    )
                }

                // 4. "WHEN YOU LISTEN" CIRCADIAN SECTION
                item {
                    WhenYouListenSection(
                        hourlyDistribution = hourlyDistribution,
                        selectedPeriod = selectedCircadianPeriod,
                        onSelectPeriod = { selectedCircadianPeriod = it }
                    )
                }

                // 5. DUAL SPOTLIGHTS ROW (Top Artists & Top Tracks)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TopArtistsCard(
                            artists = topArtists,
                            modifier = Modifier.weight(1f),
                            onClick = { activeDetailSheet = StatsSheetType.ARTISTS }
                        )

                        TopTracksCard(
                            songs = topSongs,
                            modifier = Modifier.weight(1f),
                            onClick = { activeDetailSheet = StatsSheetType.TRACKS }
                        )
                    }
                }
            }
        }

        // Functional Detail Bottom Sheet (For Tracks, Artists, Plays)
        activeDetailSheet?.let { sheetType ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { activeDetailSheet = null },
                sheetState = sheetState,
                containerColor = PaletteOxfordBlue,
                contentColor = PaletteCream,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(PaletteSand.copy(alpha = 0.5f))
                    )
                }
            ) {
                StatsDetailSheetContent(
                    sheetType = sheetType,
                    allSongs = allSongs,
                    allArtists = allArtists,
                    streamHistory = streamHistory,
                    onPlaySong = { viewModel.playSongStat(it) },
                    onPlayEvent = { viewModel.playHistoryEvent(it) },
                    onClose = { activeDetailSheet = null }
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   1. TOP HEADER SECTION
   ------------------------------------------------------------------------------------------------- */

@Composable
private fun StatsHeaderSection(
    selectedTimeframe: StatsTimeframe,
    isDropdownExpanded: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    onSelectTimeframe: (StatsTimeframe) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "YOUR MUSIC UNIVERSE",
                color = PaletteSand.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = UrbanistFontFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Listening ",
                    color = PaletteCream,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily
                )
                Text(
                    text = "Stats",
                    color = PaletteSand,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily
                )
            }
            Text(
                text = "More than numbers, it's a part of you.",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = UrbanistFontFamily
            )
        }

        // Dropdown Timeframe Selector Pill
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                    .clickable { onToggleDropdown(true) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedTimeframe.label,
                    color = PaletteCream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = UrbanistFontFamily
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Timeframe",
                    tint = PaletteSand,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { onToggleDropdown(false) },
                modifier = Modifier
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            ) {
                StatsTimeframe.entries.forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tf.label,
                                color = if (isSelected) PaletteSand else PaletteCream,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                fontFamily = UrbanistFontFamily
                            )
                        },
                        onClick = { onSelectTimeframe(tf) }
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   2. HERO — PERSONAL MUSIC UNIVERSE
   ------------------------------------------------------------------------------------------------- */

@Composable
private fun MusicUniverseHero(
    formattedDuration: String,
    timeframeLabel: String,
    uniqueSongs: Int,
    uniqueArtists: Int,
    totalStreams: Int,
    hasData: Boolean,
    onOpenTracks: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlays: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "UniverseOrbit")

    // Slow ambient breathing glow
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    // Continuous 360-degree orbital revolution along the elliptical path
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitRevolution"
    )

    val globeBitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = R.drawable.img_stats_globe)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Celestial Sphere & Continuous Revolving Orbital Rings Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width * 0.5f, size.height * 0.48f)
            val sphereRadius = size.width * 0.23f
            val sphereDiameter = sphereRadius * 2f

            // 1. Ambient Golden Halo Behind Sphere
            val haloRadius = sphereRadius * (if (hasData) 1.6f else 1.4f) * pulseGlow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PaletteSand.copy(alpha = if (hasData) 0.35f else 0.20f),
                        PaletteSand.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = centerOffset
            )

            // 2. Tilted Elliptical Orbital Paths (-22 degrees tilt)
            val orbitTilt = -22f
            val ringRadiusX = sphereRadius * 1.85f
            val ringRadiusY = sphereRadius * 0.65f

            // Draw full base orbital rings behind the sphere
            rotate(degrees = orbitTilt, pivot = centerOffset) {
                // Outer dashed ring
                drawOval(
                    color = PaletteSand.copy(alpha = 0.32f),
                    topLeft = Offset(centerOffset.x - ringRadiusX, centerOffset.y - ringRadiusY),
                    size = Size(ringRadiusX * 2f, ringRadiusY * 2f),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
                )
                // Inner solid ring
                drawOval(
                    color = PaletteCream.copy(alpha = 0.40f),
                    topLeft = Offset(centerOffset.x - ringRadiusX * 0.82f, centerOffset.y - ringRadiusY * 0.82f),
                    size = Size(ringRadiusX * 1.64f, ringRadiusY * 1.64f),
                    style = Stroke(width = 1.2f)
                )
            }

            // 3. 3D Orbital Pearls Depth Separation
            // The 4 pearls revolve along the ellipse.
            // Pearls on the back arc (sin(angleRad) < 0) are drawn BEFORE the globe photo.
            // This guarantees the globe naturally occludes any pearl behind it with ZERO overlap!
            // Pearls on the front arc (sin(angleRad) >= 0) are drawn AFTER the globe photo.

            val tiltRad = orbitTilt * (Math.PI / 180.0)
            val cosTilt = cos(tiltRad)
            val sinTilt = sin(tiltRad)
            val pearlPhases = listOf(0.0, 90.0, 180.0, 270.0)

            data class PearlData(
                val position: Offset,
                val isBehind: Boolean
            )

            val pearls = pearlPhases.map { phaseDeg ->
                val angleDeg = (orbitProgress + phaseDeg) % 360.0
                val angleRad = angleDeg * (Math.PI / 180.0)

                val ex = ringRadiusX * cos(angleRad)
                val ey = ringRadiusY * sin(angleRad)

                val rotX = ex * cosTilt - ey * sinTilt
                val rotY = ex * sinTilt + ey * cosTilt

                val pearlPos = Offset((centerOffset.x + rotX).toFloat(), (centerOffset.y + rotY).toFloat())
                val isBehind = sin(angleRad) < 0.0

                PearlData(pearlPos, isBehind)
            }

            // Draw BACK pearls (rendered behind the center globe)
            pearls.filter { it.isBehind }.forEach { pearl ->
                drawCircle(
                    color = PaletteSand.copy(alpha = 0.25f),
                    radius = 7.0f,
                    center = pearl.position
                )
                drawCircle(
                    color = PaletteSand.copy(alpha = 0.70f),
                    radius = 3.5f,
                    center = pearl.position
                )
            }

            // 4. Center Globe Image (Image 3)
            // Rendered right in the center — naturally covers any pearl passing behind it!
            val dstTopLeft = IntOffset(
                (centerOffset.x - sphereRadius).roundToInt(),
                (centerOffset.y - sphereRadius).roundToInt()
            )
            val dstSize = IntSize(
                sphereDiameter.roundToInt(),
                sphereDiameter.roundToInt()
            )
            drawImage(
                image = globeBitmap,
                dstOffset = dstTopLeft,
                dstSize = dstSize
            )

            // Front arc of orbital rings (drawn over the bottom half of the globe for depth realism)
            rotate(degrees = orbitTilt, pivot = centerOffset) {
                drawArc(
                    color = PaletteCream.copy(alpha = 0.50f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - ringRadiusX * 0.82f, centerOffset.y - ringRadiusY * 0.82f),
                    size = Size(ringRadiusX * 1.64f, ringRadiusY * 1.64f),
                    style = Stroke(width = 1.4f)
                )
            }

            // Draw FRONT pearls (rendered in front of the center globe)
            pearls.filter { !it.isBehind }.forEach { pearl ->
                drawCircle(
                    color = PaletteSand.copy(alpha = 0.45f),
                    radius = 9.0f,
                    center = pearl.position
                )
                drawCircle(
                    color = PaletteSand,
                    radius = 4.5f,
                    center = pearl.position
                )
            }
        }

        // 4. STATS METRICS SURROUNDING THE CELESTIAL ORB

        // Top-Left: Total Listening Time
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = formattedDuration,
                color = PaletteCream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = UrbanistFontFamily
            )
            Text(
                text = timeframeLabel,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = UrbanistFontFamily
            )
        }

        // Top-Right: Tracks Count (Interactive)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenTracks() },
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uniqueSongs.toString(),
                    color = PaletteCream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View Tracks",
                    tint = PaletteSand.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = "Tracks",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = UrbanistFontFamily
            )
        }

        // Bottom-Left: Artists Count (Interactive)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenArtists() },
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uniqueArtists.toString(),
                    color = PaletteCream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View Artists",
                    tint = PaletteSand.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = "Artists",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = UrbanistFontFamily
            )
        }

        // Bottom-Right: Plays Count (Interactive)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenPlays() },
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = totalStreams.toString(),
                    color = PaletteCream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View Plays",
                    tint = PaletteSand.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = "Plays",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = UrbanistFontFamily
            )
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   3. "WHEN YOU LISTEN" CIRCADIAN SECTION (REAL NUMBERS)
   ------------------------------------------------------------------------------------------------- */

@Composable
private fun WhenYouListenSection(
    hourlyDistribution: List<HourCount>,
    selectedPeriod: CircadianPeriod,
    onSelectPeriod: (CircadianPeriod) -> Unit
) {
    var morningDurationMs = 0L
    var afternoonDurationMs = 0L
    var eveningDurationMs = 0L
    var nightDurationMs = 0L

    if (hourlyDistribution.isNotEmpty()) {
        hourlyDistribution.forEach { item ->
            val dur = if (item.totalDurationMs > 0) item.totalDurationMs else item.count * 60000L
            when (item.hourOfDay) {
                in 6..11 -> morningDurationMs += dur
                in 12..16 -> afternoonDurationMs += dur
                in 17..21 -> eveningDurationMs += dur
                else -> nightDurationMs += dur
            }
        }
    }

    val selectedDurationMs = when (selectedPeriod) {
        CircadianPeriod.MORNING -> morningDurationMs
        CircadianPeriod.AFTERNOON -> afternoonDurationMs
        CircadianPeriod.EVENING -> eveningDurationMs
        CircadianPeriod.NIGHT -> nightDurationMs
    }

    val totalDurationMs = morningDurationMs + afternoonDurationMs + eveningDurationMs + nightDurationMs
    val selectedPercentage = if (totalDurationMs > 0L) ((selectedDurationMs.toFloat() / totalDurationMs) * 100).toInt() else 0
    val selectedMinutes = selectedDurationMs / (1000 * 60)
    val selectedHours = selectedMinutes / 60
    val selectedRemMinutes = selectedMinutes % 60
    val selectedDurationLabel = if (selectedHours > 0) "${selectedHours}h ${selectedRemMinutes}m" else "${selectedRemMinutes}m"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header with Crescent & 4 Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PaletteSand.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = null,
                            tint = PaletteSand,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = "When You Listen",
                            color = PaletteCream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = UrbanistFontFamily
                        )
                        Text(
                            text = "Your musical day in a new light.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = UrbanistFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 4 Interactive Filter Chips
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F1626))
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircadianPeriod.entries.forEach { period ->
                        val isSelected = period == selectedPeriod
                        val bgAnim by animateColorAsState(
                            targetValue = if (isSelected) PaletteSand else Color.Transparent,
                            label = "ChipBg"
                        )
                        val iconTintAnim by animateColorAsState(
                            targetValue = if (isSelected) PaletteDarkNavy else TextSecondary,
                            label = "ChipTint"
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgAnim)
                                .clickable { onSelectPeriod(period) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = period.icon,
                                contentDescription = period.title,
                                tint = iconTintAnim,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Circadian Wave Graph Representing Real Numbers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val baseLineY = h * 0.82f

                    val maxWaveH = h * 0.62f
                    val maxPeriodDur = maxOf(morningDurationMs, afternoonDurationMs, eveningDurationMs, nightDurationMs).coerceAtLeast(1L)

                    val hMorning = if (totalDurationMs > 0L) (morningDurationMs.toFloat() / maxPeriodDur) * maxWaveH else 0f
                    val hAfternoon = if (totalDurationMs > 0L) (afternoonDurationMs.toFloat() / maxPeriodDur) * maxWaveH else 0f
                    val hEvening = if (totalDurationMs > 0L) (eveningDurationMs.toFloat() / maxPeriodDur) * maxWaveH else 0f
                    val hNight = if (totalDurationMs > 0L) (nightDurationMs.toFloat() / maxPeriodDur) * maxWaveH else 0f

                    val xMorning = w * 0.16f
                    val xAfternoon = w * 0.40f
                    val xEvening = w * 0.65f
                    val xNight = w * 0.88f

                    val pMorning = Offset(xMorning, baseLineY - hMorning)
                    val pAfternoon = Offset(xAfternoon, baseLineY - hAfternoon)
                    val pEvening = Offset(xEvening, baseLineY - hEvening)
                    val pNight = Offset(xNight, baseLineY - hNight)

                    val activePoint = when (selectedPeriod) {
                        CircadianPeriod.MORNING -> if (totalDurationMs == 0L) Offset(xMorning, baseLineY) else pMorning
                        CircadianPeriod.AFTERNOON -> if (totalDurationMs == 0L) Offset(xAfternoon, baseLineY) else pAfternoon
                        CircadianPeriod.EVENING -> if (totalDurationMs == 0L) Offset(xEvening, baseLineY) else pEvening
                        CircadianPeriod.NIGHT -> if (totalDurationMs == 0L) Offset(xNight, baseLineY) else pNight
                    }

                    // 1. Silky-smooth Catmull-Rom spline wave path (C1 continuous, zero kinks)
                    val pts = listOf(
                        Offset(0f, baseLineY - hMorning * 0.25f),
                        pMorning,
                        pAfternoon,
                        pEvening,
                        pNight,
                        Offset(w, baseLineY - hNight * 0.25f)
                    )

                    val wavePath = Path().apply {
                        if (totalDurationMs == 0L) {
                            moveTo(0f, baseLineY)
                            lineTo(w, baseLineY)
                        } else {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 0 until pts.size - 1) {
                                val p0 = if (i == 0) pts[0] else pts[i - 1]
                                val p1 = pts[i]
                                val p2 = pts[i + 1]
                                val p3 = if (i + 2 < pts.size) pts[i + 2] else pts[i + 1]

                                val cp1x = p1.x + (p2.x - p0.x) / 6f
                                val cp1y = p1.y + (p2.y - p0.y) / 6f
                                val cp2x = p2.x - (p3.x - p1.x) / 6f
                                val cp2y = p2.y - (p3.y - p1.y) / 6f

                                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }
                        }
                    }

                    // Background fill under the wave
                    val fillPath = Path().apply {
                        addPath(wavePath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PaletteSand.copy(alpha = if (totalDurationMs > 0L) 0.22f else 0.06f),
                                Color(0xFF1B2A4A).copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = baseLineY - maxWaveH,
                            endY = h
                        )
                    )

                    // Wave Crest Line
                    drawPath(
                        path = wavePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                PaletteSand.copy(alpha = 0.4f),
                                PaletteSand,
                                PaletteSand.copy(alpha = 0.4f)
                            )
                        ),
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    )

                    // 2. Rising Sun/Moon pinned precisely on the spline curve
                    val sunCenter = activePoint
                    if (selectedDurationMs > 0L) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PaletteSand.copy(alpha = 0.9f),
                                    PaletteSand.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = sunCenter,
                                radius = 32f
                            ),
                            radius = 28f,
                            center = sunCenter
                        )
                        drawCircle(
                            color = PaletteSand,
                            radius = 11f,
                            center = sunCenter
                        )
                        drawCircle(
                            color = PaletteDarkNavy,
                            radius = 4.5f,
                            center = sunCenter
                        )
                    } else {
                        // Calm resting indicator on the curve/baseline
                        drawCircle(
                            color = PaletteSand.copy(alpha = 0.35f),
                            radius = 8f,
                            center = sunCenter
                        )
                        drawCircle(
                            color = PaletteSand,
                            radius = 4f,
                            center = sunCenter
                        )
                    }

                    // Vertical Pin to Baseline
                    if (sunCenter.y < baseLineY - 2f) {
                        drawLine(
                            color = PaletteSand.copy(alpha = 0.75f),
                            start = Offset(sunCenter.x, sunCenter.y + (if (selectedDurationMs > 0L) 12f else 6f)),
                            end = Offset(sunCenter.x, baseLineY),
                            strokeWidth = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    }
                }

                // Floating Duration Badge at Active Point
                val badgeXOffsetFraction = when (selectedPeriod) {
                    CircadianPeriod.MORNING -> 0.06f
                    CircadianPeriod.AFTERNOON -> 0.30f
                    CircadianPeriod.EVENING -> 0.55f
                    CircadianPeriod.NIGHT -> 0.76f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(badgeXOffsetFraction + 0.22f)
                            .wrapContentWidth(Alignment.End)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.9f))
                                .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$selectedDurationLabel • $selectedPercentage%",
                                color = PaletteSand,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = UrbanistFontFamily
                            )
                        }
                    }
                }
            }

            // Horizontal Axis Time Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("6 AM", "12 PM", "6 PM", "12 AM").forEach { timeText ->
                    Text(
                        text = timeText,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = UrbanistFontFamily
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   4. DUAL SPOTLIGHT CARDS (Top Artists & Top Tracks with Real Photos and Music Art)
   ------------------------------------------------------------------------------------------------- */

@Composable
private fun TopArtistsCard(
    artists: List<ArtistStat>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hasArtists = artists.isNotEmpty()
    val displayArtists = if (hasArtists) artists.take(4).map { it.artistName } else emptyList()

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top Artists",
                        color = PaletteCream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = UrbanistFontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasArtists) "The voices you keep coming back to." else "No listening data yet",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = UrbanistFontFamily
                    )
                }
                Text(
                    text = "✦",
                    color = PaletteSand,
                    fontSize = 13.sp
                )
            }

            // Visual with Real Profile Photos ONLY (or clean empty state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasArtists) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        displayArtists.forEachIndexed { index, name ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (index * 24).dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PaletteOxfordBlue)
                                    .border(
                                        width = if (index == 1) 1.8.dp else 1.5.dp,
                                        color = if (index == 1) PaletteSand else PaletteOxfordBlue,
                                        shape = CircleShape
                                    )
                            ) {
                                SieloArtistPhoto(
                                    imageUrl = null,
                                    name = name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } else {
                    // Minimal clean empty placeholders: 3 translucent wireframe rings
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PaletteDarkNavy.copy(alpha = 0.5f))
                                    .border(1.dp, BorderGlass, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (i == 0) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Arrow Action Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PaletteDarkNavy.copy(alpha = 0.8f))
                        .border(1.dp, BorderGlass, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Artists",
                        tint = PaletteCream,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTracksCard(
    songs: List<SongStat>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val eligibleSongs = songs
    val hasTracks = eligibleSongs.isNotEmpty()
    val displaySongs = if (hasTracks) eligibleSongs.take(3).map { it.songTitle to it.artistName } else emptyList()

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top Tracks",
                        color = PaletteCream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = UrbanistFontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasTracks) "Songs that defined your rotation." else "No tracks played yet",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = UrbanistFontFamily
                    )
                }
                Text(
                    text = "✦",
                    color = PaletteSand,
                    fontSize = 13.sp
                )
            }

            // Stacked Fanned-Out Visual with Real Music Art ONLY (or clean empty state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasTracks) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        displaySongs.forEachIndexed { index, (title, artist) ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (index * 20).dp)
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, PaletteOxfordBlue, RoundedCornerShape(10.dp))
                            ) {
                                SieloSongArtwork(
                                    thumbnailUrl = null,
                                    title = title,
                                    artist = artist,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Minimal clean empty placeholders: 3 layered vinyl sleeve outlines
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PaletteDarkNavy.copy(alpha = 0.5f))
                                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (i == 0) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Arrow Action Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PaletteDarkNavy.copy(alpha = 0.8f))
                        .border(1.dp, BorderGlass, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Tracks",
                        tint = PaletteCream,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   6. HEAVY ROTATION SONG CARD
   ------------------------------------------------------------------------------------------------- */

@Composable
private fun ModernSongLeaderboardCard(
    rank: Int,
    song: SongStat,
    onPlay: () -> Unit
) {
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> PaletteSlateBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable { onPlay() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$rank",
                    color = medalColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily,
                    modifier = Modifier.width(30.dp)
                )

                SieloSongArtwork(
                    thumbnailUrl = song.thumbnailUrl,
                    title = song.songTitle,
                    artist = song.artistName,
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.songTitle,
                        color = PaletteCream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = UrbanistFontFamily
                    )
                    Text(
                        text = "${song.artistName} • ${song.playCount} ${if (song.playCount == 1) "play" else "plays"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = UrbanistFontFamily
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PaletteSand.copy(alpha = 0.15f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------------
   7. DETAIL SHEET CONTENT (Tracks, Artists, Stream History)
   ------------------------------------------------------------------------------------------------- */

@Composable
fun StatsDetailSheetContent(
    sheetType: StatsSheetType,
    allSongs: List<SongStat>,
    allArtists: List<ArtistStat>,
    streamHistory: List<ListeningEventEntity>,
    onPlaySong: (SongStat) -> Unit,
    onPlayEvent: (ListeningEventEntity) -> Unit,
    onClose: () -> Unit
) {
    val title = when (sheetType) {
        StatsSheetType.TRACKS -> "All Unique Tracks (${allSongs.size})"
        StatsSheetType.ARTISTS -> "All Listened Artists (${allArtists.size})"
        StatsSheetType.PLAYS -> "Stream History (${streamHistory.size} plays)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = PaletteCream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = UrbanistFontFamily
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (sheetType) {
                StatsSheetType.TRACKS -> {
                    val qualifyingSongs = allSongs
                    if (qualifyingSongs.isEmpty()) {
                        item {
                            ModernEmptyStatsState(message = "Play your favorite songs to uncover your most played tracks.")
                        }
                    } else {
                        itemsIndexed(qualifyingSongs) { idx, song ->
                            ModernSongLeaderboardCard(
                                rank = idx + 1,
                                song = song,
                                onPlay = { onPlaySong(song) }
                            )
                        }
                    }
                }
                StatsSheetType.ARTISTS -> {
                    if (allArtists.isEmpty()) {
                        item {
                            ModernEmptyStatsState(message = "Play your favorite songs to uncover your top artists.")
                        }
                    } else {
                        itemsIndexed(allArtists) { idx, artist ->
                            ModernArtistLeaderboardRow(
                                rank = idx + 1,
                                artist = artist
                            )
                        }
                    }
                }
                StatsSheetType.PLAYS -> {
                    if (streamHistory.isEmpty()) {
                        item {
                            ModernEmptyStatsState(message = "No playback history logged yet.")
                        }
                    } else {
                        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        itemsIndexed(streamHistory) { idx, event ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                                    .clickable { onPlayEvent(event) }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SieloSongArtwork(
                                            thumbnailUrl = event.thumbnailUrl,
                                            title = event.songTitle,
                                            artist = event.artistName,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = event.songTitle,
                                                color = PaletteCream,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = UrbanistFontFamily
                                            )
                                            Text(
                                                text = "${event.artistName} • ${dateFormat.format(Date(event.timestampMs))}",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = UrbanistFontFamily
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(PaletteSand.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = PaletteSand,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernArtistLeaderboardRow(
    rank: Int,
    artist: ArtistStat
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> PaletteSlateBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "#$rank",
                    color = rankColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = UrbanistFontFamily,
                    modifier = Modifier.width(32.dp)
                )

                SieloArtistPhoto(
                    imageUrl = null,
                    name = artist.artistName,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = artist.artistName,
                        color = PaletteCream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = UrbanistFontFamily
                    )
                    Text(
                        text = "${artist.playCount} plays",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = UrbanistFontFamily
                    )
                }
            }
        }
    }
}

@Composable
fun ModernEmptyStatsState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                tint = PaletteSand,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontFamily = UrbanistFontFamily
            )
        }
    }
}
