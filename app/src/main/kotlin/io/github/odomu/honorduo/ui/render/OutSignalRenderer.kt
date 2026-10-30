/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.ui.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer.drawTimeGlyphs
import io.github.odomu.honorduo.ui.render.GlyphInk.FILL
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT_INK
import io.github.odomu.honorduo.ui.render.GlyphInk.applyWeight
import io.github.odomu.honorduo.ui.render.GlyphInk.drawType
import io.github.odomu.honorduo.ui.render.GlyphInk.isDarkBackground
import io.github.odomu.honorduo.ui.render.GlyphInk.measureType
import io.github.odomu.honorduo.ui.render.GlyphInk.withAlpha
import io.github.odomu.honorduo.ui.render.geometry.RectGeometry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The out-of-ring signal rows: the bars, the labels beside them and the activity
 * arrows, drawn in the status bar's own view rather than inside the glyph.
 */
internal object OutSignalRenderer {

    /**
     * Scratch for one capsule bar; rewritten every draw, never kept.
     *
     * The out-of-ring reading is painted on a view of its own rather than inside the
     * glyph's `onDraw`, but never reentrantly: it is one view per battery container,
     * and a container paints its children one at a time.
     */
    private val OUT_BAR = RectF()


    /** Scratch path for the out-of-ring activity arrows; same single-draw rule. */

