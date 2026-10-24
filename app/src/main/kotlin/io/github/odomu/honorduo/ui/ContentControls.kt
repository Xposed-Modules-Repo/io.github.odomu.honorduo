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

package io.github.odomu.honorduo.ui


import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioSettings
import io.github.odomu.honorduo.render.TrioZones
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Content controls shared by General and the visual editor.
 *
 * **Declaration order is the order they appear in a component's sheet**, and it
 * follows one rule: *where a thing goes, before how it looks*. A row that decides
 * placement comes first, because it answers the question the user opened the sheet with
 * ("where is this drawn?"). Size, weight and colour rows live in [geometrySliders] and
 * come after these, in their own groups.
 *
 * Only one placement choice is left, and only one piece has it: the network type's
 * "show it beside the ring". Every other piece is placed or released - add it to a third
 * and it is drawn there, do not and MagicOS keeps its own icon - so the old "signal in ring /
 * out of ring" switch, and the "stacked signal" style that went with it, were asking the
 * user to choose between states the layout already decides.
 */
internal enum class ContentControl(val owner: TrioSettingsPart) {
    Enabled(TrioSettingsPart.Appearance),
    MergeIcons(TrioSettingsPart.Appearance),
    Style(TrioSettingsPart.Appearance),
    AntiBurnIn(TrioSettingsPart.Appearance),
    SignalColumns(TrioSettingsPart.Mobile),
    Wifi(TrioSettingsPart.Wifi),
    ReplaceWifiIcon(TrioSettingsPart.Wifi),
    ReplaceStatusIcon(TrioSettingsPart.Appearance),
    /**
     * The signal card: one switch for the aggregate meter, then one per card.
     *
     * The two per-card rows are rows of THIS card rather than cards of their own.
     * They were separate cards, and both sheets resolved to the same set of settings -
     * two entries in the library that could only ever repeat each other. The ink is
     * one kind of thing (a row of level dots); which reading it shows is a property of
     * the row, not a second component.
     */
    Mobile(TrioSettingsPart.Mobile),
    DataSim(TrioSettingsPart.Mobile),
    /**
     * The out-of-ring rendering: the module's own stacked reading, or MagicOS' icon.
     *
     * Owned by the signal card rather than being a piece of its own, because it is a
     * property of where the signal is drawn, not a third reading. It sits directly under
     * the position row it depends on.
     */
    Stacked(TrioSettingsPart.Mobile),
    MobileActivity(TrioSettingsPart.Mobile),
    Sim1(TrioSettingsPart.Mobile),
    Sim2(TrioSettingsPart.Mobile),
    Value(TrioSettingsPart.Battery),
    Percent(TrioSettingsPart.Battery),
    /**
     * A charger puts the number in the ring's centre, over whatever the layout put there.
     *
     * Owned by the percentage: it is a rule about that reading rather than a third kind
     * of content. Resolved per frame, so nothing stored changes when the cable moves.
     */
    ChargingShowsValue(TrioSettingsPart.Battery),
    Bolt(TrioSettingsPart.Bolt),
    BoltAnimEnabled(TrioSettingsPart.Bolt),
    BoltAnim(TrioSettingsPart.Bolt),
    /**
     * Network type, and the ONE piece that has a position choice of its own.
     *
     * Every other piece is placed or released: add it to a third and it is drawn there,
     * do not and MagicOS keeps its own icon. There is no third option, which is why the old
     * "signal in ring / out of ring" switch is gone. The type is different - it is text with
     * no native counterpart to hand back - so "show it beside the ring" is a real choice
     * rather than a way of expressing the same thing twice.
     */
    Type(TrioSettingsPart.Type),
    Power(TrioSettingsPart.Readouts),
    StackStyle(TrioSettingsPart.Mobile),
    StackLabel(TrioSettingsPart.Type),
    /**
     * The type steps aside while Wi-Fi is up.
     *
     * Owned by the type: it is a rule about when that label is worth drawing, not a
     * reading of its own. One switch for every arrangement - the label says what the
     * cellular radio is doing, and with Wi-Fi up it shares the bar with the arcs the
     * user is actually reading.
     */
    TypeHideOnWifi(TrioSettingsPart.Type),
    RunningPower(TrioSettingsPart.Readouts),
    Temperature(TrioSettingsPart.Readouts),
    /** The temperature shows only while the charger is attached (#45). */
    TempChargingOnly(TrioSettingsPart.Readouts),
    /**
     * The frame rate, in the same sheet as the two readouts.
     *
     * It is a reading like the wattage and the temperature, so it is switched on in the
     * same place; what differs is that it never shares their two-line strip. That strip
     * is sized to hold exactly those two, and a third line would scale all three down -
     * so a frame rate that has no band of its own is drawn in a view beside the strip.
     */
    Fps(TrioSettingsPart.Readouts),
    Clock(TrioSettingsPart.Clock),
    ClockLunar(TrioSettingsPart.Clock),
    ClockSeconds(TrioSettingsPart.Clock),
    ClockDate(TrioSettingsPart.Clock),
    ClockDateFormat(TrioSettingsPart.Clock),
    ClockWeekday(TrioSettingsPart.Clock),
    ClockWeekdayFormat(TrioSettingsPart.Clock),
}

