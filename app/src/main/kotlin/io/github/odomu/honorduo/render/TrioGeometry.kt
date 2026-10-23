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

package io.github.odomu.honorduo.render

import android.graphics.Path
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance


/**
 * Geometry of the "trio" glyph in a 120x120 design space. All units are design
 * units.
 */
object TrioGeometry {

    /** The SVG viewBox. Everything is drawn inside 0..120 on both axes. */
    const val BOX = 120f

    /** Arc centre: the two endpoints (15.5,88.25) and (103.5,88.25) with r=51.5. */
    const val B_CX = 59.5f
    const val B_CY = 61.487152f
    const val B_R = 51.5f

    /**
     * The width of the ring's 12 o'clock opening, in degrees.
     *
     * This is the *reference* opening: the arc's own bottom gap is set to
     * match it (see [#B_SWEEP]), so a ring with content at both ends reads as
     * two mirrored halves rather than as a long bottom and a short top.
     *
     * Stated in degrees rather than as a fraction of the sweep, so that changing the
     * sweep cannot silently change how wide the notch is - it used to be
     * `0.35 * B_SWEEP`, which meant widening the arc by 32 degrees also widened
     * the notch by 11.
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
     * Clockwise sweep (deg). The missing part is at the BOTTOM.
     *
     * Derived from [#GAP_WIDTH_DEG]: the bottom opening is
     * `360 - B_SWEEP`, and making it equal the top gap is what centres the glyph
     * vertically. It used to be 242.62, leaving 117.38 degrees bare at the bottom
     * against 84.92 at the top - 16.2 degrees short on each side of the notch, which
     * reads as "the lower half of the ring is shorter".
     */
    const val B_SWEEP = 360f - GAP_WIDTH_DEG

    /**
     * Start angle (deg), placed so the arc's bottom opening is centred on 6 o'clock.
     *
     * `270 - sweep/2` puts the midpoint of the missing part at 90 degrees
     * (straight down). Spelling it out from the sweep rather than keeping the old
     * absolute 148.69 is what keeps the opening centred if the sweep changes again.
     */
    const val B_START = 270f - B_SWEEP / 2f

    /**
     * The 12 o'clock opening, as path progress in `0..1` of the sweep.
     *
     * Converted from [#GAP_WIDTH_DEG] so the notch keeps its width in
     * *degrees* whatever the sweep is. Centred on 0.5, which is 12 o'clock.
     */
    const val GAP_START_IDLE = 0.5f - GAP_WIDTH_DEG / 2f / B_SWEEP
    const val GAP_END_IDLE = 0.5f + GAP_WIDTH_DEG / 2f / B_SWEEP

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
    const val GAP_START_DUAL = 1f - 180f / B_SWEEP
    const val GAP_END_DUAL = 180f / B_SWEEP

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

    /** Cap height of the digits, in em. Roboto's is ~0.71em. */
    const val CAP_HEIGHT_RATIO = 0.71f

    /**
     * Cut between the top third and the middle one.
     *
     * **38, unchanged.** Moving this was tried and reverted: the cut is not
     * only a size hint, it is the line `TrioZones` hit-tests and the line
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
    /** Below this battery level the ring turns the "critical" colour. */
    const val CRITICAL_LEVEL = 20

    /** Columns in one reading: one capsule bar, and one dot beneath it. */
    const val STACK_COLUMNS = 4

    /** Bar width in reference pixels. Also the dot row's diameter. */
    const val STACK_BAR_W = 39f

    /** Column pitch: the bar plus a 0.6-bar gap, as the reference draws it. */
    const val STACK_PITCH = 62f

    /** Dot diameter in reference pixels. Equal to the bar width, as drawn. */
    const val STACK_DOT_D = 39f

