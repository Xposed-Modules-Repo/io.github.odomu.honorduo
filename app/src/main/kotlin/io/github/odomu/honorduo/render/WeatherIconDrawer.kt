/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws sharp, procedural vector weather icons on Canvas directly with exact scale and ink color.
 */
internal object WeatherIconDrawer {

    enum class WeatherType {
        SUNNY, CLOUDY, OVERCAST, RAIN, THUNDER, SNOW, FOG, HAZE
    }

    private val STROKE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val FILL_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val PATH = Path()
    private val RECT = RectF()

    fun parseWeatherType(condition: String): WeatherType {
        return when {
            condition.contains("雷") -> WeatherType.THUNDER
            condition.contains("雪") -> WeatherType.SNOW
            condition.contains("雨") -> WeatherType.RAIN
            condition.contains("雾") -> WeatherType.FOG
            condition.contains("霾") || condition.contains("沙") || condition.contains("尘") -> WeatherType.HAZE
            condition.contains("阴") -> WeatherType.OVERCAST
            condition.contains("多云") -> WeatherType.CLOUDY
            condition.contains("晴") -> WeatherType.SUNNY
            else -> WeatherType.SUNNY
        }
    }

    fun drawWeatherIcon(
        c: Canvas,
        type: WeatherType,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
    ) {
        if (size <= 0f) return
        STROKE_PAINT.color = color
        STROKE_PAINT.strokeWidth = maxOf(1f, size * 0.1f)
        FILL_PAINT.color = color

        when (type) {
            WeatherType.SUNNY -> drawSunny(c, x, y, size)
            WeatherType.CLOUDY -> drawCloudy(c, x, y, size)
            WeatherType.OVERCAST -> drawOvercast(c, x, y, size)
            WeatherType.RAIN -> drawRain(c, x, y, size)
            WeatherType.THUNDER -> drawThunder(c, x, y, size)
            WeatherType.SNOW -> drawSnow(c, x, y, size)
            WeatherType.FOG -> drawFog(c, x, y, size)
            WeatherType.HAZE -> drawHaze(c, x, y, size)
        }
    }

    /**
     * The system font's glyph for [condition].
     *
     * The one place the symbol look is defined: the status-bar clock can pick it as
     * an alternative to the drawn icon, and the notification shade has nothing but
     * text to work with, so both read this map and stay in step.
     */
    fun glyphFor(condition: String): String = when {
        condition.contains("雷") -> "\u26C8"
        condition.contains("雪") -> "\u2744"
        condition.contains("雨") -> "\uD83C\uDF27"
        condition.contains("雾") -> "\uD83C\uDF2B"
        condition.containsAny("霾", "沙", "尘") -> "\uD83C\uDF2B"
        condition.contains("阴") -> "\u2601"
        condition.contains("多云") -> "\u26C5"
        else -> "\u2600"
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }

