/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.odomu.honorduo.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

private val AMBIENT_POINTS = floatArrayOf(
    0.8f, 0.2f, 1.0f,
    0.8f, 0.9f, 1.0f,
    0.2f, 0.9f, 1.0f,
    0.2f, 0.2f, 1.0f,
)

private val AMBIENT_LIGHT_COLORS1 = floatArrayOf(
    0.68f, 0.72f, 1.0f, 1.0f,
    0.98f, 0.76f, 0.88f, 1.0f,
    0.82f, 0.70f, 0.98f, 1.0f,
    0.60f, 0.78f, 1.0f, 1.0f,
)
private val AMBIENT_LIGHT_COLORS2 = floatArrayOf(
    0.98f, 0.75f, 0.85f, 1.0f,
    0.72f, 0.80f, 1.0f, 1.0f,
    0.90f, 0.72f, 0.98f, 1.0f,
    0.65f, 0.76f, 1.0f, 1.0f,
)
private val AMBIENT_LIGHT_COLORS3 = floatArrayOf(
    0.75f, 0.70f, 1.0f, 1.0f,
    0.98f, 0.74f, 0.82f, 1.0f,
    0.65f, 0.80f, 0.98f, 1.0f,
    0.96f, 0.78f, 0.92f, 1.0f,
)

private val AMBIENT_DARK_COLORS1 = floatArrayOf(
    0.2f, 0.06f, 0.88f, 0.4f,
    0.3f, 0.14f, 0.55f, 0.5f,
    0.0f, 0.64f, 0.96f, 0.5f,
    0.11f, 0.16f, 0.83f, 0.4f,
)
private val AMBIENT_DARK_COLORS2 = floatArrayOf(
    0.07f, 0.15f, 0.79f, 0.5f,
    0.62f, 0.21f, 0.67f, 0.5f,
    0.06f, 0.25f, 0.84f, 0.5f,
    0.0f, 0.2f, 0.78f, 0.5f,
)
private val AMBIENT_DARK_COLORS3 = floatArrayOf(
    0.58f, 0.3f, 0.74f, 0.4f,
    0.27f, 0.18f, 0.6f, 0.5f,
    0.66f, 0.26f, 0.62f, 0.5f,
    0.12f, 0.16f, 0.7f, 0.6f,
)

private const val SHADER_ASSET_PATH = "shaders/ambient_mesh_bg.agsl"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AmbientMeshShaderPainter(context: Context) {
    private val shaderSource: String = context.assets.open(SHADER_ASSET_PATH).bufferedReader().use { it.readText() }

    val shader: RuntimeShader by lazy {
        RuntimeShader(shaderSource).also { s ->
            s.setFloatUniform("uTranslateY", 0f)
            s.setFloatUniform("uNoiseScale", 1.5f)
            s.setFloatUniform("uPointRadiusMulti", 1f)
            s.setFloatUniform("uAlphaMulti", 1f)
            s.setFloatUniform("uAlphaOffset", 0.1f)
            s.setFloatUniform("uShadowOffset", 0.01f)
        }
    }

    val brush: Brush by lazy { ShaderBrush(shader) }
    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)

    fun updateResolution(width: Float, height: Float) {
        resolution[0] = width
        resolution[1] = height
        shader.setFloatUniform("uResolution", resolution)
    }

    fun updateBoundAndPreset(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
        isDark: Boolean,
    ) {
        val heightRatio = (logoHeight / totalHeight).coerceAtLeast(0.01f)
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
        shader.setFloatUniform("uBound", bound)

        shader.setFloatUniform("uPoints", AMBIENT_POINTS)
        shader.setFloatUniform("uPointOffset", if (isDark) 0.4f else 0.25f)
        shader.setFloatUniform("uLightOffset", if (isDark) 0.0f else 0.05f)
        shader.setFloatUniform("uSaturateOffset", if (isDark) 0.17f else 0.35f)
        shader.setFloatUniform("uShadowNoiseScale", 5.0f)
        shader.setFloatUniform("uShadowColorMulti", 0.35f)
        shader.setFloatUniform("uShadowColorOffset", 0.3f)
    }

    fun updateColors(colors: FloatArray) {
        shader.setFloatUniform("uColors", colors)
    }

    fun updateAnimTime(time: Float) {
        shader.setFloatUniform("uAnimTime", time)
    }
}

/**
 * Ambient mesh dynamic gradient background component strictly aligned with Cirno BgEffectBackground:
 * - Real dynamic fluid time tracking via withFrameNanos
 * - Smooth spring-based color interpolation cycle (5.0s on light, 8.0s on dark)
 * - Remains visible behind the entire scrolling About page
 */
@Composable
internal fun AmbientMeshBackground(
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val surface = MiuixTheme.colorScheme.surface
    val painter = remember(context) { AmbientMeshShaderPainter(context) }

    var animTime by remember { mutableFloatStateOf(0f) }
    val currentColors = remember { FloatArray(16) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            animTime = (now - start) / 1_000_000_000f
        }
    }

    val colorStage = remember { Animatable(0f) }
    LaunchedEffect(isDark) {
        val delayMillis = if (isDark) (8.0f * 500).toLong() else (5.0f * 500).toLong()
        var targetStage = 1f
        while (isActive) {
            delay(delayMillis.milliseconds)
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(surface)

        val logoHeight = size.height * 0.90f

        val stage = colorStage.value
        val base = stage.toInt()
        val fraction = stage - base

        val getColors = { index: Int ->
            if (isDark) {
                when (index % 4) {
                    0 -> AMBIENT_DARK_COLORS2
                    1 -> AMBIENT_DARK_COLORS1
                    2 -> AMBIENT_DARK_COLORS2
                    3 -> AMBIENT_DARK_COLORS3
                    else -> AMBIENT_DARK_COLORS2
                }
            } else {
                when (index % 4) {
                    0 -> AMBIENT_LIGHT_COLORS2
                    1 -> AMBIENT_LIGHT_COLORS1
                    2 -> AMBIENT_LIGHT_COLORS2
                    3 -> AMBIENT_LIGHT_COLORS3
                    else -> AMBIENT_LIGHT_COLORS2
                }
            }
        }

        val start = getColors(base)
        val end = getColors(base + 1)
        for (i in currentColors.indices) {
            currentColors[i] = start[i] + (end[i] - start[i]) * fraction
        }

        painter.updateResolution(size.width, size.height)
        painter.updateBoundAndPreset(logoHeight, size.height, size.width, isDark)
        painter.updateColors(currentColors)
        painter.updateAnimTime(animTime)

        drawRect(
            brush = painter.brush,
        )
    }
}