    /**
     * Gap between the bars' baseline and the top of the dot row, both measured
     * as edges.
     *
     * The reference has no antialiasing, so its edges land on whole pixels
     * and the gap is exactly determinate: the bars' last ink row is 237, which
     * puts their bottom edge at 238, and the dots' first ink row is 247, so the
     * blank band is `238..246` - nine units. Measuring the gap by
     * subtracting those two ink-row indices instead gives ten and a reading one
     * unit too tall, which is exactly the error the reference comparison in
     * `work/outringcheck/compare.py` exists to catch.
     */
    const val STACK_DOT_GAP = 16f

    /** The compact style's individual row height, in design units. */
    const val STACK_ROW_H = 200f

    /** Gap between a card's type label and its bars, in design units. */
    const val STACK_LABEL_GAP = 6f

    /** Radius of a level dot, and the centre x of each of the two columns. */
    const val RECT_DOT_R = 8.6f
    const val RECT_DOT_LX = 9.6f
    const val RECT_DOT_RX = 110.4f

    /** Centre y of the first dot; each following dot drops by [#RECT_DOT_STEP]. */
    const val RECT_DOT_TOP = 15.6f
    const val RECT_DOT_STEP = 22.5f

    /** Four dots per column, the number of steps a phone's signal meter has. */
    const val RECT_DOT_ROWS = 4

    /**
     * The battery bar: a capsule spanning the full width of the columns' outer
     * edges, so its ends line up with the dots above it.
     *
     * Its thickness is `TrioSettings#ringStroke` - the ring the slider
     * would otherwise draw is absent here, and the bar is the one heavy line the
     * arrangement keeps, so the slider stays meaningful.
     */
    const val RECT_BAR_LEFT = 1f
    const val RECT_BAR_RIGHT = 119f
    const val RECT_BAR_CY = 107.3f

    /**
     * The inner edges of the two dot columns: the box the centre column occupies.
     *
     * Stated as its own pair of numbers because *two* things have to agree
     * about it, and they were two independent constants before:
     *
     *   - [#RECT_TEXT_CLEAR] - the width the renderer fits the percentage
     *       and the network type into, so their ink is bounded by these edges
     *   - the tappable centre column in `TrioZones` - which decides whether
     *       a tap on that ink means "the percentage" or "the mobile meter".
     *
     * The failure when they disagree is silent and wrong: a three-digit
     * percentage at a large size reaches these edges, so a seam placed even one
     * design unit inside them puts the right-hand stroke of "100" in the mobile
     * column, and tapping the number turns the signal meter off instead. Measured
     * at 960px: a seam 1 unit in misattributes 334 ink pixels, 3 units in
     * misattributes 3324, and every one of them is a three-digit reading.
     */
    const val RECT_TEXT_LEFT = RECT_DOT_LX + RECT_DOT_R
    const val RECT_TEXT_RIGHT = RECT_DOT_RX - RECT_DOT_R

    /**
     * Horizontal room for text between the two dot columns. Unlike the ring's
     * clear width this does not depend on the font size: the columns are
     * vertical, so a taller glyph eats into the same gap.
     */
    const val RECT_TEXT_CLEAR = RECT_TEXT_RIGHT - RECT_TEXT_LEFT

    /** Baseline and scale of the network type in the top slot. */
    const val RECT_TYPE_BASELINE = 42.3f
    const val RECT_TYPE_SCALE = 1.26f

    /**
     * Baseline, x and scale of the percentage. The reference draws the digits
     * noticeably larger than the ring arrangement does, which is the point of
     * the arrangement: the columns leave the middle free to be read at a glance.
     */
    const val RECT_VALUE_X = 60f
    const val RECT_VALUE_BASELINE = 92.7f
    const val RECT_VALUE_SCALE = 1.38f

