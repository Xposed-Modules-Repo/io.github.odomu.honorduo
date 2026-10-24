/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Manages real-time weather query from MagicOS TotemWeather ContentProvider,
 * listening for content updates and broadcasts with strict power-saving guards.
 */
internal object WeatherManager {

    private val WEATHER_URI = "content://com.hihonor.android.weather/weatherInfo".toUri()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HonorDuo-Weather").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    private val WEATHER_KEYWORDS = arrayOf(
        "雷阵雨", "冰雹", "暴雨", "大雨", "中雨", "小雨", "雨夹雪", "阵雨", "雨",
        "暴雪", "大雪", "中雪", "小雪", "阵雪", "雪",
        "沙尘暴", "浮尘", "扬沙", "大雾", "雾", "霾", "阴", "多云", "晴",
    )

    data class WeatherData(val condition: String, val tempC: Int) {
        fun text(): String = "$condition ${tempC}°C"
        fun tempText(): String = "${tempC}°C"
    }

    @Volatile
    var data: WeatherData? = null
        private set

    @Volatile
    var currentWeather: String = ""
        private set

    @Volatile private var started = false
    @Volatile private var lastFetchTime = 0L
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes to eliminate battery drain

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext ?: context

        try {
            app.contentResolver.registerContentObserver(WEATHER_URI, true, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    if (ScreenStateTracker.isScreenOn) {
                        fetchAsync(app, force = true)
                    }
                }
            })
        } catch (_: Throwable) {}

        try {
            val filter = IntentFilter().apply {
                addAction("com.hihonor.android.action.WEATHER_CHANGE")
                addAction("com.hihonor.android.action.WIDGET_CHANGE")
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (ScreenStateTracker.isScreenOn) {
                        fetchAsync(app, force = true)
                    }
                }
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Throwable) {}

        ScreenStateTracker.addListener { on ->
            if (on) onScreenOn(app)
        }

        fetchAsync(app, force = true)
    }

    fun onScreenOn(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastFetchTime > CACHE_EXPIRY_MS) {
            fetchAsync(context, force = false)
        }
    }

    fun fetchAsync(context: Context, force: Boolean = false) {
        // Suspend weather queries during screen-off to eliminate battery drain
        if (!ScreenStateTracker.isScreenOn) return
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchTime < CACHE_EXPIRY_MS && data != null) {
            return
        }
        executor.execute {
            if (!ScreenStateTracker.isScreenOn) return@execute
            try {
                val queried = queryWeather(context)
                if (queried != null) {
                    lastFetchTime = System.currentTimeMillis()
                    val hasChanged = queried != data
                    if (hasChanged) {
                        data = queried
                        currentWeather = queried.text()
                        TrioState.sWeather = queried.text()
                        main.post {
                            BarClockManager.sync(TrioConfig.appearance())
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun queryWeather(context: Context): WeatherData? {
        val cr = context.contentResolver ?: return null
        return cr.query(
            WEATHER_URI,
            arrayOf("temperature", "weather_text", "weather_icon", "mobile_link"),
            null, null,
            "update_time DESC LIMIT 1",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val tempIdx = cursor.getColumnIndex("temperature")
            val iconIdx = cursor.getColumnIndex("weather_icon")
            val textIdx = cursor.getColumnIndex("weather_text")
            val linkIdx = cursor.getColumnIndex("mobile_link")

            val tempVal = if (tempIdx >= 0 && !cursor.isNull(tempIdx)) cursor.getFloat(tempIdx) else Float.NaN
            if (tempVal.isNaN()) return null

            val tempC = if (tempVal > 45f) {
                ((tempVal - 32f) * 5f / 9f).roundToInt()
            } else {
                tempVal.roundToInt()
            }

            var condition: String? = null
            if (textIdx >= 0 && !cursor.isNull(textIdx)) {
                val t = cursor.getString(textIdx)
                if (!t.isNullOrBlank()) condition = t.trim()
            }

            if (condition == null && linkIdx >= 0 && !cursor.isNull(linkIdx)) {
                condition = parseConditionFromLink(cursor.getString(linkIdx))
            }

            if (condition == null && iconIdx >= 0 && !cursor.isNull(iconIdx)) {
                condition = mapIconToCondition(cursor.getInt(iconIdx))
            }

            WeatherData(condition ?: "晴", tempC)
        }
    }

    private fun parseConditionFromLink(link: String?): String? {
        if (link == null) return null
        val idx = link.indexOf("diffSummary=")
        if (idx < 0) return null
        val start = idx + 12
        var end = link.indexOf('&', start)
        if (end < 0) end = link.length
        val decoded = runCatching { URLDecoder.decode(link.substring(start, end), "UTF-8") }.getOrNull() ?: return null
        return WEATHER_KEYWORDS.firstOrNull { decoded.contains(it) }
    }

    private fun mapIconToCondition(icon: Int): String = when (icon) {
        1, 2, 33 -> "晴"
        3, 4, 34, 47 -> "多云"
        7, 8, 35, 46 -> "阴"
        11, 45 -> "雾"
        in 14..15 -> "雷阵雨"
        in 16..20, 12, 13, 75 -> "雨"
        in 21..25 -> "雪"
        in 26..29 -> "沙尘"
        in 30..32 -> "霾"
        else -> "晴"
    }
}
