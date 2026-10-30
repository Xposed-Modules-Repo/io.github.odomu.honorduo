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

import android.content.SharedPreferences
import android.os.Bundle
import io.github.odomu.honorduo.model.ModuleSettings.Companion.readMobileTypeMode
import io.github.odomu.honorduo.model.ModuleSettings.Companion.readOutTypeSize
import io.github.odomu.honorduo.model.ModuleSettings.Companion.readRingLayout
import kotlin.math.roundToInt

/**
 * One coherent set of user settings.
 *
 * Deliberately free of the Xposed API: the settings app reads this type too,
 * and `io.github.libxposed.api` is `compileOnly`, so pulling it in here would
 * mean shipping classes the app cannot resolve at runtime.
 *
 * Every field is a `var` so a snapshot can be assembled cheaply and published
 * whole; a published instance is never mutated afterwards, which is why a single
 * changed key copies the snapshot and republishes ([copy], [applyKeyFrom]).
 */
class ModuleSettings {

    @JvmField
    var enabled = Prefs.DEF_ENABLED
    @JvmField
    var mergeIcons = Prefs.DEF_MERGE_ICONS
    @JvmField
    var showWifi = false
    @JvmField
    var showMobile = false
    @JvmField
    var showMobileActivity = Prefs.DEF_SHOW_MOBILE_ACTIVITY
    @JvmField
    var showValue = false
    @JvmField
    var showBolt = false

    /** See [Prefs.KEY_TYPE_HIDE_ON_WIFI]; one setting for every arrangement. */
    @JvmField
    var typeHideOnWifi = Prefs.DEF_TYPE_HIDE_ON_WIFI

    /** See [Prefs.KEY_CHARGING_SHOWS_VALUE]; the ring only. */
    @JvmField
    var chargingShowsValue = Prefs.DEF_CHARGING_SHOWS_VALUE

    /** See [Prefs.KEY_PERF_TEMP_CHARGING_ONLY]. */
    @JvmField
    var perfTempChargingOnly = Prefs.DEF_PERF_TEMP_CHARGING_ONLY

    /** Size of the Wi-Fi arcs, in percent; see [Prefs.KEY_WIFI_SCALE]. */
    @JvmField
    var wifiScale = Prefs.DEF_WIFI_SCALE

    /** The capsule's extra straight run; see [Prefs.KEY_CAPSULE_LENGTH]. */
    @JvmField
    var capsuleLength = Prefs.DEF_CAPSULE_LENGTH

    /** Interior-cell nudges, in design units; see [Prefs.KEY_CAPSULE_LEFT_DX]. */
    @JvmField
    var capsuleLeftDx = Prefs.DEF_CAPSULE_NUDGE
    @JvmField
    var capsuleLeftDy = Prefs.DEF_CAPSULE_NUDGE
    @JvmField
    var capsuleRightDx = Prefs.DEF_CAPSULE_NUDGE
    @JvmField
    var capsuleRightDy = Prefs.DEF_CAPSULE_NUDGE
    @JvmField
    var ringSpacingX = Prefs.DEF_RING_SPACING_X
    @JvmField
    var ringSpacingY = Prefs.DEF_RING_SPACING_Y
    @JvmField
    var replaceWifiIcon = Prefs.DEF_REPLACE_WIFI_ICON
    @JvmField
    var replaceStatusIcon = Prefs.DEF_REPLACE_STATUS_ICON

    /** Hides the bar's own clock outright; see [Prefs.KEY_HIDE_STATUS_CLOCK]. */
    @JvmField
    var hideStatusClock = Prefs.DEF_HIDE_STATUS_CLOCK

    /** Hides the bar's alarm icon; see [Prefs.KEY_HIDE_ALARM_ICON]. */
    @JvmField
    var hideAlarmIcon = Prefs.DEF_HIDE_ALARM_ICON
    var hidePrivacyChip = Prefs.DEF_HIDE_PRIVACY_CHIP

    /** Hides the bar's Bluetooth icon; see [Prefs.KEY_HIDE_BLUETOOTH_ICON]. */
    @JvmField
    var hideBluetoothIcon = Prefs.DEF_HIDE_BLUETOOTH_ICON

    /** Where the network type is drawn: [Prefs.MOBILE_TYPE_OFF] / `_IN_RING` / `_OUT_RING`. */
    @JvmField
    var mobileTypeMode = Prefs.MOBILE_TYPE_OFF

    /**
     * Draws the percentage enlarged in the middle of the ring, with the arcs shrunk
     * into the 12 o'clock notch. Stored under the frozen key
     * [Prefs.KEY_VALUE_CENTRED].
     */
    @JvmField
    var valueCentred = false

    /** Bottom-bar material; see [Prefs.KEY_NAV_STYLE]. */
    @JvmField
    var navStyle = Prefs.DEF_NAV_STYLE

    /** Bottom-bar label mode; see [Prefs.KEY_NAV_LABELS]. */
    @JvmField
    var navLabels = Prefs.DEF_NAV_LABELS

    /**
     * Which arrangement is drawn: [Prefs.STYLE_RING], [Prefs.STYLE_RECT] or
     * [Prefs.STYLE_CAPSULE]. The same keys feed all three; what differs is where
     * each piece goes, and [StatusAppearance] is where that is resolved.
     */
    @JvmField
    var trioStyle = Prefs.DEF_TRIO_STYLE

