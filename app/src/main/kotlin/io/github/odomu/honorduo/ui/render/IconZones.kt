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

import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The three tappable thirds of the glyph, and which drawn piece sits in each.
 *
 * This is the rule behind the settings screen's visual editor: the glyph is shown
 * large, the user taps its top, middle or bottom, and the switch for whatever is drawn
 * there flips. For that to be honest two things must hold, and this object is where
 * both are stated once:
 *
 * - **Where the thirds are.** [boxes] gives each in the same 120x120 design units the
 *   rest of the renderer uses, and [zoneAt] inverts that through the very transform
 *   [IconRenderer.designTransform] draws with - so a hot spot lands on the pixels it
 *   names rather than near them.
 * - **What is in them.** [occupant] reads the slot arbitration [StatusAppearance] has
 *   already performed and only *places* each decided piece in a third. It decides
 *   nothing itself: with the drawing rule and this table free to disagree, the editor
 *   would name a piece the user is not looking at.
 */
object IconZones {

    /** The three bands, in the order [Prefs.ZONE_*] stores them. */
    enum class Zone { TOP, CENTRE, BOTTOM }

    /** The pieces the editor can point at. */
    enum class Part {
        BOLT, WIFI, MOBILE, VALUE, TYPE, SIM_1, SIM_2, CLOCK, POWER, TEMP, RUNNING, FPS, CURRENT,
    }

    /**
     * The rectangular arrangement's two horizontal cuts, in design units.
     *
     * Measured against the ink the pieces actually produce rather than chosen as round
     * numbers: the top slot's ink bottom reaches 48.06 at the thickest arc stroke, the
     * percentage's ink top 49.04 at the largest size, so 48.5 sits in the narrowest window
     * in the whole feature. The bar's ink top reaches 99.3 at the thickest stroke and the
     * percentage's ink bottom is 95.4, so 96 sits between them.
     */
    private const val RECT_TOP_BOTTOM = 48.5f
    private const val RECT_CENTRE_BOTTOM = 96f

    /**
     * The rectangle's vertical seam: exactly the box the renderer bounds the centre text
     * by, read off [IconGeometry].
     *
     * Derived rather than restated. The renderer fits the percentage into
     * `RECT_TEXT_LEFT..RIGHT`, so a three-digit 100 % at a large size reaches those edges -
     * and a seam even one design unit inside them would put the right-hand stroke in the
     * mobile column, where a tap turns the signal meter off instead of flipping the number.
     */
    private const val RECT_SEAM_LEFT = IconGeometry.RECT_TEXT_LEFT
    private const val RECT_SEAM_RIGHT = IconGeometry.RECT_TEXT_RIGHT

    /**
     * The ring's two cuts, taken from [IconGeometry].
     *
     * The renderer needs them as well: a piece placed in a third is sized by the room
     * that third gives, so the band the editor hit-tests and the band the text is fitted
     * into have to be the same band.
     */
    private const val RING_TOP_BOTTOM = IconGeometry.ZONE_CUT_TOP
    private const val RING_CENTRE_BOTTOM = IconGeometry.ZONE_CUT_BOTTOM

