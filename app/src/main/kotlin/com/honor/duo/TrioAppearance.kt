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

package com.honor.duo

import kotlin.math.roundToInt

/**
 * Every decision about *what* the trio glyph puts on screen, resolved once from a
 * single settings snapshot.
 *
 * This is the one place that rule lives: the renderer asks it what to draw, the
 * hook side asks it what to suppress, and the settings screen asks it which rows
 * mean anything - so a row cannot be greyed out for a reason the drawing code does
 * not share.
 *
 * Deliberately free of Android types and of static state, so the hooked SystemUI
 * process and the settings app can both build it, cheaply enough to build per
 * frame. Immutable.
 */
class TrioAppearance(c: TrioSettings) {

    /** [Prefs.STYLE_RING], [Prefs.STYLE_RECT] or [Prefs.STYLE_CAPSULE]. */
    @JvmField val style: Int = c.trioStyle

    /** True when [style] is the rectangular arrangement. */
    @JvmField val rect: Boolean = c.trioStyle == Prefs.STYLE_RECT

    /** True when [style] is the capsule arrangement. */
    @JvmField val capsule: Boolean = c.trioStyle == Prefs.STYLE_CAPSULE

    /** True when [style] is the ring; stated explicitly rather than as `!rect`. */
    @JvmField val ring: Boolean = c.trioStyle == Prefs.STYLE_RING

    /** True when the arrangement places pieces in bands - the ring and the capsule. */
    @JvmField val slotted: Boolean = !rect

    /** The master switch. False hands every slot back to the system. */
    @JvmField val glyph: Boolean = c.enabled

    /** The Wi-Fi arcs are wanted; ink also needs a signal - see [wifiInk]. */
    @JvmField val wifi: Boolean = c.showWifi

    /** The mobile level dots are drawn. */
    @JvmField val mobile: Boolean = c.showMobile
    @JvmField val showMobileActivity: Boolean = c.showMobileActivity

    /**
     * The type steps aside while Wi-Fi is up; see [Prefs.KEY_TYPE_HIDE_ON_WIFI].
     *
     * The FLAG only, not the decision: whether Wi-Fi is up this instant is runtime
     * state, and this class is a snapshot of the settings that the preview and the
     * settings process also read. [TrioRenderer], which already reads the live levels,
     * applies the flag to the frame.
     */
    @JvmField val typeHideOnWifi: Boolean = c.typeHideOnWifi

    /** While charging the ring's centre shows the percentage; see [Prefs.KEY_CHARGING_SHOWS_VALUE]. */
    @JvmField val chargingShowsValue: Boolean = c.chargingShowsValue

    /** The temperature rides with the charger; see [Prefs.KEY_PERF_TEMP_CHARGING_ONLY]. */
    @JvmField val perfTempChargingOnly: Boolean = c.perfTempChargingOnly

    /** Wi-Fi arcs' size, in percent; see [Prefs.KEY_WIFI_SCALE]. */
    @JvmField val wifiScale: Int = c.wifiScale

    /** The capsule's extra straight run, in design units; see [Prefs.KEY_CAPSULE_LENGTH]. */
    @JvmField val capsuleLength: Int = c.capsuleLength

    /** Interior-cell nudges, in design units; see [Prefs.KEY_CAPSULE_LEFT_DX]. */
    @JvmField val capsuleLeftDx: Float = c.capsuleLeftDx.toFloat()
    @JvmField val capsuleLeftDy: Float = c.capsuleLeftDy.toFloat()
    @JvmField val capsuleRightDx: Float = c.capsuleRightDx.toFloat()
    @JvmField val capsuleRightDy: Float = c.capsuleRightDy.toFloat()

    /** The same setting as a multiplier, clamped so a stale store cannot distort it. */
    fun wifiScaleFactor(): Float =
        Prefs.clamp(wifiScale, Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE) / 100f

    /** The signal is drawn beside the glyph, i.e. handed back to the system. */
    @JvmField val signalOutOfRing: Boolean = c.signalMode == Prefs.SIGNAL_OUT_RING

    /** While out of ring, the module draws its own stacked reading. */
    @JvmField val stackedSignal: Boolean = c.stackedSignal

    /** The user wants a second row of level dots; see [dualSimRows]. */
    @JvmField val dualSim: Boolean = c.dualSim

    /** SIM 1's own row of four dots is wanted, as a component of its own. */
    @JvmField val showSim1: Boolean = c.showSim1

    /** SIM 2's own row of four dots is wanted; see [showSim1]. */
    @JvmField val showSim2: Boolean = c.showSim2

    /** The user wants only the current data SIM in the out-of-ring reading. */
    @JvmField val dataSimOnly: Boolean = c.dataSimOnly

