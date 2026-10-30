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

package io.github.odomu.honorduo.ui.render.geometry

import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The ring arrangement: the battery arc with its flow, the flank rows, the notch
 * pieces and the readings that stand in the three zones.
 */
object RingGeometry {

    /**
     * The *reference* width of the ring's 12 o'clock opening, in degrees.
     *
     * The mouths are no longer cut at a fixed angle: [notchHalfDeg] fits each one to the
     * piece it holds, and a Wi-Fi fan needs about 56 degrees where this left 96. What
     * survives here is the ceiling for that fit, and the width used by the pieces whose ink
     * cannot be measured up front - a text reading, and a row of level beads (whose span is
     * [#BEAD_SPAN_DEG] plus clearance, inside this mouth).
     *
     * It also still sets [#B_SWEEP], which is the closed-ring reference the beam and ink-box
     * checks are written against.
     */
    const val GAP_WIDTH_DEG = 96f


    /**
     * The angular span the four card beads occupy, in degrees.
     *
     * Independent of [#GAP_WIDTH_DEG] on purpose. The notch is wider than the
     * beads need, so the arc's ends keep their distance from them; if the beads were
     * spread to fill whatever the opening allows, widening the notch would drag the
     * beads outward with it and the clearance would never change.
     *
     * 60.4 degrees is the span the beads already have at the default stroke, where
     * the spacing looked right: four beads, one diameter apart at the ends.
     */
    const val BEAD_SPAN_DEG = 60.4f


    /**
     * The 12 o'clock opening, as path progress in `0..1` of the sweep.
     *
     * Converted from [#GAP_WIDTH_DEG] so the notch keeps its width in
     * *degrees* whatever the sweep is. Centred on 0.5, which is 12 o'clock.
     */
    const val GAP_START_IDLE = 0.5f - GAP_WIDTH_DEG / 2f / IconGeometry.B_SWEEP

    const val GAP_END_IDLE = 0.5f + GAP_WIDTH_DEG / 2f / IconGeometry.B_SWEEP


    /** The charging mouth: the same width, as it always was for a bolt or two digits. */
    const val GAP_START_CHARGE = GAP_START_IDLE

    const val GAP_END_CHARGE = GAP_END_IDLE


    /**
     * The 12 o'clock gap used by the dual-SIM reading: exactly as wide as the
     * opening the arc already leaves bare at the bottom, so the two rows of dots
     * sit in matching mouths.
     *
     * [#GAP_START_IDLE] is sized for a bolt or two digits. Measured
     * against the dots it is too narrow: the top row is [#DOTS] mirrored
     * about [#B_CY], so its outer dots reach the arc's own radius, and the
     * rounded stroke ends are then only ~10.4 units from the outer dot centres
     * while a cap (stroke/2) plus a dot ([#DUAL_DOT_R]) is 14. The cap and
     * the dot merge into one blob and the top row reads as if the ring had cut it
     * off. The bottom row never had this problem because it sits in the arc's
     * natural opening, which is 32.5 degrees wider.
     *
     * Opening the notch to that natural width is what "the rows are symmetric"
     * means geometrically, and it hands the top row the same clearance the bottom
     * row has (~10 units instead of an overlap of ~3.6).
     *
     * Both bounds are derived from [#B_SWEEP] rather than typed in: the
     * bare part of the circle is `360 - B_SWEEP`, so its half is
     * `180 / B_SWEEP - 0.5` of the path - measured either side of t=0.5,
     * which is 12 o'clock.
     */
    const val GAP_START_DUAL = 1f - 180f / IconGeometry.B_SWEEP

    const val GAP_END_DUAL = 180f / IconGeometry.B_SWEEP


    /**
     * Gap used when nothing is drawn in the 12 o'clock slot.
     *
     * A zero-width gap at position 0 makes the ring draw as one unbroken arc:
     * with `gapStart == gapEnd == 0` the two halves of
     * `batteryRing`'s split collapse into a single 0..to sweep. An empty
     * mouth is not a feature, so the ring simply closes.
     */
    const val GAP_NONE = 0f


    /**
     * Cut between the top third and the middle one.
     *
     * **38, unchanged.** Moving this was tried and reverted: the cut is not
     * only a size hint, it is the line `IconZones` hit-tests and the line
     * `tests/drawing-zones.ps1` attributes every painted pixel to. Nudging it
     * to 40 re-attributed the top of the Wi-Fi arcs to the top third and the harness
     * failed with "WIFI loses ink only in the thirds that name it: got TOP".
     *
     * The type's size problem is therefore solved on the type's own terms - see
     * [#zoneTextRoomH] - rather than by redividing a box whose division other
     * things depend on.
     */
    const val ZONE_CUT_TOP = 38f