    /**
     * Scale of the Wi-Fi group in the top slot and the y of its topmost ink.
     *
     * Only a translation and a uniform scale: the arcs themselves are the
     * same paths the ring arrangement uses, so both arrangements stay visually
     * identical in shape.
     *
     * `0.95` is measured against the reference: its outer arc's ink is
     * 3.05 dot diameters wide, and the untouched group at scale 1 is 3.20, so
     * the arcs are very nearly full size in this arrangement. It is not exactly
     * 1: the reference's own arcs are a little flatter than this module's
     * (wider ink, thinner stroke). That difference is deliberately not copied -
     * the arcs here are the existing icon, only repositioned.
     */
    const val RECT_WIFI_SCALE = 0.95f

    /**
     * The outer arc's apex, half a stroke above its centre line, sits here in
     * design units. The reference puts that ink two of its own pixels below the
     * top of the dot columns, which is about 1.5 design units, so this is a
     * little lower than the dots' ink top at [#RECT_DOT_TOP] - [#RECT_DOT_R][*].
     */
    const val RECT_WIFI_TOP = 8.5f

    /**
     * The charging bolt sits in the same top slot. The ring arrangement puts its
     * ink centre at [#BOLT_CX] / y=17.26; the arrangement's axis is 60 and
     * its top slot centre slightly lower, so both are nudged by a hair.
     */
    const val RECT_BOLT_DX = 0.55f
    const val RECT_BOLT_DY = 0.24f
    const val INK_X = B_CX - 59.5f
    const val INK_Y = B_CY - 59.5f
    const val INK_W = 119f
    const val INK_H = 119f