    /** Gap between the capsule's cells, in percent of the reference gap. */
    @JvmField val capGap: Int = c.capGap

    /** The in-glyph battery number carries a percent sign. */
    @JvmField val showPercent: Boolean = c.showPercent

    /** The battery percentage is drawn. */
    @JvmField val value: Boolean = c.showValue

    /** The charging bolt toggle; the free ring gives it a slot of its own. */
    @JvmField val boltWanted: Boolean = c.showBolt

    /** [Prefs.MOBILE_TYPE_OFF], `IN_RING` or `OUT_RING`. */
    @JvmField val typeMode: Int = c.mobileTypeMode

    /**
     * Where the type is drawn, decided by one fact: whether it is placed in a band.
     *
     * The stored mode is only the on/off switch now. It used to also carry "in ring" and
     * "outside", and the two could disagree with the layout - a type removed from the
     * ring still said "in ring" and disappeared, and one placed by the zone picker while
     * the mode said "outside" was drawn outside anyway. Placement is what the renderer
     * acts on, so placement is what decides.
     */
    @JvmField val typeInRing: Boolean = c.ringZones.any { it.contains(Prefs.PART_TYPE) }

    /**
     * The network type is drawn as a status-bar view outside the glyph.
     *
     * "Inside" with nothing in any band counts as outside: the user removed it from the
     * ring, and the type would otherwise vanish instead of moving to the place the layout
     * has for it.
     */
    @JvmField val typeOutOfRing: Boolean = c.ringZones.none { it.contains(Prefs.PART_TYPE) }

    /** The charging power is drawn in the out-of-ring strip; see [perfAnywhere]. */
    @JvmField val perfPower: Boolean = c.perfPower
    @JvmField val perfRunning: Boolean = c.perfRunning
    @JvmField val clockSeconds: Boolean = c.clockSeconds

    /** The date is drawn on its own line above the time. */
    @JvmField val clockDate: Boolean = c.clockDate

    /** The weekday shares that line with the date. */
    @JvmField val clockWeekday: Boolean = c.clockWeekday

    @JvmField val clockTopOffsetX: Int = c.clockTopOffsetX
    @JvmField val clockBottomOffsetX: Int = c.clockBottomOffsetX
    @JvmField val clockLunarGap: Int = c.clockLunarGap
    @JvmField val clockRowGap: Int = c.clockRowGap
    @JvmField val clockLunar: Boolean = c.clockLunar
    @JvmField val clockDateFormat: Int = c.clockDateFormat
    @JvmField val clockWeekdayFormat: Int = c.clockWeekdayFormat
    @JvmField val clockWeekdayColorOnDark: Int = c.clockWeekdayColorOnDark
    @JvmField val clockWeekdayColorOnLight: Int = c.clockWeekdayColorOnLight
    @JvmField val clockDateColorOnDark: Int = c.clockDateColorOnDark
    @JvmField val clockDateColorOnLight: Int = c.clockDateColorOnLight
    @JvmField val clockLunarColorOnDark: Int = c.clockLunarColorOnDark
    @JvmField val clockLunarColorOnLight: Int = c.clockLunarColorOnLight

    /** The battery temperature is drawn in the same strip. */
    @JvmField val perfTemp: Boolean = c.perfTemp

    /** The screen's frame rate is drawn - in the glyph when placed, on its own otherwise. */
    @JvmField val perfFps: Boolean = c.perfFps

    /** Overall size of the two readouts, in percent of the adaptive fit. */
    @JvmField val perfScale: Int = c.perfScale

    /** How heavy the two readouts are drawn, on the platform's 100..900 scale. */
    @JvmField val perfWeight: Int = c.perfWeight

    /** Extra spacing between the two stacked readout lines, in dp. */
    @JvmField val perfLineGap: Int = c.perfLineGap

    /** Overall size of the glyph, in percent of the fit the host box implies. */
    @JvmField val glyphScale: Int = c.glyphScale

    @JvmField val roleColors: Boolean = c.roleColors
    @JvmField val criticalOnDark: Int = c.criticalOnDark
    @JvmField val criticalOnLight: Int = c.criticalOnLight
    @JvmField val chargingOnDark: Int = c.chargingOnDark
    @JvmField val chargingOnLight: Int = c.chargingOnLight
    @JvmField val lowOnDark: Int = c.lowOnDark
    @JvmField val lowOnLight: Int = c.lowOnLight
    @JvmField val lowThreshold: Int = c.lowThreshold

    @JvmField val ringStroke: Int = c.ringStroke
    @JvmField val arcStroke: Int = c.arcStroke
    @JvmField val trackAlpha: Int = c.trackAlpha
    @JvmField val valueSize: Int = c.valueSize
    @JvmField val valueWeight: Int = c.valueWeight

