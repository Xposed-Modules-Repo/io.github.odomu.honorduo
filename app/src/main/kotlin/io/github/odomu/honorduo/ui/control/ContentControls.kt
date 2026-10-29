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

package io.github.odomu.honorduo.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.component.GlassDropdownPreference
import io.github.odomu.honorduo.ui.component.GlassMultiDropdownPreference
import io.github.odomu.honorduo.ui.component.SettingsItemMargin
import io.github.odomu.honorduo.ui.dialog.zoneLabelRes
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import io.github.odomu.honorduo.ui.render.IconZones
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
internal enum class ContentControl(val owner: SettingsPart) {
    Enabled(SettingsPart.Appearance),
    MergeIcons(SettingsPart.Appearance),
    Style(SettingsPart.Appearance),
    AntiBurnIn(SettingsPart.Appearance),
    SignalColumns(SettingsPart.Mobile),
    Wifi(SettingsPart.Wifi),
    ReplaceWifiIcon(SettingsPart.Wifi),
    ReplaceStatusIcon(SettingsPart.Appearance),
    /**
     * The signal card: one switch for the aggregate meter, then one per card.
     *
     * The two per-card rows are rows of THIS card rather than cards of their own.
     * They were separate cards, and both sheets resolved to the same set of settings -
     * two entries in the library that could only ever repeat each other. The ink is
     * one kind of thing (a row of level dots); which reading it shows is a property of
     * the row, not a second component.
     */
    Mobile(SettingsPart.Mobile),
    DataSim(SettingsPart.Mobile),
    /**
     * The out-of-ring rendering: the module's own stacked reading, or MagicOS' icon.
     *
     * Owned by the signal card rather than being a piece of its own, because it is a
     * property of where the signal is drawn, not a third reading. It sits directly under
     * the position row it depends on.
     */
    Stacked(SettingsPart.Mobile),
    MobileActivity(SettingsPart.Mobile),
    Sim1(SettingsPart.Mobile),
    Sim2(SettingsPart.Mobile),
    Value(SettingsPart.Battery),
    Percent(SettingsPart.Battery),
    /**
     * A charger puts the number in the ring's centre, over whatever the layout put there.
     *
     * Owned by the percentage: it is a rule about that reading rather than a third kind
     * of content. Resolved per frame, so nothing stored changes when the cable moves.
     */
    ChargingShowsValue(SettingsPart.Battery),
    Bolt(SettingsPart.Bolt),
    BoltAnimEnabled(SettingsPart.Bolt),
    BoltAnim(SettingsPart.Bolt),
    /**
     * Network type, and the ONE piece that has a position choice of its own.
     *
     * Every other piece is placed or released: add it to a third and it is drawn there,
     * do not and MagicOS keeps its own icon. There is no third option, which is why the old
     * "signal in ring / out of ring" switch is gone. The type is different - it is text with
     * no native counterpart to hand back - so "show it beside the ring" is a real choice
     * rather than a way of expressing the same thing twice.
     */
    Type(SettingsPart.Type),
    ShowOutType(SettingsPart.Type),
    Power(SettingsPart.Readouts),
    StackStyle(SettingsPart.Mobile),
    StackLabel(SettingsPart.Type),
    /**
     * The type steps aside while Wi-Fi is up.
     *
     * Owned by the type: it is a rule about when that label is worth drawing, not a
     * reading of its own. One switch for every arrangement - the label says what the
     * cellular radio is doing, and with Wi-Fi up it shares the bar with the arcs the
     * user is actually reading.
     */
    TypeHideOnWifi(SettingsPart.Type),
    RunningPower(SettingsPart.Readouts),
    Temperature(SettingsPart.Readouts),
    /** The temperature shows only while the charger is attached (#45). */
    TempChargingOnly(SettingsPart.Readouts),
    /**
     * The frame rate, in the same sheet as the two readouts.
     *
     * It is a reading like the wattage and the temperature, so it is switched on in the
     * same place; what differs is that it never shares their two-line strip. That strip
     * is sized to hold exactly those two, and a third line would scale all three down -
     * so a frame rate that has no band of its own is drawn in a view beside the strip.
     */
    Fps(SettingsPart.Readouts),
    /**
     * The battery current, in the same sheet as the other readouts.
     *
     * Placeable like the rest: it can live inside a band of the ring or the capsule, or
     * stand beside them in the strip, and the unit and sign rows below it are what a
     * number of milliamps needs to be readable.
     */
    Current(SettingsPart.Readouts),
    CurrentUnit(SettingsPart.Readouts),
    CurrentPositive(SettingsPart.Readouts),
    Voltage(SettingsPart.Readouts),
    PerfSignal(SettingsPart.Readouts),
    BtBatteryMode(SettingsPart.Readouts),
    Clock(SettingsPart.Clock),
    HideStatusClock(SettingsPart.Clock),
    ClockLunar(SettingsPart.Clock),
    ClockSeconds(SettingsPart.Clock),
    ClockDate(SettingsPart.Clock),
    ClockDateFormat(SettingsPart.Clock),
    ClockWeekday(SettingsPart.Clock),
    ClockWeekdayFormat(SettingsPart.Clock),
    ClockWeather(SettingsPart.Clock),
    ClockWeatherStyle(SettingsPart.Clock),
    ClockStyle(SettingsPart.Clock),
    HideAlarmIcon(SettingsPart.Appearance),
    HideBluetoothIcon(SettingsPart.Appearance),
    ShadeHidePower(SettingsPart.Shade),
    CcSliderLongPress(SettingsPart.Shade),
    ShadeHideBell(SettingsPart.Shade),
    ShadeHideChargeNotif(SettingsPart.Shade),
    ShadeHideHotspotNotif(SettingsPart.Shade),
    ShadeHideAdbNotif(SettingsPart.Shade),
    HideHdVolte(SettingsPart.Shade),
    HideHdVowifi(SettingsPart.Shade),
    ShadeLunar(SettingsPart.Shade),
    ShadeWeather(SettingsPart.Shade),
    ShadeWeatherStyle(SettingsPart.Shade),
    EffectiveScope(SettingsPart.Appearance),
}

