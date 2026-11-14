package com.example.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val CoinEasing = CubicBezierEasing(0.2f, 1.35f, 0.35f, 1.0f)
private val MarkEasing = CubicBezierEasing(0.6f, 0.0f, 0.3f, 1.0f)
private val DotEasing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1.0f)
private val RiseEasing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1.0f)
private val EaseOut = LinearOutSlowInEasing

private fun sampleProgress(
    timeMs: Float,
    startMs: Float,
    durationMs: Float,
    easing: Easing = LinearEasing
): Float {
    if (timeMs <= startMs) return 0f
    if (timeMs >= startMs + durationMs) return 1f
    val fraction = (timeMs - startMs) / durationMs
    return easing.transform(fraction.coerceIn(0f, 1f))
}

@Composable
fun LaunchAnimationScreen(
    onAnimationFinished: () -> Unit
) {
    val animTime = remember { androidx.compose.animation.core.Animatable(0f) }
    var hasFinished by remember { mutableStateOf(false) }

    fun finishEarly() {
        if (!hasFinished) {
            hasFinished = true
            onAnimationFinished()
        }
    }

    LaunchedEffect(Unit) {
        animTime.animateTo(
            targetValue = 3600f,
            animationSpec = tween(durationMillis = 3600, easing = LinearEasing)
        )
        delay(100)
        finishEarly()
    }

    val timeMs = animTime.value

    // Glow progress
    val glowProgress = sampleProgress(timeMs, 100f, 1600f, EaseOut)
    val glowScale = 0.4f + 0.6f * glowProgress
    val glowAlpha = glowProgress

    // Coin progress (overshoot bounce + 3D rotateY)
    val coinProgress = sampleProgress(timeMs, 200f, 950f, CoinEasing)
    val coinScale = coinProgress
    val coinRotationY = -200f * (1f - coinProgress.coerceIn(0f, 1.15f))
    val coinAlpha = sampleProgress(timeMs, 200f, 285f).coerceIn(0f, 1f)

    // Inner rim
    val rimAlpha = sampleProgress(timeMs, 850f, 400f)

    // Indian Rupee Symbol paths
    val path1Progress = sampleProgress(timeMs, 950f, 550f, MarkEasing)
    val path2Progress = sampleProgress(timeMs, 1100f, 550f, MarkEasing)
    val path3Progress = sampleProgress(timeMs, 1200f, 550f, MarkEasing)

    // Pulse wave
    val pulseProgress = sampleProgress(timeMs, 1750f, 1000f, EaseOut)
    val pulseScale = 1.0f + 1.2f * pulseProgress
    val pulseAlpha = if (timeMs in 1750f..2750f) 0.8f * (1f - pulseProgress) else 0f

    // Orange Dot
    val dotProgress = sampleProgress(timeMs, 1700f, 800f, DotEasing)
    val dotTranslateY = (-260).dp * (1f - dotProgress)
    val dotScale = 0.6f + 0.4f * dotProgress
    val dotAlpha = sampleProgress(timeMs, 1700f, 200f).coerceIn(0f, 1f)

    // Tagline and Hint
    val tagAlpha = sampleProgress(timeMs, 2600f, 800f)
    val hintAlpha = sampleProgress(timeMs, 3200f, 400f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1517))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finishEarly() }
    ) {
        // 1. Ambient Background Glow (centered at 50% width, 44% height)
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (glowAlpha > 0f) {
                val glowRadius = size.minDimension * 0.7f * glowScale
                val glowCenter = Offset(size.width * 0.5f, size.height * 0.44f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF16343A).copy(alpha = glowAlpha),
                            Color(0xFF16343A).copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        ),
                        center = glowCenter,
                        radius = glowRadius
                    ),
                    center = glowCenter,
                    radius = glowRadius
                )
            }
        }

        // 2. Central Content (Coin + "Yosan" + Tagline)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Coin Wrap (128dp x 128dp)
            Box(
                modifier = Modifier.size(128.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulse Ring expanding around the coin
                if (pulseAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .border(2.dp, Color(0xFF34D1A0), CircleShape)
                    )
                }

                // 3D Coin with Shadow, Radial Gradient, Inner Rim, and Rupee Symbol
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = coinScale
                            scaleY = coinScale
                            rotationY = coinRotationY
                            cameraDistance = 16f * density
                            alpha = coinAlpha
                        }
                ) {
                    val pathMeasure = remember { PathMeasure() }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = size.width / 100f

                        // Outer soft green shadow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x5924C896),
                                    Color(0x2E24C896),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.5f + 14f * scale),
                                radius = size.width * 0.72f
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f + 14f * scale),
                            radius = size.width * 0.72f
                        )

                        // Main Coin Surface (Radial Gradient at 32% x, 28% y)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF34D1A0), Color(0xFF17956D)),
                                center = Offset(size.width * 0.32f, size.height * 0.28f),
                                radius = size.width * 0.75f
                            ),
                            radius = size.width * 0.5f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )

                        // Top inner highlight
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x40FFFFFF),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.4f
                            ),
                            radius = size.width * 0.5f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )

                        // Inner Rim
                        if (rimAlpha > 0f) {
                            val rimInset = 7.dp.toPx()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.28f * rimAlpha),
                                radius = (size.width - (rimInset * 2f)) / 2f,
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Indian Rupee Symbol (SVG paths animated)
                        val strokeWidth = 7f * scale
                        val markColor = Color(0xFFF4FFFB)

                        // Path 1: M30 31 H71
                        if (path1Progress > 0f) {
                            val p1 = Path().apply {
                                moveTo(30f * scale, 31f * scale)
                                lineTo(71f * scale, 31f * scale)
                            }
                            if (path1Progress >= 1f) {
                                drawPath(
                                    path = p1,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            } else {
                                pathMeasure.setPath(p1, false)
                                val segment = Path()
                                pathMeasure.getSegment(0f, pathMeasure.length * path1Progress, segment, true)
                                drawPath(
                                    path = segment,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // Path 2: M30 43 H71
                        if (path2Progress > 0f) {
                            val p2 = Path().apply {
                                moveTo(30f * scale, 43f * scale)
                                lineTo(71f * scale, 43f * scale)
                            }
                            if (path2Progress >= 1f) {
                                drawPath(
                                    path = p2,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            } else {
                                pathMeasure.setPath(p2, false)
                                val segment = Path()
                                pathMeasure.getSegment(0f, pathMeasure.length * path2Progress, segment, true)
                                drawPath(
                                    path = segment,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // Path 3: M33 31 H52 C67 31 67 55 52 55 H33 L59 77
                        if (path3Progress > 0f) {
                            val p3 = Path().apply {
                                moveTo(33f * scale, 31f * scale)
                                lineTo(52f * scale, 31f * scale)
                                cubicTo(
                                    67f * scale, 31f * scale,
                                    67f * scale, 55f * scale,
                                    52f * scale, 55f * scale
                                )
                                lineTo(33f * scale, 55f * scale)
                                lineTo(59f * scale, 77f * scale)
                            }
                            if (path3Progress >= 1f) {
                                drawPath(
                                    path = p3,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            } else {
                                pathMeasure.setPath(p3, false)
                                val segment = Path()
                                pathMeasure.getSegment(0f, pathMeasure.length * path3Progress, segment, true)
                                drawPath(
                                    path = segment,
                                    color = markColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }

                // Dropping Vibrant Orange Dot (20dp) at top-right
                if (dotAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 4.dp + dotTranslateY)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                                alpha = dotAlpha
                            }
                            .size(20.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xB3FF9F1C),
                                        Color(0x4DFF9F1C),
                                        Color.Transparent
                                    ),
                                    radius = size.width * 1.5f
                                ),
                                radius = size.width * 1.5f
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF9F1C), CircleShape)
                                .border(3.dp, Color(0xFF0B1517), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            // Brand Word: "Yosan" (staggered rising letters)
            Row(
                modifier = Modifier
                    .height(58.dp)
                    .clipToBounds(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val letters = listOf("Y", "o", "s", "a", "n")
                val delays = listOf(2000f, 2060f, 2120f, 2180f, 2240f)
                letters.forEachIndexed { idx, char ->
                    val progress = sampleProgress(timeMs, delays[idx], 600f, RiseEasing)
                    val translateY = 64.dp * (1f - progress)
                    Text(
                        text = char,
                        color = Color(0xFFEAF7F3),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                        modifier = Modifier.offset(y = translateY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline: "Every rupee, planned."
            Text(
                text = "Every rupee, planned.",
                color = Color(0xFF7FA39A).copy(alpha = tagAlpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp
            )
        }

        // Bottom hint: "Tap to skip"
        if (hintAlpha > 0f) {
            Text(
                text = "Tap to skip",
                color = Color(0xFF7FA39A).copy(alpha = hintAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}