    /** Independent size and weight of the capsule or free-ring clock. */
    @JvmField val clockSize: Int = c.clockSize
    @JvmField val clockWeight: Int = c.clockWeight
    @JvmField val typeSize: Int = c.typeSize
    @JvmField val outTypeSize: Int = c.outTypeSize
    @JvmField val outSignalSize: Int = c.outSignalSize
    @JvmField val outDotSize: Int = c.outDotSize
    /**
     * Which look the stacked reading uses - see [Prefs.KEY_STACK_STYLE].
     *
     * With only the data card shown the two styles draw the same single row, so the
     * style is not part of what is drawn: reading it as the dot style keeps every
     * consumer - the drawing, the sheets, the caption rules - on the one style that
     * describes what is actually on screen. The stored value is left alone for the
     * moment the second card comes back.
     */
    @JvmField val stackStyle: Int =
        if (c.dataSimOnly) Prefs.STACK_STYLE_DOTS else c.stackStyle

    /**
     * Where the caption sits: over bar 1 when beside, or as the standalone label.
     *
     * A choice in both styles: the dot style draws the caption over bar 1 or leaves it
     * to the standalone label, and the two-row style does the same - its caption sits
     * over the first bar of each row, so it needs no label column of its own.
     */
    @JvmField val stackLabel: Int = c.stackLabel

    /** The gap the out-of-ring label keeps on each side, in dp. */
    @JvmField val outTypeMarginLeft: Int = c.outTypeMarginLeft
    @JvmField val outTypeMarginRight: Int = c.outTypeMarginRight

    /** Gap between the out-of-ring reading and the glyph, in dp. */
    @JvmField val outSignalMargin: Int = c.outSignalMargin

    /** Scale of a trailing "A" in the network type, as a percentage. */
    @JvmField val typeSuffixScale: Int = c.typeSuffixScale

    /** Size of the in-ring charging bolt, as a percentage of its reference size. */
    @JvmField val boltScale: Int = c.boltScale

    /** How the charging bolt moves - see [Prefs.KEY_BOLT_ANIM]. */
    @JvmField val boltAnim: Int = c.boltAnim

    /** Speed of that motion, in percent of the authored loop. */
    @JvmField val boltSpeed: Int = c.boltSpeed

    /** Size of the ring's level-dot rows, as a percentage of the bead the stroke implies. */
    @JvmField val dotScale: Int = c.dotScale

    /** The out-of-ring order, nearest the battery first - see [Prefs.KEY_OUT_ORDER]. */
    @JvmField val outOrder: IntArray = c.outOrder.clone()

    @JvmField val typeWeight: Int = c.typeWeight

    /**
     * The three ring slots, each an ordered list of candidate pieces.
     *
     * There is no second, switch-derived layout: every piece the ring draws is
     * whatever these lists resolve to.
     */
    @JvmField val ringZones: Array<IntArray> = TrioSettings.copyZones(c.ringZones)

    /**
     * Whether `part` appears anywhere in the layout, whatever its priority.
     *
     * This is the question the native-icon handoff needs: if a piece is placed at
     * all, the module owns that reading and the system's own icon must stay hidden -
     * even on a frame where a higher-priority candidate is the one drawing that zone.
     * Asking "is it drawn this frame" instead would make the native icon flicker in
     * and out as the fallback chain moved.
     */
    fun hasRingPart(part: Int): Boolean {
        for (z in 0 until Prefs.ZONE_COUNT) {
            val ids = ringZones[z]
            for (id in ids) if (id == part) return true
        }
        return false
    }

    /**
     * Whether the module draws the arcs itself, which is also what decides whether the
     * system's own Wi-Fi icon is hidden.
     *
     * Only the ring is slot-based; the capsule and the rectangle arbitrate their
     * single reading from the switches, so there the switch alone answers.
     */
    fun ownsWifi(): Boolean = wifi && (!ring || hasRingPart(Prefs.PART_WIFI))


    fun ownsValue(): Boolean = value && (!ring || hasRingPart(Prefs.PART_VALUE))

    fun ownsType(): Boolean =
        typeOutOfRing || (typeInRing && (!ring || hasRingPart(Prefs.PART_TYPE)))

    /** Whether the Wi-Fi arcs carry ink at this signal level. */
    fun wifiInk(wifiLevel: Int): Boolean = ownsWifi() && wifiLevel >= 1

    /**
     * Whether a row of level dots is drawn per SIM - SIM 1 on top, SIM 2 below - rather
     * than the usual single row for the current data SIM.
     *
     * Both of the other readings own the glyph's slots: the arcs take the notch the
     * bottom row needs, and charging already reports itself through the colour and the
     * bolt. Also needs two SIMs to read: with one - or with the levels not yet sampled -
     * the single row falls back to the current data SIM.
     */
    fun dualSimRows(wifiInk: Boolean, charging: Boolean, sims: Int): Boolean =
        dualSim && signalDots() && sims >= 2 && !wifiInk && !charging

