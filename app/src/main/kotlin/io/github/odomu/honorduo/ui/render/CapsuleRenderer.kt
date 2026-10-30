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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.GlyphInk.ARC
import io.github.odomu.honorduo.ui.render.GlyphInk.BOLT_INK_W
import io.github.odomu.honorduo.ui.render.GlyphInk.FILL
import io.github.odomu.honorduo.ui.render.GlyphInk.GLYPH_ADVANCE
import io.github.odomu.honorduo.ui.render.GlyphInk.PATH_MEASURE
import io.github.odomu.honorduo.ui.render.GlyphInk.SPARK_POS
import io.github.odomu.honorduo.ui.render.GlyphInk.STROKE
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT
import io.github.odomu.honorduo.ui.render.GlyphInk.applyWeight
import io.github.odomu.honorduo.ui.render.GlyphInk.capsulePerfText
import io.github.odomu.honorduo.ui.render.GlyphInk.drawBoltShape
import io.github.odomu.honorduo.ui.render.GlyphInk.drawType
import io.github.odomu.honorduo.ui.render.GlyphInk.fitSize
import io.github.odomu.honorduo.ui.render.GlyphInk.isDarkBackground
import io.github.odomu.honorduo.ui.render.GlyphInk.measureType
import io.github.odomu.honorduo.ui.render.GlyphInk.tempText
import io.github.odomu.honorduo.ui.render.GlyphInk.valueText
import io.github.odomu.honorduo.ui.render.GlyphInk.withAlpha
import io.github.odomu.honorduo.ui.render.IconRenderer.CellAlign
import io.github.odomu.honorduo.ui.render.IconRenderer.animPhase
import io.github.odomu.honorduo.ui.render.IconRenderer.flowAlpha
import io.github.odomu.honorduo.ui.render.IconRenderer.flowHeadColor
import io.github.odomu.honorduo.ui.render.IconRenderer.flowPiece
import io.github.odomu.honorduo.ui.render.IconRenderer.pulseWave
import io.github.odomu.honorduo.ui.render.OutSignalRenderer.levelMapped
import io.github.odomu.honorduo.ui.render.RingRenderer.RING_FLOW_SEGMENTS
import io.github.odomu.honorduo.ui.render.RingRenderer.RING_FLOW_SPAN
import io.github.odomu.honorduo.ui.render.RingRenderer.drawWifiArcs
import io.github.odomu.honorduo.ui.render.geometry.CapsuleGeometry
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import io.github.odomu.honorduo.ui.render.geometry.RingGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The capsule arrangement: the pill body, its two bands, the openings and the cells
 * that sit inside it.
 */
internal object CapsuleRenderer {

    /** Capsule sparks, and their radius against the outline's stroke. */
    private const val CAPSULE_SPARK_COUNT = 3

    private const val CAPSULE_SPARK_SCALE = 0.6f

    /**
     * The Bluetooth level the cached digit text was built for, and that text.
     *
     * The readout is drawn every frame and [Canvas.drawText] needs a String, so the level's
     * text used to be rebuilt 60 times a second for a value that only changes on a Bluetooth
     * broadcast.
     */
    private var btTextLevel = Int.MIN_VALUE

    private var btText = ""

    internal fun btLevelText(level: Int): String {
        if (level != btTextLevel) {
            btTextLevel = level
            btText = level.toString()
        }
        return btText
    }

    /**
     * Scratch for the capsule's outline; rebuilt each pass because the gap the
     * dot rows cut changes with the stroke and with the mobile switch.
     */
    private val CAPSULE = Path()