internal fun contentControlsFor(part: TrioSettingsPart): List<ContentControl> =
    ContentControl.entries.filter {
        val matchesOwner = it.owner == part || (part == TrioSettingsPart.Battery && it == ContentControl.Bolt)
        matchesOwner && !(part == TrioSettingsPart.Appearance &&
            (it == ContentControl.Enabled || it == ContentControl.MergeIcons || it == ContentControl.Style || it == ContentControl.AntiBurnIn))
    }

@Composable
internal fun ContentControlRow(
    control: ContentControl,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    ringSettings: TrioSettings = settings,
) {
    val a = TrioAppearance.of(settings)
    val master = listOf(a.glyph to R.string.master_title)

    val positioned = when (control) {
        ContentControl.Value -> TrioZones.Part.VALUE
        ContentControl.Bolt -> TrioZones.Part.BOLT
        ContentControl.Type -> TrioZones.Part.TYPE
        ContentControl.Clock -> TrioZones.Part.CLOCK
        ContentControl.Fps -> TrioZones.Part.FPS
        ContentControl.Power -> TrioZones.Part.POWER
        ContentControl.RunningPower -> TrioZones.Part.RUNNING
        ContentControl.Temperature -> TrioZones.Part.TEMP
        else -> null
    }
    if (control == ContentControl.Type) {
        TypePositionControl(settings, update, zonesAllowed = a.slotted)
        return
    }
    if ((a.ring || a.capsule) && positioned != null && !a.rect) {
        RingPositionControl(positioned, ringSettings, update)
        if (control == ContentControl.Clock) return
    }
    if (control == ContentControl.Mobile) {
        SignalPositionControl(settings, update)
    }
    if (control == ContentControl.Sim1 && a.slotted) {
        SignalCardPositionControl(TrioZones.Part.SIM_1, settings, update, visible = signalInsideRing(a))
    }
    if (control == ContentControl.Sim2 && a.slotted) {
        SignalCardPositionControl(TrioZones.Part.SIM_2, settings, update, visible = signalInsideRing(a))
    }

    when (control) {
        ContentControl.Enabled -> ContentToggle(settings.enabled, R.string.master_module_title,
            null, emptyList()) { value -> update { it.setEnabled(value) } }
        ContentControl.MergeIcons -> ContentToggle(settings.mergeIcons, R.string.master_title,
            R.string.merge_icons_summary, listOf(settings.enabled to R.string.master_module_title)) { value -> update { it.setMergeIcons(value) } }
        ContentControl.Style -> {
            val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
            ContentChoice(styles.indexOf(a.style).coerceAtLeast(0), R.string.trio_style_title,
                null,
                listOf(R.string.trio_style_ring, R.string.trio_style_capsule),
                master) { index -> update { it.setTrioStyle(styles[index]) } }
        }
        ContentControl.AntiBurnIn -> ContentToggle(
            checked = settings.antiBurnIn,
            title = R.string.anti_burn_in_title,
            summaryRes = R.string.anti_burn_in_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setAntiBurnIn(value) } }
        ContentControl.SignalColumns -> if (a.stackedOut()) {
            val columns = listOf(4, 5)
            ContentChoice(
                selected = columns.indexOf(settings.signalColumns).coerceAtLeast(0),
                title = R.string.signal_columns_title,
                summaryRes = R.string.signal_columns_summary,
                options = listOf(R.string.signal_columns_4, R.string.signal_columns_5),
                dependencies = listOf(a.mobile to R.string.show_mobile_title),
            ) { index -> update { it.setSignalColumns(columns[index]) } }
        } else Unit
        ContentControl.Wifi -> WifiPositionControl(settings, ringSettings, update)
        ContentControl.ReplaceWifiIcon -> ContentToggle(
            checked = settings.replaceWifiIcon,
            title = R.string.replace_wifi_icon_title,
            summaryRes = R.string.replace_wifi_icon_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setReplaceWifiIcon(value) } }
        ContentControl.ReplaceStatusIcon -> ContentToggle(
            checked = settings.replaceStatusIcon,
            title = R.string.replace_status_icon_title,
            summaryRes = R.string.replace_status_icon_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setReplaceStatusIcon(value) } }
        ContentControl.Mobile -> Unit
        ContentControl.DataSim -> ContentToggle(a.dataSimOnly, R.string.data_sim_only_title,
            null, emptyList()) { value -> update { it.setDataSimOnly(value) } }
        ContentControl.Stacked -> ContentToggle(
            checked = a.stackedSignal,
            title = R.string.stacked_signal_title,
            summaryRes = R.string.stacked_signal_summary,
            dependencies = listOf(a.signalOutOfRing to R.string.ring_position_mobile),
        ) { value -> update { it.setStackedSignal(value) } }
        ContentControl.MobileActivity -> if (a.signalOutOfRing) {
            ContentToggle(
                checked = settings.showMobileActivity,
                title = R.string.show_mobile_activity_title,
                summaryRes = R.string.show_mobile_activity_summary,
                dependencies = listOf(true to R.string.ring_position_mobile),
            ) { value -> update { it.setShowMobileActivity(value) } }
        } else Unit
        ContentControl.Sim1 -> ContentToggle(
            checked = a.showSim1,
            title = R.string.show_sim_1_title,
            summaryRes = null,
            dependencies = listOf(a.mobile to R.string.show_mobile_title) +
                (signalInsideRing(a) to R.string.ring_position_mobile),
        ) { value -> update { it.setShowSim1(value) } }
        ContentControl.Sim2 -> ContentToggle(
            checked = a.showSim2,
            title = R.string.show_sim_2_title,
            summaryRes = null,
            dependencies = listOf(a.mobile to R.string.show_mobile_title) +
                (signalInsideRing(a) to R.string.ring_position_mobile),
        ) { value -> update { it.setShowSim2(value) } }
        ContentControl.Value -> ContentToggle(a.value, R.string.show_value_title, null, emptyList()) { value -> update { it.setShowValue(value) } }
        ContentControl.Percent -> ContentToggle(
            checked = settings.showPercent,
            title = R.string.show_percent_title,
            summaryRes = null,
            dependencies = listOf(a.value to R.string.show_value_title),
        ) { value -> update { it.setShowPercent(value) } }
        ContentControl.ChargingShowsValue -> ContentToggle(
            checked = settings.chargingShowsValue,
            title = R.string.charging_shows_value_title,
            summaryRes = R.string.charging_shows_value_summary,
            // The ring is the only arrangement with a centre; the capsule and the
            // rectangle have no zone this could occupy.
            dependencies = listOf(!a.ring to R.string.trio_style_title),
        ) { value -> update { it.setChargingShowsValue(value) } }
        ContentControl.Bolt -> ContentToggle(a.boltWanted, R.string.show_bolt_title, null, emptyList()) { value -> update { it.setShowBolt(value) } }
        ContentControl.BoltAnimEnabled -> ContentToggle(
            checked = settings.boltAnim != Prefs.BOLT_ANIM_OFF,
            title = R.string.bolt_anim_enabled_title,
            summaryRes = null,
            dependencies = listOf(a.boltWanted to R.string.show_bolt_title),
        ) { enabled ->
            update {
                it.setBoltAnim(if (enabled) Prefs.BOLT_ANIM_PULSE else Prefs.BOLT_ANIM_OFF)
            }
        }
        ContentControl.BoltAnim -> if (settings.boltAnim != Prefs.BOLT_ANIM_OFF) {
            val styles = listOf(Prefs.BOLT_ANIM_PULSE, Prefs.BOLT_ANIM_FLOW, Prefs.BOLT_ANIM_SPARK)
            val selectedIndex = styles.indexOf(settings.boltAnim).coerceAtLeast(0)
            ContentChoice(
                selected = selectedIndex,
                title = R.string.bolt_anim_style_title,
                summaryRes = R.string.bolt_anim_summary,
                options = listOf(
                    R.string.bolt_anim_pulse,
                    R.string.bolt_anim_flow,
                    R.string.bolt_anim_spark,
                ),
                dependencies = listOf(a.boltWanted to R.string.show_bolt_title),
            ) { index -> update { it.setBoltAnim(styles[index]) } }
        } else Unit
        ContentControl.Type -> TypePositionControl(settings, update, zonesAllowed = a.slotted)
        ContentControl.TypeHideOnWifi -> ContentToggle(
            checked = settings.typeHideOnWifi,
            title = R.string.type_hide_on_wifi_title,
            summaryRes = R.string.type_hide_on_wifi_summary,
            dependencies = listOf(!a.typeAnywhere() to R.string.show_mobile_type_title),
        ) { value -> update { it.setTypeHideOnWifi(value) } }
        ContentControl.Power -> ContentToggle(settings.perfPower, R.string.perf_power_title, null, emptyList()) { value -> update { it.setPerfPower(value) } }
        ContentControl.RunningPower -> ContentToggle(checked = settings.perfRunning, title = R.string.perf_running_title, summaryRes = null,
            dependencies = listOf(settings.perfPower to R.string.perf_power_title)) { value -> update { it.setPerfRunning(value) } }
        // Absent (not greyed) when the caption has nowhere to sit: type inside the
        // glyph, or the stacked reading off.
        ContentControl.StackLabel ->
            if (a.typeOutOfRing && a.stackedOut()) {
            ContentChoice(
                settings.stackLabel, R.string.stack_label_title, null,
                listOf(R.string.stack_label_left, R.string.stack_label_above),
                emptyList(),
            ) { value -> update { it.setStackLabel(value) } }
        }
        // With one card both styles draw the same row, so the choice changes nothing.
        ContentControl.StackStyle ->
            if (!a.dataSimOnly) {
                ContentChoice(
                    settings.stackStyle, R.string.stack_style_title, null,
                    listOf(R.string.stack_style_dots, R.string.stack_style_rows),
                    listOf(a.stackedOut() to R.string.stacked_signal_title),
                ) { value -> update { it.setStackStyle(value) } }
            }
        ContentControl.TempChargingOnly -> ContentToggle(
            checked = settings.perfTempChargingOnly,
            title = R.string.perf_temp_charging_only_title,
            summaryRes = R.string.perf_temp_charging_only_summary,
            dependencies = listOf(settings.perfTemp to R.string.perf_temp_title),
        ) { value -> update { it.setPerfTempChargingOnly(value) } }
        ContentControl.Temperature -> ContentToggle(settings.perfTemp, R.string.perf_temp_title, null, emptyList()) { value -> update { it.setPerfTemp(value) } }
        ContentControl.Fps -> ContentToggle(checked = settings.perfFps, title = R.string.perf_fps_title, summaryRes = R.string.perf_fps_summary,
            dependencies = emptyList()) { value -> update { it.setPerfFps(value) } }
        ContentControl.Clock -> Unit
        ContentControl.ClockLunar -> ContentToggle(
            checked = settings.clockLunar,
            title = R.string.clock_lunar_title,
            summaryRes = R.string.clock_lunar_summary,
            dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
        ) { value -> update { it.setClockLunar(value) } }
        ContentControl.ClockSeconds -> ContentToggle(
            checked = settings.clockSeconds,
            title = R.string.clock_seconds_title,
            summaryRes = R.string.clock_seconds_summary,
            dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
        ) { value -> update { it.setClockSeconds(value) } }
        ContentControl.ClockDate -> ContentToggle(
            checked = settings.clockDate,
            title = R.string.clock_date_title,
            summaryRes = R.string.clock_date_summary,
            dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
        ) { value -> update { it.setClockDate(value) } }
        ContentControl.ClockDateFormat -> if (settings.clockDate) {
            ContentChoice(
                selected = settings.clockDateFormat,
                title = R.string.clock_date_format_title,
                summaryRes = null,
                options = listOf(
                    R.string.clock_date_format_slash_md,
                    R.string.clock_date_format_slash_ymd,
                    R.string.clock_date_format_zh_md,
                    R.string.clock_date_format_zh_ymd,
                ),
                dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
                onChange = { value -> update { it.setClockDateFormat(value) } },
            )
        } else Unit
        ContentControl.ClockWeekday -> ContentToggle(
            checked = settings.clockWeekday,
            title = R.string.clock_weekday_title,
            summaryRes = R.string.clock_weekday_summary,
            dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
        ) { value -> update { it.setClockWeekday(value) } }
        ContentControl.ClockWeekdayFormat -> if (settings.clockWeekday) {
            ContentChoice(
                selected = settings.clockWeekdayFormat,
                title = R.string.clock_weekday_format_title,
                summaryRes = null,
                options = listOf(
                    R.string.clock_weekday_format_zh_short,
                    R.string.clock_weekday_format_zh_full,
                    R.string.clock_weekday_format_en_short,
                ),
                dependencies = listOf(!a.hasRingPart(Prefs.PART_CLOCK) to R.string.ring_position_clock),
                onChange = { value -> update { it.setClockWeekdayFormat(value) } },
            )
        } else Unit
    }
}