    /**
     * Cut between the middle third and the bottom one.
     *
     * **86, raised from 92.** This is the one cut that had to move, and the
     * reason is measured rather than aesthetic: a placed reading is sized by the
     * room its third gives, and the bottom third used to get
     * `2 * (104 - 92) = 24` units - about 15pt for a reading the user asked to
     * be 36. Moving the cut up 6 units gives 36, which is the whole difference
     * between "half the size you asked for" and "what the slider says".
     *
     * What stops it going further is the middle third's tallest drawn piece: a
     * bolt fitted to the slot box is 44 units tall about a centre line at 64, so it
     * reaches y = 86. At 86 the two just touch, which is why the harness is what
     * decides this number rather than a round figure.
     *
     * The top cut stays at 38 on purpose: it is also the line the editor
     * hit-tests, and moving it re-attributed the top of the Wi-Fi arcs to another
     * third.
     */
    const val ZONE_CUT_BOTTOM = 86f


    /** The level-dot placement box in the top third, in design units. */
    const val DOT_BOX_TOP = 28f


    /** The level-dot placement box in the bottom third, in design units. */
    const val DOT_BOX_BOTTOM = 20f


    /**
     */
    const val DOT_BOX_CENTRE = 44f


    /**
     * Clearance kept between the end bead and the arc's own round cap, in design units.
     *
     * Both are antialiased, so "touching exactly" still interleaves pixels; one unit
     * is the fringe.
     */
    const val ARC_ROW_CLEARANCE = 1f


    /**
     * Clearance kept between a flank bead row and the arc's own round cap, in design units.
     *
     * The flank row replaces a stretch of the ring rather than sitting in a mouth the ring
     * already left, so this is what stops the end bead from merging into the arc's cap and
     * reading as the arc's rounded end instead of as a dot - the defect that made a
     * four-bead flank look like two beads with a blunt ring.
     */
    const val FLANK_ROW_CLEARANCE = 3f


    /**
     * Clear space between a piece in the ring's 12/6 o'clock notch and the arc's round ends.
     *
     * The mouth is sized to the piece now instead of being a fixed 96 degrees, so this is
     * what keeps the piece's ink from touching the stroke's end caps.
     */
    const val NOTCH_PIECE_CLEARANCE = 3f


    /**
     * Extra height the notch gives the Wi-Fi arcs and the bolt.
     *
     * The notch's bands were sized for a row of level dots (28 and 20 design units) and the
     * Wi-Fi arcs are 37 units tall: fitted into the dot band they came out at about
     * two-thirds of what the ring allows, which is what "the pieces look small" was.
     */
    const val NOTCH_PIECE_EXTRA = 9f


    /**
     * How far the icon in a 12/6 o'clock mouth moves toward that mouth, in design units.
     *
     * The band's centre sat nine units inside the arc's own line, so a fan drawn there was
     * half buried: the mouth was open but the piece filled it from the inside, which reads as
     * "there is no opening". Moving the piece out is what makes it straddle the gap the way
     * the capsule's openings already do. The box grows by the same amount on both sides, so
     * the piece still fits whatever the mouth.
     */
    const val NOTCH_PIECE_LIFT = 6f


    /** Inset the ring keeps between a slot's ink and the slot's own box, in design units. */
    const val RING_SLOT_INSET = 2.5f


    /** Wi-Fi scale boost in the 12 o'clock mouth; the middle band gets its own, larger one. */
    const val RING_WIFI_BOOST_TOP = 0.88f

    const val RING_WIFI_BOOST_CENTRE = 1.25f


    /**
     * Clear space between the ring's own stroke and the flank's inner Wi-Fi arc.
     *
     * The flank arcs are threaded onto the ring's outside, so without this the small arc
     * is drawn straight onto the ring's stroke edge and reads as a thickening of it rather
     * than as its own mark. It is wide on purpose: at the status bar's size the arcs are a
     * few pixels, and anything tighter than this turns the pair into one thick band.
     */
    const val FLANK_WIFI_GAP = 4f


    /** Clear space between the flank's two Wi-Fi arcs. */
    const val FLANK_WIFI_ARC_GAP = 4f


    /**
     * The flank arcs' stroke, as a share of the ring's own drawn stroke.
     *
     * Nearly the ring's weight: thinner marks read as a second, fainter outline of the
     * ring at status-bar size, which is what "too small" looked like.
     */
    const val FLANK_WIFI_STROKE_RATIO = 0.85f


    /** The flank fan's angular width, in degrees: the near arc and the far one. */
    const val FLANK_WIFI_SWEEP_INNER = 46f

    const val FLANK_WIFI_SWEEP_OUTER = 70f


