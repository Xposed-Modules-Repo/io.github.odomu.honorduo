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

/**
 * The capsule arrangement: the pill body, its two bands, the openings and the cells
 * that sit inside it.
 */
object CapsuleGeometry {

    /** Cap height of the digits, in em. Roboto's is ~0.71em. */
    const val CAP_HEIGHT_RATIO = 0.71f


    /** Outer bounds of the capsule outline, in design units. */
    const val CAP_LEFT = 1f


    /**
     * Right edge of the outline.
     *
     * No longer wide enough for two columns. The capsule used to reserve a left
     * column for the time, so its width was the two readings side by side; the time
     * is placed like any other piece now, and when it is not in the capsule that
     * column was drawn as empty space. The flat run between the caps is what is left
     * after both ends, and it now holds exactly one reading: the value's own width,
     * its two text insets, and the gap between them.
     *
     * Stated as a constant rather than measured from the current reading on purpose:
     * "9" and "100" are the same piece two frames apart, and an outline that followed
     * the digits would breathe with every percent. The room reserved here fits the
     * widest reading the body can hold, at the default stroke.
     *
     * Below roughly this value the top and bottom rows start shrinking instead: those
     * bands are fitted to the flat run, so the outline cannot get narrower without
     * taking size away from whatever sits in the openings.
     */
    const val CAP_RIGHT = 122.3f


    /**
     * Right edge when the body shows two pieces side by side.
     *
     * Two readings need the room the old clock column used to take, so the capsule
     * goes back to the width it was drawn at before that column was dropped. Which of
     * the two edges is in force is decided by the arrangement, not by the current
     * reading: see `StatusAppearance.capsuleRight`.
     */
    const val CAP_RIGHT_PAIR = 139f

    const val CAP_TOP = 1f


    /**
     * Bottom edge of the outline.
     *
     * Quoted against the height the status bar offers its icons -
     * `status_bar_icon_height`, 20dp, 60px at density 3 - less the two
     * units of the design space that keep the ink off the host's edge. The
     * reference's 26.5% text rule then lands at 15.6 units of cap height, which
     * is what the stock battery percentage is drawn at, so the readings are the
     * size they would be anywhere else on the bar.
     *
     * Deliberately the number that does *not* move when the shape gets
     * wider: the bar's height is fixed, and the capsule grows sideways into it
     * rather than up. See [#CAP_RIGHT].
     */
    const val CAP_BOTTOM = 60f


    /**
     * Corner radius: half the height, which is what makes the outline a stadium
     * rather than a rounded rectangle.
     */
    const val CAP_RADIUS = (CAP_BOTTOM - CAP_TOP) * 0.5f


    /**
     * The outline's thickness, as a fraction of `ringStroke`.
     *
     * The reference draws it at 12.6% of the capsule's height, which is 7.4
     * units here; `ringStroke` defaults to 14, so this is the factor that
     * lands there. Kept as a ratio rather than a constant so the stroke slider
     * still means something in this arrangement - it is the one row that adjusts
     * the glyph's weight in every style.
     */
    const val CAP_STROKE_RATIO = 0.53f


    /**
     * Radius of one signal dot, and the pitch between their centres.
     *
     * From the reference: a dot is as thick as the stroke (12.6% of the
     * height) and the pitch is 18.5% of it. Dots thinner than the line they
     * interrupt read as beads dropped on top of it, which is the one thing the
     * reference is careful not to look like.
     */
    const val CAP_DOT_R = 3.7f

    const val CAP_DOT_PITCH = 10.9f


    /**
     * The clear space the stroke leaves on each side of a dot row, as a fraction
     * of the capsule's *path* height - the outline's own height, excluding
     * the stroke, which is the measure the reference's own figures are stated
     * against.
     *
     * **9%, against the reference's 6.67%, at the user's request.** The
     * reference leaves 9px out of a 135px path height. Measured on the device at
     * that value the gap is 3px, which is faithful to the mockup but was reported
     * as still too tight - and on a 60px-tall glyph 6.67% is under four pixels, so
     * there is not much of it to see. 9% is about a third more than the reference
     * and stays well inside the point where the outline would read as broken
     * rather than as a row sitting in it.
     *
     * A ratio rather than a constant: the stroke is a slider, and the clearance
     * has to hold at every width. It is measured against the PATH rather than the
     * ink height because the ink height depends on the stroke - a ratio against
     * that would move the clearance whenever the user changed the line's weight.
     *
     * The first cut of this used 3.3%, which was a measurement error and a bad
     * one to have shipped: it came from counting the antialiased pixels between the
     * dot and the stroke as track, which made the gap look half its real size.
     *
     * Without any clearance the row and the line run together, which is what the
     * very first cut did: its [#capDotReach] was written as "the dots' own
     * span" and nothing else, so the gap was exactly as wide as the row it held and
     * the outer two merged into the stroke ends. The row then reads as a line with
     * two lumps on it rather than as four dots interrupting a line.
     */
    const val CAP_DOT_CLEARANCE_RATIO = 0.09f