/**
 * The signal's own two-way position: in the ring, or back to the system.
 *
 * This is the one position row that is not a slot list, because the signal is the one
 * piece drawn from per-card rows rather than from a single slot. "Outside the ring" here
 * means exactly what "return it to the system" means on every other row - the glyph draws
 * appears.
 */
@Composable
private fun SignalPositionControl(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val outside = settings.signalMode == Prefs.SIGNAL_OUT_RING
    ContentChoice(
        selected = if (outside) 1 else 0,
        title = R.string.ring_position_mobile,
        summaryRes = null,
        options = listOf(R.string.signal_mode_in_ring, R.string.signal_mode_out_ring),
        dependencies = listOf(settings.enabled to R.string.master_title),
        onChange = { value ->
            update { it.setSignalMode(if (value == 1) Prefs.SIGNAL_OUT_RING else Prefs.SIGNAL_IN_RING) }
        },
    )
}

/**
 * Whether the signal is drawn inside the glyph, which is what enables the per-card rows.
 *
 * Covers the capsule as well as the ring: both draw the rows in a band, so both can place
 * them. The rectangle cannot, and [TrioAppearance.slotted] is what the callers gate on, so
 * this only has to answer the shared part.
 */
private fun signalInsideRing(a: TrioAppearance): Boolean =
    a.mobile && a.slotted && !a.signalOutOfRing