    /**
     * The ring's drawn stroke as a fraction of the stored `ringStroke` setting.
     *
     * The stored value is the ring's visual weight; the arc is drawn at this share of it so
     * the beads and the flank arcs keep their proportions when the slider moves.
     */
    const val RING_STROKE_RATIO = 0.68f


    /**
     * Clearance kept between a fitted line's edge and the arc, in design units.
     *
     * Both are antialiased, so "touching exactly" still interleaves pixels. Small
     * enough to cost a fraction of a point at the default sizes.
     */
    const val ZONE_TYPE_FRINGE = 1f


    /**
     * Slack kept between a fitted line and its third's boundary, in design units.
     *
     * Not a tuning knob. It matters because this cap is the one the draw path
     * trusts: when the width budget binds, the size lands exactly on the cap, and
     * "exactly" is where the antialiased fringe of the glyph meets the antialiased
     * edge of the ink above it. Two units is enough for a fringe at the largest
     * sizes the sliders reach, and small enough that it costs a fifth of a point at
     * the default size.
     */
    const val ZONE_TEXT_INSET = 2.5f


    /**
     * The vertical centre each zone places its piece on: `0` top,
     * `1` middle, `2` bottom.
     *
     * Fixed numbers rather than the middles of the thirds, and that is
     * deliberate: the drawn figures - the arcs, the bolt, the dot rows - have their
     * own sizes and were tuned against these centres, so re-centring them whenever
     * a cut line moves would shift drawings that nobody asked to move. The cuts
     * divide the *hot zones*; these say where a piece is painted.
     *
     * Text is the one piece that is also bounded by the third it sits in (a
     * reading has to fit the band it belongs to), which is why
     * [#zoneTextRoomH] measures around these centres rather than around the
     * cuts' middles - a mismatch there would fit the type to a band it is not drawn
     * in.
     */
    @JvmField
    val ZONE_CY = floatArrayOf(19f, 64f, 104f)


    /**
     * The vertical room a placed text piece may use in zone `z`, in design
     * units.
     *
     * Two different rules, by design:
     *
     *   - **Top and bottom** are limited by the *signal-dot box* - the
     *       28 and 20 units the level dots were designed into. Those two slots exist
     *       to hold a row of dots on the ring's own openings, so a reading placed
     *       there is a guest in the dots' space and takes the dots' measure.
     *   - **Middle** is limited by the ring's interior, which is the largest
     *       plainly available area in the glyph and the place a reading is actually
     *       meant to sit.
     *
     * Stating it this way is what keeps the three slots from having to share one
     * compromised number: the middle gets the room it visibly has, while the two end
     * slots stay in proportion with the dots they stand in for.
     */
    @JvmStatic
    fun zoneTextRoomH(z: Int): Float {
        if (z == 1) {
            return 2f * (IconGeometry.B_R - Prefs.DEF_RING_STROKE) - 2f * ZONE_TEXT_INSET - 2f * ZONE_TYPE_FRINGE
        }
        return if (z == 0) 32f else 26f
    }


    /**
     * The height of the level-dot box a slot stands in for: `28` in the top
     * third and `20` in the bottom.
     *
     * These are the two figures `IconRenderer.drawFreeRing` fits the dot
     * rows into (`height = z == 0 ? 28 : z == 1 ? 44 : 20`). They are named
     * here because the type cap now uses them too - see [#zoneTextRoomH] - and
     * a second copy of "28" in the renderer is how the two would drift.
     */
    @JvmStatic
    fun signalDotBoxH(z: Int): Float {
        return if (z == 0) DOT_BOX_TOP else DOT_BOX_BOTTOM
    }


    /**
     * The largest bead radius the ink box can hold, in design units.
     *
     * The end thirds thread their beads onto the ring's own arc, so the outermost one
     * sits at radius `B_R` and its outer edge reaches `B_R + r` from the ring
     * centre - straight up at 12 o'clock, or straight down at 6. The ink box is the square
     * that scales the glyph, so `INK_W / 2` is the furthest any ink may reach; this is
     * that limit solved for the bead.
     *
     * It equals `MAX_RING_STROKE / 2`, which is not a coincidence: the box was
     * sized so the shipped bead - whose diameter is the ring's thickness - just fits at the
     * thickest stroke. That is what makes this a real ceiling rather than a spare margin,
     * and why the dot-size setting has to stop here instead of growing the box: enlarging
     * the box would shrink the glyph for every install that never touched the slider.
     */
    @JvmStatic
    fun maxBeadR(): Float {
        return IconGeometry.INK_W / 2f - IconGeometry.B_R
    }


    /** The radius of one flank bead at scale 1, by column count. */
    @JvmStatic
    fun flankBeadR(columns: Int): Float = if (columns == 5) 4.2f else 4.9f


