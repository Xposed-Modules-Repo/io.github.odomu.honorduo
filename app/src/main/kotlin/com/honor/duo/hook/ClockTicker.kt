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

package com.honor.duo.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.honor.duo.model.Prefs
import com.honor.duo.model.TrioConfig
import com.honor.duo.model.TrioState
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private var timeFormat: java.text.DateFormat? = null

    private var dateFormat: java.text.DateFormat? = null

    private var weekdayFormat: SimpleDateFormat? = null

    private var is24Hour = true

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext ?: context
        timeFormat = AndroidDateFormat.getTimeFormat(app)
        val locales = app.resources.configuration.locales
        val locale = if (locales.size() > 0) locales[0] else Locale.getDefault()
        dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
        weekdayFormat = SimpleDateFormat("EEEE", locale)
        is24Hour = AndroidDateFormat.is24HourFormat(app)
        main.post { tick() }
    }

    fun formatDate(now: java.util.Date, format: Int): String {
        return when (format) {
            Prefs.CLOCK_DATE_YEAR_MONTH_DAY -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(now)
            else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(now)
        }
    }

    fun formatWeekday(now: java.util.Date, format: Int): String {
        val cal = Calendar.getInstance().apply { time = now }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val zhDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val idx = (dayOfWeek - 1).coerceIn(0, 6)
        return when (format) {
            Prefs.CLOCK_WEEKDAY_ZH_SHORT -> "周" + zhDays[idx]
            Prefs.CLOCK_WEEKDAY_ZH_FULL -> "星期" + zhDays[idx]
            Prefs.CLOCK_WEEKDAY_EN_SHORT -> SimpleDateFormat("EEE", Locale.ENGLISH).format(now)
            Prefs.CLOCK_WEEKDAY_EN_FULL -> SimpleDateFormat("EEEE", Locale.ENGLISH).format(now)
            else -> "周" + zhDays[idx]
        }
    }

    fun formatLunar(now: java.util.Date): String {
        return try {
            val cal = android.icu.util.ChineseCalendar()
            cal.time = now
            val month = cal.get(android.icu.util.ChineseCalendar.MONTH)
            val day = cal.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
            val isLeap = cal.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1
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
        if (timeFormat == null) return
        try {
            if (usesClock()) publishNow()
        } catch (_: Throwable) {
            // Same contract as the tick: never let one bad format stop the clock.
        }
    }

    private fun tick() {
        var delay = MAX_LATENESS_MS
        try {
            if (timeFormat != null && usesClock()) publishNow()
            // Recomputed either way: the style can be switched at any moment and
            // the next tick should already be aligned to the boundary.
            delay = millisToNextMinute()
        } catch (_: Throwable) {
            // keep ticking
        } finally {
            // In a finally, deliberately: a ticker that died on one bad frame
            // would leave the clock frozen for the life of the process.
            main.postDelayed({ tick() }, delay.coerceAtLeast(1L))
        }
    }

    /**
     * Whether anything on screen wants the time.
     *
     * Asks the same rule the suppression asks, so the renderer drawing the time
     * and the hook hiding the bar's own copy cannot disagree.
     */
    private fun usesClock(): Boolean = TrioConfig.appearance().hidesStatusClock()

    /**
     * The time in the user's own locale format.
     *
     * `getTimeFormat` rather than a pattern of this module's own: the bar's
     * clock follows the system setting, and a second clock that disagreed with
     * every other one on the phone would read as a bug.
     */
    private fun seconds(): Boolean = TrioConfig.appearance().clockSeconds

    private fun format(now: java.util.Date): String {
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
        if (timeFormat == null) return
        val format = timeFormat ?: return
        val time = format.format(Calendar.getInstance().time)
        if (time == TrioState.sClockText) {
            return
        }
        TrioState.sClockText = time
        TrioHooks.invalidateHosts()
    }

    /** The longest a tick may be late before the boundary is recomputed. */
    private const val MAX_LATENESS_MS = 60_000L
    private const val MINUTE_MS = 60_000L

    /** The date line's size, relative to the time's, in the module's bar clock. */
    internal const val BAR_DATE_SCALE = 0.74f
}