/**
 * One card's third, with the thirds the other card holds disabled.
 *
 * The two cards cannot share a third: two rows of four dots in one slot would overlap
 * and the layout would have to pick one of them, so the second card's choice is expressed
 * by disabling the option rather than by silently moving the first card. A disabled row
 * that cannot be picked is also the honest presentation of "one of these is taken".
 *
 * The middle is disabled for every card, always, because the ring does not hold a signal
 * row there - see [TrioZones.isSignalRow] and the reason in `Prefs.zoneAccepts`. That is not
 * the other card taking it: it is a band the row has no business in, so it is blocked
 * whatever the twin is doing.
 */
@Composable
private fun SignalCardPositionControl(
    part: TrioZones.Part,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    visible: Boolean,
) {
    // Hidden rather than greyed, like every other gated row: a signal card has no slot
    // to pick while the signal is drawn outside the glyph.
    if (!visible) {
        return
    }
    val id = TrioZones.encodePart(part)
    val twin = if (part == TrioZones.Part.SIM_1) TrioZones.Part.SIM_2 else TrioZones.Part.SIM_1
    val twinId = TrioZones.encodePart(twin)
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) } + 1
    val takenByTwin = zones.indexOfFirst { it.contains(twinId) }
    // Option 0 is "not placed" and is always allowed; 1..3 mirror the zones. The middle is
    // refused for the row itself, and the one the twin holds is refused because it is taken.
    val blocked = buildSet {
        add(TrioZones.Zone.CENTRE.ordinal + 1)
        if (takenByTwin >= 0) add(takenByTwin + 1)
    }
    ContentChoice(
        selected = placedAt,
        title = if (part == TrioZones.Part.SIM_1) R.string.ring_position_sim_1
        else R.string.ring_position_sim_2,
        summaryRes = null,
        options = listOf(R.string.ring_position_native, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom),
        dependencies = listOf(settings.enabled to R.string.master_title),
        blockedIndices = blocked,
        onChange = { value -> update { it.setRingPartPosition(part, value) } },
    )
}

