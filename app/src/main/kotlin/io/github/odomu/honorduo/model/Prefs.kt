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

package io.github.odomu.honorduo.model

import io.github.odomu.honorduo.render.TrioGeometry


/**
 * The single source of truth for every setting shared between the settings app and
 * the hooked SystemUI process.
 *
 * Both sides talk over the Xposed framework's remote preferences: the settings
 * app writes, the hooked process only reads. Because the framework casts stored
 * values to the requested type without conversion, every key is read and written
 * with exactly one type for the whole lifetime of the module. The `DEF_*` defaults
 * live here as well so both sides agree when the framework cannot provide remote
 * preferences (embedded mode, or a first run before the settings screen was ever
 * opened).
 */
object Prefs {

    /**
     * The display density the retired raw-pixel sizes were authored against - the
     * test device's density 3. Only the one-time migration consults this, when it
     * has no real display to ask.
     */
    const val AUTHORED_DENSITY = 3f

    /** Remote-preference group name. Also the settings app's file name. */
    const val NAME = "honorduo_settings"

    /**
     * Explicit broadcast the settings app sends to SystemUI after every write,
     * carrying the whole snapshot as extras.
     *
     * Remote preferences remain the store of record, but the framework's "a value
     * changed" callback has been observed to never reach the hooked process: the
     * daemon database held the new `enabled` value while SystemUI kept drawing from
     * the old one. The broadcast is a second, callback-independent path.
     *
     * Lives here rather than in [TrioConfig] because the settings app must be able
     * to name it, and [TrioConfig] references the Xposed API.
     */
    const val ACTION_RELOAD = "io.github.odomu.honorduo.action.RELOAD"

    /**
     * Package the reload broadcast is addressed to. SystemUI registers the receiver,
     * so an explicit package keeps the intent away from every other process.
     */
    const val SYSTEMUI_PACKAGE = "com.android.systemui"

    // Keys.
    const val KEY_ENABLED = "enabled"
    const val KEY_MERGE_ICONS = "merge_icons"
    /**
     * Overall size of the trio glyph, as a percentage of the size the host box
     * already implies.
     *
     * Shrink-only - the range stops at 100 - because the box the row hands over
     * is the limit: the glyph is fitted inside it, and asking for more cannot make
     * the box grow.
     */
    const val KEY_GLYPH_SCALE = "glyph_scale_pct"

    const val KEY_SHOW_WIFI = "show_wifi"
    const val KEY_SHOW_MOBILE = "show_mobile"
    const val KEY_SHOW_MOBILE_ACTIVITY = "show_mobile_activity"
    const val KEY_SHOW_VALUE = "show_value"
    const val KEY_SHOW_BOLT = "show_bolt"

    /**
     * While charging, the ring's centre shows the percentage whatever the layout put
     * there; unplugging returns to the arrangement.
     *
     * Not an edit to the layout: the layout is the user's arrangement, and a charger
     * that rewrote it would have to be undone on unplug - losing whatever had been
     * there if the two ever disagreed. Only the centre: the openings are what the ring
     * was opened for.
     *
     * One key for every arrangement, not a [STYLED_KEYS] entry.
     */
    const val KEY_CHARGING_SHOWS_VALUE = "charging_shows_value"

    /**
     * The network type steps aside while Wi-Fi is connected.
     *
     * Whether Wi-Fi is up this instant is runtime state, so the flag is stored here but
     * applied by the renderer, which already reads the live levels - folding the
     * decision into [TrioAppearance] would let the preview (a fixed scene) and the bar
     * (live) draw one stored setting two ways.
     */
    const val KEY_TYPE_HIDE_ON_WIFI = "type_hide_on_wifi"

    /**
     * How much longer than the reference the capsule's straight run is drawn, in design
     * units.
     *
     * The capsule is a stadium: two half-circle caps joined by two straight runs. The caps are fixed by the height, but the straight run
     * can be any length without changing the shape - lengthening it is how the capsule gets
     * wider while every proportion stated against the height stays put.
     */
    const val KEY_CAPSULE_LENGTH = "capsule_length"

    /** The four interior-cell nudges, in design units, per cell. */
    const val KEY_CAPSULE_LEFT_DX = "capsule_left_dx"
    const val KEY_CAPSULE_LEFT_DY = "capsule_left_dy"
    const val KEY_CAPSULE_RIGHT_DX = "capsule_right_dx"
    const val KEY_CAPSULE_RIGHT_DY = "capsule_right_dy"

    const val KEY_RING_SPACING_X = "ring_spacing_x"
    const val KEY_RING_SPACING_Y = "ring_spacing_y"

    const val KEY_REPLACE_WIFI_ICON = "replace_wifi_icon"
    const val DEF_REPLACE_WIFI_ICON = false

    const val KEY_REPLACE_STATUS_ICON = "replace_status_icon"
    const val DEF_REPLACE_STATUS_ICON = false

    /**
     * Hides the status bar's own clock, with no replacement.
     *
     * Independent of the glyph on purpose: a user who wants a bare bar should not have
     * to enable icon merging first, which is what the placement rule required before.
     */
    const val KEY_HIDE_STATUS_CLOCK = "hide_status_clock"
    const val DEF_HIDE_STATUS_CLOCK = false

    /** Hides the status bar's alarm icon; the alarm itself still fires. */
    const val KEY_HIDE_ALARM_ICON = "hide_alarm_icon"
    const val DEF_HIDE_ALARM_ICON = false

    /** Hides the status bar's Bluetooth icon; the connection itself is unaffected. */
    const val KEY_HIDE_BLUETOOTH_ICON = "hide_bluetooth_icon"
    const val DEF_HIDE_BLUETOOTH_ICON = false

    /**
     * Size of the Wi-Fi arcs, as a percentage of the size the arrangement fits them to.
     *
     * One key for every arrangement: the arcs are one drawing, so a user who finds them
     * small expects them to grow wherever they appear. Each arrangement's own
     * fit-to-slot ceiling still applies - the multiplier operates inside the room that
     * exists, so a large value crowded against a thick stroke stops growing rather
     * than spilling over the outline.
     */
    const val KEY_WIFI_SCALE = "wifi_scale"

    /** The temperature appears and disappears with the charging power (#45). */
    const val KEY_PERF_TEMP_CHARGING_ONLY = "perf_temp_charging_only"

    /**
     * Where the mobile network type is drawn: [MOBILE_TYPE_OFF],
     * [MOBILE_TYPE_IN_RING] or [MOBILE_TYPE_OUT_RING].
     *
     * An `int`, and every key keeps exactly one type for the module's lifetime.
     */
    const val KEY_MOBILE_TYPE_MODE = "mobile_type_mode"

    /** Legacy boolean, read only while migrating to [KEY_MOBILE_TYPE_MODE]. */
    const val KEY_SHOW_MOBILE_TYPE = "show_mobile_type"

    /**
     * Draws the battery percentage enlarged in the middle of the ring, with the
     * arcs shrunk into the 12 o'clock notch.
     *
     * The stored name is frozen at `"swap_wifi_value"` and must never change: it
     * is a boolean the user may already have set, and rewriting the key would
     * silently reset that choice on every existing install.
     */
    const val KEY_VALUE_CENTRED = "swap_wifi_value"