    /** Gap kept between two flank beads, in design units. */
    const val FLANK_BEAD_GAP = 2f


    /** The flank bead the row draws with: the user's size, capped by the ink box. */
    @JvmStatic
    fun flankDotR(columns: Int, scale: Float): Float =
        minOf(flankBeadR(columns) * maxOf(0.01f, scale), maxBeadR())


    /**
     * The flank Wi-Fi arcs' stroke, from the ring's own drawn stroke.
     */
    @JvmStatic
    fun flankWifiStroke(ringStroke: Float): Float = ringStroke * FLANK_WIFI_STROKE_RATIO


    /**
     * Radius of the flank's near Wi-Fi arc, measured from the ring's centre.
     *
     * Derived from the ring's stroke and [FLANK_WIFI_GAP] rather than fixed: the arcs sit
     * outside the ring, so a thicker ring has to push them outward or the inner one is drawn
     * onto the ring's edge - the fault that made the small arc look like a thick spot on the
     * ring itself. Two scalars rather than an array, because this is read per frame by the
     * draw path and per measure pass by [hostAspect].
     */
    @JvmStatic
    fun flankWifiInnerR(ringStroke: Float, baseR: Float): Float {
        val wifiStroke = flankWifiStroke(ringStroke)
        return baseR + ringStroke * 0.5f + FLANK_WIFI_GAP + wifiStroke * 0.5f
    }


    /** Radius of the flank's far Wi-Fi arc: the near one plus a stroke and the arc gap. */
    @JvmStatic
    fun flankWifiOuterR(ringStroke: Float, baseR: Float): Float =
        flankWifiInnerR(ringStroke, baseR) + flankWifiStroke(ringStroke) + FLANK_WIFI_ARC_GAP


    /**
     * How far one flank's Wi-Fi arcs reach past the ring's own ink box, in design units.
     *
     * The ring's box is the ring: [INK_W] is the stroke's outer edge plus the widest bead. The
     * flank arcs are the one piece drawn deliberately outside it, so this is the extra width
     * [hostAspect] asks for, and therefore what stops the host clipping them.
     */
    @JvmStatic
    fun ringFlankWifiExtra(a: StatusAppearance?): Float {
        if (a == null || !a.ring) return 0f
        val left = a.ringZones.getOrNull(Prefs.ZONE_LEFT)?.contains(Prefs.PART_WIFI) == true
        val right = a.ringZones.getOrNull(Prefs.ZONE_RIGHT)?.contains(Prefs.PART_WIFI) == true
        if (!left && !right) return 0f
        val stroke = a.ringStroke * RING_STROKE_RATIO
        val reach = flankWifiOuterR(stroke, IconGeometry.B_R) + flankWifiStroke(stroke) * 0.5f
        // One unit of slack for the antialiased edge, or the outermost arc's fringe is cut
        // by the view it asked for.
        return maxOf(0f, reach + 1f - IconGeometry.INK_W * 0.5f)
    }


    /**
     * A piece's drawn scale inside a bounded slot: the room it is given, times the user's
     * size, capped by that same room.
     *
     * The cap is what keeps the ink inside the slot. Multiplying the fitted scale by a size
     * setting above 100% draws the piece past the band and past the ink box, and the host
     * clips it - which is what "the Wi-Fi is cut off" was on the capsule's openings.
     */
    @JvmStatic
    fun fittedScale(room: Float, userFactor: Float): Float =
        minOf(room * maxOf(0.01f, userFactor), room)


    /** The room a notch piece has in its band, before the user's size is applied. */
    @JvmStatic
    fun notchRoom(z: Int, part: Int, a: StatusAppearance): Float {
        val slotW = notchSlotW(a.ringStroke)
        val slotH = notchSlotH(z, part)
        return when (part) {
            Prefs.PART_WIFI -> {
                val extra = maxOf(0f, a.arcStroke - IconGeometry.W_STROKE)
                minOf(slotW / (IconGeometry.W_INK_W + extra), slotH / (IconGeometry.W_INK_H + extra))
            }
            Prefs.PART_BOLT -> minOf(slotW / 15f, slotH / IconGeometry.BOLT_INK_H)
            else -> 1f
        }
    }


    /** The scale the notch draws a Wi-Fi fan at, in ink units per design unit. */
    @JvmStatic
    fun notchWifiScale(z: Int, a: StatusAppearance): Float {
        val boost = if (z == 1) RING_WIFI_BOOST_CENTRE else RING_WIFI_BOOST_TOP
        val room = notchRoom(z, Prefs.PART_WIFI, a) * boost
        return fittedScale(room, a.wifiScaleFactor())
    }