    /**
     * The band added above and below the outline's ink box to hold the two openings' pieces.
     *
     * **Why a band on the host and not a taller ink box.** The capsule is fitted to the
     * host by whichever axis binds, and [hostAspect] asks the status bar to reserve a width
     * derived from the ink box's aspect ratio. Making the ink box itself taller therefore
     * makes the capsule *narrower*: the glyph would shrink to make room for the very band
     * it was meant to gain.
     *
     * So the band is added to the *host* instead: [hostAspect] keeps the width it always
     * asked for and grows the height by one band at each end, so the drawing scale is
     * still decided by the width alone. Every pixel of the capsule
     * - outline, stroke, clock, percentage, dot rows - stays exactly where it was; only the host
     * got taller, and the space it gained is the space these readings now use.
     *
     * Quoted against the reference the design was drawn from rather than against the stroke,
     * because it is a text band: the mockup puts an opening's label at a cap height of about a
     * quarter of the path height. The **inward** half of such a label is the half that binds
     * - it runs down to the interior's own top, where the clock is, and that clearance is
     * fixed by the dot rows rather than by this constant - so the band is sized to at least
     * match that inward half and stop being the limit. Anything less and the label is fitted to
     * the band instead of to the row it is in, which is what makes it look shrunken.
     */
    const val CAP_BAND_H = 13f


    /**
     * Clearance kept between the outline and a reading sitting across its opening.
     *
     * One unit is the antialiasing fringe: both the line and the glyph are antialiased, so
     * touching exactly still interleaves pixels.
     */
    const val CAP_OPENING_GAP = 1f


    /** The gap the two readings keep between each other. */
    const val CAP_TEXT_GAP = 4f


    /**
     * The margin a reading keeps from the outline's inner edge, as a fraction of
     * the capsule's height.
     *
     * Read off the reference at about 8.6% of the height. It matters most for
     * the time, which sits in the left slot right where the end cap curves back in
     * underneath it: with the slot starting at the interior's own edge the digits
     * begin immediately at the stroke's inner face, and at the reference device's
     * text size the "1" of "19:41" ends up touching the line.
     *
     * A ratio rather than a constant for the same reason as the dot clearance -
     * the stroke is a slider, and the margin has to grow with it.
     */
    const val CAP_TEXT_INSET_RATIO = 0.086f


    /**
     * Natural width of each reading per unit of font size, at the default face.
     *
     * Measured off the platform's own metrics, not guessed: "19:41" is 2.52
     * units wide per unit of size and "100" is 1.68. Their ratio is what the two
     * slots are divided by, and it is the whole reason a half each was wrong.
     */
    const val CAP_TIME_TEXT_PER_UNIT = 2.52f

    const val CAP_VALUE_TEXT_PER_UNIT = 1.68f


    /**
     * How tall a reading may be, as a fraction of the interior.
     *
     * Not a fraction of the capsule's outer height: the dot rows are inside
     * that, and a reading has to clear them. The other two arrangements never
     * needed this - they are 117 and 114 units tall, so a size that fits their
     * width always fits their height too.
     *
     * **A backstop, not the binding bound.** Measured rather than assumed:
     * with the size slider at its own ceiling the width fit is what decides the
     * type, and the height only takes over for a single narrow glyph at a
     * `valueSize` the slider cannot reach. A negative control that made
     * this limit a hundred times larger still passed every pixel assertion in
     * `tests/capsule.ps1`. It is kept because the two bounds are computed
     * from different things and the height is the one that would fail silently if
     * a future size range grew past it - but it is not what keeps the digits
     * inside the outline today, and saying otherwise would be a comment that
     * lies about which line is doing the work.
     */
    const val CAP_TEXT_HEIGHT_RATIO = 0.62f


    /** How much of the capsule's interior height the body's ink fills at the default size. */
    const val CAP_TEXT_FILL = 0.5f


    /**
     * The interior's height at the default stroke: the box less the stroke's two halves
     * and the two dot-row radii. The readings and the icons are both quoted against it,
     * so they stay the same size as each other.
     */
    const val CAP_INTERIOR_REF_H =
        CAP_BOTTOM - CAP_TOP - Prefs.DEF_RING_STROKE * CAP_STROKE_RATIO - 2f * CAP_DOT_R