    /**
     * Whether the glyph itself draws the mobile level dots.
     *
     * Out of the ring the answer is no: the module hands the reading back, and
     * leaving the dots behind would show the same signal twice.
     */
    fun signalDots(): Boolean {
        if (signalOutOfRing) return false
        // The ring is slot-based, so a card has to be placed there to be drawn.
        return mobile && (!ring || hasRingPart(Prefs.PART_MOBILE))
    }

    /** Whether the glyph draws SIM 1's own row this frame. */
    fun drawsSim1(): Boolean =
        showSim1 && mobile && slotted && !signalOutOfRing && hasRingPart(Prefs.PART_SIM_1)

    /** Whether the glyph draws SIM 2's own row this frame; see [drawsSim1]. */
    fun drawsSim2(): Boolean =
        showSim2 && mobile && slotted && !signalOutOfRing && hasRingPart(Prefs.PART_SIM_2)

    /** Whether either per-card row is drawn, which is what the hooks must ask. */
    fun drawsAnySimRow(): Boolean = drawsSim1() || drawsSim2()

    /**
     * Whether the module is drawing the signal outside the glyph as its own stacked
     * reading, rather than leaving the native icon alone.
     */
    fun stackedOut(): Boolean = signalOutOfRing && stackedSignal

    /**
     * Whether the stacked reading draws the per-card network type.
     *
     * Not a setting of its own: it is the existing "outside" position of the network
     * type. Choosing the type belongs beside the signal, and when that signal
     * is this module's stacked reading, beside it means inside it.
     */
    fun stackTypeShown(): Boolean = stackedOut() && typeOutOfRing &&
        stackLabel == Prefs.STACK_LABEL_BESIDE

    /**
     * Whether the out-of-ring reading draws the types at all - in either label mode.
     *
     * BESIDE puts one above bar 1; ALONE puts one beside each card's row. The reading
     * needs both to size its own box, and the standalone label must not be mounted on
     * top of either.
     */
    fun labelInReading(): Boolean = stackedOut() && typeOutOfRing

    /**
     * Whether the system's own mobile-signal group has to be hidden, which is exactly
     * while this module draws that signal itself.
     *
     * The trap it encodes: hiding the group while drawing nothing in its place would
     * delete a signal indicator from the status bar. That is why this asks "am I drawing
     * the reading" rather than "did the user ask for a reading".
     */
    fun foldsMobile(): Boolean = signalDots() || drawsAnySimRow() || stackedOut()

    /** Whether the network type is drawn at all, in either position. */
    fun typeAnywhere(): Boolean = typeInRing || typeOutOfRing

    /** The in-ring bolt's size multiplier, 1.0 for the reference size. */
    fun boltScaleFactor(): Float =
        Prefs.clamp(boltScale, Prefs.MIN_BOLT_SCALE, Prefs.MAX_BOLT_SCALE) / 100f

    /** Whether the bolt moves at all. */
    fun boltAnimates(): Boolean = boltAnim != Prefs.BOLT_ANIM_OFF

    /**
     * The loop's speed multiplier, 1.0 at the authored pace.
     *
     * A multiplier rather than a duration: every piece derives its motion from
     * [TrioRenderer.animPhase], and a phase of 0..1 per loop is what they all read.
     */
    fun boltSpeedFactor(): Float =
        Prefs.clamp(boltSpeed, Prefs.MIN_BOLT_SPEED, Prefs.MAX_BOLT_SPEED) / 100f

    /**
     * Whether the outline's own motion is running this frame.
     *
     * Deliberately independent of whether the bolt has ink: the ring and the capsule
     * animate for the battery, not for the bolt, and gating on the bolt meant a layout
     * that queued another piece ahead of it - or a preview arranged that way - showed a
     * completely still outline. A full battery still stops everything, because there is
     * nothing left to charge.
     */
    fun animatesOutline(charging: Boolean, level: Int): Boolean =
        charging && level < 100 && boltAnimates()

    /** The ring's level-dot size multiplier, 1.0 for the bead the stroke implies. */
    fun dotScaleFactor(): Float =
        Prefs.clamp(dotScale, Prefs.MIN_DOT_SCALE, Prefs.MAX_DOT_SCALE) / 100f

    /**
     * Whether the performance strip is drawn at all.
     *
     * The two readouts share one view and one reserved strip, so this is the question
     * every caller that mounts or measures that view has to ask - never [perfPower] or
     * [perfTemp] alone.
     */
    fun perfAnywhere(): Boolean = perfPower || perfTemp