    /** The scale the notch draws the bolt at. */
    @JvmStatic
    fun notchBoltScale(z: Int, a: StatusAppearance): Float =
        fittedScale(notchRoom(z, Prefs.PART_BOLT, a), a.boltScaleFactor())


    /** The width of the box a notch piece is fitted to, from the ring's stored stroke. */
    @JvmStatic
    fun notchSlotW(ringStroke: Int): Float = 78f - ringStroke - 2f * RING_SLOT_INSET


    /** The height of the box a notch piece is fitted to; Wi-Fi and the bolt get extra. */
    @JvmStatic
    fun notchSlotH(z: Int, part: Int): Float {
        val base = if (z == 0) DOT_BOX_TOP else DOT_BOX_BOTTOM
        return if (part == Prefs.PART_WIFI || part == Prefs.PART_BOLT) {
            base + NOTCH_PIECE_EXTRA
        } else {
            base
        }
    }


    /**
     * The extra ink box the ring needs when a mouth holds an icon, on **each** side.
     *
     * Zero unless one of the two mouths holds a Wi-Fi fan or the bolt: a text reading keeps the
     * band's own centre, so nothing has to move for it.
     */
    @JvmStatic
    fun ringInkPad(a: StatusAppearance?): Float {
        if (a == null || !a.ring) return 0f
        val top = a.ringZones.getOrNull(Prefs.ZONE_TOP)?.any { isNotchIcon(it) } == true
        val bottom = a.ringZones.getOrNull(Prefs.ZONE_BOTTOM)?.any { isNotchIcon(it) } == true
        return if (top || bottom) NOTCH_PIECE_LIFT else 0f
    }

    private fun isNotchIcon(id: Int): Boolean = id == Prefs.PART_WIFI || id == Prefs.PART_BOLT


    /** The ring's ink height for this frame: the reference box plus [ringInkPad] on both sides. */
    @JvmStatic
    fun ringInkH(a: StatusAppearance?): Float = IconGeometry.INK_H + 2f * ringInkPad(a)


    /** The top of the ring's ink box, in design units (negative when it grew). */
    @JvmStatic
    fun ringInkTop(a: StatusAppearance?): Float = -ringInkPad(a)


    /**
     * The slot centre the ring draws band `z` at.
     *
     * A mouth's icon is pulled toward its mouth by [NOTCH_PIECE_LIFT]; everything else stays on
     * the band's own centre.
     */
    @JvmStatic
    fun notchCy(z: Int, a: StatusAppearance): Float =
        if (z == 0) ZONE_CY[0] - a.ringSpacingY - ringInkPad(a)
        else ZONE_CY[2] + a.ringSpacingY + ringInkPad(a)


    /**
     * Half the angular width the ring must leave at the 12 or 6 o'clock mouth for [part],
     * in degrees; `0` when the mouth holds nothing.
     *
     * The piece's ink, its clearance and **the arc's own round cap** all have to fit outside
     * the cut point: the cap is a circle of `stroke / 2` centred on the arc's end, so a cut
     * sized to the piece alone leaves the cap sitting on the piece - which reads as "there is
     * no opening". Measured at radius [B_R], the line the arc is drawn on.
     *
     * Only pieces whose ink is known without a text measurement get a fitted mouth: the Wi-Fi
     * arcs and the bolt. Text keeps the reference [GAP_WIDTH_DEG], because its width depends on
     * the string and on the font the user installed.
     */
    @JvmStatic
    fun notchHalfDeg(z: Int, part: Int, a: StatusAppearance): Float {
        if (part == Prefs.PART_NONE) return 0f
        val inkW = when (part) {
            Prefs.PART_WIFI -> IconGeometry.W_INK_W * notchWifiScale(z, a)
            Prefs.PART_BOLT -> 15f * notchBoltScale(z, a)
            else -> return GAP_WIDTH_DEG * 0.5f
        }
        val capR = a.ringStroke * RING_STROKE_RATIO * 0.5f
        val reach = inkW * 0.5f + NOTCH_PIECE_CLEARANCE + capR
        val half = Math.toDegrees(
            asin((reach / IconGeometry.B_R).coerceIn(0f, 1f).toDouble()),
        ).toFloat()
        return half.coerceIn(12f, GAP_WIDTH_DEG * 0.5f)
    }


