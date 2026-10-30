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

package io.github.odomu.honorduo.hook.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import io.github.odomu.honorduo.hook.core.SystemUiHook
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Event-driven battery state for the out-of-ring readouts: charging power and
 * battery temperature.
 *
 * Why the framework and not the filesystem: this module runs inside SystemUI
 * as `android.uid.systemui`, and every kernel-side route is closed to it.
 * `/sys/class/power_supply/battery/` refuses even a rooted shell, the health HAL
 * answers FAILED_TRANSACTION, and `dumpsys battery` is a shell tool rather than an
 * API. What is left is the pair the platform itself uses: the sticky
 * ACTION_BATTERY_CHANGED broadcast for the temperature and the voltage, and
 * BatteryManager for the current.
 *
 * Same shape as [WifiPresence]: a sticky broadcast re-read on every delivery, a
 * bounded low-frequency watchdog for the deliveries MagicOS drops, and nothing on
 * the per-frame drawing path.
 */
internal object BatteryPresence {

    private val main = Handler(Looper.getMainLooper())

    private var registered = false
    private var watchdogRunning = false
    private var appContext: Context? = null
    private var batteryManager: BatteryManager? = null
    private var powerManager: PowerManager? = null
    private val watchdogTick = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            if (!isWatchdogNeeded()) {
                watchdogRunning = false
                return
            }
            refresh(ctx)
            main.postDelayed(this, watchdogDelay())
        }
    }

    /**
     * How long until the next watchdog re-read.
     * The user's own interval (Readouts -> Refresh interval, ten seconds by default): the watchdog
     * covers deliveries MagicOS drops, and it is the one part of the readout that costs
     * wake-ups, so its pace is a setting rather than a constant.
     */
    private fun watchdogDelay(): Long =
        Prefs.clamp(
            ModuleConfig.appearance().perfRefreshSec,
            Prefs.MIN_PERF_REFRESH_SEC,
            Prefs.MAX_PERF_REFRESH_SEC,
        ) * 1_000L

    private fun isWatchdogNeeded(): Boolean {
        val a = ModuleConfig.appearance()
        return ScreenStateTracker.isScreenOn && BarVisibility.shown && a.enabled && a.perfAnywhere()
    }

    private var lastIntervalMs = 0L

    private fun ensureWatchdog() {
        val needed = isWatchdogNeeded()
        if (!needed) {
            stopWatchdog()
            return
        }
        val interval = watchdogDelay()
        if (!watchdogRunning) {
            watchdogRunning = true
            lastIntervalMs = interval
            main.removeCallbacks(watchdogTick)
            main.post(watchdogTick)
        } else if (lastIntervalMs != interval) {
            lastIntervalMs = interval
            main.removeCallbacks(watchdogTick)
            main.postDelayed(watchdogTick, interval)
        }
    }

    private fun stopWatchdog() {
        watchdogRunning = false
        main.removeCallbacks(watchdogTick)
    }

    fun onConfigChanged() {
        main.post {
            // The threshold may have moved under the latch; recompute before the
            // watchdog's next read, so the next repaint already has the right ink.
            if (StatusState.updateBatteryHot(ModuleConfig.appearance().tempThreshold)) {
                SystemUiHook.invalidateHosts()
            }
            ensureWatchdog()
        }
    }

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        batteryManager = app.getSystemService(BatteryManager::class.java)
        powerManager = app.getSystemService(PowerManager::class.java)
        ScreenStateTracker.addListener { screenOn ->
                if (screenOn) {
                    refresh(app)
                    ensureWatchdog()
                } else {
                    stopWatchdog()
                }
            }
        BarVisibility.addListener { shown ->
            main.post {
                if (shown) {
                    refresh(app)
                    ensureWatchdog()
                } else {
                    stopWatchdog()
                }
            }
        }
        ensureWatchdog()
        if (registered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ignored: Context?, intent: Intent?) {
                // Delivery is cheap to filter, and the read behind it is not: a sticky
                // re-read plus BatteryManager and thermal queries. Nothing here has a
                // reading on screen when the bar is hidden or no readout is enabled, and
                // the bar's return re-reads through the listener above.
                if (!BarVisibility.shown || !isWatchdogNeeded()) return
                refresh(context, intent)
            }
        }
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // A protected broadcast, so only the system can send it; nothing
                // is taken from the extras of a received delivery, so a forged
                // one could not move the reading either.
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registered = true
            refresh(context)
        } catch (_: RuntimeException) {
            // A ROM that refuses the registration leaves the readouts on their
            // "unknown" state rather than crashing SystemUI.
        }
    }

    /**
     * Re-reads the platform's battery figures.
     *
     * Returns immediately while the bar is hidden. Every line below is either a
     * binder call or a broadcast registration, and a reading painted into an undrawn
     * row is work with nothing to show for it; the state is re-read once when the
     * bar returns, which is also when it first needs to be right.
     */
    private fun refresh(context: Context, directIntent: Intent? = null) {
        // Both gates belong here rather than only in the callers: the screen and the bar
        // can each go away between a delivery and this read, and every line below is a
        // binder call.
        if (!ScreenStateTracker.isScreenOn || !BarVisibility.shown || !isWatchdogNeeded()) return
        try {
            val battery = directIntent ?: context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ) ?: return

            val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = battery.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN,
            )
            val temperature = battery.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                Int.MIN_VALUE,
            )
            val voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            // "A source is attached" is deliberately wider than "status is
            // charging": at 100 % the platform flips to FULL and stops pushing
            // current, and a readout that vanished there would disappear exactly
            // where a user looks for it.
            val attached = plugged != 0 ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val whole = if (temperature == Int.MIN_VALUE) {
                StatusState.TEMP_UNKNOWN
            } else {
                (temperature / 10f).roundToInt()
            }
            // One read serves both: the watt figure takes its magnitude from it and the
            // current readout takes it whole, sign included.
            val current = currentNow(context)
            publish(
                attached, wattsOf(current, voltage), whole, thermalStatus(context), current, voltage,
            )
        } catch (_: RuntimeException) {
            // Permissions or ROM differences must never crash SystemUI.
        }
    }

    /**
     * The framework's own throttling verdict on the SoC.
     *
     * A public call with no permission, and a different question from the battery
     * temperature: it is reached from skin and junction sensors this process cannot
     * read, and it is the platform already deciding to slow the device down. The
     * readout uses it to turn hot before the battery has warmed, which is the state
     * a user notices as stutter.
     */
    private fun thermalStatus(context: Context): Int = try {
        powerManager?.currentThermalStatus ?: context.getSystemService(PowerManager::class.java)
            ?.currentThermalStatus
            ?: StatusState.THERMAL_UNKNOWN
    } catch (_: RuntimeException) {
        StatusState.THERMAL_UNKNOWN
    }

    /** The battery current in microamperes or milliamperes, or [StatusState.CURRENT_UNKNOWN] when unknown. */
    private fun currentNow(context: Context): Int {
        val bm = batteryManager ?: context.getSystemService(BatteryManager::class.java) ?: return StatusState.CURRENT_UNKNOWN
        val now = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (_: RuntimeException) {
            StatusState.CURRENT_UNKNOWN
        }
        if (now != StatusState.CURRENT_UNKNOWN && now != 0) return now

        // Fallback to average current when instantaneous current is 0 or unreadable
        val avg = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        } catch (_: RuntimeException) {
            StatusState.CURRENT_UNKNOWN
        }
        if (avg != StatusState.CURRENT_UNKNOWN && avg != 0) return avg
        return now
    }

    /**
     * The power going into the battery, in watts, from its own current and voltage.
     */
    private fun wattsOf(currentUnits: Int, voltageMilliVolts: Int): Float {
        if (currentUnits == StatusState.CURRENT_UNKNOWN || voltageMilliVolts <= 0) {
            return StatusState.POWER_UNKNOWN
        }
        val absCurrent = abs(currentUnits).toFloat()
        val milliamps = if (absCurrent > 10_000f) absCurrent / 1_000f else absCurrent
        val volts = voltageMilliVolts / 1_000f
        return (milliamps * volts) / 1_000f
    }

    private fun publish(
        attached: Boolean, watts: Float, temperature: Int, thermal: Int, currentUa: Int, voltageMv: Int,
    ) {
        val beforeAttached = StatusState.sChargingAttached
        val beforeWatts = StatusState.sChargePowerW
        val beforeTemperature = StatusState.sBatteryTempC
        val beforeCurrent = StatusState.sCurrentUa
        val beforeVoltage = StatusState.sVoltageMv

        StatusState.sChargingAttached = attached
        StatusState.sChargePowerW = watts
        StatusState.sBatteryTempC = temperature
        StatusState.sThermalStatus = thermal
        StatusState.sCurrentUa = currentUa
        StatusState.sVoltageMv = voltageMv
        val hotMoved = StatusState.updateBatteryHot(ModuleConfig.appearance().tempThreshold)

        val moved = beforeAttached != attached ||
            beforeWatts.compareTo(watts) != 0 ||
            beforeTemperature != temperature ||
            beforeCurrent != currentUa ||
            beforeVoltage != voltageMv
        if (moved || hotMoved) SystemUiHook.invalidateHosts()
    }
}