    /** Which three-in-one layout is drawn: one of the `STYLE_*` constants. */
    const val KEY_TRIO_STYLE = "trio_style"

    /**
     * The ring layout: an ordered list of pieces per zone, as one string.
     *
     * Format is `"top|middle|bottom"` where each section is a comma-separated list
     * of part ids in priority order, and an empty section means "nothing placed
     * here". For example `"3,1|2|7,8"` is top = [percentage, arcs], middle = [mobile
     * meter], bottom = [SIM 1, SIM 2].
     *
     * A string rather than a packed int: order is the whole point now (the first
     * piece that has something to draw wins the zone), a zone may hold any number of
     * candidates, and it is legible in the prefs XML.
     */
    const val KEY_RING_LAYOUT = "ring_layout"

    /**
     * Suffix that makes a component setting belong to the capsule instead of the
     * ring: `show_wifi@capsule`.
     *
     * The ring's own key keeps its historical, unsuffixed name - the arrangement
     * that was there first - so every value an existing install holds stays where it
     * is and needs no migration.
     */
    const val STYLE_SUFFIX = "@capsule"

    /**
     * The keys whose values belong to **one arrangement** rather than to the module.
     *
     * These are the switches that decide what the glyph draws and where. Splitting
     * the storage is what lets each arrangement keep its own arrangement.
     *
     * Deliberately not here: every size, weight, stroke and colour. Those are
     * properties of the component itself - a user who made the digits 40pt wants them
     * 40pt in whichever shape is drawn.
     */
    @JvmField
    val STYLED_KEYS: Set<String> = hashSetOf(
        KEY_SHOW_WIFI, KEY_SHOW_MOBILE, KEY_SHOW_VALUE, KEY_SHOW_BOLT,
        KEY_SHOW_SIM_1, KEY_SHOW_SIM_2, KEY_MOBILE_TYPE_MODE, KEY_RING_LAYOUT,
        KEY_SIGNAL_MODE, KEY_STACKED_SIGNAL, KEY_DUAL_SIM, KEY_VALUE_CENTRED,
    )

    /**
     * The storage key for `base` under `style`.
     *
     * The ring returns `base` unchanged: it is the arrangement that was there
     * before the split. Only the capsule is moved to a suffixed name, and a capsule
     * value that does not exist yet falls back to the ring's, so an upgrade inherits
     * the look the user already had.
     */
    @JvmStatic
    fun styledKey(base: String, style: Int): String {
        if (base !in STYLED_KEYS) return base
        return if (style == STYLE_CAPSULE) base + STYLE_SUFFIX else base
    }

    /** Whether `key` is the capsule's copy of one of the [STYLED_KEYS]. */
    @JvmStatic
    fun isCapsuleKey(key: String?): Boolean = key != null && key.endsWith(STYLE_SUFFIX)

    /** The [STYLED_KEYS] base a storage key names, or the key itself. */
    @JvmStatic
    fun styledBase(key: String): String =
        if (isCapsuleKey(key)) key.substring(0, key.length - STYLE_SUFFIX.length) else key

    /** The retired one-piece-per-zone encodings, read once by [migrateLayout]. */
    const val KEY_RING_SLOTS_V2 = "ring_slots_v2"
    const val KEY_RING_SLOTS = "ring_slots"

    /**
     * A fresh install starts empty: every zone with nothing placed.
     *
     * The user builds the glyph themselves. With priority lists there is no single
     * arrangement to ship, and an empty ring is an honest starting point.
     */
    const val EMPTY_LAYOUT = "||"

    /**
     * Part ids. [PART_NONE] never appears in a stored layout - an empty zone is an
     * empty section - so it only means "this id is not a piece" in decoded lists.
     */
    const val PART_NONE = 0
    const val PART_WIFI = 1
    const val PART_MOBILE = 2
    const val PART_VALUE = 3
    const val PART_BOLT = 4
    const val PART_TYPE = 5
    const val PART_CLOCK = 6

    /** SIM 1's own row of four dots. */
    const val PART_SIM_1 = 7

    /** SIM 2's own row of four dots. */
    const val PART_SIM_2 = 8

    /** The charging wattage, as its own piece: "9.6W" while a charger is attached. */
    const val PART_POWER = 9

    /** The battery temperature, as its own piece: "32°C". */
    const val PART_TEMP = 10

    /** The wattage while running on battery, as its own piece: "9.6W" unplugged. */
    const val PART_RUNNING = 11

    /** The screen's current refresh rate, as its own piece: "120". */
    const val PART_FPS = 12

    /** The highest valid part id. */
    const val PART_MAX = PART_FPS

    /**
     * How many zones the layout has, in the order the layout string stores them:
     * top, middle, bottom. Append-only: reordering would reinterpret every stored
     * layout.
     */
    const val ZONE_COUNT = 3

    /** The middle band, in the order [ZONE_COUNT] stores them. */
    const val ZONE_CENTRE = 1

    /** The bottom band, in the same order. */
    const val ZONE_BOTTOM = 2

    /**
     * Whether the ring's zone `zone` may hold part `id`.
     *
     * One placement is refused, and only one: a signal row may not sit in the
     * ring's middle band. The ring's outline is already cut at 12 and 6 o'clock, and
     * those two openings are where a row of level beads belongs; the middle has no
     * opening, so the same beads there are a short horizontal smudge across a closed
     * ring.
     *
     * Applies to the ring only: the capsule draws its rows across its two openings
     * and the rectangle has no middle band.
     */
    @JvmStatic
    fun zoneAccepts(zone: Int, id: Int): Boolean = !(zone == ZONE_CENTRE && isSignalRow(id))

    /** Whether `id` is a row of level dots: the aggregate meter or either card's row. */
    @JvmStatic
    fun isSignalRow(id: Int): Boolean =
        id == PART_MOBILE || id == PART_SIM_1 || id == PART_SIM_2

    /**
     * Splits a layout into its sections, each an ordered id list.
     *
     * Never returns null and never throws: a corrupt or missing layout decodes to
     * [EMPTY_LAYOUT]. An unreadable section is simply empty, so one damaged zone
     * cannot take the other two with it.
     */
    @JvmStatic
    fun decodeLayout(layout: String?): Array<IntArray> {
        val out = Array(ZONE_COUNT) { IntArray(0) }
        if (layout.isNullOrEmpty()) return out
        val sections = layout.split("|")
        for (z in 0 until ZONE_COUNT) {
            out[z] = decodeSection(sections.getOrNull(z) ?: "")
        }
        return repair(out)
    }

    /**
     * Moves a stored middle signal row to the bottom band, where the drawing wants it.
     *
     * A layout written before [zoneAccepts] existed can name one, and the choice on
     * reading it is between honouring it (draw the smudge the rule prevents), dropping
     * it (silently delete a piece the user placed), or moving it. Moving is the only
     * answer that neither lies about the drawing nor loses the setting.
     */
    private fun repair(zones: Array<IntArray>): Array<IntArray> {
        val kept = ArrayList<Int>()
        var bottom = zones[ZONE_BOTTOM]
        var moved = false
        for (id in zones[ZONE_CENTRE]) {
            if (!isSignalRow(id)) {
                kept.add(id)
                continue
            }
            moved = true
            if (id !in bottom) bottom += id
        }
        if (!moved) return zones
        zones[ZONE_CENTRE] = kept.toIntArray()
        zones[ZONE_BOTTOM] = bottom
        return zones
    }