    /**
     * Whether any performance reading is switched on, the frame rate included.
     *
     * The size and weight rows ask this rather than [perfAnywhere]: those rows size
     * every reading the module draws, so gating them on the watt and the temperature
     * would grey out a frame rate that is the only one switched on.
     */
    fun perfPairShown(): Boolean = perfAnywhere() || perfFps

    /** Whether `id` is placed in any of the glyph's bands. */
    fun placed(id: Int): Boolean = ringZones.any { it.contains(id) }

    /**
     * Whether the arrangement in force actually draws `id` this frame.
     *
     * A different question from [placed]: a band draws its first piece (the capsule
     * body two), and the rectangle has no bands at all. A piece sitting behind another
     * one in a list, or in a list the rectangle never reads, is not on screen - and the
     * out-of-ring readings have to know that, or they would suppress a reading nothing
     * is drawing.
     */
    fun drawsPart(
        id: Int,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ): Boolean {
        if (rect) {
            return false
        }
        if (capsule) {
            val cap = capsule(level, charging, wifiLevel, mobileType, sims)
            if (cap.parts.any { it == id }) return true
            return cap.second[TrioZones.Zone.CENTRE.ordinal] == id
        }
        return ring(level, charging, wifiLevel, mobileType, sims).parts.any { it == id }
    }

    /** The fit multiplier the glyph is drawn at, from [glyphScale]. */
    fun glyphFit(): Float =
        Prefs.clamp(glyphScale, Prefs.MIN_GLYPH_SCALE, Prefs.MAX_GLYPH_SCALE) / 100f

    /** The same, for the reading: [perfScale] as a multiplier. */
    fun perfFit(): Float =
        Prefs.clamp(perfScale, Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE) / 100f

    /** The weight the two readouts are drawn at, from [perfWeight]. */
    fun perfTypeWeight(): Int =
        Prefs.clamp(perfWeight, Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT)

    /**
     * The size a readout piece is drawn at inside the glyph, in design units.
     *
     * The same "读数整体尺寸" multiplier the out-of-ring strip uses, applied to the size
     * the other in-glyph readings ship at, so one slider moves every reading the module
     * draws.
     */
    fun perfReadingSize(): Int = (PERF_READING_UNITS * perfFit()).roundToInt()

    /** The ring arrangement's slot assignment for one frame. */
    fun ring(level: Int, charging: Boolean, wifiLevel: Int, mobileType: String?, sims: Int): Ring =
        Ring(this, level, charging, wifiLevel, mobileType, sims)

    /** The rectangular arrangement's slot assignment for one frame. */
    fun rect(level: Int, charging: Boolean, wifiLevel: Int, mobileType: String?, sims: Int): Rect =
        Rect(this, level, charging, wifiLevel, mobileType, sims)

    /** The capsule arrangement's slot assignment for one frame. */
    fun capsule(
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ): Capsule = Capsule(this, level, charging, wifiLevel, mobileType, sims)

    /**
     * Whether the bar's clock shows anything the ROM's own clock cannot.
     *
     * Seconds and the date line are both written by the module; with neither on there
     * is nothing to add and the native clock is left alone.
     */
    fun drawsBarClock(): Boolean = clockSeconds || clockDate || clockWeekday ||
        clockTopOffsetX != Prefs.DEF_CLOCK_TOP_OFFSET_X ||
        clockBottomOffsetX != Prefs.DEF_CLOCK_BOTTOM_OFFSET_X ||
        clockLunarGap != Prefs.DEF_CLOCK_LUNAR_GAP ||
        clockRowGap != Prefs.DEF_CLOCK_ROW_GAP ||
        clockSize != Prefs.DEF_CLOCK_SIZE || clockWeight != Prefs.DEF_CLOCK_WEIGHT ||
        clockWeekdayColorOnDark != 0 || clockWeekdayColorOnLight != 0 ||
        clockDateColorOnDark != 0 || clockDateColorOnLight != 0 ||
        clockLunarColorOnDark != 0 || clockLunarColorOnLight != 0

    /**
     * Whether the bar's own clock has to step aside for this appearance.
     *
     * Only when the time would actually be drawn: it has to be placed, and placed
     * where the arrangement still reaches. A band draws its first piece (the body two,
     * in the capsule), so a clock third in a queue never appears - hiding the bar's
     * copy for it left a hole with nothing standing in for it.
     *
     * The check counts the pieces queued ahead of the clock rather than the ones with
     * ink this frame: it has to answer without a battery or signal reading, and being
     * wrong the safe way (bar clock kept) beats a blank.
     */
    fun hidesStatusClock(): Boolean {
        if (!glyph) return false
        val drawn = if (capsule) 2 else 1
        for (z in 0 until Prefs.ZONE_COUNT) {
            var ahead = 0
            for (id in ringZones[z]) {
                if (id == Prefs.PART_NONE) continue
                if (id == Prefs.PART_CLOCK) return ahead < drawn
                ahead++
            }
        }
        return false
    }