    /**
     * Resolves the two readings the out-of-ring glyph draws: the capsule row, and
     * the dot row beneath it.
     *
     * Row one is the first SIM that answered and row two the second, which is also
     * why the dot row disappears when only one SIM answered. Four ascending bars is
     * what a lone reading looks like, so a single SIM must not be split across two
     * rows just to fill the space.
     *
     * [dataSimOnly] collapses the pair the other way: the reading that matters is
     * the data SIM's, and the other one is dropped whether or not it answered.
     *
     * When telephony never answered, the aggregate reading the glyph itself uses is
     * the last resort, so a missing subscription list does not leave the out-of-ring
     * glyph blank while the in-ring one still reads a level.
     *
     * @param out receives `{barLevel, dotLevel}`; a dot level of `-1` means "no
     *   second row", and a bar level of `-1` means "nothing to read"
     */
    fun outSignalLevels(
        dataSimOnly: Boolean, mobileLevel: Int, slotLevels: IntArray?,
        dataSlot: Int, sims: Int, out: IntArray,
    ) {
        var slot0 = -1
        var slot1 = -1
        if (slotLevels != null && slotLevels.size >= StatusState.SIM_SLOTS) {
            slot0 = slotLevels[0]
            slot1 = slotLevels[1]
        }
        val firstAnswered = if (slot0 >= 0) slot0 else slot1
        var bars: Int
        var dots = -1
        if (dataSimOnly) {
            bars = firstAnswered
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1
            }
        } else if (slot0 >= 0 && sims >= 2) {
            // The second card's row is drawn whenever that card exists, even unlit.
            bars = slot0
            dots = maxOf(0, slot1)
        } else {
            bars = firstAnswered
        }
        if (bars < 0) {
            bars = mobileLevel
        }
        out[0] = bars
        out[1] = dots
    }


    /** The clock sheet's sample: fitted as one block according to offsets and gaps. */
    internal fun drawPreviewClock(
        c: Canvas, width: Int, height: Int, time: String,
        date: String, weekday: String, lunar: String, weather: String,
        a: StatusAppearance, fg: Int,
    ) {
        applyWeight(a.clockWeight)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val isDark = isDarkBackground(fg)
        val wkColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight
        val dtColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight
        val lnColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight
        val wtColor = if (isDark) a.clockWeatherColorOnDark else a.clockWeatherColorOnLight
        val wkDrawColor = if (wkColor != 0) wkColor else fg
        val dtDrawColor = if (dtColor != 0) dtColor else fg
        val lnDrawColor = if (lnColor != 0) lnColor else fg
        val wtDrawColor = if (wtColor != 0) wtColor else fg
        val inkBounds = TEXT_INK

        val isBalanced = a.clockStyle == Prefs.CLOCK_STYLE_BALANCED
        var timeSize = height * 0.45f * userScale
        var lineSize = timeSize * PREVIEW_DATE_SCALE
        val lunarGap = a.clockLunarGap.toFloat()
        val rowGap = a.clockRowGap.toFloat()
        val topOffsetX = a.clockTopOffsetX.toFloat()
        val bottomOffsetX = a.clockBottomOffsetX.toFloat()

        val previewCondition = if (weather.contains(" ")) weather.substringBefore(" ") else weather
        val previewTemp = if (weather.contains(" ")) weather.substringAfter(" ") else ""

        fun vectorWeather(): Boolean =
            a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON || a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON_TEXT

        fun symbolWeather(): Boolean =
            a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL || a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL_TEXT

        fun weatherLabel(): String {
            if (weather.isEmpty()) return ""
            if (a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON || a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL) {
                return previewTemp.ifEmpty { weather }
            }
            return weather
        }

        fun weatherWidth(sz: Float): Float {
            val txt = weatherLabel()
            if (txt.isEmpty()) return 0f
            TEXT.textSize = sz
            val iconW = when {
                vectorWeather() -> sz * RectGeometry.WEATHER_ICON_SCALE + sz * 0.25f
                symbolWeather() -> TEXT.measureText(WeatherIconDrawer.glyphFor(previewCondition)) + sz * 0.25f
                else -> 0f
            }
            return iconW + TEXT.measureText(txt)
        }

        fun drawWeather(startX: Float, yBaseline: Float, sz: Float) {
            val txt = weatherLabel()
            if (txt.isEmpty()) return
            var x = startX
            TEXT.textSize = sz
            TEXT.getTextBounds("0", 0, 1, inkBounds)
            val inkCentre = yBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
            when {
                vectorWeather() -> {
                    val iconSz = sz * RectGeometry.WEATHER_ICON_SCALE
                    WeatherIconDrawer.drawWeatherIcon(
                        c, WeatherIconDrawer.parseWeatherType(previewCondition),
                        x, inkCentre - iconSz * 0.5f, iconSz, wtDrawColor,
                    )
                    x += iconSz + sz * 0.25f
                }
                symbolWeather() -> {
                    val glyph = WeatherIconDrawer.glyphFor(previewCondition)
                    TEXT.color = wtDrawColor
                    c.drawText(glyph, x, yBaseline, TEXT)
                    x += TEXT.measureText(glyph) + sz * 0.25f
                }
            }
            TEXT.color = wtDrawColor
            c.drawText(txt, x, yBaseline, TEXT)
        }

        if (isBalanced) {
            val step = lineSize * 0.28f
            val wordStep = lineSize * 0.16f
            val hasTop = date.isNotEmpty() || weekday.isNotEmpty() || weather.isNotEmpty()
            TEXT.textSize = lineSize
            val dtW = if (date.isEmpty()) 0f else TEXT.measureText(date)
            val wkW = if (weekday.isEmpty()) 0f else TEXT.measureText(weekday)
            val wtW = weatherWidth(lineSize)
            val topW = rowWidth(floatArrayOf(dtW, wkW, wtW), wordStep)

            val lnW = if (lunar.isEmpty()) 0f else TEXT.measureText(lunar)
            TEXT.textSize = timeSize
            val tmW = TEXT.measureText(time)
            val botW = rowWidth(floatArrayOf(lnW, tmW), step)

            val widest = max(topW, botW)
            val clear = width * 0.9f
            if (widest > clear) {
                val scale = clear / widest
                timeSize *= scale
                lineSize *= scale
            }
            val gap = rowGap
            TEXT.textSize = timeSize
            val timeInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            TEXT.textSize = lineSize
            val lineInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            val totalH = (if (hasTop) lineInk + gap else 0f) + timeInk
            var rowTop = height / 2f - totalH / 2f
            val cx = width / 2f

            if (hasTop) {
                TEXT.textAlign = Paint.Align.LEFT
                val baseline = rowTop - TEXT.fontMetrics.ascent
                var x = cx - topW / 2f + topOffsetX
                if (date.isNotEmpty()) {
                    TEXT.color = dtDrawColor
                    c.drawText(date, x, baseline, TEXT)
                    x += TEXT.measureText(date) + wordStep
                }
                if (weekday.isNotEmpty()) {
                    TEXT.color = wkDrawColor
                    c.drawText(weekday, x, baseline, TEXT)
                    x += TEXT.measureText(weekday) + wordStep
                }
                drawWeather(x, baseline, lineSize)
                rowTop += lineInk + gap
            }

            // Lunar then Time: one line, the time at its own size, the lunar centred on
            // the time's digits.
            TEXT.textSize = timeSize
            val timeBaseline = rowTop - TEXT.fontMetrics.ascent
            var botX = cx - botW / 2f + bottomOffsetX
            if (lunar.isNotEmpty()) {
                TEXT.getTextBounds(time, 0, time.length, inkBounds)
                val timeCentre = timeBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
                TEXT.textSize = lineSize
                TEXT.getTextBounds(lunar, 0, lunar.length, inkBounds)
                val lunarBaseline = timeCentre - (inkBounds.top + inkBounds.bottom) * 0.5f
                TEXT.color = lnDrawColor
                c.drawText(lunar, botX, lunarBaseline, TEXT)
                botX += TEXT.measureText(lunar) + step
            }
            TEXT.textSize = timeSize
            TEXT.color = fg
            drawTimeGlyphs(
                c, TEXT, time, botX, timeBaseline,
                if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight, fg,
            )
        } else {
            val step = lineSize * 0.2f
            val wordStep = lineSize * 0.16f
            TEXT.textSize = lineSize
            val wkW = if (weekday.isEmpty()) 0f else TEXT.measureText(weekday)
            val dtW = if (date.isEmpty()) 0f else TEXT.measureText(date)
            val effectiveLunarGap = if (date.isNotEmpty() && lunar.isNotEmpty()) max(lunarGap, wordStep) else 0f
            val lnW = if (lunar.isEmpty()) 0f else TEXT.measureText(lunar)
            val wtW = weatherWidth(lineSize)
            TEXT.textSize = timeSize
            val tmW = TEXT.measureText(time)
            val topW = rowWidth(floatArrayOf(tmW, wtW), step)
            var botW = dtW
            if (lnW > 0f) {
                if (botW > 0f) botW += effectiveLunarGap
                botW += lnW
            }
            if (wkW > 0f) {
                if (botW > 0f) botW += wordStep
                botW += wkW
            }
            val widest = max(topW, botW)
            val clear = width * 0.9f
            if (widest > clear) {
                val scale = clear / widest
                timeSize *= scale
                lineSize *= scale
            }
            val gap = rowGap
            TEXT.textSize = timeSize
            val timeInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            TEXT.textSize = lineSize
            val lineInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            val hasBottom = date.isNotEmpty() || lunar.isNotEmpty() || weekday.isNotEmpty()
            val totalH = timeInk + (if (hasBottom) lineInk + gap else 0f)
            var top = height / 2f - totalH / 2f
            val cx = width / 2f

            // Row 1: Time + Weather, the caption centred on the time's ink.
            TEXT.textAlign = Paint.Align.LEFT
            var currX = cx - topW / 2f + topOffsetX
            val timeBaseline = top - TEXT.fontMetrics.ascent
            TEXT.textSize = timeSize
            TEXT.getTextBounds("0", 0, 1, inkBounds)
            val timeInkCentre = timeBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
            val label = weatherLabel()
            var captionBaseline = timeBaseline
            if (label.isNotEmpty()) {
                TEXT.textSize = lineSize
                TEXT.getTextBounds("0", 0, 1, inkBounds)
                captionBaseline = timeInkCentre - (inkBounds.top + inkBounds.bottom) * 0.5f
            }
            TEXT.textSize = timeSize
            TEXT.color = fg
            drawTimeGlyphs(
                c, TEXT, time, currX, timeBaseline,
                if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight, fg,
            )
            currX += TEXT.measureText(time) + step
            drawWeather(currX, captionBaseline, lineSize)

            // Row 2: Date + Lunar + Weekday.
            if (hasBottom) {
                top += timeInk + gap
                var botX = cx - botW / 2f + bottomOffsetX
                val yBaseline = top - TEXT.fontMetrics.ascent
                TEXT.textSize = lineSize
                if (date.isNotEmpty()) {
                    TEXT.color = dtDrawColor
                    c.drawText(date, botX, yBaseline, TEXT)
                    botX += TEXT.measureText(date) + (if (lunar.isNotEmpty()) effectiveLunarGap else wordStep)
                }
                if (lunar.isNotEmpty()) {
                    TEXT.color = lnDrawColor
                    c.drawText(lunar, botX, yBaseline, TEXT)
                    botX += TEXT.measureText(lunar) + wordStep
                }
                if (weekday.isNotEmpty()) {
                    TEXT.color = wkDrawColor
                    c.drawText(weekday, botX, yBaseline, TEXT)
                }
            }
        }
    }


    /** A row's ink width: measured parts plus one gap between each adjacent pair. */
    private fun rowWidth(parts: FloatArray, step: Float): Float {
        var sum = 0f
        var drawn = 0
        for (w in parts) {
            if (w <= 0f) continue
            if (drawn > 0) sum += step
            sum += w
            drawn++
        }
        return sum
    }


    /** The preview's date line, as a share of the time's size. */
    private const val PREVIEW_DATE_SCALE = 0.74f


    /**
     * Whether the reading is drawn as the compact two-row style.
     *
     * Only a two-card reading has rows to stack. With one card this style has a
     * single row to work with, and its short, wide proportions read as a squat block
     * beside the dot style. A lone card therefore draws exactly the dot style's bars,
     * whichever style is selected: the styles differ in how they show the *second*
     * card, not in how they show the first.
     */
    fun rowsStyle(a: StatusAppearance?, dual: Boolean): Boolean =
        dual && a != null && a.stackStyle == Prefs.STACK_STYLE_ROWS

    private const val LONE_ROW_FILL = 0.74f

    private val ACTIVITY_UP = Path()

    private val ACTIVITY_DOWN = Path()


    /**
     * The pixel height of the reading's box at the user's [sizeDp], on a display of
     * the given [density].
     *
     * Deliberately independent of any view: the reading's size used to be a share of
     * the row it stood in, so the system laying that row out taller made the reading
     * grow with it. A dp is fixed.
     */
    fun outSignalHeight(a: StatusAppearance?, sizeDp: Int, density: Float, dual: Boolean): Int {
        if (sizeDp <= 0 || density <= 0f) {
            return 0
        }
        // Two rows and their gap cost height, so the bars get a boost at the same setting.
        if (rowsStyle(a, dual)) {
            return max(1, (sizeDp * density * 1.58f).roundToInt())
        }
        return max(1, (sizeDp * density).roundToInt())
    }


    /**
     * The pixel width of a reading [height] pixels tall, including the type labels.
     *
     * The label column is part of the reading, so the view has to be that much wider:
     * sizing it for the bars alone would clip the label off the left edge.
     */
    fun outSignalWidth(
        height: Int, a: StatusAppearance?, type: String?, type2: String?,
        dual: Boolean = true,
    ): Int {
        if (height <= 0) {
            return 0
        }
        val inkW = outSignalInkWidth(a, type, type2, dual)
        val inkH = outSignalInkHeight(a, dual)
        val fill = if (dual) 1f else LONE_ROW_FILL
        return max(1, (height * fill * inkW / inkH).roundToInt())
    }


    /**
     * Column pitch for a style.
     *
     * The two looks are not proportioned alike in the reference: the dot style's bars
     * are spaced by 0.85 of their width, the compact two-row style's by 1.56.
     */
    private fun outPitch(a: StatusAppearance?, dual: Boolean = true): Float {
        val rows = rowsStyle(a, dual)
        return if (rows) {
            // The compact style's own reference: 1.6dp bars on a 0.9dp gap across a
            // 6.2dp row, i.e. everything is a fixed fraction of the row height.
            RectGeometry.STACK_ROW_H * (ROW_BAR + ROW_GAP)
        } else {
            // The reference's own pitch: 39-unit bars on a 28-unit gap.
            RectGeometry.STACK_PITCH
        }
    }


    /** Columns per row: 4 (default) or 5, adapting proportions harmoniously. */
    private fun outColumns(a: StatusAppearance?): Int = a?.signalColumns?.coerceIn(4, 5) ?: 4


    private fun outBarHeights(columns: Int): FloatArray =
        if (columns == 5) RectGeometry.STACK_BAR_H_5 else RectGeometry.STACK_BAR_H


    /** Map signal level (0..4 or 0..5) to lit bar count according to column style. */
    internal fun levelMapped(level: Int, columns: Int): Int {
        if (level <= 0) return 0
        if (columns == 5) {
            return when (level) {
                1 -> 1
                2 -> 2
                3 -> 4
                4 -> 5
                else -> level.coerceIn(0, 5)
            }
        }
        return level.coerceIn(0, 4)
    }


    private fun outBarWidth(a: StatusAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            RectGeometry.STACK_ROW_H * ROW_BAR
        } else {
            RectGeometry.STACK_BAR_W
        }


    private fun outLabelGap(a: StatusAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            RectGeometry.STACK_ROW_H * ROW_LABEL_GAP
        } else {
            RectGeometry.STACK_LABEL_GAP
        }


    /**
     * Proportions for the two-row style: a 1.6dp bar on a 0.9dp gap, on a 6.2dp row,
     * plus a 2dp label gap and a 7.5sp label.
     */
    private const val ROW_BAR = 0.34f

    private const val ROW_GAP = 0.18f

    private const val ROW_LABEL_GAP = 0.24f


    /**
     * The gap between the primary and secondary parts of the reading, in design units.
     */
    private fun rowGap(a: StatusAppearance, baseInk: Float): Float =
        RectGeometry.STACK_DOT_GAP * (a.outSignalMargin.coerceAtLeast(0) / Prefs.DEF_OUT_SIGNAL_MARGIN.toFloat())


    // --- DOTS STYLE CONSTANTS ---
    private const val DOTS_ACTIVITY_ARROW_W = 1.35f

    private const val DOTS_ACTIVITY_GAP = 0.6f

    private const val DOTS_ACTIVITY_GAP_SHARE = 0.25f

    /** Dedicated activity arrow height for single-card, independent from dual-card */
    private const val SINGLE_ACTIVITY_PAIR_H = 44f


    private fun dotsActivityBlockWidth(barW: Float): Float =
        barW * (DOTS_ACTIVITY_ARROW_W + DOTS_ACTIVITY_GAP)


    private fun rowsGap(a: StatusAppearance): Float =
        RectGeometry.STACK_ROW_H * 0.12f


    // --- ROWS STYLE CONSTANTS ---
    private const val ROWS_ACTIVITY_ARROW_W = 1.15f

    private const val ROWS_ACTIVITY_GAP = 0.5f

    private const val ROWS_ACTIVITY_GAP_SHARE = 0.22f


    private fun rowsActivityBlockWidth(barW: Float): Float =
        barW * (ROWS_ACTIVITY_ARROW_W + ROWS_ACTIVITY_GAP)


    /** Width of the bars alone, in design units: what the label can overhang. */
    fun outSignalBarsWidth(a: StatusAppearance?, dual: Boolean = true): Float {
        val barsW = outPitch(a, dual) * (outColumns(a) - 1) + outBarWidth(a, dual)
        val actW = if (a?.showMobileActivity == true) {
            if (rowsStyle(a, dual)) {
                rowsActivityBlockWidth(outBarWidth(a, dual))
            } else {
                dotsActivityBlockWidth(outBarWidth(a, dual))
            }
        } else 0f
        return barsW + actW
    }


    /** The reading's own width in design units, label column included. */
    fun outSignalInkWidth(
        a: StatusAppearance?, type: String?, type2: String?, dual: Boolean = true,
    ): Float {
        val bars = outSignalBarsWidth(a, dual)
        if (a == null || !a.labelInReading()) {
            return bars
        }
        if (labelAbove(a)) {
            // The label is centred over bar 1, so the box grows only by its left overhang.
            return bars + outLabelOverhang(a, type, type2, dual)
        }
        val size = aloneCaptionSize(a, dual)
        val second = if (dual) type2 else null
        val widest = max(
            labelWidth(type, size, a.typeSuffixScale),
            labelWidth(second, size, a.typeSuffixScale),
        )
        return if (widest <= 0f) bars else widest + outLabelGap(a, dual) + bars
    }


    /**
     * How far the caption hangs past the bars' left edge, in design units.
     *
     * Zero when the caption already fits over bar 1, which is the usual case: the bars
     * are as wide as the reading, and the caption only borrows from that width.
     */
    fun outLabelOverhang(
        a: StatusAppearance?, type: String?, type2: String?, dual: Boolean = true,
    ): Float {
        if (a == null || !labelAbove(a)) {
            return 0f
        }
        val size = outCaptionSize(a, dual)
        val second = if (dual) type2 else null
        val widest = max(
            labelWidth(type, size, a.typeSuffixScale),
            labelWidth(second, size, a.typeSuffixScale),
        )
        if (widest <= 0f) {
            return 0f
        }
        val barW = outBarWidth(a, dual)
        // The caption is NOT centred on bar 1: its 'G' is (see typeLabelXOverBar1), so the
        // ink reaches `prefix + half of the rest` left of bar 1's centre. Reserving half
        // the whole caption instead left the prefix hanging outside the box, where the
        // view clipped it - which only showed while the activity arrows were off, because
        // their width pushed the reading far enough right to hide the overflow.
        val leftExtent = max(
            labelLeftExtent(type, size, a.typeSuffixScale),
            labelLeftExtent(second, size, a.typeSuffixScale),
        )
        val leftOverhang = leftExtent - barW * 0.5f
        val actW = if (a.showMobileActivity) {
            if (rowsStyle(a, dual)) rowsActivityBlockWidth(barW) else dotsActivityBlockWidth(barW)
        } else 0f
        return max(0f, leftOverhang - actW)
    }


    fun outSignalInkHeight(a: StatusAppearance?, dual: Boolean = true): Float {
        if (a != null && rowsStyle(a, dual)) {
            val rowH = RectGeometry.STACK_ROW_H
            return rowH * 2f + rowsGap(a)
        }
        val dotD = RectGeometry.STACK_DOT_D * ((a?.outDotSize ?: 100) / 100f)
        val barsH = RectGeometry.STACK_BAR_H[RectGeometry.STACK_COLUMNS - 1]
        if (!dual) return barsH
        val gap = if (a != null) rowGap(a, barsH + dotD) else RectGeometry.STACK_DOT_GAP
        return barsH + gap + dotD
    }


    /**
     * Caption size in design units when the network type is shown beside the reading (ALONE mode).
     *
     * In ALONE mode, the baseline font size is 12dp on screen (by default), contrasting with
     * the 14dp default used when the caption is positioned directly above the signal bar (BESIDE mode).
     * It scales proportionally whenever the user adjusts the outTypeSize setting (12dp * outTypeSize / 14).
     */
    private fun aloneCaptionSize(a: StatusAppearance, dual: Boolean = true): Float {
        val totalDesignH = if (rowsStyle(a, dual)) {
            val rowH = RectGeometry.STACK_ROW_H
            rowH * 2f + rowsGap(a)
        } else {
            val columns = outColumns(a)
            val barHeights = outBarHeights(columns)
            val barsH = barHeights[columns - 1]
            val dotScale = a.outDotSize / 100f
            val baseDotD = if (columns == 5) 31f else RectGeometry.STACK_DOT_D
            val dotD = baseDotD * dotScale
            val gap = rowGap(a, barsH + dotD)
            barsH + gap + dotD
        }
        // Maps 14dp setting to visual 12dp size (12/14 scale ratio) so the default setting renders the ideal size
        val scaleFactor = a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat()
        return totalDesignH * 0.55f * scaleFactor
    }


    private fun outCaptionSize(a: StatusAppearance, dual: Boolean = true): Float =
        outSignalLabelSize(a, dual) * (a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat())


    private fun outSignalLabelSize(a: StatusAppearance, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            RectGeometry.STACK_ROW_H * 0.72f
        } else {
            RectGeometry.STACK_BAR_H[RectGeometry.STACK_COLUMNS - 1] * LABEL_TO_BAR
        }


    private const val LABEL_TO_BAR = 0.65f


    private fun labelAbove(a: StatusAppearance): Boolean =
        a.stackTypeShown() && a.stackLabel == Prefs.STACK_LABEL_BESIDE


    /**
     * One card's network type, in the reading's label column.
     *
     * Left-aligned on a shared edge and centred on its own row, so a two-card reading
     * shows both types in one column rather than one label floating beside the pair.
     * The size is the same one the BESIDE caption uses, so switching the option does not
     * change how large the type is.
     */
    private fun drawColumnLabel(
        c: Canvas, text: String?, left: Float, targetY: Float, scale: Float,
        fg: Int, a: StatusAppearance, dual: Boolean,
        align: Int = 0, // 0 = center on targetY, 1 = top edge on targetY, -1 = bottom edge on targetY
    ) {
        if (text.isNullOrEmpty() || scale <= 0f) {
            return
        }
        val size = aloneCaptionSize(a, dual) * scale
        if (size <= 0f) {
            return
        }
        applyWeight(max(a.typeWeight, LABEL_WEIGHT))
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
        val baseline = when (align) {
            1 -> targetY - TEXT_INK.top // top edge of drawn ink touches targetY
            -1 -> targetY - TEXT_INK.bottom // bottom edge of drawn ink touches targetY
            else -> targetY - (TEXT_INK.top + TEXT_INK.bottom) * 0.5f // center of drawn ink aligns with targetY
        }
        val ink = measureType(text, size, a.typeSuffixScale)
        drawType(c, text, left + ink * 0.5f, baseline, size, fg, a)
    }


    /**
     * How far a caption's ink reaches LEFT of the bar its 'G' is centred on.
     *
     * [labelWidth] is the caption's total width; the placement puts the 'G' - not the
     * caption's centre - over bar 1, so the prefix hangs that much further left.
     */
    private fun labelLeftExtent(text: String?, size: Float, suffixScale: Int): Float {
        if (text.isNullOrEmpty() || size <= 0f) {
            return 0f
        }
        val g = text.indexOf('G')
        val prefix = if (g > 0) text.substring(0, g) else ""
        val total = labelWidth(text, size, suffixScale)
        val prefixW = if (prefix.isEmpty()) 0f else {
            applyWeight(LABEL_WEIGHT)
            measureType(prefix, size, 100)
        }
        return prefixW + (total - prefixW) * 0.5f
    }


    fun labelWidth(text: String?, size: Float, suffixScale: Int = 100): Float {
        if (text.isNullOrEmpty() || size <= 0f) {
            return 0f
        }
        applyWeight(LABEL_WEIGHT)
        return measureType(text, size, suffixScale)
    }


    private const val LABEL_WEIGHT = 700


    /** Floor for the idle arrows: the track alpha alone renders them nearly invisible. */
    private const val ACTIVITY_IDLE_ALPHA = 165


    /**
     * Activity arrows for the DOTS style:
     * - Exactly spans the height of the first dual-dot (Bar 1 + Dot 1)
     * - Top of up arrow aligns with top of Bar 1
     * - Bottom of down arrow aligns with bottom of Dot 1
     */
    private fun drawDotsActivityArrows(
        c: Canvas, cx: Float, top: Float, bottom: Float, scale: Float,
        barWDesign: Float, fg: Int, inactive: Int, activeIn: Boolean, activeOut: Boolean,
    ) {
        val w = barWDesign * DOTS_ACTIVITY_ARROW_W * scale
        val pairH = bottom - top
        val gap = pairH * DOTS_ACTIVITY_GAP_SHARE
        val h = max(0f, (pairH - gap) * 0.5f)
        val halfW = w / 2f

        // Up arrow: top at `top` (aligned with top of Bar 1)
        val upTop = top
        val upBottom = upTop + h
        ACTIVITY_UP.reset()
        ACTIVITY_UP.moveTo(cx, upTop)
        ACTIVITY_UP.lineTo(cx - halfW, upBottom)
        ACTIVITY_UP.lineTo(cx + halfW, upBottom)
        ACTIVITY_UP.close()
        FILL.color = if (activeOut) fg else inactive
        c.drawPath(ACTIVITY_UP, FILL)

        // Down arrow: tip lands on `bottom` (aligned with bottom of Dot 1)
        val downTop = bottom - h
        val downTip = bottom + 0.5f
        ACTIVITY_DOWN.reset()
        ACTIVITY_DOWN.moveTo(cx, downTip)
        ACTIVITY_DOWN.lineTo(cx - halfW, downTop)
        ACTIVITY_DOWN.lineTo(cx + halfW, downTop)
        ACTIVITY_DOWN.close()
        FILL.color = if (activeIn) fg else inactive
        c.drawPath(ACTIVITY_DOWN, FILL)
    }


    /**
     * Activity arrows for the ROWS style:
     * - Bottom-aligned with the first signal bar (row1Baseline)
     */
    private fun drawRowsActivityArrows(
        c: Canvas, cx: Float, baseline: Float, bar1H: Float, scale: Float,
        barWDesign: Float, fg: Int, inactive: Int, activeIn: Boolean, activeOut: Boolean,
    ) {
        val w = barWDesign * ROWS_ACTIVITY_ARROW_W * 1.15f * scale
        val pairH = 66f * scale
        val gap = pairH * ROWS_ACTIVITY_GAP_SHARE
        val h = max(0f, (pairH - gap) * 0.5f)
        val halfW = w / 2f

        // Down arrow: bottom tip lands on baseline
        val downTip = baseline + 0.5f
        val downTop = baseline - h
        ACTIVITY_DOWN.reset()
        ACTIVITY_DOWN.moveTo(cx, downTip)
        ACTIVITY_DOWN.lineTo(cx - halfW, downTop)
        ACTIVITY_DOWN.lineTo(cx + halfW, downTop)
        ACTIVITY_DOWN.close()
        FILL.color = if (activeIn) fg else inactive
        c.drawPath(ACTIVITY_DOWN, FILL)

        // Up arrow: above down arrow
        val upBottom = downTop - gap
        val upTop = upBottom - h
        ACTIVITY_UP.reset()
        ACTIVITY_UP.moveTo(cx, upTop)
        ACTIVITY_UP.lineTo(cx - halfW, upBottom)
        ACTIVITY_UP.lineTo(cx + halfW, upBottom)
        ACTIVITY_UP.close()
        FILL.color = if (activeOut) fg else inactive
        c.drawPath(ACTIVITY_UP, FILL)
    }


    /**
     * Shifts the caption so that the letter 'G' is centered directly over Bar 1.
     */
    private fun typeLabelXOverBar1(type: String?, bar1Center: Float, size: Float): Float {
        if (type.isNullOrEmpty()) return bar1Center
        val gIdx = type.indexOf('G')
        if (gIdx <= 0) return bar1Center
        val prefix = type.substring(0, gIdx)
        TEXT.textSize = size
        val prefixW = TEXT.measureText(prefix)
        return bar1Center - prefixW * 0.5f
    }


    /**
     * The primary reading: four signal bars on top, the secondary card underneath as
     * a dot row or a second row of bars.
     *
     * Independent of the ring's arrangement: the row stands in the status bar, in the
     * reference image's 350x350 units, and neither style borrows from the other.
     */
    fun drawOutSignal(
        c: Canvas, width: Int, height: Int, bars: Int, dots: Int,
        fg: Int, a: StatusAppearance,
        type: String? = null, type2: String? = null,
    ): Boolean {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false
        }
        if (rowsStyle(a, dots >= 0)) {
            return drawStackRows(c, width, height, bars, dots, fg, a, type, type2)
        }
        val dotRow = dots >= 0
        val columns = outColumns(a)
        val barHeights = outBarHeights(columns)
        val pitch = outPitch(a, dotRow)
        val barRun = outSignalBarsWidth(a, dotRow)
        val inkW = outSignalInkWidth(a, type, type2, dotRow)
        val labelW = inkW - barRun
        val dotScale = a.outDotSize / 100f
        val baseDotD = if (columns == 5) 31f else RectGeometry.STACK_DOT_D
        val dotD = baseDotD * dotScale
        val barsH = barHeights[columns - 1]
        val gap = if (dotRow) rowGap(a, barsH + dotD) else 0f
        val inkH = if (dotRow) barsH + gap + dotD else barsH
        val fitHeight = height * (if (dotRow) 1f else LONE_ROW_FILL)
        val scale = min(width / inkW, fitHeight / inkH)
        if (scale <= 0f) {
            return false
        }
        val x0 = (width - inkW * scale) / 2f
        val inkTop = (height - inkH * scale) / 2f
        val above = labelAbove(a)
        val barsOffset = if (above) outLabelOverhang(a, type, type2, dotRow) * scale else 0f
        val barWDesign = outBarWidth(a, dotRow)
        val barW = barWDesign * scale
        val actTotal = if (a.showMobileActivity) dotsActivityBlockWidth(barWDesign) * scale else 0f

        // Bars start after barsOffset plus actTotal
        // Where the bars start. BESIDE reserves the caption's overhang on the left; ALONE
        // puts the bars first and reserves the label column on the RIGHT, so the column
        // sits past the reading the way the standalone label used to.
        val barsX = when {
            above -> x0 + barsOffset + actTotal
            a.labelInReading() -> x0 + actTotal
            else -> x0 + labelW * scale + actTotal
        }
        val actCx = if (a.showMobileActivity) {
            barsX - barWDesign * (DOTS_ACTIVITY_GAP + DOTS_ACTIVITY_ARROW_W * 0.5f) * scale
        } else 0f
        val baseline = inkTop + barsH * scale

        // 1. Network type: letter 'G' aligned directly with the first signal bar (Bar 1)
        val bar1Center = barsX + barW * 0.5f
        if (above && !type.isNullOrEmpty()) {
            val capSize = outCaptionSize(a, dotRow) * scale
            val labelX = typeLabelXOverBar1(type, bar1Center, capSize)
            drawOutLabel(
                c, type, labelX,
                inkTop + 4f * scale, scale, fg, a, dual = dotRow, hang = true,
            )
        }

        // 2. The type column, when the setting asks for it beside the reading: one label
        // per card, each centred on its own row (bars above, dots below).
        if (!above && a.labelInReading()) {
            val colLeft = barsX + barRun * scale - actTotal + outLabelGap(a, dotRow) * scale
            val signalTop = baseline - barsH * scale
            val signalBottom = if (dotRow) baseline + (gap + dotD) * scale else baseline
            if (dotRow) {
                // Dual cards: Top label aligns top edge with signal top; bottom label aligns bottom edge with dot bottom
                drawColumnLabel(c, type, colLeft, signalTop, scale, fg, a, true, align = 1)
                drawColumnLabel(c, type2, colLeft, signalBottom, scale, fg, a, true, align = -1)
            } else {
                // Single card: center vertically on the signal cluster
                val midY = (signalTop + signalBottom) * 0.5f
                drawColumnLabel(c, type, colLeft, midY, scale, fg, a, false, align = 0)
            }
        }

        // 3. Activity arrows: independent parameters for dual-card vs single-card
        val actBottom = if (dotRow) baseline + (gap + dotD) * scale else baseline
        val actTop = if (dotRow) {
            // Dual card with dots: height matches Bar 1 + Dot 1
            baseline - barHeights[0] * scale
        } else {
            // Single card: dedicated independent height
            actBottom - SINGLE_ACTIVITY_PAIR_H * scale
        }
        if (a.showMobileActivity) {
            val actIn = StatusState.sDataActivity == 1 || StatusState.sDataActivity == 3
            val actOut = StatusState.sDataActivity == 2 || StatusState.sDataActivity == 3
            val actIdle = withAlpha(fg, maxOf(a.trackAlpha, ACTIVITY_IDLE_ALPHA))
            drawDotsActivityArrows(
                c, actCx, actTop, actBottom, scale, barWDesign,
                fg, actIdle, actIn, actOut,
            )
        }

        val radius = barW / 2f
        val dotR = dotD * scale / 2f
        val dotCy = baseline + (gap + dotD / 2f) * scale
        val inactive = withAlpha(fg, a.trackAlpha)
        for (i in 0 until columns) {
            val left = barsX + i * pitch * scale
            FILL.color = if (i < levelMapped(bars, columns)) fg else inactive
            OUT_BAR.set(
                left, baseline - barHeights[i] * scale,
                left + barW, baseline,
            )
            c.drawRoundRect(OUT_BAR, radius, radius, FILL)
            if (dotRow) {
                FILL.color = if (i < levelMapped(dots, columns)) fg else inactive
                c.drawCircle(left + radius, dotCy, dotR, FILL)
            }
        }
        return true
    }


    /**
     * The compact look: the second card as a second row of bars, not a dot row.
     * Completely decoupled from the dot style.
     */
    private fun drawStackRows(
        c: Canvas, width: Int, height: Int, bars: Int, dots: Int,
        fg: Int, a: StatusAppearance, type: String?, type2: String?,
    ): Boolean {
        if (bars < 0) {
            return false
        }
        val columns = outColumns(a)
        val barW = outBarWidth(a)
        val pitch = outPitch(a)
        val rowH = RectGeometry.STACK_ROW_H
        val dual = dots >= 0
        val above = labelAbove(a)
        val actTotal = if (a.showMobileActivity) rowsActivityBlockWidth(barW) else 0f
        val inkW = outSignalInkWidth(a, type, type2, dual)
        val gap = if (dual) rowsGap(a) else 0f
        val inkH = if (dual) rowH * 2f + gap else rowH
        val scale = min(width / inkW, height.toFloat() / inkH)
        if (scale <= 0f) {
            return false
        }
        val x0 = (width - inkW * scale) / 2f
        val top = (height - inkH * scale) / 2f
        val barsOffset = if (above) outLabelOverhang(a, type, type2, dual) * scale else 0f
        val barWpx = barW * scale
        val barsX = x0 + barsOffset + actTotal * scale
        val actCx = barsX - barW * (ROWS_ACTIVITY_GAP + ROWS_ACTIVITY_ARROW_W * 0.5f) * scale
        val inactive = withAlpha(fg, a.trackAlpha)
        val actIn = StatusState.sDataActivity == 1 || StatusState.sDataActivity == 3
        val actOut = StatusState.sDataActivity == 2 || StatusState.sDataActivity == 3

        // Row 1 (SIM 1)
        val row1Baseline = top + rowH * scale
        val bar1H = (rowH / columns) * scale

        // 1. Network type: letter 'G' aligned directly with the first signal bar (Bar 1)
        val bar1Center = barsX + barWpx * 0.5f
        if (above && !type.isNullOrEmpty()) {
            val capSize = outCaptionSize(a, dual) * scale
            val labelX = typeLabelXOverBar1(type, bar1Center, capSize)
            drawOutLabel(
                c, type, labelX,
                top + 10f * scale, scale, fg, a, dual = true, hang = true,
            )
        } else if (!above && a.labelInReading()) {
            val colLeft = barsX + (pitch * (columns - 1) + barW) * scale +
                outLabelGap(a, true) * scale
            val signalTop = top
            val signalBottom = if (dual) top + (rowH * 2f + gap) * scale else top + rowH * scale
            if (dual) {
                // Dual cards: Top label aligns top edge with Row 1 top; bottom label aligns bottom edge with Row 2 bottom
                drawColumnLabel(c, type, colLeft, signalTop, scale, fg, a, true, align = 1)
                drawColumnLabel(c, type2, colLeft, signalBottom, scale, fg, a, true, align = -1)
            } else {
                // Single card: center vertically on the row
                val midY = (signalTop + signalBottom) * 0.5f
                drawColumnLabel(c, type, colLeft, midY, scale, fg, a, false, align = 0)
            }
        }

        // 2. Activity icon: to the left of Bar 1, bottom-aligned with Bar 1
        if (a.showMobileActivity) {
            drawRowsActivityArrows(
                c, actCx, row1Baseline, bar1H, scale, barW, fg,
                withAlpha(fg, maxOf(a.trackAlpha, ACTIVITY_IDLE_ALPHA)), actIn, actOut,
            )
        }

        // 3. Draw Row 1 bars
        drawRowBars(
            c, levelMapped(bars, columns), barsX, top, rowH * scale, columns,
            barWpx, pitch * scale, fg, inactive,
        )

        // Row 2 (SIM 2)
        if (dual) {
            val secondTop = top + (rowH + gap) * scale
            // Network type of Row 2: letter 'G' aligned directly with Bar 1 of Row 2
            if (above && !type2.isNullOrEmpty()) {
                val capSize = outCaptionSize(a, true) * scale
                val labelX2 = typeLabelXOverBar1(type2, bar1Center, capSize)
                drawOutLabel(
                    c, type2, labelX2,
                    secondTop + 10f * scale, scale, fg, a, dual = true, hang = true,
                )
            }
            drawRowBars(
                c, levelMapped(max(0, dots), columns), barsX, secondTop, rowH * scale, columns,
                barWpx, pitch * scale, fg, inactive,
            )
        }
        return true
    }


    /** One row of the compact style, with four steps matching signal meter levels. */
    private fun drawRowBars(
        c: Canvas, lit: Int, x0: Float, top: Float, rowH: Float, columns: Int,
        barW: Float, pitch: Float, fg: Int, inactive: Int,
    ) {
        val radius = barW / 2f
        val base = top + rowH
        for (i in 0 until columns) {
            val h = rowH * (i + 1) / columns
            val left = x0 + i * pitch
            OUT_BAR.set(left, base - h, left + barW, base)
            FILL.color = if (i < lit) fg else inactive
            c.drawRoundRect(OUT_BAR, radius, radius, FILL)
        }
    }


    /** The caption, centred on [x], sitting entirely above [clearTop]. */
    private fun drawOutLabel(
        c: Canvas, text: String, x: Float, edge: Float, scale: Float,
        fg: Int, a: StatusAppearance, dual: Boolean = true, hang: Boolean = false,
    ) {
        val size = outCaptionSize(a, dual) * scale
        // Small bold label: enough weight to stay legible at 6sp.
        applyWeight(max(a.typeWeight, LABEL_WEIGHT))
        TEXT.textSize = size
        TEXT.color = fg
        // `hang` puts the caption's DRAWN top on `edge`; otherwise its baseline sits a gap
        // above it. The anchor is the ink box from [Paint.getTextBounds], not the font
        // metrics: metrics reserve the face's whole leading above the capitals, so hanging
        // by them dropped the visible glyph a fifth of an em below the top it was meant to
        // touch - which is exactly the "still too low" this caption kept reading as.
        val baseline = if (hang) {
            TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
            edge - TEXT_INK.top
        } else {
            edge - (TEXT.fontMetrics.ascent + TEXT.fontMetrics.descent) / 2f
        }
        // Through [drawType], so a trailing "A" shrinks instead of running into the bars.
        drawType(c, text, x, baseline, size, fg, a)
    }
}