    /**
     * The design-space boxes a zone covers, for the ring and the rectangle.
     *
     * In the ring the bands run the full width of the design box: the ring's own ink
     * reaches the edges at mid-height, and a tap there has to land somewhere rather than
     * fall through a gap between two inset columns. In the rectangle the two dot columns
     * are one component - the mobile meter - so they are the bottom zone at every height,
     * and only the strip between them is cut into three bands.
     *
     * @return boxes as `{left, top, right, bottom}`, or an empty list for an unknown zone
     */
    @JvmStatic
    fun boxes(zone: Zone, rect: Boolean): Array<FloatArray> {
        if (rect) {
            return when (zone) {
                Zone.TOP -> arrayOf(
                    floatArrayOf(RECT_SEAM_LEFT, 0f, RECT_SEAM_RIGHT, RECT_TOP_BOTTOM),
                )
                Zone.CENTRE -> arrayOf(
                    floatArrayOf(
                        RECT_SEAM_LEFT, RECT_TOP_BOTTOM, RECT_SEAM_RIGHT, RECT_CENTRE_BOTTOM,
                    ),
                )
                Zone.BOTTOM -> arrayOf(
                    // The two columns, then the strip the bar sits in.
                    floatArrayOf(0f, 0f, RECT_SEAM_LEFT, IconGeometry.BOX),
                    floatArrayOf(RECT_SEAM_RIGHT, 0f, IconGeometry.BOX, IconGeometry.BOX),
                    floatArrayOf(
                        RECT_SEAM_LEFT, RECT_CENTRE_BOTTOM, RECT_SEAM_RIGHT, IconGeometry.BOX,
                    ),
                )
            }
        }
        return when (zone) {
            Zone.TOP -> arrayOf(
                floatArrayOf(0f, 0f, IconGeometry.BOX, RING_TOP_BOTTOM),
            )
            Zone.CENTRE -> arrayOf(
                floatArrayOf(0f, RING_TOP_BOTTOM, IconGeometry.BOX, RING_CENTRE_BOTTOM),
            )
            // Full width: the level dots sit along the arc's own opening, which is the
            // full width of the glyph at that height.
            Zone.BOTTOM -> arrayOf(
                floatArrayOf(0f, RING_CENTRE_BOTTOM, IconGeometry.BOX, IconGeometry.BOX),
            )
        }
    }

    /**
     * The boxes for whichever arrangement is in force, including the capsule.
     *
     * The capsule maps the three zones onto its own shape: the top and bottom openings,
     * and the whole interior. There is no reserved left column - the clock is a piece like
     * any other now, so whatever the centre band holds owns all of the body.
     */
    @JvmStatic
    fun boxes(zone: Zone, a: StatusAppearance): Array<FloatArray> {
        if (!a.capsule) return boxes(zone, a.rect)
        val stroke = a.ringStroke * IconGeometry.CAP_STROKE_RATIO
        val top = IconGeometry.capInteriorTop(stroke)
        val bottom = IconGeometry.capInteriorBottom(stroke)
        // Include the antialiased fringe outside the mathematical stroke bounds; this does
        // not move the seams between the readings and the signal rows.
        val fringe = 1f
        // The drawn shape, not the ink box: with two pieces in the body the capsule is
        // the wider one, and a tap past its edge is not a tap on the capsule.
        val shapeRight = a.capsuleRight()
        val shapeLeft = IconGeometry.capLeftFor(shapeRight, IconGeometry.capPairRight(a))
        val left = shapeLeft + stroke * 0.5f - fringe
        val right = shapeRight + stroke * 0.5f + fringe
        return when (zone) {
            Zone.TOP -> arrayOf(
                floatArrayOf(left, IconGeometry.CAP_INK_TOP - fringe, right, top),
            )
            Zone.CENTRE -> arrayOf(
                floatArrayOf(IconGeometry.capInteriorLeft(stroke), top, right, bottom),
            )
            Zone.BOTTOM -> arrayOf(
                floatArrayOf(left, bottom, right, IconGeometry.CAP_INK_BOTTOM + fringe),
            )
        }
    }

    /** The zone a view-space point lands in, or `null` outside every box. */
    @JvmStatic
    fun zoneAt(transform: FloatArray?, a: StatusAppearance?, viewX: Float, viewY: Float): Zone? {
        if (a == null || transform == null || transform.size < 3 || transform[0] <= 0f) return null
        val x = (viewX - transform[1]) / transform[0]
        val y = (viewY - transform[2]) / transform[0]
        for (zone in Zone.entries) {
            for (box in boxes(zone, a)) if (inside(box, x, y)) return zone
        }
        return null
    }