    /**
     * Half the angular width the ring leaves at a flank for `part`, in degrees; `0` keeps the
     * ring closed there.
     *
     * Every piece may sit on a flank now, so this answers for all of them: a bead row uses its
     * own span (see [flankOpenHalfDeg]), the Wi-Fi fan keeps the ring closed because it is
     * drawn *outside* the stroke, and anything else - a reading, the bolt - is cut for a cell
     * of its reference width.
     */
    @JvmStatic
    fun flankHalfDeg(part: Int, a: StatusAppearance): Float {
        // An empty flank leaves the ring closed; only a piece opens a mouth for itself.
        if (part == Prefs.PART_NONE) return 0f
        val stroke = a.ringStroke * RING_STROKE_RATIO
        val capR = stroke * 0.5f
        val inkHalf = when {
            Prefs.isSignalRow(part) -> {
                val columns = a.signalColumns.coerceIn(4, 5)
                // The row spans BEAD_SPAN_DEG; the cut is that half plus the clearance rule.
                return flankOpenHalfDeg(columns, stroke, flankDotR(columns, a.dotScaleFactor()))
            }
            part == Prefs.PART_WIFI -> return 0f
            part == Prefs.PART_BOLT -> 7.5f * a.boltScaleFactor()
            else -> FLANK_CELL_INK_HALF
        }
        val half = Math.toDegrees(
            asin(((inkHalf + NOTCH_PIECE_CLEARANCE + capR) / IconGeometry.B_R).coerceIn(0f, 1f).toDouble()),
        ).toFloat()
        return half.coerceIn(10f, GAP_WIDTH_DEG * 0.5f)
    }


    /** The widest a flank cell's ink may be, across the flat of its mouth. */
    @JvmStatic
    fun flankCellWidth(halfDeg: Float): Float =
        (2f * IconGeometry.B_R * sin(Math.toRadians(halfDeg.toDouble())).toFloat())
            .coerceAtMost(IconGeometry.INK_W * 0.6f)


    /** The height a flank cell's ink may use: the middle band's, which is the roomiest. */
    const val FLANK_CELL_ROOM_H = DOT_BOX_CENTRE


    /** Reference half-width of a reading on a flank, before the real string is fitted. */
    private const val FLANK_CELL_INK_HALF = 17f


    /** Half the angular span the row's bead centres occupy on the ring, in degrees. */
    @JvmStatic
    fun flankRowHalfDeg(columns: Int, dotR: Float): Float {
        val span = (columns - 1) * (2f * dotR + FLANK_BEAD_GAP) * 0.5f
        return Math.toDegrees((span / IconGeometry.B_R).toDouble()).toFloat()
    }


    /**
     * Half the angular width the ring must be cut at a flank that holds a bead row, in degrees.
     *
     * The row is centred on 9 or 3 o'clock and the arc ends have round caps, so the cut is
     * the row's own half span plus the cap, the bead and [FLANK_ROW_CLEARANCE]. Cutting it
     * at a fixed 60.4 degrees - which is what the flanks did - put the outer two beads
     * exactly on the caps, where they read as the arc's blunt ends: a four-bead flank then
     * looked like two beads. Cutting it at the row's span alone has the same fault.
     *
     * The row is packed at [FLANK_BEAD_GAP], not spread across the notch's
     * [BEAD_SPAN_DEG], so the arc keeps most of its length: a wide cut would have merged
     * the flank opening with the top and bottom ones on a fully loaded ring.
     */
    @JvmStatic
    fun flankOpenHalfDeg(columns: Int, ringStroke: Float, dotR: Float): Float {
        val clearance = dotR + ringStroke * 0.5f + FLANK_ROW_CLEARANCE
        val half = flankRowHalfDeg(columns, dotR) +
            Math.toDegrees((clearance / IconGeometry.B_R).toDouble()).toFloat()
        // A ceiling so an extreme dot size cannot turn the two arcs into a broken circle.
        return minOf(half, 60f)
    }


    /**
     * The dot radius for a placed card row in the top or bottom third, at the reference size.
     *
     * Exactly half the ring's stroke, so each dot's **diameter equals the ring's
     * thickness** and a row of four reads as four beads threaded onto the open arc -
     * the ring cut into segments, drawn in the same ink. That is the whole point of
     * putting these rows in the notches: the notch is where the ring has no arc, so
     * there is nothing to collide with.
     *
     * The earlier version treated the row as something drawn *inside* the
     * ring and capped its radius by the clear chord at the slot's centre line. That was
     * the wrong model and produced the absurd result that a thicker ring forced smaller
     * dots: the notch is 84.9 degrees, so its arc is `R * 1.482 = 76.3` design
     * units long, and four stroke-diameter beads plus their gaps need only 56-60 of
     * that even at the thickest setting.
     *
     * The result is scaled down only if the four beads would not fit the opening,
     * which the numbers above say cannot happen for the shipped stroke range - but the
     * guard stays so a future change to the notch width degrades into smaller dots
     * rather than into overlap.
     *
     * This is the scale-1 case of [int, float, float)][#arcRowDotR(int,]; ask that
     * one when the user's `dot_scale` applies.
     */
    @JvmStatic
    fun arcRowDotR(z: Int, ringStroke: Int, slotWidth: Float): Float {
        return arcRowDotR(z, ringStroke, slotWidth, 1f)
    }