    /**
     * The requested reading size, as a multiple of `valueSize`.
     *
     * The reference draws digits whose *cap height* is about a quarter of the
     * capsule's height, which reads small once the capsule is only one reading wide:
     * the body's ink now fills [#CAP_TEXT_FILL] of the interior's height instead.
     * A font's em is that cap height divided by [#CAP_HEIGHT_RATIO], so both factors
     * are in the expression. Quoted against `valueSize` so the size slider keeps
     * control of it exactly as it does in the other two arrangements.
     */
    const val CAP_TEXT_SCALE = CAP_TEXT_FILL * CAP_INTERIOR_REF_H / (CAP_HEIGHT_RATIO * 36f)


    /**
     * How much of the capsule's interior an icon fills.
     *
     * Above the text's own [#CAP_TEXT_FILL]: a three-arc icon carries four levels in the
     * same ink a digit uses for one glyph, so matching the cap height left every arc a
     * couple of units tall and the levels indistinguishable at status-bar size.
     */
    const val CAP_ICON_FILL = 0.72f

    /**
     * The ink height every reading and icon in the capsule is drawn at, in design
     * units: [#CAP_TEXT_FILL] of [#CAP_INTERIOR_REF_H].
     *
     * The same number [#CAP_TEXT_SCALE] produces, restated as the height the icons
     * are matched against so the two cannot drift apart. The reference kept its arcs
     * and its digits the same height to the pixel, and this keeps that relationship
     * at the larger fill.
     */
    const val CAP_ICON_H = CAP_ICON_FILL * CAP_INTERIOR_REF_H


    /**
     * Extra width one flank costs when it holds the Wi-Fi arcs, in design units.
     *
     * The arcs stand outside the outline's stroke with a gap, so a side that draws them
     * needs room the body itself does not. Only added while Wi-Fi actually inks there
     * (`capFlankWifi`): reserving it for a placement that draws nothing was leaving the
     * capsule visibly off-centre.
     */
    const val CAP_FLANK_WIFI_W = 24f

    /**
     * The capsule's ink box: the outline plus its stroke, which is what has to
     * fit inside the host view.
     *
     * Sized against the largest stroke the slider offers rather than the
     * default, the way [#RectGeometry.RECT_INK_W] is: fitting the extremes means no
     * setting can push ink outside the host, where the platform clips it.
     */
    const val CAP_INK_X = CAP_LEFT - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f

    fun capFlankWifi(a: StatusAppearance?, zone: Int, wifiLevel: Int): Boolean =
        a != null && a.wifiInk(wifiLevel) &&
            a.ringZones.getOrNull(zone)?.contains(Prefs.PART_WIFI) == true

    @JvmStatic
    fun capInkX(a: StatusAppearance?, wifiLevel: Int): Float {
        return if (capFlankWifi(a, Prefs.ZONE_LEFT, wifiLevel)) CAP_INK_X - CAP_FLANK_WIFI_W else CAP_INK_X
    }


    /**
     * The top of the capsule's ink: the outline plus its stroke.
     *
     * **Deliberately unchanged by the opening bands.** A reading placed in the top or
     * bottom opening is fitted to the room that exists *inside* this box (see
     * [#capOpeningCy]), rather than the box being widened to hold it. Growing the box was
     * tried and reverted: [hostAspect] divides `CAP_INK_W` by this height to ask the status
     * bar for a width, so a taller box makes the capsule NARROWER - the glyph would have
     * shrunk to make room for the very band it was meant to fit.
     */
    const val CAP_INK_Y = CAP_TOP - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f

