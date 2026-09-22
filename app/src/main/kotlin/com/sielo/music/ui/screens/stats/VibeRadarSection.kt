package com.sielo.music.ui.screens.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderHighlight
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.StatsTimeframe
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 5-Axis Vibe Radar Component.
 * Visualizes user's authentic listening patterns (Activity, Replay, Consistency, Discovery, Variety)
 * with symmetrical pentagonal geometry and reactive tap exploration.
 */
@Composable
fun VibeRadarSection(
    vibeRadarData: VibeRadarData,
    timeframe: StatsTimeframe,
    modifier: Modifier = Modifier
) {
    var selectedMetricIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Smooth entry and value transition animation
    val animProgress by animateFloatAsState(
        targetValue = if (vibeRadarData.hasData) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "RadarPolygonAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 1. Header with Crescent / Star Emblem & Timeframe Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
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
                        Text(
                            text = "✦",
                            color = PaletteSand,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = "LISTENING VIBE",
                            color = PaletteSand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = UrbanistFontFamily
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Vibe Radar",
                            color = PaletteCream,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = UrbanistFontFamily
                        )
                    }
                }

                // Timeframe Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy.copy(alpha = 0.85f))
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (vibeRadarData.hasData) "• ${timeframe.label}" else "• No data yet",
                        color = if (vibeRadarData.hasData) PaletteSand else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = UrbanistFontFamily
                    )
                }
            }

            // Subtitle Description
            Text(
                text = if (vibeRadarData.hasData) {
                    "Your personal sound pattern across ${timeframe.label}."
                } else {
                    "A visual blueprint of your real listening behavior."
                },
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = UrbanistFontFamily
            )

            // 2. Interactive Canvas Radar Chart
            val angles = listOf(
                -90.0, // 0: Activity (Top)
                -18.0, // 1: Replay (Upper Right)
                54.0,  // 2: Consistency (Lower Right)
                126.0, // 3: Discovery (Lower Left)
                198.0  // 4: Variety (Upper Left)
            )

            val touchTolerancePx = with(density) { 32.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(264.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(vibeRadarData, animProgress) {
                            detectTapGestures { tapOffset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val centerX = w * 0.5f
                                val centerY = h * 0.5f
                                val maxRadius = (minOf(w, h) * 0.33f).coerceAtMost(110f * density.density)

                                var closestIndex: Int? = null
                                var minDistance = Float.MAX_VALUE

                                angles.forEachIndexed { index, deg ->
                                    val rad = Math.toRadians(deg)
                                    // Check label center
                                    val labelRadius = maxRadius + 26f * density.density
                                    val lx = centerX + (labelRadius * cos(rad)).toFloat()
                                    val ly = centerY + (labelRadius * sin(rad)).toFloat()
                                    val dLabel = hypot(tapOffset.x - lx, tapOffset.y - ly)

                                    // Check data vertex center
                                    val metric = vibeRadarData.metricsList[index]
                                    val r = maxRadius * (metric.value / 100f) * animProgress
                                    val px = centerX + (r * cos(rad)).toFloat()
                                    val py = centerY + (r * sin(rad)).toFloat()
                                    val dPoint = hypot(tapOffset.x - px, tapOffset.y - py)

                                    val d = minOf(dLabel, dPoint)
                                    if (d < touchTolerancePx && d < minDistance) {
                                        minDistance = d
                                        closestIndex = index
                                    }
                                }

                                selectedMetricIndex = if (selectedMetricIndex == closestIndex) null else closestIndex
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val centerX = w * 0.5f
                    val centerY = h * 0.5f
                    val maxRadius = (minOf(w, h) * 0.33f).coerceAtMost(110f * density.density)

                    // 1. Draw 4 Concentric Pentagons (Skip inner ring in empty state for clear center)
                    for (step in 1..4) {
                        if (!vibeRadarData.hasData && step <= 2) continue
                        val r = maxRadius * (step / 4f)
                        val pentagonPath = Path().apply {
                            angles.forEachIndexed { index, deg ->
                                val rad = Math.toRadians(deg)
                                val px = centerX + (r * cos(rad)).toFloat()
                                val py = centerY + (r * sin(rad)).toFloat()
                                if (index == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }
                        drawPath(
                            path = pentagonPath,
                            color = PaletteSlateBlue.copy(alpha = if (step == 4) 0.35f else 0.18f),
                            style = Stroke(width = if (step == 4) 1.4f else 1.0f)
                        )
                    }

                    // 2. Draw 5 Radial Axis Lines (Clear center circle when empty)
                    val spokeStartR = if (!vibeRadarData.hasData) 44f * density.density else 0f
                    angles.forEach { deg ->
                        val rad = Math.toRadians(deg)
                        val startX = centerX + (spokeStartR * cos(rad)).toFloat()
                        val startY = centerY + (spokeStartR * sin(rad)).toFloat()
                        val endX = centerX + (maxRadius * cos(rad)).toFloat()
                        val endY = centerY + (maxRadius * sin(rad)).toFloat()
                        drawLine(
                            color = PaletteSlateBlue.copy(alpha = 0.28f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.2f
                        )
                    }

                    // 3. Draw Data Polygon (When has data)
                    if (vibeRadarData.hasData && animProgress > 0.01f) {
                        val dataPath = Path().apply {
                            vibeRadarData.metricsList.forEachIndexed { index, metric ->
                                val deg = angles[index]
                                val rad = Math.toRadians(deg)
                                val r = (maxRadius * (metric.value / 100f) * animProgress).coerceAtLeast(3f)
                                val px = centerX + (r * cos(rad)).toFloat()
                                val py = centerY + (r * sin(rad)).toFloat()
                                if (index == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }

                        // Polygon fill
                        drawPath(
                            path = dataPath,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PaletteSand.copy(alpha = 0.28f),
                                    PaletteSand.copy(alpha = 0.12f),
                                    PaletteDarkNavy.copy(alpha = 0.05f)
                                ),
                                center = Offset(centerX, centerY),
                                radius = maxRadius
                            )
                        )

                        // Polygon outline
                        drawPath(
                            path = dataPath,
                            color = PaletteSand,
                            style = Stroke(
                                width = 2.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // 4. Data Points / Vertex Pearls
                        vibeRadarData.metricsList.forEachIndexed { index, metric ->
                            val deg = angles[index]
                            val rad = Math.toRadians(deg)
                            val r = (maxRadius * (metric.value / 100f) * animProgress).coerceAtLeast(3f)
                            val px = centerX + (r * cos(rad)).toFloat()
                            val py = centerY + (r * sin(rad)).toFloat()
                            val isSelected = selectedMetricIndex == index

                            // Outer halo
                            drawCircle(
                                color = if (isSelected) PaletteSand.copy(alpha = 0.60f) else PaletteSand.copy(alpha = 0.30f),
                                radius = if (isSelected) 10f * density.density else 6f * density.density,
                                center = Offset(px, py)
                            )
                            // Inner core
                            drawCircle(
                                color = if (isSelected) PaletteSand else PaletteCream,
                                radius = if (isSelected) 4.5f * density.density else 3f * density.density,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // 5. Draw Labels around the 5 Vertices
                    angles.forEachIndexed { index, deg ->
                        val metric = vibeRadarData.metricsList[index]
                        val rad = Math.toRadians(deg)
                        val labelRadius = maxRadius + (26f * density.density)
                        val lx = centerX + (labelRadius * cos(rad)).toFloat()
                        val ly = centerY + (labelRadius * sin(rad)).toFloat()
                        val isSelected = selectedMetricIndex == index

                        val labelText = if (vibeRadarData.hasData) {
                            if (metric.isAvailable) {
                                "${metric.label}\n${(metric.value * animProgress).toInt()}"
                            } else {
                                "${metric.label}\n—"
                            }
                        } else {
                            metric.label
                        }

                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(labelText),
                            style = TextStyle(
                                color = if (isSelected) PaletteSand else PaletteCream.copy(alpha = 0.88f),
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontFamily = UrbanistFontFamily,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                        )

                        val textOffset = Offset(
                            lx - textLayoutResult.size.width / 2f,
                            ly - textLayoutResult.size.height / 2f
                        )
                        drawText(textLayoutResult, topLeft = textOffset)
                    }
                }

                // Empty State Overlay if no history (compact, zero overlap with vertex labels)
                if (!vibeRadarData.hasData) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "✦",
                            color = PaletteSand.copy(alpha = 0.90f),
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "YOUR VIBE IS WAITING",
                            color = PaletteCream,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = UrbanistFontFamily,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Keep listening to\nreveal your Sound DNA.",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = UrbanistFontFamily,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Coverage / Audio Feature Status Pill
            if (vibeRadarData.hasData) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1420))
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (vibeRadarData.coveragePercent > 0) PaletteSageGreen else PaletteSand.copy(alpha = 0.6f))
                    )
                    Text(
                        text = if (vibeRadarData.coveragePercent > 0) {
                            "${vibeRadarData.coverageDescription} · ${vibeRadarData.coverageLevel.name}"
                        } else {
                            "Acoustic features pending · Behavior radar active"
                        },
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = UrbanistFontFamily
                    )
                }
            }

            // 3. Interactive Detail Callout
            AnimatedContent(
                targetState = selectedMetricIndex,
                label = "RadarMetricCallout"
            ) { targetIdx ->
                val metric = targetIdx?.let { vibeRadarData.metricsList.getOrNull(it) }
                if (metric != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1420).copy(alpha = 0.90f))
                            .border(1.dp, BorderHighlight, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = metric.label.uppercase(),
                                color = PaletteSand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = UrbanistFontFamily
                            )
                            Text(
                                text = if (vibeRadarData.hasData) "${metric.value.toInt()} / 100" else "—",
                                color = PaletteCream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = UrbanistFontFamily
                            )
                        }
                        Text(
                            text = metric.rawDescription,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontFamily = UrbanistFontFamily
                        )
                        Text(
                            text = metric.insightSummary,
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = UrbanistFontFamily
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1420).copy(alpha = 0.65f))
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SOUND DNA",
                                color = PaletteSand,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = UrbanistFontFamily
                            )
                            Text(
                                text = "Tap any axis for details",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = UrbanistFontFamily
                            )
                        }
                        Text(
                            text = vibeRadarData.soundDnaSummary,
                            color = PaletteCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = UrbanistFontFamily,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 4. Compact Subtle Metric Indicators (Section 15)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vibeRadarData.metricsList.forEachIndexed { index, metric ->
                    val isSelected = selectedMetricIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PaletteDarkNavy.copy(alpha = 0.7f) else Color.Transparent)
                            .clickable { selectedMetricIndex = if (isSelected) null else index }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = metric.label,
                            color = if (isSelected) PaletteSand else PaletteCream,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = UrbanistFontFamily,
                            modifier = Modifier.width(86.dp)
                        )

                        // Horizontal indicator line: dot + bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Background subtle line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(PaletteSlateBlue.copy(alpha = 0.25f))
                            )
                            // Active bar
                            val fraction = if (vibeRadarData.hasData) (metric.value / 100f).coerceIn(0f, 1f) else 0f
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(2.5.dp)
                                        .background(if (isSelected) PaletteSand else PaletteSageGreen.copy(alpha = 0.85f))
                                )
                                // Dot at the tip
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .wrapContentWidth(Alignment.End)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) PaletteSand else PaletteCream)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (vibeRadarData.hasData && metric.isAvailable) metric.value.toInt().toString() else "—",
                            color = if (isSelected) PaletteSand else PaletteCream.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = UrbanistFontFamily,
                            modifier = Modifier.width(26.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
