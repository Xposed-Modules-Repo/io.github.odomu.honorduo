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

package io.github.odomu.honorduo.hook.statusbar

import android.content.Context
import android.icu.util.ChineseCalendar
import android.os.Handler
import android.os.Looper
import io.github.odomu.honorduo.hook.core.SystemUiHook
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker.addListener
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker.isScreenOn
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

/**
 * The time of day the capsule arrangement draws, and the repaint that keeps it
 * current.
 *
 * Nothing else in the module needs to know the time: every other reading
 * arrives as an event - a battery broadcast, a signal level, an ink change - and
 * repaints what moved. A clock has no such event; it is correct exactly until the
 * minute rolls over, so something has to wake on that boundary.
 *
 * Two decisions worth stating:
 *
 * - **Aligned to the minute boundary, not a flat 60 s.** A fixed delay from
 *   whenever the module started would leave the displayed minute changing a
 *   fraction of a second after the real one, drifting further every tick.
 * - **Nothing is formatted unless the time is on screen.** The ticker runs once
 *   per process, but each tick asks what is drawn first, so an install on any
 *   other style pays one cheap check a minute rather than a format and a repaint.
 */
internal object ClockTicker {

    private val main = Handler(Looper.getMainLooper())

    private var started = false

    private var timeFormat: DateFormat? = null

    private var dateFormat: DateFormat? = null

    private var weekdayFormat: SimpleDateFormat? = null
    private var is24Hour = true

    private val ZH_DAYS = arrayOf("日", "一", "二", "三", "四", "五", "六")

    // The date formats take their numerals and separators from the system locale, so they are
    // rebuilt when that locale changes rather than captured once: a static SimpleDateFormat
    // built from `Locale.getDefault()` keeps formatting in the locale the process started in.
    private var dateLocale: Locale = Locale.getDefault()
    private var FMT_SLASH_YMD = SimpleDateFormat("yyyy/MM/dd", dateLocale)
    private var FMT_ZH_MD = SimpleDateFormat("M月d日", dateLocale)
    private var FMT_ZH_YMD = SimpleDateFormat("yyyy年M月d日", dateLocale)
    private var FMT_DEF_MD = SimpleDateFormat("MM/dd", dateLocale)

    /** Rebuilds the locale-dependent date formats if the system locale moved under us. */
    @Synchronized
    private fun refreshDateFormats() {
        val locale = Locale.getDefault()
        if (locale == dateLocale) return
        dateLocale = locale
        FMT_SLASH_YMD = SimpleDateFormat("yyyy/MM/dd", locale)
        FMT_ZH_MD = SimpleDateFormat("M月d日", locale)
        FMT_ZH_YMD = SimpleDateFormat("yyyy年M月d日", locale)
        FMT_DEF_MD = SimpleDateFormat("MM/dd", locale)
    }

