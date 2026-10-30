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

import android.graphics.Path
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Entry points and frame state: the dispatch that picks an arrangement, the fit that
 * maps the 120x120 design space into a host view, and the animation clock the
 * arrangements read.
 *
 * Drawing itself lives in [RingRenderer], [CapsuleRenderer], [RectRenderer] and
 * [OutSignalRenderer]; the ink they share is [GlyphInk].
 */
object IconGeometry {

    /** The SVG viewBox. Everything is drawn inside 0..120 on both axes. */
    const val BOX = 120f


    /** Arc centre: the two endpoints (15.5,88.25) and (103.5,88.25) with r=51.5. */
    const val B_CX = 59.5f

    const val B_CY = 61.487152f

    const val B_R = 51.5f


    /**
     * Clockwise sweep (deg). The missing part is at the BOTTOM.
     *
     * Derived from [#GAP_WIDTH_DEG]: the bottom opening is
     * `360 - B_SWEEP`, and making it equal the top gap is what centres the glyph
     * vertically. It used to be 242.62, leaving 117.38 degrees bare at the bottom
     * against 84.92 at the top - 16.2 degrees short on each side of the notch, which
     * reads as "the lower half of the ring is shorter".
     */
    const val B_SWEEP = 360f - RingGeometry.GAP_WIDTH_DEG


    /**
     * Start angle (deg), placed so the arc's bottom opening is centred on 6 o'clock.
     *
     * `270 - sweep/2` puts the midpoint of the missing part at 90 degrees
     * (straight down). Spelling it out from the sweep rather than keeping the old
     * absolute 148.69 is what keeps the opening centred if the sweep changes again.
     */
    const val B_START = 270f - B_SWEEP / 2f


    /**
     * The charging bolt, scaled up from the reference path.
     *
     * sliver next to a 103-unit ring. [#BOLT_SCALE] grows it about
     * [#BOLT_CX] and pins its top edge to [#BOLT_TOP], so it fills
     * the opened 12 o'clock gap without leaving the 120x120 box.
     *
     * The renderer draws the path with `translate(boltOffsetX(f), boltOffsetY(f))`
     * then `scale(BOLT_SCALE * f)`.
     */
    const val BOLT_CX = 59.45f

    const val BOLT_TOP = 1.5f


    /** Top edge and horizontal centre of the reference path, before scaling. */
    const val BOLT_TOP_REF = 2.2f

    const val BOLT_CX_REF = 59.45f


    /**
     * Chosen against the default ring stroke: the reference bolt is sized for a
     * stroke of 8 design units, and the shipping default is 14, so a much larger
     * scale is needed for the two to read as one icon.
     */
    const val BOLT_SCALE = 1.6f


    /** SVG letter-spacing="-0.04em". */
    const val VALUE_LETTER_SPACING = -0.04f


    /**
     * The character the reference draws smaller than the rest of the network
     * type: the "A" of "5GA".
     *
     * Kept here rather than in either drawing path so the ring canvas and the
     * out-of-ring label cannot end up disagreeing about which glyph is the
     * suffix. The system reports the type as one string ("5G", "5GA"), so the only
     * signal available is the trailing character.
     */
    const val TYPE_SUFFIX = 'A'

    const val W_CX = 60f

    const val W_STROKE = 7f


    /**
     * The Wi-Fi icon: two quadratic curved arcs and the anchor dot, referenced from CombinedStatus
     * (RoundedWifiGeometry).
     *
     * Coordinates are defined in 120x120 canvas space:
     * - Outer arc: apex at (60, 42), endpoints at (39, 52) and (81, 52).
     * - Middle arc: apex at (60, 56.5), endpoints at (47.5, 62) and (72.5, 62).
     * - Dot: apex at (60, 67), bottom tip at (60, 75.85).
     *
     * With W_STROKE = 7f:
     * - Topmost ink is at W_TOP - W_STROKE * 0.5f = 42 - 3.5 = 38.5f.
     * - Bottom of dot is at 75.85f.
     * - Total ink height = 37.35f, ink width = 49f, ink center Y = 57.2f.
     */
    const val W_TOP = 42f