    /**
     * The capsule arrangement: a stadium outline that *is* the battery gauge,
     * broken at 12 and 6 o'clock by two four-dot rows, with the time on the left
     * inside and the percentage, the arcs or the bolt on the right.
     *
     * The order of the two outline passes is the whole design. The track is drawn
     * first, in full, in the track colour; then the charged part is drawn over it in
     * the role colour, covering the fraction of the outline's length the level asks
     * for. That is what reads as one line filling up rather than as two lines
     * meeting.
     */
    internal fun drawCapsuleLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int, mobileType: String?,
        slotLevels: IntArray?, sims: Int, role: Int, fg: Int,
        a: StatusAppearance,
    ) {
        val stroke = a.ringStroke * CapsuleGeometry.CAP_STROKE_RATIO
        val cap = a.capsule(level, charging, wifiLevel, mobileType, sims)

        // The track first, in full, then the charged part over it. `level < 0` is "no reading yet" and fills nothing.
        val right = a.capsuleRight()
        val left = CapsuleGeometry.capLeftFor(right, CapsuleGeometry.capPairRight(a))
        STROKE.strokeWidth = stroke
        STROKE.style = Paint.Style.STROKE
        // One reach per opening, because the two openings no longer necessarily hold the same thing.
        val topReach = openingReach(cap, IconZones.Zone.TOP, stroke, level, mobileType, a)
        val bottomReach = openingReach(cap, IconZones.Zone.BOTTOM, stroke, level, mobileType, a)
        val openLeft = cap.dotsIn(IconZones.Zone.LEFT)
        val openRight = cap.dotsIn(IconZones.Zone.RIGHT)
        val hasLeftWifi = cap.parts[IconZones.Zone.LEFT.ordinal] == Prefs.PART_WIFI
        val hasRightWifi = cap.parts[IconZones.Zone.RIGHT.ordinal] == Prefs.PART_WIFI
        buildCapsuleOutline(CAPSULE, topReach, bottomReach, left, right, openLeft, openRight)
        val animated = a.animatesOutline(charging, level)
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f
        STROKE.color = withAlpha(fg, a.trackAlpha)
        canvas.drawPath(CAPSULE, STROKE)

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            drawCapsuleFill(canvas, min(1f, level / 100f), topReach, bottomReach, left, right, openLeft, openRight)
        }

        if (animated) {
            when (a.boltAnim) {
                Prefs.BOLT_ANIM_FLOW -> {
                    val head = flowHeadColor(role)
                    drawCapsuleFlow(canvas, head, stroke)
                }
                // The spark style moves the outline as well as the bolt. It was drawn
                // only for the flow style, which left this one with a completely still
                // capsule - the one style whose whole point is that the ink travels.
                // Ink colour, for the same reason as the ring: it is what reads on both
                // the background and the charged outline.
                Prefs.BOLT_ANIM_SPARK -> drawCapsuleSparks(canvas, fg, stroke)
                else -> Unit
            }
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, mobileLevel, slotLevels, cap.dual, fg, a, cap, left, right)
        }
        if (hasLeftWifi) {
            drawCapsuleFlankWifi(canvas, stroke, wifiLevel, fg, a, left, right, isLeft = true)
        }
        if (hasRightWifi) {
            drawCapsuleFlankWifi(canvas, stroke, wifiLevel, fg, a, left, right, isLeft = false)
        }

        // Three bands, the same three the ring has: the two openings and the interior.
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.TOP, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.CENTRE, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.BOTTOM, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
    }

    /**
     * Draws whatever `band` holds, at that band's own centre.
     *
     * The centre comes from the zone, not from the piece: a bolt and a percentage are
     * laid out differently inside the capsule, but they agree about where each band
     * is, which is the whole point of putting them in bands.
     */
    private fun drawCapsuleCell(
        c: Canvas, stroke: Float, band: IconZones.Zone,
        cap: StatusAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance, left: Float, right: Float,
    ) {
        if (band == IconZones.Zone.CENTRE) {
            drawCapsuleBody(c, stroke, cap, level, quickCharging, wifiLevel, type, fg, a, left, right)
            return
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return
        }
        drawCapsulePiece(
            c, stroke, part, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = CapsuleGeometry.capDotCx(left, right),
            cy = CapsuleGeometry.capOpeningCy(band == IconZones.Zone.TOP, stroke),
            align = CellAlign.CENTRE,
            width = CapsuleGeometry.capOpeningWidth(right),
            heightHint = openingRoom(band, stroke),
        )
    }

    /**
     * The body: one piece centred on the interior, or the two the user placed there
     * side by side.
     *
     * The outline is already as wide as the arrangement needs - `capsuleRight` reads
     * the stored layout, so a second piece widens it for every frame, including the
     * ones where that piece has no ink. When it has none, the first stays centred
     * rather than sitting in the left half with nothing beside it.
     */
    private fun drawCapsuleBody(
        c: Canvas, stroke: Float, cap: StatusAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance, left: Float, right: Float,
    ) {
        val first = cap.parts[IconZones.Zone.CENTRE.ordinal]
        if (first == Prefs.PART_NONE) {
            return
        }
        val second = cap.second[IconZones.Zone.CENTRE.ordinal]
        val cy = CapsuleGeometry.capInteriorCy(stroke)
        // The two cells are independent components on one track, not a width divided
        // between them: each hangs off its own edge of the interior and grows inwards,
        // so placing a clock on the left cannot squeeze the number on the right. The
        // track is what each is fitted against, which is why a reading can be set past
        // the ceiling a fixed half-slot used to impose.
        // The margin the ink keeps from each cap IS the spacing setting - one control, not
        // a constant beside it. 100% is the reference inset; 0% lets a reading run to the
        // cap, which is also the only way two pieces can meet in the middle.
        val baseInset = CAP_CELL_PADDING * a.capGap / 100f
        val leftExtra = if (cap.dotsIn(IconZones.Zone.LEFT)) 8f else 0f
        val rightExtra = if (cap.dotsIn(IconZones.Zone.RIGHT)) 8f else 0f
        val track = CapsuleGeometry.capReadingsWidth(stroke, right) - 2f * baseInset - leftExtra - rightExtra
        val edgeLeft = CapsuleGeometry.capTextLeft(stroke) + baseInset + leftExtra
        val edgeRight = CapsuleGeometry.capTextRight(stroke, right) - baseInset - rightExtra
        // A lone piece occupies the first cell, which reads from the interior's left edge.
        drawCapsulePiece(
            c, stroke, first, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = edgeLeft + a.capsuleLeftDx,
            cy = cy + a.capsuleLeftDy,
            align = CellAlign.LEFT, width = track, heightHint = -1f,
        )
        if (second == Prefs.PART_NONE) {
            return
        }
        val isIcon = second == Prefs.PART_BOLT || second == Prefs.PART_WIFI
        val secondAnchorX = if (isIcon) {
            val inkHalf = when (second) {
                Prefs.PART_BOLT -> BOLT_INK_W * CapsuleGeometry.capBoltScale(stroke) *
                    a.boltScaleFactor() * 0.5f
                else -> IconGeometry.W_INK_W * CapsuleGeometry.capWifiScale(stroke) *
                    a.wifiScaleFactor() * 0.5f
            }
            CapsuleGeometry.capRightIconCx(stroke, right, baseInset + rightExtra, inkHalf) + a.capsuleRightDx
        } else {
            edgeRight + a.capsuleRightDx
        }
        drawCapsulePiece(
            c, stroke, second, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = secondAnchorX,
            cy = cy + a.capsuleRightDy,
            align = if (isIcon) CellAlign.CENTRE else CellAlign.RIGHT,
            width = track, heightHint = -1f,
        )
    }

    /** Draws one piece in a cell of the given width and height budget. */
    private fun drawCapsulePiece(
        c: Canvas, stroke: Float, part: Int, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, width: Float, heightHint: Float,
    ) {
        when (part) {
            Prefs.PART_BOLT -> {
                // An opening gives an icon the whole lane it is centred on; the body gives it
                // the capsule's own reference scale.
                val room = if (heightHint > 0f) CapsuleGeometry.capOpeningIconRoomH(stroke) else -1f
                val baseScale = if (room > 0f) {
                    val maxW = if (width > 0f) width else 40f
                    minOf(maxW / 15f, room / IconGeometry.BOLT_INK_H)
                } else {
                    CapsuleGeometry.capBoltScale(stroke)
                }
                // Capped by the room: multiplying past it drew the bolt out of the ink box,
                // where the host clipped it.
                val scale = if (room > 0f) {
                    RingGeometry.fittedScale(baseScale, a.boltScaleFactor())
                } else {
                    baseScale * a.boltScaleFactor()
                }
                val ink = BOLT_INK_W * scale
                drawCapsuleBolt(
                    c, stroke, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a,
                    alignedCx(anchorX, ink, align), cy, heightHint, width, scale,
                )
            }
            Prefs.PART_WIFI -> {
                val extra = max(0f, a.arcStroke - IconGeometry.W_STROKE)
                val room = if (heightHint > 0f) CapsuleGeometry.capOpeningIconRoomH(stroke) else -1f
                val baseScale = if (room > 0f) {
                    val maxW = if (width > 0f) width else 60f
                    minOf(
                        maxW / (IconGeometry.W_INK_W + extra),
                        room / (IconGeometry.W_INK_H + extra),
                    )
                } else {
                    CapsuleGeometry.capWifiScale(stroke)
                }
                val scale = if (room > 0f) {
                    RingGeometry.fittedScale(baseScale, a.wifiScaleFactor())
                } else {
                    baseScale * a.wifiScaleFactor()
                }
                val ink = IconGeometry.W_INK_W * scale
                drawCapsuleWifi(
                    c, stroke, wifiLevel, fg, a, alignedCx(anchorX, ink, align), cy, heightHint, width, scale,
                )
            }
            Prefs.PART_VALUE -> drawCapsuleValue(
                c, stroke, level, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_TYPE -> drawCapsuleType(
                c, stroke, type, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_CLOCK -> drawCapsuleTime(
                c, stroke, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_POWER, Prefs.PART_RUNNING, Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS ->
                drawCapsulePerf(
                    c, stroke, capsulePerfText(part, a), fg, a, anchorX, cy, align,
                    heightHint, width,
                )
            else -> Unit
        }
    }

    /** Turns an anchor and the ink it will draw into the centre the drawers take. */
    private fun alignedCx(anchorX: Float, inkW: Float, align: CellAlign): Float = when (align) {
        CellAlign.LEFT -> anchorX + inkW * 0.5f
        CellAlign.RIGHT -> anchorX - inkW * 0.5f
        CellAlign.CENTRE -> anchorX
    }

    /**
     * A readout as its own capsule cell: the wattage or the temperature, never both.
     *
     * The cell holds one line, because the piece is one reading: the out-of-ring strip
     * stacks the pair, and a cell that did the same would be the thing this piece
     * exists to avoid.
     */
    private fun drawCapsulePerf(
        c: Canvas, stroke: Float, text: String?, fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        if (text.isNullOrEmpty()) {
            return
        }
        applyWeight(a.perfTypeWeight())
        val size = capsuleTextSize(
            text, a.perfReadingSize(), width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        val tempIsHigh = a.roleColorsTemp && text == tempText() &&
            StatusState.sBatteryHot
        TEXT.color = if (tempIsHigh) (if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight) else fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun openingRoom(band: IconZones.Zone, stroke: Float): Float {
        // An EM, not an ink height: everything downstream of this treats it as a font size.
        // See CapsuleGeometry.capOpeningTextEm.
        return if (band == IconZones.Zone.CENTRE) -1f else CapsuleGeometry.capOpeningTextEm(stroke)
    }

    /**
     * How far the outline is cut back at one opening: the half-width of whatever
     * that opening draws.
     *
     * A dot row is fixed - four beads on their own pitch - but a reading is not:
     * "73" and "100" and "5GA" are three different widths, and a gap cut for the
     * widest of them would leave the line interrupted across half the capsule for
     * the narrowest. So the reach is measured per frame from the piece that band
     * actually holds, and zero when it holds nothing.
     */
    private fun openingReach(
        cap: StatusAppearance.Capsule, band: IconZones.Zone,
        stroke: Float, level: Int, type: String?, a: StatusAppearance,
    ): Float {
        if (cap.dotsIn(band)) {
            return CapsuleGeometry.capDotReach(stroke)
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return 0f
        }
        val factor = when (part) {
            Prefs.PART_WIFI -> a.wifiScaleFactor()
            Prefs.PART_BOLT -> a.boltScaleFactor()
            else -> 1f
        }
        return CapsuleGeometry.capPieceReach(
            part, level, type ?: "", stroke,
            CapsuleGeometry.capOpeningTextRoomH(stroke), factor,
        ) + CapsuleGeometry.capDotClearance() + stroke * 0.5f
    }

    private fun buildCapsuleOutline(
        p: Path, topReach: Float, bottomReach: Float, left: Float, right: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
    ) {
        val top = CapsuleGeometry.CAP_TOP
        val bottom = CapsuleGeometry.CAP_BOTTOM
        val r = CapsuleGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f
        val k = 0.55228475f * r

        p.reset()
        p.moveTo(cx + topReach, top)
        p.lineTo(right - r, top)
        if (!openRight) {
            p.cubicTo(right - r + k, top, right, cy - k, right, cy)
            p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom)
        } else {
            p.moveTo(right - r, bottom)
        }
        p.lineTo(cx + bottomReach, bottom)
        p.moveTo(cx - bottomReach, bottom)
        p.lineTo(left + r, bottom)
        if (!openLeft) {
            p.cubicTo(left + r - k, bottom, left, cy + k, left, cy)
            p.cubicTo(left, cy - k, left + r - k, top, left + r, top)
        } else {
            p.moveTo(left + r, top)
        }
        p.lineTo(cx - topReach, top)
    }

    /**
     * The charged part of the outline, walked segment by segment.
     *
     * Drawn as its own geometry rather than by dashing the track's path: a dash
     * pattern would depend on the platform measuring the path for us, and the fill's
     * end would land wherever that measurement said. Walking the outline explicitly
     * means the fraction drawn is the fraction asked for.
     *
     * **Where it fills from.** The two dot rows divide the outline into exactly two
     * arcs - a left one and a right one - and the level is read off them as two
     * bars: full is entirely lit, and the charge is spent from the *bottom of the
     * right bar*, travelling counterclockwise. That is why the walk below runs
     * *clockwise from the bottom gap's left edge*: it visits the lit arc in the
     * order the level grows through it.
     */
    private fun drawCapsuleFill(
        c: Canvas, fraction: Float, topReach: Float, bottomReach: Float,
        left: Float, right: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
    ) {
        val top = CapsuleGeometry.CAP_TOP
        val bottom = CapsuleGeometry.CAP_BOTTOM
        val r = CapsuleGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f

        // Four straight runs and two half-circle caps, in travel order. The gaps
        // are not segments: they carry no ink in either pass.
        val topLeft = (cx - topReach) - (left + r)
        val bottomLeft = (cx - bottomReach) - (left + r)
        val bottomRight = (right - r) - (cx + bottomReach)
        val topRight = (right - r) - (cx + topReach)
        val leftCapLen = if (openLeft) 0f else PI.toFloat() * r
        val rightCapLen = if (openRight) 0f else PI.toFloat() * r
        val total = topLeft + leftCapLen + bottomLeft + bottomRight + rightCapLen + topRight
        if (total <= 0f) {
            return
        }
        var left0 = total * fraction

        // The six pieces, clockwise from the bottom gap's left edge. Each starts exactly where the previous one ended, so the fill has no seam.
        left0 = run(c, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom)
        if (!openLeft) {
            left0 = cap(c, left0, leftCapLen, left + r, cy, r, 90f, 180f)
        }
        left0 = run(c, left0, topLeft, left + r, top, cx - topReach, top)
        left0 = run(c, left0, topRight, cx + topReach, top, right - r, top)
        if (!openRight) {
            left0 = cap(c, left0, rightCapLen, right - r, cy, r, 270f, 180f)
        }
        run(c, left0, bottomRight, right - r, bottom, cx + bottomReach, bottom)
    }

    /**
     * Draws as much of one straight run as `remaining` allows, and answers what is
     * left.
     */
    internal fun run(
        c: Canvas, remaining: Float, length: Float,
        x1: Float, y1: Float, x2: Float, y2: Float,
    ): Float {
        if (remaining <= 0f || length <= 0f) {
            return remaining
        }
        if (remaining >= length) {
            c.drawLine(x1, y1, x2, y2, STROKE)
            return remaining - length
        }
        val t = remaining / length
        c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, STROKE)
        return 0f
    }

    /**
     * The same for one half-circle cap.
     *
     * `startDeg` is the angle the cap is entered at and `sweepDeg` is signed, so a
     * partial cap grows from the end the walk arrives at rather than from the
     * middle.
     */
    internal fun cap(
        c: Canvas, remaining: Float, length: Float,
        cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float,
    ): Float {
        if (remaining <= 0f || length <= 0f) {
            return remaining
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r)
        if (remaining >= length) {
            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
            return remaining - length
        }
        c.drawArc(ARC, startDeg, sweepDeg * (remaining / length), false, STROKE)
        return 0f
    }

    /**
     * The same flowing current as the ring's, on the capsule's outline.
     *
     * A stroked segment travelling the outline, not a bead on it: the outline is a thin
     * line, and a dot wide enough to see sits on the line like a knot. The outline is
     * two contours - the right half and the left half, with both mouths left open - so
     * each half carries its own light, the second offset by half a lap: one light
     * arriving at a mouth as the other leaves is a flow, while both starting together
     * at the two mouths is a blink.
     *
     * Each half's light wraps past its own end rather than restarting there, the same
     * way the ring's does, so neither half ever has a lap with no tail.
     */
    private fun drawCapsuleFlow(c: Canvas, color: Int, stroke: Float) {
        PATH_MEASURE.setPath(CAPSULE, false)
        STROKE.strokeWidth = stroke
        var half = 0
        do {
            val length = PATH_MEASURE.length
            if (length > 0f) {
                val phase = (animPhase() + half * 0.5f) % 1f
                val span = length * RING_FLOW_SPAN
                for (i in 0 until RING_FLOW_SEGMENTS) {
                    val head = phase * length - i * span
                    STROKE.color = withAlpha(color, flowAlpha(i, RING_FLOW_SEGMENTS))
                    capsuleFlowSegment(c, head - span, head, length)
                }
            }
            half++
        } while (PATH_MEASURE.nextContour())
    }

    /** One segment of the capsule's light, in path length that may run outside 0..length. */
    private fun capsuleFlowSegment(c: Canvas, fromIn: Float, toIn: Float, length: Float) {
        val from = fromIn - floor(fromIn / length) * length
        val to = from + (toIn - fromIn)
        if (to <= length) {
            flowPiece(c, from, to)
            return
        }
        flowPiece(c, from, length)
        flowPiece(c, 0f, to - length)
    }

    /**
     * Sparks travelling the capsule's outline, the same idea as the bolt's.
     *
     * Each contour is walked on its own: the outline is two halves with the mouths
     * between them, and a spark crossing a mouth would read as the ring being closed.
     */
    private fun drawCapsuleSparks(c: Canvas, color: Int, stroke: Float) {
        PATH_MEASURE.setPath(CAPSULE, false)
        val phase = animPhase()
        val radius = stroke * CAPSULE_SPARK_SCALE
        do {
            val length = PATH_MEASURE.length
            if (length <= 0f) continue
            for (i in 0 until CAPSULE_SPARK_COUNT) {
                val at = ((phase + i / CAPSULE_SPARK_COUNT.toFloat()) % 1f) * length
                // Trailing sparks
                for (tail in 2 downTo 1) {
                    val tat = ((at - tail * 0.022f * length + length) % length)
                    if (PATH_MEASURE.getPosTan(tat, SPARK_POS, null)) {
                        FILL.color = withAlpha(color, if (tail == 1) 140 else 60)
                        c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * (if (tail == 1) 0.7f else 0.45f), FILL)
                    }
                }
                // Main spark with glowing aura and brilliant core
                if (PATH_MEASURE.getPosTan(at, SPARK_POS, null)) {
                    FILL.color = withAlpha(color, 120)
                    c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * 1.4f, FILL)
                    FILL.color = Color.WHITE
                    c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * 0.85f, FILL)
                }
            }
        } while (PATH_MEASURE.nextContour())
    }

    /** Explicit per-card placements are independent of the legacy dual-row switch. */
    private fun capsuleSignalLevel(
        part: Int, legacySlot: Int, fallback: Int, levels: IntArray?, dual: Boolean,
    ): Int {
        val slot = when (part) {
            Prefs.PART_SIM_1 -> 0
            Prefs.PART_SIM_2 -> 1
            else -> if (dual) legacySlot else -1
        }
        return levels?.getOrNull(slot)?.takeIf { it >= 0 } ?: fallback
    }

    /**
     * The two openings' dot rows, each drawn only if its band holds one.
     *
     * They are separate placements now - [StatusAppearance.Capsule.dotsIn] - so a
     * capsule can draw SIM 1 above and nothing below. The **outline's** gap is still
     * cut at both openings while any row exists, because a stroke interrupted on one
     * side only reads as damage rather than as a row between two segments.
     */
    private fun drawCapsuleDots(
        c: Canvas, level: Int, slotLevels: IntArray?, dual: Boolean,
        fg: Int, a: StatusAppearance, cap: StatusAppearance.Capsule?, left: Float, right: Float,
    ) {
        val dataSlot = StatusState.sDataSlot
        val dataLevel = if (slotLevels != null && dataSlot in slotLevels.indices && slotLevels[dataSlot] >= 0) {
            slotLevels[dataSlot]
        } else level
        val topPart = cap?.dotParts?.get(Prefs.ZONE_TOP) ?: Prefs.PART_NONE
        val bottomPart = cap?.dotParts?.get(Prefs.ZONE_BOTTOM) ?: Prefs.PART_NONE
        val top = capsuleSignalLevel(topPart, 0, dataLevel, slotLevels, dual)
        val bottom = capsuleSignalLevel(bottomPart, 1, dataLevel, slotLevels, dual)
        val topRow = cap == null || cap.dotsIn(IconZones.Zone.TOP)
        val bottomRow = (bottomPart != Prefs.PART_NONE || !a.dataSimOnly) && (cap == null || cap.dotsIn(IconZones.Zone.BOTTOM))
        val leftFlank = cap?.dotsIn(IconZones.Zone.LEFT) == true
        val rightFlank = cap?.dotsIn(IconZones.Zone.RIGHT) == true
        val inactive = withAlpha(fg, a.trackAlpha)
        val columns = a.signalColumns.coerceIn(4, 5)
        val topActive = if (top < 0) 0 else levelMapped(top, columns)
        val bottomActive = if (bottom < 0) 0 else levelMapped(bottom, columns)
        val topY = CapsuleGeometry.capDotTopY()
        val bottomY = CapsuleGeometry.capDotBottomY()
        val pitch = if (columns == 5) CapsuleGeometry.CAP_DOT_PITCH * 0.82f else CapsuleGeometry.CAP_DOT_PITCH
        val halfSpan = (columns - 1) * pitch * 0.5f
        val first = CapsuleGeometry.capDotCx(left, right) - halfSpan
        val dotR = if (columns == 5) CapsuleGeometry.CAP_DOT_R * 0.85f else CapsuleGeometry.CAP_DOT_R
        val btLevel = StatusState.sBluetoothBatteryLevel
        val btActive = a.btBatteryMode > 0 && btLevel in 0..100
        if (btActive && topRow) {
            val cx = CapsuleGeometry.capDotCx(left, right)
            if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                TEXT.textSize = 20f
                TEXT.typeface = Typeface.DEFAULT_BOLD
                TEXT.textAlign = Paint.Align.CENTER
                TEXT.color = fg
                c.drawText(btLevelText(btLevel), cx, topY + 6f, TEXT)
            } else {
                val halfBar = 16f
                STROKE.style = Paint.Style.STROKE
                STROKE.strokeWidth = 4f
                STROKE.strokeCap = Paint.Cap.ROUND
                STROKE.color = inactive
                c.drawLine(cx - halfBar, topY, cx + halfBar, topY, STROKE)
                if (btLevel > 0) {
                    STROKE.color = fg
                    c.drawLine(cx - halfBar, topY, cx - halfBar + (2f * halfBar * (btLevel / 100f)), topY, STROKE)
                }
            }
        }
        FILL.style = Paint.Style.FILL
        for (i in 0 until columns) {
            val x = first + i * pitch
            if (topRow && !btActive) {
                FILL.color = if (i < topActive) fg else inactive
                c.drawCircle(x, topY, dotR, FILL)
            }
            if (bottomRow) {
                FILL.color = if (i < bottomActive) fg else inactive
                c.drawCircle(x, bottomY, dotR, FILL)
            }
        }
        if (leftFlank || rightFlank) {
            val capCy = (CapsuleGeometry.CAP_TOP + CapsuleGeometry.CAP_BOTTOM) * 0.5f
            val sim1Level = if (slotLevels != null && slotLevels.isNotEmpty() && slotLevels[0] >= 0) slotLevels[0] else dataLevel
            val sim2Level = if (slotLevels != null && slotLevels.size > 1 && slotLevels[1] >= 0) slotLevels[1] else dataLevel
            val capR = CapsuleGeometry.CAP_RADIUS
            val halfDiv = (columns - 1) * 0.5f
            val arcHalfAngle = Math.toRadians(55.0).toFloat()
            val flankDotR = (if (columns == 5) 4.0f else 4.6f) * a.dotScaleFactor()
            if (leftFlank) {
                val leftPart = a.ringZones.getOrNull(Prefs.ZONE_LEFT)?.firstOrNull { Prefs.isSignalRow(it) }
                val lLevel = when (leftPart) {
                    Prefs.PART_SIM_1 -> sim1Level
                    Prefs.PART_SIM_2 -> sim2Level
                    else -> dataLevel
                }
                val lActive = if (lLevel < 0) 0 else levelMapped(lLevel, columns)
                val arcCenterCx = left + capR
                for (i in 0 until columns) {
                    val t = (i - halfDiv) / halfDiv
                    val angle = Math.toRadians(180.0).toFloat() + t * arcHalfAngle
                    val x = arcCenterCx + capR * cos(angle)
                    val y = capCy + capR * sin(angle)
                    FILL.color = if (i < lActive) fg else inactive
                    c.drawCircle(x, y, flankDotR, FILL)
                }
            }
            if (rightFlank) {
                val rightPart = a.ringZones.getOrNull(Prefs.ZONE_RIGHT)?.firstOrNull { Prefs.isSignalRow(it) }
                val rLevel = when (rightPart) {
                    Prefs.PART_SIM_2 -> sim2Level
                    Prefs.PART_SIM_1 -> sim1Level
                    else -> dataLevel
                }
                val rActive = if (rLevel < 0) 0 else levelMapped(rLevel, columns)
                val arcCenterCx = right - capR
                for (i in 0 until columns) {
                    val t = (i - halfDiv) / halfDiv
                    val angle = 0f + t * arcHalfAngle
                    val x = arcCenterCx + capR * cos(angle)
                    val y = capCy + capR * sin(angle)
                    FILL.color = if (i < rActive) fg else inactive
                    c.drawCircle(x, y, flankDotR, FILL)
                }
            }
        }
    }

    /**
     * Draws the time one character at a time so each glyph can take its own colour.
     *
     * A single `drawText` cannot: one paint has one colour. The advances come from the
     * same paint, so the run measures exactly like the plain draw it replaces. A glyph
     * with no colour of its own (index beyond the list, or `0`) keeps [fallback].
     *
     * @param x the anchor the paint's own alignment is measured from
     */
    internal fun drawTimeGlyphs(
        c: Canvas, paint: Paint, text: String, x: Float, baseline: Float,
        colors: IntArray, fallback: Int,
    ) {
        if (text.isEmpty()) return
        val align = paint.textAlign
        val width = paint.measureText(text)
        val start = when (align) {
            Paint.Align.CENTER -> x - width * 0.5f
            Paint.Align.RIGHT -> x - width
            else -> x
        }
        paint.textAlign = Paint.Align.LEFT
        var cursor = start
        for (i in text.indices) {
            val picked = if (i < colors.size) colors[i] else 0
            paint.color = if (picked != 0) picked else fallback
            paint.getTextWidths(text, i, i + 1, GLYPH_ADVANCE)
            c.drawText(text, i, i + 1, cursor, baseline, paint)
            cursor += GLYPH_ADVANCE[0]
        }
        paint.textAlign = align
    }

    /** The time, in a capsule cell centred on `(cx, cy)`. */
    private fun drawCapsuleTime(
        c: Canvas, stroke: Float, fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        val text = StatusState.sClockText
        if (text.isEmpty()) {
            return
        }
        // The clock's own size and weight, not the value's.
        applyWeight(a.clockWeight)
        val size = capsuleTextSize(
            text, a.clockSize, width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        val baseline = cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f
        drawTimeGlyphs(
            c, TEXT, text, x, baseline,
            if (isDarkBackground(fg)) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight,
            fg,
        )
    }

    private fun drawCapsuleValue(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: StatusAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        val text = valueText(level, a)
        applyWeight(a.valueWeight)
        val size = capsuleTextSize(
            text, a.valueSize, width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun drawCapsuleType(
        c: Canvas, stroke: Float, type: String?, fg: Int,
        a: StatusAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        applyWeight(a.typeWeight)
        // The width of the row it is in, not of the interior's right-hand slot. See
        // CapsuleGeometry.capOpeningWidth: an opening spans the capsule, a slot does not.
        val size = capsuleTextSize(
            type, a.typeSize, width, a.typeSuffixScale,
            heightHint, stroke,
        )
        // The trailing "A" is drawn smaller, so the box the label occupies is measured the
        // way drawType lays it out rather than with a plain measureText.
        val ink = measureType(type, size, a.typeSuffixScale)
        drawType(
            c, type, alignedCx(anchorX, ink, align),
            cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f,
            size, fg, a,
        )
    }

    /**
     * The size a capsule reading is drawn at: its own setting, capped by the height the
     * cell can hold, then fitted to the track it was handed.
     *
     * The two cells size themselves differently, and the difference is the whole point:
     *
     *  - In the **interior** (a negative hint) the setting is an absolute size, capped by
     *    what a line of the interior's height can be.
     *  - In an **opening** the band is the size: `hint` is the largest line the band can
     *    hold, and the setting scales that. Measured against an absolute em instead - which
     *    is what this did - every opening label was clamped to the band's own height and the
     *    size slider did nothing at all up there, which is exactly what was reported.
     *
     * [slotWidth] is the track, not a share of it - see CapsuleGeometry.capReadingsWidth.
     * A reading is fitted to the room it could use if it were alone, which is what keeps
     * one cell's content from resizing the other.
     */
    private fun capsuleTextSize(
        text: String?, setting: Int, slotWidth: Float, suffixPercent: Int,
        heightHint: Float, stroke: Float,
    ): Float {
        val requested = if (heightHint < 0f) {
            min(
                setting * CapsuleGeometry.CAP_TEXT_SCALE,
                CapsuleGeometry.capTextMaxSize(stroke),
            )
        } else {
            heightHint * setting / Prefs.DEF_VALUE_SIZE
        }
        return fitSize(text, requested, slotWidth, suffixPercent)
    }

    private fun drawCapsuleWifi(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: StatusAppearance, cx: Float, cy: Float,
        heightHint: Float = -1f, width: Float = -1f, scaleIn: Float = -1f,
    ) {
        val extra = max(0f, a.arcStroke - IconGeometry.W_STROKE)
        val scale = if (scaleIn > 0f) {
            scaleIn
        } else {
            val baseScale = if (heightHint > 0f) {
                val maxW = if (width > 0f) width else 60f
                minOf(
                    maxW / (IconGeometry.W_INK_W + extra),
                    heightHint / (IconGeometry.W_INK_H + extra),
                ) * 0.95f
            } else {
                CapsuleGeometry.capWifiScale(stroke)
            }
            baseScale * a.wifiScaleFactor()
        }
        c.withTranslation(
            cx - IconGeometry.W_CX * scale,
            cy - IconGeometry.W_INK_CY * scale,
        ) {
            c.scale(scale, scale)
            drawWifiArcs(c, level, fg, a)
        }
    }

    private fun drawCapsuleFlankWifi(
        c: Canvas, stroke: Float, wifiLevel: Int, fg: Int, a: StatusAppearance,
        left: Float, right: Float, isLeft: Boolean,
    ) {
        val capR = CapsuleGeometry.CAP_RADIUS
        val capCy = (CapsuleGeometry.CAP_TOP + CapsuleGeometry.CAP_BOTTOM) * 0.5f
        val arcCenterCx = if (isLeft) left + capR else right - capR
        val inactive = withAlpha(fg, a.trackAlpha)
        val strokeWidth = stroke * 0.78f
        STROKE.strokeWidth = strokeWidth
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.style = Paint.Style.STROKE

        val arcParams = if (isLeft) arrayOf(Triple(156f, 48f, 9.0f), Triple(144f, 72f, 19.0f)) else arrayOf(Triple(-24f, 48f, 9.0f), Triple(-36f, 72f, 19.0f))

        for (idx in arcParams.indices) {
            val (startAngle, sweepAngle, offset) = arcParams[idx]
            val r = capR + offset
            ARC.set(arcCenterCx - r, capCy - r, arcCenterCx + r, capCy + r)
            val active = if (idx == 0) wifiLevel >= 1 else wifiLevel >= 3
            STROKE.color = if (active) fg else inactive
            c.drawArc(ARC, startAngle, sweepAngle, false, STROKE)
        }
    }

    /**
     * The charging bolt, in a capsule cell centred on `(cx, cy)`.
     *
     * The cell's own fit is the base; the user's size multiplies inside that room.
     * The offsets are recomputed from the resulting scale rather than added to the
     * existing pair, because `offset = centre - reference * scale` only keeps the
     * ink centred while the scale it was derived from is the scale being drawn.
     */
    private fun drawCapsuleBolt(
        c: Canvas, stroke: Float, color: Int, a: StatusAppearance,
        cx: Float, cy: Float,
        heightHint: Float = -1f, width: Float = -1f, scaleIn: Float = -1f,
    ) {
        val scale = if (scaleIn > 0f) {
            scaleIn
        } else {
            val baseScale = if (heightHint > 0f) {
                val maxW = if (width > 0f) width else 40f
                minOf(maxW / 15f, heightHint / IconGeometry.BOLT_INK_H) * 0.95f
            } else {
                CapsuleGeometry.capBoltScale(stroke)
            }
            baseScale * a.boltScaleFactor()
        }
        c.withTranslation(
            cx - IconGeometry.BOLT_CX_REF * scale,
            cy - IconGeometry.BOLT_INK_CY * scale,
        ) {
            c.scale(scale, scale)
            FILL.style = Paint.Style.FILL
            drawBoltShape(c, color, a)
        }
    }

    /**
     * The inset a capsule cell keeps from its cap at 100% spacing, in design units.
     *
     * The spacing setting scales this; there is no second, fixed padding to keep in step
     * with it, and the value is not applied to the outline.
     */
    private const val CAP_CELL_PADDING = 6f

}