    /**
     * The ring layout: for each of the three zones, an ordered list of candidate
     * pieces, most wanted first.
     *
     * A zone draws the first entry that has something to show on this frame, so
     * the list is a fallback chain rather than a stack of overlapping ink. Empty by
     * default - a fresh install starts with every zone empty, because with priority
     * lists there is no single arrangement to ship.
     */
    @JvmField
    var ringZones: Array<IntArray> = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT)

    /** Draws one row of level dots per SIM instead of a single row. */
    @JvmField
    var dualSim = false

    /** Where the mobile reading goes: [Prefs.SIGNAL_IN_RING] or [Prefs.SIGNAL_OUT_RING]. */
    @JvmField
    var signalMode = Prefs.DEF_SIGNAL_MODE

    /** Draws the module's own stacked reading while the signal is out of the ring. */
    @JvmField
    var stackedSignal = false

    /** SIM 1's own row of dots, as a placed component. */
    @JvmField
    var showSim1 = false

    /** SIM 2's own row of dots. */
    @JvmField
    var showSim2 = false

    /** Gap between the capsule's cells, in percent of the reference gap. */
    @JvmField
    var capGap = Prefs.DEF_CAP_GAP

    /** Appends "%" to the in-glyph battery number. */
    @JvmField
    var showPercent = false

    /** Draws only the current data SIM in the out-of-ring reading. */
    @JvmField
    var dataSimOnly = false

    @JvmField
    var roleColors = true
    @JvmField
    var criticalOnDark = Prefs.DEF_COLOR_CRITICAL_ON_DARK
    @JvmField
    var criticalOnLight = Prefs.DEF_COLOR_CRITICAL_ON_LIGHT
    @JvmField
    var chargingOnDark = Prefs.DEF_COLOR_CHARGING_ON_DARK
    @JvmField
    var chargingOnLight = Prefs.DEF_COLOR_CHARGING_ON_LIGHT
    @JvmField
    var lowOnDark = Prefs.DEF_COLOR_LOW_ON_DARK
    @JvmField
    var lowOnLight = Prefs.DEF_COLOR_LOW_ON_LIGHT
    @JvmField
    var lowThreshold = Prefs.DEF_LOW_THRESHOLD
    @JvmField
    var roleColorsTemp = Prefs.DEF_ROLE_COLORS_TEMP
    @JvmField
    var tempThreshold = Prefs.DEF_TEMP_THRESHOLD
    @JvmField
    var highTempOnDark = Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK
    @JvmField
    var highTempOnLight = Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT

    @JvmField
    var ringStroke = Prefs.DEF_RING_STROKE
    @JvmField
    var arcStroke = Prefs.DEF_ARC_STROKE
    @JvmField
    var valueSize = Prefs.DEF_VALUE_SIZE

    /** Numeric font weight of the value/type text, 100..900. */
    @JvmField
    var valueWeight = Prefs.DEF_VALUE_WEIGHT

    /**
     * Size and weight of the time in the capsule's left-hand slot.
     *
     * Independent of [valueSize] / [valueWeight]: the two slots hold different
     * strings and are read differently, so a change made for one must not move the
     * other. They fall back to the value's own size when unset, so an install that
     * predates these keys keeps the look it had.
     */
    @JvmField
    var clockSize = Prefs.DEF_CLOCK_SIZE
    @JvmField
    var clockWeight = Prefs.DEF_CLOCK_WEIGHT

    /** Size and weight of the centred network type ("5G", "5GA"). */
    @JvmField
    var typeSize = Prefs.DEF_TYPE_SIZE

    /** Font size of the network type when drawn outside the glyph, in dp. */
    @JvmField
    var outTypeSize = Prefs.DEF_OUT_TYPE_SIZE_DP

    /** Height of the stacked out-of-ring signal, in dp. */
    @JvmField
    var outSignalSize = Prefs.DEF_OUT_SIGNAL_SIZE

    /** Dot-row diameter of the out-of-ring reading, in percent of a bar's width. */
    var outDotSize = Prefs.DEF_OUT_DOT_SIZE

    /** Which look the stacked reading uses - see [Prefs.KEY_STACK_STYLE]. */
    var stackStyle = Prefs.DEF_STACK_STYLE

    /** Where the stacked reading puts its type - see [Prefs.KEY_STACK_LABEL]. */
    var stackLabel = Prefs.DEF_STACK_LABEL
    var antiBurnIn = Prefs.DEF_ANTI_BURN_IN
    var signalColumns = Prefs.DEF_SIGNAL_COLUMNS
    var fontPath: String = Prefs.DEF_FONT_PATH

    /** The gap the out-of-ring type label keeps on each side, in dp. */
    @JvmField
    var outTypeMarginLeft = Prefs.DEF_OUT_TYPE_MARGIN_LEFT
    @JvmField
    var outTypeMarginRight = Prefs.DEF_OUT_TYPE_MARGIN_RIGHT

    /** The gap between the out-of-ring reading and the battery, in dp. */
    @JvmField
    var outSignalMargin = Prefs.DEF_OUT_SIGNAL_MARGIN

    /** Scale of a trailing "A" in the network type, as a percentage of the main size. */
    @JvmField
    var typeSuffixScale = Prefs.DEF_TYPE_SUFFIX_SCALE

    /** Size of the in-ring charging bolt, as a percentage of its reference size. */
    @JvmField
    var boltScale = Prefs.DEF_BOLT_SCALE

    /** How the charging bolt moves - see [Prefs.KEY_BOLT_ANIM]. */
    @JvmField
    var boltAnim = Prefs.DEF_BOLT_ANIM

    /** How fast the charging animation runs, in percent - see [Prefs.KEY_BOLT_SPEED]. */
    @JvmField
    var boltSpeed = Prefs.DEF_BOLT_SPEED

    /** Size of the ring's level-dot rows, as a percentage of the bead the stroke implies. */
    @JvmField
    var dotScale = Prefs.DEF_DOT_SCALE

    /**
     * The out-of-ring order, nearest the battery first - see [Prefs.KEY_OUT_ORDER].
     *
     * Always [Prefs.OUT_SLOT_COUNT] entries: [Prefs.decodeOutOrder] normalises
     * whatever was stored, so nothing downstream copes with a short array.
     */
    @JvmField
    var outOrder: IntArray = Prefs.OUT_DEFAULT_ORDER.clone()

    @JvmField
    var perfOrder: IntArray = Prefs.PERF_DEFAULT_ORDER.clone()

    @JvmField
    var typeWeight = Prefs.DEF_TYPE_WEIGHT
    @JvmField
    var trackAlpha = Prefs.DEF_TRACK_ALPHA

    /** Draws the charging power beside the battery. */
    @JvmField
    var perfPower = false

    /** Shows the power readout while unplugged too, as the running draw. */
    @JvmField
    var perfRunning = false

    /** Seconds in the drawn time - see [Prefs.KEY_CLOCK_SECONDS]. */
    var clockSeconds = Prefs.DEF_CLOCK_SECONDS

    /** Date line above the drawn time - see [Prefs.KEY_CLOCK_DATE]. */
    @JvmField
    var clockDate = Prefs.DEF_CLOCK_DATE

    /** Weekday beside the date - see [Prefs.KEY_CLOCK_WEEKDAY]. */
    @JvmField
    var clockWeekday = Prefs.DEF_CLOCK_WEEKDAY

    @JvmField var clockTopOffsetX = Prefs.DEF_CLOCK_TOP_OFFSET_X
    @JvmField var clockBottomOffsetX = Prefs.DEF_CLOCK_BOTTOM_OFFSET_X
    @JvmField var clockLunarGap = Prefs.DEF_CLOCK_LUNAR_GAP
    @JvmField var clockRowGap = Prefs.DEF_CLOCK_ROW_GAP
    @JvmField
    var clockLunar = Prefs.DEF_CLOCK_LUNAR
    @JvmField
    var clockDateFormat = Prefs.DEF_CLOCK_DATE_FORMAT
    @JvmField
    var clockWeekdayFormat = Prefs.DEF_CLOCK_WEEKDAY_FORMAT

    @JvmField var clockWeekdayColorOnDark = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockWeekdayColorOnLight = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockDateColorOnDark = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockDateColorOnLight = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockLunarColorOnDark = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockLunarColorOnLight = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockWeather = Prefs.DEF_CLOCK_WEATHER
    @JvmField var clockWeatherStyle = Prefs.DEF_CLOCK_WEATHER_STYLE
    @JvmField var clockStyle = Prefs.DEF_CLOCK_STYLE
    @JvmField var clockWeatherColorOnDark = Prefs.DEF_COLOR_CLOCK
    @JvmField var clockWeatherColorOnLight = Prefs.DEF_COLOR_CLOCK

    /**
     * Colour of each character of the time, indexed in the drawn string.
     *
     * An entry of `0` is "not set": that character draws in the clock's own ink, which
     * is also what makes an untouched install look exactly as it did before the row
     * existed. Kept per background like every other clock colour.
     */
    @JvmField var clockDigitColorsOnDark: IntArray = Prefs.decodeClockDigitColors(null)
    @JvmField var clockDigitColorsOnLight: IntArray = Prefs.decodeClockDigitColors(null)

    /** Control center: hide the header's power button. */
    @JvmField var shadeHidePower = Prefs.DEF_SHADE_HIDE_POWER

    /** Notification shade: hide the bell that opens notification management. */
    @JvmField var shadeHideBell = Prefs.DEF_SHADE_HIDE_BELL

    /** Notification shade: append the lunar date to the date line. */
    @JvmField var shadeLunar = Prefs.DEF_SHADE_LUNAR

    /** Pull-down panel weather master switch. */
    @JvmField var shadeWeather = Prefs.DEF_SHADE_WEATHER

    /** Pull-down panel weather position: 0=after date/time, 1=traffic row. */
    @JvmField var shadeWeatherPosition = Prefs.DEF_SHADE_WEATHER_POSITION

    /** Pull-down panel weather display style: 0=text, 1=icon, 2=symbol. */
    @JvmField var shadeWeatherStyle = Prefs.DEF_SHADE_WEATHER_STYLE

    /** Effective scope: 0=all locations, 1=exclude control center, 2=exclude notification shade, 3=exclude both. */
    @JvmField var effectiveScope = Prefs.DEF_EFFECTIVE_SCOPE

    /** Disables status bar scaling when a live capsule message is docked. */
    @JvmField var capsuleNoScale = Prefs.DEF_CAPSULE_NO_SCALE
    @JvmField var hideHdVolte = Prefs.DEF_HIDE_HD_VOLTE
    @JvmField var hideHdVowifi = Prefs.DEF_HIDE_HD_VOWIFI
    @JvmField var ccSliderLongPress = Prefs.DEF_CC_SLIDER_LONG_PRESS

    /** Trio Bluetooth headset battery indicator mode: 0=off, 1=arc, 2=digit. */
    @JvmField var btBatteryMode = Prefs.DEF_BT_BATTERY_MODE
    @JvmField var shadeHideChargeNotif = Prefs.DEF_SHADE_HIDE_CHARGE_NOTIF
    @JvmField var shadeHideHotspotNotif = Prefs.DEF_SHADE_HIDE_HOTSPOT_NOTIF
    @JvmField var shadeHideAdbNotif = Prefs.DEF_SHADE_HIDE_ADB_NOTIF


    /** Click action on notification shade traffic weather: 0=disabled, 1=open weather app, 2=native traffic stats. */
    @JvmField var shadeWeatherClick = Prefs.DEF_SHADE_WEATHER_CLICK

    /** Draws the battery temperature in the same view. */
    @JvmField
    var perfTemp = false

    /** Draws the screen's frame rate - see [Prefs.KEY_PERF_FPS]. */
    @JvmField
    var perfFps = Prefs.DEF_PERF_FPS

    /** Show the battery current among the readouts. */
    @JvmField
    var perfCurrent = Prefs.DEF_PERF_CURRENT

    /** Show the battery voltage among the readouts. */
    @JvmField
    var perfVoltage = Prefs.DEF_PERF_VOLTAGE

    /** Show cellular signal strength among the hardware readouts (item #6). */
    @JvmField
    var perfSignal = Prefs.DEF_PERF_SIGNAL
    /** How the current is written: milliamps or amps. */
    @JvmField
    var currentUnit = Prefs.DEF_CURRENT_UNIT

    /** Draw the current as a magnitude, whatever direction the platform reports. */
    @JvmField
    var currentPositive = Prefs.DEF_CURRENT_POSITIVE

    /** Overall size of the two readouts, as a percentage of the adaptive fit. */
    @JvmField
    var perfScale = Prefs.DEF_PERF_SCALE

    /** How heavy the two readouts are drawn, on the platform's 100..900 scale. */
    @JvmField
    var perfWeight = Prefs.DEF_PERF_WEIGHT

    /** Extra spacing between the two stacked readout lines, in dp. */
    @JvmField
    var perfLineGap = Prefs.DEF_PERF_LINE_GAP

    /** How often the charging power and the temperature are re-read, in seconds. */
    @JvmField
    var perfRefreshSec = Prefs.DEF_PERF_REFRESH_SEC

    /** Overall size of the glyph, as a percentage of the fit the host box implies. */
    @JvmField
    var glyphScale = Prefs.DEF_GLYPH_SCALE

    /** Only used by the settings app; the hooked side reads it through [ModuleConfig]. */
    @JvmField
    var debugLog = false

    companion object {

        /** A snapshot holding every shipped default. */
        @JvmStatic
        fun defaults(): ModuleSettings = ModuleSettings().apply {
            enabled = Prefs.DEF_ENABLED
            mergeIcons = Prefs.DEF_MERGE_ICONS
            showWifi = Prefs.DEF_SHOW_WIFI
            showMobile = Prefs.DEF_SHOW_MOBILE
            showValue = Prefs.DEF_SHOW_VALUE
            showMobileActivity = Prefs.DEF_SHOW_MOBILE_ACTIVITY
            showBolt = Prefs.DEF_SHOW_BOLT
            typeHideOnWifi = Prefs.DEF_TYPE_HIDE_ON_WIFI
            chargingShowsValue = Prefs.DEF_CHARGING_SHOWS_VALUE
            perfTempChargingOnly = Prefs.DEF_PERF_TEMP_CHARGING_ONLY
            wifiScale = Prefs.DEF_WIFI_SCALE
            capsuleLength = Prefs.DEF_CAPSULE_LENGTH
            capsuleLeftDx = Prefs.DEF_CAPSULE_NUDGE
            capsuleLeftDy = Prefs.DEF_CAPSULE_NUDGE
            capsuleRightDx = Prefs.DEF_CAPSULE_NUDGE
            capsuleRightDy = Prefs.DEF_CAPSULE_NUDGE
            ringSpacingX = Prefs.DEF_RING_SPACING_X
            ringSpacingY = Prefs.DEF_RING_SPACING_Y
            replaceWifiIcon = Prefs.DEF_REPLACE_WIFI_ICON
            replaceStatusIcon = Prefs.DEF_REPLACE_STATUS_ICON
            hideStatusClock = Prefs.DEF_HIDE_STATUS_CLOCK
            hideAlarmIcon = Prefs.DEF_HIDE_ALARM_ICON
            hidePrivacyChip = Prefs.DEF_HIDE_PRIVACY_CHIP
            hideBluetoothIcon = Prefs.DEF_HIDE_BLUETOOTH_ICON
            mobileTypeMode = Prefs.DEF_MOBILE_TYPE_MODE
            valueCentred = Prefs.DEF_VALUE_CENTRED
            navStyle = Prefs.DEF_NAV_STYLE
            navLabels = Prefs.DEF_NAV_LABELS
            trioStyle = Prefs.DEF_TRIO_STYLE
            dualSim = Prefs.DEF_DUAL_SIM
            signalMode = Prefs.DEF_SIGNAL_MODE
            stackedSignal = Prefs.DEF_STACKED_SIGNAL
            showSim1 = Prefs.DEF_SHOW_SIM_1
            showSim2 = Prefs.DEF_SHOW_SIM_2
            dataSimOnly = Prefs.DEF_DATA_SIM_ONLY
            showPercent = Prefs.DEF_SHOW_PERCENT
            capGap = Prefs.DEF_CAP_GAP
            perfPower = Prefs.DEF_PERF_POWER
            perfRunning = Prefs.DEF_PERF_RUNNING
            clockSeconds = Prefs.DEF_CLOCK_SECONDS
            clockDate = Prefs.DEF_CLOCK_DATE
            clockWeekday = Prefs.DEF_CLOCK_WEEKDAY
            clockTopOffsetX = Prefs.DEF_CLOCK_TOP_OFFSET_X
            clockBottomOffsetX = Prefs.DEF_CLOCK_BOTTOM_OFFSET_X
            clockLunarGap = Prefs.DEF_CLOCK_LUNAR_GAP
            clockRowGap = Prefs.DEF_CLOCK_ROW_GAP
            clockLunar = Prefs.DEF_CLOCK_LUNAR
            clockDateFormat = Prefs.DEF_CLOCK_DATE_FORMAT
            clockWeekdayFormat = Prefs.DEF_CLOCK_WEEKDAY_FORMAT
            clockWeekdayColorOnDark = Prefs.DEF_COLOR_CLOCK
            clockWeekdayColorOnLight = Prefs.DEF_COLOR_CLOCK
            clockDateColorOnDark = Prefs.DEF_COLOR_CLOCK
            clockDateColorOnLight = Prefs.DEF_COLOR_CLOCK
            clockLunarColorOnDark = Prefs.DEF_COLOR_CLOCK
            clockLunarColorOnLight = Prefs.DEF_COLOR_CLOCK
            clockWeather = Prefs.DEF_CLOCK_WEATHER
            clockWeatherStyle = Prefs.DEF_CLOCK_WEATHER_STYLE
            clockStyle = Prefs.DEF_CLOCK_STYLE
            clockWeatherColorOnDark = Prefs.DEF_COLOR_CLOCK
            clockWeatherColorOnLight = Prefs.DEF_COLOR_CLOCK
            clockDigitColorsOnDark = Prefs.decodeClockDigitColors(null)
            clockDigitColorsOnLight = Prefs.decodeClockDigitColors(null)
            shadeHidePower = Prefs.DEF_SHADE_HIDE_POWER
            shadeHideBell = Prefs.DEF_SHADE_HIDE_BELL
            shadeLunar = Prefs.DEF_SHADE_LUNAR
            shadeWeather = Prefs.DEF_SHADE_WEATHER
            shadeWeatherPosition = Prefs.DEF_SHADE_WEATHER_POSITION
            shadeWeatherStyle = Prefs.DEF_SHADE_WEATHER_STYLE
            effectiveScope = Prefs.DEF_EFFECTIVE_SCOPE
            capsuleNoScale = Prefs.DEF_CAPSULE_NO_SCALE
            hideHdVolte = Prefs.DEF_HIDE_HD_VOLTE
            hideHdVowifi = Prefs.DEF_HIDE_HD_VOWIFI
            ccSliderLongPress = Prefs.DEF_CC_SLIDER_LONG_PRESS
            btBatteryMode = Prefs.DEF_BT_BATTERY_MODE
            shadeHideChargeNotif = Prefs.DEF_SHADE_HIDE_CHARGE_NOTIF
            shadeHideHotspotNotif = Prefs.DEF_SHADE_HIDE_HOTSPOT_NOTIF
            shadeHideAdbNotif = Prefs.DEF_SHADE_HIDE_ADB_NOTIF
            shadeWeatherClick = Prefs.DEF_SHADE_WEATHER_CLICK
            perfTemp = Prefs.DEF_PERF_TEMP
            perfFps = Prefs.DEF_PERF_FPS
            perfCurrent = Prefs.DEF_PERF_CURRENT
            perfVoltage = Prefs.DEF_PERF_VOLTAGE
            perfSignal = Prefs.DEF_PERF_SIGNAL
            currentPositive = Prefs.DEF_CURRENT_POSITIVE
            perfScale = Prefs.DEF_PERF_SCALE
            perfWeight = Prefs.DEF_PERF_WEIGHT
            perfLineGap = Prefs.DEF_PERF_LINE_GAP
            perfRefreshSec = Prefs.DEF_PERF_REFRESH_SEC
            glyphScale = Prefs.DEF_GLYPH_SCALE
            roleColors = Prefs.DEF_ROLE_COLORS
            criticalOnDark = Prefs.DEF_COLOR_CRITICAL_ON_DARK
            criticalOnLight = Prefs.DEF_COLOR_CRITICAL_ON_LIGHT
            chargingOnDark = Prefs.DEF_COLOR_CHARGING_ON_DARK
            chargingOnLight = Prefs.DEF_COLOR_CHARGING_ON_LIGHT
            lowOnDark = Prefs.DEF_COLOR_LOW_ON_DARK
            lowOnLight = Prefs.DEF_COLOR_LOW_ON_LIGHT
            lowThreshold = Prefs.DEF_LOW_THRESHOLD
            roleColorsTemp = Prefs.DEF_ROLE_COLORS_TEMP
            tempThreshold = Prefs.DEF_TEMP_THRESHOLD
            highTempOnDark = Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK
            highTempOnLight = Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT
            ringStroke = Prefs.DEF_RING_STROKE
            arcStroke = Prefs.DEF_ARC_STROKE
            valueSize = Prefs.DEF_VALUE_SIZE
            valueWeight = Prefs.DEF_VALUE_WEIGHT
            clockSize = Prefs.DEF_CLOCK_SIZE
            clockWeight = Prefs.DEF_CLOCK_WEIGHT
            typeSize = Prefs.DEF_TYPE_SIZE
            outTypeSize = Prefs.DEF_OUT_TYPE_SIZE_DP
            outSignalSize = Prefs.DEF_OUT_SIGNAL_SIZE
            outDotSize = Prefs.DEF_OUT_DOT_SIZE
            stackStyle = Prefs.DEF_STACK_STYLE
            stackLabel = Prefs.DEF_STACK_LABEL
            outTypeMarginLeft = Prefs.DEF_OUT_TYPE_MARGIN_LEFT
            outTypeMarginRight = Prefs.DEF_OUT_TYPE_MARGIN_RIGHT
            outSignalMargin = Prefs.DEF_OUT_SIGNAL_MARGIN
            typeSuffixScale = Prefs.DEF_TYPE_SUFFIX_SCALE
            boltScale = Prefs.DEF_BOLT_SCALE
            boltAnim = Prefs.DEF_BOLT_ANIM
            boltSpeed = Prefs.DEF_BOLT_SPEED
            dotScale = Prefs.DEF_DOT_SCALE
            outOrder = Prefs.OUT_DEFAULT_ORDER.clone()
            perfOrder = Prefs.PERF_DEFAULT_ORDER.clone()
            typeWeight = Prefs.DEF_TYPE_WEIGHT
            trackAlpha = Prefs.DEF_TRACK_ALPHA
            fontPath = Prefs.DEF_FONT_PATH
            debugLog = Prefs.DEF_DEBUG_LOG
        }

        /** Reads every key, falling back to the shared defaults. */
        @JvmStatic
        fun from(p: SharedPreferences): ModuleSettings = from(p, Prefs.AUTHORED_DENSITY)

        /**
         * Reads every key, clamping the numeric ones here rather than at draw time
         * so the renderer can trust them.
         *
         * @param density the display's density, needed only to convert a value left
         *   in the retired raw-pixel key by an install that predates
         *   [Prefs.KEY_OUT_TYPE_SIZE_DP]
         */
        @JvmStatic
        fun from(p: SharedPreferences, density: Float): ModuleSettings = from(p, density, -1)

        /**
         * The same read, for a stated style rather than the stored one.
         *
         * `styleOverride` resolves the per-arrangement keys without changing the
         * stored setting, which is what the settings screen's style tiles need: they
         * preview the *other* arrangement, and a read that first had to write
         * the stored style would push a bogus transient value at the hooked process.
         */
        @JvmStatic
        fun from(p: SharedPreferences, density: Float, styleOverride: Int): ModuleSettings {
            val s = ModuleSettings()
            s.enabled = p.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED)
            s.mergeIcons = p.getBoolean(Prefs.KEY_MERGE_ICONS, Prefs.DEF_MERGE_ICONS)
            // The style first: the component switches below are stored per
            // arrangement, so which key to read depends on it.
            s.navStyle = Prefs.normalizeNavStyle(
                p.getInt(Prefs.KEY_NAV_STYLE, Prefs.DEF_NAV_STYLE),
            )
            s.navLabels = Prefs.normalizeNavLabels(
                p.getInt(Prefs.KEY_NAV_LABELS, Prefs.DEF_NAV_LABELS),
            )
            s.trioStyle = if (styleOverride >= 0) {
                Prefs.normalizeTrioStyle(styleOverride)
            } else {
                Prefs.normalizeTrioStyle(p.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE))
            }

            s.showWifi = readStyledBoolean(p, Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI, s.trioStyle)
            s.showMobile = readStyledBoolean(
                p, Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE, s.trioStyle,
            )
            s.showMobileActivity = readStyledBoolean(
                p, Prefs.KEY_SHOW_MOBILE_ACTIVITY, Prefs.DEF_SHOW_MOBILE_ACTIVITY, s.trioStyle,
            )
            s.showValue = readStyledBoolean(
                p, Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE, s.trioStyle,
            )
            s.showBolt = readStyledBoolean(p, Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT, s.trioStyle)
            // Plain keys, not styled: one answer for every arrangement.
            s.typeHideOnWifi = p.getBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, Prefs.DEF_TYPE_HIDE_ON_WIFI)
            s.chargingShowsValue = p.getBoolean(
                Prefs.KEY_CHARGING_SHOWS_VALUE, Prefs.DEF_CHARGING_SHOWS_VALUE,
            )
            s.perfTempChargingOnly = p.getBoolean(
                Prefs.KEY_PERF_TEMP_CHARGING_ONLY, Prefs.DEF_PERF_TEMP_CHARGING_ONLY,
            )
            s.wifiScale = Prefs.clamp(
                p.getInt(Prefs.KEY_WIFI_SCALE, Prefs.DEF_WIFI_SCALE),
                Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE,
            )
            s.capsuleLength = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_LENGTH, Prefs.DEF_CAPSULE_LENGTH),
                Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH,
            )
            s.capsuleLeftDx = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_LEFT_DX, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_X, Prefs.MAX_CAPSULE_NUDGE_X,
            )
            s.capsuleLeftDy = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_LEFT_DY, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_Y, Prefs.MAX_CAPSULE_NUDGE_Y,
            )
            s.capsuleRightDx = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_RIGHT_DX, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_X, Prefs.MAX_CAPSULE_NUDGE_X,
            )
            s.capsuleRightDy = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_RIGHT_DY, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_Y, Prefs.MAX_CAPSULE_NUDGE_Y,
            )
            s.ringSpacingX = Prefs.clamp(
                p.getInt(Prefs.KEY_RING_SPACING_X, Prefs.DEF_RING_SPACING_X),
                Prefs.MIN_RING_SPACING_X, Prefs.MAX_RING_SPACING_X,
            )
            s.ringSpacingY = Prefs.clamp(
                p.getInt(Prefs.KEY_RING_SPACING_Y, Prefs.DEF_RING_SPACING_Y),
                Prefs.MIN_RING_SPACING_Y, Prefs.MAX_RING_SPACING_Y,
            )
            s.replaceWifiIcon = p.getBoolean(Prefs.KEY_REPLACE_WIFI_ICON, Prefs.DEF_REPLACE_WIFI_ICON)
            s.replaceStatusIcon = p.getBoolean(Prefs.KEY_REPLACE_STATUS_ICON, Prefs.DEF_REPLACE_STATUS_ICON)
            s.hideStatusClock = p.getBoolean(Prefs.KEY_HIDE_STATUS_CLOCK, Prefs.DEF_HIDE_STATUS_CLOCK)
            s.hideAlarmIcon = p.getBoolean(Prefs.KEY_HIDE_ALARM_ICON, Prefs.DEF_HIDE_ALARM_ICON)
            s.hidePrivacyChip = p.getBoolean(Prefs.KEY_HIDE_PRIVACY_CHIP, Prefs.DEF_HIDE_PRIVACY_CHIP)
            s.hideBluetoothIcon = p.getBoolean(Prefs.KEY_HIDE_BLUETOOTH_ICON, Prefs.DEF_HIDE_BLUETOOTH_ICON)
            s.mobileTypeMode = readMobileTypeMode(p, s.trioStyle)
            s.valueCentred = readStyledBoolean(
                p, Prefs.KEY_VALUE_CENTRED, Prefs.DEF_VALUE_CENTRED, s.trioStyle,
            )
            s.ringZones = Prefs.decodeLayout(readRingLayout(p, s.trioStyle))
            s.dualSim = readStyledBoolean(p, Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM, s.trioStyle)
            s.signalMode = Prefs.clamp(
                readStyledInt(p, Prefs.KEY_SIGNAL_MODE, Prefs.DEF_SIGNAL_MODE, s.trioStyle),
                Prefs.MIN_SIGNAL_MODE,
                Prefs.MAX_SIGNAL_MODE,
            )
            s.stackedSignal = readStyledBoolean(
                p, Prefs.KEY_STACKED_SIGNAL, Prefs.DEF_STACKED_SIGNAL, s.trioStyle,
            )
            s.showSim1 = readStyledBoolean(
                p, Prefs.KEY_SHOW_SIM_1, Prefs.DEF_SHOW_SIM_1, s.trioStyle,
            )
            s.showSim2 = readStyledBoolean(
                p, Prefs.KEY_SHOW_SIM_2, Prefs.DEF_SHOW_SIM_2, s.trioStyle,
            )
            if (Prefs.mentionsRetiredMeter(readRingLayout(p, s.trioStyle))) {
                s.showSim1 = true
            }
            s.dataSimOnly = p.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY)
            s.showPercent = p.getBoolean(Prefs.KEY_SHOW_PERCENT, Prefs.DEF_SHOW_PERCENT)
            s.capGap = Prefs.clamp(
                p.getInt(Prefs.KEY_CAP_GAP, Prefs.DEF_CAP_GAP),
                Prefs.MIN_CAP_GAP, Prefs.MAX_CAP_GAP,
            )
            s.perfPower = p.getBoolean(Prefs.KEY_PERF_POWER, Prefs.DEF_PERF_POWER)
            s.perfRunning = p.getBoolean(Prefs.KEY_PERF_RUNNING, Prefs.DEF_PERF_RUNNING)
            s.clockSeconds = p.getBoolean(Prefs.KEY_CLOCK_SECONDS, Prefs.DEF_CLOCK_SECONDS)
            s.clockDate = p.getBoolean(Prefs.KEY_CLOCK_DATE, Prefs.DEF_CLOCK_DATE)
            s.clockWeekday = p.getBoolean(Prefs.KEY_CLOCK_WEEKDAY, Prefs.DEF_CLOCK_WEEKDAY)
            s.clockTopOffsetX = Prefs.clamp(p.getInt(Prefs.KEY_CLOCK_TOP_OFFSET_X, Prefs.DEF_CLOCK_TOP_OFFSET_X), Prefs.MIN_CLOCK_OFFSET, Prefs.MAX_CLOCK_OFFSET)
            s.clockBottomOffsetX = Prefs.clamp(p.getInt(Prefs.KEY_CLOCK_BOTTOM_OFFSET_X, Prefs.DEF_CLOCK_BOTTOM_OFFSET_X), Prefs.MIN_CLOCK_OFFSET, Prefs.MAX_CLOCK_OFFSET)
            s.clockLunarGap = Prefs.clamp(p.getInt(Prefs.KEY_CLOCK_LUNAR_GAP, Prefs.DEF_CLOCK_LUNAR_GAP), Prefs.MIN_CLOCK_LUNAR_GAP, Prefs.MAX_CLOCK_LUNAR_GAP)
            s.clockRowGap = Prefs.clamp(p.getInt(Prefs.KEY_CLOCK_ROW_GAP, Prefs.DEF_CLOCK_ROW_GAP), Prefs.MIN_CLOCK_ROW_GAP, Prefs.MAX_CLOCK_ROW_GAP)
            s.clockLunar = p.getBoolean(Prefs.KEY_CLOCK_LUNAR, Prefs.DEF_CLOCK_LUNAR)
            s.clockDateFormat = p.getInt(Prefs.KEY_CLOCK_DATE_FORMAT, Prefs.DEF_CLOCK_DATE_FORMAT)
            s.clockWeekdayFormat = p.getInt(Prefs.KEY_CLOCK_WEEKDAY_FORMAT, Prefs.DEF_CLOCK_WEEKDAY_FORMAT)
            s.clockWeekdayColorOnDark = p.getInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockWeekdayColorOnLight = p.getInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockDateColorOnDark = p.getInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockDateColorOnLight = p.getInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockLunarColorOnDark = p.getInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockLunarColorOnLight = p.getInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockWeather = p.getBoolean(Prefs.KEY_CLOCK_WEATHER, Prefs.DEF_CLOCK_WEATHER)
            s.clockWeatherStyle = p.getInt(Prefs.KEY_CLOCK_WEATHER_STYLE, Prefs.DEF_CLOCK_WEATHER_STYLE)
            s.clockStyle = p.getInt(Prefs.KEY_CLOCK_STYLE, Prefs.DEF_CLOCK_STYLE)
            s.clockWeatherColorOnDark = p.getInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockWeatherColorOnLight = p.getInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockDigitColorsOnDark = Prefs.decodeClockDigitColors(p.getString(Prefs.KEY_CLOCK_DIGIT_COLORS, null))
            s.clockDigitColorsOnLight = Prefs.decodeClockDigitColors(p.getString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, null))
            s.shadeHidePower = p.getBoolean(Prefs.KEY_SHADE_HIDE_POWER, Prefs.DEF_SHADE_HIDE_POWER)
            s.shadeHideBell = p.getBoolean(Prefs.KEY_SHADE_HIDE_BELL, Prefs.DEF_SHADE_HIDE_BELL)
            s.shadeLunar = p.getBoolean(Prefs.KEY_SHADE_LUNAR, Prefs.DEF_SHADE_LUNAR)
            s.shadeWeather = p.getBoolean(Prefs.KEY_SHADE_WEATHER, Prefs.DEF_SHADE_WEATHER)
            s.shadeWeatherPosition = p.getInt(Prefs.KEY_SHADE_WEATHER_POSITION, Prefs.DEF_SHADE_WEATHER_POSITION)
            s.shadeWeatherStyle = p.getInt(Prefs.KEY_SHADE_WEATHER_STYLE, Prefs.DEF_SHADE_WEATHER_STYLE)
            s.effectiveScope = Prefs.normalizeScope(
                p.getInt(Prefs.KEY_EFFECTIVE_SCOPE, Prefs.DEF_EFFECTIVE_SCOPE),
            )
            s.capsuleNoScale = p.getBoolean(Prefs.KEY_CAPSULE_NO_SCALE, Prefs.DEF_CAPSULE_NO_SCALE)
            s.hideHdVolte = p.getBoolean(Prefs.KEY_HIDE_HD_VOLTE, Prefs.DEF_HIDE_HD_VOLTE)
            s.hideHdVowifi = p.getBoolean(Prefs.KEY_HIDE_HD_VOWIFI, Prefs.DEF_HIDE_HD_VOWIFI)
            s.ccSliderLongPress = p.getBoolean(Prefs.KEY_CC_SLIDER_LONG_PRESS, Prefs.DEF_CC_SLIDER_LONG_PRESS)
            s.btBatteryMode = p.getInt(Prefs.KEY_BT_BATTERY_MODE, Prefs.DEF_BT_BATTERY_MODE)
            s.shadeHideChargeNotif = p.getBoolean(Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF, Prefs.DEF_SHADE_HIDE_CHARGE_NOTIF)
            s.shadeHideHotspotNotif = p.getBoolean(Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF, Prefs.DEF_SHADE_HIDE_HOTSPOT_NOTIF)
            s.shadeHideAdbNotif = p.getBoolean(Prefs.KEY_SHADE_HIDE_ADB_NOTIF, Prefs.DEF_SHADE_HIDE_ADB_NOTIF)
            s.shadeWeatherClick = p.getInt(Prefs.KEY_SHADE_WEATHER_CLICK, Prefs.DEF_SHADE_WEATHER_CLICK)
            s.perfTemp = p.getBoolean(Prefs.KEY_PERF_TEMP, Prefs.DEF_PERF_TEMP)
            s.perfFps = p.getBoolean(Prefs.KEY_PERF_FPS, Prefs.DEF_PERF_FPS)
            s.perfCurrent = p.getBoolean(Prefs.KEY_PERF_CURRENT, Prefs.DEF_PERF_CURRENT)
            s.perfVoltage = p.getBoolean(Prefs.KEY_PERF_VOLTAGE, Prefs.DEF_PERF_VOLTAGE)
            s.perfSignal = p.getBoolean(Prefs.KEY_PERF_SIGNAL, Prefs.DEF_PERF_SIGNAL)
            s.currentUnit = p.getInt(Prefs.KEY_CURRENT_UNIT, Prefs.DEF_CURRENT_UNIT)
                .coerceIn(Prefs.CURRENT_UNIT_MA, Prefs.CURRENT_UNIT_A)
            s.currentPositive = p.getBoolean(Prefs.KEY_CURRENT_POSITIVE, Prefs.DEF_CURRENT_POSITIVE)
            s.perfScale = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_SCALE, Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE,
                Prefs.MAX_PERF_SCALE,
            )
            s.perfWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_WEIGHT, Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT,
                Prefs.MAX_PERF_WEIGHT,
            )
            s.perfLineGap = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_LINE_GAP, Prefs.DEF_PERF_LINE_GAP),
                Prefs.MIN_PERF_LINE_GAP,
                Prefs.MAX_PERF_LINE_GAP,
            )
            s.perfRefreshSec = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_REFRESH_SEC, Prefs.DEF_PERF_REFRESH_SEC),
                Prefs.MIN_PERF_REFRESH_SEC,
                Prefs.MAX_PERF_REFRESH_SEC,
            )
            s.glyphScale = Prefs.clamp(
                p.getInt(Prefs.KEY_GLYPH_SCALE, Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE,
                Prefs.MAX_GLYPH_SCALE,
            )

            s.roleColors = p.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS)
            s.criticalOnDark = p.getInt(
                Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK,
            )
            s.criticalOnLight = p.getInt(
                Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT,
            )
            s.chargingOnDark = p.getInt(
                Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK,
            )
            s.chargingOnLight = p.getInt(
                Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT,
            )
            s.lowOnDark = p.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            s.lowOnLight = p.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
            s.roleColorsTemp = p.getBoolean(Prefs.KEY_ROLE_COLORS_TEMP, Prefs.DEF_ROLE_COLORS_TEMP)
            s.tempThreshold = Prefs.clamp(
                p.getInt(Prefs.KEY_TEMP_THRESHOLD, Prefs.DEF_TEMP_THRESHOLD),
                Prefs.MIN_TEMP_THRESHOLD,
                Prefs.MAX_TEMP_THRESHOLD,
            )
            s.highTempOnDark = p.getInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK)
            s.highTempOnLight = p.getInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT)
            s.lowThreshold = Prefs.clamp(
                p.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD,
                Prefs.MAX_LOW_THRESHOLD,
            )

            s.ringStroke = Prefs.clamp(
                p.getInt(Prefs.KEY_RING_STROKE, Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE,
                Prefs.MAX_RING_STROKE,
            )
            s.arcStroke = Prefs.clamp(
                p.getInt(Prefs.KEY_ARC_STROKE, Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE,
                Prefs.MAX_ARC_STROKE,
            )
            s.valueSize = Prefs.clamp(
                p.getInt(Prefs.KEY_VALUE_SIZE, Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE,
                Prefs.maxValueSize(s.trioStyle),
            )
            s.valueWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_VALUE_WEIGHT, Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT,
                Prefs.MAX_VALUE_WEIGHT,
            )
            // The clock falls back to the value's OWN size and weight, not to the
            // constants: before these keys existed the time was drawn at exactly the
            // value's size, so an install that had moved that slider keeps its clock.
            s.clockSize = Prefs.clamp(
                p.getInt(Prefs.KEY_CLOCK_SIZE, s.valueSize),
                Prefs.MIN_CLOCK_SIZE,
                Prefs.maxClockSize(s.trioStyle),
            )
            s.clockWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_CLOCK_WEIGHT, s.valueWeight),
                Prefs.MIN_CLOCK_WEIGHT,
                Prefs.MAX_CLOCK_WEIGHT,
            )
            s.typeSize = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_SIZE, Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE,
                Prefs.maxTypeSize(s.trioStyle),
            )
            s.outTypeSize = readOutTypeSize(p, density)
            s.outDotSize = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_DOT_SIZE, Prefs.DEF_OUT_DOT_SIZE),
                Prefs.MIN_OUT_DOT_SIZE, Prefs.MAX_OUT_DOT_SIZE,
            )
            s.stackStyle = p.getInt(Prefs.KEY_STACK_STYLE, Prefs.DEF_STACK_STYLE)
            s.stackLabel = p.getInt(Prefs.KEY_STACK_LABEL, Prefs.DEF_STACK_LABEL)
            s.antiBurnIn = p.getBoolean(Prefs.KEY_ANTI_BURN_IN, Prefs.DEF_ANTI_BURN_IN)
            s.signalColumns = Prefs.clamp(
                p.getInt(Prefs.KEY_SIGNAL_COLUMNS, Prefs.DEF_SIGNAL_COLUMNS),
                Prefs.MIN_SIGNAL_COLUMNS, Prefs.MAX_SIGNAL_COLUMNS,
            )
            s.fontPath = p.getString(Prefs.KEY_FONT_PATH, Prefs.DEF_FONT_PATH) ?: Prefs.DEF_FONT_PATH
            s.outSignalSize = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_SIGNAL_SIZE, Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE,
                Prefs.MAX_OUT_SIGNAL_SIZE,
            )
            s.outTypeMarginLeft = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN,
                Prefs.MAX_OUT_TYPE_MARGIN,
            )
            s.outTypeMarginRight = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN,
                Prefs.MAX_OUT_TYPE_MARGIN,
            )
            s.outSignalMargin = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_SIGNAL_MARGIN, Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN,
                Prefs.MAX_OUT_SIGNAL_MARGIN,
            )
            s.typeSuffixScale = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_SUFFIX_SCALE, Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE,
                Prefs.MAX_TYPE_SUFFIX_SCALE,
            )
            s.boltScale = Prefs.clamp(
                p.getInt(Prefs.KEY_BOLT_SCALE, Prefs.DEF_BOLT_SCALE),
                Prefs.MIN_BOLT_SCALE,
                Prefs.MAX_BOLT_SCALE,
            )
            s.boltAnim = Prefs.clamp(
                p.getInt(Prefs.KEY_BOLT_ANIM, Prefs.DEF_BOLT_ANIM),
                Prefs.MIN_BOLT_ANIM,
                Prefs.MAX_BOLT_ANIM,
            )
            s.boltSpeed = Prefs.clamp(
                p.getInt(Prefs.KEY_BOLT_SPEED, Prefs.DEF_BOLT_SPEED),
                Prefs.MIN_BOLT_SPEED,
                Prefs.MAX_BOLT_SPEED,
            )
            s.dotScale = Prefs.clamp(
                p.getInt(Prefs.KEY_DOT_SCALE, Prefs.DEF_DOT_SCALE),
                Prefs.MIN_DOT_SCALE,
                Prefs.MAX_DOT_SCALE,
            )
            s.outOrder = Prefs.decodeOutOrder(
                p.getString(Prefs.KEY_OUT_ORDER, Prefs.OUT_DEFAULT_ORDER_STRING),
            )
            s.perfOrder = Prefs.decodePerfOrder(
                p.getString(Prefs.KEY_PERF_ORDER, Prefs.PERF_DEFAULT_ORDER_STRING),
            )
            s.typeWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_WEIGHT, Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT,
                Prefs.MAX_TYPE_WEIGHT,
            )
            s.trackAlpha = Prefs.clamp(
                p.getInt(Prefs.KEY_TRACK_ALPHA, Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA,
                Prefs.MAX_TRACK_ALPHA,
            )
            s.debugLog = p.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG)
            return s
        }

        /**
         * Reads a snapshot delivered as broadcast extras, falling back to the same
         * defaults as [from]. The extras come from another process and are clamped
         * identically; `bundle` may be null or partial, and every key falls back on
         * its own.
         */
        @JvmStatic
        fun fromBundle(bundle: Bundle?): ModuleSettings {
            if (bundle == null) return defaults()
            val s = ModuleSettings()
            s.enabled = bundle.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED)
            s.mergeIcons = bundle.getBoolean(Prefs.KEY_MERGE_ICONS, Prefs.DEF_MERGE_ICONS)
            // The style first, for the same reason as the stored read.
            s.navStyle = Prefs.normalizeNavStyle(
                bundle.getInt(Prefs.KEY_NAV_STYLE, Prefs.DEF_NAV_STYLE),
            )
            s.navLabels = Prefs.normalizeNavLabels(
                bundle.getInt(Prefs.KEY_NAV_LABELS, Prefs.DEF_NAV_LABELS),
            )
            s.trioStyle = Prefs.normalizeTrioStyle(
                bundle.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE),
            )

            s.showWifi = readStyledBoolean(bundle, Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI, s.trioStyle)
            s.showMobile = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE, s.trioStyle,
            )
            s.showMobileActivity = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_MOBILE_ACTIVITY, Prefs.DEF_SHOW_MOBILE_ACTIVITY, s.trioStyle,
            )
            s.showValue = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE, s.trioStyle,
            )
            s.showBolt = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT, s.trioStyle,
            )
            s.typeHideOnWifi = bundle.getBoolean(
                Prefs.KEY_TYPE_HIDE_ON_WIFI, Prefs.DEF_TYPE_HIDE_ON_WIFI,
            )
            s.chargingShowsValue = bundle.getBoolean(
                Prefs.KEY_CHARGING_SHOWS_VALUE, Prefs.DEF_CHARGING_SHOWS_VALUE,
            )
            s.perfTempChargingOnly = bundle.getBoolean(
                Prefs.KEY_PERF_TEMP_CHARGING_ONLY, Prefs.DEF_PERF_TEMP_CHARGING_ONLY,
            )
            s.wifiScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_WIFI_SCALE, Prefs.DEF_WIFI_SCALE),
                Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE,
            )
            s.capsuleLength = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_LENGTH, Prefs.DEF_CAPSULE_LENGTH),
                Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH,
            )
            s.capsuleLeftDx = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_LEFT_DX, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_X, Prefs.MAX_CAPSULE_NUDGE_X,
            )
            s.capsuleLeftDy = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_LEFT_DY, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_Y, Prefs.MAX_CAPSULE_NUDGE_Y,
            )
            s.capsuleRightDx = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_RIGHT_DX, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_X, Prefs.MAX_CAPSULE_NUDGE_X,
            )
            s.capsuleRightDy = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_RIGHT_DY, Prefs.DEF_CAPSULE_NUDGE),
                Prefs.MIN_CAPSULE_NUDGE_Y, Prefs.MAX_CAPSULE_NUDGE_Y,
            )
            s.ringSpacingX = Prefs.clamp(
                bundle.getInt(Prefs.KEY_RING_SPACING_X, Prefs.DEF_RING_SPACING_X),
                Prefs.MIN_RING_SPACING_X, Prefs.MAX_RING_SPACING_X,
            )
            s.ringSpacingY = Prefs.clamp(
                bundle.getInt(Prefs.KEY_RING_SPACING_Y, Prefs.DEF_RING_SPACING_Y),
                Prefs.MIN_RING_SPACING_Y, Prefs.MAX_RING_SPACING_Y,
            )
            s.replaceWifiIcon = bundle.getBoolean(Prefs.KEY_REPLACE_WIFI_ICON, Prefs.DEF_REPLACE_WIFI_ICON)
            s.replaceStatusIcon = bundle.getBoolean(Prefs.KEY_REPLACE_STATUS_ICON, Prefs.DEF_REPLACE_STATUS_ICON)
            s.hideStatusClock = bundle.getBoolean(Prefs.KEY_HIDE_STATUS_CLOCK, Prefs.DEF_HIDE_STATUS_CLOCK)
            s.hideAlarmIcon = bundle.getBoolean(Prefs.KEY_HIDE_ALARM_ICON, Prefs.DEF_HIDE_ALARM_ICON)
            s.hidePrivacyChip = bundle.getBoolean(Prefs.KEY_HIDE_PRIVACY_CHIP, Prefs.DEF_HIDE_PRIVACY_CHIP)
            s.hideBluetoothIcon = bundle.getBoolean(Prefs.KEY_HIDE_BLUETOOTH_ICON, Prefs.DEF_HIDE_BLUETOOTH_ICON)
            s.mobileTypeMode = readMobileTypeMode(bundle, s.trioStyle)
            s.valueCentred = readStyledBoolean(
                bundle, Prefs.KEY_VALUE_CENTRED, Prefs.DEF_VALUE_CENTRED, s.trioStyle,
            )
            s.ringZones = Prefs.decodeLayout(readRingLayout(bundle, s.trioStyle))
            s.dualSim = readStyledBoolean(bundle, Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM, s.trioStyle)
            s.signalMode = Prefs.clamp(
                readStyledInt(bundle, Prefs.KEY_SIGNAL_MODE, Prefs.DEF_SIGNAL_MODE, s.trioStyle),
                Prefs.MIN_SIGNAL_MODE,
                Prefs.MAX_SIGNAL_MODE,
            )
            s.stackedSignal = readStyledBoolean(
                bundle, Prefs.KEY_STACKED_SIGNAL, Prefs.DEF_STACKED_SIGNAL, s.trioStyle,
            )
            s.showSim1 = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_SIM_1, Prefs.DEF_SHOW_SIM_1, s.trioStyle,
            )
            s.showSim2 = readStyledBoolean(
                bundle, Prefs.KEY_SHOW_SIM_2, Prefs.DEF_SHOW_SIM_2, s.trioStyle,
            )
            s.dataSimOnly = bundle.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY)
            s.showPercent = bundle.getBoolean(Prefs.KEY_SHOW_PERCENT, Prefs.DEF_SHOW_PERCENT)
            s.capGap = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAP_GAP, Prefs.DEF_CAP_GAP),
                Prefs.MIN_CAP_GAP, Prefs.MAX_CAP_GAP,
            )
            s.perfPower = bundle.getBoolean(Prefs.KEY_PERF_POWER, Prefs.DEF_PERF_POWER)
            s.perfRunning = bundle.getBoolean(Prefs.KEY_PERF_RUNNING, Prefs.DEF_PERF_RUNNING)
            s.clockSeconds = bundle.getBoolean(Prefs.KEY_CLOCK_SECONDS, Prefs.DEF_CLOCK_SECONDS)
            s.clockDate = bundle.getBoolean(Prefs.KEY_CLOCK_DATE, Prefs.DEF_CLOCK_DATE)
            s.clockWeekday = bundle.getBoolean(Prefs.KEY_CLOCK_WEEKDAY, Prefs.DEF_CLOCK_WEEKDAY)
            s.clockTopOffsetX = Prefs.clamp(bundle.getInt(Prefs.KEY_CLOCK_TOP_OFFSET_X, Prefs.DEF_CLOCK_TOP_OFFSET_X), Prefs.MIN_CLOCK_OFFSET, Prefs.MAX_CLOCK_OFFSET)
            s.clockBottomOffsetX = Prefs.clamp(bundle.getInt(Prefs.KEY_CLOCK_BOTTOM_OFFSET_X, Prefs.DEF_CLOCK_BOTTOM_OFFSET_X), Prefs.MIN_CLOCK_OFFSET, Prefs.MAX_CLOCK_OFFSET)
            s.clockLunarGap = Prefs.clamp(bundle.getInt(Prefs.KEY_CLOCK_LUNAR_GAP, Prefs.DEF_CLOCK_LUNAR_GAP), Prefs.MIN_CLOCK_LUNAR_GAP, Prefs.MAX_CLOCK_LUNAR_GAP)
            s.clockRowGap = Prefs.clamp(bundle.getInt(Prefs.KEY_CLOCK_ROW_GAP, Prefs.DEF_CLOCK_ROW_GAP), Prefs.MIN_CLOCK_ROW_GAP, Prefs.MAX_CLOCK_ROW_GAP)
            s.clockLunar = bundle.getBoolean(Prefs.KEY_CLOCK_LUNAR, Prefs.DEF_CLOCK_LUNAR)
            s.clockDateFormat = bundle.getInt(Prefs.KEY_CLOCK_DATE_FORMAT, Prefs.DEF_CLOCK_DATE_FORMAT)
            s.clockWeekdayFormat = bundle.getInt(Prefs.KEY_CLOCK_WEEKDAY_FORMAT, Prefs.DEF_CLOCK_WEEKDAY_FORMAT)
            s.clockWeekdayColorOnDark = bundle.getInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockWeekdayColorOnLight = bundle.getInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockDateColorOnDark = bundle.getInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockDateColorOnLight = bundle.getInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockLunarColorOnDark = bundle.getInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockLunarColorOnLight = bundle.getInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockWeather = bundle.getBoolean(Prefs.KEY_CLOCK_WEATHER, Prefs.DEF_CLOCK_WEATHER)
            s.clockWeatherStyle = bundle.getInt(Prefs.KEY_CLOCK_WEATHER_STYLE, Prefs.DEF_CLOCK_WEATHER_STYLE)
            s.clockStyle = bundle.getInt(Prefs.KEY_CLOCK_STYLE, Prefs.DEF_CLOCK_STYLE)
            s.clockWeatherColorOnDark = bundle.getInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, Prefs.DEF_COLOR_CLOCK)
            s.clockWeatherColorOnLight = bundle.getInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, Prefs.DEF_COLOR_CLOCK)
            s.clockDigitColorsOnDark = Prefs.decodeClockDigitColors(bundle.getString(Prefs.KEY_CLOCK_DIGIT_COLORS))
            s.clockDigitColorsOnLight = Prefs.decodeClockDigitColors(bundle.getString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT))
            s.shadeHidePower = bundle.getBoolean(Prefs.KEY_SHADE_HIDE_POWER, Prefs.DEF_SHADE_HIDE_POWER)
            s.shadeHideBell = bundle.getBoolean(Prefs.KEY_SHADE_HIDE_BELL, Prefs.DEF_SHADE_HIDE_BELL)
            s.shadeLunar = bundle.getBoolean(Prefs.KEY_SHADE_LUNAR, Prefs.DEF_SHADE_LUNAR)
            s.shadeWeather = bundle.getBoolean(Prefs.KEY_SHADE_WEATHER, Prefs.DEF_SHADE_WEATHER)
            s.shadeWeatherStyle = bundle.getInt(Prefs.KEY_SHADE_WEATHER_STYLE, Prefs.DEF_SHADE_WEATHER_STYLE)
            s.shadeWeatherPosition = bundle.getInt(Prefs.KEY_SHADE_WEATHER_POSITION, Prefs.DEF_SHADE_WEATHER_POSITION)
            s.shadeWeatherClick = bundle.getInt(Prefs.KEY_SHADE_WEATHER_CLICK, Prefs.DEF_SHADE_WEATHER_CLICK)
            s.effectiveScope = Prefs.normalizeScope(
                bundle.getInt(Prefs.KEY_EFFECTIVE_SCOPE, Prefs.DEF_EFFECTIVE_SCOPE),
            )
            s.capsuleNoScale = bundle.getBoolean(Prefs.KEY_CAPSULE_NO_SCALE, Prefs.DEF_CAPSULE_NO_SCALE)
            s.hideHdVolte = bundle.getBoolean(Prefs.KEY_HIDE_HD_VOLTE, Prefs.DEF_HIDE_HD_VOLTE)
            s.hideHdVowifi = bundle.getBoolean(Prefs.KEY_HIDE_HD_VOWIFI, Prefs.DEF_HIDE_HD_VOWIFI)
            s.ccSliderLongPress = bundle.getBoolean(Prefs.KEY_CC_SLIDER_LONG_PRESS, Prefs.DEF_CC_SLIDER_LONG_PRESS)
            s.btBatteryMode = bundle.getInt(Prefs.KEY_BT_BATTERY_MODE, Prefs.DEF_BT_BATTERY_MODE)
            s.shadeHideChargeNotif = bundle.getBoolean(Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF, Prefs.DEF_SHADE_HIDE_CHARGE_NOTIF)
            s.shadeHideHotspotNotif = bundle.getBoolean(Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF, Prefs.DEF_SHADE_HIDE_HOTSPOT_NOTIF)
            s.shadeHideAdbNotif = bundle.getBoolean(Prefs.KEY_SHADE_HIDE_ADB_NOTIF, Prefs.DEF_SHADE_HIDE_ADB_NOTIF)
            s.perfSignal = bundle.getBoolean(Prefs.KEY_PERF_SIGNAL, Prefs.DEF_PERF_SIGNAL)
            s.perfTemp = bundle.getBoolean(Prefs.KEY_PERF_TEMP, Prefs.DEF_PERF_TEMP)
            s.perfFps = bundle.getBoolean(Prefs.KEY_PERF_FPS, Prefs.DEF_PERF_FPS)
            s.perfCurrent = bundle.getBoolean(Prefs.KEY_PERF_CURRENT, Prefs.DEF_PERF_CURRENT)
            s.perfVoltage = bundle.getBoolean(Prefs.KEY_PERF_VOLTAGE, Prefs.DEF_PERF_VOLTAGE)
            s.currentUnit = bundle.getInt(Prefs.KEY_CURRENT_UNIT, Prefs.DEF_CURRENT_UNIT)
                .coerceIn(Prefs.CURRENT_UNIT_MA, Prefs.CURRENT_UNIT_A)
            s.currentPositive = bundle.getBoolean(Prefs.KEY_CURRENT_POSITIVE, Prefs.DEF_CURRENT_POSITIVE)
            s.perfScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_SCALE, Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE,
                Prefs.MAX_PERF_SCALE,
            )
            s.perfWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_WEIGHT, Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT,
                Prefs.MAX_PERF_WEIGHT,
            )
            s.perfLineGap = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_LINE_GAP, Prefs.DEF_PERF_LINE_GAP),
                Prefs.MIN_PERF_LINE_GAP,
                Prefs.MAX_PERF_LINE_GAP,
            )
            s.perfRefreshSec = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_REFRESH_SEC, Prefs.DEF_PERF_REFRESH_SEC),
                Prefs.MIN_PERF_REFRESH_SEC,
                Prefs.MAX_PERF_REFRESH_SEC,
            )
            s.glyphScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_GLYPH_SCALE, Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE,
                Prefs.MAX_GLYPH_SCALE,
            )

            s.roleColors = bundle.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS)
            s.criticalOnDark = bundle.getInt(
                Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK,
            )
            s.criticalOnLight = bundle.getInt(
                Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT,
            )
            s.chargingOnDark = bundle.getInt(
                Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK,
            )
            s.chargingOnLight = bundle.getInt(
                Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT,
            )
            s.lowOnDark = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            s.lowOnLight = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
            s.roleColorsTemp = bundle.getBoolean(Prefs.KEY_ROLE_COLORS_TEMP, Prefs.DEF_ROLE_COLORS_TEMP)
            s.tempThreshold = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TEMP_THRESHOLD, Prefs.DEF_TEMP_THRESHOLD),
                Prefs.MIN_TEMP_THRESHOLD,
                Prefs.MAX_TEMP_THRESHOLD,
            )
            s.highTempOnDark = bundle.getInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK)
            s.highTempOnLight = bundle.getInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT)
            s.lowThreshold = Prefs.clamp(
                bundle.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD,
                Prefs.MAX_LOW_THRESHOLD,
            )

            s.ringStroke = Prefs.clamp(
                bundle.getInt(Prefs.KEY_RING_STROKE, Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE,
                Prefs.MAX_RING_STROKE,
            )
            s.arcStroke = Prefs.clamp(
                bundle.getInt(Prefs.KEY_ARC_STROKE, Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE,
                Prefs.MAX_ARC_STROKE,
            )
            s.valueSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_VALUE_SIZE, Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE,
                Prefs.maxValueSize(s.trioStyle),
            )
            s.valueWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_VALUE_WEIGHT, Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT,
                Prefs.MAX_VALUE_WEIGHT,
            )
            s.clockSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CLOCK_SIZE, s.valueSize),
                Prefs.MIN_CLOCK_SIZE,
                Prefs.maxClockSize(s.trioStyle),
            )
            s.clockWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CLOCK_WEIGHT, s.valueWeight),
                Prefs.MIN_CLOCK_WEIGHT,
                Prefs.MAX_CLOCK_WEIGHT,
            )
            s.typeSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_SIZE, Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE,
                Prefs.maxTypeSize(s.trioStyle),
            )
            s.outTypeSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_SIZE_DP, Prefs.DEF_OUT_TYPE_SIZE_DP),
                Prefs.MIN_OUT_TYPE_SIZE_DP,
                Prefs.MAX_OUT_TYPE_SIZE_DP,
            )
            s.outDotSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_DOT_SIZE, Prefs.DEF_OUT_DOT_SIZE),
                Prefs.MIN_OUT_DOT_SIZE, Prefs.MAX_OUT_DOT_SIZE,
            )
            s.stackStyle = bundle.getInt(Prefs.KEY_STACK_STYLE, Prefs.DEF_STACK_STYLE)
            s.stackLabel = bundle.getInt(Prefs.KEY_STACK_LABEL, Prefs.DEF_STACK_LABEL)
            s.antiBurnIn = bundle.getBoolean(Prefs.KEY_ANTI_BURN_IN, Prefs.DEF_ANTI_BURN_IN)
            s.signalColumns = Prefs.clamp(
                bundle.getInt(Prefs.KEY_SIGNAL_COLUMNS, Prefs.DEF_SIGNAL_COLUMNS),
                Prefs.MIN_SIGNAL_COLUMNS, Prefs.MAX_SIGNAL_COLUMNS,
            )
            s.fontPath = bundle.getString(Prefs.KEY_FONT_PATH, Prefs.DEF_FONT_PATH) ?: Prefs.DEF_FONT_PATH
            s.outSignalSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_SIGNAL_SIZE, Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE,
                Prefs.MAX_OUT_SIGNAL_SIZE,
            )
            s.outTypeMarginLeft = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN,
                Prefs.MAX_OUT_TYPE_MARGIN,
            )
            s.outTypeMarginRight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN,
                Prefs.MAX_OUT_TYPE_MARGIN,
            )
            s.outSignalMargin = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_SIGNAL_MARGIN, Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN,
                Prefs.MAX_OUT_SIGNAL_MARGIN,
            )
            s.typeSuffixScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_SUFFIX_SCALE, Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE,
                Prefs.MAX_TYPE_SUFFIX_SCALE,
            )
            s.boltScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BOLT_SCALE, Prefs.DEF_BOLT_SCALE),
                Prefs.MIN_BOLT_SCALE,
                Prefs.MAX_BOLT_SCALE,
            )
            s.boltAnim = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BOLT_ANIM, Prefs.DEF_BOLT_ANIM),
                Prefs.MIN_BOLT_ANIM,
                Prefs.MAX_BOLT_ANIM,
            )
            s.boltSpeed = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BOLT_SPEED, Prefs.DEF_BOLT_SPEED),
                Prefs.MIN_BOLT_SPEED,
                Prefs.MAX_BOLT_SPEED,
            )
            s.dotScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_DOT_SCALE, Prefs.DEF_DOT_SCALE),
                Prefs.MIN_DOT_SCALE,
                Prefs.MAX_DOT_SCALE,
            )
            s.outOrder = Prefs.decodeOutOrder(
                bundle.getString(Prefs.KEY_OUT_ORDER, Prefs.OUT_DEFAULT_ORDER_STRING),
            )
            s.perfOrder = Prefs.decodePerfOrder(
                bundle.getString(Prefs.KEY_PERF_ORDER, Prefs.PERF_DEFAULT_ORDER_STRING),
            )
            s.typeWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_WEIGHT, Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT,
                Prefs.MAX_TYPE_WEIGHT,
            )
            s.trackAlpha = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TRACK_ALPHA, Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA,
                Prefs.MAX_TRACK_ALPHA,
            )
            s.debugLog = bundle.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG)
            return s
        }

        /**
         * The same read on top of an existing snapshot: keys the bundle carries are
         * taken from it, keys it leaves out keep the value `base` already had.
         *
         * Seeding matters because the bundle is untrusted input. If an absent key
         * fell back to the shared default instead, a single missing extra would wipe
         * the user's configuration - including `enabled`.
         */
        @JvmStatic
        fun fromBundle(bundle: Bundle?, base: ModuleSettings?): ModuleSettings {
            val s = base?.copy() ?: defaults()
            if (bundle == null) return s
            val delivered = fromBundle(bundle)
            for (key in bundle.keySet()) s.applyKeyFrom(delivered, key)
            return s
        }

        /** A deep copy of the zone lists; a snapshot is never mutated after publication. */
        @JvmStatic
        fun copyZones(zones: Array<IntArray>?): Array<IntArray> =
            Array(Prefs.ZONE_COUNT) { z -> zones?.getOrNull(z)?.clone() ?: IntArray(0) }

        /** One per-arrangement boolean, with the ring's value as the capsule's fallback. */
        private fun readStyledBoolean(
            p: SharedPreferences,
            base: String,
            def: Boolean,
            style: Int,
        ): Boolean = readStyled(p, base, style) { p.getBoolean(it, def) }

        /** One per-arrangement int, with the ring's value as the capsule's fallback. */
        private fun readStyledInt(p: SharedPreferences, base: String, def: Int, style: Int): Int =
            readStyled(p, base, style) { p.getInt(it, def) }

        /**
         * Reads `base` under `style`, falling back to the ring's key.
         *
         * Reads through [SharedPreferences.contains] rather than substituting a
         * default, because "absent" and "present but equal to the default" are
         * different answers here: absent means "inherit", present means "this is
         * what the user chose".
         */
        private inline fun <T> readStyled(
            p: SharedPreferences,
            base: String,
            style: Int,
            read: (String) -> T,
        ): T {
            val own = Prefs.styledKey(base, style)
            return read(if (p.contains(own)) own else base)
        }

        /**
         * Reads the three-way network-type placement, migrating the legacy boolean.
         *
         * The new int key wins whenever it is present. Only while it is absent is
         * the old [Prefs.KEY_SHOW_MOBILE_TYPE] boolean consulted, and an existing
         * "on" maps to [Prefs.MOBILE_TYPE_IN_RING] so that user keeps the placement
         * they had.
         */
        private fun readMobileTypeMode(p: SharedPreferences, style: Int): Int {
            val own = Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style)
            if (p.contains(own)) {
                return Prefs.clamp(
                    p.getInt(own, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE,
                    Prefs.MAX_MOBILE_TYPE_MODE,
                )
            }
            if (style == Prefs.STYLE_CAPSULE && p.contains(Prefs.KEY_MOBILE_TYPE_MODE)) {
                return Prefs.clamp(
                    p.getInt(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE,
                    Prefs.MAX_MOBILE_TYPE_MODE,
                )
            }
            return if (p.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)) {
                Prefs.MOBILE_TYPE_IN_RING
            } else {
                Prefs.MOBILE_TYPE_OFF
            }
        }

        /** The broadcast-extras mirror of [readMobileTypeMode]. */
        private fun readMobileTypeMode(bundle: Bundle, style: Int): Int {
            val own = Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style)
            if (bundle.containsKey(own)) {
                return Prefs.clamp(
                    bundle.getInt(own, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE,
                    Prefs.MAX_MOBILE_TYPE_MODE,
                )
            }
            if (style == Prefs.STYLE_CAPSULE && bundle.containsKey(Prefs.KEY_MOBILE_TYPE_MODE)) {
                return Prefs.clamp(
                    bundle.getInt(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE,
                    Prefs.MAX_MOBILE_TYPE_MODE,
                )
            }
            return if (bundle.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)) {
                Prefs.MOBILE_TYPE_IN_RING
            } else {
                Prefs.MOBILE_TYPE_OFF
            }
        }

        /**
         * Reads the out-of-ring type size, migrating the retired raw-pixel key.
         *
         * The dp key wins whenever it is present. Only an install that predates it
         * has that value converted once, by dividing by the display density. The old
         * key is never written again, so this runs at most until the user's first
         * write of the new key.
         */
        private fun readOutTypeSize(p: SharedPreferences, density: Float): Int {
            if (p.contains(Prefs.KEY_OUT_TYPE_SIZE_DP)) {
                return Prefs.clamp(
                    p.getInt(Prefs.KEY_OUT_TYPE_SIZE_DP, Prefs.DEF_OUT_TYPE_SIZE_DP),
                    Prefs.MIN_OUT_TYPE_SIZE_DP,
                    Prefs.MAX_OUT_TYPE_SIZE_DP,
                )
            }
            if (p.contains(LEGACY_KEY_OUT_TYPE_SIZE)) {
                val safeDensity = if (density > 0f) density else Prefs.AUTHORED_DENSITY
                return Prefs.clamp(
                    (p.getInt(
                        LEGACY_KEY_OUT_TYPE_SIZE,
                        LEGACY_DEF_OUT_TYPE_SIZE
                    ) / safeDensity).roundToInt(),
                    Prefs.MIN_OUT_TYPE_SIZE_DP,
                    Prefs.MAX_OUT_TYPE_SIZE_DP,
                )
            }
            return Prefs.clamp(
                Prefs.DEF_OUT_TYPE_SIZE_DP,
                Prefs.MIN_OUT_TYPE_SIZE_DP,
                Prefs.MAX_OUT_TYPE_SIZE_DP,
            )
        }

        /**
         * The retired raw-pixel size key and its default. Read once by
         * [readOutTypeSize] to convert an install that predates the dp key; never
         * written again.
         */
        private const val LEGACY_KEY_OUT_TYPE_SIZE = "out_type_size"
        private const val LEGACY_DEF_OUT_TYPE_SIZE = 32

        /**
         * Reads the ring layout, migrating the retired packed encodings.
         *
         * The string key wins whenever it is present. Only an install that predates
         * it has its packed int converted, and the conversion is delegated to
         * [Prefs.migrateLayout], which knows the two widths.
         */
        private fun readRingLayout(p: SharedPreferences, style: Int): String {
            val own = Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style)
            if (p.contains(own)) return p.getString(own, Prefs.EMPTY_LAYOUT) ?: Prefs.EMPTY_LAYOUT
            // The capsule has no layout of its own: adopt the ring's, so an upgrading
            // install opens on the arrangement it was already showing.
            if (style == Prefs.STYLE_CAPSULE && p.contains(Prefs.KEY_RING_LAYOUT)) {
                return p.getString(Prefs.KEY_RING_LAYOUT, Prefs.EMPTY_LAYOUT) ?: Prefs.EMPTY_LAYOUT
            }
            return Prefs.migrateLayout(
                packedAsString(p, Prefs.KEY_RING_SLOTS_V2),
                packedAsString(p, Prefs.KEY_RING_SLOTS),
            )
        }

        /** The broadcast-extras mirror of [readRingLayout]. */
        private fun readRingLayout(bundle: Bundle, style: Int): String {
            val own = Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style)
            if (bundle.containsKey(own)) {
                return bundle.getString(own, Prefs.EMPTY_LAYOUT) ?: Prefs.EMPTY_LAYOUT
            }
            if (style == Prefs.STYLE_CAPSULE && bundle.containsKey(Prefs.KEY_RING_LAYOUT)) {
                return bundle.getString(Prefs.KEY_RING_LAYOUT, Prefs.EMPTY_LAYOUT) ?: Prefs.EMPTY_LAYOUT
            }
            return Prefs.migrateLayout(
                packedAsString(bundle, Prefs.KEY_RING_SLOTS_V2),
                packedAsString(bundle, Prefs.KEY_RING_SLOTS),
            )
        }

        /** One per-arrangement boolean out of the broadcast extras. */
        private fun readStyledBoolean(bundle: Bundle, base: String, def: Boolean, style: Int): Boolean {
            val own = Prefs.styledKey(base, style)
            return bundle.getBoolean(if (bundle.containsKey(own)) own else base, def)
        }

        /** One per-arrangement int out of the broadcast extras. */
        private fun readStyledInt(bundle: Bundle, base: String, def: Int, style: Int): Int {
            val own = Prefs.styledKey(base, style)
            return bundle.getInt(if (bundle.containsKey(own)) own else base, def)
        }

        /** One retired packed key as the string [Prefs.migrateLayout] expects, or null. */
        private fun packedAsString(p: SharedPreferences, key: String): String? =
            if (p.contains(key)) p.getInt(key, 0).toString() else null

        private fun packedAsString(bundle: Bundle, key: String): String? =
            if (bundle.containsKey(key)) bundle.getInt(key, 0).toString() else null
    }

    /**
     * An independent copy.
     */
    fun copy(): ModuleSettings = ModuleSettings().also { s ->
        s.enabled = enabled
        s.mergeIcons = mergeIcons
        s.showWifi = showWifi
        s.showMobile = showMobile
        s.showValue = showValue
        s.showBolt = showBolt
        s.showMobileActivity = showMobileActivity
        s.typeHideOnWifi = typeHideOnWifi
        s.chargingShowsValue = chargingShowsValue
        s.perfTempChargingOnly = perfTempChargingOnly
        s.wifiScale = wifiScale
        s.capsuleLength = capsuleLength
        s.capsuleLeftDx = capsuleLeftDx
        s.capsuleLeftDy = capsuleLeftDy
        s.capsuleRightDx = capsuleRightDx
        s.capsuleRightDy = capsuleRightDy
        s.ringSpacingX = ringSpacingX
        s.ringSpacingY = ringSpacingY
        s.mobileTypeMode = mobileTypeMode
        s.valueCentred = valueCentred
        s.navStyle = navStyle
        s.navLabels = navLabels
        s.trioStyle = trioStyle
        s.ringZones = copyZones(ringZones)
        s.dualSim = dualSim
        s.signalMode = signalMode
        s.stackedSignal = stackedSignal
        s.showSim1 = showSim1
        s.showSim2 = showSim2
        s.dataSimOnly = dataSimOnly
        s.showPercent = showPercent
        s.capGap = capGap
        s.perfPower = perfPower
        s.perfRunning = perfRunning
        s.clockSeconds = clockSeconds
        s.clockDate = clockDate
        s.clockWeekday = clockWeekday
        s.clockTopOffsetX = clockTopOffsetX
        s.clockBottomOffsetX = clockBottomOffsetX
        s.clockLunarGap = clockLunarGap
        s.clockRowGap = clockRowGap
        s.clockLunar = clockLunar
        s.clockDateFormat = clockDateFormat
        s.clockWeekdayFormat = clockWeekdayFormat
        s.clockWeekdayColorOnDark = clockWeekdayColorOnDark
        s.clockWeekdayColorOnLight = clockWeekdayColorOnLight
        s.clockDateColorOnDark = clockDateColorOnDark
        s.clockDateColorOnLight = clockDateColorOnLight
        s.clockLunarColorOnDark = clockLunarColorOnDark
        s.clockLunarColorOnLight = clockLunarColorOnLight
        s.clockWeather = clockWeather
        s.clockWeatherStyle = clockWeatherStyle
        s.clockStyle = clockStyle
        s.clockWeatherColorOnDark = clockWeatherColorOnDark
        s.clockWeatherColorOnLight = clockWeatherColorOnLight
        s.clockDigitColorsOnDark = clockDigitColorsOnDark.copyOf()
        s.clockDigitColorsOnLight = clockDigitColorsOnLight.copyOf()
        s.shadeHidePower = shadeHidePower
        s.shadeHideBell = shadeHideBell
        s.shadeLunar = shadeLunar
        s.shadeWeather = shadeWeather
        s.shadeWeatherStyle = shadeWeatherStyle
        s.shadeWeatherPosition = shadeWeatherPosition
        s.shadeWeatherClick = shadeWeatherClick
        s.effectiveScope = effectiveScope
        s.capsuleNoScale = capsuleNoScale
        s.hideHdVolte = hideHdVolte
        s.hideHdVowifi = hideHdVowifi
        s.ccSliderLongPress = ccSliderLongPress
        s.btBatteryMode = btBatteryMode
        s.shadeHideChargeNotif = shadeHideChargeNotif
        s.shadeHideHotspotNotif = shadeHideHotspotNotif
        s.shadeHideAdbNotif = shadeHideAdbNotif
        s.perfSignal = perfSignal
        s.perfTemp = perfTemp
        s.perfFps = perfFps
        s.perfCurrent = perfCurrent
        s.perfVoltage = perfVoltage
        s.currentPositive = currentPositive
        s.perfScale = perfScale
        s.perfWeight = perfWeight
        s.perfLineGap = perfLineGap
        s.perfRefreshSec = perfRefreshSec
        s.glyphScale = glyphScale
        s.roleColors = roleColors
        s.criticalOnDark = criticalOnDark
        s.criticalOnLight = criticalOnLight
        s.chargingOnDark = chargingOnDark
        s.chargingOnLight = chargingOnLight
        s.lowOnDark = lowOnDark
        s.lowOnLight = lowOnLight
        s.lowThreshold = lowThreshold
        s.roleColorsTemp = roleColorsTemp
        s.tempThreshold = tempThreshold
        s.highTempOnDark = highTempOnDark
        s.highTempOnLight = highTempOnLight
        s.ringStroke = ringStroke
        s.arcStroke = arcStroke
        s.valueSize = valueSize
        s.valueWeight = valueWeight
        s.clockSize = clockSize
        s.clockWeight = clockWeight
        s.typeSize = typeSize
        s.outTypeSize = outTypeSize
        s.outSignalSize = outSignalSize
        s.outDotSize = outDotSize
        s.stackStyle = stackStyle
        s.stackLabel = stackLabel
        s.antiBurnIn = antiBurnIn
        s.signalColumns = signalColumns
        s.fontPath = fontPath
        s.outTypeMarginLeft = outTypeMarginLeft
        s.outTypeMarginRight = outTypeMarginRight
        s.outSignalMargin = outSignalMargin
        s.typeSuffixScale = typeSuffixScale
        s.boltScale = boltScale
        s.boltAnim = boltAnim
        s.boltSpeed = boltSpeed
        s.dotScale = dotScale
        s.outOrder = outOrder.clone()
        s.perfOrder = perfOrder.clone()
        s.typeWeight = typeWeight
        s.trackAlpha = trackAlpha
        s.debugLog = debugLog
    }

    /**
     * This snapshot's component state for the other arrangement.
     *
     * Every field except the switches and the layout is carried over unchanged,
     * because the rest are properties of the components themselves rather than of
     * the shape - a 40pt digit is 40pt in both. Exists for the settings screen's own
     * preview: a style tile that drew the current arrangement's values under the
     * other shape's name would advertise a look that clicking it does not produce.
     */
    fun withComponentsFrom(other: ModuleSettings?, style: Int): ModuleSettings {
        if (other == null) return this
        return copy().also { s ->
            s.trioStyle = Prefs.normalizeTrioStyle(style)
            s.showWifi = other.showWifi
            s.showMobile = other.showMobile
            s.showValue = other.showValue
            s.showBolt = other.showBolt
            s.showSim1 = other.showSim1
            s.showSim2 = other.showSim2
            s.mobileTypeMode = other.mobileTypeMode
            s.valueCentred = other.valueCentred
            s.dualSim = other.dualSim
            s.signalMode = other.signalMode
            s.stackedSignal = other.stackedSignal
            s.ringZones = copyZones(other.ringZones)
        }
    }

    /**
     * Copies the one field named by `key` out of `src`.
     *
     * The framework's remote map is only guaranteed fresh for the key it just
     * reported: the daemon merges a diff and fires one callback per key, so a full
     * re-read here would take stale values for every key whose diff never arrived.
     * That is exactly how a value published by another path can be silently
     * reverted by the next unrelated change.
     *
     * @return true when the key maps to a field of this type
     */
    fun applyKeyFrom(src: ModuleSettings?, key: String?): Boolean {
        if (src == null || key == null) return false
        // A per-arrangement key arrives suffixed when a capsule value changed, and
        // the field it writes is the same one the unsuffixed key writes: the snapshot
        // holds the CURRENT arrangement's values.
        val k = if (Prefs.isCapsuleKey(key)) Prefs.styledBase(key) else key
        when (k) {
            Prefs.KEY_ENABLED -> enabled = src.enabled
            Prefs.KEY_MERGE_ICONS -> mergeIcons = src.mergeIcons
            Prefs.KEY_SHOW_WIFI -> showWifi = src.showWifi
            Prefs.KEY_SHOW_MOBILE -> showMobile = src.showMobile
            Prefs.KEY_SHOW_VALUE -> showValue = src.showValue
            Prefs.KEY_SHOW_BOLT -> showBolt = src.showBolt
            Prefs.KEY_TYPE_HIDE_ON_WIFI -> typeHideOnWifi = src.typeHideOnWifi
            Prefs.KEY_CHARGING_SHOWS_VALUE -> chargingShowsValue = src.chargingShowsValue
            Prefs.KEY_PERF_TEMP_CHARGING_ONLY -> perfTempChargingOnly = src.perfTempChargingOnly
            Prefs.KEY_WIFI_SCALE -> wifiScale = src.wifiScale
            Prefs.KEY_CAPSULE_LENGTH -> capsuleLength = src.capsuleLength
            Prefs.KEY_CAPSULE_LEFT_DX -> capsuleLeftDx = src.capsuleLeftDx
            Prefs.KEY_CAPSULE_LEFT_DY -> capsuleLeftDy = src.capsuleLeftDy
            Prefs.KEY_CAPSULE_RIGHT_DX -> capsuleRightDx = src.capsuleRightDx
            Prefs.KEY_CAPSULE_RIGHT_DY -> capsuleRightDy = src.capsuleRightDy
            Prefs.KEY_RING_SPACING_X -> ringSpacingX = src.ringSpacingX
            Prefs.KEY_RING_SPACING_Y -> ringSpacingY = src.ringSpacingY
            Prefs.KEY_SHOW_MOBILE_ACTIVITY -> showMobileActivity = src.showMobileActivity
            Prefs.KEY_MOBILE_TYPE_MODE -> mobileTypeMode = src.mobileTypeMode
            Prefs.KEY_VALUE_CENTRED -> valueCentred = src.valueCentred
            Prefs.KEY_NAV_STYLE -> navStyle = Prefs.normalizeNavStyle(src.navStyle)
            Prefs.KEY_NAV_LABELS -> navLabels = Prefs.normalizeNavLabels(src.navLabels)
            Prefs.KEY_TRIO_STYLE -> trioStyle = Prefs.normalizeTrioStyle(src.trioStyle)
            Prefs.KEY_RING_LAYOUT -> ringZones = copyZones(src.ringZones)
            Prefs.KEY_DUAL_SIM -> dualSim = src.dualSim
            Prefs.KEY_SIGNAL_MODE -> signalMode = src.signalMode
            Prefs.KEY_STACKED_SIGNAL -> stackedSignal = src.stackedSignal
            Prefs.KEY_SHOW_SIM_1 -> showSim1 = src.showSim1
            Prefs.KEY_SHOW_SIM_2 -> showSim2 = src.showSim2
            Prefs.KEY_DATA_SIM_ONLY -> dataSimOnly = src.dataSimOnly
            Prefs.KEY_SHOW_PERCENT -> showPercent = src.showPercent
            Prefs.KEY_CAP_GAP -> capGap = src.capGap
            Prefs.KEY_PERF_POWER -> perfPower = src.perfPower
            Prefs.KEY_PERF_RUNNING -> perfRunning = src.perfRunning
            Prefs.KEY_CLOCK_SECONDS -> clockSeconds = src.clockSeconds
            Prefs.KEY_CLOCK_DATE -> clockDate = src.clockDate
            Prefs.KEY_CLOCK_WEEKDAY -> clockWeekday = src.clockWeekday
            Prefs.KEY_CLOCK_TOP_OFFSET_X -> clockTopOffsetX = src.clockTopOffsetX
            Prefs.KEY_CLOCK_BOTTOM_OFFSET_X -> clockBottomOffsetX = src.clockBottomOffsetX
            Prefs.KEY_CLOCK_LUNAR_GAP -> clockLunarGap = src.clockLunarGap
            Prefs.KEY_CLOCK_ROW_GAP -> clockRowGap = src.clockRowGap
            Prefs.KEY_CLOCK_LUNAR -> clockLunar = src.clockLunar
            Prefs.KEY_CLOCK_DATE_FORMAT -> clockDateFormat = src.clockDateFormat
            Prefs.KEY_CLOCK_WEEKDAY_FORMAT -> clockWeekdayFormat = src.clockWeekdayFormat
            Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK -> clockWeekdayColorOnDark = src.clockWeekdayColorOnDark
            Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT -> clockWeekdayColorOnLight = src.clockWeekdayColorOnLight
            Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK -> clockDateColorOnDark = src.clockDateColorOnDark
            Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT -> clockDateColorOnLight = src.clockDateColorOnLight
            Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK -> clockLunarColorOnDark = src.clockLunarColorOnDark
            Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT -> clockLunarColorOnLight = src.clockLunarColorOnLight
            Prefs.KEY_CLOCK_WEATHER -> clockWeather = src.clockWeather
            Prefs.KEY_CLOCK_WEATHER_STYLE -> clockWeatherStyle = src.clockWeatherStyle
            Prefs.KEY_CLOCK_STYLE -> clockStyle = src.clockStyle
            Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK -> clockWeatherColorOnDark = src.clockWeatherColorOnDark
            Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT -> clockWeatherColorOnLight = src.clockWeatherColorOnLight
            Prefs.KEY_CLOCK_DIGIT_COLORS -> clockDigitColorsOnDark = src.clockDigitColorsOnDark.copyOf()
            Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT -> clockDigitColorsOnLight = src.clockDigitColorsOnLight.copyOf()
            Prefs.KEY_SHADE_HIDE_POWER -> shadeHidePower = src.shadeHidePower
            Prefs.KEY_SHADE_HIDE_BELL -> shadeHideBell = src.shadeHideBell
            Prefs.KEY_SHADE_LUNAR -> shadeLunar = src.shadeLunar
            Prefs.KEY_SHADE_WEATHER -> shadeWeather = src.shadeWeather
            Prefs.KEY_SHADE_WEATHER_STYLE -> shadeWeatherStyle = src.shadeWeatherStyle
            Prefs.KEY_SHADE_WEATHER_POSITION -> shadeWeatherPosition = src.shadeWeatherPosition
            Prefs.KEY_SHADE_WEATHER_CLICK -> shadeWeatherClick = src.shadeWeatherClick
            Prefs.KEY_EFFECTIVE_SCOPE -> effectiveScope = src.effectiveScope
            Prefs.KEY_CAPSULE_NO_SCALE -> capsuleNoScale = src.capsuleNoScale
            Prefs.KEY_HIDE_HD_VOLTE -> hideHdVolte = src.hideHdVolte
            Prefs.KEY_HIDE_HD_VOWIFI -> hideHdVowifi = src.hideHdVowifi
            Prefs.KEY_CC_SLIDER_LONG_PRESS -> ccSliderLongPress = src.ccSliderLongPress
            Prefs.KEY_BT_BATTERY_MODE -> btBatteryMode = src.btBatteryMode
            Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF -> shadeHideChargeNotif = src.shadeHideChargeNotif
            Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF -> shadeHideHotspotNotif = src.shadeHideHotspotNotif
            Prefs.KEY_SHADE_HIDE_ADB_NOTIF -> shadeHideAdbNotif = src.shadeHideAdbNotif
            Prefs.KEY_PERF_SIGNAL -> perfSignal = src.perfSignal
            Prefs.KEY_PERF_TEMP -> perfTemp = src.perfTemp
            Prefs.KEY_PERF_FPS -> perfFps = src.perfFps
            Prefs.KEY_PERF_CURRENT -> perfCurrent = src.perfCurrent
            Prefs.KEY_PERF_VOLTAGE -> perfVoltage = src.perfVoltage
            Prefs.KEY_CURRENT_POSITIVE -> currentPositive = src.currentPositive
            Prefs.KEY_PERF_SCALE -> perfScale = src.perfScale
            Prefs.KEY_PERF_WEIGHT -> perfWeight = src.perfWeight
            Prefs.KEY_PERF_LINE_GAP -> perfLineGap = src.perfLineGap
            Prefs.KEY_PERF_REFRESH_SEC -> perfRefreshSec = src.perfRefreshSec
            Prefs.KEY_GLYPH_SCALE -> glyphScale = src.glyphScale
            Prefs.KEY_ROLE_COLORS -> roleColors = src.roleColors
            Prefs.KEY_COLOR_CRITICAL_ON_DARK -> criticalOnDark = src.criticalOnDark
            Prefs.KEY_COLOR_CRITICAL_ON_LIGHT -> criticalOnLight = src.criticalOnLight
            Prefs.KEY_COLOR_CHARGING_ON_DARK -> chargingOnDark = src.chargingOnDark
            Prefs.KEY_COLOR_CHARGING_ON_LIGHT -> chargingOnLight = src.chargingOnLight
            Prefs.KEY_COLOR_LOW_ON_DARK -> lowOnDark = src.lowOnDark
            Prefs.KEY_COLOR_LOW_ON_LIGHT -> lowOnLight = src.lowOnLight
            Prefs.KEY_LOW_THRESHOLD -> lowThreshold = src.lowThreshold
            Prefs.KEY_ROLE_COLORS_TEMP -> roleColorsTemp = src.roleColorsTemp
            Prefs.KEY_TEMP_THRESHOLD -> tempThreshold = src.tempThreshold
            Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK -> highTempOnDark = src.highTempOnDark
            Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT -> highTempOnLight = src.highTempOnLight
            Prefs.KEY_RING_STROKE -> ringStroke = src.ringStroke
            Prefs.KEY_ARC_STROKE -> arcStroke = src.arcStroke
            Prefs.KEY_VALUE_SIZE -> valueSize = src.valueSize
            Prefs.KEY_VALUE_WEIGHT -> valueWeight = src.valueWeight
            Prefs.KEY_CLOCK_SIZE -> clockSize = src.clockSize
            Prefs.KEY_CLOCK_WEIGHT -> clockWeight = src.clockWeight
            Prefs.KEY_TYPE_SIZE -> typeSize = src.typeSize
            Prefs.KEY_OUT_TYPE_SIZE_DP -> outTypeSize = src.outTypeSize
            Prefs.KEY_OUT_SIGNAL_SIZE -> outSignalSize = src.outSignalSize
            Prefs.KEY_OUT_DOT_SIZE -> outDotSize = src.outDotSize
            Prefs.KEY_STACK_STYLE -> stackStyle = src.stackStyle
            Prefs.KEY_STACK_LABEL -> stackLabel = src.stackLabel
            Prefs.KEY_SIGNAL_COLUMNS -> signalColumns = src.signalColumns
            Prefs.KEY_ANTI_BURN_IN -> antiBurnIn = src.antiBurnIn
            Prefs.KEY_FONT_PATH -> fontPath = src.fontPath
            Prefs.KEY_OUT_TYPE_MARGIN_LEFT -> outTypeMarginLeft = src.outTypeMarginLeft
            Prefs.KEY_OUT_TYPE_MARGIN_RIGHT -> outTypeMarginRight = src.outTypeMarginRight
            Prefs.KEY_OUT_SIGNAL_MARGIN -> outSignalMargin = src.outSignalMargin
            Prefs.KEY_TYPE_SUFFIX_SCALE -> typeSuffixScale = src.typeSuffixScale
            Prefs.KEY_BOLT_SCALE -> boltScale = src.boltScale
            Prefs.KEY_BOLT_ANIM -> boltAnim = src.boltAnim
            Prefs.KEY_BOLT_SPEED -> boltSpeed = src.boltSpeed
            Prefs.KEY_DOT_SCALE -> dotScale = src.dotScale
            Prefs.KEY_OUT_ORDER -> outOrder = src.outOrder.clone()
            Prefs.KEY_PERF_ORDER -> perfOrder = src.perfOrder.clone()
            Prefs.KEY_TYPE_WEIGHT -> typeWeight = src.typeWeight
            Prefs.KEY_TRACK_ALPHA -> trackAlpha = src.trackAlpha
            Prefs.KEY_DEBUG_LOG -> debugLog = src.debugLog
            Prefs.KEY_REPLACE_WIFI_ICON -> replaceWifiIcon = src.replaceWifiIcon
            Prefs.KEY_REPLACE_STATUS_ICON -> replaceStatusIcon = src.replaceStatusIcon
            Prefs.KEY_HIDE_STATUS_CLOCK -> hideStatusClock = src.hideStatusClock
            Prefs.KEY_HIDE_ALARM_ICON -> hideAlarmIcon = src.hideAlarmIcon
            Prefs.KEY_HIDE_PRIVACY_CHIP -> hidePrivacyChip = src.hidePrivacyChip
            Prefs.KEY_HIDE_BLUETOOTH_ICON -> hideBluetoothIcon = src.hideBluetoothIcon
            else -> return false
        }
        return true
    }

    /**
     * The inverse of [fromBundle], using the same key names so the two sides cannot
     * drift apart. The settings app sends this to the hooked process after every
     * write.
     */
    fun toBundle(): Bundle = Bundle().apply {
        // The style first: the component switches below are written under the CURRENT
        // arrangement's key, so the hooked process has to be able to pick them out.
        val style = Prefs.normalizeTrioStyle(trioStyle)
        putInt(Prefs.KEY_NAV_STYLE, Prefs.normalizeNavStyle(navStyle))
        putInt(Prefs.KEY_NAV_LABELS, Prefs.normalizeNavLabels(navLabels))
        putInt(Prefs.KEY_TRIO_STYLE, style)
        putBoolean(Prefs.KEY_ENABLED, enabled)
        putBoolean(Prefs.KEY_MERGE_ICONS, mergeIcons)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, style), showWifi)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, style), showMobile)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), showValue)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, style), showBolt)
        putBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, typeHideOnWifi)
        putBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE, chargingShowsValue)
        putBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, perfTempChargingOnly)
        putInt(Prefs.KEY_WIFI_SCALE, wifiScale)
        putInt(Prefs.KEY_CAPSULE_LENGTH, capsuleLength)
        putInt(Prefs.KEY_CAPSULE_LEFT_DX, capsuleLeftDx)
        putInt(Prefs.KEY_CAPSULE_LEFT_DY, capsuleLeftDy)
        putInt(Prefs.KEY_CAPSULE_RIGHT_DX, capsuleRightDx)
        putInt(Prefs.KEY_CAPSULE_RIGHT_DY, capsuleRightDy)
        putInt(Prefs.KEY_RING_SPACING_X, ringSpacingX)
        putInt(Prefs.KEY_RING_SPACING_Y, ringSpacingY)
        putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style), mobileTypeMode)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE_ACTIVITY, style), showMobileActivity)
        putBoolean(Prefs.styledKey(Prefs.KEY_VALUE_CENTRED, style), valueCentred)
        putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style), Prefs.encodeLayout(ringZones))
        putBoolean(Prefs.styledKey(Prefs.KEY_DUAL_SIM, style), dualSim)
        putInt(
            Prefs.styledKey(Prefs.KEY_SIGNAL_MODE, style),
            Prefs.clamp(signalMode, Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE),
        )
        putBoolean(Prefs.styledKey(Prefs.KEY_STACKED_SIGNAL, style), stackedSignal)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, style), showSim1)
        putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, style), showSim2)
        putBoolean(Prefs.KEY_DATA_SIM_ONLY, dataSimOnly)
        putBoolean(Prefs.KEY_SHOW_PERCENT, showPercent)
        putInt(Prefs.KEY_CAP_GAP, capGap)
        putBoolean(Prefs.KEY_PERF_POWER, perfPower)
        putBoolean(Prefs.KEY_PERF_RUNNING, perfRunning)
        putBoolean(Prefs.KEY_CLOCK_SECONDS, clockSeconds)
        putBoolean(Prefs.KEY_CLOCK_DATE, clockDate)
        putBoolean(Prefs.KEY_CLOCK_WEEKDAY, clockWeekday)
        putInt(Prefs.KEY_CLOCK_TOP_OFFSET_X, clockTopOffsetX)
        putInt(Prefs.KEY_CLOCK_BOTTOM_OFFSET_X, clockBottomOffsetX)
        putInt(Prefs.KEY_CLOCK_LUNAR_GAP, clockLunarGap)
        putInt(Prefs.KEY_CLOCK_ROW_GAP, clockRowGap)
        putBoolean(Prefs.KEY_CLOCK_LUNAR, clockLunar)
        putInt(Prefs.KEY_CLOCK_DATE_FORMAT, clockDateFormat)
        putInt(Prefs.KEY_CLOCK_WEEKDAY_FORMAT, clockWeekdayFormat)
        putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_DARK, clockWeekdayColorOnDark)
        putInt(Prefs.KEY_COLOR_CLOCK_WEEKDAY_ON_LIGHT, clockWeekdayColorOnLight)
        putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_DARK, clockDateColorOnDark)
        putInt(Prefs.KEY_COLOR_CLOCK_DATE_ON_LIGHT, clockDateColorOnLight)
        putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_DARK, clockLunarColorOnDark)
        putInt(Prefs.KEY_COLOR_CLOCK_LUNAR_ON_LIGHT, clockLunarColorOnLight)
        putBoolean(Prefs.KEY_CLOCK_WEATHER, clockWeather)
        putInt(Prefs.KEY_CLOCK_WEATHER_STYLE, clockWeatherStyle)
        putInt(Prefs.KEY_CLOCK_STYLE, clockStyle)
        putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_DARK, clockWeatherColorOnDark)
        putInt(Prefs.KEY_COLOR_CLOCK_WEATHER_ON_LIGHT, clockWeatherColorOnLight)
        putString(Prefs.KEY_CLOCK_DIGIT_COLORS, Prefs.encodeClockDigitColors(clockDigitColorsOnDark))
        putString(Prefs.KEY_CLOCK_DIGIT_COLORS_LIGHT, Prefs.encodeClockDigitColors(clockDigitColorsOnLight))
        putBoolean(Prefs.KEY_SHADE_HIDE_POWER, shadeHidePower)
        putBoolean(Prefs.KEY_SHADE_HIDE_BELL, shadeHideBell)
        putBoolean(Prefs.KEY_SHADE_LUNAR, shadeLunar)
        putBoolean(Prefs.KEY_SHADE_WEATHER, shadeWeather)
        putInt(Prefs.KEY_SHADE_WEATHER_STYLE, shadeWeatherStyle)
        putInt(Prefs.KEY_SHADE_WEATHER_POSITION, shadeWeatherPosition)
        putInt(Prefs.KEY_SHADE_WEATHER_CLICK, shadeWeatherClick)
        putInt(Prefs.KEY_EFFECTIVE_SCOPE, effectiveScope)
        putBoolean(Prefs.KEY_CAPSULE_NO_SCALE, capsuleNoScale)
        putBoolean(Prefs.KEY_HIDE_HD_VOLTE, hideHdVolte)
        putBoolean(Prefs.KEY_HIDE_HD_VOWIFI, hideHdVowifi)
        putBoolean(Prefs.KEY_CC_SLIDER_LONG_PRESS, ccSliderLongPress)
        putInt(Prefs.KEY_BT_BATTERY_MODE, btBatteryMode)
        putBoolean(Prefs.KEY_SHADE_HIDE_CHARGE_NOTIF, shadeHideChargeNotif)
        putBoolean(Prefs.KEY_SHADE_HIDE_HOTSPOT_NOTIF, shadeHideHotspotNotif)
        putBoolean(Prefs.KEY_SHADE_HIDE_ADB_NOTIF, shadeHideAdbNotif)
        putBoolean(Prefs.KEY_PERF_SIGNAL, perfSignal)
        putBoolean(Prefs.KEY_PERF_TEMP, perfTemp)
        putBoolean(Prefs.KEY_PERF_FPS, perfFps)
        putBoolean(Prefs.KEY_PERF_CURRENT, perfCurrent)
        putBoolean(Prefs.KEY_PERF_VOLTAGE, perfVoltage)
        putBoolean(Prefs.KEY_CURRENT_POSITIVE, currentPositive)
        putInt(Prefs.KEY_PERF_SCALE, perfScale)
        putInt(Prefs.KEY_PERF_WEIGHT, perfWeight)
        putInt(Prefs.KEY_PERF_LINE_GAP, perfLineGap)
        putInt(Prefs.KEY_PERF_REFRESH_SEC, perfRefreshSec)
        putInt(Prefs.KEY_GLYPH_SCALE, glyphScale)
        putBoolean(Prefs.KEY_ROLE_COLORS, roleColors)
        putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, criticalOnDark)
        putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, criticalOnLight)
        putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, chargingOnDark)
        putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, chargingOnLight)
        putInt(Prefs.KEY_COLOR_LOW_ON_DARK, lowOnDark)
        putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, lowOnLight)
        putInt(Prefs.KEY_LOW_THRESHOLD, lowThreshold)
        putBoolean(Prefs.KEY_ROLE_COLORS_TEMP, roleColorsTemp)
        putInt(Prefs.KEY_TEMP_THRESHOLD, tempThreshold)
        putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_DARK, highTempOnDark)
        putInt(Prefs.KEY_COLOR_HIGH_TEMP_ON_LIGHT, highTempOnLight)
        putInt(Prefs.KEY_RING_STROKE, ringStroke)
        putInt(Prefs.KEY_ARC_STROKE, arcStroke)
        putInt(Prefs.KEY_VALUE_SIZE, valueSize)
        putInt(Prefs.KEY_VALUE_WEIGHT, valueWeight)
        putInt(Prefs.KEY_CLOCK_SIZE, clockSize)
        putInt(Prefs.KEY_CLOCK_WEIGHT, clockWeight)
        putInt(Prefs.KEY_TYPE_SIZE, typeSize)
        putInt(Prefs.KEY_OUT_TYPE_SIZE_DP, outTypeSize)
        putInt(Prefs.KEY_OUT_SIGNAL_SIZE, outSignalSize)
        putInt(Prefs.KEY_OUT_DOT_SIZE, outDotSize)
        putInt(Prefs.KEY_STACK_STYLE, stackStyle)
        putInt(Prefs.KEY_STACK_LABEL, stackLabel)
        putBoolean(Prefs.KEY_ANTI_BURN_IN, antiBurnIn)
        putInt(Prefs.KEY_SIGNAL_COLUMNS, signalColumns)
        putString(Prefs.KEY_FONT_PATH, fontPath)
        putInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, outTypeMarginLeft)
        putInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, outTypeMarginRight)
        putInt(Prefs.KEY_OUT_SIGNAL_MARGIN, outSignalMargin)
        putInt(Prefs.KEY_TYPE_SUFFIX_SCALE, typeSuffixScale)
        putInt(Prefs.KEY_BOLT_SCALE, boltScale)
        putInt(Prefs.KEY_BOLT_ANIM, boltAnim)
        putInt(Prefs.KEY_BOLT_SPEED, boltSpeed)
        putInt(Prefs.KEY_DOT_SCALE, dotScale)
        putString(Prefs.KEY_OUT_ORDER, Prefs.encodeOutOrder(outOrder))
        putString(Prefs.KEY_PERF_ORDER, Prefs.encodePerfOrder(perfOrder))
        putInt(Prefs.KEY_TYPE_WEIGHT, typeWeight)
        putInt(Prefs.KEY_TRACK_ALPHA, trackAlpha)
        putBoolean(Prefs.KEY_DEBUG_LOG, debugLog)
        putBoolean(Prefs.KEY_REPLACE_WIFI_ICON, replaceWifiIcon)
        putBoolean(Prefs.KEY_REPLACE_STATUS_ICON, replaceStatusIcon)
        putBoolean(Prefs.KEY_HIDE_STATUS_CLOCK, hideStatusClock)
        putBoolean(Prefs.KEY_HIDE_ALARM_ICON, hideAlarmIcon)
        putBoolean(Prefs.KEY_HIDE_PRIVACY_CHIP, hidePrivacyChip)
        putBoolean(Prefs.KEY_HIDE_BLUETOOTH_ICON, hideBluetoothIcon)
    }

    /**
     * Field-wise equality.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModuleSettings) return false
        // Content equality on arrays first
        if (!ringZones.contentDeepEquals(other.ringZones)) return false
        if (!outOrder.contentEquals(other.outOrder)) return false
        if (!perfOrder.contentEquals(other.perfOrder)) return false
        if (!clockDigitColorsOnDark.contentEquals(other.clockDigitColorsOnDark)) return false
        if (!clockDigitColorsOnLight.contentEquals(other.clockDigitColorsOnLight)) return false

        // Scalar fields grouped by domain
        return enabled == other.enabled && mergeIcons == other.mergeIcons && fontPath == other.fontPath &&
            debugLog == other.debugLog && antiBurnIn == other.antiBurnIn &&
            // Styles & layout dimensions
            trioStyle == other.trioStyle && navStyle == other.navStyle && navLabels == other.navLabels &&
            glyphScale == other.glyphScale && dotScale == other.dotScale && ringStroke == other.ringStroke &&
            arcStroke == other.arcStroke && ringSpacingX == other.ringSpacingX && ringSpacingY == other.ringSpacingY &&
            capsuleLength == other.capsuleLength && capGap == other.capGap &&
            capsuleLeftDx == other.capsuleLeftDx && capsuleLeftDy == other.capsuleLeftDy &&
            capsuleRightDx == other.capsuleRightDx && capsuleRightDy == other.capsuleRightDy &&
            // Components & display options
            showWifi == other.showWifi && showMobile == other.showMobile && showMobileActivity == other.showMobileActivity &&
            showValue == other.showValue && showBolt == other.showBolt && showPercent == other.showPercent &&
            wifiScale == other.wifiScale && valueCentred == other.valueCentred && valueSize == other.valueSize &&
            valueWeight == other.valueWeight && boltScale == other.boltScale && boltAnim == other.boltAnim &&
            boltSpeed == other.boltSpeed && chargingShowsValue == other.chargingShowsValue &&
            // Signal & mobile type
            dualSim == other.dualSim && signalMode == other.signalMode && stackedSignal == other.stackedSignal &&
            showSim1 == other.showSim1 && showSim2 == other.showSim2 && dataSimOnly == other.dataSimOnly &&
            signalColumns == other.signalColumns && mobileTypeMode == other.mobileTypeMode &&
            typeHideOnWifi == other.typeHideOnWifi && typeSize == other.typeSize && typeWeight == other.typeWeight &&
            typeSuffixScale == other.typeSuffixScale && outTypeSize == other.outTypeSize &&
            outTypeMarginLeft == other.outTypeMarginLeft && outTypeMarginRight == other.outTypeMarginRight &&
            outSignalSize == other.outSignalSize && outDotSize == other.outDotSize && outSignalMargin == other.outSignalMargin &&
            stackStyle == other.stackStyle && stackLabel == other.stackLabel && trackAlpha == other.trackAlpha &&
            // Performance readings
            perfPower == other.perfPower && perfRunning == other.perfRunning && perfTemp == other.perfTemp &&
            perfTempChargingOnly == other.perfTempChargingOnly && perfFps == other.perfFps &&
            perfCurrent == other.perfCurrent && perfVoltage == other.perfVoltage && perfSignal == other.perfSignal && currentPositive == other.currentPositive &&
            currentUnit == other.currentUnit && perfScale == other.perfScale && perfWeight == other.perfWeight &&
            perfLineGap == other.perfLineGap && perfRefreshSec == other.perfRefreshSec &&
            // Clock & shade
            clockSeconds == other.clockSeconds && clockDate == other.clockDate && clockWeekday == other.clockWeekday &&
            clockLunar == other.clockLunar && clockDateFormat == other.clockDateFormat &&
            clockWeekdayFormat == other.clockWeekdayFormat && clockWeather == other.clockWeather &&
            clockWeatherStyle == other.clockWeatherStyle && clockStyle == other.clockStyle &&
            clockTopOffsetX == other.clockTopOffsetX && clockBottomOffsetX == other.clockBottomOffsetX &&
            clockLunarGap == other.clockLunarGap && clockRowGap == other.clockRowGap && clockSize == other.clockSize &&
            clockWeight == other.clockWeight && hideStatusClock == other.hideStatusClock &&
            shadeHidePower == other.shadeHidePower && shadeHideBell == other.shadeHideBell &&
            shadeLunar == other.shadeLunar && shadeWeather == other.shadeWeather &&
            shadeWeatherStyle == other.shadeWeatherStyle && replaceWifiIcon == other.replaceWifiIcon &&
            // New pull-down panel appearance, capsule and signal keys: without these the
            // snapshot compares equal after a write and Compose skips the recomposition.
            shadeWeatherPosition == other.shadeWeatherPosition && shadeWeatherClick == other.shadeWeatherClick &&
            effectiveScope == other.effectiveScope && capsuleNoScale == other.capsuleNoScale &&
            hideHdVolte == other.hideHdVolte && hideHdVowifi == other.hideHdVowifi &&
            ccSliderLongPress == other.ccSliderLongPress &&
            btBatteryMode == other.btBatteryMode &&
            shadeHideChargeNotif == other.shadeHideChargeNotif && shadeHideHotspotNotif == other.shadeHideHotspotNotif &&
            shadeHideAdbNotif == other.shadeHideAdbNotif &&
            replaceStatusIcon == other.replaceStatusIcon && hideAlarmIcon == other.hideAlarmIcon &&
            hidePrivacyChip == other.hidePrivacyChip &&
            hideBluetoothIcon == other.hideBluetoothIcon &&
            // Colors & thresholds
            roleColors == other.roleColors && roleColorsTemp == other.roleColorsTemp && lowThreshold == other.lowThreshold &&
            tempThreshold == other.tempThreshold && criticalOnDark == other.criticalOnDark &&
            criticalOnLight == other.criticalOnLight && chargingOnDark == other.chargingOnDark &&
            chargingOnLight == other.chargingOnLight && lowOnDark == other.lowOnDark && lowOnLight == other.lowOnLight &&
            highTempOnDark == other.highTempOnDark && highTempOnLight == other.highTempOnLight &&
            clockWeekdayColorOnDark == other.clockWeekdayColorOnDark && clockWeekdayColorOnLight == other.clockWeekdayColorOnLight &&
            clockDateColorOnDark == other.clockDateColorOnDark && clockDateColorOnLight == other.clockDateColorOnLight &&
            clockLunarColorOnDark == other.clockLunarColorOnDark && clockLunarColorOnLight == other.clockLunarColorOnLight &&
            clockWeatherColorOnDark == other.clockWeatherColorOnDark && clockWeatherColorOnLight == other.clockWeatherColorOnLight
    }

    override fun hashCode(): Int {
        var h = (if (enabled) 1 else 0) xor (mobileTypeMode shl 1) xor trioStyle
        h = h * 31 + (if (perfPower) 1 else 0)
        h = h * 31 + (if (perfRunning) 1 else 0)
        h = h * 31 + (if (perfTemp) 1 else 0)
        h = h * 31 + (if (perfCurrent) 1 else 0)
        h = h * 31 + (if (perfVoltage) 1 else 0)
        h = h * 31 + (if (perfFps) 1 else 0)
        h = h * 31 + (if (perfSignal) 1 else 0)
        h = h * 31 + currentUnit
        h = h * 31 + perfScale
        h = h * 31 + effectiveScope
        h = h * 31 + btBatteryMode
        h = h * 31 + shadeWeatherPosition
        h = h * 31 + shadeWeatherClick
        h = h * 31 + (if (capsuleNoScale) 1 else 0)
        h = h * 31 + (if (hideHdVolte) 1 else 0)
        h = h * 31 + (if (hideHdVowifi) 1 else 0)
        h = h * 31 + (if (ccSliderLongPress) 1 else 0)
        h = h * 31 + (if (shadeHideChargeNotif) 1 else 0)
        h = h * 31 + (if (shadeHideHotspotNotif) 1 else 0)
        h = h * 31 + (if (shadeHideAdbNotif) 1 else 0)
        h = h * 31 + ringZones.contentDeepHashCode()
        h = h * 31 + perfOrder.contentHashCode()
        return if (h != 0) h else 1
    }
}