    /**
     * The widest stroke the arcs can carry and still read as separate marks with generous gaps.
     * With outer apex at 42, middle at 56.5 (diff 14.5) and dot at 67 (diff 10.5),
     * a stroke of 7..8f leaves ~6.5..7.5f of clean daylight between them.
     */
    const val W_MAX_STROKE = 8f


    /**
     * Builds the three Wi-Fi paths (referenced from CombinedStatus RoundedWifiGeometry):
     * [0] = dot (anchor droplet / wedge shape)
     * [1] = middle quadratic curved arc
     * [2] = outer quadratic curved arc
     */
    @JvmStatic
    fun buildWifiPaths(): Array<Path> {
        val dot = Path()
        val middle = Path()
        val outer = Path()
        outer.moveTo(39f, 52f)
        outer.quadTo(60f, 32f, 81f, 52f)
        middle.moveTo(47.5f, 62f)
        middle.quadTo(60f, 51f, 72.5f, 62f)
        dot.moveTo(53.5f, 70f)
        dot.quadTo(60f, 64f, 66.5f, 70f)
        dot.lineTo(62.8f, 74.4f)
        dot.quadTo(60f, 77.3f, 57.2f, 74.4f)
        dot.close()
        return arrayOf(dot, middle, outer)
    }

    /**
     * Tight ink box of the whole glyph (battery arc + stroke, Wi-Fi arcs + dot, level
     * dots, top indicator), in design units. Used to fit the 120x120 artwork into the
     * host view without non-uniform scaling.
     *
     * Derived from the arc rather than typed in. It used to be
     * `1.0, 0.5, 117.0, 117.5` - the box of the old 242.6-degree sweep, whose ends
     * sat at y = 88.25 and whose round caps reached the bottom of the design box. The
     * sweep is now 264 degrees with the bottom opening centred on 6 o'clock, so those
     * ends moved down to y ~ 96 and the old box no longer described the ink: the glyph
     * was scaled to a frame larger than itself and came out visibly smaller in the
     * status bar.
     *
     * The extents are taken from the arc's own endpoints plus half a stroke, which is
     * what the round caps add. The top of the box is the arc's apex (also half a stroke
     * clear), and the bottom is the lower of the two endpoints.
     */
    // The values are literals because the offline harness reads them out of this file with
    // a regex and measures the render against them; a static initialiser computing them
    // would make the number invisible to that check. They are still DERIVED, and
    // [checkInkBox] recomputes them at class load so a change to the arc or the
    // beads that is not mirrored here fails loudly instead of silently scaling the glyph
    // wrong.
    //
    // Derivation: the box is SQUARE and centred on the RING'S CENTRE (B_CX, B_CY), not on the
    // extent of whatever happens to be drawn. That distinction is the whole point:
    //
    //   - Centred on the ring, the glyph never moves or resizes as pieces come and go. The
    //     status bar draws the same ring every frame; only its contents change.
    //   - Centred on the drawn ink, the ring would slide up and down as a percentage or a dot
    //     row appeared, because the arc has an opening at the bottom and its ink sits above
    //     the ring's centre.
    //
    // Half-extent 59.5 is the reach of the outermost ink from the ring's centre: the cap of
    // the thickest stroke above (61.487 - 1.99 = 59.50) and the bottom bead row below
    // (120.99 - 61.487 = 59.50). Those two are equal, which is what makes the box symmetric
    // and the ring exactly centred.
    //
    // The bead rows ARE included, at the thickest stroke the slider allows. Excluding them
    // was tried and is wrong: a placed bottom row reaches y 118 while a ring-only box stopped
    // at 104, so at 100% size the host clipped the row - the visible symptom being a bottom
    // edge that "does not show completely".

    const val INK_X = B_CX - 59.5f

    const val INK_Y = B_CY - 59.5f

    const val INK_W = 119f

    const val INK_H = 119f


    /**
     * The Wi-Fi group's ink extents, in design units.
     *
     * Measured off the geometry rather than read off a constant, because the
     * obvious constants are the wrong ones: `W1_R` is the arc's centre-line
     * radius, and the ink reaches half a stroke beyond it, while the arc's
     * *endpoints* are what set the width - not its apex. Taking the chord
     * for the width and the centre-line span for the height - which the first cut
     * of this did - understates both, and the group then overruns the slot it was
     * fitted to.
     *
     * Quoted at `DEF_ARC_STROKE`, the default the arcs are drawn at; the
     * fit below is against this height, and the offset compensates for the stroke
     * actually in force the way the ring's own Wi-Fi offsets do.
     */
    const val W_INK_W = 49f