    // Weekdays spelled in English are locale constants, not the system locale's words.
    private val FMT_EN_SHORT = SimpleDateFormat("EEE", Locale.ENGLISH)
    private val FMT_EN_FULL = SimpleDateFormat("EEEE", Locale.ENGLISH)
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext ?: context
        timeFormat = AndroidDateFormat.getTimeFormat(app)
        val locales = app.resources.configuration.locales
        val locale = if (locales.size() > 0) locales[0] else Locale.getDefault()
        dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        weekdayFormat = SimpleDateFormat("EEEE", locale)
        is24Hour = AndroidDateFormat.is24HourFormat(app)
        addListener { screenOn ->
            main.post {
                if (screenOn) {
                    main.removeCallbacks(tickRunnable)
                    main.post(tickRunnable)
                } else {
                    main.removeCallbacks(tickRunnable)
                }
            }
        }
        BarVisibility.addListener { shown ->
            if (shown && isScreenOn) {
                main.removeCallbacks(tickRunnable)
                main.post(tickRunnable)
            }
        }
        main.post(tickRunnable)
    }

    fun formatDate(now: Date, format: Int): String {
        refreshDateFormats()
        val fmt = when (format) {
            Prefs.CLOCK_DATE_SLASH_YMD -> FMT_SLASH_YMD
            Prefs.CLOCK_DATE_ZH_MD -> FMT_ZH_MD
            Prefs.CLOCK_DATE_ZH_YMD -> FMT_ZH_YMD
            else -> FMT_DEF_MD
        }
        return synchronized(fmt) { fmt.format(now) }
    }

    fun formatWeekday(now: Date, format: Int): String {
        val cal = Calendar.getInstance().apply { time = now }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val idx = (dayOfWeek - 1).coerceIn(0, 6)
        return when (format) {
            Prefs.CLOCK_WEEKDAY_ZH_SHORT -> "周" + ZH_DAYS[idx]
            Prefs.CLOCK_WEEKDAY_ZH_FULL -> "星期" + ZH_DAYS[idx]
            Prefs.CLOCK_WEEKDAY_EN_SHORT -> synchronized(FMT_EN_SHORT) { FMT_EN_SHORT.format(now) }
            Prefs.CLOCK_WEEKDAY_EN_FULL -> synchronized(FMT_EN_FULL) { FMT_EN_FULL.format(now) }
            else -> "周" + ZH_DAYS[idx]
        }
    }

    fun formatLunar(now: Date): String {
        return try {
            val cal = ChineseCalendar()
            cal.time = now
            val month = cal.get(ChineseCalendar.MONTH)
            val day = cal.get(ChineseCalendar.DAY_OF_MONTH)
            val isLeap = cal.get(ChineseCalendar.IS_LEAP_MONTH) == 1
            val months = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
            val days = arrayOf(
                "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
            )
            val m = if (month in months.indices) months[month] else "${month + 1}月"
            val d = if (day in 1..30) days[day - 1] else "${day}日"
            (if (isLeap) "闰" else "") + m + d
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * `[time, date, weekday, lunar]` for the module's bar clock; an entry is empty when it was
     * not asked for.
     */
    @JvmStatic
    fun barTexts(
        date: Boolean,
        weekday: Boolean,
        dateFormatType: Int = Prefs.DEF_CLOCK_DATE_FORMAT,
        weekdayFormatType: Int = Prefs.DEF_CLOCK_WEEKDAY_FORMAT,
    ): Array<String> {
        val now = Calendar.getInstance().time
        return arrayOf(
            format(now),
            if (date) formatDate(now, dateFormatType) else "",
            if (weekday) formatWeekday(now, weekdayFormatType) else "",
            formatLunar(now),
        )
    }

    /** Formats and repaints now, so a just-placed clock is not blank for a minute. */
    fun refresh() {
        try {
            publishNow()
        } catch (_: Throwable) {
        }
    }

    private val tickRunnable = Runnable { tick() }

    private fun tick() {
        if (!isScreenOn) return
        var delay = MAX_LATENESS_MS
        try {
            if (BarVisibility.shown && !StatusBarRowManager.suppressed && timeFormat != null && usesClock()) publishNow()
            // Recomputed either way: the style can be switched at any moment and
            // the next tick should already be aligned to the boundary.
            delay = millisToNextMinute()
        } catch (_: Throwable) {
            // keep ticking
        } finally {
            // In a finally, deliberately: a ticker that died on one bad frame
            // would leave the clock frozen for the life of the process.
            main.postDelayed(tickRunnable, delay.coerceAtLeast(1L))
        }
    }

    /**
     * Whether anything on screen wants the time.
     *
     * Asks the same rule the suppression asks, so the renderer drawing the time
     * and the hook hiding the bar's own copy cannot disagree.
     */
    private fun usesClock(): Boolean = ModuleConfig.appearance().hidesStatusClock()

    /**
     * The time in the user's own locale format.
     *
     * `getTimeFormat` rather than a pattern of this module's own: the bar's
     * clock follows the system setting, and a second clock that disagreed with
     * every other one on the phone would read as a bug.
     */
    private fun seconds(): Boolean = ModuleConfig.appearance().clockSeconds

    private fun format(now: Date): String {
        if (!seconds()) {
            return timeFormat?.format(now).orEmpty()
        }
        // A separate pattern rather than a flag on the locale formatter: the system
        // format is "the time without seconds", and there is no portable way to ask it
        // for one more field. The 24-hour choice is still the system's.
        val pattern = if (is24Hour) "HH:mm:ss" else "h:mm:ss"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(now)
    }

    /** Milliseconds from now to the top of the next minute. */
    private fun millisToNextMinute(): Long {
        val now = Calendar.getInstance()
        val elapsed = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        return MINUTE_MS - elapsed
    }

    /**
     * Publishes the glyph's time, minutes only: "show seconds" is a bar-clock feature
     * and never reaches the glyph, which has no room for two more digits.
     */
    private fun publishNow() {
        val now = Calendar.getInstance().time
        val a = ModuleConfig.appearance()
        StatusState.sClockDate = formatDate(now, a.clockDateFormat)
        StatusState.sClockWeekday = formatWeekday(now, a.clockWeekdayFormat)
        StatusState.sClockLunar = formatLunar(now)
        if (timeFormat == null) return
        val format = timeFormat ?: return
        val time = format.format(now)
        StatusState.sClockText = time
        SystemUiHook.invalidateHosts()
    }

    /** The longest a tick may be late before the boundary is recomputed. */
    private const val MAX_LATENESS_MS = 60_000L
    private const val MINUTE_MS = 60_000L

    /** The date line's size, relative to the time's, in the module's bar clock. */
    internal const val BAR_DATE_SCALE = 0.74f
}
