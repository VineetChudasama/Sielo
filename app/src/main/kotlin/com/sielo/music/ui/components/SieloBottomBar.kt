package com.sielo.music.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sielo.music.ui.navigation.Screen
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import kotlin.math.roundToInt

private val NavAnimationSpec = tween<Float>(
    durationMillis = 350,
    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
)

@Composable
fun SieloBottomBar(
    navController: NavController,
    onTabSelected: ((Screen) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val items = Screen.bottomNavItems
    val selectedIndex = remember(currentRoute) {
        val idx = items.indexOfFirst { it.route == currentRoute }
        if (idx >= 0) idx else 0
    }

    val density = LocalDensity.current

    // Geometry constants
    val containerHeightDp = 80.dp
    val wingTopDp = 17.dp
    val wingBottomDp = 63.dp
    val wingCornerRadiusDp = 23.dp
    val wingHeightDp = wingBottomDp - wingTopDp // 46.dp

    val domeRiseDp = 14.dp
    val domePeakYDp = wingTopDp - domeRiseDp // 3.dp
    val domeHalfWidthDp = 46.dp

    val bottomSwellDepthDp = 13.dp
    val bottomSwellYDp = wingBottomDp + bottomSwellDepthDp // 76.dp
    val bottomSwellHalfWidthDp = 44.dp

    val scoopDepthDp = 17.dp
    val scoopHalfWidthDp = 36.dp

    val activeCenterYDp = 40.dp

    val wingTopPx = with(density) { wingTopDp.toPx() }
    val wingBottomPx = with(density) { wingBottomDp.toPx() }
    val wingCornerRadiusPx = with(density) { wingCornerRadiusDp.toPx() }
    val domePeakYPx = with(density) { domePeakYDp.toPx() }
    val domeHalfWidthPx = with(density) { domeHalfWidthDp.toPx() }
    val bottomSwellYPx = with(density) { bottomSwellYDp.toPx() }
    val bottomSwellHalfWidthPx = with(density) { bottomSwellHalfWidthDp.toPx() }
    val scoopDepthPx = with(density) { scoopDepthDp.toPx() }
    val scoopHalfWidthPx = with(density) { scoopHalfWidthDp.toPx() }
    val activeCenterYPx = with(density) { activeCenterYDp.toPx() }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val itemCenterPositions = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }

    val targetX = if (itemCenterPositions.size == items.size && itemCenterPositions[selectedIndex] > 0f) {
        itemCenterPositions[selectedIndex]
    } else if (containerWidthPx > 0f) {
        (selectedIndex + 0.5f) * (containerWidthPx / items.size)
    } else {
        0f
    }

    val animatedCenterX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = NavAnimationSpec,
        label = "activeCenterX"
    )

    val targetEffectStrength = when (selectedIndex) {
        2 -> 1.0f     // 3rd element (Listen Together) - full prominent curves
        1, 3 -> 0.65f  // 2nd & 4th elements (Search & Stats) - slightly smaller curves
        else -> 0.0f   // 1st & 5th elements (Home & Profile) - normal pill
    }

    val effectStrength by animateFloatAsState(
        targetValue = targetEffectStrength,
        animationSpec = NavAnimationSpec,
        label = "effectStrength"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .height(containerHeightDp)
            .onGloballyPositioned { coordinates ->
                containerWidthPx = coordinates.size.width.toFloat()
            }
    ) {
        // Canvas: 2-Layer System with Conditional Organic Effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            if (width <= 0f) return@Canvas

            val activeX = if (animatedCenterX > 0f) animatedCenterX else (selectedIndex + 0.5f) * (width / items.size)
            val centerY = activeCenterYPx

            // ==========================================
            // LAYER 1 (BELOW): Light Chassis (Normal Pill or Organic Dome)
            // ==========================================
            val wingRadius = with(density) { 23.dp.toPx() }
            val domeRadius = with(density) { 36.dp.toPx() }
            val domeRise = (domeRadius - wingRadius) * effectStrength
            val domeHalfWidth = with(density) { (40.dp + 8.dp * effectStrength).toPx() }

            fun getLightTopY(x: Float): Float {
                if (domeRise <= 0.1f) return centerY - wingRadius
                val dx = kotlin.math.abs(x - activeX)
                return if (dx < domeHalfWidth) {
                    val factor = (1f + kotlin.math.cos(Math.PI.toFloat() * (dx / domeHalfWidth))) / 2f
                    (centerY - wingRadius) - domeRise * factor
                } else {
                    centerY - wingRadius
                }
            }

            fun getLightBottomY(x: Float): Float {
                if (domeRise <= 0.1f) return centerY + wingRadius
                val dx = kotlin.math.abs(x - activeX)
                return if (dx < domeHalfWidth) {
                    val factor = (1f + kotlin.math.cos(Math.PI.toFloat() * (dx / domeHalfWidth))) / 2f
                    (centerY + wingRadius) + domeRise * factor
                } else {
                    centerY + wingRadius
                }
            }

            val leftBound = wingRadius
            val rightBound = (width - wingRadius).coerceAtLeast(leftBound)

            val outerChassisPath = Path().apply {
                val startTopY = getLightTopY(leftBound)
                moveTo(leftBound, startTopY)

                val stepPx = with(density) { 2.dp.toPx() }
                var curX = leftBound + stepPx
                while (curX < rightBound) {
                    lineTo(curX, getLightTopY(curX))
                    curX += stepPx
                }
                lineTo(rightBound, getLightTopY(rightBound))

                val rightTopY = getLightTopY(rightBound)
                val rightBottomY = getLightBottomY(rightBound)
                arcTo(
                    rect = Rect(
                        left = rightBound - wingRadius,
                        top = rightTopY,
                        right = rightBound + wingRadius,
                        bottom = rightBottomY
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )

                curX = rightBound - stepPx
                while (curX > leftBound) {
                    lineTo(curX, getLightBottomY(curX))
                    curX -= stepPx
                }
                lineTo(leftBound, getLightBottomY(leftBound))

                val leftBottomY = getLightBottomY(leftBound)
                arcTo(
                    rect = Rect(
                        left = leftBound - wingRadius,
                        top = startTopY,
                        right = leftBound + wingRadius,
                        bottom = leftBottomY
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )

                close()
            }

            // Fill Lighter Underlying Chassis (#415A77 PaletteSlateBlue gradient)
            drawPath(
                path = outerChassisPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF527196),
                        PaletteSlateBlue,
                        Color(0xFF26374A)
                    ),
                    startY = centerY - domeRadius,
                    endY = centerY + domeRadius
                ),
                style = Fill
            )

            // Lighter layer top & perimeter rim highlight stroke
            drawPath(
                path = outerChassisPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PaletteSand.copy(alpha = 0.70f),
                        PaletteSlateBlue.copy(alpha = 0.40f),
                        Color(0x33415A77)
                    ),
                    startY = centerY - domeRadius,
                    endY = centerY + domeRadius
                ),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // ==========================================
            // LAYER 2 (TOP): Inset Dark Layer (Normal Pill or Cradle Cutouts)
            // ==========================================
            val darkInset = with(density) { 3.5.dp.toPx() }
            val darkWingRadius = (wingRadius - darkInset).coerceAtLeast(10f)
            val scoopDepth = with(density) { 15.dp.toPx() } * effectStrength
            val scoopHalfWidth = with(density) { (30.dp + 8.dp * effectStrength).toPx() }

            fun getDarkTopY(x: Float): Float {
                if (scoopDepth <= 0.1f) return centerY - darkWingRadius
                val dx = kotlin.math.abs(x - activeX)
                return if (dx < scoopHalfWidth) {
                    val factor = (1f + kotlin.math.cos(Math.PI.toFloat() * (dx / scoopHalfWidth))) / 2f
                    (centerY - darkWingRadius) + scoopDepth * factor
                } else {
                    centerY - darkWingRadius
                }
            }

            fun getDarkBottomY(x: Float): Float {
                if (scoopDepth <= 0.1f) return centerY + darkWingRadius
                val dx = kotlin.math.abs(x - activeX)
                return if (dx < scoopHalfWidth) {
                    val factor = (1f + kotlin.math.cos(Math.PI.toFloat() * (dx / scoopHalfWidth))) / 2f
                    (centerY + darkWingRadius) - scoopDepth * factor
                } else {
                    centerY + darkWingRadius
                }
            }

            val darkLeftBound = darkWingRadius
            val darkRightBound = (width - darkWingRadius).coerceAtLeast(darkLeftBound)

            val darkPillPath = Path().apply {
                val startTopY = getDarkTopY(darkLeftBound)
                moveTo(darkLeftBound, startTopY)

                val stepPx = with(density) { 2.dp.toPx() }
                var curX = darkLeftBound + stepPx
                while (curX < darkRightBound) {
                    lineTo(curX, getDarkTopY(curX))
                    curX += stepPx
                }
                lineTo(darkRightBound, getDarkTopY(darkRightBound))

                val rightTopY = getDarkTopY(darkRightBound)
                val rightBottomY = getDarkBottomY(darkRightBound)
                arcTo(
                    rect = Rect(
                        left = darkRightBound - darkWingRadius,
                        top = rightTopY,
                        right = darkRightBound + darkWingRadius,
                        bottom = rightBottomY
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )

                curX = darkRightBound - stepPx
                while (curX > darkLeftBound) {
                    lineTo(curX, getDarkBottomY(curX))
                    curX -= stepPx
                }
                lineTo(darkLeftBound, getDarkBottomY(darkLeftBound))

                val leftBottomY = getDarkBottomY(darkLeftBound)
                arcTo(
                    rect = Rect(
                        left = darkLeftBound - darkWingRadius,
                        top = startTopY,
                        right = darkLeftBound + darkWingRadius,
                        bottom = leftBottomY
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )

                close()
            }

            // Fill Inset Dark Layer (#1B263B PaletteOxfordBlue)
            drawPath(
                path = darkPillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B273A),
                        PaletteOxfordBlue,
                        Color(0xFF0F1622)
                    ),
                    startY = centerY - darkWingRadius,
                    endY = centerY + darkWingRadius
                ),
                style = Fill
            )

            // Dark layer border stroke
            drawPath(
                path = darkPillPath,
                color = PaletteSlateBlue.copy(alpha = 0.35f),
                style = Stroke(width = 1.dp.toPx())
            )

            // ==========================================
            // ACTIVE ELEMENT: Glossy Glass Circular Disc
            // ==========================================
            val glossyRadiusPx = with(density) { 24.dp.toPx() }
            val circleCenter = Offset(activeX, activeCenterYPx)

            // 1. Soft Ambient Depth Shadow
            drawCircle(
                color = Color(0x55000000),
                radius = glossyRadiusPx + with(density) { 3.dp.toPx() },
                center = circleCenter + Offset(0f, with(density) { 2.dp.toPx() })
            )

            // 2. Translucent Glass Body with subtle inner depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x38FFFFFF),
                        PaletteSlateBlue.copy(alpha = 0.55f),
                        PaletteOxfordBlue.copy(alpha = 0.85f),
                        Color(0xE60D1B2A)
                    ),
                    center = circleCenter - Offset(glossyRadiusPx * 0.25f, glossyRadiusPx * 0.35f),
                    radius = glossyRadiusPx * 1.35f
                ),
                radius = glossyRadiusPx,
                center = circleCenter
            )

            // 3. Curved Specular Gloss Highlight (Top-down sheen)
            val glossTopPath = Path().apply {
                val sheenWidthPx = glossyRadiusPx * 1.75f
                val sheenHeightPx = glossyRadiusPx * 1.05f
                val sheenTopY = activeCenterYPx - glossyRadiusPx + with(density) { 1.5.dp.toPx() }

                addOval(
                    Rect(
                        left = activeX - sheenWidthPx / 2f,
                        top = sheenTopY,
                        right = activeX + sheenWidthPx / 2f,
                        bottom = sheenTopY + sheenHeightPx
                    )
                )
            }

            drawPath(
                path = glossTopPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    startY = activeCenterYPx - glossyRadiusPx,
                    endY = activeCenterYPx
                )
            )

            // 4. Inner Concentric Glass Ring (Refraction ring)
            drawCircle(
                color = PaletteSand.copy(alpha = 0.25f),
                radius = glossyRadiusPx - with(density) { 2.5.dp.toPx() },
                center = circleCenter,
                style = Stroke(width = with(density) { 1.dp.toPx() })
            )

            // 5. Outer Illuminated Glass Rim with Top Bright Glint & Bottom Bounce Light
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PaletteSand.copy(alpha = 0.90f),
                        PaletteSlateBlue.copy(alpha = 0.45f),
                        Color(0x33415A77),
                        Color(0x66D4C4A8)
                    ),
                    startY = activeCenterYPx - glossyRadiusPx,
                    endY = activeCenterYPx + glossyRadiusPx
                ),
                radius = glossyRadiusPx,
                center = circleCenter,
                style = Stroke(width = with(density) { 1.4.dp.toPx() })
            )
        }

        // Active Icon (centered directly inside the organic light layer shape)
        if (animatedCenterX > 0f) {
            val activeScreen = items[selectedIndex]
            val activeIconBoxSizeDp = 48.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (animatedCenterX - with(density) { (activeIconBoxSizeDp / 2).toPx() }).roundToInt(),
                            y = (activeCenterYPx - with(density) { (activeIconBoxSizeDp / 2).toPx() }).roundToInt()
                        )
                    }
                    .size(activeIconBoxSizeDp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = activeScreen,
                    animationSpec = tween(durationMillis = 200),
                    label = "activeButtonIcon"
                ) { targetScreen ->
                    Icon(
                        imageVector = targetScreen.selectedIcon,
                        contentDescription = targetScreen.title,
                        tint = PaletteCream,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // 5 Inactive Navigation Items on the Dark Layer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeightDp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex

                val inactiveIconAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0f else 1f,
                    animationSpec = NavAnimationSpec,
                    label = "inactiveIconAlpha_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(containerHeightDp)
                        .onGloballyPositioned { coordinates ->
                            val localPos = coordinates.positionInParent()
                            val cX = localPos.x + coordinates.size.width / 2f
                            if (index < itemCenterPositions.size) {
                                itemCenterPositions[index] = cX
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected?.invoke(screen)
                            if (screen.route == Screen.Home.route) {
                                val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                                if (!popped && currentRoute != Screen.Home.route) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            } else if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Inactive Icon on Dark Layer
                    Box(
                        modifier = Modifier
                            .offset(y = ((wingTopDp + wingHeightDp / 2) - containerHeightDp / 2))
                            .graphicsLayer { alpha = inactiveIconAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.unselectedIcon,
                            contentDescription = screen.title,
                            tint = PaletteSageGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}