    const val W_INK_H = 37.35f


    /**
     * The group's ink centre. Centring on this - rather than aligning its top
     * edge - is what keeps the arcs optically centred in a slot whose height
     * comes from the dot rows rather than from the group itself.
     */
    const val W_INK_CY = 57.2f


    /** The bolt's ink extremes, from `buildBolt`'s own coordinates. */
    const val BOLT_INK_H = 19.9f

    const val BOLT_INK_CY = 12.05f


    /**
     * Amber used for the charging bolt while the battery reports quick charge.
     * At the real 60px status-bar size colour is the only channel that stays
     * instantly legible, so quick charge is told apart by hue rather than by
     * extra glyphs (two bolts merge into an unreadable blob at that size).
     */
    const val QUICK_CHARGE = 0xFFFFBA28.toInt()


    /**
     * The same offset for a bolt scaled by `factor` about its ink centre.
     *
     * `offset = target - reference * scale`, the shape every bolt offset here
     * uses. Multiplying the existing offset instead would translate the bolt rather than
     * grow it around [#BOLT_CX]/[#BOLT_TOP], so a larger setting would drift up
     * and to the left out of the notch.
     */
    @JvmStatic
    fun boltOffsetX(factor: Float): Float {
        return BOLT_CX - BOLT_CX_REF * (BOLT_SCALE * factor)
    }

    @JvmStatic
    fun boltOffsetY(factor: Float): Float {
        return BOLT_TOP - BOLT_TOP_REF * (BOLT_SCALE * factor)
    }


    /**
     * Whether `type` ends in a suffix that should be drawn smaller.
     *
     * Both conditions matter: `scalePercent` of 100 is "no shrink", and
     * a bare "A" is the whole label rather than a suffix of one - shrinking it
     * would just draw the label smaller, which is what the size slider is for.
     */
    @JvmStatic
    fun hasShrunkSuffix(type: String, scalePercent: Int): Boolean {
        return scalePercent < 100
                && type.length > 1
                && type[type.length - 1] == TYPE_SUFFIX
    }


    /** `type` without its trailing [#TYPE_SUFFIX]. */
    @JvmStatic
    fun typeBase(type: String): String {
        return type.substring(0, type.length - 1)
    }


    /**
     * Clear horizontal room for a value drawn in the 12 o'clock gap: the chord
     * between the two rounded arc ends, less one stroke width for those caps.
     */
    @JvmStatic
    fun gapClearWidth(stroke: Float): Float {
        val half = Math.toRadians(((RingGeometry.GAP_END_IDLE - RingGeometry.GAP_START_IDLE) * B_SWEEP * 0.5f).toDouble())
        return ((2.0 * B_R * sin(half))).toFloat() - stroke
    }