    /**
     * Right edge for the capsule.
     *
     * Two pieces placed in the body are drawn side by side and need the pair width;
     * anything less keeps the single-reading width. Read from the stored layout rather
     * than from what has ink this frame, so the outline does not resize when the second
     * piece has nothing to say for a moment.
     */
    fun capsuleRight(): Float {
        var placed = 0
        for (id in ringZones[TrioZones.Zone.CENTRE.ordinal]) {
            if (id != Prefs.PART_NONE) placed++
        }
        val extra = TrioGeometry.capExtra(this)
        // The spacing setting is not a width knob any more: the cells hang off their own
        // edges, so padding them cannot require a wider shape. Reading it here made the
        // outline move whenever the slider did, while the ink inside stayed put.
        return (if (placed < 2) TrioGeometry.CAP_RIGHT else TrioGeometry.CAP_RIGHT_PAIR) + extra
    }

    /** Where each piece goes in the ring arrangement. */
    class Ring internal constructor(
        a: TrioAppearance,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ) {
        /** Actual occupants this frame. */
        @JvmField val parts: IntArray = IntArray(Prefs.ZONE_COUNT)

        /** The arcs leave ink this frame. */
        @JvmField val wifi: Boolean
        @JvmField val drawWifi: Boolean

        /** The arcs are shrunk into the notch to make room for the centre. */
        @JvmField val wifiInGap: Boolean

        /** The bolt takes the notch. */
        @JvmField val bolt: Boolean

        /** The percentage is drawn at all. */
        @JvmField val drawValue: Boolean
        @JvmField val valueInCentre: Boolean
        @JvmField val valueInGap: Boolean

        /** The in-glyph network type is drawn, and where. */
        @JvmField val typeInCentre: Boolean
        @JvmField val typeInGap: Boolean

        /** Something occupies the notch, so the ring has to open for it. */
        @JvmField val gapUsed: Boolean

        /** The level dots are drawn as two rows, one per SIM. */
        @JvmField val dual: Boolean

        init {
            // The zone draws the FIRST candidate that has something to show this frame.
            for (z in 0 until Prefs.ZONE_COUNT) {
                parts[z] = firstDrawableInk(a, a.ringZones[z], z, level, charging, wifiLevel, mobileType)
            }
            // A charger puts the number in the middle, whatever the layout arranged there.
            //
            // An override of what this FRAME shows, not an edit to what is stored: the
            // layout is the user's arrangement, and rewriting it on plug-in would then have
            // to be undone on unplug - losing whatever had been there if the two ever
            // disagreed. Resolved here, next to the other per-frame answers, so the drawing
            // and the settings screen's preview see the same centre.
            //
            // Only the centre: the openings are what the ring was opened for, and a mouth
            // holding something else would change the outline's shape as a side effect of a
            // preference about a number.
            if (charging && a.chargingShowsValue && level >= 0 &&
                parts[TrioZones.Zone.CENTRE.ordinal] != Prefs.PART_VALUE
            ) {
                parts[TrioZones.Zone.CENTRE.ordinal] = Prefs.PART_VALUE
            }
            wifi = a.wifiInk(wifiLevel)
            drawWifi = wifi
            bolt = charging && a.drawsBolt()
            drawValue = a.ownsValue() && level >= 0
            valueInCentre = parts[1] == Prefs.PART_VALUE
            valueInGap = parts[0] == Prefs.PART_VALUE
            wifiInGap = parts[0] == Prefs.PART_WIFI
            typeInCentre = parts[1] == Prefs.PART_TYPE
            typeInGap = parts[0] == Prefs.PART_TYPE
            gapUsed = parts[0] != Prefs.PART_NONE
            // The dual switch does not change how a zone is drawn: a zone draws one piece, so a second row has nowhere to go.
            dual = false
        }

        fun gapStart(): Float = when {
            !gapUsed -> TrioGeometry.GAP_NONE
            dual -> TrioGeometry.GAP_START_DUAL
            bolt -> TrioGeometry.GAP_START_CHARGE
            else -> TrioGeometry.GAP_START_IDLE
        }

        fun gapEnd(): Float = when {
            !gapUsed -> TrioGeometry.GAP_NONE
            dual -> TrioGeometry.GAP_END_DUAL
            bolt -> TrioGeometry.GAP_END_CHARGE
            else -> TrioGeometry.GAP_END_IDLE
        }

        fun bottomOpen(): Boolean = parts[2] != Prefs.PART_NONE

        fun sweep(): Float = if (bottomOpen()) TrioGeometry.B_SWEEP else 360f

        fun arcStart(): Float = 270f - sweep() / 2f
    }