    /**
     * The same box for the rectangular arrangement.
     *
     * Wider and slightly shorter, and - unlike [#INK_W]/[#INK_H] -
     * sized against the *extremes* every slider can reach rather than
     * against the default look: the bolt's tip at `showValue` with the
     * thickest arc strokes, and the bar's underside at
     * `Prefs#MAX_RING_STROKE`. Fitting the extremes means no setting can
     * push ink outside the host view, where it would be clipped; the cost is
     * that the default glyph ends up a few percent smaller than it could be.
     */
    const val RECT_INK_X = 1f
    const val RECT_INK_Y = 1f
    const val RECT_INK_W = 118f
    const val RECT_INK_H = 114.4f

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
     * reading: see `TrioAppearance.capsuleRight`.
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
    const val CAP_BAND_H = 9f

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
     * The ink height every reading and icon in the capsule is drawn at, in design
     * units: [#CAP_TEXT_FILL] of [#CAP_INTERIOR_REF_H].
     *
     * The same number [#CAP_TEXT_SCALE] produces, restated as the height the icons
     * are matched against so the two cannot drift apart. The reference kept its arcs
     * and its digits the same height to the pixel, and this keeps that relationship
     * at the larger fill.
     */
    /**
     * How much of the capsule's interior an icon fills.
     *
     * Above the text's own [#CAP_TEXT_FILL]: a three-arc icon carries four levels in the
     * same ink a digit uses for one glyph, so matching the cap height left every arc a
     * couple of units tall and the levels indistinguishable at status-bar size.
     */
    const val CAP_ICON_FILL = 0.72f

    const val CAP_ICON_H = CAP_ICON_FILL * CAP_INTERIOR_REF_H

    /**
     * The capsule's ink box: the outline plus its stroke, which is what has to
     * fit inside the host view.
     *
     * Sized against the largest stroke the slider offers rather than the
     * default, the way [#RECT_INK_W] is: fitting the extremes means no
     * setting can push ink outside the host, where the platform clips it.
     */
    const val CAP_INK_X = CAP_LEFT - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f

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
    fun capExtra(a: TrioAppearance?): Float = Prefs.clamp(
        a?.capsuleLength ?: Prefs.DEF_CAPSULE_LENGTH,
        Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH,
    ).toFloat()

    /** The capsule's ink box, widened by the user's extra run. */
    @JvmStatic
    fun capInkW(a: TrioAppearance?): Float = CAP_INK_W + capExtra(a)

    /** The two-cell body's right edge, widened by the user's extra run. */
    @JvmStatic
    fun capPairRight(a: TrioAppearance?): Float = CAP_RIGHT_PAIR + capExtra(a)

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
        val half = Math.toRadians(((GAP_END_IDLE - GAP_START_IDLE) * B_SWEEP * 0.5f).toDouble())
        return ((2.0 * B_R * kotlin.math.sin(half))).toFloat() - stroke
    }

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
            // The ring's interior, less a margin so the ink does not touch the arc.
            // The interior is measured on the arc's inside edge; the extra two units
            // clear the antialiased rim on both horizontal edges of the line, which is
            // what turned "fits by measurement" into a two-pixel overlap.
            return 2f * (B_R - Prefs.DEF_RING_STROKE) - 2f * ZONE_TEXT_INSET - 2f * ZONE_TYPE_FRINGE
        }
        // A signal-dot box, less the same margin.
        return signalDotBoxH(z) - 2f * ZONE_TEXT_INSET
    }

    /**
     * The height of the level-dot box a slot stands in for: `28` in the top
     * third and `20` in the bottom.
     *
     * These are the two figures `TrioRenderer.drawFreeRing` fits the dot
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
        return INK_W / 2f - B_R
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
     * @param scale `TrioAppearance.dotScaleFactor()`: 1.0 for the reference bead
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
            return ((B_R * Math.toRadians((360f - B_SWEEP).toDouble()))).toFloat()
        }
        return ((B_R * Math.toRadians((GAP_WIDTH_DEG).toDouble()))).toFloat()
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
            if (kotlin.math.abs(rowA - B_CY) > kotlin.math.abs(rowB - B_CY)) rowA else rowB
        val dy = binding - B_CY
        val inside = B_R.toDouble() * B_R - dy * dy
        if (inside <= 0) {
            return maxOf(0f, gapClearWidth(ringStroke.toFloat()) - 2f * ZONE_TYPE_FRINGE)
        }
        val chord = ((2.0 * kotlin.math.sqrt(inside))).toFloat()
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
        val chord = kotlin.math.sin(halfAngle).toFloat() * 2f * B_R
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
            DOTS_DUAL[i][1] = 2f * B_CY - element[1]
            DOTS_DUAL[i + DOTS.size][0] = element[0]
            DOTS_DUAL[i + DOTS.size][1] = element[1]
        }
    }

    //
    // The out-of-ring reading stands in the status bar, not in the glyph, so
    // none of the ink box above applies to it. Its numbers are quoted in the
    // 350x350 reference image and kept in that image's own units; the view
    // measures itself against them and scales to whatever height the status bar
    // row gives it. Working in the reference's units rather than in dp is what
    // keeps the two rows, the bar steps and the round caps in the proportions
    // the reference draws, at every density.

    /**
     * Bar heights, ascending left to right, in reference pixels.
     *
     * An even 25px step - 75, 100, 125, 150 - on a shared baseline, which is
     * what makes the row read as a level at a glance. The baseline is the
     * *bottom*: the bars grow upwards, so index `3` is both the
     * tallest bar and the height of the whole row.
     */
    @JvmField
    val STACK_BAR_H = floatArrayOf(40f, 76f, 110f, 142f)
    @JvmField
    val STACK_BAR_H_5 = floatArrayOf(30f, 58f, 86f, 114f, 142f)

    //
    // Everything below lays the same pieces out the other way: two columns of
    // four dots carry the mobile signal, the top slot between them carries the
    // Wi-Fi arcs, the network type or the charging bolt, the percentage sits
    // enlarged below that, and a full-width bar along the bottom is the battery.
    // There is no ring at all.
    //
    // The coordinates still live in the same 120x120 design space, so the two
    // arrangements differ only in where the pieces go and in the ink box used to
    // fit them into the host view.

    /** Keeps the Wi-Fi group horizontally centred on the icon axis. */
    @JvmStatic
    fun rectWifiOffsetX(factor: Float = 1f): Float {
        return 60f - W_CX * RECT_WIFI_SCALE * factor
    }

    /** Puts the outer arc's apex, half a stroke above its centre line, at [#RECT_WIFI_TOP]. */
    @JvmStatic
    fun rectWifiOffsetY(stroke: Float, factor: Float = 1f): Float {
        return RECT_WIFI_TOP - (W_TOP - stroke * 0.5f) * RECT_WIFI_SCALE * factor
    }

    /** The rectangle's bolt offset for a bolt scaled by `factor`; see [#boltOffsetX(float)]. */
    @JvmStatic
    fun rectBoltOffsetX(factor: Float): Float {
        return boltOffsetX(factor) + RECT_BOLT_DX
    }

    @JvmStatic
    fun rectBoltOffsetY(factor: Float): Float {
        return boltOffsetY(factor) + RECT_BOLT_DY
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

    init {
        checkInkBox()
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
        if (kotlin.math.abs(INK_X + INK_W / 2f - B_CX) > 0.01f
            || kotlin.math.abs(INK_Y + INK_H / 2f - B_CY) > 0.01f
        ) {
            throw AssertionError(
                "ink box is not centred on the ring: box centre "
                        + (INK_X + INK_W / 2f) + "," + (INK_Y + INK_H / 2f)
                        + " vs ring " + B_CX + "," + B_CY
            )
        }
        if (kotlin.math.abs(INK_W - INK_H) > 0.01f) {
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
            val dx = kotlin.math.abs(B_R * kotlin.math.cos(angle).toFloat()) + capPad
            val dy = kotlin.math.abs(B_R * kotlin.math.sin(angle).toFloat()) + capPad
            reach = maxOf(reach, maxOf(dx, dy))
        }
        val stroke = Prefs.MAX_RING_STROKE
        val halfOpening = Math.toRadians(GAP_WIDTH_DEG * 0.5).toFloat()
        // Both ends of the dot-size slider, because the beads are the one piece whose ink
        // the user moves directly: at MAX_DOT_SCALE they are what reaches furthest, and at
        // MIN_DOT_SCALE they shrink away and the arc's own caps become the outer limit.
        // Checking only the reference size is exactly what let a larger bead overrun the
        // box before maxBeadR() existed.
        for (scalePct in intArrayOf(
            Prefs.MIN_DOT_SCALE, Prefs.DEF_DOT_SCALE,
            Prefs.MAX_DOT_SCALE
        )) {
            val beadR = arcRowDotR(0, stroke, 78f - stroke, scalePct / 100f)
            val inset = (beadR + stroke * 0.5f + 1f) / B_R
            val beadHalfAngle = minOf(
                halfOpening - inset,
                Math.toRadians(BEAD_SPAN_DEG * 0.5).toFloat(),
            )
            for (row in 0 until 2) {
                for (i in -1..1) {
                    val angle = beadHalfAngle * i
                    val dx = kotlin.math.abs(B_R * kotlin.math.sin(angle)) + beadR
                    val dy = kotlin.math.abs(B_R * kotlin.math.cos(angle)) + beadR
                    reach = maxOf(reach, maxOf(dx, dy))
                }
            }
            // The ceiling itself has to be a real one: a bead radius past maxBeadR() would
            // put the outer edge outside the box, whatever the opening allows.
            if (beadR > maxBeadR() + 0.01f) {
                throw AssertionError(
                    "dot radius " + beadR + " at " + scalePct
                            + "% exceeds what the ink box holds: " + maxBeadR()
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
        // Dead on the path line, which is what the reference does: the label straddles the
        // outline, half of it above and half below, so the gap the dot row opened in the
        // stroke is exactly where the label sits. The band above the ink box is therefore
        // only half a label tall, and the other half lives in the opening itself.
        return 0f
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
        // The budget is measured from the PATH LINE outwards on both sides, because that is
        // where the reading is centred. Its inward half runs down to the interior's own top -
        // the first thing it could collide with, which is the clock - and its outward half runs
        // up to the box's edge. The two are not equal: the band is one stroke's worth taller
        // than the interior's clearance, so the INWARD half is what binds.
        //
        // Note what is NOT subtracted again: the path line is the stroke's centre, so a piece
        // centred on it has already cleared half the stroke by construction. Taking the
        // stroke's half out a second time - which this did - charged the same clearance twice.
        val inward = capInteriorTop(stroke) - CAP_TOP
        val outward = CAP_TOP - CAP_INK_TOP
        val half = minOf(inward, outward) - CAP_OPENING_GAP
        // Ink on both sides of the line, so the ink budget is twice the smaller half.
        val ink = 2f * half
        // No extra reduction. The clearance to the interior already leaves this at about 0.81
        // of the body's readings, which is the reference's own 0.82 - applying a ratio on top
        // of that shrank the label twice for the same reason, which is what made the network
        // type look wrong in the top opening. The proportion is a CONSEQUENCE of the geometry,
        // not a second knob. This is an INK height - see [capOpeningTextEm], which is
        // what the text fit actually wants.
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
    fun capPieceReach(part: Int, level: Int, type: String, stroke: Float, roomH: Float): Float {
        return when (part) {
            // The arcs are drawn at capWifiScale about their own centre.
            Prefs.PART_WIFI -> W_INK_W * capWifiScale(stroke) * 0.5f
            // The bolt path is 12.5 units wide against its 19.9 of height; the reach is
            // half of that, scaled the way the bolt itself is.
            Prefs.PART_BOLT -> 6.25f * capBoltScale(stroke)
            // Three digits is the widest this can get.
            Prefs.PART_VALUE -> capTextReach("100", stroke, roomH)
            Prefs.PART_TYPE -> capTextReach("5GA", stroke, roomH)
            Prefs.PART_CLOCK -> capTextReach("19:41", stroke, roomH)
            // One line each, and the widest form each can take.
            Prefs.PART_POWER, Prefs.PART_RUNNING -> capTextReach("99.9W", stroke, roomH)
            Prefs.PART_TEMP -> capTextReach("45°C", stroke, roomH)
            Prefs.PART_FPS -> capTextReach("120", stroke, roomH)
            else -> 0f
        }
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
        // The em the renderer will actually draw at: the user's own size, clamped by the row's
        // budget. Both are ems, so they compare directly. Clamping the reach and clamping the
        // drawn size have to use the same number or the gap gets cut for a glyph that is not
        // there - too narrow and the stroke runs through the label, too wide and the outline is
        // interrupted for nothing.
        val em = minOf(Prefs.DEF_VALUE_SIZE * CAP_TEXT_SCALE, roomH)
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
        return minOf(CAP_ICON_H / W_INK_H, capInteriorHeight(stroke) / W_INK_H)
    }

    @JvmStatic
    fun capBoltScale(stroke: Float): Float {
        return minOf(CAP_ICON_H / BOLT_INK_H, capInteriorHeight(stroke) / BOLT_INK_H)
    }

    /** Places the arcs' ink centre on the right-hand slot's own centre. */
    @JvmStatic
    fun capWifiOffsetX(stroke: Float): Float {
        return capRightCx(stroke) - W_CX * capWifiScale(stroke)
    }

    @JvmStatic
    fun capWifiOffsetY(stroke: Float): Float {
        return capInteriorCy(stroke) - W_INK_CY * capWifiScale(stroke)
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
    fun hostAspect(a: TrioAppearance?): Float {
        if (a == null) {
            return INK_W / INK_H
        }
        if (a.capsule) {
            // The capsule's own box plus one band at each end: the body keeps its height
            // and the bands are the extra room the opening readings need.
            return capInkW(a) / (CAP_INK_H + 2f * CAP_BAND_H)
        }
        if (a.rect) {
            return RECT_INK_W / RECT_INK_H
        }
        return INK_W / INK_H
    }

}
