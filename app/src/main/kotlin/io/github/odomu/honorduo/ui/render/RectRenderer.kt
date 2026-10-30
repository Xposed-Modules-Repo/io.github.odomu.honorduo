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
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.GlyphInk.FILL
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT
import io.github.odomu.honorduo.ui.render.GlyphInk.applyWeight
import io.github.odomu.honorduo.ui.render.GlyphInk.clamp01
import io.github.odomu.honorduo.ui.render.GlyphInk.drawBoltShape
import io.github.odomu.honorduo.ui.render.GlyphInk.drawType
import io.github.odomu.honorduo.ui.render.GlyphInk.fitSize
import io.github.odomu.honorduo.ui.render.GlyphInk.valueText
import io.github.odomu.honorduo.ui.render.GlyphInk.withAlpha
import io.github.odomu.honorduo.ui.render.RingRenderer.drawWifiArcs
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import io.github.odomu.honorduo.ui.render.geometry.RectGeometry
import kotlin.math.min

/**
 * The rectangular arrangement: two columns of level dots, the value between them and
 * the battery bar across the bottom.
 */
internal object RectRenderer {

    /** Scratch for the rectangular battery bar; rewritten every frame, never kept. */
    private val RECT_BAR = Path()

    private val RECT_RECT = RectF()


    /**
     * Corner radii of the lit bar segment: rounded on the left so it follows the
     * track's capsule, square on the right so the level ends on a clean vertical
     * edge. Order is the [Path.addRoundRect] order - TL, TR, BR, BL.
     */
    private val RECT_RADII = FloatArray(8)


    /**
     * The rectangular arrangement: two columns of four level dots, the top slot
     * between them, the enlarged percentage below that, and the battery bar across
     * the bottom.
     *
     * Draw order is back to front and does not depend on which slot is used, because
     * none of the pieces overlap: the bar and the dots are the frame, and the value
     * never reaches either.
     */
    internal fun drawRectLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int,
        slotLevels: IntArray?, sims: Int, mobileType: String?, role: Int,
        fg: Int, a: StatusAppearance,
    ) {
        val r = a.rect(level, charging, wifiLevel, mobileType, sims)
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a)
        }
        drawRectBatteryBar(canvas, level, role, fg, a)

        // One slot at the top, and only one thing in it.
        if (r.bolt) {
            drawRectBolt(canvas, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a)
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a)
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a)
        }
    }


    /**
     * The two mirrored dot columns. Both carry the same level, and drawing two of them
     * is what makes the arrangement symmetrical around the text rather than a list of
     * items on one side.
     *
     * In the dual-SIM reading that symmetry becomes the point: the columns are
     * already two, so the left one reports SIM 1 and the right one SIM 2.
     *
     * @param slotLevels per-SIM levels, or `null` to repeat [level]
     */
    private fun drawRectDots(
        c: Canvas, level: Int, slotLevels: IntArray?, dual: Boolean,
        fg: Int, a: StatusAppearance,
    ) {
        val left = if (dual && slotLevels != null) slotLevels[0] else level
        val right = if (dual && slotLevels != null) slotLevels[1] else level
        val inactive = withAlpha(fg, a.trackAlpha)
        val leftActive = if (left < 0) 0 else min(RectGeometry.RECT_DOT_ROWS, left)
        val rightActive = if (right < 0) 0 else min(RectGeometry.RECT_DOT_ROWS, right)
        for (i in 0 until RectGeometry.RECT_DOT_ROWS) {
            val y = RectGeometry.RECT_DOT_TOP + i * RectGeometry.RECT_DOT_STEP
            FILL.color = if (i < leftActive) fg else inactive
            c.drawCircle(RectGeometry.RECT_DOT_LX, y, RectGeometry.RECT_DOT_R, FILL)
            FILL.color = if (i < rightActive) fg else inactive
            c.drawCircle(RectGeometry.RECT_DOT_RX, y, RectGeometry.RECT_DOT_R, FILL)
        }
    }


    /**
     * The battery bar: the track as one capsule across the full width, then the lit
     * part on top of it.
     *
     * The lit part is rounded on the left and square on the right, so its head
     * follows the track's cap while its tail stays a vertical edge wherever the level
     * happens to fall.
     */
    private fun drawRectBatteryBar(
        c: Canvas, level: Int, role: Int, fg: Int,
        a: StatusAppearance,
    ) {
        val left = RectGeometry.RECT_BAR_LEFT
        val right = RectGeometry.RECT_BAR_RIGHT
        val r = a.ringStroke * 0.5f
        val top = RectGeometry.RECT_BAR_CY - r
        val bottom = RectGeometry.RECT_BAR_CY + r

        FILL.color = withAlpha(fg, a.trackAlpha)
        RECT_RECT.set(left, top, right, bottom)
        c.drawRoundRect(RECT_RECT, r, r, FILL)

        if (level <= 0) {
            return
        }
        val cut = left + (right - left) * clamp01(level / 100f)
        RECT_RECT.set(left, top, cut, bottom)
        RECT_RADII[0] = r
        RECT_RADII[1] = r
        RECT_RADII[2] = 0f
        RECT_RADII[3] = 0f
        RECT_RADII[4] = 0f
        RECT_RADII[5] = 0f
        RECT_RADII[6] = r
        RECT_RADII[7] = r
        RECT_BAR.reset()
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW)
        FILL.color = role
        c.drawPath(RECT_BAR, FILL)
    }


    /** The Wi-Fi group moved into the top slot; the arcs themselves are unchanged. */
    private fun drawRectWifi(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val factor = a.wifiScaleFactor()
        c.withTranslation(
            RectGeometry.rectWifiOffsetX(factor),
            RectGeometry.rectWifiOffsetY(a.arcStroke.toFloat(), factor)
        ) {
            c.scale(RectGeometry.RECT_WIFI_SCALE * factor, RectGeometry.RECT_WIFI_SCALE * factor)
            drawWifiArcs(c, level, fg, a)
        }
    }


    private fun drawRectBolt(c: Canvas, color: Int, a: StatusAppearance) {
        c.withSave {
            val factor = a.boltScaleFactor()
            c.translate(RectGeometry.rectBoltOffsetX(factor), RectGeometry.rectBoltOffsetY(factor))
            c.scale(IconGeometry.BOLT_SCALE * factor, IconGeometry.BOLT_SCALE * factor)
            drawBoltShape(c, color, a)
        }
    }


    /**
     * The network type in the top slot, between the dot columns.
     *
     * Sized against [RectGeometry.RECT_TYPE_SCALE] like the percentage is against its
     * own, so the two read as a pair, and shrunk by [fitSize] if the slot is too
     * narrow for it.
     */
    private fun drawRectType(c: Canvas, type: String?, fg: Int, a: StatusAppearance) {
        val requested = a.typeSize * RectGeometry.RECT_TYPE_SCALE
        applyWeight(a.typeWeight)
        val size = fitSize(type, requested, RectGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale)
        drawType(c, type, RectGeometry.RECT_VALUE_X, RectGeometry.RECT_TYPE_BASELINE, size, fg, a)
    }

    private fun drawRectValue(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val text = valueText(level, a)
        val requested = a.valueSize * RectGeometry.RECT_VALUE_SCALE
        applyWeight(a.valueWeight)
        val size = fitSize(text, requested, RectGeometry.RECT_TEXT_CLEAR)
        TEXT.textSize = size
        TEXT.color = fg
        c.drawText(text, RectGeometry.RECT_VALUE_X, RectGeometry.RECT_VALUE_BASELINE, TEXT)
    }
}
