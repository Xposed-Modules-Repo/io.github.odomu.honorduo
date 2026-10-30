/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.odomu.honorduo.hook.core.NotifCenterHook
import io.github.odomu.honorduo.hook.statusbar.BarClockManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Manages real-time weather query from MagicOS TotemWeather ContentProvider,
 * listening for content updates and broadcasts with strict power-saving guards.
 */
internal object WeatherManager {

    private const val TAG = "HonorDuo-Weather"

    /** Info-level tracing, behind the module's own debug switch; warnings stay unconditional. */
    private fun logDebug(msg: String) {
        if (ModuleConfig.debugLog()) Log.i(TAG, msg)
    }
    private val WEATHER_URI = Prefs.WEATHER_URI.toUri()
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
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext ?: context
        logDebug("WeatherManager starting with context: ${app.packageName}")

        try {
            app.contentResolver.registerContentObserver(WEATHER_URI, true, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    logDebug("ContentObserver onChange triggered, screenOn=${ScreenStateTracker.isScreenOn}")
                    if (ScreenStateTracker.isScreenOn && BarVisibility.shown) {
                        fetchAsync(app, force = true)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register weather ContentObserver", t)
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Prefs.ACTION_WEATHER_CHANGE)
                addAction(Prefs.ACTION_WIDGET_CHANGE)
                addAction(Prefs.ACTION_TEMPERATURE_FORMAT_CHANGE)
                addAction(Prefs.ACTION_CITYINFO_CHANGE)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val act = intent?.action
                    logDebug("Weather broadcast received: $act, status=${intent?.getIntExtra("status", 0)}")
                    if (act == Prefs.ACTION_WEATHER_CHANGE && intent.getIntExtra("status", 0) != 1) return
                    // A query is a provider round trip plus a JSON parse; with the bar hidden
                    // nothing draws it, and the bar's return asks again through onScreenOn.
                    if (ScreenStateTracker.isScreenOn && BarVisibility.shown) {
                        fetchAsync(app, force = true)
                    }
                }
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register weather receiver", t)
        }

        ScreenStateTracker.addListener { on ->
            if (on) {
                logDebug("Screen turned ON, checking weather staleness")
                onScreenOn(app)
            }
        }
        // The bar's return is the other moment a reading becomes drawable: a cold start and
        // a fullscreen app both leave it hidden, and fetches are suspended until it is back.
        BarVisibility.addListener { shown ->
            if (shown) onScreenOn(app)
        }

        fetchAsync(app)
    }

    fun onScreenOn(context: Context) {
        // Not forced: the stamp also covers the no-data case, so an unlock loop on a ROM
        // whose provider refuses does not become a query loop.
        fetchAsync(context)
    }

    fun onConfigChanged(context: Context) {
        val a = ModuleConfig.appearance()
        logDebug("onConfigChanged: enabled=${a.enabled}, clockWeather=${a.clockWeather}, shadeWeather=${a.shadeWeather}, currentData=$data")
        if (a.enabled && (a.clockWeather || a.shadeWeather)) {
            fetchAsync(context)
        }
    }

    fun fetchAsync(context: Context, force: Boolean = false) {
        if (!ScreenStateTracker.isScreenOn || !BarVisibility.shown) {
            logDebug("fetchAsync skipped: screen off or bar hidden")
            return
        }
        val a = ModuleConfig.appearance()
        if (!a.enabled || (!a.clockWeather && !a.shadeWeather)) {
            logDebug("fetchAsync skipped: weather features disabled")
            return
        }
        val now = System.currentTimeMillis()
        // The stamp alone answers "did we try recently", so a failed or still-empty read is
        // not re-attempted on every caller. It used to require `data != null`, which meant
        // the cache never applied to the one case that needed it: no data yet.
        if (!force && now - lastFetchTime < CACHE_EXPIRY_MS) {
            logDebug("fetchAsync skipped: cached (${now - lastFetchTime}ms old)")
            return
        }
        lastFetchTime = now
        logDebug("fetchAsync scheduled, force=$force")
        executor.execute {
            if (!ScreenStateTracker.isScreenOn) {
                logDebug("fetchAsync background skipped: screen turned OFF")
                return@execute
            }
            try {
                val queried = queryWeather(context)
                logDebug("queryWeather result: $queried")
                if (queried != null && queried != data) {
                    data = queried
                    currentWeather = queried.text()
                    StatusState.sWeather = queried.text()
                    // Repaint only on a real change: an unchanged answer still cost the
                    // clock a requestLayout plus two reflective TimeManager invokes.
                    main.post {
                        BarClockManager.sync(ModuleConfig.appearance())
                        NotifCenterHook.onWeatherChanged()
                    }
                    logDebug("Weather updated: ${queried.text()}")
                } else {
                    logDebug("no weather answer; the stamp backs the next attempt off")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchAsync failed with error", t)
            }
        }
    }

    private fun queryWeather(context: Context): WeatherData? {
        return queryFromCall(context) ?: queryFromCursor(context)
    }

    /** Queries weather via system lockscreen weather plugin call method (MagicOS standard). */
    private fun queryFromCall(context: Context): WeatherData? = runCatching {
        val cr = context.contentResolver ?: return null
        val extras = Bundle().apply {
            putInt("city_type", 10)
            putInt("dayAndNightWeather", 1)
        }
        val bundle = cr.call(WEATHER_URI, "query_home_city_weather", null, extras)
        if (bundle == null) {
            logDebug("queryFromCall: call returned null bundle")
            return null
        }
        val json = bundle.getString("key_home_city_weather")?.takeIf { it.isNotEmpty() }
        if (json == null) {
            logDebug("queryFromCall: key_home_city_weather is null or empty")
            return null
        }
        val root = org.json.JSONObject(json)
        val days = root.optJSONArray("weather") ?: return null
        val dayItems = (0 until days.length()).mapNotNull { days.optJSONObject(it) }
        val today = dayItems.firstOrNull { it.optInt("day_index") == 1 } ?: return null

        val now = System.currentTimeMillis()
        val sunrise = today.optLong("sunrise_time")
        val sunset = today.optLong("sunset_time")
        val isNight = sunrise > 0L && sunset > 0L && (now < sunrise || now > sunset)
        val prefix = if (isNight) "night_" else ""

        val temp = today.optDouble("${prefix}current_temperature", Double.NaN)
            .takeIf { !it.isNaN() } ?: return null
        val fahrenheit = root.optString("temp_unit").equals("F", true)
        val condition = today.optString("${prefix}weather_native_des").trim()
            .takeIf { it.isNotEmpty() }
            ?: mapIconToCondition(today.optInt("${prefix}weather_icon", -1))

        val tempC = if (fahrenheit) ((temp - 32.0) * 5.0 / 9.0).roundToInt() else temp.roundToInt()
        logDebug("queryFromCall parsed: $condition $tempC°C (raw temp=$temp, fahr=$fahrenheit)")
        WeatherData(condition, tempC)
    }.onFailure {
        Log.w(TAG, "queryFromCall threw exception", it)
    }.getOrNull()

    /** Fallback query via ContentProvider table cursor. */
    private fun queryFromCursor(context: Context): WeatherData? {
        val cr = context.contentResolver ?: return null
        return runCatching {
            cr.query(
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

                logDebug("queryFromCursor parsed: ${condition ?: "晴"} $tempC°C")
                WeatherData(condition ?: "晴", tempC)
            }
        }.onFailure {
            Log.w(TAG, "queryFromCursor threw exception", it)
        }.getOrNull()
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
