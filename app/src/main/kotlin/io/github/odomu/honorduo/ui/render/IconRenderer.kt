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
import android.graphics.PorterDuff
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer.drawCapsuleLayout
import io.github.odomu.honorduo.ui.render.GlyphInk.ARC
import io.github.odomu.honorduo.ui.render.GlyphInk.BOLT_CYCLE_MS
import io.github.odomu.honorduo.ui.render.GlyphInk.PATH_MEASURE
import io.github.odomu.honorduo.ui.render.GlyphInk.STROKE
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT
import io.github.odomu.honorduo.ui.render.GlyphInk.applyWeight
import io.github.odomu.honorduo.ui.render.GlyphInk.fpsText
import io.github.odomu.honorduo.ui.render.GlyphInk.roleColor
import io.github.odomu.honorduo.ui.render.GlyphInk.setFont
import io.github.odomu.honorduo.ui.render.IconRenderer.FLOW_LIFT
import io.github.odomu.honorduo.ui.render.IconRenderer.quickCharge
import io.github.odomu.honorduo.ui.render.OutSignalRenderer.drawPreviewClock
import io.github.odomu.honorduo.ui.render.RectRenderer.drawRectLayout
import io.github.odomu.honorduo.ui.render.RingRenderer.batteryRing
import io.github.odomu.honorduo.ui.render.RingRenderer.drawRingLayout
import io.github.odomu.honorduo.ui.render.RingRenderer.drawWifiArcs
import io.github.odomu.honorduo.ui.render.geometry.CapsuleGeometry
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import io.github.odomu.honorduo.ui.render.geometry.RectGeometry
import io.github.odomu.honorduo.ui.render.geometry.RingGeometry
import io.github.odomu.honorduo.ui.view.GlyphView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Entry points and frame state: the dispatch that picks an arrangement, the fit that
 * maps the 120x120 design space into a host view, and the animation clock the
 * arrangements read.
 *
 * Drawing itself lives in [RingRenderer], [CapsuleRenderer], [RectRenderer] and
 * [OutSignalRenderer]; the ink they share is [GlyphInk].
 */
internal object IconRenderer {

    /**
     * The same loop while a fast charger is attached.
     *
     * Half the normal period. The light travelling the ring is the one thing on the
     * glyph that can say "fast" rather than merely "charging", and the only way it can
     * say it without a second colour or a second shape is with speed.
     */
    private const val QUICK_CYCLE_MS = BOLT_CYCLE_MS / 2


    /** How far the running light is lifted towards white from the charged colour. */
    private const val FLOW_LIFT = 0.8f


    /** One travelling segment of the capsule's outline; rewritten per segment. */
    private val FLOW_PATH = Path()


    /**
     * Whether the frame being drawn sits on a fast charger.
     *
     * Every moving piece on the glyph reads the same [animPhase], so the speed has to
     * belong to the frame rather than to one drawing call: an outline running at one
     * rate and the bolt inside it at another reads as broken. Written once per frame
     * at the top of [drawInto], before anything animates.
     */
    private var quickCharge = false


    /**
     * The loop's speed multiplier for this frame.
     *
     * Like [quickCharge], it belongs to the frame rather than to one drawing call: the
     * outline and the bolt read the same [animPhase], so they have to be told the same
     * speed or they drift apart mid-animation.
     */
    private var animSpeed = 1f


    /**
     * Whether this frame's bolt animation is running.
     *
     * Not the same question as "is a style selected": a full battery stops the loop (the
     * view stops asking for frames), so the *drawing* has to stop moving too. Without
     * this flag the bolt kept taking its phase from the clock, and every unrelated
     * repaint - a battery broadcast, the minute tick - drew a different phase. That is
     * exactly the judder a full battery showed in every style.
     */
    internal var boltMoving = false