internal fun contentControlsFor(part: SettingsPart): List<ContentControl> =
    ContentControl.entries.filter {
        val matchesOwner = it.owner == part || (part == SettingsPart.Battery && it == ContentControl.Bolt)
        matchesOwner && !(part == SettingsPart.Appearance &&
            (it == ContentControl.Enabled || it == ContentControl.MergeIcons || it == ContentControl.Style || it == ContentControl.AntiBurnIn || it == ContentControl.EffectiveScope))
    }

@Composable
internal fun ContentControlRow(
    control: ContentControl,
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    ringSettings: ModuleSettings = settings,
) {
    val a = StatusAppearance.of(settings)
    val master = listOf(a.glyph to R.string.master_title)

    val positioned = when (control) {
        ContentControl.Value -> IconZones.Part.VALUE
        ContentControl.Bolt -> IconZones.Part.BOLT
        ContentControl.Type -> IconZones.Part.TYPE
        ContentControl.Clock -> IconZones.Part.CLOCK
        ContentControl.Fps -> IconZones.Part.FPS
        ContentControl.Power -> IconZones.Part.POWER
        ContentControl.RunningPower -> IconZones.Part.RUNNING
        ContentControl.Temperature -> IconZones.Part.TEMP
        ContentControl.Current -> IconZones.Part.CURRENT
        ContentControl.Voltage -> IconZones.Part.VOLTAGE
        ContentControl.PerfSignal -> IconZones.Part.SIGNAL
        ContentControl.BtBatteryMode -> IconZones.Part.BT
        else -> null
    }
    if (control == ContentControl.Type) {
        TypePositionControl(settings, update, zonesAllowed = a.slotted)
        return
    }
    val isPartActive = when (control) {
        ContentControl.Power -> settings.perfPower
        ContentControl.RunningPower -> settings.perfRunning
        ContentControl.Temperature -> settings.perfTemp
        ContentControl.Fps -> settings.perfFps
        ContentControl.Current -> settings.perfCurrent
        ContentControl.Voltage -> settings.perfVoltage
        ContentControl.PerfSignal -> settings.perfSignal
        ContentControl.BtBatteryMode -> settings.btBatteryMode > 0
        ContentControl.Value -> settings.showValue
        ContentControl.Bolt -> settings.showBolt
        ContentControl.Clock -> true
        else -> true
    }
    if (control == ContentControl.Mobile) {
        SignalPositionControl(settings, update)
    }
    if (control == ContentControl.Sim1 && a.slotted) {
        SignalCardPositionControl(IconZones.Part.SIM_1, settings, update, visible = signalInsideRing(a))
    }
    if (control == ContentControl.Sim2 && a.slotted) {
        SignalCardPositionControl(IconZones.Part.SIM_2, settings, update, visible = signalInsideRing(a))
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
        ContentControl.SignalColumns -> {
            val columns = listOf(4, 5)
            ContentChoice(
                selected = columns.indexOf(settings.signalColumns).coerceAtLeast(0),
                title = R.string.signal_columns_title,
                summaryRes = R.string.signal_columns_summary,
                options = listOf(R.string.signal_columns_4, R.string.signal_columns_5),
                dependencies = emptyList(),
            ) { index -> update { it.setSignalColumns(columns[index]) } }
        }
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
        ContentControl.ShowOutType -> if (!a.typeInRing) {
            ContentToggle(
                checked = settings.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING,
                title = R.string.show_out_type_title,
                summaryRes = R.string.show_out_type_summary,
                dependencies = listOf(settings.enabled to R.string.master_title),
            ) { value ->
                update {
                    it.setMobileTypeMode(if (value) Prefs.MOBILE_TYPE_OUT_RING else Prefs.MOBILE_TYPE_OFF)
                }
            }
        } else Unit
        ContentControl.ClockWeather -> ContentToggle(
            checked = settings.clockWeather,
            title = R.string.clock_weather_title,
            summaryRes = R.string.clock_weather_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setClockWeather(value) } }
        ContentControl.ClockWeatherStyle -> if (settings.clockWeather) {
            ContentChoice(
                selected = settings.clockWeatherStyle.coerceIn(0, Prefs.WEATHER_STYLE_COUNT - 1),
                title = R.string.clock_weather_style_title,
                summaryRes = null,
                options = listOf(
                    R.string.weather_style_text,
                    R.string.weather_style_icon,
                    R.string.weather_style_icon_text,
                    R.string.weather_style_symbol,
                    R.string.weather_style_symbol_text,
                ),
                dependencies = listOf(settings.clockWeather to R.string.clock_weather_title),
            ) { value -> update { it.setClockWeatherStyle(value) } }
        } else Unit
        ContentControl.ClockStyle -> ContentChoice(
            selected = settings.clockStyle.coerceIn(0, 1),
            title = R.string.clock_layout_style_title,
            summaryRes = null,
            options = listOf(R.string.clock_layout_style_classic, R.string.clock_layout_style_balanced),
            dependencies = emptyList(),
        ) { value -> update { it.setClockStyle(value) } }
        ContentControl.HideAlarmIcon -> ContentToggle(
            checked = settings.hideAlarmIcon,
            title = R.string.hide_alarm_icon_title,
            summaryRes = R.string.hide_alarm_icon_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setHideAlarmIcon(value) } }
        ContentControl.HideBluetoothIcon -> ContentToggle(
            checked = settings.hideBluetoothIcon,
            title = R.string.hide_bluetooth_icon_title,
            summaryRes = R.string.hide_bluetooth_icon_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setHideBluetoothIcon(value) } }
        ContentControl.ShadeHidePower -> ContentToggle(
            checked = settings.shadeHidePower,
            title = R.string.shade_hide_power_title,
            summaryRes = R.string.shade_hide_power_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeHidePower(value) } }
        ContentControl.ShadeHideBell -> ContentToggle(
            checked = settings.shadeHideBell,
            title = R.string.shade_hide_bell_title,
            summaryRes = R.string.shade_hide_bell_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeHideBell(value) } }
        ContentControl.ShadeHideChargeNotif -> ContentToggle(
            checked = settings.shadeHideChargeNotif,
            title = R.string.shade_hide_charge_notif_title,
            summaryRes = R.string.shade_hide_charge_notif_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeHideChargeNotif(value) } }
        ContentControl.ShadeHideHotspotNotif -> ContentToggle(
            checked = settings.shadeHideHotspotNotif,
            title = R.string.shade_hide_hotspot_notif_title,
            summaryRes = R.string.shade_hide_hotspot_notif_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeHideHotspotNotif(value) } }
        ContentControl.ShadeHideAdbNotif -> ContentToggle(
            checked = settings.shadeHideAdbNotif,
            title = R.string.shade_hide_adb_notif_title,
            summaryRes = R.string.shade_hide_adb_notif_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeHideAdbNotif(value) } }
        ContentControl.ShadeLunar -> ContentToggle(
            checked = settings.shadeLunar,
            title = R.string.shade_lunar_title,
            summaryRes = R.string.shade_lunar_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeLunar(value) } }
        ContentControl.ShadeWeather -> ContentToggle(
            checked = settings.shadeWeather,
            title = R.string.shade_weather_title,
            summaryRes = R.string.shade_weather_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setShadeWeather(value) } }
        ContentControl.ShadeWeatherStyle -> if (settings.shadeWeather) {
            ContentChoice(
                selected = settings.shadeWeatherStyle.coerceIn(0, Prefs.WEATHER_STYLE_COUNT - 1),
                title = R.string.clock_weather_style_title,
                summaryRes = null,
                options = listOf(
                    R.string.weather_style_text,
                    R.string.weather_style_icon,
                    R.string.weather_style_icon_text,
                    R.string.weather_style_symbol,
                    R.string.weather_style_symbol_text,
                ),
                dependencies = listOf(settings.shadeWeather to R.string.shade_weather_title),
            ) { value -> update { it.setShadeWeatherStyle(value) } }
        } else Unit
        ContentControl.Power -> ContentToggle(settings.perfPower, R.string.perf_power_title, R.string.perf_reading_common_summary, emptyList()) { value -> update { it.setPerfPower(value) } }
        ContentControl.RunningPower -> ContentToggle(checked = settings.perfRunning, title = R.string.perf_running_title, summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList()) { value -> update { it.setPerfRunning(value) } }
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
        ContentControl.Temperature -> ContentToggle(settings.perfTemp, R.string.perf_temp_title, R.string.perf_reading_common_summary, emptyList()) { value -> update { it.setPerfTemp(value) } }
        ContentControl.Fps -> ContentToggle(checked = settings.perfFps, title = R.string.perf_fps_title, summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList()) { value -> update { it.setPerfFps(value) } }
        ContentControl.Current -> ContentToggle(
            checked = settings.perfCurrent,
            title = R.string.perf_current_title,
            summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setPerfCurrent(value) } }
        ContentControl.CurrentUnit -> ContentChoice(
            settings.currentUnit, R.string.current_unit_title, R.string.current_unit_summary,
            listOf(R.string.current_unit_ma, R.string.current_unit_a),
            listOf(settings.perfCurrent to R.string.perf_current_title),
        ) { value -> update { it.setCurrentUnit(value) } }
        ContentControl.CurrentPositive -> ContentToggle(
            checked = settings.currentPositive,
            title = R.string.current_positive_title,
            summaryRes = R.string.current_positive_summary,
            dependencies = listOf(settings.perfCurrent to R.string.perf_current_title),
        ) { value -> update { it.setCurrentPositive(value) } }
        ContentControl.Voltage -> ContentToggle(
            checked = settings.perfVoltage,
            title = R.string.perf_voltage_title,
            summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setPerfVoltage(value) } }
        ContentControl.Clock -> Unit
        ContentControl.HideStatusClock -> ContentToggle(
            checked = settings.hideStatusClock,
            title = R.string.hide_status_clock_title,
            summaryRes = R.string.hide_status_clock_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setHideStatusClock(value) } }
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
        ContentControl.PerfSignal -> ContentToggle(
            checked = settings.perfSignal,
            title = R.string.perf_signal_title,
            summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList(),
        ) { value -> update { it.setPerfSignal(value) } }
        ContentControl.BtBatteryMode -> ContentToggle(
            checked = settings.btBatteryMode > 0,
            title = R.string.bt_battery_title,
            summaryRes = R.string.perf_reading_common_summary,
            dependencies = emptyList(),
        ) { on ->
            update {
                it.setBtBatteryMode(if (on) Prefs.BT_BATTERY_ARC else Prefs.BT_BATTERY_OFF)
            }
        }
        ContentControl.HideHdVolte -> ContentToggle(
            checked = settings.hideHdVolte,
            title = R.string.hide_hd_volte_title,
            summaryRes = R.string.hide_hd_volte_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setHideHdVolte(value) } }
        ContentControl.HideHdVowifi -> ContentToggle(
            checked = settings.hideHdVowifi,
            title = R.string.hide_hd_vowifi_title,
            summaryRes = R.string.hide_hd_vowifi_summary,
            dependencies = listOf(settings.enabled to R.string.master_module_title),
        ) { value -> update { it.setHideHdVowifi(value) } }
        ContentControl.CcSliderLongPress -> ContentToggle(
            checked = settings.ccSliderLongPress,
            title = R.string.cc_slider_long_press_title,
            summaryRes = R.string.cc_slider_long_press_summary,
            dependencies = listOf(settings.enabled to R.string.master_title),
        ) { value -> update { it.setCcSliderLongPress(value) } }
        ContentControl.EffectiveScope -> ContentMultiChoice(
            selected = scopeActiveSet(settings.effectiveScope),
            title = R.string.effective_scope_title,
            options = listOf(
                R.string.scope_option_cc,
                R.string.scope_option_shade,
                R.string.scope_option_keyguard,
            ),
            dependencies = listOf(settings.enabled to R.string.master_title),
            minimumSelected = 1,
        ) { next -> update { it.setEffectiveScope(scopeMaskOf(next)) } }

    }
    // 开关渲染完毕后，若该组件处于开启状态，紧接着渲染其对应的环内/胶囊位置选择下拉
    if ((a.ring || a.capsule) && positioned != null && !a.rect && isPartActive) {
        RingPositionControl(positioned, ringSettings, update)
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
    settings: ModuleSettings,
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
 * them. The rectangle cannot, and [StatusAppearance.slotted] is what the callers gate on, so
 * this only has to answer the shared part.
 */
private fun signalInsideRing(a: StatusAppearance): Boolean =
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
 * row there - see [IconZones.isSignalRow] and the reason in `Prefs.zoneAccepts`. That is not
 * the other card taking it: it is a band the row has no business in, so it is blocked
 * whatever the twin is doing.
 */
@Composable
private fun SignalCardPositionControl(
    part: IconZones.Part,
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    visible: Boolean,
) {
    if (!visible) return
    val id = IconZones.encodePart(part)
    val twin = if (part == IconZones.Part.SIM_1) IconZones.Part.SIM_2 else IconZones.Part.SIM_1
    val twinId = IconZones.encodePart(twin)
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) }
    val takenByTwin = zones.indexOfFirst { it.contains(twinId) }

    val targetZones = listOf(
        IconZones.Zone.LEFT,
        IconZones.Zone.TOP,
        IconZones.Zone.CENTRE,
        IconZones.Zone.BOTTOM,
        IconZones.Zone.RIGHT,
    )
    val selected = if (placedAt >= 0) {
        val currentZone = IconZones.Zone.entries[placedAt]
        targetZones.indexOf(currentZone).let { if (it >= 0) it + 1 else 0 }
    } else 0

    val blocked = buildSet {
        add(targetZones.indexOf(IconZones.Zone.CENTRE) + 1)
        if (takenByTwin >= 0) {
            val twinZone = IconZones.Zone.entries[takenByTwin]
            val twinOption = targetZones.indexOf(twinZone)
            if (twinOption >= 0) add(twinOption + 1)
        }
    }
    ContentChoice(
        selected = selected,
        title = if (part == IconZones.Part.SIM_1) R.string.ring_position_sim_1 else R.string.ring_position_sim_2,
        summaryRes = null,
        options = listOf(
            R.string.ring_position_native,
            R.string.drawing_hint_left,
            R.string.drawing_hint_top,
            R.string.drawing_hint_centre,
            R.string.drawing_hint_bottom,
            R.string.drawing_hint_right,
        ),
        dependencies = listOf(settings.enabled to R.string.master_title),
        blockedIndices = blocked,
        onChange = { value ->
            val targetZone = if (value in 1..targetZones.size) targetZones[value - 1] else null
            update { it.setRingPartZone(part, targetZone) }
        },
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
    settings: ModuleSettings,
    ringSettings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = IconZones.encodePart(IconZones.Part.WIFI)
    val placedAt = ringSettings.ringZones.indexOfFirst { it.contains(id) }
    val targetZones = listOf(
        IconZones.Zone.LEFT,
        IconZones.Zone.TOP,
        IconZones.Zone.CENTRE,
        IconZones.Zone.BOTTOM,
        IconZones.Zone.RIGHT,
    )
    val selected = if (!settings.showWifi || placedAt < 0) 0 else {
        val currentZone = IconZones.Zone.entries[placedAt]
        targetZones.indexOf(currentZone).let { if (it >= 0) it + 1 else 0 }
    }
    ContentChoice(
        selected = selected,
        title = R.string.ring_position_wifi,
        summaryRes = null,
        options = listOf(
            R.string.ring_position_native,
            R.string.drawing_hint_left,
            R.string.drawing_hint_top,
            R.string.drawing_hint_centre,
            R.string.drawing_hint_bottom,
            R.string.drawing_hint_right,
        ),
        dependencies = listOf(settings.enabled to R.string.master_title),
        onChange = { value ->
            val targetZone = if (value in 1..targetZones.size) targetZones[value - 1] else null
            update { it.setWifiZone(targetZone) }
        },
    )
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
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    zonesAllowed: Boolean,
) {
    val id = IconZones.encodePart(IconZones.Part.TYPE)
    val placedAt = settings.ringZones.indexOfFirst { it.contains(id) } + 1
    val selected = if (placedAt in 1..3) placedAt else 0
    ContentChoice(
        selected = selected,
        title = R.string.ring_position_type,
        summaryRes = null,
        options = listOf(
            R.string.mobile_type_outside,
            R.string.drawing_hint_top,
            R.string.drawing_hint_centre,
            R.string.drawing_hint_bottom,
        ),
        dependencies = listOf(settings.enabled to R.string.master_title),
        blockedIndices = if (zonesAllowed) emptySet() else setOf(1, 2, 3),
    ) { value ->
        update {
            when (value) {
                0 -> {
                    it.setRingPartPosition(IconZones.Part.TYPE, 0)
                    if (settings.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING) {
                        it.setMobileTypeMode(Prefs.MOBILE_TYPE_OUT_RING)
                    }
                }
                else -> {
                    it.setRingPartPosition(IconZones.Part.TYPE, value)
                    it.setMobileTypeMode(Prefs.MOBILE_TYPE_IN_RING)
                }
            }
        }
    }
}

