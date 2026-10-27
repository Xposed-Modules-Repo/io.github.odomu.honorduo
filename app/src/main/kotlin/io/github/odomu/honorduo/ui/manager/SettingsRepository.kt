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

package io.github.odomu.honorduo.ui.manager
import android.content.Context

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.odomu.honorduo.HonorDuoApp
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.render.IconZones

/**
 * Reads and writes every setting, and mirrors each write to the Xposed
 * framework so the hooked SystemUI process sees it immediately.
 *
 * Two stores are involved and only one of them is authoritative for the
 * hooked process:
 *
 *   - the app's own [SharedPreferences] file, which drives this UI and
 *       survives while the framework is absent;
 *   - the framework's remote-preference group of the same name, which is what
 *       the hooked process actually reads.
 *
 * The mirror is therefore not "sync on connect and forget": every single
 * write is pushed, otherwise a value the user changes while the service is
 * bound would never reach SystemUI.
 */
class SettingsRepository(val context: Context) {

    private val appContext = context.applicationContext
    private val service: XposedService? get() = HonorDuoApp.xposedService

    /** The display density, needed to migrate the retired raw-pixel size key. */
    private val density: Float
        get() = appContext.resources.displayMetrics.density

    private fun preferences() =
        appContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    /** The current values, as the preview should render them. */
    fun read(): ModuleSettings = ModuleSettings.from(preferences(), density)

    /**
     * The snapshot for a style the user is *not* currently in.
     *
     * The style tiles need this: each arrangement keeps its own component switches and
     * layout now, so showing the current one under both names would advertise a look that
     * picking that tile does not produce.
     *
     * A pure read of the other arrangement's keys - the stored style is not touched, so
     * nothing observes a transient state and no write is pushed to the hooked process.
     */
    fun readForStyle(style: Int): ModuleSettings =
        ModuleSettings.from(preferences(), density, Prefs.normalizeTrioStyle(style))

    fun setEnabled(value: Boolean) = writeBoolean(Prefs.KEY_ENABLED, value)
    fun setMergeIcons(value: Boolean) = writeBoolean(Prefs.KEY_MERGE_ICONS, value)
    fun setShowWifi(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_WIFI) { e, k ->
        e.putBoolean(k, value) }
    fun setShowMobile(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_MOBILE) { e, k ->
        e.putBoolean(k, value) }
    fun setShowValue(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_VALUE) { e, k ->
        e.putBoolean(k, value) }
    fun setShowBolt(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_BOLT) { e, k ->
        e.putBoolean(k, value) }
    fun setDualSim(value: Boolean) = writeStyled(style(), Prefs.KEY_DUAL_SIM) { e, k ->
        e.putBoolean(k, value) }
    /** SIM 1's own row of dots, placed as a component - see [Prefs.KEY_SHOW_SIM_1]. */
    fun setShowSim1(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_SIM_1) { e, k ->
        e.putBoolean(k, value) }
    /** SIM 2's own row of dots, placed as a component - see [Prefs.KEY_SHOW_SIM_2]. */
    fun setShowSim2(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_SIM_2) { e, k ->
        e.putBoolean(k, value) }
    fun setValueCentred(value: Boolean) = writeStyled(style(), Prefs.KEY_VALUE_CENTRED) { e, k ->
        e.putBoolean(k, value) }
    /** 0 = hidden, 1 = inside the ring, 2 = outside it. */
    fun setMobileTypeMode(value: Int) = writeStyled(style(), Prefs.KEY_MOBILE_TYPE_MODE) { e, k ->
        e.putInt(k, value) }
    /** Stored IDs stay 0 = ring and 2 = capsule; retired ID 1 returns to the ring. */
    fun setTrioStyle(value: Int) = writeInt(Prefs.KEY_TRIO_STYLE, Prefs.normalizeTrioStyle(value))
    fun setNavStyle(value: Int) = writeInt(Prefs.KEY_NAV_STYLE, Prefs.normalizeNavStyle(value))
    fun setNavLabels(value: Int) = writeInt(Prefs.KEY_NAV_LABELS, Prefs.normalizeNavLabels(value))