    /**
     * The same, with the user's dot-size multiplier applied.
     *
     * The multiplier scales the *reference* radius and then the opening's room
     * still caps the result. That order is the whole contract: [#arcRowBeadRoomR] is
     * a mechanical limit - four beads and the arc's caps have to fit the gap - while the
     * percentage is a look, so the look is applied first and the limit is allowed to
     * refuse it. Capping before scaling would let a large percentage push the beads past
     * the caps; scaling before capping means a large value simply stops growing.
     *
     * [#maxBeadR()] is the second ceiling, and it is the one the ink box imposes:
     * the bead rides the arc, so scaling it pushes ink outward toward the edge of the box
     * that fits the glyph to its view. This is exactly the `bolt_scale` precedent -
     * a large value crowded against a thick stroke stops growing rather than spilling over
     * - and it is why the size setting never has to shrink the glyph.
     *
     * 1.0 is exactly the old behaviour, so the default and every caller that does not
     * pass a scale go on drawing the bead whose diameter equals the ring's thickness.
     *
     * @param scale `StatusAppearance.dotScaleFactor()`: 1.0 for the reference bead
     */
    @JvmStatic
    fun arcRowDotR(z: Int, ringStroke: Int, slotWidth: Float, scale: Float): Float {
        val fit = arcRowDotRFitted(z, ringStroke, slotWidth)
        val scaled = maxOf(0.5f, fit * maxOf(0.01f, scale))
        // The middle third is not an opening: the row there is fitted to the interior
        // rather than threaded onto a bare arc, so there is no cap to overrun and the
        // user's size is the only limit. The end thirds have the arc's caps to clear.
        if (z == 1) {
            return scaled
        }
        return maxOf(
            0.5f, minOf(
                scaled, minOf(
                    arcRowBeadRoomR(z, ringStroke),
                    maxBeadR()
                )
            )
        )
    }


    /** The reference bead radius for `z`, before any user multiplier. */
    @JvmStatic
    fun arcRowDotRFitted(z: Int, ringStroke: Int, slotWidth: Float): Float {
        // The middle third is not a notch: the ring runs past it on both sides, so a row
        // there has to fit the space between the arcs rather than being threaded onto
        // one. It keeps the compact proportions it has always had - the dot box divided
        // into four - which is also what the aggregate meter uses for a single row.
        if (z == 1) {
            return maxOf(0.5f, minOf(DOT_BOX_CENTRE / 3f / 2f, slotWidth / 18f))
        }
        // The end thirds sit in the ring's own openings, where a bead as thick as the
        // stroke is what makes the row read as outline segments. Four beads plus the
        // arc's caps have to fit that opening, which the numbers say they always do for
        // the shipped stroke range.
        val preferred = maxOf(1f, ringStroke * 0.5f)
        return maxOf(0.5f, minOf(preferred, arcRowBeadRoomR(z, ringStroke)))
    }


    /**
     * The largest bead radius that still lets four of them sit inside the opening.
     *
     * Four beads at diameter `2r`, spaced at most one diameter apart, span
     * `3 * 2r + 2r = 8r` of arc, and the caps of the arc itself eat a
     * `stroke/2` at each end.
     */
    @JvmStatic
    fun arcRowBeadRoomR(z: Int, ringStroke: Int): Float {
        val opening = arcOpeningLength(z)
        // Both ends of that opening are the arc's own round caps, each reaching stroke/2
        // in, plus a unit of clearance so the end bead's antialiased edge does not meet
        // the cap's.
        val usable = maxOf(0f, opening - ringStroke - 2f * ARC_ROW_CLEARANCE)
        return maxOf(0.5f, usable / 8f)
    }


    /**
     * The length of the bare arc a zone's row is threaded onto, in design units.
     *
     * The top third uses the 12 o'clock notch; the bottom third uses the arc's own
     * opening. Both are [#GAP_WIDTH_DEG] wide by construction, so they measure
     * the same.
     */
    @JvmStatic
    fun arcOpeningLength(z: Int): Float {
        if (z == 2) {
            return ((IconGeometry.B_R * Math.toRadians((360f - IconGeometry.B_SWEEP).toDouble()))).toFloat()
        }
        return ((IconGeometry.B_R * Math.toRadians((GAP_WIDTH_DEG).toDouble()))).toFloat()
    }


