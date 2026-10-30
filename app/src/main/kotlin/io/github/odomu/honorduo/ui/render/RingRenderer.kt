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
import android.graphics.Typeface
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer.btLevelText
import io.github.odomu.honorduo.ui.render.CapsuleRenderer.drawTimeGlyphs
import io.github.odomu.honorduo.ui.render.GlyphInk.ARC
import io.github.odomu.honorduo.ui.render.GlyphInk.FILL
import io.github.odomu.honorduo.ui.render.GlyphInk.STROKE
import io.github.odomu.honorduo.ui.render.GlyphInk.TEXT
import io.github.odomu.honorduo.ui.render.GlyphInk.WIFI_DOT
import io.github.odomu.honorduo.ui.render.GlyphInk.WIFI_MIDDLE
import io.github.odomu.honorduo.ui.render.GlyphInk.WIFI_OUTER
import io.github.odomu.honorduo.ui.render.GlyphInk.applyWeight
import io.github.odomu.honorduo.ui.render.GlyphInk.clamp01
import io.github.odomu.honorduo.ui.render.GlyphInk.currentText
import io.github.odomu.honorduo.ui.render.GlyphInk.drawBoltShape
import io.github.odomu.honorduo.ui.render.GlyphInk.drawType
import io.github.odomu.honorduo.ui.render.GlyphInk.fitSize
import io.github.odomu.honorduo.ui.render.GlyphInk.fpsText
import io.github.odomu.honorduo.ui.render.GlyphInk.inkCentredBaseline
import io.github.odomu.honorduo.ui.render.GlyphInk.isDarkBackground
import io.github.odomu.honorduo.ui.render.GlyphInk.powerText
import io.github.odomu.honorduo.ui.render.GlyphInk.tempText
import io.github.odomu.honorduo.ui.render.GlyphInk.textInkHeight
import io.github.odomu.honorduo.ui.render.GlyphInk.valueText
import io.github.odomu.honorduo.ui.render.GlyphInk.withAlpha
import io.github.odomu.honorduo.ui.render.IconRenderer.animPhase
import io.github.odomu.honorduo.ui.render.IconRenderer.arc
import io.github.odomu.honorduo.ui.render.IconRenderer.flowAlpha
import io.github.odomu.honorduo.ui.render.IconRenderer.flowArc
import io.github.odomu.honorduo.ui.render.IconRenderer.flowHeadColor
import io.github.odomu.honorduo.ui.render.IconRenderer.pulseWave
import io.github.odomu.honorduo.ui.render.OutSignalRenderer.levelMapped
import io.github.odomu.honorduo.ui.render.RingRenderer.RING_POINT
import io.github.odomu.honorduo.ui.render.RingRenderer.addRingGap
import io.github.odomu.honorduo.ui.render.RingRenderer.buildRingSegments
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import io.github.odomu.honorduo.ui.render.geometry.RingGeometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The ring arrangement: the battery arc with its flow, the flank rows, the notch
 * pieces and the readings that stand in the three zones.
 */
internal object RingRenderer {

    /**
     * The flowing light on the ring: how many trail segments, and how long each is.
     *
     * Many short segments rather than a few long ones, because a segment carries one
     * alpha: at four segments the 60-step drop between them reads as four dashes, not
     * as one tail. They abut exactly - no overlap - because an overlap would composite
     * two trail alphas into a brighter band, which is the same defect in the other
     * direction.
     */
    internal const val RING_FLOW_SEGMENTS = 24

    internal const val RING_FLOW_SPAN = 0.013f


    /** Sparks travelling the ring, and their radius against the ring's stroke. */
    private const val RING_SPARK_COUNT = 3

    private const val RING_SPARK_SCALE = 0.55f

    private const val DUAL_SIGNAL_HALF_SPAN_RAD = 0.42760567f


    /** One point on the battery arc, rewritten per frame; never kept. */
    private val RING_POINT = FloatArray(2)


    /**
     * The ring's drawable segments, as `(startDeg, sweepDeg)` pairs; rewritten per call.
     *
     * Four segments is the most any opening combination can produce (two flanks, the top
     * mouth and the bottom opening), and [buildRingSegments] refuses to write past the end.
     */
    private val SEGMENTS = FloatArray(8)


    /**
     * The rings openings for one call, as `(startDeg, endDeg)` pairs; rewritten per call.
     *
     * At most four (two mouths, two flank cuts); [addRingGap] refuses to write past the end.
     */
    private val GAPS = FloatArray(8)