/**
 * Wi-Fi's position, with "off" as one of the choices.
 *
 * Wi-Fi is the one piece whose switch and position always changed together: switching it
 * off and removing it from the ring produced the same picture, and switching it on with
 * nothing placed produced none. Two rows for one decision invited the pair to disagree, so
 * they are one row here - index 0 means off-and-unplaced, 1..3 mean on-and-in-that-third.
 *
 * Both facts are written together through [SettingsRepository.setWifiPosition], because
 * writing them separately would leave a frame in between where the switch and the layout
 * disagree.
 */
@Composable
private fun WifiPositionControl(
    settings: TrioSettings,
    ringSettings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = TrioZones.encodePart(TrioZones.Part.WIFI)
    val placedAt = ringSettings.ringZones.indexOfFirst { it.contains(id) } + 1
    // Off wins over "placed somewhere": a switch that is off draws no arcs whatever the
    // layout says, so reporting a slot here would name a place that produces nothing.
    val selected = if (!settings.showWifi) 0 else placedAt
    ContentChoice(selected, R.string.ring_position_wifi, null,
        listOf(R.string.ring_position_native, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom),
        listOf(settings.enabled to R.string.master_title)) { value ->
        update { it.setWifiPosition(value) }
    }
}

/**
 * The one option every position dropdown has to be able to refuse: the ring's middle does
 * not hold a signal row.
 *
 * Written as a set of option indices rather than as a boolean so the caller can add the
 * options it has its own reason to disable - the third the other card already holds - and
 * hand one set to [ContentChoice]. The index is the zone's ordinal plus one: zero is "not
 * placed" in every one of these dropdowns, and 1..3 mirror the three bands.
 *
 * Only meaningful where the row is actually a ring row. The per-card rows are ring-only,
 * and the Wi-Fi row never names a signal row, so in practice this is the card rows' own
 * constant - kept a function so the arithmetic cannot be written slightly differently in
 * two places.
 */
