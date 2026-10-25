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

package io.github.odomu.honorduo.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
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
    private var pendingRefresh: Runnable? = null
    private var appContext: Context? = null

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
            TrioConfig.appearance().perfRefreshSec,
            Prefs.MIN_PERF_REFRESH_SEC,
            Prefs.MAX_PERF_REFRESH_SEC,
        ) * 1_000L

    private fun isWatchdogNeeded(): Boolean {
        val a = TrioConfig.appearance()
        return ScreenStateTracker.isScreenOn && BarVisibility.shown && a.enabled && a.perfAnywhere()
    }

    private fun ensureWatchdog() {
        if (isWatchdogNeeded()) {
            watchdogRunning = true
            // Restarted rather than only started: the interval itself is a setting, and
            // a timer already posted with the old pace has to be re-armed at the new one.
            main.removeCallbacks(watchdogTick)
            main.post(watchdogTick)
        } else {
            stopWatchdog()
        }
    }

    private fun stopWatchdog() {
        watchdogRunning = false
        main.removeCallbacks(watchdogTick)
        pendingRefresh?.let(main::removeCallbacks)
    }

    fun onConfigChanged() {
        main.post {
            // The threshold may have moved under the latch; recompute before the
            // watchdog's next read, so the next repaint already has the right ink.
            if (TrioState.updateBatteryHot(TrioConfig.appearance().tempThreshold)) {
                TrioHooks.invalidateHosts()
            }
            ensureWatchdog()
        }
    }

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        ScreenStateTracker.addListener { screenOn ->
            main.post {
                if (screenOn) {
                    refresh(app)
                    ensureWatchdog()
                } else {
                    stopWatchdog()
                }
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
                // Re-read the sticky intent rather than trust this delivery's
                // extras: the two must not be able to disagree.
                refresh(context)
                scheduleRefresh(context)
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
     * Coalesces a recheck.
     *
     * A broadcast can arrive before the charge counter and the current have
     * both settled, so the frame after it is re-read once; the same coalescer
     * serves a burst of deliveries.
     */
    private fun scheduleRefresh(context: Context) {
        if (!BarVisibility.shown) return
        main.post {
            if (!BarVisibility.shown) return@post
            refresh(context)
            pendingRefresh?.let(main::removeCallbacks)
            val again = Runnable { if (BarVisibility.shown) refresh(context) }
            pendingRefresh = again
            main.postDelayed(again, RECHECK_MS)
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
    private fun refresh(context: Context) {
        if (!BarVisibility.shown) return
        try {
            val battery = context.registerReceiver(
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
                TrioState.TEMP_UNKNOWN
            } else {
                (temperature / 10f).roundToInt()
            }
            // One read serves both: the watt figure takes its magnitude from it and the
            // current readout takes it whole, sign included.
            val current = currentNow(context)
            publish(
                attached, wattsOf(current, voltage), whole, thermalStatus(context), current,
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
        context.getSystemService(android.os.PowerManager::class.java)
            ?.currentThermalStatus
            ?: TrioState.THERMAL_UNKNOWN
    } catch (_: RuntimeException) {
        TrioState.THERMAL_UNKNOWN
    }


    /** The battery current in microamperes, or [Int.MIN_VALUE] when unknown. */
    private fun currentNow(context: Context): Int = try {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?: Int.MIN_VALUE
    } catch (_: RuntimeException) {
        Int.MIN_VALUE
    }

    /**
     * The power going into the battery, in watts, from its own current and voltage.
     *
     * Deliberately the battery's side of the circuit rather than the charger's
     * negotiated limit: the negotiated figure is fixed for the whole session and
     * would sit there unchanged while the real power tapered away, which is the
     * opposite of what a performance readout is for.
     *
     * The sign of `current_now` is not portable - this platform answers
     * negative while charging - so only the magnitude is used. Being unable to ask
     * is a different answer from zero and is [TrioState.POWER_UNKNOWN]; a real
     * zero stays a real zero.
     */
    private fun wattsOf(currentUnits: Int, voltageMilliVolts: Int): Float {
        if (currentUnits == Int.MIN_VALUE || voltageMilliVolts <= 0) {
            return TrioState.POWER_UNKNOWN
        }
        val microAmps = kotlin.math.abs(currentUnits).toFloat() * 1_000f
        return microAmps * (voltageMilliVolts / 1000f) / 1_000_000f
    }

    private fun publish(
        attached: Boolean, watts: Float, temperature: Int, thermal: Int, currentUa: Int,
    ) {
        val beforeAttached = TrioState.sChargingAttached
        val beforeWatts = TrioState.sChargePowerW
        val beforeTemperature = TrioState.sBatteryTempC
        val beforeCurrent = TrioState.sCurrentUa

        TrioState.sChargingAttached = attached
        TrioState.sChargePowerW = watts
        TrioState.sBatteryTempC = temperature
        TrioState.sThermalStatus = thermal
        TrioState.sCurrentUa = currentUa
        // The latch, not the raw comparison: the threshold has to hold across the
        // tenths-of-a-degree jitter that whole-degree rounding produces.
        val hotMoved = TrioState.updateBatteryHot(TrioConfig.appearance().tempThreshold)

        // Float.compare, not !=: the watts may be POWER_UNKNOWN (a NaN), and
        // NaN != NaN would make every frame look like a change and repaint the
        // bar once every few seconds for nothing.
        val moved = beforeAttached != attached ||
            beforeWatts.compareTo(watts) != 0 ||
            beforeTemperature != temperature ||
            beforeCurrent != currentUa
        if (moved || hotMoved) TrioHooks.invalidateHosts()
    }

    private const val RECHECK_MS = 2_000L
}