    private fun inside(box: FloatArray, x: Float, y: Float): Boolean =
        x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]

    /** Whether a design-space point lies in `zone`, for the ring or the rectangle. */
    @JvmStatic
    fun inZone(zone: Zone, rect: Boolean, designX: Float, designY: Float): Boolean =
        boxes(zone, rect).any { inside(it, designX, designY) }

    /** The same hit test for a view-space point, in design units. */
    @JvmStatic
    fun zoneAt(transform: FloatArray?, rect: Boolean, viewX: Float, viewY: Float): Zone? {
        if (transform == null || transform.size < 3) return null
        val scale = transform[0]
        if (scale <= 0f) return null
        val designX = (viewX - transform[1]) / scale
        val designY = (viewY - transform[2]) / scale
        return Zone.entries.firstOrNull { inZone(it, rect, designX, designY) }
    }

    /** Maps a design-space point back into view space, or reports that it cannot. */
    @JvmStatic
    fun toView(transform: FloatArray?, designX: Float, designY: Float, out: FloatArray?): Boolean {
        if (transform == null || transform.size < 3 || out == null || out.size < 2 ||
            transform[0] <= 0f
        ) {
            return false
        }
        out[0] = designX * transform[0] + transform[1]
        out[1] = designY * transform[0] + transform[2]
        return true
    }

    /** The largest share of the shorter edge the preview may inset. */
    const val MAX_INSET = 0.4f

    /**
     * The transform the preview draws with: the glyph's own fit, inside an inset cell.
     *
     * @return `{scale, translateX, translateY}`, or `null` when there is nothing to map
     */
    @JvmStatic
    fun viewTransform(
        width: Int,
        height: Int,
        inset: Float,
        appearance: StatusAppearance?,
    ): FloatArray? {
        if (width <= 0 || height <= 0 || appearance == null) return null
        val safeInset = inset.coerceIn(0f, MAX_INSET)
        val pad = minOf(width, height) * safeInset
        val innerW = (width - 2f * pad).roundToInt()
        val innerH = (height - 2f * pad).roundToInt()
        if (innerW <= 0 || innerH <= 0) return null
        val t = FloatArray(3)
        if (!IconRenderer.designTransform(innerW, innerH, appearance, t)) return null
        t[1] += pad
        t[2] += pad
        return t
    }

    /** What the ring arrangement draws in `zone` this frame. */
    @JvmStatic
    fun occupant(zone: Zone, a: StatusAppearance?, r: StatusAppearance.Ring?): Part? {
        if (a == null || r == null) return null
        // One answer, because there is one layout: the slot says what is drawn, and
        // StatusAppearance has already blanked the slots whose piece has nothing to show
        // this frame.
        return decodePart(r.parts[zone.ordinal])
    }

    /**
     * What the rectangular arrangement draws in `zone` this frame.
     *
     * Mutually exclusive by construction, so the first match wins and the rest are
     * unreachable.
     */
    @JvmStatic
    fun occupant(zone: Zone, a: StatusAppearance?, rc: StatusAppearance.Rect?): Part? {
        if (a == null || rc == null) return null
        return when (zone) {
            Zone.TOP -> when {
                rc.bolt -> Part.BOLT
                rc.wifi -> Part.WIFI
                rc.type -> Part.TYPE
                else -> null
            }
            Zone.CENTRE -> if (rc.value) Part.VALUE else null
            // The bar is the battery and is not optional; what the user can switch down
            // here is the dot columns framing it.
            Zone.BOTTOM -> if (rc.dots) Part.MOBILE else null
        }
    }

    /**
     * What the capsule arrangement draws in `zone` this frame.
     *
     * A band holding a dot row reports that row, whatever the layout's id list says
     * after the cell was resolved: the row IS the band's piece, and the editor has to name
     * the thing the user can see there.
     */
    @JvmStatic
    fun occupant(zone: Zone, a: StatusAppearance?, cap: StatusAppearance.Capsule?): Part? {
        if (a == null || cap == null) return null
        if (cap.dotsIn(zone)) return Part.MOBILE
        return decodePart(cap.parts[zone.ordinal])
    }

    /** What the arrangement in force draws in `zone` for these readings. */
    @JvmStatic
    fun occupant(
        zone: Zone,
        a: StatusAppearance?,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ): Part? {
        if (a == null) return null
        if (a.capsule) return occupant(zone, a, a.capsule(level, charging, wifiLevel, mobileType, sims))
        return if (a.rect) {
            occupant(zone, a, a.rect(level, charging, wifiLevel, mobileType, sims))
        } else {
            occupant(zone, a, a.ring(level, charging, wifiLevel, mobileType, sims))
        }
    }

    /**
     * Whether a view-space tap lands on the ring's own outline.
     *
     * Radial distance within a tolerance of the arc's radius, then the arc's angular
     * span - which follows the sweep actually drawn, so with the bottom closed there is ink
     * all the way round and a tap down there lands on the ring. The [Zone] bands stay where
     * they are; this only answers "is this tap on the battery outline".
     */
    @JvmStatic
    fun batteryRingAt(
        transform: FloatArray?,
        a: StatusAppearance?,
        ring: StatusAppearance.Ring?,
        viewX: Float,
        viewY: Float,
    ): Boolean {
        if (transform == null || transform.size < 3 || transform[0] <= 0f ||
            a == null || !a.ring || ring == null
        ) {
            return false
        }
        val x = ((viewX - transform[1]) / transform[0] - IconGeometry.B_CX).toDouble()
        val y = ((viewY - transform[2]) / transform[0] - IconGeometry.B_CY).toDouble()
        val tolerance = maxOf(5f, a.ringStroke * .5f + 2f).toDouble()
        if (abs(hypot(x, y) - IconGeometry.B_R) > tolerance) return false
        val cap = Math.toDegrees(atan2(tolerance, IconGeometry.B_R.toDouble()))
        val sweep = ring.sweep()
        val start = ring.arcStart()
        var angle = (Math.toDegrees(atan2(y, x)) - start + 360) % 360
        if (angle > 360 - cap) angle -= 360
        if (angle < -cap || angle > sweep + cap) return false
        val progress = angle / sweep
        val pad = cap / sweep
        return ring.gapStart() == ring.gapEnd() ||
            progress <= ring.gapStart() + pad ||
            progress >= ring.gapEnd() - pad
    }

    /** Whether the ring's `zone` may hold `part`. */
    @JvmStatic
    fun ringAccepts(zone: Zone?, part: Part?): Boolean =
        zone == null || part == null || Prefs.zoneAccepts(zone.ordinal, encodePart(part))

    /** Whether this arrangement places pieces in bands at all. */
    @JvmStatic
    fun hasSlots(settings: ModuleSettings?): Boolean =
        settings != null && settings.trioStyle != Prefs.STYLE_RECT

    /** Whether `part` is a row of level dots. */
    @JvmStatic
    fun isSignalRow(part: Part?): Boolean = part != null && Prefs.isSignalRow(encodePart(part))

    /**
     * Whether `part` may be placed in `zone`.
     *
     * Only the ring refuses a placement; the capsule and the rectangle have no middle
     * band to get wrong.
     */
    @JvmStatic
    fun accepts(zone: Zone, part: Part?, current: ModuleSettings?): Boolean {
        if (current == null || part == null) return true
        return current.trioStyle != Prefs.STYLE_RING || ringAccepts(zone, part)
    }

    /** The stored id for `part`. */
    @JvmStatic
    fun encodePart(part: Part?): Int = when (part) {
        Part.WIFI -> Prefs.PART_WIFI
        Part.MOBILE -> Prefs.PART_MOBILE
        Part.SIM_1 -> Prefs.PART_SIM_1
        Part.SIM_2 -> Prefs.PART_SIM_2
        Part.VALUE -> Prefs.PART_VALUE
        Part.BOLT -> Prefs.PART_BOLT
        Part.TYPE -> Prefs.PART_TYPE
        Part.CLOCK -> Prefs.PART_CLOCK
        Part.POWER -> Prefs.PART_POWER
        Part.TEMP -> Prefs.PART_TEMP
        Part.RUNNING -> Prefs.PART_RUNNING
        Part.FPS -> Prefs.PART_FPS
        Part.CURRENT -> Prefs.PART_CURRENT
        null -> Prefs.PART_NONE
    }

    /** The piece a stored id names, or `null` for none. */
    @JvmStatic
    fun decodePart(id: Int): Part? = when (id) {
        Prefs.PART_WIFI -> Part.WIFI
        Prefs.PART_MOBILE -> Part.MOBILE
        Prefs.PART_VALUE -> Part.VALUE
        Prefs.PART_BOLT -> Part.BOLT
        Prefs.PART_TYPE -> Part.TYPE
        Prefs.PART_CLOCK -> Part.CLOCK
        Prefs.PART_SIM_1 -> Part.SIM_1
        Prefs.PART_SIM_2 -> Part.SIM_2
        Prefs.PART_POWER -> Part.POWER
        Prefs.PART_TEMP -> Part.TEMP
        Prefs.PART_RUNNING -> Part.RUNNING
        Prefs.PART_FPS -> Part.FPS
        Prefs.PART_CURRENT -> Part.CURRENT
        else -> null
    }

    /**
     * The snapshot the editor should save against.
     *
     * The preview resolves a temporary arrangement from the current snapshot, so the
     * stored value is already the one to edit.
     */
    @JvmStatic
    fun editableRingSettings(
        current: ModuleSettings,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ): ModuleSettings = current

    /** Explicit placement: the settings that put `want` in `zone`. */
    @JvmStatic
    fun place(zone: Zone?, want: Part?, current: ModuleSettings?, sims: Int): Assignment? {
        if (current == null || zone == null) return null
        return assign(zone, current.trioStyle == Prefs.STYLE_RECT, want, current, sims)
    }

    /**
     * `current` with `want` at the front of `zone`'s priority list.
     *
     * Three behaviours in one, because they are the same write seen from different
     * starting points: a piece that was not placed anywhere is **added**; one already in
     * this zone moves to the front, so the user can promote it; one that was in another
     * zone is **moved**, removed from there - the same piece in two zones would draw twice
     * and make the priority lists disagree about which copy is the real one.
     *
     * Passing `null` clears the zone instead. To drop a single candidate see [withoutAt]:
     * "empty this third" and "remove this one entry" are different intentions.
     *
     * A placement the ring refuses - a signal row in the middle band - is handed back
     * unchanged rather than written, so no caller can store a layout the renderer would
     * draw as a smudge.
     */
    @JvmStatic
    fun withPiece(current: ModuleSettings, zone: Int, want: Part?): ModuleSettings {
        if (zone !in 0 until Prefs.ZONE_COUNT) return current
        if (!accepts(Zone.entries[zone], want, current)) return current
        val next = current.copy()
        val id = encodePart(want)
        if (id == Prefs.PART_NONE) {
            val cleared = ModuleSettings.copyZones(current.ringZones)
            cleared[zone] = IntArray(0)
            next.ringZones = cleared
            return next
        }
        val lists = ArrayList<ArrayList<Int>>(Prefs.ZONE_COUNT)
        for (z in 0 until Prefs.ZONE_COUNT) {
            // Drop the id everywhere, then re-add it at the target's front: that single
            // rule gives add, promote and move without three separate paths.
            lists.add(current.ringZones[z].filterTo(ArrayList()) { it != id })
        }
        lists[zone].add(0, id)
        next.ringZones = Array(Prefs.ZONE_COUNT) { z -> lists[z].toIntArray() }
        next.enabled = true
        next.mergeIcons = true
        // A placed piece needs its own switch on, or the zone would reserve space it then
        // draws nothing in.
        val hasWifiNow = next.ringZones.any { it.contains(Prefs.PART_WIFI) }
        val hasValueNow = next.ringZones.any { it.contains(Prefs.PART_VALUE) }
        val hasBoltNow = next.ringZones.any { it.contains(Prefs.PART_BOLT) }
        val hasMobileNow = next.ringZones.any { it.contains(Prefs.PART_MOBILE) || it.contains(Prefs.PART_SIM_1) || it.contains(Prefs.PART_SIM_2) }
        next.showWifi = hasWifiNow
        next.showValue = hasValueNow
        next.showBolt = hasBoltNow
        if (hasMobileNow) {
            next.showMobile = true
            if (want == Part.SIM_1 || want == Part.SIM_2 || want == Part.MOBILE) {
                next.signalMode = Prefs.SIGNAL_IN_RING
            }
        }
        if (want == Part.SIM_1 || next.ringZones.any { it.contains(Prefs.PART_SIM_1) }) {
            next.showSim1 = true
        }
        if (want == Part.SIM_2 || next.ringZones.any { it.contains(Prefs.PART_SIM_2) }) {
            next.showSim2 = true
        }
        if (want == Part.TYPE || next.ringZones.any { it.contains(Prefs.PART_TYPE) }) {
            next.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING
        }
        if (want == Part.FPS || next.ringZones.any { it.contains(Prefs.PART_FPS) }) {
            next.perfFps = true
        }
        if (want == Part.CURRENT || next.ringZones.any { it.contains(Prefs.PART_CURRENT) }) {
            next.perfCurrent = true
        }
        if (want == Part.POWER || next.ringZones.any { it.contains(Prefs.PART_POWER) }) {
            next.perfPower = true
        }
        if (want == Part.TEMP || next.ringZones.any { it.contains(Prefs.PART_TEMP) }) {
            next.perfTemp = true
        }
        if (want == Part.RUNNING || next.ringZones.any { it.contains(Prefs.PART_RUNNING) }) {
            next.perfRunning = true
        }
        return next
    }

    /**
     * `current` with the entry at `index` dropped from `zone`'s list.
     *
     * Separate from [withPiece] because when a zone's head is removed the user expects
     * the piece behind it to take over, not the whole chain to vanish.
     */
    @JvmStatic
    fun withoutAt(current: ModuleSettings, zone: Int, index: Int): ModuleSettings {
        if (zone !in 0 until Prefs.ZONE_COUNT) return current
        val list = current.ringZones[zone]
        if (index !in list.indices) return current
        val next = current.copy()
        val zones = ModuleSettings.copyZones(current.ringZones)
        zones[zone] = list.filterIndexed { i, _ -> i != index }.toIntArray()
        next.ringZones = zones
        next.showWifi = zones.any { it.contains(Prefs.PART_WIFI) }
        next.showValue = zones.any { it.contains(Prefs.PART_VALUE) }
        next.showBolt = zones.any { it.contains(Prefs.PART_BOLT) }
        return next
    }

    /**
     * `current` with the entries of `zone` swapped around `index`.
     *
     * Out of range is a no-op rather than an error: the buttons at the ends of the list
     * stay present and simply do nothing, which is honest feedback.
     *
     * @param delta -1 to promote one place, +1 to demote
     */
    @JvmStatic
    fun moveWithin(current: ModuleSettings, zone: Int, index: Int, delta: Int): ModuleSettings {
        if (zone !in 0 until Prefs.ZONE_COUNT) return current
        val zones = ModuleSettings.copyZones(current.ringZones)
        val list = zones[zone]
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return current
        val swapped = list[index]
        list[index] = list[to]
        list[to] = swapped
        return current.copy().also { it.ringZones = zones }
    }

    /**
     * The pieces `zone` can hold.
     *
     * [Part.MOBILE] is deliberately excluded: the aggregate meter is no longer a piece
     * of its own. The signal is the two cards' rows, which read the same ink but each show
     * one card's level. The ring and the capsule share the slot-driven list; only the
     * rectangle still asks the switch solver, because it has no bands to place into.
     */
    @JvmStatic
    fun placementChoices(zone: Zone, current: ModuleSettings?, sims: Int): List<Part> =
        if (hasSlots(current)) {
            ringChoices(zone)
        } else {
            choices(zone, current!!.trioStyle == Prefs.STYLE_RECT, current, sims)
        }

    /** [placeableParts] minus the ones the ring refuses in `zone`. */
    @JvmStatic
    fun ringChoices(zone: Zone): List<Part> = placeableParts().filter { ringAccepts(zone, it) }

    /** Every piece the ring may hold, in enum order, minus the retired aggregate meter. */
    @JvmStatic
    fun placeableParts(): List<Part> = Part.entries.filter { it != Part.MOBILE }

    /**
     * The switch settings that put `want` into `zone`, or `null` when no combination can.
     *
     * The renderer does not have "put X in the notch" as a concept - it *derives* where
     * each piece goes from the switches, with a fixed precedence inside each slot. So an
     * assignment has to be solved for: find the switch combination whose derivation lands
     * `want` where the user asked. It is solved by **enumerating the relevant switch
     * combinations and asking [StatusAppearance] where each one puts things**, rather than by
     * a table of intended assignments - a hand-written table would be a second copy of the
     * arbitration rule, and the last feature here to keep two copies of one boundary
     * shipped a defect where tapping the battery percentage turned the signal meter off.
     *
     * Among all the combinations that work, the one returned is the least disruptive:
     * fewest switches changed, then fewest pieces hidden, then the enumerated order (so the
     * answer is deterministic).
     *
     * @param sims how many SIMs the device has a reading for; passed in rather than read
     *   from `current.dualSim` because one is a fact about the device and the other is the
     *   switch that decides whether to use it - deriving one from the other is circular
     */
    @JvmStatic
    fun assign(
        zone: Zone,
        rect: Boolean,
        want: Part?,
        current: ModuleSettings?,
        sims: Int,
    ): Assignment? {
        if (current == null) return null
        // A refused placement is unsolvable, so the page reports it as such instead of
        // writing a list the drawing would skip.
        if (!accepts(zone, want, current)) return null
        // The ring and the capsule both keep ordered lists per band, so "put X in Z" moves X
        // to the FRONT of Z's list: the newly chosen piece wins the band and the ones behind
        // it stay as fallbacks rather than being thrown away.
        if (hasSlots(current)) {
            val placed = withPiece(current, zone.ordinal, want)
            return Assignment(placed, distance(current, placed))
        }

        var best: Assignment? = null
        for (candidate in candidates(current, zone)) {
            if (!satisfies(candidate, zone, want)) continue
            best = better(best, candidate, current)
        }
        return best
    }

    /**
     * Whether this snapshot answers the request on every frame where the request is
     * meaningful.
     *
     * Which frames those are is the whole subtlety, and getting it wrong is how the
     * first version of this solver reported the bolt and the dual rows as unreachable: the
     * bolt is judged while charging, because that is the only frame it exists on; an empty
     * zone is judged with and without Wi-Fi and on both charging and idle frames, or a
     * cleared slot could immediately fill with a fallback type when Wi-Fi disconnects; and
     * everything else is judged while idle, because the percentage really is displaced by
     * the bolt while charging and that is existing behaviour the page has no business
     * overriding.
     */
    private fun satisfies(candidate: ModuleSettings, zone: Zone, want: Part?): Boolean {
        if (want == Part.BOLT) {
            return occupantOf(candidate, zone, ASSIGN_LEVEL, true, ASSIGN_WIFI_LEVEL) == Part.BOLT
        }
        if (want == null) {
            for (wifi in intArrayOf(-1, ASSIGN_WIFI_LEVEL)) {
                if (occupantOf(candidate, zone, ASSIGN_LEVEL, true, wifi) != null ||
                    occupantOf(candidate, zone, ASSIGN_LEVEL, false, wifi) != null
                ) {
                    return false
                }
            }
            return true
        }
        return occupantOf(candidate, zone, ASSIGN_LEVEL, false, ASSIGN_WIFI_LEVEL) == want
    }

    /** Where a snapshot puts a zone, on one frame. */
    private fun occupantOf(
        s: ModuleSettings,
        zone: Zone,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
    ): Part? {
        val a = StatusAppearance.of(s)
        return occupant(zone, a, level, charging, wifiLevel, ASSIGN_TYPE, 1)
    }

    /**
     * The level the solver reasons at: a two-digit reading, which is what the glyph spends
     * most of its life showing.
     */
    private const val ASSIGN_LEVEL = 79

    /** The Wi-Fi level the solver assumes; any level that draws ink answers the same. */
    private const val ASSIGN_WIFI_LEVEL = 3

    /** The network type the solver assumes; any non-empty type answers the same. */
    private const val ASSIGN_TYPE = "5G"

    /**
     * One solved assignment: the settings to write and how many switches they move.
     *
     * `changes` counts the switches that differ from the frame it was solved against,
     * which is what makes "the smallest edit that does what was asked" measurable.
     */
    class Assignment internal constructor(
        @JvmField val settings: ModuleSettings,
        @JvmField val changes: Int,
    )

    /** Picks the candidate that is least disruptive; see [assign]. */
    private fun better(best: Assignment?, candidate: ModuleSettings, current: ModuleSettings): Assignment {
        val changes = distance(current, candidate)
        if (best == null) return Assignment(candidate, changes)
        if (changes != best.changes) {
            return if (changes < best.changes) Assignment(candidate, changes) else best
        }
        val hiddenNow = hidden(candidate)
        val hiddenBest = hidden(best.settings)
        if (hiddenNow != hiddenBest) {
            return if (hiddenNow < hiddenBest) Assignment(candidate, changes) else best
        }
        // Equal on both counts: keep the incumbent, so the winner is decided by the
        // enumeration order rather than by which candidate happened to be compared last.
        return best
    }

    /**
     * How many pieces this snapshot switches off.
     *
     * Encodes a preference worth stating: moving a displaced piece is gentler than
     * hiding it. Emptying the ring's centre can be done either by moving the arcs into the
     * notch or by switching them off; both cost one switch, and this picks the move.
     */
    private fun hidden(s: ModuleSettings): Int {
        var n = 0
        if (!s.showWifi) n++
        if (!s.showMobile) n++
        if (!s.showValue) n++
        if (s.mobileTypeMode == Prefs.MOBILE_TYPE_OFF) n++
        if (!s.showSim1) n++
        if (!s.showSim2) n++
        return n
    }

    /** How many switches differ between two snapshots. */
    private fun distance(a: ModuleSettings, b: ModuleSettings): Int {
        var n = 0
        // The layout itself is an edit: a placement IS a slot write now, so leaving this
        // out would report "moved a component to another slot" as zero changes.
        if (!a.ringZones.contentDeepEquals(b.ringZones)) n++
        if (a.showWifi != b.showWifi) n++
        if (a.showMobile != b.showMobile) n++
        if (a.showValue != b.showValue) n++
        if (a.showBolt != b.showBolt) n++
        if (a.showSim1 != b.showSim1) n++
        if (a.showSim2 != b.showSim2) n++
        if (a.valueCentred != b.valueCentred) n++
        if (a.mobileTypeMode != b.mobileTypeMode) n++
        return n
    }

    /**
     * Every switch combination worth testing for a request about this zone.
     *
     * Not the full space: a switch that cannot reach the zone being asked about is held
     * at the user's value, which both shrinks the search and avoids proposing edits the
     * request did not need. The bolt is the one piece without a switch of its own - it is
     * drawn instead of the percentage while charging - so it is found because the solver
     * varies both.
     */
    private fun candidates(current: ModuleSettings, zone: Zone): List<ModuleSettings> {
        val out = ArrayList<ModuleSettings>()
        val bools = booleanArrayOf(false, true)
        val capsule = current.trioStyle == Prefs.STYLE_CAPSULE
        val centres = if (capsule) booleanArrayOf(current.valueCentred) else bools
        // Keep an existing outside label when a request does not need to change it: the
        // editor places pieces inside the glyph, so it may preserve OUT_RING but must not
        // turn that separate label on as a side effect of clearing ink.
        val typeModes = if (current.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING) {
            intArrayOf(Prefs.MOBILE_TYPE_OFF, Prefs.MOBILE_TYPE_IN_RING, Prefs.MOBILE_TYPE_OUT_RING)
        } else {
            intArrayOf(Prefs.MOBILE_TYPE_OFF, Prefs.MOBILE_TYPE_IN_RING)
        }
        // The signal meter's switch decides whether the bottom band and the rectangle's
        // columns hold anything, so it is varied there and held everywhere else.
        val mobiles = if (zone == Zone.BOTTOM) bools else booleanArrayOf(current.showMobile)
        for (wifi in bools) {
            for (value in bools) {
                for (centred in centres) {
                    for (bolt in bools) {
                        for (typeMode in typeModes) {
                            for (mobile in mobiles) {
                                for (sim1 in bools) {
                                    for (sim2 in bools) {
                                        out.add(current.copy().also {
                                            it.showWifi = wifi
                                            it.showValue = value
                                            it.valueCentred = centred
                                            it.showBolt = bolt
                                            it.mobileTypeMode = typeMode
                                            it.showMobile = mobile
                                            it.showSim1 = sim1
                                            it.showSim2 = sim2
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * The pieces a zone could hold, whether or not they are there now.
     *
     * Derived by asking [assign] whether each piece is reachable, so the list the page
     * offers and the solver that satisfies it cannot disagree.
     */
    @JvmStatic
    fun choices(zone: Zone, rect: Boolean, current: ModuleSettings?, sims: Int): List<Part> {
        if (current == null) return emptyList()
        return Part.entries.filter { assign(zone, rect, it, current, sims) != null }
    }

}