/**
 * The network type's position: outside first, then the three bands, then the system.
 *
 * "Outside" leads because it is the default the type has always had on this bar, and
 * because a type that is not in the ring must land somewhere - removing it from the band
 * list used to leave it unplaced and therefore invisible. Index 0 writes the outside
 * mode, 1..3 place it in a band, 4 hands it back to the system.
 */
@Composable
private fun TypePositionControl(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    zonesAllowed: Boolean,
) {
    val id = TrioZones.encodePart(TrioZones.Part.TYPE)
    val placedAt = settings.ringZones.indexOfFirst { it.contains(id) } + 1
    // No "hand back" entry: outside IS the released position for the type.
    val selected = if (placedAt in 1..3) placedAt else 0
    ContentChoice(selected, R.string.ring_position_type, null,
        listOf(R.string.mobile_type_outside, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom),
        listOf(settings.enabled to R.string.master_title),
        blockedIndices = if (zonesAllowed) emptySet() else setOf(1, 2, 3),
    ) { value ->
        update {
            when (value) {
                0 -> {
                    it.setMobileTypeMode(Prefs.MOBILE_TYPE_OUT_RING)
                    it.setRingPartPosition(TrioZones.Part.TYPE, 0)
                }
                else -> {
                    it.setRingPartPosition(TrioZones.Part.TYPE, value)
                    it.setMobileTypeMode(Prefs.MOBILE_TYPE_IN_RING)
                }
            }
        }
    }
}

private fun middleBlocked(part: TrioZones.Part): Set<Int> =
    if (TrioZones.isSignalRow(part)) setOf(TrioZones.Zone.CENTRE.ordinal + 1) else emptySet()

