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
            main.postDelayed(this, WATCHDOG_MS)
        }
    }

    private fun isWatchdogNeeded(): Boolean {
        val a = io.github.odomu.honorduo.model.TrioConfig.appearance()
        return ScreenStateTracker.isScreenOn && a.enabled && a.perfAnywhere()
    }

    private fun ensureWatchdog() {
        if (isWatchdogNeeded()) {
            if (!watchdogRunning) {
                watchdogRunning = true
                main.removeCallbacks(watchdogTick)
                main.post(watchdogTick)
            }
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
        main.post { ensureWatchdog() }
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
        main.post {
            refresh(context)
            pendingRefresh?.let(main::removeCallbacks)
            val again = Runnable { refresh(context) }
            pendingRefresh = again
            main.postDelayed(again, RECHECK_MS)
        }
    }

    private fun refresh(context: Context) {
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
            publish(attached, wattsOf(currentNow(context), voltage), whole)
        } catch (_: RuntimeException) {
            // Permissions or ROM differences must never crash SystemUI.
        }
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

    private fun publish(attached: Boolean, watts: Float, temperature: Int) {
        val beforeAttached = TrioState.sChargingAttached
        val beforeWatts = TrioState.sChargePowerW
        val beforeTemperature = TrioState.sBatteryTempC

        TrioState.sChargingAttached = attached
        TrioState.sChargePowerW = watts
        TrioState.sBatteryTempC = temperature

        // Float.compare, not !=: the watts may be POWER_UNKNOWN (a NaN), and
        // NaN != NaN would make every frame look like a change and repaint the
        // bar once every few seconds for nothing.
        val moved = beforeAttached != attached ||
            beforeWatts.compareTo(watts) != 0 ||
            beforeTemperature != temperature
        if (moved) TrioHooks.invalidateHosts()
    }

    private const val WATCHDOG_MS = 10_000L
    private const val RECHECK_MS = 2_000L
}