    private fun drawSunny(c: Canvas, x: Float, y: Float, size: Float) {
        val cx = x + size * 0.5f
        val cy = y + size * 0.5f
        val r = size * 0.22f
        STROKE_PAINT.style = Paint.Style.STROKE
        c.drawCircle(cx, cy, r, STROKE_PAINT)
        val rayStart = size * 0.32f
        val rayEnd = size * 0.44f
        for (i in 0 until 8) {
            val rad = Math.toRadians((i * 45).toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            c.drawLine(
                cx + rayStart * cos, cy + rayStart * sin,
                cx + rayEnd * cos, cy + rayEnd * sin,
                STROKE_PAINT,
            )
        }
    }

    private fun drawCloud(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, fill: Boolean) {
        PATH.reset()
        val left = cx - w * 0.5f
        val right = cx + w * 0.5f
        val bottom = cy + h * 0.5f
        val top = cy - h * 0.5f

        val rBottom = h * 0.35f
        PATH.moveTo(left + rBottom, bottom)
        PATH.lineTo(right - rBottom, bottom)
        RECT.set(right - rBottom * 2f, bottom - rBottom * 2f, right, bottom)
        PATH.arcTo(RECT, 90f, -120f)
        RECT.set(cx - w * 0.25f, top, cx + w * 0.35f, top + h * 0.9f)
        PATH.arcTo(RECT, -30f, -130f)
        RECT.set(left, bottom - rBottom * 2.2f, left + rBottom * 2.2f, bottom)
        PATH.arcTo(RECT, 200f, -120f)
        PATH.close()

        if (fill) {
            c.drawPath(PATH, FILL_PAINT)
        } else {
            STROKE_PAINT.style = Paint.Style.STROKE
            c.drawPath(PATH, STROKE_PAINT)
        }
    }

    private fun drawOvercast(c: Canvas, x: Float, y: Float, size: Float) {
        drawCloud(c, x + size * 0.5f, y + size * 0.52f, size * 0.85f, size * 0.52f, fill = false)
    }

    private fun drawCloudy(c: Canvas, x: Float, y: Float, size: Float) {
        val sunCx = x + size * 0.68f
        val sunCy = y + size * 0.32f
        val sunR = size * 0.16f
        STROKE_PAINT.style = Paint.Style.STROKE
        c.drawCircle(sunCx, sunCy, sunR, STROKE_PAINT)
        drawCloud(c, x + size * 0.44f, y + size * 0.58f, size * 0.72f, size * 0.46f, fill = false)
    }

    private fun drawRain(c: Canvas, x: Float, y: Float, size: Float) {
        drawCloud(c, x + size * 0.5f, y + size * 0.38f, size * 0.8f, size * 0.45f, fill = false)
        STROKE_PAINT.style = Paint.Style.STROKE
        val dropLen = size * 0.18f
        val dropSlant = size * 0.06f
        val y1 = y + size * 0.68f
        val y2 = y1 + dropLen
        val xStarts = floatArrayOf(x + size * 0.3f, x + size * 0.5f, x + size * 0.7f)
        for (xs in xStarts) {
            c.drawLine(xs, y1, xs - dropSlant, y2, STROKE_PAINT)
        }
    }

    private fun drawThunder(c: Canvas, x: Float, y: Float, size: Float) {
        drawCloud(c, x + size * 0.5f, y + size * 0.36f, size * 0.8f, size * 0.44f, fill = false)
        PATH.reset()
        val lx = x + size * 0.5f
        val ly = y + size * 0.56f
        PATH.moveTo(lx + size * 0.05f, ly)
        PATH.lineTo(lx - size * 0.08f, ly + size * 0.18f)
        PATH.lineTo(lx + size * 0.02f, ly + size * 0.18f)
        PATH.lineTo(lx - size * 0.06f, ly + size * 0.38f)
        STROKE_PAINT.style = Paint.Style.STROKE
        c.drawPath(PATH, STROKE_PAINT)
    }

    private fun drawSnow(c: Canvas, x: Float, y: Float, size: Float) {
        drawCloud(c, x + size * 0.5f, y + size * 0.38f, size * 0.8f, size * 0.45f, fill = false)
        val r = maxOf(1f, size * 0.05f)
        val yDot = y + size * 0.76f
        c.drawCircle(x + size * 0.32f, yDot, r, FILL_PAINT)
        c.drawCircle(x + size * 0.50f, yDot + size * 0.06f, r, FILL_PAINT)
        c.drawCircle(x + size * 0.68f, yDot, r, FILL_PAINT)
    }

    private fun drawFog(c: Canvas, x: Float, y: Float, size: Float) {
        STROKE_PAINT.style = Paint.Style.STROKE
        val line1Y = y + size * 0.30f
        val line2Y = y + size * 0.50f
        val line3Y = y + size * 0.70f
        c.drawLine(x + size * 0.15f, line1Y, x + size * 0.85f, line1Y, STROKE_PAINT)
        c.drawLine(x + size * 0.05f, line2Y, x + size * 0.95f, line2Y, STROKE_PAINT)
        c.drawLine(x + size * 0.20f, line3Y, x + size * 0.80f, line3Y, STROKE_PAINT)
    }

    private fun drawHaze(c: Canvas, x: Float, y: Float, size: Float) {
        STROKE_PAINT.style = Paint.Style.STROKE
        val y1 = y + size * 0.32f
        val y2 = y + size * 0.52f
        val y3 = y + size * 0.72f
        c.drawLine(x + size * 0.1f, y1, x + size * 0.4f, y1, STROKE_PAINT)
        c.drawLine(x + size * 0.55f, y1, x + size * 0.9f, y1, STROKE_PAINT)
        c.drawLine(x + size * 0.2f, y2, x + size * 0.8f, y2, STROKE_PAINT)
        c.drawLine(x + size * 0.1f, y3, x + size * 0.45f, y3, STROKE_PAINT)
        c.drawLine(x + size * 0.6f, y3, x + size * 0.9f, y3, STROKE_PAINT)
    }
}