    /** One section of [KEY_RING_LAYOUT]: a comma-separated id list, deduplicated. */
    private fun decodeSection(section: String): IntArray {
        if (section.isEmpty()) return IntArray(0)
        val ids = ArrayList<Int>()
        for (piece in section.split(",")) {
            val id = piece.trim().toIntOrNull() ?: continue
            if (id !in 1..PART_MAX) continue
            // The aggregate meter is retired as a placeable piece: a stored layout
            // that still names it is migrated to SIM 1's row, which is the reading it
            // used to show for the data SIM. Dropping the id instead would empty a
            // zone an existing install had filled.
            val migrated = if (id == PART_MOBILE) PART_SIM_1 else id
            if (migrated !in ids) ids.add(migrated)
        }
        return ids.toIntArray()
    }

    /**
     * Whether a raw layout string still names the retired aggregate meter.
     *
     * Asked of the string rather than of a decoded layout, because decoding has
     * already migrated the id - so this is the only way to tell an upgrading install
     * from one that simply has SIM 1 placed and its switch off on purpose.
     */
    @JvmStatic
    fun mentionsRetiredMeter(layout: String?): Boolean {
        if (layout.isNullOrEmpty()) return false
        for (piece in layout.split("|", ",")) {
            if (piece.trim().toIntOrNull() == PART_MOBILE) return true
        }
        return false
    }

    /** The inverse of [decodeLayout], with the same tolerance for gaps. */
    @JvmStatic
    fun encodeLayout(zones: Array<IntArray>?): String {
        val sb = StringBuilder()
        for (z in 0 until ZONE_COUNT) {
            if (z > 0) sb.append('|')
            val ids = zones?.getOrNull(z) ?: continue
            var first = true
            for (id in ids) {
                if (id !in 1..PART_MAX) continue
                if (!first) sb.append(',')
                sb.append(id)
                first = false
            }
        }
        return sb.toString()
    }

    /**
     * Converts a layout written by the retired one-piece-per-zone encodings.
     *
     * Four-bit first, then the original three-bit. Each stored id becomes a
     * single-entry priority list, so an existing install keeps every piece where it
     * was and gains the ability to add more behind it.
     */
    @JvmStatic
    fun migrateLayout(v2: String?, legacy: String?): String {
        v2?.trim()?.toIntOrNull()?.let { return fromPacked(it, RING_PART_BITS) }
        legacy?.trim()?.toIntOrNull()?.let { return fromPacked(it, 3) }
        return EMPTY_LAYOUT
    }

    /**
     * Unpacks a zone id from an old encoding into a one-piece priority list.
     *
     * The two widths differ, so the shift is a parameter rather than a constant:
     * reading a three-bit value with four-bit shifts would scatter the user's pieces
     * into the wrong zones.
     */
    private fun fromPacked(packed: Int, bits: Int): String {
        val mask = (1 shl bits) - 1
        val zones = Array(ZONE_COUNT) { z ->
            val id = (packed shr (z * bits)) and mask
            if (id in 1..PART_MAX) intArrayOf(id) else IntArray(0)
        }
        return encodeLayout(zones)
    }

    /** Bits per zone in [KEY_RING_SLOTS_V2]; three zones fit in twelve. */
    const val RING_PART_BITS = 4

    /**
     * Draws the mobile level as two rows of four dots - one SIM each - instead of the
     * single row of the current data SIM.
     *
     * Only honoured while there is no Wi-Fi ink and no charging bolt: with the arcs
     * or the bolt on screen the glyph's top and bottom slots are taken, and the user
     * asked for the two-SIM reading precisely as the alternative to those.
     */
    const val KEY_DUAL_SIM = "dual_sim_signal"

    /**
     * Draws SIM 1's own row of four dots, as a component the user places like any
     * other.
     *
     * Independent of [KEY_DUAL_SIM] and of its SIM 2 twin: the dual switch
     * describes the aggregate meter and its two-row shape, while these two describe
     * the cards themselves - SIM 1 can sit in the notch while SIM 2 sits at the
     * bottom, which one switch could not express.
     */
    const val KEY_SHOW_SIM_1 = "show_sim_1"

    /** Draws SIM 2's own row of four dots; see [KEY_SHOW_SIM_1]. */
    const val KEY_SHOW_SIM_2 = "show_sim_2"

    /**
     * Where the mobile signal reading goes: the glyph's own thirds, or back to the
     * system.
     *
     * An `int` holding one of the `SIGNAL_*` constants. Out of the ring the module
     * draws no level dots and leaves the native icon in place. Which third each card
     * sits in is a separate fact, stored in the ring layout as [PART_SIM_1] /
     * [PART_SIM_2].
     */
    const val KEY_SIGNAL_MODE = "signal_mode"

    /**
     * Draws the out-of-ring signal as four ascending capsule bars for one SIM over
     * four dots for the other, instead of leaving the native icon in place.
     *
     * Only honoured while [KEY_SIGNAL_MODE] is [SIGNAL_OUT_RING]: inside the glyph
     * the ring's own level dots are the signal.
     */
    const val KEY_STACKED_SIGNAL = "stacked_signal"

    /**
     * Draws only the current data SIM's out-of-ring signal row, leaving the other SIM
     * out entirely. Gated on [KEY_STACKED_SIGNAL]: while the native icon is in place
     * the module draws no signal at all.
     */
    const val KEY_DATA_SIM_ONLY = "data_sim_only"

    /**
     * Gap between the capsule's two cells, in percent of the reference gap.
     *
     * Only the capsule has cells to separate: the ring's pieces are placed in bands, and
     * the rectangle derives its own columns.
     */
    const val KEY_CAP_GAP = "cap_gap"
    const val DEF_CAP_GAP = 100
    const val MIN_CAP_GAP = 0
    const val MAX_CAP_GAP = 300

    /**
     * Appends a percent sign to the in-glyph battery number - "85%" rather than "85".
     *
     * Off by default: the ring's number is the only reading there, and the sign costs
     * width that the digits could use.
     */
    const val KEY_SHOW_PERCENT = "show_percent"
    const val DEF_SHOW_PERCENT = false

    /**
     * Draws the battery's charging power - watts, with one decimal below 10W - in the
     * status bar, in the same out-of-ring strip as the network type.
     *
     * Only drawn while an external source is attached: an unplugged phone has no
     * charging power, and the segment is dropped rather than shown as 0W. The number
     * is the power actually going into the battery - its own current times its own
     * voltage - which tapers as the battery fills.
     */
    const val KEY_PERF_POWER = "perf_power"

    /**
     * Keeps the power readout while the phone is not charging, showing what the
     * battery is drawing instead of disappearing.
     *
     * The number is the same measurement - the battery's own current times its own
     * voltage - so an unplugged reading is the running draw, and a plugged one is
     * what is going in. Charging wins when both could be shown.
     */
    const val KEY_PERF_RUNNING = "perf_running"

