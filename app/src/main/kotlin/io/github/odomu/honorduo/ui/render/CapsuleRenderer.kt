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
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer.openingReach
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
import io.github.odomu.honorduo.ui.render.GlyphInk.setFont
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
import kotlin.math.roundToInt
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
        canvas: Canvas, cap: StatusAppearance.Capsule, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int, mobileType: String?,
        slotLevels: IntArray?, sims: Int, role: Int, fg: Int,
        a: StatusAppearance,
    ) {
        val stroke = a.ringStroke * CapsuleGeometry.CAP_STROKE_RATIO

        // The track first, in full, then the charged part over it. `level < 0` is "no reading yet" and fills nothing.
        val right = requiredCapsuleRight(a, cap, mobileType, level)
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
     * How far right the outline must reach so the body's two pieces fit at their
     * configured sizes.
     *
     * The pieces are the ones the body will actually draw - [cap]'s first and second
     * ink-bearing part - not the first two placements: a three-piece queue draws two, and
     * reserving width for a placement that never gets ink left a wide, empty capsule.
     * Text is still measured from the widest form each part can show, so a reading that
     * changes every second cannot resize the outline.
     *
     * Cached on the appearance and the two part ids: this runs once per frame, and the
     * answer only changes when the settings or the ink-bearing pieces do.
     */
    internal fun requiredCapsuleRight(
        a: StatusAppearance, cap: StatusAppearance.Capsule, type: String?, level: Int,
    ): Float {
        val first = cap.parts[IconZones.Zone.CENTRE.ordinal]
        val second = cap.second[IconZones.Zone.CENTRE.ordinal]
        val third = cap.third[IconZones.Zone.CENTRE.ordinal]
        if (a === lastAppearance && first == lastFirst && second == lastSecond &&
            third == lastThird && type == lastType && level == lastLevel
        ) {
            return lastRight
        }
        val right = computeCapsuleRight(a, first, second, third, type, level)
        lastAppearance = a
        lastFirst = first
        lastSecond = second
        lastThird = third
        lastType = type
        lastLevel = level
        lastRight = right
        return right
    }

    private var lastAppearance: StatusAppearance? = null
    private var lastFirst = -1
    private var lastSecond = -1
    private var lastThird = -1
    private var lastType: String? = null
    private var lastLevel = -1
    private var lastRight = 0f

    private fun computeCapsuleRight(
        a: StatusAppearance, first: Int, second: Int, third: Int, type: String?, level: Int,
    ): Float {
        val pairRight = CapsuleGeometry.capPairRight(a)
        if (first == Prefs.PART_NONE) return a.capsuleRight()
        val stroke = a.ringStroke * CapsuleGeometry.CAP_STROKE_RATIO
        setFont(a.fontPath)
        val available = CapsuleGeometry.capReadingsWidth(stroke, pairRight, CapsuleGeometry.CAP_LEFT)
        // The pair reserves from fixed samples so its width never moves with the readings.
        // Three pieces cannot afford that: the samples are the widest form of every piece,
        // so the capsule came out far wider than the line it held and the spare width went
        // straight into the two gaps. Measure what is drawn instead.
        val measured = third != Prefs.PART_NONE
        fun widthOf(part: Int): Float = if (measured) {
            shownInkWidth(part, a, type, level, stroke, available)
        } else {
            pieceInkWidth(part, a, type, stroke)
        }
        var content = widthOf(first)
        var count = 1
        if (second != Prefs.PART_NONE) {
            content += widthOf(second)
            count++
        }
        if (third != Prefs.PART_NONE) {
            content += widthOf(third)
            count++
        }
        if (count == 1) return a.capsuleRight()
        if (content <= 0f) return pairRight
        // The pair's width reserves the reference inset plus one reference gap; three
        // pieces reserve the same, so their two gaps share the slack exactly the way the
        // pair's single gap does. Reserving the spacing setting four times made the
        // capsule wider than the two-piece one and left the gaps visibly tighter.
        // Three pieces reserve the spacing setting four times: two outer margins and two
        // inner gaps, all the same value. Reserving only the reference 4-unit gap left
        // the row cramped as soon as the clock was one of the three, because its five
        // glyphs eat nearly the whole track and the spare width was all the gap there was.
        val needed = if (count >= 3) {
            content + 4f * (CAP_CELL_PADDING * a.capGap / 100f)
        } else content + CapsuleGeometry.CAP_TEXT_GAP + 2f * CAP_CELL_PADDING
        return pairRight + maxOf(0f, needed - available)
    }

    /**
     * The width the piece is about to draw, measured from the text it will show.
     *
     * [pieceInkWidth] answers the reservation question and therefore measures fixed
     * samples ("100", "88:88"); using those for placement left the gap beside a short
     * reading visibly wider than the gap beside a long one.
     */
    private fun shownInkWidth(
        part: Int, a: StatusAppearance, type: String?, level: Int, stroke: Float, track: Float,
    ): Float {
        val text = pieceText(part, a, type, level) ?: return pieceInkWidth(part, a, type, stroke)
        if (text.isEmpty()) return 0f
        applyWeight(textWeightOf(part, a))
        val setting = textSizeOf(part, a)
        val suffix = if (part == Prefs.PART_TYPE) a.typeSuffixScale else 100
        // The size the drawer will pick for this exact string in this exact cell.
        val size = capsuleTextSize(text, setting, track, suffix, -1f, stroke)
        return textBoxWidth(text, size, part == Prefs.PART_TYPE, suffix)
    }

    /** Natural width of one piece at the size the interior draws it. */
    private fun pieceInkWidth(part: Int, a: StatusAppearance, type: String?, stroke: Float): Float =
        when (part) {
            Prefs.PART_BOLT -> BOLT_INK_W * CapsuleGeometry.capBoltScale(stroke) * a.boltScaleFactor()
            Prefs.PART_WIFI ->
                IconGeometry.W_INK_W * CapsuleGeometry.capWifiScale(stroke) * a.wifiScaleFactor()
            // The line form never exceeds this span, whatever the level.
            Prefs.PART_BT -> if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                textInkWidth("100", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            } else {
                32f
            }
            Prefs.PART_CLOCK ->
                textInkWidth("88:88", a.clockWeight, a.clockSize, false, stroke)
            Prefs.PART_VALUE ->
                textInkWidth(valueText(100, a), a.valueWeight, a.valueSize, false, stroke)
            Prefs.PART_TYPE -> if (type.isNullOrEmpty()) {
                textInkWidth("5G", a.typeWeight, a.typeSize, true, stroke, a.typeSuffixScale)
            } else {
                textInkWidth(type, a.typeWeight, a.typeSize, true, stroke, a.typeSuffixScale)
            }
            Prefs.PART_POWER, Prefs.PART_RUNNING ->
                textInkWidth("99.9W", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            Prefs.PART_TEMP ->
                textInkWidth("88\u00B0C", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            Prefs.PART_CURRENT ->
                textInkWidth("-1999mA", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            Prefs.PART_FPS ->
                textInkWidth("120", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            Prefs.PART_VOLTAGE ->
                textInkWidth("4.99V", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            Prefs.PART_SIGNAL ->
                textInkWidth("-120dBm", a.perfTypeWeight(), a.perfReadingSize(), false, stroke)
            else -> 0f
        }

    private fun textInkWidth(
        text: String, weight: Int, setting: Int, isType: Boolean, stroke: Float,
        suffixPercent: Int = 100,
    ): Float {
        applyWeight(weight)
        val size = minOf(setting * CapsuleGeometry.CAP_TEXT_SCALE, CapsuleGeometry.capTextMaxSize(stroke))
        TEXT.textSize = size
        return if (isType) measureType(text, size, suffixPercent) else TEXT.measureText(text)
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
        // The line form IS part of the outline: it sits on the cut path, centred in the
        // mouth, so it reads as a piece cut out of the capsule. Drawing it on the opening
        // lane (where digits straddle the gap) made it float above the outline.
        if (part == Prefs.PART_BT && a.btBatteryMode != Prefs.BT_BATTERY_DIGIT) {
            drawCapsuleHeadsetLine(c, fg, a, band == IconZones.Zone.TOP, left, right)
            return
        }
        // A reading in an opening keeps the same margin from the cut ends that the body
        // keeps from the caps. Fitting it to the whole mouth made it touch both arcs, so
        // its ink crossed them and the two sides read as different distances.
        val mouth = CapsuleGeometry.capOpeningWidth(right) - 2f * CapsuleGeometry.capTextInset()
        drawCapsulePiece(
            c, stroke, part, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = CapsuleGeometry.capDotCx(left, right),
            cy = CapsuleGeometry.capOpeningCy(band == IconZones.Zone.TOP, stroke),
            align = CellAlign.CENTRE,
            width = mouth.coerceAtLeast(1f),
            heightHint = openingRoom(band, stroke),
        )
    }

    /**
     * The headset line in a top/bottom opening, drawn across the mouth the outline was
     * cut for.
     *
     * The length comes from [openingReach] minus half the stroke, so its round caps meet
     * the outline's cut ends. A fixed short line left gaps on both sides, which read as
     * two stray bars (the outline ends) flanking a separate line instead of one piece
     * carved out of the capsule.
     */
    /**
     * Half-width of the headset line in an opening.
     *
     * Deliberately the same span a signal-dot row inks at the current column count: the
     * two forms share an opening, so their left/right margins must match instead of the
     * line looking pinched or stretched next to the dots.
     */
    private fun capsuleHeadsetLineHalf(a: StatusAppearance): Float {
        val columns = a.signalColumns.coerceIn(4, 5)
        return capsuleDotHalfSpan(a, columns) + capsuleDotR(a, columns)
    }

    private fun drawCapsuleHeadsetLine(
        c: Canvas, fg: Int, a: StatusAppearance, top: Boolean,
        left: Float, right: Float,
    ) {
        val bt = StatusState.sBluetoothBatteryLevel
        if (a.btBatteryMode == Prefs.BT_BATTERY_OFF || bt !in 0..100) return
        val cx = CapsuleGeometry.capDotCx(left, right)
        val y = if (top) CapsuleGeometry.CAP_TOP else CapsuleGeometry.CAP_BOTTOM
        // Same span as the signal-dot row at this column setting; the opening is cut for
        // that span plus clearance, which is what keeps the side margins identical.
        val half = capsuleHeadsetLineHalf(a)
        STROKE.style = Paint.Style.STROKE
        STROKE.strokeWidth = maxOf(7f, a.ringStroke * 0.5f)
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.color = withAlpha(fg, a.trackAlpha)
        c.drawLine(cx - half, y, cx + half, y, STROKE)
        if (bt > 0) {
            STROKE.color = fg
            c.drawLine(cx - half, y, cx - half + 2f * half * (bt / 100f), y, STROKE)
        }
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
        val third = cap.third[IconZones.Zone.CENTRE.ordinal]
        val cy = CapsuleGeometry.capInteriorCy(stroke)
        // The two cells are independent components on one track: each hangs off its own
        // edge of the interior, and the track is what each is fitted against. When both
        // are text the measured width grows the capsule, so neither has to shrink.
        // The margin the ink keeps from each cap IS the spacing setting - one control, not
        // a constant beside it. 100% is the reference inset; 0% lets a reading run to the
        // cap.
        val baseInset = CAP_CELL_PADDING * a.capGap / 100f
        val leftExtra = if (cap.dotsIn(IconZones.Zone.LEFT)) 8f else 0f
        val rightExtra = if (cap.dotsIn(IconZones.Zone.RIGHT)) 8f else 0f
        val track = CapsuleGeometry.capReadingsWidth(stroke, right, left) - 2f * baseInset - leftExtra - rightExtra
        val edgeLeft = CapsuleGeometry.capTextLeft(stroke, left) + baseInset + leftExtra
        val edgeRight = CapsuleGeometry.capTextRight(stroke, right) - baseInset - rightExtra
        // A lone piece is centred in the interior so it keeps the same margin on both
        // sides; a pair still hangs off the two edges. Left-aligning the lone piece made
        // one side touch the cap while the other side held all the slack.
        if (second == Prefs.PART_NONE) {
            val centreX = (CapsuleGeometry.capTextLeft(stroke, left) +
                CapsuleGeometry.capTextRight(stroke, right)) * 0.5f + a.capsuleLeftDx
            drawCapsulePiece(
                c, stroke, first, level, quickCharging, wifiLevel, type, fg, a,
                anchorX = centreX,
                cy = cy + a.capsuleLeftDy,
                align = CellAlign.CENTRE, width = track, heightHint = -1f,
            )
            return
        }
        if (third != Prefs.PART_NONE) {
            val placed = threePieceLayout(
                a, first, second, third, type, level, stroke, edgeLeft, edgeRight,
            )
            val x1 = placed[0] + a.capsuleLeftDx
            val x2 = placed[2]
            val x3 = placed[4] + a.capsuleRightDx
            val midY = cy + (a.capsuleLeftDy + a.capsuleRightDy) * 0.5f
            drawCapsulePiece(
                c, stroke, first, level, quickCharging, wifiLevel, type, fg, a,
                anchorX = x1, cy = cy + a.capsuleLeftDy,
                align = CellAlign.LEFT, width = track, heightHint = -1f,
            )
            drawCapsulePiece(
                c, stroke, second, level, quickCharging, wifiLevel, type, fg, a,
                anchorX = x2, cy = midY,
                align = CellAlign.LEFT, width = track, heightHint = -1f,
            )
            drawCapsulePiece(
                c, stroke, third, level, quickCharging, wifiLevel, type, fg, a,
                anchorX = x3, cy = cy + a.capsuleRightDy,
                align = CellAlign.LEFT, width = track, heightHint = -1f,
            )
            return
        }
        drawCapsulePiece(
            c, stroke, first, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = edgeLeft + a.capsuleLeftDx,
            cy = cy + a.capsuleLeftDy,
            align = CellAlign.LEFT, width = track, heightHint = -1f,
        )
        val isIcon = second == Prefs.PART_BOLT || second == Prefs.PART_WIFI
        val secondAnchorX = if (isIcon) {
            capsuleRightAnchor(second, stroke, right, baseInset + rightExtra, a)
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

    /** Where a right-hand icon's centre goes, so its ink clears the cap. */
    private fun capsuleRightAnchor(
        part: Int, stroke: Float, right: Float, inset: Float, a: StatusAppearance,
    ): Float {
        val inkHalf = when (part) {
            Prefs.PART_BOLT -> BOLT_INK_W * CapsuleGeometry.capBoltScale(stroke) * a.boltScaleFactor() * 0.5f
            else -> IconGeometry.W_INK_W * CapsuleGeometry.capWifiScale(stroke) * a.wifiScaleFactor() * 0.5f
        }
        return CapsuleGeometry.capRightIconCx(stroke, right, inset, inkHalf) + a.capsuleRightDx
    }

    /**
     * The exact text a piece draws, or null for the pieces that draw ink instead.
     *
     * One source for both the drawer and the placement maths: measuring a piece from a
     * different string than the one it draws is what put the gaps of three pieces out of
     * step - the signal reading was measured as a wattage.
     */
    private fun pieceText(part: Int, a: StatusAppearance, type: String?, level: Int): String? =
        when (part) {
            Prefs.PART_VALUE -> valueText(level, a)
            Prefs.PART_TYPE -> type
            Prefs.PART_CLOCK -> StatusState.sClockText.takeIf { it.isNotEmpty() }
            Prefs.PART_POWER, Prefs.PART_RUNNING, Prefs.PART_TEMP, Prefs.PART_CURRENT,
            Prefs.PART_FPS, -> capsulePerfText(part, a)
            Prefs.PART_VOLTAGE -> StatusState.voltageText()
            Prefs.PART_SIGNAL -> StatusState.signalText()
            Prefs.PART_BT -> if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                StatusState.sBluetoothBatteryLevel.takeIf { it in 0..100 }?.let { btLevelText(it) }
            } else {
                null
            }
            else -> null
        }

    /**
     * Where three centre pieces go on one track.
     *
     * The outer two keep the pair's own inset from the caps and the two inner gaps are
     * equal, so the group reads as one row instead of three cells of unequal slack.
     * Returns `[x1, w1, x2, w2, x3, w3]`, anchored on each piece's ink edge.
     */
    internal fun threePieceLayout(
        a: StatusAppearance, first: Int, second: Int, third: Int,
        type: String?, level: Int, stroke: Float,
        edgeLeft: Float, edgeRight: Float,
    ): FloatArray {
        val track = edgeRight - edgeLeft
        val w1 = shownInkWidth(first, a, type, level, stroke, track)
        val w2 = shownInkWidth(second, a, type, level, stroke, track)
        val w3 = shownInkWidth(third, a, type, level, stroke, track)
        // The pair spends its whole spare width on one gap; three pieces split the same
        // spare into two, so the row reads like the two-piece arrangement.
        val step = (track - w1 - w2 - w3).coerceAtLeast(0f) / 2f
        val x1 = edgeLeft
        val x2 = x1 + w1 + step
        val x3 = x2 + w2 + step
        return floatArrayOf(x1, w1, x2, w2, x3, w3)
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
                c, stroke, pieceText(part, a, type, level) ?: "", fg, a,
                anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_TYPE -> drawCapsuleType(
                c, stroke, type, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_CLOCK -> drawCapsuleTime(
                c, stroke, pieceText(part, a, type, level) ?: "", fg, a,
                anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_POWER, Prefs.PART_RUNNING, Prefs.PART_TEMP, Prefs.PART_CURRENT,
            Prefs.PART_FPS, Prefs.PART_VOLTAGE, Prefs.PART_SIGNAL -> drawCapsulePerf(
                c, stroke, pieceText(part, a, type, level), fg, a, anchorX, cy, align,
                heightHint, width,
            )
            Prefs.PART_BT -> {
                val btLevel = StatusState.sBluetoothBatteryLevel
                if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                    drawCapsulePerf(
                        c, stroke, pieceText(part, a, type, level),
                        fg, a, anchorX, cy, align, heightHint, width,
                    )
                } else {
                    // The line uses the cell's own width and centre. Drawing it at the raw
                    // dot-band coordinates was what pushed it out of the capsule and over
                    // the neighbouring signal ink.
                    val half = minOf(
                        16f,
                        (if (width > 0f) width * 0.35f else 16f),
                    )
                    val cx = alignedCx(anchorX, half * 2f, align)
                    STROKE.style = Paint.Style.STROKE
                    STROKE.strokeWidth = maxOf(7f, a.ringStroke * 0.5f)
                    STROKE.strokeCap = Paint.Cap.ROUND
                    STROKE.color = withAlpha(fg, a.trackAlpha)
                    c.drawLine(cx - half, cy, cx + half, cy, STROKE)
                    if (btLevel > 0) {
                        STROKE.color = fg
                        c.drawLine(cx - half, cy, cx - half + 2f * half * (btLevel / 100f), cy, STROKE)
                    }
                }
            }
            else -> Unit
        }
    }

    /** Turns an anchor and the ink it will draw into the centre the drawers take. */
    private fun alignedCx(anchorX: Float, inkW: Float, align: CellAlign): Float = when (align) {
        CellAlign.LEFT -> anchorX + inkW * 0.5f
        CellAlign.RIGHT -> anchorX - inkW * 0.5f
        CellAlign.CENTRE -> anchorX
    }

    /** Scratch for the glyph bounds of one text run. */
    private val INK_BOUNDS = android.graphics.Rect()

    /**
     * The centre that puts a text run's **ink** on the cell's edge, not its advance box.
     *
     * A digit like "1" sits inside a wide advance box with a large left side bearing, so
     * aligning the advance box left a visible gap between the cap and the number while the
     * other side looked tight - the reading looked pushed to the right of its cell. The
     * advance box is still what gets fitted to the track; only the placement uses ink.
     */
    private fun inkAlignedCx(anchorX: Float, advance: Float, align: CellAlign): Float {
        if (advance <= 0f) return anchorX
        if (align == CellAlign.CENTRE) {
            // Centre the ink, not the advance box: a run whose last glyph carries a wide
            // bearing sat visibly right of the opening it was supposed to be centred in,
            // with no margin left on that side.
            return anchorX + advance * 0.5f - (INK_BOUNDS.left + INK_BOUNDS.right) * 0.5f
        }
        val edge = if (align == CellAlign.LEFT) INK_BOUNDS.left.toFloat()
        else INK_BOUNDS.right.toFloat()
        // `drawText` with Align.CENTER puts the glyph origin at `x - advance / 2`, so the
        // ink sits at `x - advance / 2 + bound`. Solving for the ink edge gives this one
        // expression for both sides; using `x - advance / 2 - bound` on the right side
        // pushed a right-aligned reading a full advance to the left, on top of its
        // neighbour.
        return anchorX + advance * 0.5f - edge
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
        val advance = TEXT.measureText(text)
        TEXT.getTextBounds(text, 0, text.length, INK_BOUNDS)
        val x = inkAlignedCx(anchorX, advance, align)
        c.drawText(text, x, cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    /**
     * The capsule dot row follows the same two sliders as the out-of-ring reading.
     *
     * Spacing uses the pitch factor outright, width is capped: past 1.35x the four
     * beads touch and the row reads as a bar, so the radius stops growing there even
     * though the bars themselves keep widening.
     */
    private const val CAPSULE_DOT_MAX_SCALE = 1.35f

    private fun capsuleDotPitch(a: StatusAppearance, columns: Int): Float =
        a.outBarPitchFactor() * (if (columns == 5) CapsuleGeometry.CAP_DOT_PITCH * 0.82f else CapsuleGeometry.CAP_DOT_PITCH)

    private fun capsuleDotR(a: StatusAppearance, columns: Int): Float =
        min(a.outBarWidthFactor(), CAPSULE_DOT_MAX_SCALE) *
            (if (columns == 5) CapsuleGeometry.CAP_DOT_R * 0.85f else CapsuleGeometry.CAP_DOT_R)

    private fun capsuleDotHalfSpan(a: StatusAppearance, columns: Int): Float =
        (columns - 1) * capsuleDotPitch(a, columns) * 0.5f

    /**
     * The two levels a band's opening should stack, or `null` for a single row.
     *
     * The rule is the ring's own: a chain that names both cards stacks them, and so does
     * the aggregate meter once the user turns dual rows on and the platform actually
     * reports two levels. Order follows the first signal component in the chain, so a
     * chain that leads with SIM 2 draws SIM 2 on the row nearest the line - the same
     * reading the ring gives.
     */
    private fun stackedPair(
        chain: IntArray?, a: StatusAppearance, levels: IntArray?, dataLevel: Int,
    ): Pair<Int, Int>? {
        if (a.dataSimOnly || chain == null) return null
        val bothCards = chain.contains(Prefs.PART_SIM_1) && chain.contains(Prefs.PART_SIM_2)
        val aggregate = chain.contains(Prefs.PART_MOBILE) && a.dualSim && (levels?.size ?: 0) >= 2
        if (!bothCards && !aggregate) return null
        val first = chain.firstOrNull {
            it == Prefs.PART_SIM_1 || it == Prefs.PART_SIM_2 || it == Prefs.PART_MOBILE
        } ?: return null
        fun level(slot: Int) = levels?.getOrNull(slot)?.takeIf { it >= 0 } ?: dataLevel
        return if (first == Prefs.PART_SIM_2) level(1) to level(0) else level(0) to level(1)
    }

    /** Row pitch of a stacked pair, as a multiple of the dot radius; the ring's own. */
    private const val DUAL_ROW_PITCH = 2.65f

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
            val columns = a.signalColumns.coerceIn(4, 5)
            return CapsuleGeometry.capDotReach(
                stroke, capsuleDotHalfSpan(a, columns), capsuleDotR(a, columns),
            )
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return 0f
        }
        if (part == Prefs.PART_BT) {
            // Digit form needs room for a three-digit string; the line form is a short
            // segment, and cutting the outline for the text width turned it into a
            // capsule-wide bar.
            val piece = if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                CapsuleGeometry.capPieceReach(
                    part, level, type ?: "", stroke,
                    CapsuleGeometry.capOpeningTextRoomH(stroke),
                )
            } else {
                capsuleHeadsetLineHalf(a)
            }
            return piece + CapsuleGeometry.capDotClearance() + stroke * 0.5f
        }
        if (part != Prefs.PART_WIFI && part != Prefs.PART_BOLT) {
            // Text: cut the gap for the line that is actually drawn. The geometry's own
            // estimate counts 0.31 em per character, which is narrower than the clock's
            // five glyphs at the size the drawer picks - the text then ran over the arc
            // ends and its two margins stopped matching.
            val half = openingTextHalfWidth(part, a, type, level, stroke)
            if (half > 0f) {
                return half + CapsuleGeometry.capDotClearance() + stroke * 0.5f
            }
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

    /**
     * Half the width a piece inks in an opening, at the size the drawer will request.
     *
     * Measured without the mouth fit: the mouth is derived from this answer, so fitting
     * here would be circular. A line too wide for the capsule still gets shrunk later.
     */
    private fun openingTextHalfWidth(
        part: Int, a: StatusAppearance, type: String?, level: Int, stroke: Float,
    ): Float {
        val text = pieceText(part, a, type, level) ?: return -1f
        if (text.isEmpty()) return 0f
        applyWeight(textWeightOf(part, a))
        val size = CapsuleGeometry.capOpeningTextEm(stroke) * textSizeOf(part, a) / Prefs.DEF_VALUE_SIZE
        val suffix = if (part == Prefs.PART_TYPE) a.typeSuffixScale else 100
        return textBoxWidth(text, size, part == Prefs.PART_TYPE, suffix) * 0.5f
    }

    /** The weight a piece draws its text with. */
    private fun textWeightOf(part: Int, a: StatusAppearance): Int = when (part) {
        Prefs.PART_VALUE -> a.valueWeight
        Prefs.PART_TYPE -> a.typeWeight
        Prefs.PART_CLOCK -> a.clockWeight
        else -> a.perfTypeWeight()
    }

    /** The size setting a piece draws its text with. */
    private fun textSizeOf(part: Int, a: StatusAppearance): Int = when (part) {
        Prefs.PART_VALUE -> a.valueSize
        Prefs.PART_TYPE -> a.typeSize
        Prefs.PART_CLOCK -> a.clockSize
        else -> a.perfReadingSize()
    }

    /**
     * Width of [text] at [size]: its ink for a reading, the box for a type label.
     *
     * The drawer aligns a reading by its glyph bounds and a type label by the box
     * `measureType` returns, so the two must be measured the same way they are placed.
     */
    private fun textBoxWidth(text: String, size: Float, isType: Boolean, suffix: Int): Float {
        TEXT.textSize = size
        if (isType) return measureType(text, size, suffix)
        TEXT.getTextBounds(text, 0, text.length, INK_BOUNDS)
        return INK_BOUNDS.width().toFloat()
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
        // A card that is not in service gets an empty row of its own, never the data
        // card's level: -2 maps to zero lit dots in levelMapped().
        if (slot in 0..1 && !StatusState.slotEnabled(slot)) return -2
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
        val topYRaw = CapsuleGeometry.capDotTopY()
        val bottomYRaw = CapsuleGeometry.capDotBottomY()
        val rawPitch = capsuleDotPitch(a, columns)
        val rawDotR = capsuleDotR(a, columns)
        // Snap the row onto the device pixel grid: four 1-2px dots at status-bar size read
        // as blurry and unevenly spaced when their centres and radii land on fractions.
        val s = runCatching { c.matrix.mapRadius(1f) }.getOrDefault(1f).let { if (it > 0f) it else 1f }
        fun snap(v: Float): Float = (v * s).roundToInt() / s
        val pitch = max(1f / s, snap(rawPitch))
        val dotR = max(1f / s, snap(rawDotR))
        val centreX = CapsuleGeometry.capDotCx(left, right)
        val first = snap(centreX - (columns - 1) * rawPitch * 0.5f)
        val topY = snap(topYRaw)
        val bottomY = snap(bottomYRaw)
        // A band whose chain names both cards - or the aggregate meter with dual rows
        // enabled - draws two rows, exactly as the ring's openings do. The second row
        // goes outward, into the band the capsule's host already reserves above/below
        // the body, so the centre readings keep the interior they were sized for.
        val topPair = stackedPair(a.ringZones.getOrNull(IconZones.Zone.TOP.ordinal), a, slotLevels, dataLevel)
        val bottomPair = stackedPair(a.ringZones.getOrNull(IconZones.Zone.BOTTOM.ordinal), a, slotLevels, dataLevel)
        val rowStep = rawDotR * DUAL_ROW_PITCH
        val topSecondY = snap(topYRaw - rowStep)
        val bottomSecondY = snap(bottomYRaw + rowStep)
        FILL.style = Paint.Style.FILL
        for (i in 0 until columns) {
            val x = first + i * pitch
            if (topRow) {
                val pair = topPair
                if (pair != null) {
                    FILL.color = if (i < levelMapped(pair.first, columns)) fg else inactive
                    c.drawCircle(x, topY, dotR, FILL)
                    FILL.color = if (i < levelMapped(pair.second, columns)) fg else inactive
                    c.drawCircle(x, topSecondY, dotR, FILL)
                } else {
                    FILL.color = if (i < topActive) fg else inactive
                    c.drawCircle(x, topY, dotR, FILL)
                }
            }
            if (bottomRow) {
                val pair = bottomPair
                if (pair != null) {
                    FILL.color = if (i < levelMapped(pair.first, columns)) fg else inactive
                    c.drawCircle(x, bottomY, dotR, FILL)
                    FILL.color = if (i < levelMapped(pair.second, columns)) fg else inactive
                    c.drawCircle(x, bottomSecondY, dotR, FILL)
                } else {
                    FILL.color = if (i < bottomActive) fg else inactive
                    c.drawCircle(x, bottomY, dotR, FILL)
                }
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
        c: Canvas, stroke: Float, text: String, fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
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
        val advance = TEXT.measureText(text)
        TEXT.getTextBounds(text, 0, text.length, INK_BOUNDS)
        var x = inkAlignedCx(anchorX, advance, align)
        if (align == CellAlign.CENTRE) {
            // drawTimeGlyphs lays the clock out glyph by glyph, and the sum of those
            // advances is not the string's advance on every face. Centring by the string
            // bounds therefore left the clock off-centre in its opening; measure the very
            // layout that will be drawn and centre that.
            var inkLeft = Float.MAX_VALUE
            var inkRight = -Float.MAX_VALUE
            var cursor = 0f
            for (i in text.indices) {
                TEXT.getTextWidths(text, i, i + 1, GLYPH_ADVANCE)
                TEXT.getTextBounds(text, i, i + 1, INK_BOUNDS)
                inkLeft = minOf(inkLeft, cursor + INK_BOUNDS.left)
                inkRight = maxOf(inkRight, cursor + INK_BOUNDS.right)
                cursor += GLYPH_ADVANCE[0]
            }
            if (inkRight > inkLeft) {
                x = anchorX + advance * 0.5f - (inkLeft + inkRight) * 0.5f
            }
        }
        val baseline = cy + (size * CapsuleGeometry.CAP_HEIGHT_RATIO) * 0.5f
        drawTimeGlyphs(
            c, TEXT, text, x, baseline,
            if (isDarkBackground(fg)) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight,
            fg,
        )
    }

    private fun drawCapsuleValue(
        c: Canvas, stroke: Float, text: String, fg: Int,
        a: StatusAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        applyWeight(a.valueWeight)
        val size = capsuleTextSize(
            text, a.valueSize, width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val advance = TEXT.measureText(text)
        TEXT.getTextBounds(text, 0, text.length, INK_BOUNDS)
        val x = inkAlignedCx(anchorX, advance, align)
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