private fun middleBlocked(part: IconZones.Part): Set<Int> =
    if (IconZones.isSignalRow(part)) setOf(IconZones.Zone.CENTRE.ordinal + 1) else emptySet()

@Composable
private fun RingPositionControl(
    part: IconZones.Part,
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = IconZones.encodePart(part)
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) }
    // Every piece may sit on a flank now: the ring cuts a mouth for whatever is there.
    val targetZones = listOf(
        IconZones.Zone.LEFT,
        IconZones.Zone.TOP,
        IconZones.Zone.CENTRE,
        IconZones.Zone.BOTTOM,
        IconZones.Zone.RIGHT,
    )

    val selected = if (part == IconZones.Part.TYPE && settings.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING) {
        targetZones.size + 1
    } else if (placedAt >= 0) {
        val currentZone = IconZones.Zone.entries[placedAt]
        targetZones.indexOf(currentZone).let { if (it >= 0) it + 1 else 0 }
    } else 0

    val options = listOf(R.string.ring_position_native) +
        targetZones.map { zone -> zoneLabelRes(zone) } +
        if (part == IconZones.Part.TYPE) listOf(R.string.mobile_type_outside) else emptyList()

    val blocked = buildSet {
        if (IconZones.isSignalRow(part)) {
            val centreIdx = targetZones.indexOf(IconZones.Zone.CENTRE)
            if (centreIdx >= 0) add(centreIdx + 1)
        }
    }

    val title = when (part) {
        IconZones.Part.WIFI -> R.string.ring_position_wifi
        IconZones.Part.MOBILE -> R.string.ring_position_mobile
        IconZones.Part.SIM_1 -> R.string.ring_position_sim_1
        IconZones.Part.SIM_2 -> R.string.ring_position_sim_2
        IconZones.Part.VALUE -> R.string.ring_position_value
        IconZones.Part.BOLT -> R.string.ring_position_bolt
        IconZones.Part.TYPE -> R.string.ring_position_type
        IconZones.Part.CLOCK -> R.string.ring_position_clock
        IconZones.Part.POWER -> R.string.ring_position_power
        IconZones.Part.TEMP -> R.string.ring_position_temp
        IconZones.Part.RUNNING -> R.string.ring_position_running
        IconZones.Part.FPS -> R.string.ring_position_fps
        IconZones.Part.CURRENT -> R.string.ring_position_current
        IconZones.Part.VOLTAGE -> R.string.ring_position_voltage
        IconZones.Part.SIGNAL -> R.string.ring_position_signal
        IconZones.Part.BT -> R.string.ring_position_bt
    }
    ContentChoice(
        selected = selected,
        title = title,
        summaryRes = null,
        options = options,
        dependencies = listOf(settings.enabled to R.string.master_title),
        blockedIndices = blocked,
    ) { value ->
        if (part == IconZones.Part.TYPE && value == targetZones.size + 1) {
            update {
                it.setRingPartZone(IconZones.Part.TYPE, null)
                it.setMobileTypeMode(Prefs.MOBILE_TYPE_OUT_RING)
            }
        } else {
            val targetZone = if (value in 1..targetZones.size) targetZones[value - 1] else null
            update { it.setRingPartZone(part, targetZone) }
        }
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
/**
 * The scopes the module is active in, as option indices.
 */
private fun scopeActiveSet(mask: Int): Set<Int> = buildSet {
    if ((mask and Prefs.SCOPE_BIT_CC) == 0) add(0)
    if ((mask and Prefs.SCOPE_BIT_SHADE) == 0) add(1)
    if ((mask and Prefs.SCOPE_BIT_KEYGUARD) == 0) add(2)
}

private fun scopeMaskOf(active: Set<Int>): Int {
    var mask = 0
    if (0 !in active) mask = mask or Prefs.SCOPE_BIT_CC
    if (1 !in active) mask = mask or Prefs.SCOPE_BIT_SHADE
    if (2 !in active) mask = mask or Prefs.SCOPE_BIT_KEYGUARD
    return mask
}

/**
 * Miuix-style multi-select dropdown using GlassMultiDropdownPreference with Checkbox on the left.
 *
 * @param minimumSelected how many entries must stay checked. The scope row is the one
 *   caller that sets it: "nothing checked" would mean the module shows nowhere, which is
 *   not a place the user can pick a scope for and reads as an error rather than a choice.
 *   So the last checked entry cannot be cleared, and the row always states a scope.
 */
@Composable
private fun ContentMultiChoice(
    selected: Set<Int>,
    title: Int,
    options: List<Int>,
    dependencies: List<Pair<Boolean, Int>>,
    minimumSelected: Int = 0,
    onChange: (Set<Int>) -> Unit,
) {
    ContentGate(dependencies) {
        val labels = options.map { stringResource(it) }
        val summary = if (selected.size >= options.size) {
            stringResource(R.string.scope_all)
        } else {
            options.indices.filter { it in selected }.joinToString(stringResource(R.string.scope_list_separator)) {
                labels[it]
            }
        }
        GlassMultiDropdownPreference(
            title = stringResource(title),
            options = labels,
            selectedIndices = selected,
            onToggleIndex = { index ->
                val next = selected.toMutableSet()
                if (!next.remove(index)) next.add(index)
                if (next.size >= minimumSelected) onChange(next)
            },
            summary = summary,
            insideMargin = SettingsItemMargin,
            enabled = dependencies.all { it.first },
        )
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