    // Measured against the widest the outline can be, not the one-reading width: the
    // two-piece body draws out to CAP_RIGHT_PAIR, and a box cut for the narrow shape
    // would let the host clip the right cap off.
    const val CAP_INK_W: Float =
        (CAP_RIGHT_PAIR + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_X

    const val CAP_INK_H: Float =
        (CAP_BOTTOM + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_Y


    /**
     * The user's extra straight run, in design units.
     *
     * Clamped here as well as in the store: the renderer, the zones and the host's width
     * request all read it, and they must agree even on a value written by an older build.
     */
    @JvmStatic
    fun capExtra(a: StatusAppearance?): Float = Prefs.clamp(
        a?.capsuleLength ?: Prefs.DEF_CAPSULE_LENGTH,
        Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH,
    ).toFloat()


    /** The capsule's ink box, widened by the user's extra run. */
    @JvmStatic
    fun capInkW(a: StatusAppearance?, wifiLevel: Int): Float {
        val hasLeftWifi = capFlankWifi(a, Prefs.ZONE_LEFT, wifiLevel)
        val hasRightWifi = capFlankWifi(a, Prefs.ZONE_RIGHT, wifiLevel)
        val flankW = (if (hasLeftWifi) CAP_FLANK_WIFI_W else 0f) + (if (hasRightWifi) CAP_FLANK_WIFI_W else 0f)
        return CAP_INK_W + capExtra(a) + flankW
    }


    /** The two-cell body's right edge, widened by the user's extra run. */
    @JvmStatic
    fun capPairRight(a: StatusAppearance?): Float = CAP_RIGHT_PAIR + capExtra(a)


    /**
     * The top of what the capsule draws, band included.
     *
     * The outline plus its stroke is where the *capsule* starts; the band above that
     * is what the 12 o'clock piece sits in.
     *
     * This box is what the renderer fits the drawing to, and it is **taller without being
     * wider**. That is the whole point: the fit is width-driven for this shape, so the
     * capsule's own pixels are decided by `CAP_INK_W` alone and are unchanged, while the
     * two bands give the opening readings somewhere to live.
     */
    const val CAP_INK_TOP = CAP_INK_Y - CAP_BAND_H


    /** The bottom of what the capsule draws, band included - the mirror of [#CAP_INK_TOP]. */
    const val CAP_INK_BOTTOM = CAP_INK_Y + CAP_INK_H + CAP_BAND_H


    /**
     * The share of the readings' width the time takes, the rest going to the
     * right-hand slot.
     *
     * **Not a half, and the even split was the bug behind "the clock's maximum
     * is too small".** The two slots held equal halves of the interior, but the
     * strings in them are not equal: at one unit of font size "19:41" is 2.52 units
     * wide and "100" is 1.68, so the time ran out of width long before the number
     * did. The clock's slider stopped doing anything above a setting of about 36 -
     * its documented maximum of 44 was unreachable, and raising that maximum would
     * have changed nothing. Measured before this fix: setting 44 drew the time at
     * 23.03 units instead of the 27.37 it asks for, and settings of 44, 60 and 120
     * all drew it at the same size. The slot was the binding constraint, not the
     * ceiling.
     *
     * 2.52 / (2.52 + 1.68) = 0.60, the two strings' natural widths at a common
     * size, so each reading gets the room its own string needs and both reach their
     * documented maximum together. The reference agrees: its time spans 156px
     * against its number's 92px, a share of 0.63.
     *
     * **A fixed ratio, deliberately, and the choice is a real one.** Dividing
     * instead in proportion to the two *size settings* would let the clock go
     * larger - up to about setting 62 with the number at its smallest, where this
     * caps it at 44 - but it would also make the time drift sideways every time the
     * number's slider moved, because its slot would be re-divided underneath it. Two
     * settings that the user is told are independent cannot behave that way, and the
     * fixed ratio costs only the ceiling that a 60px capsule cannot honestly hold
     * anyway. Following the current strings rather than the settings was rejected
     * for a second reason: the number is "9" at one level and "100" at another, and
     * is replaced entirely by the arcs, the bolt or the type while charging, so the
     * clock would resize itself as the battery moved.
     */
    const val CAP_TIME_WIDTH_SHARE =
        CAP_TIME_TEXT_PER_UNIT / (CAP_TIME_TEXT_PER_UNIT + CAP_VALUE_TEXT_PER_UNIT)


    //
    // The outline itself is the battery gauge: its length is the full track and
    // the role-coloured part of it is the level, so the shape fills up instead of
    // containing a gauge. Two four-dot rows interrupt the stroke at 12 and 6
    // o'clock, and the time and the right-hand reading share the interior.
    //
    // The shape is quoted against the reference the design was drawn from: a
    // capsule 2.34 times as wide as it is tall, a stroke 12.6% of that height,
    // dots as thick as the stroke with a pitch of 18.5%, and text 26.5% of the
    // height. All four of those are kept, and the reason they can be is the host
    // width - see CAP_RIGHT.

    /** The clear space on one side of a dot row, in design units. */
    @JvmStatic
    fun capDotClearance(): Float {
        return CAP_DOT_CLEARANCE_RATIO * (CAP_BOTTOM - CAP_TOP)
    }


    /**
     * Half the horizontal span of one dot row: how far the outline's path must be
     * cut back at 12 and 6 o'clock for the row to sit in a real gap.
     *
     * **Three terms, and the third is the one that was missing.** The path
     * has to be cut back by the dots' own half-span, by the clearance wanted
     * between them and the line, and by half the stroke - because `STROKE`
     * is drawn with [android.graphics.Paint.Cap#ROUND], so the cut end inks
     * a semicircle of radius `stroke / 2` *past* its endpoint. Cutting
     * the path back by only the first two terms therefore spends the whole
     * clearance on that semicircle.
     *
     * Measured, at the two ends of the stroke slider, with the first two terms
     * alone and [#CAP_DOT_CLEARANCE_RATIO] at the reference's own 6.67%:
     * the visible gap is +0.23 units at `ringStroke` 14 and -0.31 at 16. It
     * is negative at the top of the range - the round cap overlaps the outer dot -
     * and near enough to zero at the default to be invisible. That is what "the
     * dots still have no room" was, and it is why raising the ratio alone did not
     * fix it: the clearance was being eaten, not miscalculated.
     *
     * The stroke is a slider, so the third term belongs in the geometry rather
     * than in a constant: at the default it is 3.71 units and at the maximum 4.24.
     */
    @JvmStatic
    fun capDotReach(stroke: Float): Float {
        return CAP_DOT_PITCH * 1.5f + CAP_DOT_R + capDotClearance() + stroke * 0.5f
    }


    /**
     * Centre y of the top and bottom dot rows.
     *
     * Exactly the outline's path coordinates, and deliberately not
     * `CAP_TOP + stroke / 2`: the stroke straddles the path, so the path's
     * own y is the line's centre. Putting the dots on the stroke's inner edge
     * instead - which the first cut of this did - hangs them below the line they
     * are supposed to be set into.
     */
    @JvmStatic
    fun capDotTopY(): Float {
        return CAP_TOP
    }

    @JvmStatic
    fun capDotBottomY(): Float {
        return CAP_BOTTOM
    }


    /** Where the two end caps stop and the flat runs begin. */
    @JvmStatic
    fun capFlatLeft(): Float {
        return CAP_LEFT + CAP_RADIUS
    }

    @JvmStatic
    fun capFlatRight(right: Float = CAP_RIGHT): Float {
        return right - CAP_RADIUS
    }


    /**
     * The interior, and the two reading slots inside it.
     *
     * Not the flat run between the caps: the caps curve in only near the top
     * and bottom edges, so at the height the readings are drawn the interior runs
     * almost the full width. The reference relies on exactly that - its text
     * spans well past the point where the flat run ends - so insetting by the
     * radius would shrink the type for no reason.
     *
     * Everything is derived from the stroke rather than fixed, because the
     * stroke is a slider: a heavier line eats into the room the readings have, and
     * they have to give way rather than overrun.
     */
    @JvmStatic
    fun capInteriorLeft(stroke: Float): Float {
        return CAP_LEFT + stroke * 0.5f
    }

    @JvmStatic
    fun capInteriorRight(stroke: Float, right: Float = CAP_RIGHT): Float {
        return right - stroke * 0.5f
    }


    /**
     * The interior's vertical extent - and this is the bound that matters.
     *
     * The two dot rows ride the outline's own centreline, so the top row's ink
     * reaches `CAP_TOP + stroke / 2 + CAP_DOT_R` and the bottom row's
     * mirrors it. Anything drawn inside the capsule has to live between them
     * measuring against the outline's outer bounds instead - which is what the
     * first cut of this did - lets the Wi-Fi arcs and the digits run straight
     * through the dot rows, which is exactly what the offline render showed.
     */
    @JvmStatic
    fun capInteriorTop(stroke: Float): Float {
        return CAP_TOP + stroke * 0.5f + CAP_DOT_R
    }

    @JvmStatic
    fun capInteriorBottom(stroke: Float): Float {
        return CAP_BOTTOM - stroke * 0.5f - CAP_DOT_R
    }

    @JvmStatic
    fun capInteriorHeight(stroke: Float): Float {
        return capInteriorBottom(stroke) - capInteriorTop(stroke)
    }

    @JvmStatic
    fun capInteriorCy(stroke: Float): Float {
        return (capInteriorTop(stroke) + capInteriorBottom(stroke)) * 0.5f
    }


    /**
     * The vertical centre a piece placed in one of the two **openings** is drawn on.
     *
     * The two opening bands are not the interior: they are the strips at 12 and 6 o'clock
     * where the outline is cut, which is what lets a row of beads read as the ring segmented.
     * Drawing a cell at `CAP_TOP`/`CAP_BOTTOM` - which is what this used to do, on
     * the reasoning that a band's centre is its anchor - put the reading exactly ON the
     * outline's centreline: half of it stuck out beyond the capsule, where the host clipped it
     * and the stroke painted over it. That is the "component shows only partially" defect.
     *
     * **The piece lives inside the capsule, in the lane the opening leaves free.** Not
     * outside it: the strip beyond the stroke is the ink box's own margin, about one stroke
     * wide, which is enough for a bead and nowhere near enough for a line of digits. So an
     * opening's reading is centred in the free lane just *inside* the outline, between
     * the stroke's inner face and the first thing that would collide with it - which for the
     * top is nothing but the interior's own readings, and that is why the lane is measured from
     * the interior's edge.
     *
     * @param top true for the 12 o'clock opening, false for the 6 o'clock one
     */
    @JvmStatic
    fun capOpeningCy(top: Boolean, stroke: Float): Float {
        return if (top) CAP_TOP + openingLaneCy(stroke) else CAP_BOTTOM - openingLaneCy(stroke)
    }


    /**
     * How far OUTSIDE the outline an opening's piece sits, measured from the path line.
     *
     * **Astride the gap, not tucked inside it.** The two openings are where the outline is
     * cut for the dot rows, so a reading placed there belongs in that gap, centred on the path
     * line: it then reads as the row that opened the outline rather than as a second line of
     * digits crammed under the top edge. Putting it in the lane just *inside* the
     * outline - which is what this used to do - stacked it on the body's own readings (the clock
     * shares the left column) and asked a 7.4-unit strip to do the work of a text line.
     *
     * That lane existed only because the ink box left about one stroke of room beyond the
     * outline and a taller line would have been clipped by the host. The box is not the limit
     * any more: [#CAP_BAND_H] is added above and below it, and [hostAspect] asks the
     * status bar for the taller box, so the drawing scale - and with it every pixel of the
     * capsule - is unchanged.
     */
    @JvmStatic
    fun openingLaneCy(stroke: Float): Float {
        // Straddling the gap was right, but centring it exactly on the path line wasted the
        // band: the outward half is a whole band deep while the inward half only reaches the
        // interior's dot clearance, so the line was sized by the smaller half. Moving the lane
        // by half the difference makes the two halves equal and lets the piece use the band it
        // was given. The sign is outward - negative, since y grows downwards - so a positive
        // result here would push the top label into the interior instead.
        val inward = capInteriorTop(stroke) - CAP_TOP
        val outward = CAP_TOP - CAP_INK_TOP
        return (inward - outward) * 0.5f
    }


    /**
     * The height a reading in an opening may use, in design units.
     *
     * The band, less what the stroke and the clearance take out of it. A line fitted to this
     * fits the band wherever the piece is drawn, which is why the same number is both the fit's
     * bound and the band's usable height.
     *
     * Measured from the band's outer edge rather than from the path line: a piece centred on
     * the path line reaches into the opening as well as out of it, and the outline's stroke is
     * drawn across the middle of that. The band is what is left beyond the stroke.
     */
    @JvmStatic
    fun capOpeningTextRoomH(stroke: Float): Float {
        // Both halves of the lane, measured around the piece's own centre rather than around
        // the path line: [openingLaneCy] has already moved the lane so the two are equal.
        // Inward runs to the interior's dot clearance - the first thing a line could collide
        // with - and outward to the ink box's own top.
        val inward = capInteriorTop(stroke) - CAP_TOP
        val outward = CAP_TOP - CAP_INK_TOP
        val half = (inward + outward) * 0.5f - CAP_OPENING_GAP
        // Ink on both sides of the centre, so the ink budget is twice the half.
        val ink = 2f * half
        // Never smaller than a dot: a band that cannot draw a bead is not a band.
        return maxOf(CAP_DOT_R * 2f, ink)
    }


    /**
     * The same budget as an **em**, which is the unit the text fit measures in.
     *
     * Two different quantities, and feeding one where the other was wanted is what made an
     * opening's label come out at a fifth of the body's readings: the size was clamped to an
     * INK height, then multiplied by [#CAP_HEIGHT_RATIO] again to get its own ink - so the
     * cap height came out as the square of the ratio's effect rather than the once it should be.
     * [#capOpeningTextRoomH] answers the ink a cell has; this answers the font size that
     * produces it.
     */
    @JvmStatic
    fun capOpeningTextEm(stroke: Float): Float {
        return capOpeningTextRoomH(stroke) / CAP_HEIGHT_RATIO
    }


    /**
     * Half the width a piece placed in an opening inks, in design units.
     *
     * Used to cut the outline back by exactly what that opening holds, so a reading sits in
     * a gap rather than on top of the line. The dot rows have their own measured reach
     * ([#capDotReach]); this is the same question for a piece made of text or an icon.
     *
     * Measured rather than estimated: the three candidates for an opening - the percentage,
     * the network type and the clock - differ by more than 2:1 in width, so a single constant
     * would either cut the outline for the widest and leave it broken under "73", or fit "73"
     * and bury "100" under the stroke. Worst-case on purpose where a piece can grow: the outline
     * must be wide enough at every reading, not only at the one on screen.
     *
     * @param part      the piece that opening holds
     * @param level     battery level, for the percentage this frame
     * @param type      network type, for the label this frame
     * @param stroke    the capsule's stroke, which sets the text's own height budget
     * @param roomH     the height that opening's reading is fitted to
     */
    @JvmStatic
    fun capPieceReach(
        part: Int, level: Int, type: String, stroke: Float, roomH: Float,
        userFactor: Float = 1f,
    ): Float {
        return when (part) {
            // The arcs are drawn at their fitted scale about their own centre, and the user's
            // size can grow them up to the band. The gap has to be cut for the drawn width,
            // or a large size leaves the fan overlapping the outline.
            Prefs.PART_WIFI -> {
                val room = roomH / IconGeometry.W_INK_H
                IconGeometry.W_INK_W * RingGeometry.fittedScale(room, userFactor) * 0.5f
            }
            // The bolt path is 12.5 units wide against its 19.9 of height; the reach is
            // half of that, scaled the way the bolt itself is.
            Prefs.PART_BOLT -> {
                val room = roomH / IconGeometry.BOLT_INK_H
                6.25f * RingGeometry.fittedScale(room, userFactor)
            }
            // Three digits is the widest this can get.
            Prefs.PART_VALUE -> capTextReach("100", stroke, roomH)
            Prefs.PART_TYPE -> capTextReach("5GA", stroke, roomH)
            Prefs.PART_CLOCK -> capTextReach("19:41", stroke, roomH)
            // One line each, and the widest form each can take.
            Prefs.PART_POWER, Prefs.PART_RUNNING -> capTextReach("99.9W", stroke, roomH)
            Prefs.PART_TEMP -> capTextReach("45°C", stroke, roomH)
            Prefs.PART_CURRENT -> capTextReach("-1999mA", stroke, roomH)
            Prefs.PART_FPS -> capTextReach("120", stroke, roomH)
            else -> 0f
        }
    }


    /**
     * The height an **icon** may use in a capsule opening.
     *
     * Not [capOpeningTextRoomH]: that budget is measured from the band's outer edge and is what
     * a line of digits is fitted to. An icon is centred on the lane (see [openingLaneCy]) and
     * may use the whole lane around it, which is larger - the difference is what made the
     * capsule's Wi-Fi come out noticeably smaller than the ring's.
     */
    @JvmStatic
    fun capOpeningIconRoomH(stroke: Float): Float {
        val cy = capOpeningCy(true, stroke)
        val outward = cy - CAP_INK_TOP
        val inward = capInteriorTop(stroke) - cy
        // One unit of slack: the outermost arc is antialiased, and a fit that lands exactly on
        // the box edge loses its fringe to the host's clip.
        return 2f * minOf(inward, outward) - 1f
    }


    /**
     * Half the width `text` inks at the size an opening gives it.
     *
     * The same two bounds the renderer applies - the user's size setting and the opening's
     * height - so the gap is cut for the glyph that will actually be drawn rather than for a
     * nominal one. Estimated from the character count instead of measured on a Paint: geometry
     * has no font, and the harness renders without one either, so an estimate that is
     * deliberately a little generous is the honest answer here. The clearance term the caller
     * adds covers the remainder.
     */
    @JvmStatic
    fun capTextReach(text: String, stroke: Float, roomH: Float): Float {
        // The largest em the renderer can draw here: the opening's budget scaled by the
        // biggest size the slider reaches. Cut for the ceiling on purpose - the outline must
        // be clear at every setting, and the clearance term the caller adds covers the rest.
        val em = roomH / CAP_HEIGHT_RATIO * Prefs.MAX_CAPSULE_READING_SIZE / Prefs.DEF_VALUE_SIZE
        // Average advance width of a digit in the condensed face the readings use, in ems.
        val half = maxOf(0, text.length) * em * 0.31f
        // Never eat more than a quarter of the capsule: the outline still has to read as an
        // outline, and a gap wider than this stops being an interruption of the stroke and
        // becomes a shape with two horns.
        return minOf(half, capFlatRight() * 0.5f)
    }


    /**
     * The width a piece placed in an **opening** may use, in design units.
     *
     * A piece in an opening is not in a slot. The two interior slots exist because the clock
     * and the percentage have to share the body side by side, which is why the right-hand one is
     * only [#capValueSlotWidth] wide. An opening spans the whole capsule instead - it is a
     * row the outline was cut for - so fitting its text to the right-hand slot crushed it to a
     * third of the size the same string gets anywhere else. That was the "the network type looks
     * far too small" report.
     *
     * Bounded by the flat run between the end caps rather than by the full outline width:
     * past the flat run the shape curves away, and the text would hang its first and last glyph
     * out over the curve. The run is the straight edge the opening actually has.
     */
    @JvmStatic
    fun capOpeningWidth(right: Float = CAP_RIGHT): Float {
        return capFlatRight(right) - capFlatLeft()
    }


    /** Centre of the capsule's width: where a full-width opening row sits. */
    @JvmStatic
    fun capDotCx(left: Float, right: Float): Float {
        return (left + right) * 0.5f
    }


    /**
     * Left edge of the outline.
     *
     * The ink box is cut for the wide (two-piece) shape, so the narrow one is centred
     * inside it rather than hugging its left side; otherwise the glyph would sit a few
     * units off-centre in the status bar.
     */
    @JvmStatic
    fun capLeftFor(right: Float, pair: Float = CAP_RIGHT_PAIR): Float {
        return CAP_LEFT + (pair - right) * 0.5f
    }


    /** The margin on one side of the readings, in design units. */
    @JvmStatic
    fun capTextInset(): Float {
        return CAP_TEXT_INSET_RATIO * (CAP_BOTTOM - CAP_TOP)
    }


    /** Where the readings' usable width starts, inside the outline's inner face. */
    @JvmStatic
    fun capTextLeft(stroke: Float): Float {
        return capInteriorLeft(stroke) + capTextInset()
    }

    @JvmStatic
    fun capTextRight(stroke: Float, right: Float = CAP_RIGHT): Float {
        return capInteriorRight(stroke, right) - capTextInset()
    }


    /** The width the two readings have to share, once the gap between them is taken out. */
    @JvmStatic
    fun capReadingsWidth(stroke: Float, right: Float = CAP_RIGHT): Float {
        return capTextRight(stroke, right) - capTextLeft(stroke) - CAP_TEXT_GAP
    }


    /** Usable width of the right-hand slot: the number, the arcs, the bolt or the type. */
    @JvmStatic
    fun capValueSlotWidth(stroke: Float, right: Float = CAP_RIGHT): Float {
        return capReadingsWidth(stroke, right) * (1f - CAP_TIME_WIDTH_SHARE)
    }


    /** Centre of the right-hand slot: the percentage, the arcs or the bolt. */
    @JvmStatic
    fun capRightCx(stroke: Float, right: Float = CAP_RIGHT): Float {
        return capTextRight(stroke, right) - capValueSlotWidth(stroke, right) * 0.5f
    }


    /**
     * Largest size the capsule's interior allows.
     *
     * A backstop for a size the slider cannot currently reach - see
     * [#CAP_TEXT_HEIGHT_RATIO] for why it is stated as one.
     */
    @JvmStatic
    fun capTextMaxSize(stroke: Float): Float {
        return capInteriorHeight(stroke) * CAP_TEXT_HEIGHT_RATIO / CAP_HEIGHT_RATIO
    }


    /**
     * The arcs' and the bolt's scales, each sized so its own ink is
     * [#CAP_ICON_H] tall.
     *
     * Two scales, not one shared, because the two icons have different ink
     * heights - the group is 39.65 units tall and the bolt 19.9 - so a single
     * factor would draw the bolt at half the height of the arcs. The ring
     * arrangement keeps them separate for the same reason.
     *
     * Each is also capped by the interior, so an extreme stroke setting makes
     * the icons give way rather than cross the dot rows.
     */
    @JvmStatic
    fun capWifiScale(stroke: Float): Float {
        return minOf(CAP_ICON_H / IconGeometry.W_INK_H, capInteriorHeight(stroke) / IconGeometry.W_INK_H)
    }

    @JvmStatic
    fun capBoltScale(stroke: Float): Float {
        return minOf(CAP_ICON_H / IconGeometry.BOLT_INK_H, capInteriorHeight(stroke) / IconGeometry.BOLT_INK_H)
    }


    /** Places the arcs' ink centre on the right-hand slot's own centre. */
    @JvmStatic
    fun capWifiOffsetX(stroke: Float): Float {
        return capRightCx(stroke) - IconGeometry.W_CX * capWifiScale(stroke)
    }

    @JvmStatic
    fun capWifiOffsetY(stroke: Float): Float {
        return capInteriorCy(stroke) - IconGeometry.W_INK_CY * capWifiScale(stroke)
    }


    /**
     * The centre an icon in the right-hand cell is drawn on.
     *
     * The right cell hugs the same gutter the left cell does: `inset` is the spacing
     * setting, applied to [capTextRight] exactly as [capTextLeft] is on the other side,
     * so an icon never sits closer to the cap than a reading does. `inkHalf` is the
     * half-width the piece will actually draw, which the caller already knows because it
     * draws that piece.
     */
    @JvmStatic
    fun capRightIconCx(
        stroke: Float,
        right: Float,
        inset: Float,
        inkHalf: Float,
    ): Float {
        return capTextRight(stroke, right) - inset - inkHalf
    }
}