    /**
     * The arrangement the user is currently looking at.
     *
     * Every component switch is written under *this* style's key, so adjusting the
     * capsule cannot reach into the ring's arrangement - see [Prefs.STYLED_KEYS]. Read fresh
     * from storage rather than cached, because the style is itself a setting and a stale copy
     * would write one arrangement's value into the other.
     */
    private fun style(): Int = Prefs.normalizeTrioStyle(preferences()
        .getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE))

    /**
     * Writes one per-arrangement value, to this style's own key.
     *
     * [block] receives the resolved key rather than the base one: the whole point is that
     * the caller does not decide which arrangement it is writing.
     */
    private fun writeStyled(
        style: Int,
        base: String,
        block: (SharedPreferences.Editor, String) -> Unit,
    ) {
        val key = Prefs.styledKey(base, style)
        preferences().edit(commit = true) { block(this, key) }
        push { block(it, key) }
        notifyModule()
    }

    /** Publish one layout edit as a single local/remote transaction and one broadcast. */
    fun applyRingAssignment(next: ModuleSettings) {
        // The arrangement being written is the snapshot's own, not the stored one: the caller
        // built `next` for a specific style, and a layout written under the wrong key would
        // show up in the other arrangement on the next style switch.
        val style = Prefs.normalizeTrioStyle(next.trioStyle)
        val write: (SharedPreferences.Editor) -> Unit = { e ->
            e.putBoolean(Prefs.KEY_ENABLED, next.enabled)
            e.putBoolean(Prefs.KEY_MERGE_ICONS, next.mergeIcons)
            e.putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style),
                Prefs.encodeLayout(next.ringZones))
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, style), next.showWifi)
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, style), next.showMobile)
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), next.showValue)
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, style), next.showBolt)
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, style), next.showSim1)
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, style), next.showSim2)
            e.putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style), next.mobileTypeMode)
            e.putInt(Prefs.styledKey(Prefs.KEY_SIGNAL_MODE, style), next.signalMode)
        }
        preferences().edit {
            write(this)
        }
        push(write)
        notifyModule()
    }

    /**
     * Put a piece at the front of a zone's priority list, or take it out of the layout.
     *
     * Mirrors the old "position" control: 1..3 name a ring zone and 0 (or the external
     * type slot) removes the piece. The three ints are still the three zones, which is why
     * the mapping is kept - the ring's editor drives the priority lists directly.
     */
    fun setRingPartZone(part: IconZones.Part, zone: IconZones.Zone?) {
        val current = ModuleSettings.from(preferences(), density)
        if (zone != null) {
            applyRingAssignment(
                IconZones.withPiece(current, zone.ordinal, part)
            )
        } else {
            val next = current.copy()
            val id = IconZones.encodePart(part)
            next.ringZones = ModuleSettings.copyZones(current.ringZones)
            for (z in next.ringZones.indices) {
                next.ringZones[z] = next.ringZones[z].filter { it != id }.toIntArray()
            }
            if (part == IconZones.Part.TYPE && current.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING) {
                next.mobileTypeMode = Prefs.MOBILE_TYPE_OFF
            }
            applyRingAssignment(next)
        }
    }

    fun setRingPartPosition(part: IconZones.Part, position: Int) {
        if (position in 1..Prefs.ZONE_COUNT) {
            setRingPartZone(part, IconZones.Zone.entries[position - 1])
        } else {
            setRingPartZone(part, null)
        }
    }
    /**
     * Wi-Fi's switch and ring slot as one decision: `0` turns the arcs off and takes
     * them out of the layout, `1..3` turn them on and put them in that third.
     *
     * One call rather than two setters, because the pair has to move together: between a
     * "switch off" write and a "remove from layout" write the module would be asked to draw
     * arcs it has no slot for, or hold a slot it will not fill.
     */
    fun setWifiZone(zone: IconZones.Zone?) {
        val current = ModuleSettings.from(preferences(), density)
        if (zone != null) {
            val next = IconZones.withPiece(current, zone.ordinal, IconZones.Part.WIFI)
            next.showWifi = true
            applyRingAssignment(next)
        } else {
            val next = current.copy()
            next.showWifi = false
            val id = IconZones.encodePart(IconZones.Part.WIFI)
            next.ringZones = ModuleSettings.copyZones(current.ringZones)
            for (z in next.ringZones.indices) {
                next.ringZones[z] = next.ringZones[z].filter { it != id }.toIntArray()
            }
            applyRingAssignment(next)
        }
    }

    fun setWifiPosition(position: Int) {
        if (position in 1..Prefs.ZONE_COUNT) {
            setWifiZone(IconZones.Zone.entries[position - 1])
        } else {
            setWifiZone(null)
        }
    }

    
    fun setSignalMode(value: Int) = writeStyled(style(), Prefs.KEY_SIGNAL_MODE) { e, k ->
        e.putInt(k, value.coerceIn(Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE)) }

    /** Out of ring, draw the module's own reading rather than handing the icon back. */
    fun setStackedSignal(value: Boolean) = writeStyled(style(), Prefs.KEY_STACKED_SIGNAL) { e, k ->
        e.putBoolean(k, value) }
    fun setShowMobileActivity(value: Boolean) = writeStyled(style(), Prefs.KEY_SHOW_MOBILE_ACTIVITY) { e, k ->
        e.putBoolean(k, value) }

    fun setDataSimOnly(value: Boolean) = writeBoolean(Prefs.KEY_DATA_SIM_ONLY, value)

    /** Percent sign after the in-glyph battery number - see [Prefs.KEY_SHOW_PERCENT]. */
    fun setShowPercent(value: Boolean) = writeBoolean(Prefs.KEY_SHOW_PERCENT, value)

    /** A charger puts the percentage in the ring's centre - see [Prefs.KEY_CHARGING_SHOWS_VALUE]. */
    fun setChargingShowsValue(value: Boolean) = writeBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE, value)

    /** The type steps aside while Wi-Fi is up - see [Prefs.KEY_TYPE_HIDE_ON_WIFI]. */
    fun setTypeHideOnWifi(value: Boolean) = writeBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, value)

    /** The temperature rides with the charger - see [Prefs.KEY_PERF_TEMP_CHARGING_ONLY]. */
    /** Wi-Fi arc size, in percent - see [Prefs.KEY_WIFI_SCALE]. */
    fun setWifiScale(value: Int) = writeInt(Prefs.KEY_WIFI_SCALE, value)

    /** The capsule's extra straight run - see [Prefs.KEY_CAPSULE_LENGTH]. */
    fun setCapsuleLength(value: Int) = writeInt(Prefs.KEY_CAPSULE_LENGTH, value)
    fun setCapsuleLeftDx(value: Int) = writeInt(Prefs.KEY_CAPSULE_LEFT_DX, value)
    fun setCapsuleLeftDy(value: Int) = writeInt(Prefs.KEY_CAPSULE_LEFT_DY, value)
    fun setCapsuleRightDx(value: Int) = writeInt(Prefs.KEY_CAPSULE_RIGHT_DX, value)
    fun setCapsuleRightDy(value: Int) = writeInt(Prefs.KEY_CAPSULE_RIGHT_DY, value)
    fun setRingSpacingX(value: Int) = writeInt(Prefs.KEY_RING_SPACING_X, value)
    fun setRingSpacingY(value: Int) = writeInt(Prefs.KEY_RING_SPACING_Y, value)
    fun setReplaceWifiIcon(value: Boolean) = writeBoolean(Prefs.KEY_REPLACE_WIFI_ICON, value)
    fun setReplaceStatusIcon(value: Boolean) = writeBoolean(Prefs.KEY_REPLACE_STATUS_ICON, value)
    fun setHideStatusClock(value: Boolean) = writeBoolean(Prefs.KEY_HIDE_STATUS_CLOCK, value)
    fun setHideAlarmIcon(value: Boolean) = writeBoolean(Prefs.KEY_HIDE_ALARM_ICON, value)
    fun setHideBluetoothIcon(value: Boolean) = writeBoolean(Prefs.KEY_HIDE_BLUETOOTH_ICON, value)

    fun setPerfTempChargingOnly(value: Boolean) =
        writeBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, value)

    /** Charging power in the out-of-ring strip - see [Prefs.KEY_PERF_POWER]. */
    fun setPerfPower(value: Boolean) {
        val current = read()
        if (value && !current.perfPower) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_POWER)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_POWER, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_POWER, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_POWER, value)
        }
    }
    /** Keep the power readout while unplugged - see [Prefs.KEY_PERF_RUNNING]. */
    fun setPerfRunning(value: Boolean) = writeBoolean(Prefs.KEY_PERF_RUNNING, value)
    /** Seconds in the drawn time - see [Prefs.KEY_CLOCK_SECONDS]. */
    fun setClockSeconds(value: Boolean) = writeBoolean(Prefs.KEY_CLOCK_SECONDS, value)
    /** Date line above the drawn time - see [Prefs.KEY_CLOCK_DATE]. */
    fun setClockDate(value: Boolean) = writeBoolean(Prefs.KEY_CLOCK_DATE, value)
    /** Weekday beside the date - see [Prefs.KEY_CLOCK_WEEKDAY]. */
    fun setClockWeekday(value: Boolean) = writeBoolean(Prefs.KEY_CLOCK_WEEKDAY, value)
    fun setClockTopOffsetX(value: Int) = writeInt(Prefs.KEY_CLOCK_TOP_OFFSET_X, value)
    fun setClockBottomOffsetX(value: Int) = writeInt(Prefs.KEY_CLOCK_BOTTOM_OFFSET_X, value)
    fun setClockLunarGap(value: Int) = writeInt(Prefs.KEY_CLOCK_LUNAR_GAP, value)
    fun setClockRowGap(value: Int) = writeInt(Prefs.KEY_CLOCK_ROW_GAP, value)
    fun setClockLunar(value: Boolean) = writeBoolean(Prefs.KEY_CLOCK_LUNAR, value)
    fun setClockDateFormat(value: Int) = writeInt(Prefs.KEY_CLOCK_DATE_FORMAT, value)
    fun setClockWeekdayFormat(value: Int) = writeInt(Prefs.KEY_CLOCK_WEEKDAY_FORMAT, value)
    fun setColorClockWeekdayOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, value)
    fun setColorClockWeekdayOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, value)
    fun setColorClockDateOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, value)
    fun setColorClockDateOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, value)
    fun setColorClockLunarOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, value)
    fun setColorClockLunarOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, value)
    fun setClockWeather(value: Boolean) = writeBoolean(Prefs.KEY_CLOCK_WEATHER, value)
    fun setClockWeatherStyle(value: Int) = writeInt(Prefs.KEY_CLOCK_WEATHER_STYLE, value)
    fun setClockStyle(value: Int) = writeInt(Prefs.KEY_CLOCK_STYLE, value)
    fun setShadeHidePower(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_HIDE_POWER, value)
    fun setShadeHideBell(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_HIDE_BELL, value)
    fun setShadeLunar(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_LUNAR, value)
    fun setShadeWeather(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_WEATHER, value)
    fun setShadeWeatherStyle(value: Int) = writeInt(Prefs.KEY_SHADE_WEATHER_STYLE, value)
    fun setEffectiveScope(value: Int) = writeInt(Prefs.KEY_EFFECTIVE_SCOPE, value)
    fun setCapsuleNoScale(value: Boolean) = writeBoolean(Prefs.KEY_CAPSULE_NO_SCALE, value)
    fun setHideHdVolte(value: Boolean) = writeBoolean(Prefs.KEY_HIDE_HD_VOLTE, value)
    fun setHideHdVowifi(value: Boolean) = writeBoolean(Prefs.KEY_HIDE_HD_VOWIFI, value)
    fun setCcSliderLongPress(value: Boolean) = writeBoolean(Prefs.KEY_CC_SLIDER_LONG_PRESS, value)
    fun setShadeHideChargeNotif(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF, value)
    fun setShadeHideHotspotNotif(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF, value)
    fun setShadeHideAdbNotif(value: Boolean) = writeBoolean(Prefs.KEY_SHADE_HIDE_ADB_NOTIF, value)
    fun setBtBatteryMode(value: Int) = writeInt(Prefs.KEY_BT_BATTERY_MODE, value)
    fun setShadeWeatherPosition(value: Int) = writeInt(Prefs.KEY_SHADE_WEATHER_POSITION, value)
    fun setShadeWeatherClick(value: Int) = writeInt(Prefs.KEY_SHADE_WEATHER_CLICK, value)
    fun setColorClockWeatherOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, value)
    fun setColorClockWeatherOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, value)
    /** Battery temperature in the out-of-ring strip - see [Prefs.KEY_PERF_TEMP]. */
    fun setPerfTemp(value: Boolean) {
        val current = read()
        if (value && !current.perfTemp) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_TEMP)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_TEMP, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_TEMP, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_TEMP, value)
        }
    }
    /** The screen's frame rate - see [Prefs.KEY_PERF_FPS]. */
    fun setPerfFps(value: Boolean) {
        val current = read()
        if (value && !current.perfFps) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_FPS)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_FPS, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_FPS, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_FPS, value)
        }
    }
    /** Overall size of the two readouts, in percent - see [Prefs.KEY_PERF_SCALE]. */
    fun setPerfScale(value: Int) = writeInt(Prefs.KEY_PERF_SCALE, value)
    /** Weight of the two readouts - see [Prefs.KEY_PERF_WEIGHT]. */
    fun setPerfWeight(value: Int) = writeInt(Prefs.KEY_PERF_WEIGHT, value)
    fun setPerfLineGap(value: Int) = writeInt(Prefs.KEY_PERF_LINE_GAP, value)

    /** Whether the battery current is drawn among the readouts. */
    fun setPerfCurrent(value: Boolean) {
        val current = read()
        if (value && !current.perfCurrent) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_CURRENT)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_CURRENT, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_CURRENT, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_CURRENT, value)
        }
    }

    /** Whether the battery voltage is drawn among the readouts. */
    fun setPerfVoltage(value: Boolean) {
        val current = read()
        if (value && !current.perfVoltage) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_VOLTAGE)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_VOLTAGE, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_VOLTAGE, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_VOLTAGE, value)
        }
    }

    /** Whether cellular signal strength is drawn among the readouts (item #6). */
    fun setPerfSignal(value: Boolean) {
        val current = read()
        if (value && !current.perfSignal) {
            val order = touchPerfOrder(current.perfOrder, current, Prefs.PERF_ITEM_SIGNAL)
            preferences().edit(commit = true) {
                putBoolean(Prefs.KEY_PERF_SIGNAL, value)
                putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            push {
                it.putBoolean(Prefs.KEY_PERF_SIGNAL, value)
                it.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(order))
            }
            notifyModule()
        } else {
            writeBoolean(Prefs.KEY_PERF_SIGNAL, value)
        }
    }

    /** Milliamps or amps for the current readout. */
    fun setCurrentUnit(value: Int) =
        writeInt(Prefs.KEY_CURRENT_UNIT, Prefs.clamp(value, Prefs.CURRENT_UNIT_MA, Prefs.CURRENT_UNIT_A))

    /** Draw the current as a magnitude whatever its direction. */
    fun setCurrentPositive(value: Boolean) = writeBoolean(Prefs.KEY_CURRENT_POSITIVE, value)

    /** How often the charging power and the temperature are re-read, in seconds. */
    fun setPerfRefreshSec(value: Int) = writeInt(Prefs.KEY_PERF_REFRESH_SEC, value)

    /**
     * Colours one character of the time.
     *
     * One list holds every character, so this is a read-modify-write of that list: the
     * row it belongs to is a per-glyph tap, not a single value.
     *
     * @param index the character's place in the drawn time
     * @param onDark which background's list to edit
     * @param argb the colour, or `0` to hand the character back to the clock's own ink
     */
    fun setClockDigitColor(index: Int, onDark: Boolean, argb: Int) {
        if (index < 0 || index >= Prefs.CLOCK_DIGIT_SLOTS) return
        val current = read()
        val colors = (if (onDark) current.clockDigitColorsOnDark else current.clockDigitColorsOnLight)
            .copyOf()
        colors[index] = argb
        writeString(
            if (onDark) Prefs.KEY_CLOCK_DIGIT_COLORS else Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT,
            Prefs.encodeClockDigitColors(colors),
        )
    }

    /** Hands every character of the time back to the clock's own ink. */
    fun clearClockDigitColors() {
        writeString(Prefs.KEY_CLOCK_DIGIT_COLORS, "")
        writeString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, "")
    }

    /** Size of the in-ring charging bolt, in percent - see [Prefs.KEY_BOLT_SCALE]. */
    fun setBoltScale(value: Int) = writeInt(Prefs.KEY_BOLT_SCALE, value)

    /** How the charging bolt moves - see [Prefs.KEY_BOLT_ANIM]. */
    fun setBoltAnim(value: Int) = writeInt(Prefs.KEY_BOLT_ANIM, value)

    /** Speed of the charging animation, in percent - see [Prefs.KEY_BOLT_SPEED]. */
    fun setBoltSpeed(value: Int) = writeInt(Prefs.KEY_BOLT_SPEED, value)

    /**
     * Size of the ring's level-dot rows, in percent - see [Prefs.KEY_DOT_SCALE].
     *
     * One setter for the top and the bottom rows: they are one piece of ink, and the
     * settings screen offers one row for them.
     */
    fun setDotScale(value: Int) = writeInt(Prefs.KEY_DOT_SCALE, value)

    /**
     * The out-of-ring order, nearest the battery first - see [Prefs.KEY_OUT_ORDER].
     *
     * One write for the whole arrangement rather than a "move this piece" verb: the strip
     * always has exactly three positions, so a swap is expressible as the resulting order
     * and there is no intermediate state another reader could observe.
     */
    fun setOutOrder(order: IntArray) =
        writeString(Prefs.KEY_OUT_ORDER, Prefs.encodeOutOrder(order))

    fun setRoleColors(value: Boolean) = writeBoolean(Prefs.KEY_ROLE_COLORS, value)
    fun setColorCriticalOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, value)
    fun setColorCriticalOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, value)
    fun setColorChargingOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, value)
    fun setColorChargingOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, value)
    fun setColorLowOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_DARK, value)
    fun setColorLowOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, value)

    /** Restores every role colour, the clock's three included, to its default. */
    fun resetRoleColors() {
        preferences().edit(commit = true) {
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK)
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK)
            putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            putString(Prefs.KEY_CLOCK_DIGIT_COLORS, "")
            putString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, "")
        }
        push { prefs ->
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            prefs.putString(Prefs.KEY_CLOCK_DIGIT_COLORS, "")
            prefs.putString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, "")
        }
        notifyModule()
    }

    fun setLowThreshold(value: Int) = writeInt(Prefs.KEY_LOW_THRESHOLD, value)
    fun setTempThreshold(value: Int) = writeInt(
        Prefs.KEY_TEMP_THRESHOLD,
        Prefs.clamp(value, Prefs.MIN_TEMP_THRESHOLD, Prefs.MAX_TEMP_THRESHOLD)
    )
    fun setColorHighTempOnDark(argb: Int) = writeInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, argb)
    fun setColorHighTempOnLight(argb: Int) = writeInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, argb)
    fun setRoleColorsTemp(value: Boolean) = writeBoolean(Prefs.KEY_ROLE_COLORS_TEMP, value)
    fun setRingStroke(value: Int) = writeInt(Prefs.KEY_RING_STROKE, value)
    fun setArcStroke(value: Int) = writeInt(Prefs.KEY_ARC_STROKE, value)
    fun setValueSize(value: Int) = writeInt(Prefs.KEY_VALUE_SIZE, value)
    fun setValueWeight(value: Int) = writeInt(Prefs.KEY_VALUE_WEIGHT, value)
    fun setClockSize(value: Int) = writeInt(Prefs.KEY_CLOCK_SIZE, value)
    fun setClockWeight(value: Int) = writeInt(Prefs.KEY_CLOCK_WEIGHT, value)
    fun setTypeSize(value: Int) = writeInt(Prefs.KEY_TYPE_SIZE, value)
    fun setOutTypeSize(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_SIZE_DP, value)
    fun setOutSignalSize(value: Int) = writeInt(Prefs.KEY_OUT_SIGNAL_SIZE, value)
    /** Dot-row size of the out-of-ring reading - see [Prefs.KEY_OUT_DOT_SIZE]. */
    fun setOutDotSize(value: Int) = writeInt(Prefs.KEY_OUT_DOT_SIZE, value)
    /** Stacked look of the out-of-ring reading - see [Prefs.KEY_STACK_STYLE]. */
    fun setStackStyle(value: Int) = writeInt(Prefs.KEY_STACK_STYLE, value)
    fun setAntiBurnIn(value: Boolean) = writeBoolean(Prefs.KEY_ANTI_BURN_IN, value)
    fun setSignalColumns(value: Int) = writeInt(Prefs.KEY_SIGNAL_COLUMNS, Prefs.clamp(value, 4, 5))
    /** Where the stacked reading puts its type - see [Prefs.KEY_STACK_LABEL]. */
    fun setStackLabel(value: Int) = writeInt(Prefs.KEY_STACK_LABEL, value)
    fun setOutTypeMarginLeft(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, value)
    fun setOutTypeMarginRight(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, value)
    fun setOutSignalMargin(value: Int) = writeInt(Prefs.KEY_OUT_SIGNAL_MARGIN, value)

    /** Gap between the capsule's cells - see [Prefs.KEY_CAP_GAP]. */
    fun setCapGap(value: Int) = writeInt(
        Prefs.KEY_CAP_GAP, value.coerceIn(Prefs.MIN_CAP_GAP, Prefs.MAX_CAP_GAP),
    )
    fun setTypeSuffixScale(value: Int) = writeInt(Prefs.KEY_TYPE_SUFFIX_SCALE, value)
    fun setTypeWeight(value: Int) = writeInt(Prefs.KEY_TYPE_WEIGHT, value)
    fun setTrackAlpha(value: Int) = writeInt(Prefs.KEY_TRACK_ALPHA, value)

    /** Overall size of the trio glyph, in percent - see [Prefs.KEY_GLYPH_SCALE]. */
    fun setGlyphScale(value: Int) = writeInt(Prefs.KEY_GLYPH_SCALE, value)
    fun setFontPath(value: String) = writeString(Prefs.KEY_FONT_PATH, value)
    fun setDebugLog(value: Boolean) = writeBoolean(Prefs.KEY_DEBUG_LOG, value)
    fun getDebugLog(): Boolean =
        preferences().getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG)

    /**
     * The viewer's own filter level, which must not touch the debug log switch:
     * picking "All" in the tabs means "show everything recorded", not "stop
     * recording". Only the record toggle writes [Prefs.KEY_DEBUG_LOG].
     */
    fun setLogFilter(value: Int) = writeInt(Prefs.KEY_LOG_LEVEL, value)
    fun getLogLevel(): Int = preferences().getInt(Prefs.KEY_LOG_LEVEL, Prefs.DEF_LOG_LEVEL)

    fun setIgnoreUpdateVersion(version: String) = writeString(Prefs.KEY_IGNORE_UPDATE_VERSION, version)
    fun getIgnoreUpdateVersion(): String = preferences().getString(Prefs.KEY_IGNORE_UPDATE_VERSION, "") ?: ""

    fun isFirstLaunch(): Boolean = preferences().getBoolean(Prefs.KEY_APP_FIRST_LAUNCH, true)
    fun markFirstLaunchDone() = writeBoolean(Prefs.KEY_APP_FIRST_LAUNCH, false)

    fun getAppLanguage(): Int = preferences().getInt(Prefs.KEY_APP_LANGUAGE, Prefs.DEF_APP_LANGUAGE)
    fun setAppLanguage(value: Int) = writeInt(Prefs.KEY_APP_LANGUAGE, value)

    /**
     * Pushes every value currently in the app's own file to the framework.
     *
     * Called once when the service binds: while the framework is not
     * connected writes only land in the app's file, so a plain "push on write"
     * would leave SystemUI running with stale values from a previous session.
     */
    fun syncAllToFramework() {
        val current = preferences()
        val snapshot = ModuleSettings.from(current, density)
        // The component switches go out under the CURRENT arrangement's key. The hooked
        // process reads the same way, so both sides agree about which set is live without
        // having to exchange two snapshots.
        val style = Prefs.normalizeTrioStyle(snapshot.trioStyle)
        push { prefs ->
            prefs.putBoolean(Prefs.KEY_ENABLED, snapshot.enabled)
            prefs.putBoolean(Prefs.KEY_MERGE_ICONS, snapshot.mergeIcons)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, style), snapshot.showWifi)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, style), snapshot.showMobile)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), snapshot.showValue)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, style), snapshot.showBolt)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_DUAL_SIM, style), snapshot.dualSim)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, style), snapshot.showSim1)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, style), snapshot.showSim2)
            prefs.putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style),
                snapshot.mobileTypeMode)
            prefs.putBoolean(Prefs.styledKey(Prefs.KEY_VALUE_CENTRED, style),
                snapshot.valueCentred)
            prefs.putInt(Prefs.KEY_TRIO_STYLE, snapshot.trioStyle)
            prefs.putInt(Prefs.KEY_NAV_STYLE, snapshot.navStyle)
            prefs.putInt(Prefs.KEY_NAV_LABELS, snapshot.navLabels)
            prefs.putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style),
                Prefs.encodeLayout(snapshot.ringZones))
            prefs.putBoolean(Prefs.KEY_DATA_SIM_ONLY, snapshot.dataSimOnly)
            prefs.putBoolean(Prefs.KEY_PERF_POWER, snapshot.perfPower)
            prefs.putBoolean(Prefs.KEY_PERF_RUNNING, snapshot.perfRunning)
            prefs.putBoolean(Prefs.KEY_CLOCK_SECONDS, snapshot.clockSeconds)
            prefs.putBoolean(Prefs.KEY_CLOCK_DATE, snapshot.clockDate)
            prefs.putBoolean(Prefs.KEY_CLOCK_WEEKDAY, snapshot.clockWeekday)
            prefs.putBoolean(Prefs.KEY_CLOCK_WEATHER, snapshot.clockWeather)
            prefs.putInt(Prefs.KEY_CLOCK_WEATHER_STYLE, snapshot.clockWeatherStyle)
            prefs.putInt(Prefs.KEY_CLOCK_STYLE, snapshot.clockStyle)
            prefs.putBoolean(Prefs.KEY_SHADE_HIDE_POWER, snapshot.shadeHidePower)
            prefs.putBoolean(Prefs.KEY_SHADE_HIDE_BELL, snapshot.shadeHideBell)
            prefs.putBoolean(Prefs.KEY_HIDE_STATUS_CLOCK, snapshot.hideStatusClock)
            prefs.putBoolean(Prefs.KEY_HIDE_ALARM_ICON, snapshot.hideAlarmIcon)
            prefs.putBoolean(Prefs.KEY_HIDE_BLUETOOTH_ICON, snapshot.hideBluetoothIcon)
            prefs.putBoolean(Prefs.KEY_SHADE_LUNAR, snapshot.shadeLunar)
            prefs.putBoolean(Prefs.KEY_SHADE_WEATHER, snapshot.shadeWeather)
            prefs.putInt(Prefs.KEY_SHADE_WEATHER_STYLE, snapshot.shadeWeatherStyle)
            prefs.putInt(Prefs.KEY_SHADE_WEATHER_POSITION, snapshot.shadeWeatherPosition)
            prefs.putInt(Prefs.KEY_SHADE_WEATHER_CLICK, snapshot.shadeWeatherClick)
            prefs.putInt(Prefs.KEY_EFFECTIVE_SCOPE, snapshot.effectiveScope)
            prefs.putBoolean(Prefs.KEY_CAPSULE_NO_SCALE, snapshot.capsuleNoScale)
            prefs.putBoolean(Prefs.KEY_HIDE_HD_VOLTE, snapshot.hideHdVolte)
            prefs.putBoolean(Prefs.KEY_HIDE_HD_VOWIFI, snapshot.hideHdVowifi)
            prefs.putBoolean(Prefs.KEY_CC_SLIDER_LONG_PRESS, snapshot.ccSliderLongPress)
            prefs.putBoolean(Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF, snapshot.shadeHideChargeNotif)
            prefs.putBoolean(Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF, snapshot.shadeHideHotspotNotif)
            prefs.putBoolean(Prefs.KEY_SHADE_HIDE_ADB_NOTIF, snapshot.shadeHideAdbNotif)
            prefs.putInt(Prefs.KEY_BT_BATTERY_MODE, snapshot.btBatteryMode)
            prefs.putBoolean(Prefs.KEY_PERF_SIGNAL, snapshot.perfSignal)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, snapshot.clockWeatherColorOnDark)
            prefs.putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, snapshot.clockWeatherColorOnLight)
            prefs.putString(Prefs.KEY_CLOCK_DIGIT_COLORS, Prefs.encodeClockDigitColors(snapshot.clockDigitColorsOnDark))
            prefs.putString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, Prefs.encodeClockDigitColors(snapshot.clockDigitColorsOnLight))
            prefs.putBoolean(Prefs.KEY_PERF_CURRENT, snapshot.perfCurrent)
            prefs.putInt(Prefs.KEY_CURRENT_UNIT, snapshot.currentUnit)
            prefs.putBoolean(Prefs.KEY_CURRENT_POSITIVE, snapshot.currentPositive)
            prefs.putInt(Prefs.KEY_PERF_REFRESH_SEC, snapshot.perfRefreshSec)
            prefs.putBoolean(Prefs.KEY_PERF_TEMP, snapshot.perfTemp)
            prefs.putBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, snapshot.perfTempChargingOnly)
            prefs.putBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE, snapshot.chargingShowsValue)
            prefs.putInt(Prefs.KEY_WIFI_SCALE, snapshot.wifiScale)
            prefs.putInt(Prefs.KEY_CAPSULE_LENGTH, snapshot.capsuleLength)
            prefs.putInt(Prefs.KEY_CAPSULE_LEFT_DX, snapshot.capsuleLeftDx)
            prefs.putInt(Prefs.KEY_CAPSULE_LEFT_DY, snapshot.capsuleLeftDy)
            prefs.putInt(Prefs.KEY_CAPSULE_RIGHT_DX, snapshot.capsuleRightDx)
            prefs.putInt(Prefs.KEY_CAPSULE_RIGHT_DY, snapshot.capsuleRightDy)
            prefs.putBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, snapshot.typeHideOnWifi)
            prefs.putInt(Prefs.KEY_PERF_SCALE, snapshot.perfScale)
            prefs.putInt(Prefs.KEY_PERF_WEIGHT, snapshot.perfWeight)
            prefs.putInt(Prefs.KEY_GLYPH_SCALE, snapshot.glyphScale)

            prefs.putBoolean(Prefs.KEY_ROLE_COLORS, snapshot.roleColors)
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, snapshot.criticalOnDark)
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, snapshot.criticalOnLight)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, snapshot.chargingOnDark)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, snapshot.chargingOnLight)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_DARK, snapshot.lowOnDark)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, snapshot.lowOnLight)
            prefs.putInt(Prefs.KEY_LOW_THRESHOLD, snapshot.lowThreshold)

            prefs.putInt(Prefs.KEY_RING_STROKE, snapshot.ringStroke)
            prefs.putInt(Prefs.KEY_ARC_STROKE, snapshot.arcStroke)
            prefs.putInt(Prefs.KEY_VALUE_SIZE, snapshot.valueSize)
            prefs.putInt(Prefs.KEY_VALUE_WEIGHT, snapshot.valueWeight)
            // The clock's own pair. No migration is needed here even though the two
            // keys are new: ModuleSettings reads them with the value's size and weight
            // as the fallback, so writing the snapshot's own clockSize - which the
            // repository has already defaulted to the value's - reproduces exactly
            // the look an install had when the two readings shared one size.
            prefs.putInt(Prefs.KEY_CLOCK_SIZE, snapshot.clockSize)
            prefs.putInt(Prefs.KEY_CLOCK_WEIGHT, snapshot.clockWeight)
            prefs.putInt(Prefs.KEY_TYPE_SIZE, snapshot.typeSize)
            prefs.putInt(Prefs.KEY_OUT_TYPE_SIZE_DP, snapshot.outTypeSize)
            prefs.putInt(Prefs.KEY_OUT_SIGNAL_SIZE, snapshot.outSignalSize)
            prefs.putInt(Prefs.KEY_OUT_DOT_SIZE, snapshot.outDotSize)
            prefs.putInt(Prefs.KEY_STACK_STYLE, snapshot.stackStyle)
            prefs.putInt(Prefs.KEY_STACK_LABEL, snapshot.stackLabel)
            prefs.putBoolean(Prefs.KEY_ANTI_BURN_IN, snapshot.antiBurnIn)
            prefs.putInt(Prefs.KEY_SIGNAL_COLUMNS, snapshot.signalColumns)
            prefs.putInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, snapshot.outTypeMarginLeft)
            prefs.putInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, snapshot.outTypeMarginRight)
            prefs.putInt(Prefs.KEY_OUT_SIGNAL_MARGIN, snapshot.outSignalMargin)
            prefs.putInt(Prefs.KEY_TYPE_SUFFIX_SCALE, snapshot.typeSuffixScale)
            prefs.putInt(Prefs.KEY_TYPE_WEIGHT, snapshot.typeWeight)
            prefs.putInt(Prefs.KEY_TRACK_ALPHA, snapshot.trackAlpha)
            prefs.putString(Prefs.KEY_FONT_PATH, snapshot.fontPath)

            // Sent on connect as well as on write, because both are drawing sizes the
            // hooked process cannot re-derive: a value changed while the framework was
            // unbound would otherwise never reach SystemUI.
            prefs.putInt(Prefs.KEY_BOLT_SCALE, snapshot.boltScale)
            prefs.putInt(Prefs.KEY_BOLT_ANIM, snapshot.boltAnim)
            prefs.putInt(Prefs.KEY_BOLT_SPEED, snapshot.boltSpeed)
            prefs.putInt(Prefs.KEY_DOT_SCALE, snapshot.dotScale)
            prefs.putString(Prefs.KEY_OUT_ORDER, Prefs.encodeOutOrder(snapshot.outOrder))
            prefs.putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(snapshot.perfOrder))

            prefs.putBoolean(Prefs.KEY_DEBUG_LOG, current.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG))
        }
        notifyModule()
    }

    private fun writeBoolean(key: String, value: Boolean) {
        val prefs = preferences()
        if (prefs.contains(key) && prefs.getBoolean(key, false) == value) return
        prefs.edit { putBoolean(key, value) }
        push { it.putBoolean(key, value) }
        notifyModule()
    }

    private fun writeInt(key: String, value: Int) {
        val prefs = preferences()
        if (prefs.contains(key) && prefs.getInt(key, Int.MIN_VALUE) == value) return
        prefs.edit { putInt(key, value) }
        push { it.putInt(key, value) }
        notifyModule()
    }

    private fun writeString(key: String, value: String) {
        val prefs = preferences()
        if (prefs.contains(key) && prefs.getString(key, null) == value) return
        prefs.edit { putString(key, value) }
        push { it.putString(key, value) }
        notifyModule()
    }

    /**
     * Tells the hooked process to re-read the settings, carrying the whole
     * snapshot in the intent.
     *
     * This is a second, callback-independent path: the framework's
     * remote-preference change callback is the intended mechanism, but it has
     * been observed to stop reaching SystemUI on device - the daemon database
     * held the new value while the status bar kept drawing the old one. The
     * broadcast is sent even when the service is not bound, because the hooked
     * process is up regardless and only needs the extras.
     *
     * Delivery is asynchronous, so no ordering against [push] is needed; both
     * carry the same values.
     */
    private fun notifyModule() {
        runCatching {
            val intent = Intent(Prefs.ACTION_RELOAD)
                .setPackage(Prefs.SYSTEMUI_PACKAGE)
                .putExtras(ModuleSettings.from(preferences(), density).toBundle())
            appContext.sendBroadcast(intent)
        }.onFailure { Log.w(Prefs.TAG, "cannot notify the hooked process", it) }
    }

    private fun touchPerfOrder(currentOrder: IntArray, currentSettings: ModuleSettings, enabledItem: Int): IntArray {
        fun isItemActive(id: Int): Boolean = when (id) {
            Prefs.PERF_ITEM_POWER -> if (id == enabledItem) true else currentSettings.perfPower
            Prefs.PERF_ITEM_TEMP -> if (id == enabledItem) true else currentSettings.perfTemp
            Prefs.PERF_ITEM_CURRENT -> if (id == enabledItem) true else currentSettings.perfCurrent
            Prefs.PERF_ITEM_VOLTAGE -> if (id == enabledItem) true else currentSettings.perfVoltage
            Prefs.PERF_ITEM_FPS -> if (id == enabledItem) true else currentSettings.perfFps
            Prefs.PERF_ITEM_SIGNAL -> if (id == enabledItem) true else currentSettings.perfSignal
            else -> false
        }
        val activeOthers = currentOrder.filter { it != enabledItem && isItemActive(it) }
        val inactive = currentOrder.filter { it != enabledItem && !isItemActive(it) }
        val newOrder = ArrayList<Int>(Prefs.PERF_ITEM_COUNT)
        newOrder.addAll(activeOthers)
        newOrder.add(enabledItem)
        newOrder.addAll(inactive)
        return newOrder.toIntArray()
    }
    /** Applies [block] to the framework's remote preferences, if connected. */
    private fun push(block: (SharedPreferences.Editor) -> Unit) {
        val bound = service
        if (bound == null) {
            // Not fatal: onServiceBind replays every value through
            // syncAllToFramework(), so the write is not lost. Logged because a
            // silent return here looks exactly like "live updates are broken".
            Log.w(Prefs.TAG, "framework not bound, settings will sync on connect")
            return
        }
        runCatching {
            bound.getRemotePreferences(Prefs.NAME).edit(commit = true) {
                block(this)
            }
        }.onFailure { Log.e(Prefs.TAG, "cannot push settings to framework", it) }
    }
}