    /** Draws the battery's temperature in degrees Celsius beside the charging power. */
    const val KEY_PERF_TEMP = "perf_temp"

    /**
     * Draws the screen's current frame rate.
     *
     * The number is the display's refresh rate, which is what a status-bar frame-rate
     * readout reports: it is the rate frames are being presented at, and it moves when
     * the panel drops to 60 or climbs to 120.
     */
    const val KEY_PERF_FPS = "perf_fps"

    /**
     * Overall size of the two performance readouts, as a percentage of the size
     * [KEY_OUT_TYPE_SIZE_DP] and the adaptive fit settle on.
     *
     * A multiplier rather than a second absolute size: the pair is already sized by
     * rule - two lines together come to one icon height - and an absolute setting
     * would throw that rule away instead of tuning it.
     */
    const val KEY_PERF_SCALE = "perf_scale_pct"

    /** How heavy the two performance readouts are drawn, on the 100..900 scale. */
    const val KEY_PERF_WEIGHT = "perf_weight"

    /** Extra spacing between the two stacked readout lines, in dp. */
    const val KEY_PERF_LINE_GAP = "perf_line_gap"

    const val KEY_ROLE_COLORS = "role_colors"
    const val KEY_ROLE_COLORS_TEMP = "role_colors_temp"
    const val KEY_COLOR_CRITICAL_ON_DARK = "color_critical_on_dark"
    const val KEY_COLOR_CRITICAL_ON_LIGHT = "color_critical_on_light"
    const val KEY_COLOR_CHARGING_ON_DARK = "color_charging_on_dark"
    const val KEY_COLOR_CHARGING_ON_LIGHT = "color_charging_on_light"
    const val KEY_COLOR_LOW_ON_DARK = "color_low_on_dark"
    const val KEY_COLOR_LOW_ON_LIGHT = "color_low_on_light"
    const val KEY_TEMP_THRESHOLD = "temp_threshold"
    const val DEF_TEMP_THRESHOLD = 40
    const val MIN_TEMP_THRESHOLD = 30
    const val MAX_TEMP_THRESHOLD = 60

    const val KEY_COLOR_HIGH_TEMP_ON_DARK = "color_high_temp_on_dark"
    const val KEY_COLOR_HIGH_TEMP_ON_LIGHT = "color_high_temp_on_light"
    const val DEF_COLOR_HIGH_TEMP_ON_DARK = 0xFFFF453A.toInt()
    const val DEF_COLOR_HIGH_TEMP_ON_LIGHT = 0xFFFF3B30.toInt()
    const val KEY_LOW_THRESHOLD = "low_threshold"

    const val KEY_RING_STROKE = "ring_stroke"
    const val KEY_ARC_STROKE = "arc_stroke"
    const val KEY_VALUE_SIZE = "value_size"
    const val KEY_VALUE_WEIGHT = "value_weight"

    /**
     * Font size of the time, in the capsule's left-hand slot.
     *
     * Its own key rather than a share of [KEY_VALUE_SIZE]: the two readings sit in
     * slots drawn from the same width but hold different strings - "19:41" against
     * "100" - and sharing a size means a change made for either moves both.
     */
    const val KEY_CLOCK_SIZE = "clock_size"

    /** Font weight of the time, on the same reasoning as [KEY_CLOCK_SIZE]. */
    /**
     * Adds seconds to the time the capsule draws, and ticks the clock every second
     * instead of every minute.
     */
    const val KEY_CLOCK_SECONDS = "clock_seconds"

    const val KEY_CLOCK_WEIGHT = "clock_weight"

    /**
     *
     * A second line rather than part of [KEY_CLOCK_SECONDS]' text: the time is what the
     * slot is sized for, and the date is optional information the user may want without
     * touching the clock's own setting.
     */
    const val KEY_CLOCK_DATE = "clock_date"

    /**
     *
     * Independent of [KEY_CLOCK_DATE] because the two answer different questions: a user
     * can want the weekday without the full date, and the renderer joins whichever of
     * the two is on into one line.
     */
    const val KEY_CLOCK_WEEKDAY = "clock_weekday"

    const val KEY_CLOCK_TOP_OFFSET_X = "clock_top_offset_x"
    const val KEY_CLOCK_BOTTOM_OFFSET_X = "clock_bottom_offset_x"
    const val KEY_CLOCK_LUNAR_GAP = "clock_lunar_gap"
    const val KEY_CLOCK_ROW_GAP = "clock_row_gap"
    const val KEY_CLOCK_LUNAR = "clock_lunar"
    const val KEY_CLOCK_DATE_FORMAT = "clock_date_format"
    const val KEY_CLOCK_WEEKDAY_FORMAT = "clock_weekday_format"
    const val KEY_COLOR_CLOCK_WEEKDAY_ON_DARK = "color_clock_weekday_on_dark"
    const val KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT = "color_clock_weekday_on_light"
    const val KEY_COLOR_CLOCK_DATE_ON_DARK = "color_clock_date_on_dark"
    const val KEY_COLOR_CLOCK_DATE_ON_LIGHT = "color_clock_date_on_light"
    const val KEY_COLOR_CLOCK_LUNAR_ON_DARK = "color_clock_lunar_on_dark"
    const val KEY_COLOR_CLOCK_LUNAR_ON_LIGHT = "color_clock_lunar_on_light"
    const val KEY_CLOCK_WEATHER = "clock_weather"
    const val KEY_CLOCK_WEATHER_STYLE = "clock_weather_style"
    const val KEY_CLOCK_STYLE = "clock_style"
    const val KEY_COLOR_CLOCK_WEATHER_ON_DARK = "color_clock_weather_on_dark"
    const val KEY_COLOR_CLOCK_WEATHER_ON_LIGHT = "color_clock_weather_on_light"
    /** Plain text: the condition and the temperature. */
    const val WEATHER_STYLE_TEXT = 0

    /** The module's vector icon, with the temperature beside it. */
    const val WEATHER_STYLE_ICON = 1

    /** The same vector icon, with the condition and the temperature. */
    const val WEATHER_STYLE_ICON_TEXT = 2

    /**
     * The system's weather glyph, with the temperature beside it.
     *
     * A symbol rather than drawn ink, so it can sit where only text can - the
     * notification center's date line - and so the two places can be made to look
     * the same when the ROM's symbol font is preferred over the module's drawing.
     */
    const val WEATHER_STYLE_SYMBOL = 3

    /** That glyph, with the condition and the temperature. */
    const val WEATHER_STYLE_SYMBOL_TEXT = 4

    /** Style ids are a contiguous 0..[WEATHER_STYLE_SYMBOL_TEXT] range. */
    const val WEATHER_STYLE_COUNT = 5

    const val CLOCK_STYLE_CLASSIC = 0
    const val CLOCK_STYLE_BALANCED = 1
    const val CLOCK_DATE_SLASH_MD = 0
    const val CLOCK_DATE_SLASH_YMD = 1
    const val CLOCK_DATE_ZH_MD = 2
    const val CLOCK_DATE_ZH_YMD = 3
    const val CLOCK_DATE_MONTH_DAY = CLOCK_DATE_SLASH_MD