@Composable
private fun RingPositionControl(
    part: TrioZones.Part,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = TrioZones.encodePart(part)
    // Which zone's list currently holds this piece, or 0 for "not placed". The dropdown is a shortcut into the priority lists, not a second source of truth.
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) } + 1
    val selected = if (part == TrioZones.Part.TYPE
        && settings.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING) 4 else placedAt
    // The row's title is the SLOT it picks, never the component's own switch name.
    val title = when (part) {
        TrioZones.Part.WIFI -> R.string.ring_position_wifi
        TrioZones.Part.MOBILE -> R.string.ring_position_mobile
        TrioZones.Part.SIM_1 -> R.string.ring_position_sim_1
        TrioZones.Part.SIM_2 -> R.string.ring_position_sim_2
        TrioZones.Part.VALUE -> R.string.ring_position_value
        TrioZones.Part.BOLT -> R.string.ring_position_bolt
        TrioZones.Part.TYPE -> R.string.ring_position_type
        TrioZones.Part.CLOCK -> R.string.ring_position_clock
        TrioZones.Part.POWER -> R.string.ring_position_power
        TrioZones.Part.TEMP -> R.string.ring_position_temp
        TrioZones.Part.RUNNING -> R.string.ring_position_running
        TrioZones.Part.FPS -> R.string.ring_position_fps
    }
    ContentChoice(selected, title,
        null,
        listOf(R.string.ring_position_native, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom) +
            if (part == TrioZones.Part.TYPE) listOf(R.string.mobile_type_outside) else emptyList(),
        listOf(settings.enabled to R.string.master_title),
        // A signal row cannot be put in the middle band - see [middleBlocked].
        blockedIndices = middleBlocked(part),
    ) { value ->
        update { it.setRingPartPosition(part, value) }
    }
}

/**
 * A switch row, optionally with a description under its title.
 *
 * [summaryRes] is null for the rows that explain themselves: the master switch
 * (whose own title already says what it turns on) and the ring-position dropdown
 * (whose options name the slots). Miuix drops the description line entirely when
 * the summary is null, so the row is not left with an empty second line.
 */
@Composable
private fun ContentToggle(
    checked: Boolean,
    title: Int,
    summaryRes: Int?,
    dependencies: List<Pair<Boolean, Int>>,
    onChange: (Boolean) -> Unit,
) {
    ContentGate(dependencies) {
        SwitchPreference(
            checked = checked,
            title = stringResource(title),
            summary = summaryRes?.let { stringResource(it) },
            insideMargin = SettingsItemMargin,
            onCheckedChange = onChange,
        )
    }
}

/**
 * A dropdown row, optionally with a description and with individual options disabled.
 *
 * [blockedIndices] is for the options that exist but may not be chosen right now - the
 * third already held by the other card. Disabling the entry rather than dropping it keeps
 * the list the same length as the other cards' lists, so the numeric order the row writes
 * stays the same everywhere.
 *
 * @param enabled a gate independent of [dependencies]: the row can be live while depending
 *   on nothing, which is what the per-card rows need when the signal is outside the ring.
 */
@Composable
private fun ContentChoice(
    selected: Int,
    title: Int,
    summaryRes: Int?,
    options: List<Int>,
    dependencies: List<Pair<Boolean, Int>>,
    enabled: Boolean = true,
    blockedIndices: Set<Int> = emptySet(),
    onChange: (Int) -> Unit,
) {
    ContentGate(dependencies) {
        if (blockedIndices.isEmpty()) {
            GlassDropdownPreference(
                items = options.map { stringResource(it) },
                selectedIndex = selected,
                title = stringResource(title),
                summary = summaryRes?.let { stringResource(it) },
                insideMargin = SettingsItemMargin,
                enabled = enabled && dependencies.all { it.first },
                onSelectedIndexChange = onChange,
            )
        } else {
            GlassDropdownPreference(
                entry = DropdownEntry(
                    items = options.mapIndexed { index, res ->
                        DropdownItem(
                            text = stringResource(res),
                            enabled = index !in blockedIndices,
                            selected = index == selected,
                            // The list carries its own per-item state, so each entry has
                            // to report its own choice back.
                            onClick = { onChange(index) },
                        )
                    },
                    enabled = enabled && dependencies.all { it.first },
                ),
                title = stringResource(title),
                summary = summaryRes?.let { stringResource(it) },
                insideMargin = SettingsItemMargin,
                enabled = enabled && dependencies.all { it.first },
            )
        }
    }
}

@Composable
private fun ContentGate(dependencies: List<Pair<Boolean, Int>>, content: @Composable () -> Unit) {
    // Switched off means switched off: the row is not drawn at all.
    if (dependencies.any { !it.first }) {
        return
    }
    content()
}