    /**
     * The ring arrangement.
     *
     * The canvas is already scaled into the design space and positioned on the ink
     * box, so everything here is plain design-unit drawing.
     *
     * Which piece goes where is not decided here - [StatusAppearance.Ring] has already
     * resolved the three slots. This method only draws.
     */
    internal fun drawRingLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int,
        slotLevels: IntArray?, sims: Int, mobileType: String?, role: Int,
        fg: Int, a: StatusAppearance,
    ) {
        val r = a.ring(level, charging, wifiLevel, mobileType, sims)
        drawFreeRing(
            canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
            slotLevels, mobileType, role, fg, a, r,
        )
    }


    /** Explicit slots share the editor's thirds and fit ink on BOTH axes. */
    private fun drawFreeRing(
        c: Canvas, level: Int, charging: Boolean, quickCharging: Boolean, wifiLevel: Int,
        mobileLevel: Int, levels: IntArray?, type: String?, role: Int, fg: Int,
        a: StatusAppearance, r: StatusAppearance.Ring,
    ) {
        val hasLeftDots = r.hasLeftDots
        val hasRightDots = r.hasRightDots
        val hasLeftWifi = r.hasLeftWifi
        val hasRightWifi = r.hasRightWifi
        drawBattery(
            c, level, role, fg, a, r.gapStart(), r.gapEnd(), r.sweep(), r.arcStart(), charging,
            openLeft = hasLeftDots, openRight = hasRightDots,
            topHalfDeg = r.topHalfDeg, bottomHalfDeg = r.bottomHalfDeg,
        )
        for (z in 0 until 3) {
            val chain = a.ringZones.getOrNull(z)
            val hasDotInZone = chain != null && chain.any { p ->
                p == Prefs.PART_MOBILE || p == Prefs.PART_SIM_1 || p == Prefs.PART_SIM_2
            }
            val part = r.parts[z]
            if (part == Prefs.PART_NONE && !hasDotInZone) continue

            val cx = IconGeometry.B_CX
            val cy = when (z) {
                0 -> RingGeometry.notchCy(0, a)
                1 -> RingGeometry.ZONE_CY[1]
                else -> RingGeometry.notchCy(2, a)
            }
            // The same box the mouth is cut for (see RingGeometry.notchHalfDeg), so a piece
            // can never be fitted wider than the opening it was measured against.
            val width = RingGeometry.notchSlotW(a.ringStroke)
            val height = when (z) {
                0 -> RingGeometry.notchSlotH(0, part)
                1 -> RingGeometry.DOT_BOX_CENTRE
                else -> RingGeometry.notchSlotH(2, part)
            }
            c.withTranslation(cx, cy) {
                if (part == Prefs.PART_WIFI) {
                    // Capped by the slot, not multiplied past it: a size setting above 100%
                    // used to draw the fan out of the ink box, where the host clips it.
                    val scale = RingGeometry.notchWifiScale(z, a)
                    c.scale(scale, scale)
                    c.translate(-IconGeometry.W_CX, -IconGeometry.W_INK_CY)
                    drawWifiArcs(c, wifiLevel, fg, a)
                } else if (part == Prefs.PART_BOLT) {
                    val scale = RingGeometry.notchBoltScale(z, a)
                    c.scale(scale, scale)
                    c.translate(-IconGeometry.BOLT_CX_REF, -IconGeometry.BOLT_INK_CY)
                    drawBoltShape(c, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
                } else if (hasDotInZone || part == Prefs.PART_MOBILE || part == Prefs.PART_SIM_1 || part == Prefs.PART_SIM_2) {
                    val btLevel = StatusState.sBluetoothBatteryLevel
                    val btActive = z == 2 && a.btBatteryMode > 0 && btLevel in 0..100
                    if (btActive) {
                        if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                            TEXT.textSize = 22f
                            TEXT.typeface = Typeface.DEFAULT_BOLD
                            TEXT.textAlign = Paint.Align.CENTER
                            TEXT.color = fg
                            c.drawText(btLevelText(btLevel), 0f, 6f, TEXT)
                        } else {
                            val rho = IconGeometry.B_R
                            ARC.set(-rho, -rho, rho, rho)
                            STROKE.style = Paint.Style.STROKE
                            STROKE.strokeWidth = 6f
                            STROKE.strokeCap = Paint.Cap.ROUND
                            STROKE.color = withAlpha(fg, a.trackAlpha)
                            val startDeg = 65f
                            val sweepDeg = 50f
                            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
                            if (btLevel > 0) {
                                STROKE.color = fg
                                val progressDeg = sweepDeg * (btLevel / 100f)
                                c.drawArc(ARC, startDeg + sweepDeg - progressDeg, progressDeg, false, STROKE)
                            }
                        }
                    } else {
                    val columns = a.signalColumns.coerceIn(4, 5)
                    val baseDotR = RingGeometry.arcRowDotR(z, a.ringStroke, width, a.dotScaleFactor())

                    val dataLevel = StatusState.dataSimSignalLevel()
                    val sim1Level = if (levels != null && levels.isNotEmpty() && levels[0] >= 0) levels[0] else dataLevel
                    val sim2Level = if (levels != null && levels.size > 1 && levels[1] >= 0) levels[1] else dataLevel

                    val isStackedDual = !a.dataSimOnly && chain != null && (
                        (chain.contains(Prefs.PART_SIM_1) && chain.contains(Prefs.PART_SIM_2)) ||
                        (chain.contains(Prefs.PART_MOBILE) && a.dualSim && (levels?.size ?: 0) >= 2)
                    )

                    val ringStroke = a.ringStroke * RingGeometry.RING_STROKE_RATIO
                    val rhoMid = IconGeometry.B_R - ringStroke * 0.5f
                    val baseCapDotR = if (columns == 5) 4.2f else 4.9f
                    val dotR = baseCapDotR * a.dotScaleFactor()
                    val sign = if (z == 2) 1f else -1f
                    val inactive = withAlpha(fg, a.trackAlpha)
                    val halfDiv = (columns - 1) * 0.5f

                    if (isStackedDual) {
                        val firstPart = chain.firstOrNull { p -> p == Prefs.PART_SIM_1 || p == Prefs.PART_SIM_2 || p == Prefs.PART_MOBILE }
                        val isSim2First = firstPart == Prefs.PART_SIM_2
                        val l1 = if (isSim2First) sim2Level else sim1Level
                        val l2 = if (isSim2First) sim1Level else sim2Level
                        val active1 = if (l1 < 0) 0 else levelMapped(l1, columns)
                        val active2 = if (l2 < 0) 0 else levelMapped(l2, columns)

                        val inward = 3.8f
                        val r1 = rhoMid - inward
                        val dr = dotR * 2.65f
                        val r2 = r1 + dr
                        val halfSpanAngle = DUAL_SIGNAL_HALF_SPAN_RAD

                        // Row 1: Order 1 (shifted inward for comfortable spacing)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfSpanAngle
                            val x = r1 * sin(angle)
                            val y = (IconGeometry.B_CY + sign * r1 * cos(angle)) - RingGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active1) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                        // Row 2: Order 2 (parallel outside row 1)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfSpanAngle
                            val x = r2 * sin(angle)
                            val y = (IconGeometry.B_CY + sign * r2 * cos(angle)) - RingGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active2) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                    } else {
                        val rowLevel = when (part) {
                            Prefs.PART_SIM_1 -> sim1Level
                            Prefs.PART_SIM_2 -> sim2Level
                            else -> dataLevel
                        }
                        val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                        val rho = IconGeometry.B_R
                        val openingRad = Math.toRadians(RingGeometry.GAP_WIDTH_DEG.toDouble()).toFloat()
                        val inset = (dotR + ringStroke * 0.5f + 1f) / rho
                        val halfAngle = maxOf(0f, openingRad * 0.5f - inset)
                        val beadHalfAngle = Math.toRadians((RingGeometry.BEAD_SPAN_DEG * 0.5f).toDouble()).toFloat()
                        val halfCentreAngle = minOf(halfAngle, beadHalfAngle)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfCentreAngle
                            val x = rho * sin(angle)
                            val y = (IconGeometry.B_CY + sign * rho * cos(angle)) - RingGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                    }
                    }
                } else {
                    drawRingText(c, part, type, level, fg, a, z, 0f)
                }
            }
        }
        if (hasLeftDots || hasRightDots) {
            val columns = a.signalColumns.coerceIn(4, 5)
            val dataLevel = StatusState.dataSimSignalLevel()
            val sim1Level = if (levels != null && levels.isNotEmpty() && levels[0] >= 0) levels[0] else dataLevel
            val sim2Level = if (levels != null && levels.size > 1 && levels[1] >= 0) levels[1] else dataLevel
            val dotR = RingGeometry.flankDotR(columns, a.dotScaleFactor())
            val inactive = withAlpha(fg, a.trackAlpha)
            val rho = IconGeometry.B_R
            val halfDiv = (columns - 1) * 0.5f
            // The row's own span, not the notch's: the arc is cut wider than the row, so the
            // end beads keep their clearance instead of merging into the arc's round caps.
            val rowHalfAngle = Math.toRadians(RingGeometry.flankRowHalfDeg(columns, dotR).toDouble()).toFloat()
            if (hasLeftDots) {
                val part = r.parts[Prefs.ZONE_LEFT]
                val rowLevel = when (part) {
                    Prefs.PART_SIM_1 -> sim1Level
                    Prefs.PART_SIM_2 -> sim2Level
                    else -> dataLevel
                }
                val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                for (dot in 0 until columns) {
                    val t = (dot - halfDiv) / halfDiv
                    val angle = Math.toRadians(180.0).toFloat() + t * rowHalfAngle
                    val x = IconGeometry.B_CX + rho * cos(angle)
                    val y = IconGeometry.B_CY + rho * sin(angle)
                    FILL.color = if (dot < active) fg else inactive
                    c.drawCircle(x, y, dotR, FILL)
                }
            }
            if (hasRightDots) {
                val part = r.parts[Prefs.ZONE_RIGHT]
                val rowLevel = when (part) {
                    Prefs.PART_SIM_2 -> sim2Level
                    Prefs.PART_SIM_1 -> sim1Level
                    else -> dataLevel
                }
                val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                for (dot in 0 until columns) {
                    val t = (dot - halfDiv) / halfDiv
                    val angle = t * rowHalfAngle
                    val x = IconGeometry.B_CX + rho * cos(angle)
                    val y = IconGeometry.B_CY + rho * sin(angle)
                    FILL.color = if (dot < active) fg else inactive
                    c.drawCircle(x, y, dotR, FILL)
                }
            }
        }
        if (hasLeftWifi) {
            drawRingFlankWifi(c, wifiLevel, fg, a, isLeft = true)
        }
        if (hasRightWifi) {
            drawRingFlankWifi(c, wifiLevel, fg, a, isLeft = false)
        }
        // Every other piece may hold a flank too: drawn in the mouth the ring was cut for it,
        // centred on 9 or 3 o'clock.
        drawFlankPiece(c, level, quickCharging, type, fg, a, r, Prefs.ZONE_LEFT, isLeft = true)
        drawFlankPiece(c, level, quickCharging, type, fg, a, r, Prefs.ZONE_RIGHT, isLeft = false)
    }


    /**
     * One non-bead, non-Wi-Fi piece on a flank: a reading, the clock, the bolt.
     *
     * The mouth there is cut by [StatusAppearance.Ring.flankHalfDeg], and the piece is fitted
     * to the flat of that mouth, so the two can never disagree about how much room exists.
     */
    private fun drawFlankPiece(
        c: Canvas, level: Int, quickCharging: Boolean, type: String?,
        fg: Int, a: StatusAppearance, r: StatusAppearance.Ring, zone: Int, isLeft: Boolean,
    ) {
        val part = r.parts[zone]
        if (part == Prefs.PART_NONE || Prefs.isSignalRow(part) || part == Prefs.PART_WIFI) return
        val half = r.flankHalfDeg(zone)
        if (half <= 0f) return
        val cx = if (isLeft) IconGeometry.B_CX - IconGeometry.B_R else IconGeometry.B_CX + IconGeometry.B_R
        c.withTranslation(cx, IconGeometry.B_CY) {
            if (part == Prefs.PART_BOLT) {
                val roomW = RingGeometry.flankCellWidth(half) - 2f * RingGeometry.NOTCH_PIECE_CLEARANCE
                val roomH = RingGeometry.FLANK_CELL_ROOM_H
                val fit = min(roomW / 15f, roomH / IconGeometry.BOLT_INK_H)
                val scale = fit * a.boltScaleFactor()
                c.scale(scale, scale)
                c.translate(-IconGeometry.BOLT_CX_REF, -IconGeometry.BOLT_INK_CY)
                drawBoltShape(c, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
            } else {
                drawRingText(c, part, type, level, fg, a, -1, half)
            }
        }
    }


    /**
     * One text reading in the ring: a band's third, or a flank's cell.
     *
     * `band` names the zone the reading sits in; passing a negative value means "a flank",
     * where the room is the cell cut for it rather than a third's chord. Shared so the two
     * places cannot fit the same string two different ways.
     */
    private fun drawRingText(
        c: Canvas, part: Int, type: String?, level: Int, fg: Int,
        a: StatusAppearance, band: Int, flankHalfDeg: Float,
    ) {
        val flank = band < 0
        val text = when (part) {
            Prefs.PART_VALUE -> valueText(level, a)
            Prefs.PART_TYPE -> type
            Prefs.PART_POWER, Prefs.PART_RUNNING -> powerText()
            Prefs.PART_TEMP -> tempText()
            Prefs.PART_CURRENT -> currentText(a)
            Prefs.PART_FPS -> fpsText()
            else -> StatusState.sClockText
        }
        if (text.isNullOrEmpty()) return
        val weight = when (part) {
            Prefs.PART_VALUE -> a.valueWeight
            Prefs.PART_TYPE -> a.typeWeight
            Prefs.PART_POWER, Prefs.PART_RUNNING,
            Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS,
            -> a.perfTypeWeight()
            else -> a.clockWeight
        }
        val requested = when (part) {
            Prefs.PART_VALUE -> maxOf(a.valueSize.toFloat(), 50f)
            Prefs.PART_TYPE -> maxOf(a.typeSize.toFloat(), 46f)
            Prefs.PART_POWER, Prefs.PART_RUNNING,
            Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS,
            -> maxOf(a.perfReadingSize().toFloat(), 46f)
            else -> maxOf(a.clockSize.toFloat(), 46f)
        }
        applyWeight(weight)
        TEXT.textSize = requested
        val room = if (flank) {
            RingGeometry.FLANK_CELL_ROOM_H
        } else {
            RingGeometry.zoneTextRoomH(band)
        }
        val suffixScale = if (part == Prefs.PART_TYPE) a.typeSuffixScale else 100
        // The height cap is a straight ratio at the requested size.
        val inkAtRequested = max(1f, textInkHeight(text, requested))
        val maxByHeight = requested * room / inkAtRequested
        // The width cap is NOT independent of the answer: the third's room is a chord that
        // narrows as the line grows, the flank's is the flat of its mouth and does not.
        val flankRoomW = RingGeometry.flankCellWidth(flankHalfDeg) - 2f * RingGeometry.NOTCH_PIECE_CLEARANCE
        var fitted = maxByHeight
        for (pass in 0 until 4) {
            val inkH = max(1f, textInkHeight(text, fitted))
            val roomW = if (flank) flankRoomW else RingGeometry.zoneTextRoomW(band, a.ringStroke, inkH)
            val next = min(maxByHeight, fitSize(text, requested, roomW, suffixScale))
            if (abs(next - fitted) < 0.05f) {
                fitted = next
                break
            }
            fitted = next
        }
        // Centred on its INK rather than on its metrics.
        val slotCentre = if (flank) 0f else RingGeometry.zoneTextCy(band, a.ringStroke) -
            RingGeometry.ZONE_CY[band]
        val baseline = inkCentredBaseline(text, fitted, slotCentre)
        if (part == Prefs.PART_TYPE) {
            drawType(c, text, 0f, baseline, fitted, fg, a)
            return
        }
        TEXT.textAlign = Paint.Align.CENTER
        TEXT.textSize = fitted
        if (part == Prefs.PART_CLOCK) {
            drawTimeGlyphs(
                c, TEXT, text, 0f, baseline,
                if (isDarkBackground(fg)) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight,
                fg,
            )
        } else {
            val tempIsHigh = a.roleColorsTemp && part == Prefs.PART_TEMP && StatusState.sBatteryHot
            TEXT.color = if (tempIsHigh) {
                if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight
            } else {
                fg
            }
            c.drawText(text, 0f, baseline, TEXT)
        }
    }

    private fun drawRingFlankWifi(
        c: Canvas, wifiLevel: Int, fg: Int, a: StatusAppearance, isLeft: Boolean,
    ) {
        val cx = IconGeometry.B_CX
        val cy = IconGeometry.B_CY
        val inactive = withAlpha(fg, a.trackAlpha)
        val ringStroke = a.ringStroke * RingGeometry.RING_STROKE_RATIO
        val strokeWidth = RingGeometry.flankWifiStroke(ringStroke)
        val innerR = RingGeometry.flankWifiInnerR(ringStroke, IconGeometry.B_R)
        val outerR = RingGeometry.flankWifiOuterR(ringStroke, IconGeometry.B_R)
        STROKE.strokeWidth = strokeWidth
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.style = Paint.Style.STROKE

        // The fan keeps its own sweeps; only their radii come from the ring, so the small arc
        // stays clear of the ring's stroke instead of being drawn onto its edge. Scalars, not
        // a list of triples: this runs for every frame the glyph draws.
        val innerSweep = RingGeometry.FLANK_WIFI_SWEEP_INNER
        val outerSweep = RingGeometry.FLANK_WIFI_SWEEP_OUTER
        val centreDeg = if (isLeft) 180f else 0f

        ARC.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        STROKE.color = if (wifiLevel >= 1) fg else inactive
        c.drawArc(ARC, centreDeg - innerSweep * 0.5f, innerSweep, false, STROKE)

        ARC.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        STROKE.color = if (wifiLevel >= 3) fg else inactive
        c.drawArc(ARC, centreDeg - outerSweep * 0.5f, outerSweep, false, STROKE)
    }

    private fun drawBattery(
        c: Canvas, level: Int, role: Int, fg: Int,
        a: StatusAppearance, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float, charging: Boolean,
        openLeft: Boolean = false, openRight: Boolean = false,
        topHalfDeg: Float = 0f, bottomHalfDeg: Float = 0f,
        leftFlankHalfDeg: Float = 0f, rightFlankHalfDeg: Float = 0f,
    ) {
        val ringStroke = a.ringStroke * RingGeometry.RING_STROKE_RATIO
        STROKE.strokeWidth = ringStroke
        val animated = a.animatesOutline(charging, level)
        // Breathing moves the whole ring; the other two move a light along it. One
        // vocabulary per style, so the outline and the bolt always read as one piece.
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f

        STROKE.color = withAlpha(fg, a.trackAlpha)
        batteryRing(
            c, 0f, 1f, gapStart, gapEnd, sweep, start, openLeft, openRight,
            leftFlankHalfDeg, rightFlankHalfDeg, topHalfDeg, bottomHalfDeg,
        )

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            batteryRing(
                c, 0f, min(1f, level / 100f), gapStart, gapEnd, sweep, start, openLeft, openRight,
                leftFlankHalfDeg, rightFlankHalfDeg, topHalfDeg, bottomHalfDeg,
            )
        }
        if (!animated) {
            return
        }
        val phase = animPhase()
        when (a.boltAnim) {
            Prefs.BOLT_ANIM_FLOW -> {
                // A light running the ring, in the charged colour lifted towards white.
                val head = flowHeadColor(role)
                for (i in 0 until RING_FLOW_SEGMENTS) {
                    val to = phase - i * RING_FLOW_SPAN
                    STROKE.color = withAlpha(head, flowAlpha(i, RING_FLOW_SEGMENTS))
                    flowArc(c, to - RING_FLOW_SPAN, to, gapStart, gapEnd, sweep, start)
                }
            }
            Prefs.BOLT_ANIM_SPARK -> {
                // Electric sparks with radiant white core, charging aura, and fading comet trail
                val radius = a.ringStroke * RING_SPARK_SCALE
                for (i in 0 until RING_SPARK_COUNT) {
                    val p = (phase + i / RING_SPARK_COUNT.toFloat()) % 1f
                    // Trailing sparks
                    for (tail in 2 downTo 1) {
                        val tp = (p - tail * 0.022f + 1f) % 1f
                        if (ringPoint(tp, gapStart, gapEnd, sweep, start)) {
                            FILL.color = withAlpha(role, if (tail == 1) 140 else 60)
                            c.drawCircle(RING_POINT[0], RING_POINT[1], radius * (if (tail == 1) 0.7f else 0.45f), FILL)
                        }
                    }
                    // Main spark with glowing aura and brilliant core
                    if (ringPoint(p, gapStart, gapEnd, sweep, start)) {
                        FILL.color = withAlpha(role, 120)
                        c.drawCircle(RING_POINT[0], RING_POINT[1], radius * 1.4f, FILL)
                        FILL.color = Color.WHITE
                        c.drawCircle(RING_POINT[0], RING_POINT[1], radius * 0.85f, FILL)
                    }
                }
            }
            else -> Unit
        }
    }



    /**
     * The point on the drawn arc at visual progress [p], written into [RING_POINT].
     *
     * The mapping is the one [batteryRing] uses: progress counts only the ink, so the
     * mouth is stepped over rather than walked through.
     */
    private fun ringPoint(
        p: Float, gapStart: Float, gapEnd: Float, sweep: Float, start: Float,
    ): Boolean {
        if (p !in 0f..1f) return false
        val q = if (p <= gapStart) p else gapEnd + (p - gapStart)
        val angle = Math.toRadians((start + q * sweep).toDouble())
        RING_POINT[0] = (IconGeometry.B_CX + IconGeometry.B_R * cos(angle)).toFloat()
        RING_POINT[1] = (IconGeometry.B_CY + IconGeometry.B_R * sin(angle)).toFloat()
        return true
    }


    /**
     * Draws the open battery arc from progress [from] to [to], skipping the segment
     * hidden behind the top gap.
     *
     * Both ends are progress of the arc that is *actually drawn*, not of the full
     * path: the two segments the mouth leaves behind divide the level between them,
     * so 50% ends exactly where the left segment does and the mouth never consumes
     * any of it.
     *
     * [sweep]/[start] are passed in rather than read from [IconGeometry], because
     * the arc's angular span depends on whether anything occupies the bottom of the
     * ring this frame - see `bottomOpen`.
     */
    internal fun batteryRing(
        c: Canvas, fromIn: Float, toIn: Float, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
        leftFlankHalfDeg: Float = 0f, rightFlankHalfDeg: Float = 0f,
        topHalfDeg: Float = 0f, bottomHalfDeg: Float = 0f,
    ) {
        val from = clamp01(fromIn)
        val to = clamp01(toIn)
        if (to <= from) {
            return
        }
        // The split path only describes a mouth at 12 o'clock; a bottom mouth or a flank cut
        // needs the segment walk.
        if (!openLeft && !openRight && sweep >= 360f) {
            val mouthStart = clamp01(gapStart)
            val mouthEnd = clamp01(gapEnd)
            // Path length that actually carries ink; the level is spread over it.
            val drawn = mouthStart + (1f - mouthEnd)
            if (drawn <= 0f) {
                return
            }
            val visibleFrom = from * drawn
            val visibleTo = to * drawn
            // First segment: visible progress [0, mouthStart] is path [0, mouthStart].
            val firstEnd = min(visibleTo, mouthStart)
            if (firstEnd > visibleFrom) {
                arcByProgress(
                    c, visibleFrom, firstEnd, IconGeometry.B_CX, IconGeometry.B_CY,
                    IconGeometry.B_R, sweep, start,
                )
            }
            // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
            val secondFrom = max(visibleFrom, mouthStart)
            if (visibleTo > secondFrom) {
                arcByProgress(
                    c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    IconGeometry.B_CX, IconGeometry.B_CY, IconGeometry.B_R, sweep, start,
                )
            }
            return
        }

        // `SEGMENTS` is a scratch buffer reused by every call: this runs twice per drawn
        // frame, and the segment list used to be a List<Pair<Float,Float>> (plus its
        // filter copy) thrown away immediately.
        val segCount = buildRingSegments(
            SEGMENTS, GAPS, start, topHalfDeg, bottomHalfDeg, openLeft, openRight,
            leftFlankHalfDeg, rightFlankHalfDeg,
            Math.toDegrees((STROKE.strokeWidth / IconGeometry.B_R).toDouble()).toFloat(),
        )
        var totalDrawn = 0f
        for (i in 0 until segCount) totalDrawn += SEGMENTS[i * 2 + 1]
        if (totalDrawn <= 0f) return

        val startDrawn = from * totalDrawn
        val endDrawn = to * totalDrawn
        var curDist = 0f

        for (i in 0 until segCount) {
            val segStartDeg = SEGMENTS[i * 2]
            val segSweepDeg = SEGMENTS[i * 2 + 1]
            val segEndDist = curDist + segSweepDeg

            val drawStart = max(startDrawn, curDist)
            val drawEnd = min(endDrawn, segEndDist)

            if (drawEnd > drawStart) {
                val segDrawStartDeg = segStartDeg + (drawStart - curDist)
                val segDrawSweep = drawEnd - drawStart
                arc(c, IconGeometry.B_CX, IconGeometry.B_CY, IconGeometry.B_R, segDrawStartDeg, segDrawSweep)
            }
            curDist = segEndDist
        }
    }


    /**
     * Fills [out] with the ring's drawable segments as `(startDeg, sweepDeg)` pairs and
     * answers how many were written.
     *
     * The openings are collected as angular gaps and the arcs are their complement, so the
     * 12 and 6 o'clock mouths, the two flank cuts and any overlap between them are handled by
     * one rule instead of by hard-coded angles. Caller-owned buffers, because this runs for
     * the track and for the fill of every frame.
     */
    private fun buildRingSegments(
        out: FloatArray, gaps: FloatArray, arcStart: Float, topHalf: Float, bottomHalf: Float,
        openLeft: Boolean, openRight: Boolean,
        leftFlankHalf: Float, rightFlankHalf: Float, minSegDeg: Float,
    ): Int {
        var gapCount = 0
        if (topHalf > 0f) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 270f - topHalf, 270f + topHalf)
        }
        if (bottomHalf > 0f) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 90f - bottomHalf, 90f + bottomHalf)
        }
        if (openLeft) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 180f - leftFlankHalf, 180f + leftFlankHalf)
        }
        if (openRight) {
            gapCount = addRingGap(gaps, gapCount, arcStart, -rightFlankHalf, rightFlankHalf)
        }
        // At most four gaps, so an insertion sort over the starts is the simplest thing that
        // is obviously correct.
        for (i in 1 until gapCount) {
            var j = i
            while (j > 0 && gaps[j * 2] < gaps[(j - 1) * 2]) {
                val s = gaps[j * 2]
                val e = gaps[j * 2 + 1]
                gaps[j * 2] = gaps[(j - 1) * 2]
                gaps[j * 2 + 1] = gaps[(j - 1) * 2 + 1]
                gaps[(j - 1) * 2] = s
                gaps[(j - 1) * 2 + 1] = e
                j--
            }
        }
        val lapEnd = arcStart + 360f
        var count = 0
        var cursor = arcStart
        for (i in 0 until gapCount) {
            val from = gaps[i * 2]
            val to = gaps[i * 2 + 1]
            if (from > cursor) {
                count = addSegment(out, count, cursor, from - cursor, minSegDeg)
            }
            if (to > cursor) cursor = to
        }
        if (cursor < lapEnd) {
            count = addSegment(out, count, cursor, lapEnd - cursor, minSegDeg)
        }
        return count
    }


    /**
     * Records one opening of the ring as a gap inside `[anchor, anchor + 360)`.
     *
     * @return the new gap count
     */
    private fun addRingGap(
        gaps: FloatArray, count: Int, anchor: Float, fromDeg: Float, toDeg: Float,
    ): Int {
        if (count * 2 + 1 >= gaps.size) return count
        var from = fromDeg
        while (from < anchor) from += 360f
        while (from >= anchor + 360f) from -= 360f
        val to = minOf(from + (toDeg - fromDeg), anchor + 360f)
        gaps[count * 2] = from
        gaps[count * 2 + 1] = to
        return count + 1
    }


    /**
     * Appends one segment unless it is too short to draw as a piece of the ring.
     *
     * A wide flank cut can leave a sliver between itself and the next opening. Shorter than
     * its own two round caps it draws as a blob rather than as arc, so the two openings are
     * allowed to merge instead of leaving a nub.
     */
    private fun addSegment(
        out: FloatArray, count: Int, startDeg: Float, sweepDeg: Float, minSegDeg: Float,
    ): Int {
        if (sweepDeg <= 0f || sweepDeg < minSegDeg) return count
        if (count * 2 + 1 >= out.size) return count
        out[count * 2] = startDeg
        out[count * 2 + 1] = sweepDeg
        return count + 1
    }


    private fun arcByProgress(
        c: Canvas, from: Float, to: Float, cx: Float, cy: Float, r: Float,
        sweep: Float, start: Float,
    ) {
        arc(c, cx, cy, r, start + from * sweep, (to - from) * sweep)
    }



    /**
     * The arcs and the dot at their reference coordinates, redesigned referencing
     * CombinedStatus [RoundedWifiGeometry].
     *
     * 3-level structure matching MagicOS signal (0..3):
     * - Level 1: Dot only
     * - Level 2: Dot + middle arc
     * - Level 3: Dot + middle arc + outer arc
     *
     * Inactive levels are rendered using trackAlpha when trackAlpha > 0.
     */
    internal fun drawWifiArcs(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val strokeWidth = min(a.arcStroke.toFloat(), IconGeometry.W_MAX_STROKE)
        STROKE.strokeWidth = strokeWidth
        val inactive = withAlpha(fg, a.trackAlpha)
        val hasTrack = a.trackAlpha > 0

        // Outer arc (level >= 3)
        if (level >= 3) {
            STROKE.color = fg
            c.drawPath(WIFI_OUTER, STROKE)
        } else if (hasTrack) {
            STROKE.color = inactive
            c.drawPath(WIFI_OUTER, STROKE)
        }

        // Middle arc (level >= 2)
        if (level >= 2) {
            STROKE.color = fg
            c.drawPath(WIFI_MIDDLE, STROKE)
        } else if (hasTrack) {
            STROKE.color = inactive
            c.drawPath(WIFI_MIDDLE, STROKE)
        }

        // Dot (level >= 1)
        if (level >= 1) {
            FILL.color = fg
            c.drawPath(WIFI_DOT, FILL)
        } else if (hasTrack) {
            FILL.color = inactive
            c.drawPath(WIFI_DOT, FILL)
        }
    }
}