    /** The rectangular arrangement's slot assignment for one frame. */
    class Rect internal constructor(
        a: TrioAppearance,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ) {
        @JvmField val dots: Boolean = a.signalDots()
        @JvmField val bolt: Boolean = charging && a.drawsBolt()
        @JvmField val wifi: Boolean = !bolt && a.wifiInk(wifiLevel)
        @JvmField val type: Boolean = !bolt && !wifi && a.typeInRing &&
            !mobileType.isNullOrEmpty()
        @JvmField val value: Boolean = a.value && level >= 0

        // Same plug-not-bolt rule as the ring: a charger on screen means one reading, and
        // the bar's colour already carries it.
        @JvmField val dual: Boolean = a.dualSimRows(wifi, charging, sims)
    }

    /**
     * The capsule arrangement's slot assignment for one frame.
     *
     * Every band is a real cell: the two openings hold either a reading or a dot row,
     * and only the interior is a reading's alone. With nothing placed anywhere, the
     * shipped arbitration runs instead, so an upgrading install looks unchanged.
     */
    class Capsule internal constructor(
        a: TrioAppearance,
        level: Int,
        charging: Boolean,
        wifiLevel: Int,
        mobileType: String?,
        sims: Int,
    ) {
        @JvmField val parts: IntArray = IntArray(Prefs.ZONE_COUNT)
        /** The next piece that has ink in each band; only the body draws two. */
        @JvmField val second: IntArray = IntArray(Prefs.ZONE_COUNT)
        @JvmField val dotBands: BooleanArray = BooleanArray(Prefs.ZONE_COUNT)

        @JvmField val bolt: Boolean
        @JvmField val wifi: Boolean
        @JvmField val value: Boolean
        @JvmField val type: Boolean
        @JvmField val dots: Boolean
        @JvmField val dual: Boolean

        init {
            val zones = TrioZones.Zone.entries
            for (band in zones) {
                parts[band.ordinal] = firstInk(
                    a, a.ringZones[band.ordinal], level, charging, wifiLevel, mobileType,
                )
            }
            for (band in zones) dotBands[band.ordinal] = isDotRow(parts[band.ordinal])

            val anyPlaced = anyPlaced(parts) ||
                dotBands[TrioZones.Zone.TOP.ordinal] ||
                dotBands[TrioZones.Zone.BOTTOM.ordinal]
            if (!anyPlaced) {
                // Nothing has ever been placed: behave exactly as before these cells existed.
                val boltWanted = charging && a.drawsBolt()
                val wifiWanted = !boltWanted && a.wifiInk(wifiLevel)
                val valueWanted = !boltWanted && !wifiWanted && a.value && level >= 0
                val typeWanted = !boltWanted && !wifiWanted && !valueWanted &&
                    a.typeInRing && !mobileType.isNullOrEmpty()
                parts[TrioZones.Zone.CENTRE.ordinal] = when {
                    boltWanted -> Prefs.PART_BOLT
                    wifiWanted -> Prefs.PART_WIFI
                    valueWanted -> Prefs.PART_VALUE
                    typeWanted -> Prefs.PART_TYPE
                    else -> Prefs.PART_NONE
                }
                // And the shipped look's dot rows, which this arrangement always drew.
                val legacyDots = a.signalDots()
                dotBands[TrioZones.Zone.TOP.ordinal] = legacyDots
                dotBands[TrioZones.Zone.BOTTOM.ordinal] = legacyDots
            }
            // A band that holds a dot row draws the row, not a reading: the row IS that
            // band's piece. Clearing `parts` keeps the two answers from both claiming it.
            for (band in zones) {
                if (dotBands[band.ordinal]) parts[band.ordinal] = Prefs.PART_NONE
            }
            second[TrioZones.Zone.CENTRE.ordinal] = secondInk(
                a, a.ringZones[TrioZones.Zone.CENTRE.ordinal],
                level, charging, wifiLevel, mobileType,
            )
            dots = dotBands[TrioZones.Zone.TOP.ordinal] || dotBands[TrioZones.Zone.BOTTOM.ordinal]
            dual = a.dualSimRows(false, charging, sims)
            bolt = parts.contains(Prefs.PART_BOLT)
            wifi = parts.contains(Prefs.PART_WIFI)
            value = parts.contains(Prefs.PART_VALUE)
            type = parts.contains(Prefs.PART_TYPE)
        }

        fun dotsIn(band: TrioZones.Zone?): Boolean =
            band != null && dotBands[band.ordinal]

        private companion object {
            fun isDotRow(id: Int): Boolean =
                id == Prefs.PART_SIM_1 || id == Prefs.PART_SIM_2 || id == Prefs.PART_MOBILE

            fun anyPlaced(ids: IntArray): Boolean = ids.any { it != Prefs.PART_NONE }
        }
    }