    const val CLOCK_WEEKDAY_ZH_SHORT = 0
    const val CLOCK_WEEKDAY_ZH_FULL = 1
    const val CLOCK_WEEKDAY_EN_SHORT = 2
    const val CLOCK_WEEKDAY_EN_FULL = 3

    /** Font size of the network type drawn inside the ring. */
    const val KEY_TYPE_SIZE = "type_size"

    /**
     * Font size of the network type outside the ring, in density pixels.
     *
     * Replaces the retired raw-pixel key: the label is a real view in the status
     * bar's pixel space, but the setting is a length the user reasons about, and only
     * a dp means the same slider value lands on the same physical size on every
     * screen. The conversion happens at the point of use.
     */
    const val KEY_OUT_TYPE_SIZE_DP = "out_type_size_dp"

    /**
     * The stacked out-of-ring signal's height, in density pixels.
     *
     * A dp rather than a percentage of anything, because the row this reading
     * stands in does not keep one height: the moment the control centre is pulled
     * down the system lays the same container out through the whole status-bar inset,
     * and a reading sized as a share of its row would grow by half just because a
     * shade was opened.
     */
    const val KEY_OUT_SIGNAL_SIZE = "out_signal_size_dp"

    /** The gap the out-of-ring type keeps on its two sides, in dp. */
    const val KEY_OUT_TYPE_MARGIN_LEFT = "out_type_margin_left_dp"
    const val KEY_OUT_TYPE_MARGIN_RIGHT = "out_type_margin_right_dp"

    /**
     * The gap between the out-of-ring signal reading and the glyph, in dp.
     *
     * A margin rather than a position: the row reserves this length as part of the
     * view's own layout, so widening it moves the reading away from the glyph and
     * pushes the icons over by the same amount. An earlier version of this setting was
     * a free position offset, which moved the view without reserving anything and let
     * the reading paint over its neighbours.
     */
    /**
     * Diameter of the secondary card's dot row, in percent of the primary card's
     * bar width.
     *
     * The row is four dots under the bars; at 100% they match the bar width, which is
     * the reference's own proportion. Above that the reading grows a heavier dot row
     * without touching the bars.
     */
    const val KEY_OUT_DOT_SIZE = "out_dot_size_pct"

    /**
     * How the stacked out-of-ring reading draws the second card.
     *
     * [STACK_STYLE_DOTS] is the reference look: four bars for the primary card and a
     * row of dots beneath for the second. [STACK_STYLE_ROWS] draws the second card as
     * a second row of bars instead, the compact style.
     */
    /**
     * Where the stacked reading puts its network type.
     *
     * [STACK_LABEL_ALONE] is one label on the reading's right, naming the data card
     * only; [STACK_LABEL_BESIDE] puts the labels against the signal itself - above the
     * first bar in the dot style, beside each row in the two-row style.
     */
    const val KEY_STACK_LABEL = "stack_label"

    const val STACK_LABEL_ALONE = 0
    const val STACK_LABEL_BESIDE = 1

    const val KEY_STACK_STYLE = "stack_style"

    const val STACK_STYLE_DOTS = 0
    const val STACK_STYLE_ROWS = 1


    /**
     * How the charging bolt moves, in the ring and the capsule.
     *
     * Off by default: an always-running animation costs a frame every vsync, and the
     * bolt is a state indicator first. Every style loops for as long as the charger is
     * attached and stops the moment it is not.
     */
    const val KEY_BOLT_ANIM = "bolt_anim"

    /** Still, as shipped. */
    const val BOLT_ANIM_OFF = 0

    /** Brightness breathing between dim and full. */
    const val BOLT_ANIM_PULSE = 1

    /** A band of light sweeping along the bolt's own outline. */
    const val BOLT_ANIM_FLOW = 2

    /** Sparks travelling along the bolt, like current through a wire. */
    const val BOLT_ANIM_SPARK = 3

    const val MIN_BOLT_ANIM = BOLT_ANIM_OFF
    const val MAX_BOLT_ANIM = BOLT_ANIM_SPARK

    /**
     * How fast the charging animation runs, as a percentage of its nominal speed.
     *
     * Only meaningful while [KEY_BOLT_ANIM] is not [BOLT_ANIM_OFF]; the settings row is
     * hidden in that case rather than greyed out, because a speed for an animation that
     * does not exist is not a choice.
     */
    const val KEY_BOLT_SPEED = "bolt_speed"


    const val KEY_OUT_SIGNAL_MARGIN = "out_signal_margin_dp"

    /** Hides the power button in the control center's header. */
    const val KEY_SHADE_HIDE_POWER = "shade_hide_cc_power"

    /** Hides the notification-management bell beside the shade's clock. */
    const val KEY_SHADE_HIDE_BELL = "shade_hide_notif_bell"

    /** Appends the lunar date to the shade's date line. */
    const val KEY_SHADE_LUNAR = "shade_lunar"

    /** Appends the current weather to the shade's date line. */
    const val KEY_SHADE_WEATHER = "shade_weather"

    /** How the shade weather reads: text, glyph or both - same ids as the clock's. */
    const val KEY_SHADE_WEATHER_STYLE = "shade_weather_style"

    /**
     * The order of the three out-of-ring pieces, from the battery outwards.
     *
     * Only the order: the three views are plain children of the status bar's own
     * `LinearLayout`, so the row measures and lays them out in this sequence and
     * reserves their width.
     *
     * Stored as a comma-separated list of [OUT_SLOT_SIGNAL] /
     * [OUT_SLOT_READOUTS] / [OUT_SLOT_TYPE] ids. Always exactly three entries once
     * normalised, so a missing or duplicated id is repaired rather than losing a piece.
     */
    const val KEY_OUT_ORDER = "out_ring_order"

    /** Packed ids for [KEY_OUT_ORDER]; deliberately not `PART_*` values. */
    const val OUT_SLOT_SIGNAL = 1
    const val OUT_SLOT_READOUTS = 2
    const val OUT_SLOT_TYPE = 3
    const val OUT_SLOT_COUNT = 3

    /** The order a fresh install and every existing one starts with. */
    @JvmField
    val OUT_DEFAULT_ORDER = intArrayOf(OUT_SLOT_SIGNAL, OUT_SLOT_READOUTS, OUT_SLOT_TYPE)

    /** The default order, as the stored string. */
    const val OUT_DEFAULT_ORDER_STRING = "1,2,3"

    /**
     * Decodes [KEY_OUT_ORDER], repairing anything a hand-edited file or an older build
     * might hold.
     *
     * Never returns null, never throws, and always returns exactly
     * [OUT_SLOT_COUNT] ids: unknown values are dropped, duplicates collapsed, and
     * whatever is missing appended in default order. A partial value therefore keeps
     * the order the user did express instead of resetting the whole thing.
     */
    @JvmStatic
    fun decodeOutOrder(stored: String?): IntArray {
        val ids = ArrayList<Int>()
        if (!stored.isNullOrEmpty()) {
            for (piece in stored.split(",")) {
                val id = piece.trim().toIntOrNull() ?: continue
                if (id !in OUT_SLOT_SIGNAL..OUT_SLOT_COUNT) continue
                if (id !in ids) ids.add(id)
            }
        }
        for (id in OUT_DEFAULT_ORDER) if (id !in ids) ids.add(id)
        return IntArray(OUT_SLOT_COUNT) { ids[it] }
    }