    /**
     * Verifies the box is the ring's own square, centred on the ring.
     *
     * The check is not "does this match the drawn ink" - the two differ on any frame that
     * holds a dot row - but the two properties the box exists for:
     *
     *   - it is centred on `(B_CX, B_CY)`, so the ring never slides as contents
     *       change
     *   - it is big enough for the outermost ink the sliders can ask for, so nothing is
     *       clipped at 100%.
     *
     * A comment saying "keep these in step" is not a check, and neither is comparing
     * against one arbitrary frame: this recomputes both properties and throws at class load,
     * which means at the first frame in the harness or on the device rather than as a subtly
     * mis-scaled glyph.
     */
    @JvmStatic
    fun checkInkBox() {
        // 1. Centred on the ring, and square.
        if (abs(INK_X + INK_W / 2f - B_CX) > 0.01f
            || abs(INK_Y + INK_H / 2f - B_CY) > 0.01f
        ) {
            throw AssertionError(
                "ink box is not centred on the ring: box centre "
                        + (INK_X + INK_W / 2f) + "," + (INK_Y + INK_H / 2f)
                        + " vs ring " + B_CX + "," + B_CY
            )
        }
        if (abs(INK_W - INK_H) > 0.01f) {
            throw AssertionError(
                "ink box is not square: " + INK_W + "x" + INK_H
                        + " - a non-square box scales the ring differently on each axis"
            )
        }
        // 2. Big enough for the outermost ink, recomputed the way the drawing places it.
        val capPad = Prefs.MAX_RING_STROKE * 0.5f
        var reach = 0f
        for (i in 0..360) {
            val angle = Math.toRadians(B_START + (i / 360.0) * B_SWEEP)
            val dx = abs(B_R * cos(angle).toFloat()) + capPad
            val dy = abs(B_R * sin(angle).toFloat()) + capPad
            reach = maxOf(reach, maxOf(dx, dy))
        }
        val stroke = Prefs.MAX_RING_STROKE
        val halfOpening = Math.toRadians(RingGeometry.GAP_WIDTH_DEG * 0.5).toFloat()
        // Both ends of the dot-size slider, because the beads are the one piece whose ink
        // the user moves directly: at MAX_DOT_SCALE they are what reaches furthest, and at
        // MIN_DOT_SCALE they shrink away and the arc's own caps become the outer limit.
        // Checking only the reference size is exactly what let a larger bead overrun the
        // box before maxBeadR() existed.
        for (scalePct in intArrayOf(
            Prefs.MIN_DOT_SCALE, Prefs.DEF_DOT_SCALE,
            Prefs.MAX_DOT_SCALE
        )) {
            val beadR = RingGeometry.arcRowDotR(0, stroke, 78f - stroke, scalePct / 100f)
            val inset = (beadR + stroke * 0.5f + 1f) / B_R
            val beadHalfAngle = minOf(
                halfOpening - inset,
                Math.toRadians(RingGeometry.BEAD_SPAN_DEG * 0.5).toFloat(),
            )
            for (row in 0 until 2) {
                for (i in -1..1) {
                    val angle = beadHalfAngle * i
                    val dx = abs(B_R * sin(angle)) + beadR
                    val dy = abs(B_R * cos(angle)) + beadR
                    reach = maxOf(reach, maxOf(dx, dy))
                }
            }
            // The ceiling itself has to be a real one: a bead radius past maxBeadR() would
            // put the outer edge outside the box, whatever the opening allows.
            if (beadR > RingGeometry.maxBeadR() + 0.01f) {
                throw AssertionError(
                    "dot radius " + beadR + " at " + scalePct
                            + "% exceeds what the ink box holds: " + RingGeometry.maxBeadR()
                )
            }
        }
        val half = INK_W / 2f
        if (reach > half + 0.01f) {
            throw AssertionError(
                "ink box too small: outermost ink reaches " + reach
                        + " from the ring centre, the box allows " + half
            )
        }
    }


    /**
     * Width over height of the box an arrangement wants to be drawn in.
     *
     * The ring and the rectangle are laid out in their own ink boxes, which are nearly
     * square; the capsule is the reference's 2.3:1 shape plus the two opening bands, so its
     * host has to be wider than it is tall or the fit would be decided by the width and the
     * shape would come out short. This is the one place that ratio lives, so the host's
     * measurement and the renderer's fit can never disagree.
     */
    @JvmStatic
    fun hostAspect(a: StatusAppearance?, wifiLevel: Int): Float {
        if (a == null) {
            return INK_W / INK_H
        }
        if (a.capsule) {
            // The capsule's own box plus one band at each end: the body keeps its height
            // and the bands are the extra room the opening readings need.
            return CapsuleGeometry.capInkW(a, wifiLevel) /
                (CapsuleGeometry.CAP_INK_H + 2f * CapsuleGeometry.CAP_BAND_H)
        }
        if (a.rect) {
            return RectGeometry.RECT_INK_W / RectGeometry.RECT_INK_H
        }
        // A flank holding the Wi-Fi arcs is the one ring arrangement whose ink reaches
        // outside the ring's own box: the arcs sit *outside* the stroke with a gap, so the
        // host is asked for that width. The scale stays height-driven, so the glyph keeps its
        // size and the extra width becomes margin, instead of the arcs being clipped at the
        // view's edge as they were.
        return (INK_W + 2f * RingGeometry.ringFlankWifiExtra(a)) / RingGeometry.ringInkH(a)
    }

    init {
        checkInkBox()
    }
}