    private val DRAW_TRANSFORM = FloatArray(3)


    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure - [GlyphView] hands the icon view's own bounds through, and
     * the slot it paints into can be asked for nothing else.
     */
    internal fun drawState(canvas: Canvas, w: Int, h: Int, s: StatusState?, cfg: ModuleSettings?) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return
        }
        drawInto(
            canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
            if (s.wifiPresent) s.wifiLevel else -1, s.mobileLevel, s.slotLevels, s.mobileType,
            s.foreground(), cfg, true,
        )
    }


    /**
     * Renders the glyph into any canvas of the given size. Kept free of [View] so
     * the settings app can preview the exact same drawing code instead of a
     * hand-maintained copy of it.
     */
    fun drawInto(
        canvas: Canvas, w: Int, h: Int, level: Int, charging: Boolean,
        powerSave: Boolean, low: Boolean, wifiLevel: Int, mobileLevel: Int,
        fg: Int, cfg: ModuleSettings,
    ) {
        drawInto(
            canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
            null, null, fg, cfg, true,
        )
    }


    /**
     * @param quickCharging `true` while the host reports a fast charger; only the
     *   bolt's colour changes, never its shape or size.
     * @param slotLevels the per-SIM signal levels by slot - SIM 1 first - or `null`
     *   when the caller has none, which keeps the single row.
     * @param clearWhenDone `true` when this glyph owns the whole canvas (the hooked
     *   view case); `false` when drawing onto a canvas that already holds the
     *   settings app's background.
     */
    fun drawInto(
        canvas: Canvas, w: Int, h: Int, level: Int, charging: Boolean,
        quickCharging: Boolean, powerSave: Boolean, low: Boolean,
        wifiLevel: Int, mobileLevel: Int, slotLevels: IntArray?, mobileType: String?,
        fg: Int, cfg: ModuleSettings?, clearWhenDone: Boolean,
    ) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return
        }
        // The frame's speed and motion are settled here, before any of the moving pieces
        // ask for the phase, so the outline and the bolt cannot end up on different
        // clocks or at different speeds.
        quickCharge = quickCharging
        // Resolve what is on screen once, here, from the single rule source.
        val appearance = if (cfg === ModuleConfig.get()) ModuleConfig.appearance() else StatusAppearance.of(cfg)
        // the appearance snapshot: whether Wi-Fi is connected this instant is runtime
        // state, and folding it in there would make the preview (a fixed scene) and the
        // bar (live) draw one stored setting two ways.
        //
        // Suppressing the STRING rather than a per-arrangement flag keeps this in one
        // place: every arrangement already treats "no type" as "draw none of it", so the
        // ring, the rectangle and the capsule all obey without knowing this setting.
        val shownType = if (appearance.typeHideOnWifi && wifiLevel >= 1) null else mobileType
        animSpeed = appearance.boltSpeedFactor()
        boltMoving = appearance.animatesOutline(charging, level)
        // The transform is asked for rather than worked out here, because the settings screen's tappable zones have to land on the very pixels this drawing lands on.
        if (!designTransform(w, h, appearance, DRAW_TRANSFORM, wifiLevel)) {
            return
        }
        val scale = DRAW_TRANSFORM[0]
        setFont(appearance.fontPath)
        // The two arrangements have different ink extents, so each is fitted to
        // the host view with its own box.
        val rect = appearance.rect
        val capsule = appearance.capsule

        if (clearWhenDone) {
            // Clear unconditionally: our glyph is the sole content of this view, so
            // the framework's battery drawable must not show through underneath.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        }

        val role = roleColor(appearance, level, charging, powerSave, low, fg)

        // A slot with no reading counts as absent, so a single-SIM device - or a
        // poll that has not answered yet - is not mistaken for a second SIM.
        val slots: IntArray? =
            if (slotLevels != null && slotLevels.size >= StatusState.SIM_SLOTS) slotLevels else null
        // Only the two real slots count; a longer array is not a third card.
        val sims = if (slots == null) {
            0
        } else {
            (if (slots[0] >= 0) 1 else 0) + (if (slots[1] >= 0) 1 else 0)
        }

        canvas.withTranslation(DRAW_TRANSFORM[1], DRAW_TRANSFORM[2]) {
            canvas.scale(scale, scale)

            when {
                capsule -> drawCapsuleLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel, shownType,
                    slots, sims, role, fg, appearance,
                )

                rect -> drawRectLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance,
                )

                else -> drawRingLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance,
                )
            }

        }
    }


    /**
     * Which edge of the interior a cell hangs from.
     *
     * The left cell reads left-aligned, the right cell right-aligned, so a reading is
     * placed by the edge it starts (or ends) at rather than by a centre handed out in
     * advance. The openings still centre their piece on the band.
     */
    internal enum class CellAlign { LEFT, RIGHT, CENTRE }




    /** Draws the piece of the outline between two path lengths, if it has any. */
    internal fun flowPiece(c: Canvas, from: Float, to: Float) {
        if (to <= from) {
            return
        }
        FLOW_PATH.reset()
        if (PATH_MEASURE.getSegment(from, to, FLOW_PATH, true)) {
            c.drawPath(FLOW_PATH, STROKE)
        }
    }

    private var sLastLightenRole = 0

    private var sCachedLightenHead = 0


    /** The charged colour lifted towards white by [FLOW_LIFT]; cached against [role] to avoid per-frame recomputation. */
    internal fun flowHeadColor(role: Int): Int {
        if (role == sLastLightenRole) return sCachedLightenHead
        sLastLightenRole = role
        val a = (role ushr 24) and 0xFF
        val r = (role ushr 16) and 0xFF
        val g = (role ushr 8) and 0xFF
        val b = role and 0xFF
        val nr = r / 5 + 204
        val ng = g / 5 + 204
        val nb = b / 5 + 204
        val head = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        sCachedLightenHead = head
        return head
    }


    /**
     * Where this frame sits in the loop, 0..1. One clock for the bolt and the outline.
     *
     * A fast charger halves the period rather than changing the drawing: the same
     * light, the same trail, twice the speed - which is the one difference the glyph
     * can show without a second colour or a second shape.
     */
    internal fun animPhase(): Float {
        val base = if (quickCharge) QUICK_CYCLE_MS else BOLT_CYCLE_MS
        // Speed is a multiplier on the loop, not on the drawing: every piece reads this
        // phase, so scaling the period here moves all of them together. Clamped to a
        // minimum period so a runaway setting cannot ask for a frame per millisecond.
        val cycle = max(120L, (base / animSpeed).roundToInt().toLong())
        return (SystemClock.uptimeMillis() % cycle) / cycle.toFloat()
    }


    /** The breathing curve: no corner at the wrap, unlike a linear ramp. */
    internal fun pulseWave(): Float {
        val phase = animPhase()
        return 0.35f + 0.65f * (0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
    }


    /**
     * The trail's alpha [i] steps behind its head, out of 255.
     *
     * Squared, not linear: a linear tail ends on a visible edge, and that edge is what
     * makes a running light read as a chain of dashes rather than as one comet.
     */
    internal fun flowAlpha(i: Int, count: Int): Int {
        if (i == 0) return 210
        val t = 1f - i / count.toFloat()
        return (255f * t * t * 1.05f).toInt().coerceIn(0, 255)
    }


    /**
     * One segment of the ring's running light, in progress space that may run outside
     * 0..1.
     *
     * The light does not start over at the wrap: a segment that crosses the seam is
     * drawn as its two ends, so the stream is unbroken from one lap into the next. This
     * is what the old `if (from < 0f) continue` got wrong - every lap began with the
     * tail missing, which is the stutter that made the flow look broken.
     */
    internal fun flowArc(
        c: Canvas, from: Float, to: Float, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float,
    ) {
        val a = from - floor(from)
        val b = a + (to - from)
        if (b <= 1f) {
            batteryRing(c, a, b, gapStart, gapEnd, sweep, start)
            return
        }
        batteryRing(c, a, 1f, gapStart, gapEnd, sweep, start)
        batteryRing(c, 0f, b - 1f, gapStart, gapEnd, sweep, start)
    }

    internal fun arc(c: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
        ARC.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
    }


    /**
     * One component, drawn alone and centred: the sample of a piece that has been handed
     * back to the system, so its sheet still has something to size and colour.
     *
     * Placed pieces preview inside the arrangement itself; only the released ones use
     * this, and they must not draw a ring they are no longer part of.
     *
     * @return whether the piece is one this can draw
     */
    fun drawPartOnly(
        c: Canvas, width: Int, height: Int, part: Int, wifiLevel: Int,
        fg: Int, a: StatusAppearance, type: String? = null,
    ): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }
        // A single part is previewed without a charger state, so it must not inherit the
        // speed of whatever frame was drawn last.
        quickCharge = false
        // A single piece is a still sample: it must not inherit the last frame's motion
        // or its speed either, or a cell could be caught mid-loop.
        boltMoving = false
        animSpeed = 1f
        if (part == Prefs.PART_WIFI) {
            val scale = min(
                height * 0.78f / IconGeometry.W_INK_H,
                width * 0.78f / IconGeometry.W_INK_W,
            ) * a.wifiScaleFactor()
            c.withTranslation(width / 2f, height / 2f) {
                c.scale(scale, scale)
                c.translate(-IconGeometry.W_CX, -IconGeometry.W_INK_CY)
                drawWifiArcs(c, wifiLevel, fg, a)
            }
            return true
        }
        if (part == Prefs.PART_FPS) {
            // Its own cell in the component library: one line of digits, at the reading's
            // own weight.
            val text = fpsText() ?: return false
            applyWeight(a.perfTypeWeight())
            TEXT.color = fg
            TEXT.textAlign = Paint.Align.CENTER
            var size = height * 0.62f
            TEXT.textSize = size
            val measured = TEXT.measureText(text)
            if (measured > width * 0.9f) {
                size *= width * 0.9f / measured
                TEXT.textSize = size
            }
            c.drawText(
                text, width / 2f,
                height / 2f + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_TYPE) {
            // Passed in rather than read from StatusState: the settings process never
            // receives a mobile type, so the sheet's own sample would be blank there.
            val text = type ?: return false
            if (text.isEmpty()) {
                return false
            }
            applyWeight(a.typeWeight)
            TEXT.color = fg
            TEXT.textAlign = Paint.Align.CENTER
            var size = height * 0.62f
            TEXT.textSize = size
            val measured = TEXT.measureText(text)
            if (measured > width * 0.9f) {
                size *= width * 0.9f / measured
                TEXT.textSize = size
            }
            c.drawText(
                text, width / 2f,
                height / 2f + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_CLOCK) {
            val text = StatusState.sClockText
            if (text.isEmpty()) {
                return false
            }
            val sample = StatusState.sClockBarText.ifEmpty { text }
            val dateStr = if (a.clockDate) StatusState.sClockDate else ""
            val wkStr = if (a.clockWeekday) StatusState.sClockWeekday else ""
            val lunarStr = if (a.clockLunar) StatusState.sClockLunar else ""
            val weatherStr = if (a.clockWeather) (StatusState.sWeather.ifEmpty { "雾 18°C" }) else ""
            drawPreviewClock(c, width, height, sample, dateStr, wkStr, lunarStr, weatherStr, a, fg)
            return true
        }
        return false
    }


    /**
     * Pixels per design unit for a host of this size: the uniform scale that fits
     * the 120x120 design box into the view, i.e. the same factor [drawInto] applies
     * to its canvas.
     */
    fun inkScale(w: Int, h: Int): Float =
        inkScale(w, h, IconGeometry.INK_W, IconGeometry.INK_H)


    /**
     * The fit for whichever arrangement is selected. The settings preview draws its
     * own label beside the glyph, so it has to know the same factor [drawInto] will
     * use - and that depends on the style.
     */
    fun inkScale(w: Int, h: Int, rect: Boolean): Float =
        if (rect) {
            inkScale(w, h, RectGeometry.RECT_INK_W, RectGeometry.RECT_INK_H)
        } else {
            inkScale(w, h)
        }


    /**
     * The same fit, chosen by the stored style.
     *
     * Distinct from the boolean overload above because the capsule is a third ink box
     * rather than a variant of one of the other two.
     */
    fun inkScale(w: Int, h: Int, appearance: StatusAppearance?, wifiLevel: Int): Float {
        if (appearance?.rect == true) {
            return inkScale(w, h, RectGeometry.RECT_INK_W, RectGeometry.RECT_INK_H)
        }
        if (appearance?.capsule == true) {
            // The banded box: the capsule plus the strip either opening draws in. Its width
            // follows the user's extra run, which is why this takes the frame's answer
            // rather than the stored style id.
            return inkScale(
                w, h, CapsuleGeometry.capInkW(appearance, wifiLevel),
                CapsuleGeometry.CAP_INK_BOTTOM - CapsuleGeometry.CAP_INK_TOP,
            )
        }
        if (appearance?.ring == true) {
            val extraW = maxOf(0f, appearance.ringSpacingX.toFloat() * 2f)
            return inkScale(
                w, h, IconGeometry.INK_W + extraW, RingGeometry.ringInkH(appearance),
            )
        }
        return inkScale(w, h)
    }


    /**
     * The same fit for an arbitrary ink box. The two arrangements have different ink
     * extents, so the scale that fits one does not fit the other.
     */
    fun inkScale(w: Int, h: Int, inkW: Float, inkH: Float): Float {
        if (w <= 0 || h <= 0) {
            return 0f
        }
        return min(w / inkW, h / inkH)
    }


    /**
     * The concrete mapping from the 120x120 design box onto a canvas of this size, as
     * `{scale, translateX, translateY}`.
     *
     * This is the whole point of the method existing separately: the drawing path and
     * the settings screen's tappable zones must agree about where a design-space
     * point lands, or a hot spot ends up beside the thing it names.
     *
     * Callers apply it in this order, exactly as the canvas does: translate by the
     * returned offsets, then scale by the returned factor. Canonically
     * `device = design * scale + translate`.
     *
     * @param out receives `{scale, translateX, translateY}`; untouched when the
     *   method returns false
     * @return false when there is nothing to draw into, with [out] left alone so a
     *   caller cannot accidentally use a stale transform
     */
    fun designTransform(w: Int, h: Int, appearance: StatusAppearance?, out: FloatArray?, wifiLevel: Int): Boolean {
        if (w <= 0 || h <= 0 || appearance == null || out == null || out.size < 3) {
            return false
        }
        val rect = appearance.rect
        // The overall-size setting is applied to the fit rather than to the design units.
        val scale = inkScale(w, h, appearance, wifiLevel) * appearance.glyphFit()
        if (scale <= 0f) {
            return false
        }
        out[0] = scale
        if (appearance.capsule) {
            val boxH = CapsuleGeometry.CAP_INK_BOTTOM - CapsuleGeometry.CAP_INK_TOP
            out[1] = (w - CapsuleGeometry.capInkW(appearance, wifiLevel) * scale) / 2f - CapsuleGeometry.capInkX(appearance, wifiLevel) * scale
            out[2] = (h - boxH * scale) / 2f - CapsuleGeometry.CAP_INK_TOP * scale
        } else if (rect) {
            out[1] = (w - RectGeometry.RECT_INK_W * scale) / 2f - RectGeometry.RECT_INK_X * scale
            out[2] = (h - RectGeometry.RECT_INK_H * scale) / 2f - RectGeometry.RECT_INK_Y * scale
        } else {
            val hasBottomStacked = !appearance.dataSimOnly && ((appearance.ringZones.getOrNull(2)?.size ?: 0) > 1 ||
                (appearance.ringZones.getOrNull(2)?.contains(Prefs.PART_MOBILE) == true && appearance.dualSim))
            val stackShiftY = if (hasBottomStacked) 2.0f * scale else 0f
            val inkH = RingGeometry.ringInkH(appearance)
            val inkTop = RingGeometry.ringInkTop(appearance)
            out[1] = (w - IconGeometry.INK_W * scale) / 2f - IconGeometry.INK_X * scale
            out[2] = (h - inkH * scale) / 2f - inkTop * scale - stackShiftY
        }
        return true
    }
}
