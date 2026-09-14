package com.sielo.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sielo.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ShuffleAnimationState(
    val progress: Animatable<Float, AnimationVector1D>,
    val highlight: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope
) {
    fun playAnimation(durationMs: Int = 1150, onDone: () -> Unit = {}) {
        scope.launch {
            launch {
                highlight.snapTo(1f)
                highlight.animateTo(0f, animationSpec = tween(durationMillis = durationMs, easing = LinearEasing))
            }
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
            progress.snapTo(0f)
            onDone()
        }
    }
}

@Composable
fun rememberShuffleAnimationState(): ShuffleAnimationState {
    val progress = remember { Animatable(0f) }
    val highlight = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return remember(scope) {
        ShuffleAnimationState(progress, highlight, scope)
    }
}

@Composable
fun AnimatedShuffleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.White,
    progress: Float = 0f
) {
    val spriteSheet = ImageBitmap.imageResource(id = R.drawable.ic_shuffle_sheet)
    val totalFrames = 36
    val cols = 6
    val frameSizePx = spriteSheet.width / cols

    val currentFrame = (progress * (totalFrames - 1)).toInt().coerceIn(0, totalFrames - 1)
    val col = currentFrame % cols
    val row = currentFrame / cols

    Canvas(modifier = modifier.size(size)) {
        val srcOffset = IntOffset(col * frameSizePx, row * frameSizePx)
        val srcSize = IntSize(frameSizePx, frameSizePx)
        val dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())

        drawImage(
            image = spriteSheet,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstSize = dstSize,
            colorFilter = ColorFilter.tint(tint)
        )
    }
}