    /** The inverse of [decodeOutOrder]. */
    @JvmStatic
    fun encodeOutOrder(order: IntArray?): String =
        decodeOutOrder((order ?: OUT_DEFAULT_ORDER).joinToString(",")).joinToString(",")

    /**
     * How large a trailing "A" is drawn against the rest of the network type, as a
     * percentage.
     *
     * The reference draws "5GA" with the "A" noticeably smaller than the "5G",
     * sitting on the same baseline; 65 reproduces that ratio. 100 turns the shrink off.
     * One key for both places the type is drawn, because the proportion is a property
     * of the label rather than of where it sits.
     */
    const val KEY_TYPE_SUFFIX_SCALE = "type_suffix_scale"

    /**
     * Size of the in-ring charging bolt, as a percentage of the size the reference path
     * was drawn at.
     *
     * One key for all three arrangements: the bolt is one shape, and a user who
     * thinks it looks small expects it to change wherever it is drawn. The capsule's
     * own fit-to-slot ceiling still applies.
     */
    const val KEY_BOLT_SCALE = "bolt_scale"

    /**
     * Size of the ring's level-dot rows, as a percentage of the bead the stroke
     * implies.
     *
     * One key for the top and the bottom: the two rows are the same ink - four beads
     * threaded onto the ring's own openings - and the whole point of them is that they
     * read as the ring cut into segments. The reference size is the one
     * [TrioGeometry.arcRowDotR] derives from the stroke, so 100% keeps that exactly.
     */
    const val KEY_DOT_SCALE = "dot_scale"

    const val KEY_TYPE_WEIGHT = "type_weight"
    const val KEY_TRACK_ALPHA = "track_alpha"

    const val KEY_DEBUG_LOG = "debug_log"

    // Defaults.
    const val DEF_ENABLED = true
    const val DEF_MERGE_ICONS = true
    const val DEF_SHOW_WIFI = false
    const val DEF_SHOW_MOBILE = true
    const val DEF_SHOW_MOBILE_ACTIVITY = false
    const val DEF_SHOW_VALUE = true
    const val DEF_SHOW_BOLT = true

    /** Off: an install that never touches the key keeps drawing its own arrangement. */
    const val DEF_CHARGING_SHOWS_VALUE = false

    /** Off: a label that hides itself is surprising unless asked for. */
    const val DEF_TYPE_HIDE_ON_WIFI = false

    /** Off: the temperature stays up on battery, as it always did. */
    const val DEF_PERF_TEMP_CHARGING_ONLY = false

    /** 0: the reference stadium. */
    const val DEF_CAPSULE_LENGTH = 0
    const val MIN_CAPSULE_LENGTH = -30
    const val MAX_CAPSULE_LENGTH = 60

    /** 0: the cell sits where the layout puts it. */
    const val DEF_CAPSULE_NUDGE = 0
    const val MIN_CAPSULE_NUDGE_X = -24
    const val MAX_CAPSULE_NUDGE_X = 24
    const val MIN_CAPSULE_NUDGE_Y = -20
    const val MAX_CAPSULE_NUDGE_Y = 20

    /** 100%: the arcs exactly as the fit has always drawn them, so nothing moves on upgrade. */
    const val DEF_WIFI_SCALE = 100
    const val MIN_WIFI_SCALE = 60
    const val MAX_WIFI_SCALE = 160

    /** Legacy, read-only for migration. */
    const val DEF_SHOW_MOBILE_TYPE = false

    /** Off: the network type is not drawn at all. */
    const val MOBILE_TYPE_OFF = 0

    /** Draw the network type inside the ring, in the middle when Wi-Fi is absent. */
    const val MOBILE_TYPE_IN_RING = 1

    /** Draw the network type outside the ring. */
    const val MOBILE_TYPE_OUT_RING = 2

    /** Off by default: the shipped look keeps the number in the centre only without Wi-Fi. */
    const val DEF_MOBILE_TYPE_MODE = MOBILE_TYPE_OUT_RING

    /** Off by default: the arcs stay in the middle and the reading sits in the notch. */
    const val DEF_VALUE_CENTRED = false

    /** The original arrangement: a battery ring with the content inside it. */
    const val STYLE_RING = 0

    /** Retired storage id for the rectangular arrangement; never reuse for another style. */
    const val STYLE_RECT = 1

    /**
     * The capsule arrangement: a wide stadium outline whose stroke is the battery
     * gauge, broken at the top and bottom centre by two four-dot rows.
     */
    const val STYLE_CAPSULE = 2

    /** Keeps the capsule's stored id; the retired rectangle returns to the ring. */
    @JvmStatic
    fun normalizeTrioStyle(style: Int): Int = if (style == STYLE_CAPSULE) STYLE_CAPSULE else STYLE_RING

    /** The ring, for every install that has never chosen. */
    const val DEF_TRIO_STYLE = STYLE_RING

    /**
     * Which bottom-bar material the settings screen uses: docked, floating, or liquid
     * glass.
     */
    const val KEY_NAV_STYLE = "nav_style"
    const val NAV_HYPER_OS = 0
    const val NAV_HYPER_OS_FLOATING = 1
    const val NAV_LIQUID_GLASS = 2
    const val NAV_STYLE_MIN = NAV_HYPER_OS
    const val NAV_STYLE_MAX = NAV_LIQUID_GLASS
    const val DEF_NAV_STYLE = NAV_LIQUID_GLASS

    /** Clamps a stored nav style; anything unknown falls back to the default. */
    @JvmStatic
    fun normalizeNavStyle(style: Int): Int =
        if (style in NAV_STYLE_MIN..NAV_STYLE_MAX) style else DEF_NAV_STYLE

    /** What the bottom bar shows under each icon: both, icon only, or text only. */
    const val KEY_NAV_LABELS = "nav_labels"
    const val NAV_LABELS_BOTH = 0
    const val NAV_LABELS_ICON = 1
    const val NAV_LABELS_TEXT = 2
    const val NAV_LABELS_MIN = NAV_LABELS_BOTH
    const val NAV_LABELS_MAX = NAV_LABELS_TEXT
    const val DEF_NAV_LABELS = NAV_LABELS_BOTH

    @JvmStatic
    fun normalizeNavLabels(mode: Int): Int =
        if (mode in NAV_LABELS_MIN..NAV_LABELS_MAX) mode else DEF_NAV_LABELS

    /** The signal rows are drawn inside the glyph, in the thirds the user picked. */
    const val SIGNAL_IN_RING = 0

    /**
     * The signal is handed back to the system: the native icon keeps its place and the
     * glyph draws no level dots at all.
     */
    const val SIGNAL_OUT_RING = 1

    /** Inside the glyph, for every install that has never chosen. */
    const val DEF_SIGNAL_MODE = SIGNAL_IN_RING

    /** Off by default: outside the glyph the shipped behaviour is to hand the signal back. */
    const val DEF_STACKED_SIGNAL = false

