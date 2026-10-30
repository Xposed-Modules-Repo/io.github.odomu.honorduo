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

/**
 * The rectangular arrangement: two columns of level dots, the value between them and
 * the battery bar across the bottom.
 */
object RectGeometry {
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


    /**
     * The weather icon's box, as a share of the caption's text size.
     *
     * A little taller than a digit's cap height so the icon reads at the same weight
     * as the numbers beside it. It stays under 1.0 on purpose: past that the lowest
     * ink (a rain drop, a snow flake) reaches into the row below, which is what made
     * the two-row clock look cramped.
     */
    const val WEATHER_ICON_SCALE = 0.85f


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
     * Its thickness is `ModuleSettings#ringStroke` - the ring the slider
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
     *   - the tappable centre column in `IconZones` - which decides whether
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
        return 60f - IconGeometry.W_CX * RECT_WIFI_SCALE * factor
    }


    /** Puts the outer arc's apex, half a stroke above its centre line, at [#RECT_WIFI_TOP]. */
    @JvmStatic
    fun rectWifiOffsetY(stroke: Float, factor: Float = 1f): Float {
        return RECT_WIFI_TOP - (IconGeometry.W_TOP - stroke * 0.5f) * RECT_WIFI_SCALE * factor
    }


    /** The rectangle's bolt offset for a bolt scaled by `factor`; see [#boltOffsetX(float)]. */
    @JvmStatic
    fun rectBoltOffsetX(factor: Float): Float {
        return IconGeometry.boltOffsetX(factor) + RECT_BOLT_DX
    }

    @JvmStatic
    fun rectBoltOffsetY(factor: Float): Float {
        return IconGeometry.boltOffsetY(factor) + RECT_BOLT_DY
    }
}
