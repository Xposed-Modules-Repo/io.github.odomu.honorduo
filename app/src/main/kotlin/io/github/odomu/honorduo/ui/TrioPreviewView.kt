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

package io.github.odomu.honorduo.ui


import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.hook.ClockTicker
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioSettings
import io.github.odomu.honorduo.model.TrioState
import io.github.odomu.honorduo.render.TrioGeometry
import io.github.odomu.honorduo.render.TrioRenderer
import io.github.odomu.honorduo.render.TrioZones

/**
 * Renders the trio glyph for the settings app's live preview.
 *
 * It exists so the preview reuses [TrioRenderer] exactly as the status bar does. A copy
 * of the geometry inside the app would drift the moment either side is tweaked, and the
 * whole point of the preview is that what you see is what the status bar will draw.
 *
 * Nothing here touches the Xposed API: this class is loaded in the app's own process.
 */
class TrioPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Scratch for the rounded background and its clip; allocated once per view. */
    private val cornerRect = RectF()
    private val cornerPath = Path()

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guideRect = RectF()
    private val guidePoint = FloatArray(2)

    private var settings: TrioSettings = TrioSettings.defaults()
    private var level = 72
    private var charging = false
    private var quickCharging = false
    private var powerSave = false
    private var low = false
    private var wifiLevel = 3
    private var mobileLevel = 4
    private var slotLevels: IntArray? = null
    private var mobileType = ""
    private var foreground = 0xFFFFFFFF.toInt()
    private var backgroundColor = 0xFF1C1B1F.toInt()

    /** Fraction of the shorter edge left empty around the glyph. */
    private var inset = 0.06f

    /** Draws the out-of-ring label on its own instead of the glyph. */
    private var outTypeOnly = false

    /** Draws the out-of-ring signal reading on its own instead of the glyph. */
    private var outSignalOnly = false

    /** Draws one released component alone - no ring - or `0` for the glyph. */
    private var partOnly = 0


    /**
     * The zone whose outline is drawn over the glyph, or `null` for none.
     *
     * Set by the visual editor to show which third a press would land in. The outline is
     * derived from [TrioZones.boxes] through the same transform the glyph is drawn with, so
     * the line and the tappable area cannot disagree.
     */
    private var highlight: TrioZones.Zone? = null

    private var highlightColor = 0x66FFFFFF

    fun setSettings(value: TrioSettings?) {
        settings = value ?: TrioSettings.defaults()
        TrioState.sClockText = "00:01"
        TrioState.sClockBarText = if (settings.clockSeconds) "00:01:01" else "00:01"
        val today = java.util.Calendar.getInstance().time
        TrioState.sClockDate = ClockTicker.formatDate(today, settings.clockDateFormat)
        TrioState.sClockWeekday = ClockTicker.formatWeekday(today, settings.clockWeekdayFormat)
        TrioState.sClockLunar = ClockTicker.formatLunar(today)
        TrioState.sChargePowerW = PREVIEW_POWER_W
        // A frame rate to draw: the preview has no ticker of its own, and a reading that
        // previews blank would look broken rather than switched off.
        TrioState.sFps = PREVIEW_FPS
        TrioState.sBatteryTempC = PREVIEW_TEMP_C
        invalidate()
    }

    fun setState(
        level: Int,
        charging: Boolean,
        powerSave: Boolean,
        low: Boolean,
        wifiLevel: Int,
        mobileLevel: Int,
    ) = setState(level, charging, false, powerSave, low, wifiLevel, mobileLevel, null, null)

    fun setState(
        level: Int,
        charging: Boolean,
        quickCharging: Boolean,
        powerSave: Boolean,
        low: Boolean,
        wifiLevel: Int,
        mobileLevel: Int,
        slotLevels: IntArray?,
        mobileType: String?,
    ) {
        this.level = level
        this.charging = charging
        this.quickCharging = quickCharging
        this.powerSave = powerSave
        this.low = low
        this.wifiLevel = wifiLevel
        this.mobileLevel = mobileLevel
        this.slotLevels = slotLevels
        this.mobileType = mobileType ?: ""
        invalidate()
    }

    fun setForeground(color: Int) {
        foreground = color
        invalidate()
    }

    fun setPreviewBackground(color: Int) {
        backgroundColor = color
        invalidate()
    }
    fun setOutTypeOnly(value: Boolean) {
        outTypeOnly = value
        invalidate()
    }

    fun setOutSignalOnly(value: Boolean) {
        outSignalOnly = value
        invalidate()
    }

    /** The piece a sheet previews on its own, because it is not in the layout. */
    fun setPartOnly(part: Int) {
        partOnly = part
        invalidate()
    }


    fun setHighlight(zone: TrioZones.Zone?) {
        highlight = zone
        invalidate()
    }
    /** The zone a view-space point lands in, or `null` for the two out-of-ring cells. */
    fun zoneAt(x: Float, y: Float): TrioZones.Zone? {
        // Those two cells draw the status bar's own views, not the glyph, so the glyph's
        // thirds do not describe them.
        if (outTypeOnly || outSignalOnly) return null
        val t = zoneTransform(width, height, inset, settings) ?: return null
        return TrioZones.zoneAt(t, TrioAppearance.of(settings), x, y)
    }

    /** Whether a view-space point lands on the ring's outline. */
    fun isBatteryRingAt(x: Float, y: Float): Boolean {
        if (outTypeOnly || outSignalOnly) return false
        val a = TrioAppearance.of(settings)
        val sims = slotLevels?.count { it >= 0 } ?: 0
        return TrioZones.batteryRingAt(
            zoneTransform(width, height, inset, settings),
            a,
            a.ring(level, charging, wifiLevel, mobileType, sims),
            x,
            y,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // Rounded, not a plain rect.
        background.style = Paint.Style.FILL
        background.color = backgroundColor
        val density = resources.displayMetrics.density
        val radius = minOf(CORNER_RADIUS_DP * density, minOf(w, h) * 0.5f)
        if (radius > 0f) {
            cornerRect.set(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawRoundRect(cornerRect, radius, radius, background)
        } else {
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), background)
        }

        val pad = minOf(w, h) * inset
        val innerW = Math.round(w - 2f * pad)
        val innerH = Math.round(h - 2f * pad)
        if (innerW <= 0 || innerH <= 0) return

        // Everything the cell paints is clipped to the same rounded shape, so a glyph, a magnified label or a too-wide reading cannot spill past the corner it is drawn inside.
        canvas.withSave {
            if (radius > 0f) {
                // clipPath rather than clipRoundRect: the latter is API 30 and this module's
                // minSdk is 29.
                cornerPath.reset()
                cornerRect.set(0f, 0f, w.toFloat(), h.toFloat())
                cornerPath.addRoundRect(cornerRect, radius, radius, Path.Direction.CW)
                canvas.clipPath(cornerPath)
            }

            canvas.withTranslation(pad, pad) {
                when {
                    outTypeOnly -> drawOutTypeLabel(canvas, innerW, innerH)
                    outSignalOnly -> drawOutSignal(canvas, innerW, innerH)
                    partOnly != 0 && TrioRenderer.drawPartOnly(
                        canvas, innerW, innerH, partOnly, wifiLevel, foreground,
                        TrioAppearance.of(settings), mobileType,
                    ) -> Unit

                    else -> {
                        // false: the glyph must not punch a hole through the preview background.
                        TrioRenderer.drawInto(
                            canvas, innerW, innerH, level, charging, quickCharging, powerSave, low,
                            wifiLevel, mobileLevel, slotLevels, mobileType, foreground, settings, false,
                        )
                    }
                }
            }

            // After the glyph, so the outline reads as a frame over the ink rather than as part of it.
            if (highlight != null && !outTypeOnly && !outSignalOnly) {
                drawZoneOutline(canvas, density)
            }
        }
        if (!outTypeOnly && !outSignalOnly) {
            if (TrioAppearance.of(settings).animatesOutline(charging, level)) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawZoneOutline(canvas: Canvas, density: Float) {
        val zone = highlight ?: return
        val t = zoneTransform(width, height, inset, settings) ?: return
        guidePaint.style = Paint.Style.STROKE
        guidePaint.color = highlightColor
        guidePaint.strokeWidth = maxOf(1f, HIGHLIGHT_STROKE_DP * density)
        for (box in TrioZones.boxes(zone, TrioAppearance.of(settings))) {
            // Two opposite corners is enough: the transform is a uniform scale and a
            // translation, so an axis-aligned box stays axis-aligned.
            if (!TrioZones.toView(t, box[0], box[1], guidePoint)) continue
            guideRect.left = guidePoint[0]
            guideRect.top = guidePoint[1]
            if (!TrioZones.toView(t, box[2], box[3], guidePoint)) continue
            guideRect.right = guidePoint[0]
            guideRect.bottom = guidePoint[1]
            val r = HIGHLIGHT_CORNER_DP * density
            canvas.drawRoundRect(guideRect, r, r, guidePaint)
        }
    }

    private fun drawOutTypeLabel(canvas: Canvas, innerW: Int, innerH: Int) {
        val density = resources.displayMetrics.density
        val hostHeight = HOST_ICON_HEIGHT_DP * density
        if (mobileType.isEmpty() || hostHeight <= 0f) return

        val a = TrioAppearance.of(settings)
        labelPaint.color = foreground
        labelPaint.typeface = TrioRenderer.typefaceFor(a.typeWeight)
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.textScaleX = 1f
        // dp setting to px exactly as the hooked side resolves it, then scaled by this
        // preview's box over the icon box it is tuned against.
        val size = innerH * a.outTypeSize * density / hostHeight
        labelPaint.textSize = size

        // Shrink the trailing "A" as the status bar label does; this preview draws with a
        // Paint, so it takes the same two-run path TrioRenderer.drawType takes.
        val shrunk = TrioGeometry.hasShrunkSuffix(mobileType, a.typeSuffixScale)
        val base = if (shrunk) TrioGeometry.typeBase(mobileType) else mobileType
        val suffix = if (shrunk) TrioGeometry.TYPE_SUFFIX.toString() else ""
        val suffixSize = size * a.typeSuffixScale / 100f
        val baseWidth = labelPaint.measureText(base)
        labelPaint.textSize = suffixSize
        val suffixWidth = labelPaint.measureText(suffix)
        val width = baseWidth + suffixWidth

        // On screen the label is wrap-content beside the icon box, so nothing trims it.
        val scaleX = if (width > innerW && width > 0f) innerW / width else 1f
        labelPaint.textScaleX = scaleX

        val metrics = labelPaint.fontMetrics
        val baseline = innerH * 0.5f - (metrics.ascent + metrics.descent) * 0.5f

        // The label is placed as the status bar places it: one gap to the anchor (the inward margin), one to the native icons (the outward margin).
        val pxPerDp = innerH / HOST_ICON_HEIGHT_DP
        val roomLeft = a.outTypeMarginLeft * pxPerDp
        val roomRight = innerW - a.outTypeMarginRight * pxPerDp
        val scaledWidth = width * scaleX
        val left = if (roomRight > roomLeft) {
            roomLeft + (roomRight - roomLeft - scaledWidth) * 0.5f
        } else {
            innerW * 0.5f - scaledWidth * 0.5f
        }

        labelPaint.textSize = size
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(base, left, baseline, labelPaint)
        if (shrunk) {
            labelPaint.textSize = suffixSize
            canvas.drawText(TrioGeometry.TYPE_SUFFIX.toString(), left + baseWidth * scaleX, baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawOutSignal(canvas: Canvas, innerW: Int, innerH: Int) {
        val density = resources.displayMetrics.density
        if (density <= 0f) return

        val a = TrioAppearance.of(settings)
        val reading = IntArray(2)
        val slots = slotLevels
        TrioRenderer.outSignalLevels(
            a.dataSimOnly, mobileLevel, slots, -1,
            if (slots == null) 0 else slots.count { it >= 0 }, reading,
        )
        if (reading[0] < 0) reading[0] = 0

        // Same two steps the status bar takes: the setting, in dp, resolved against the density, then the reference aspect.
        val dual = reading[1] >= 0
        val type = if (a.labelInReading()) "5G" else null
        val type2 = if (dual) type else null
        var hostHeight = TrioRenderer.outSignalHeight(a, a.outSignalSize, density, dual)
        if (a.stackStyle == Prefs.STACK_STYLE_ROWS && dual) {
            hostHeight = Math.round(hostHeight * 1.3f)
        }
        val hostWidth = TrioRenderer.outSignalWidth(hostHeight, a, type, type2, dual)
        if (hostWidth <= 0) return

        // The reading is shown at its own size, centred in the cell - NOT offset by its margin.
        val scale = minOf(innerW / hostWidth.toFloat(), innerH / hostHeight.toFloat())
        val drawW = maxOf(1, Math.round(hostWidth * scale))
        val drawH = maxOf(1, Math.round(hostHeight * scale))
        val left = (innerW - drawW) * 0.5f
        val top = (innerH - drawH) * 0.5f
        canvas.withTranslation(left, top) {
            TrioRenderer.drawOutSignal(
                canvas, drawW, drawH, reading[0], reading[1], foreground, a, type, type2,
            )
        }
    }

    companion object {
        /** Corner radius of the cell's own background, in dp - the host card's radius. */
        private const val CORNER_RADIUS_DP = 12f

        /** Stroke width of the highlight outline, in dp. */
        private const val HIGHLIGHT_STROKE_DP = 1.5f

        /** Corner radius of the highlight outline, in dp. */
        private const val HIGHLIGHT_CORNER_DP = 3f

        /**
         * Height of the box the status bar draws the glyph in, in dp: the battery's own
         * `lock_charge_height`. The out-of-ring label is positioned against the icon it
         * stands beside, so its size only means anything relative to this box.
         */
        private const val HOST_ICON_HEIGHT_DP = 17f

        /** Sample wattage for the preview's readout pieces; the app has no sampler. */
        private const val PREVIEW_POWER_W = 9.6f

        /** Sample battery temperature, in Celsius, for the same reason. */
        private const val PREVIEW_TEMP_C = 32

        /** The frame rate the preview draws; the status bar's own is sampled there. */
        private const val PREVIEW_FPS = 120

        /** The transform the editor hit-tests with; see [TrioZones.viewTransform]. */
        @JvmStatic
        fun zoneTransform(
            width: Int,
            height: Int,
            inset: Float,
            settings: TrioSettings?,
        ): FloatArray? {
            if (settings == null) return null
            return TrioZones.viewTransform(width, height, inset, TrioAppearance.of(settings))
        }
    }
}