    /** Off by default: the stacked reading shows both SIMs unless told otherwise. */
    const val DEF_DATA_SIM_ONLY = false

    /** Off by default: the charging-power reading is an opt-in. */
    const val DEF_PERF_POWER = false

    /** Off by default, for the same reason as [DEF_PERF_POWER]. */
    const val DEF_PERF_TEMP = false

    /** Off by default, for the same reason as [DEF_PERF_POWER]. */
    const val DEF_PERF_FPS = false

    /**
     * One weight for every reading the module draws.
     *
     * 600 is the weight the status bar's own text settles on next to MagicOS'
     * medium clock: heavy enough to read as one block, light enough not to look like
     * a second typeface. Each reading's slider still overrides this.
     */
    const val DEF_FONT_WEIGHT = 600

    /** Only meaningful with [DEF_PERF_POWER] on; off keeps the readout charging-only. */
    const val DEF_PERF_RUNNING = false

    /** 100%: the shipped glyph keeps exactly the fit it had before it was settable. */
    const val DEF_GLYPH_SCALE = 100

    /** 100%: the adaptive fit already balances the two readings against the icon. */
    const val DEF_PERF_SCALE = 100

    /** 500 = medium, the weight MagicOS draws its own status-bar figures at. */
    const val DEF_PERF_WEIGHT = DEF_FONT_WEIGHT

    /** No extra spacing by default; the lines already sit on the strip's own rhythm. */
    const val DEF_PERF_LINE_GAP = 0

    /** Off by default: the second row is an opt-in reading for a dual-SIM device. */
    const val DEF_DUAL_SIM = false

    /** Off by default: with both off the aggregate meter draws what it always drew. */
    const val DEF_SHOW_SIM_1 = false

    /** Off by default; see [DEF_SHOW_SIM_1]. */
    const val DEF_SHOW_SIM_2 = false

    const val DEF_ROLE_COLORS = true
    const val DEF_ROLE_COLORS_TEMP = false
    const val DEF_COLOR_CRITICAL_ON_DARK = 0xFFFF3B30.toInt()
    const val DEF_COLOR_CRITICAL_ON_LIGHT = 0xFFFF3B30.toInt()
    const val DEF_COLOR_CHARGING_ON_DARK = 0xFF34C759.toInt()
    const val DEF_COLOR_CHARGING_ON_LIGHT = 0xFF1F8F3D.toInt()
    const val DEF_COLOR_LOW_ON_DARK = 0xFFF2B900.toInt()
    const val DEF_COLOR_LOW_ON_LIGHT = 0xFFC99700.toInt()
    const val DEF_LOW_THRESHOLD = 20

    const val DEF_RING_STROKE = 14
    const val DEF_ARC_STROKE = 7
    const val DEF_VALUE_SIZE = 36

    /** The default; see [DEF_FONT_WEIGHT]. 700 is `Typeface.BOLD`. */
    const val DEF_VALUE_WEIGHT = DEF_FONT_WEIGHT

    /** The time's default matches the value's, so the shipped look is unchanged. */
    const val DEF_CLOCK_SIZE = DEF_VALUE_SIZE

    /** Font weight of the time, matching [DEF_VALUE_WEIGHT]. */
    const val DEF_CLOCK_WEIGHT = DEF_FONT_WEIGHT

    const val DEF_CLOCK_SECONDS = false

    /** The date line ships off: the clock's shipped look is the time alone. */
    const val DEF_CLOCK_DATE = false

    /** The weekday ships off, for the same reason as [DEF_CLOCK_DATE]. */
    const val DEF_CLOCK_WEEKDAY = false
    const val DEF_CLOCK_LUNAR = true
    const val DEF_CLOCK_TOP_OFFSET_X = 0
    const val DEF_CLOCK_BOTTOM_OFFSET_X = 0
    const val DEF_CLOCK_LUNAR_GAP = 2
    /** Zero: the two lines are separated by their own line boxes and nothing else. */
    const val DEF_CLOCK_ROW_GAP = 0
    const val DEF_CLOCK_DATE_FORMAT = CLOCK_DATE_MONTH_DAY
    const val DEF_CLOCK_WEEKDAY_FORMAT = CLOCK_WEEKDAY_ZH_SHORT
    const val DEF_COLOR_CLOCK = 0
    const val DEF_CLOCK_WEATHER = true
    const val DEF_CLOCK_WEATHER_STYLE = WEATHER_STYLE_TEXT
    const val DEF_CLOCK_STYLE = CLOCK_STYLE_BALANCED

    /** Off by default: the host's own shade and control center are left untouched. */
    const val DEF_SHADE_HIDE_POWER = false
    const val DEF_SHADE_HIDE_BELL = false
    const val DEF_SHADE_LUNAR = false
    const val DEF_SHADE_WEATHER = false
    const val DEF_SHADE_WEATHER_STYLE = WEATHER_STYLE_TEXT

    const val DEF_RING_SPACING_X = 0
    const val DEF_RING_SPACING_Y = 0
    const val MIN_RING_SPACING_X = -10
    const val MAX_RING_SPACING_X = 20
    const val MIN_RING_SPACING_Y = -10
    const val MAX_RING_SPACING_Y = 20

    const val MIN_CLOCK_OFFSET = -50
    const val MAX_CLOCK_OFFSET = 50
    const val MIN_CLOCK_LUNAR_GAP = 0
    const val MAX_CLOCK_LUNAR_GAP = 30
    const val MIN_CLOCK_ROW_GAP = 0
    const val MAX_CLOCK_ROW_GAP = 16

    /** Font size of the centred network type. */
    const val DEF_TYPE_SIZE = DEF_VALUE_SIZE

    /** 14dp is about 42px on density 3: visible and proportionate to the bar's text. */
    /**
     * The out-of-ring type label's default size, in dp.
     *
     * 12 rather than the 14 the in-ring type uses: beside the signal bars and the
     * battery it is a caption, not a reading.
     */
    const val DEF_OUT_TYPE_SIZE_DP = 14
    const val KEY_ANTI_BURN_IN = "anti_burn_in"
    const val DEF_ANTI_BURN_IN = true
    const val BURN_IN_INTERVAL_MS = 30_000L

    const val KEY_FONT_PATH = "font_path"
    const val DEF_FONT_PATH = ""

    const val KEY_SIGNAL_COLUMNS = "signal_columns"
    const val DEF_SIGNAL_COLUMNS = 4
    const val MIN_SIGNAL_COLUMNS = 4
    const val MAX_SIGNAL_COLUMNS = 5

    /**
     * 16dp: the system's own signal bars measure about 48px at density 3, so the reading
     * ships at the size of the icon it replaces. The box is that number's bar height; the
     * dot row and the two-row style are given extra room on top of it.
     */
    const val DEF_OUT_SIGNAL_SIZE = 16

    /** 100%: the dot row is exactly as wide as a bar. */
    const val DEF_OUT_DOT_SIZE = 100

    const val DEF_STACK_STYLE = STACK_STYLE_DOTS

    /** Off: the bolt is a state indicator before it is an ornament. */
    const val DEF_BOLT_ANIM = BOLT_ANIM_OFF