    /**
     * The horizontal room a placed text piece may use in zone `z`, in design
     * units.
     *
     * A single width budget cannot be right, because the ring is round: the room
     * left at a given height is that height's *chord*, and it shrinks toward
     * the top and bottom of the glyph. Fitting to the chord is what stops a wide
     * reading from clipping the arc at its corners - which is what a budget measured
     * only at the centre line did.
     */
    @JvmStatic
    fun zoneTextRoomW(z: Int, ringStroke: Int, inkH: Float): Float {
        if (z != 1) {
            // The end thirds sit in the ring's own openings, where there is no arc across
            // the slot at all - the ink there is bounded by the arc's END CAPS, not by a
            // chord that the text's own height would narrow.
            //
            // Measuring the chord there was wrong twice over: it made the budget depend on
            // the line's height (so the fit oscillated between two values instead of
            // converging), and at the top it collapsed the budget to about 20 units the
            // moment the text's upper edge passed the circle's apex - a cliff that no
            // amount of iterating could settle on.
            //
            // The room in an opening is its chord: the straight distance between the two
            // caps, which is fixed by the opening's width, not by the text.
            return maxOf(0f, openingChord(z, ringStroke.toFloat()))
        }
        val centre = zoneTextCy(z, ringStroke)
        val half = maxOf(0f, inkH) * 0.5f
        // The middle third is inside the ring, so its budget really is a chord - and one
        // that narrows as the line grows, which is why the caller iterates.
        val rowA = centre - half
        val rowB = centre + half
        val binding =
            if (abs(rowA - IconGeometry.B_CY) > abs(rowB - IconGeometry.B_CY)) rowA else rowB
        val dy = binding - IconGeometry.B_CY
        val inside = IconGeometry.B_R.toDouble() * IconGeometry.B_R - dy * dy
        if (inside <= 0) {
            return maxOf(0f, IconGeometry.gapClearWidth(ringStroke.toFloat()) - 2f * ZONE_TYPE_FRINGE)
        }
        val chord = ((2.0 * sqrt(inside))).toFloat()
        // One stroke of ink lies inside the arc (half at each side), plus the
        // antialiased rim of both the glyph and the arc.
        return maxOf(0f, chord - ringStroke - 4f * ZONE_TYPE_FRINGE)
    }


    /**
     * The straight distance between the two arc caps of a zone's opening, in design
     * units.
     *
     * That is the width a piece standing in the opening has: the opening is a sector
     * bounded by the arc's round ends, so its usable width is the chord across it - a
     * fixed number, unlike the interior chord which shrinks towards the top of the
     * circle. Insets a stroke's worth so the ink clears both caps.
     */
    @JvmStatic
    fun openingChord(z: Int, ringStroke: Float): Float {
        val halfAngle = Math.toRadians(GAP_WIDTH_DEG * 0.5)
        val chord = sin(halfAngle).toFloat() * 2f * IconGeometry.B_R
        return maxOf(0f, chord - ringStroke - 4f * ZONE_TYPE_FRINGE)
    }


    /**
     * The centre a placed piece of *text* is drawn on, per zone.
     *
     * The same centres the drawn figures use, deliberately: text that shifted
     * whenever a cut line moved would sit off-centre from the arcs, the bolt and the
     * dot rows it shares a third with, and every fit above is measured about this
     * same point so the two always agree.
     */
    @JvmStatic
    fun zoneTextCy(z: Int, ringStroke: Int): Float {
        return ZONE_CY[z]
    }

    @JvmField
    val DOTS = arrayOf(
        floatArrayOf(33f, 104.2f),
        floatArrayOf(50.5f, 111.2f),
        floatArrayOf(68.5f, 111.7f),
        floatArrayOf(86f, 105.8f),
    )


    /**
     * The dual-SIM dot rows, eight entries: indices 0..3 are SIM 1 (slot 0) in
     * the top half of the ring, indices 4..7 are SIM 2 (slot 1) at
     * [#DOTS].
     *
     * The top row is [#DOTS] mirrored about [#B_CY], which is what
     * makes the two rows sit symmetrically inside the ring - the bottom row
     * follows the ring's lower curve, and the mirror puts the top row on the
     * matching upper curve. The mirror line is the ring centre rather than the
     * middle of the design box: the ring itself is centred at `B_CY`, and
     * mirroring about anything else would leave the rows visibly off-centre
     * inside it.
     *
     * Both rows also fall inside the ring's two mouths - the bottom row in the
     * arc's natural opening, the top row in the 12 o'clock notch - which is why
     * the dual reading forces a gap even though it draws no bolt.
     */
    @JvmField
    val DOTS_DUAL = Array(8) { FloatArray(2) }

    init {
        for ((i, element) in DOTS.withIndex()) {
            DOTS_DUAL[i][0] = element[0]
            DOTS_DUAL[i][1] = 2f * IconGeometry.B_CY - element[1]
            DOTS_DUAL[i + DOTS.size][0] = element[0]
            DOTS_DUAL[i + DOTS.size][1] = element[1]
        }
    }
}