    companion object {

        /** The design-unit size the other in-glyph readings ship at; the perf pieces match. */
        private const val PERF_READING_UNITS = 36f

        /** The same appearance for `cfg`, or the shipped defaults when it is null. */
        @JvmStatic
        fun of(cfg: TrioSettings?): TrioAppearance =
            TrioAppearance(cfg ?: TrioSettings.defaults())

        /**
         * Whether `part` has ink to draw on this frame, whatever cell it would go in.
         *
         * Deliberately not "is this the right zone": placement is the caller's
         * question, and this only answers "does this piece want to be drawn at all".
         */
        private fun hasInk(
            a: TrioAppearance,
            part: Int,
            level: Int,
            charging: Boolean,
            wifiLevel: Int,
            mobileType: String?,
        ): Boolean = when (part) {
            Prefs.PART_WIFI -> a.wifi && a.wifiInk(wifiLevel)
            Prefs.PART_VALUE -> a.value && level >= 0
            Prefs.PART_BOLT -> a.boltWanted && charging
            Prefs.PART_TYPE -> a.typeInRing && !mobileType.isNullOrEmpty()
            Prefs.PART_MOBILE -> a.mobile && !a.signalOutOfRing &&
                (!a.ring || a.hasRingPart(Prefs.PART_MOBILE))
            // The per-card rows are placeable in BOTH slot-based arrangements: the ring threads them onto its openings and the capsule lays them across its two.
            Prefs.PART_SIM_1 -> a.mobile && a.showSim1 && !a.signalOutOfRing && !a.rect
            Prefs.PART_SIM_2 -> a.mobile && a.showSim2 && !a.signalOutOfRing && !a.rect
            // The clock always has something to say; whether the arrangement shows it is
            // the renderer's business.
            Prefs.PART_CLOCK -> true
            // The two watt figures are one reading split by state: charging and running.
            Prefs.PART_POWER -> charging && !TrioState.sChargePowerW.isNaN()
            Prefs.PART_RUNNING -> !charging && !TrioState.sChargePowerW.isNaN()
            Prefs.PART_TEMP -> TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN &&
                (!a.perfTempChargingOnly || charging)
            // Its own piece: a frame rate has nothing to do with the charger state, so it
            // is drawn whenever the reading is on.
            Prefs.PART_FPS -> a.perfFps
            else -> false
        }

        private fun firstInk(
            a: TrioAppearance,
            order: IntArray?,
            level: Int,
            charging: Boolean,
            wifiLevel: Int,
            mobileType: String?,
        ): Int {
            if (order == null) return Prefs.PART_NONE
            for (id in order) {
                if (hasInk(a, id, level, charging, wifiLevel, mobileType)) return id
            }
            return Prefs.PART_NONE
        }

        /** The second piece with ink in `order`, which is what the body draws beside the first. */
        private fun secondInk(
            a: TrioAppearance,
            order: IntArray?,
            level: Int,
            charging: Boolean,
            wifiLevel: Int,
            mobileType: String?,
        ): Int {
            if (order == null) return Prefs.PART_NONE
            var ink = 0
            for (id in order) {
                if (id == Prefs.PART_NONE) continue
                if (!hasInk(a, id, level, charging, wifiLevel, mobileType)) continue
                ink++
                if (ink == 2) return id
            }
            return Prefs.PART_NONE
        }

        /**
         * The same, skipping the placements the ring refuses in this band - a signal row in
         * the middle, see [Prefs.zoneAccepts]. Decoding a stored layout repairs that
         * already, but this runs in the hooked process and can be reached from a broadcast
         * that never went through a decode, so the refusal is enforced here too.
         */
        private fun firstDrawableInk(
            a: TrioAppearance,
            order: IntArray?,
            band: Int,
            level: Int,
            charging: Boolean,
            wifiLevel: Int,
            mobileType: String?,
        ): Int {
            if (order == null) return Prefs.PART_NONE
            for (id in order) {
                if (!Prefs.zoneAccepts(band, id)) continue
                if (hasInk(a, id, level, charging, wifiLevel, mobileType)) return id
            }
            return Prefs.PART_NONE
        }
    }

    /**
     * Whether the module draws a charging bolt of its own, which is what decides whether
     * the native bolt has to stay out of the way.
     *
     * Not gated on [glyph], because the preview keeps drawing the chosen arrangement
     * while the master switch is off.
     */
    fun drawsBolt(): Boolean = boltWanted && (if (ring) hasRingPart(Prefs.PART_BOLT) else value)
}