    /** 100%: the loop as it was authored, one cycle per 1.4 s. */
    const val DEF_BOLT_SPEED = 100

    const val DEF_STACK_LABEL = STACK_LABEL_BESIDE



    /** 2dp is the value the single hard-coded gap used to hold. */
    const val DEF_OUT_TYPE_MARGIN_LEFT = 2
    const val DEF_OUT_TYPE_MARGIN_RIGHT = 2

    /** 2dp: the gap the reading kept from the glyph before it was settable. */
    const val DEF_OUT_SIGNAL_MARGIN = 2

    /** 65% for the trailing "A": the reference's 56/86 ratio. */
    const val DEF_TYPE_SUFFIX_SCALE = 65

    /** 100%: the bolt at exactly the size the reference path implies. */
    const val DEF_BOLT_SCALE = 100

    /** 100%: a bead whose diameter is exactly the ring's thickness. */
    const val DEF_DOT_SCALE = 100

    const val DEF_TYPE_WEIGHT = DEF_FONT_WEIGHT
    const val DEF_TRACK_ALPHA = 56

    const val DEF_DEBUG_LOG = false

    // Bounds.
    const val MIN_RING_STROKE = 4
    const val MAX_RING_STROKE = 16
    const val MIN_ARC_STROKE = 3
    const val MAX_ARC_STROKE = 16
    const val MIN_VALUE_SIZE = 16

    /**
     * The percentage's size span.
     *
     * 44 stays the ceiling for the arrangements that have a fixed slot to fill - the
     * capsule's right-hand reading and the rectangle's centre - because their geometry
     * already decides how much room there is. The ring is different: a placed reading
     * is sized by the third it sits in, and the middle third accepts far more ink. That
     * is what [MAX_RING_VALUE_SIZE] is for.
     */
    const val MAX_VALUE_SIZE = 44

    /** The percentage's ceiling when it is placed in a ring slot. */
    const val MAX_RING_VALUE_SIZE = 56

    /**
     * The percentage ceiling for `trioStyle`, in one place so the stored read, the
     * broadcast read and the slider's range cannot disagree.
     */
    @JvmStatic
    fun maxValueSize(trioStyle: Int): Int = when (trioStyle) {
        STYLE_RING -> MAX_RING_VALUE_SIZE
        STYLE_CAPSULE -> MAX_CAPSULE_READING_SIZE
        else -> MAX_VALUE_SIZE
    }

    /**
     * The ceiling the capsule's two interior cells can honestly hold.
     *
     * 44 was the ceiling while the interior was split into two fixed halves. The cells
     * are independent now - each is fitted to the whole track it hangs its edge on - so
     * the slider no longer runs into a wall at 44 and stops doing anything.
     */
    const val MAX_CAPSULE_READING_SIZE = 50

    /** The clock's ceiling for `trioStyle`; the capsule has the wider track. */
    @JvmStatic
    fun maxClockSize(trioStyle: Int): Int =
        if (trioStyle == STYLE_CAPSULE) MAX_CAPSULE_READING_SIZE else MAX_CLOCK_SIZE

    /** The in-glyph type's ceiling for `trioStyle`. */
    @JvmStatic
    fun maxTypeSize(trioStyle: Int): Int =
        if (trioStyle == STYLE_CAPSULE) MAX_CAPSULE_READING_SIZE else MAX_TYPE_SIZE

    const val MIN_VALUE_WEIGHT = 100
    const val MAX_VALUE_WEIGHT = 900

    /** The time's own size span. */
    const val MIN_CLOCK_SIZE = 16
    const val MAX_CLOCK_SIZE = 44
    const val MIN_CLOCK_WEIGHT = 100
    const val MAX_CLOCK_WEIGHT = 900
    const val MIN_TYPE_SIZE = 16
    const val MAX_TYPE_SIZE = 44

    /**
     * The span the out-of-ring type size offers, in dp: 8..26 covers everything from
     * the stock label up to a prominent one without overflowing the row.
     */
    const val MIN_OUT_TYPE_SIZE_DP = 8
    const val MAX_OUT_TYPE_SIZE_DP = 26

    /**
     * The physical span the signal slider offers, in dp: 6dp is where four bars stop
     * being distinguishable and 20dp is the tallest the row shows before the reading
     * starts pushing the icons around it.
     */
    const val MIN_OUT_SIGNAL_SIZE = 6
    const val MAX_OUT_SIGNAL_SIZE = 20

    const val MIN_OUT_DOT_SIZE = 40
    const val MAX_OUT_DOT_SIZE = 180

    /** 0 lets the label butt up against its neighbour; 16 is wider than the icon gap. */
    const val MIN_OUT_TYPE_MARGIN = 0
    const val MAX_OUT_TYPE_MARGIN = 16

    /**
     * Non-negative on purpose: this is a reserved gap, and a negative value would mean
     * "overlap the glyph", which is the very thing the reservation prevents.
     */
    const val MIN_OUT_SIGNAL_MARGIN = 0
    const val MAX_OUT_SIGNAL_MARGIN = 16

    /** The suffix may be the same size as the rest (100) or a little over half (50). */
    const val MIN_TYPE_SUFFIX_SCALE = 50
    const val MAX_TYPE_SUFFIX_SCALE = 100

    /** 60 keeps the bolt legible; 160 is where the top gap starts crowding the digits. */
    const val MIN_BOLT_SCALE = 60
    const val MAX_BOLT_SCALE = 160

    /**
     * 50% is a slow, calm loop; 200% is fast enough to read as "quick charge".
     *
     * Deliberately not a free number: past 200% the sparks are a blur at 17dp, and below
     * 50% the loop looks stopped rather than slow.
     */
    const val MIN_BOLT_SPEED = 50
    const val MAX_BOLT_SPEED = 200

    /** 50 keeps four separate beads; 160 is where they reach the arc's caps. */
    const val MIN_DOT_SCALE = 50
    const val MAX_DOT_SCALE = 160

    const val MIN_TYPE_WEIGHT = 100
    const val MAX_TYPE_WEIGHT = 900
    const val MIN_TRACK_ALPHA = 0
    const val MAX_TRACK_ALPHA = 255

    /** 60..100: the ceiling is the box the row hands over. */
    const val MIN_GLYPH_SCALE = 60
    const val MAX_GLYPH_SCALE = 100

    /** Wider than the glyph's span and allowed past 100: this multiplies a text size. */
    const val MIN_PERF_SCALE = 60
    const val MAX_PERF_SCALE = 150

    const val MIN_PERF_WEIGHT = 100
    const val MAX_PERF_WEIGHT = 900

    const val MIN_PERF_LINE_GAP = 0
    const val MAX_PERF_LINE_GAP = 12
    const val MIN_LOW_THRESHOLD = 5
    const val MAX_LOW_THRESHOLD = 50

    const val MIN_MOBILE_TYPE_MODE = MOBILE_TYPE_OFF
    const val MAX_MOBILE_TYPE_MODE = MOBILE_TYPE_OUT_RING

    const val MIN_SIGNAL_MODE = SIGNAL_IN_RING
    const val MAX_SIGNAL_MODE = SIGNAL_OUT_RING

    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}
