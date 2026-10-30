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

package io.github.odomu.honorduo.hook.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.BarClockManager
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.BatteryPresence
import io.github.odomu.honorduo.hook.tracker.FpsTicker
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker
import io.github.odomu.honorduo.hook.tracker.WeatherManager
import io.github.odomu.honorduo.log.LogManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Main Xposed hook coordinator and lifecycle dispatcher for MagicOS SystemUI.
 *
 * Dispatches hook installation and configuration updates to modular handlers:
 * - [SystemBarHook]: status-bar rows, battery, visibility suppression, and tint updates.
 * - [SignalIconHook]: cellular signal indicators and network type views.
 * - [StatusBarIconHook]: custom built-in Wi-Fi and system status icon replacements.
 * - [BarClockManager]: status-bar clock styling, dual-row date/weekday stacking, and positioning.
 * - [StatusBarRowManager]: container management, glyph views, capsule avoidance, and anti-burn-in.
 */
internal object SystemUiHook {


    const val LOG_INFO = 4
    const val LOG_WARN = 5
    const val LOG_ERROR = 6

    @Volatile
    private var sModule: XposedModule? = null

    private var sScreenTrackerStarted = false

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun install(module: XposedModule, cl: ClassLoader) {
        sModule = module
        ModuleConfig.install(module)
        ModuleConfig.addListener { applyConfigChange() }

        StatusBarRowManager.initFields(Refl.cls("com.android.systemui.statusbar.HwSystemIcons", cl) ?: Any::class.java)

        var hooked = 0
        hooked += SystemBarHook.install(module, cl)
        hooked += SignalIconHook.install(module, cl)
        hooked += BarClockManager.hookPivotXY(module, cl)
        hooked += StatusBarIconHook.install(module, cl)
        hooked += NotifCenterHook.install(module, cl)
        hooked += ControlCenterHook.install(module, cl)
        log(
            module,
            "HonorDuo installed, hooks=$hooked enabled=${ModuleConfig.get().enabled}",
        )
    }

    private fun applyConfigChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { applyConfigChange() }
            return
        }
        val a = ModuleConfig.appearance()
        if (debugLog()) {
            log(LOG_INFO, "config changed, rows=${StatusBarRowManager.rowsCount()} enabled=${a.glyph} style=${a.style}")
        }
        StatusBarRowManager.syncAll(a, force = true)
        BarClockManager.sync(a)
        StatusBarIconManager.applyIconVisibility(a)
        SystemBarHook.applyPrivacyChipVisibility(a)
        StatusBarIconHook.updateConfig(
            a.replaceWifiIcon,
            a.replaceStatusIcon,
            a.hideAlarmIcon,
            a.hideBluetoothIcon,
        )
        NotifCenterHook.onConfigChanged(a)
        ControlCenterHook.onConfigChanged(a)
        SystemBarHook.updateEffectiveScopeState()
        FpsTicker.onConfigChanged()
        BatteryPresence.onConfigChanged()
        StatusState.appContext()?.let { WeatherManager.onConfigChanged(it) }
    }

    fun ensureScreenStateTracker(context: Context) {
        if (sScreenTrackerStarted) return
        sScreenTrackerStarted = true
        ScreenStateTracker.start(context)
        ScreenStateTracker.addListener { screenOn ->
            val handler = Handler(Looper.getMainLooper())
            handler.post { onScreenStateChanged(screenOn) }
        }
        // Posted, not run inline: the producers listening to the same transition
        // (battery, clock, frame rate) refresh their state inside their own
        // callbacks, so replaying one turn later is what makes the single repaint
        // show the readings the hidden interval accumulated instead of the old ones.
        BarVisibility.addListener { shown ->
            BarClockManager.onVisibilityChanged(shown)
            if (!shown) return@addListener
            Handler(Looper.getMainLooper()).post {
                if (!BarVisibility.shown) return@post
                val pending = sRepaintPending
                sRepaintPending = false
                // Only a skipped update needs the text rebuilt; every return to the
                // bar needs the repaint, if only for the ink the new surroundings ask for.
                if (pending) StatusBarRowManager.syncAll(ModuleConfig.appearance(), force = false)
                invalidateHosts()
            }
        }
    }

    private fun onScreenStateChanged(screenOn: Boolean) {
        StatusBarRowManager.onScreenStateChanged(screenOn)
        BarClockManager.onScreenStateChanged(screenOn)
        if (screenOn) {
            invalidateHosts()
        }
    }

    /**
     * Asks for a repaint of every reading.
     *
     * While the bar is hidden the request is only recorded: nothing painted into
     * an undrawn row reaches a display, and the work behind it - the telephony
     * poll inside [StatusState.refresh] above all - is the expensive part. The one
     * repaint this defers is replayed by the visibility listener above, so the
     * bar cannot come back showing a stale reading.
     */
    fun invalidateHosts() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { invalidateHosts() }
            return
        }
        if (!BarVisibility.shown) {
            sRepaintPending = true
            return
        }
        sRepaintPending = false
        StatusBarRowManager.invalidateAll()
        BarClockManager.sync(ModuleConfig.appearance())
    }

    /** Records that a repaint was skipped while the bar was hidden. */
    fun markRepaintPending() {
        sRepaintPending = true
    }

    @Volatile
    private var sRepaintPending = false

    fun hook(
        module: XposedModule,
        m: Method?,
        id: String,
        hooker: XposedInterface.Hooker,
    ): Int {
        if (m == null) {
            if (debugLog()) {
                log(module, "skip $id: method not found")
            }
            return 0
        }
        return try {
            module.hook(m).setId(id).intercept(hooker)
            1
        } catch (t: Throwable) {
            log(module, "skip $id: $t")
            0
        }
    }
    fun hook(
        module: XposedModule,
        c: Constructor<*>?,
        id: String,
        hooker: XposedInterface.Hooker,
    ): Int {
        if (c == null) {
            if (debugLog()) {
                log(module, "skip $id: constructor not found")
            }
            return 0
        }
        return try {
            module.hook(c).setId(id).intercept(hooker)
            1
        } catch (t: Throwable) {
            log(module, "skip $id: $t")
            0
        }
    }


    fun log(module: XposedModule, msg: String) {
        LogManager.i(Prefs.TAG, msg)
    }

    fun log(priority: Int, msg: String) {
        val level = when (priority) {
            LOG_ERROR -> "E"
            LOG_WARN -> "W"
            else -> "I"
        }
        LogManager.log(level, Prefs.TAG, msg)
    }

    /** True when the user asked for verbose logging. */
    fun debugLog(): Boolean = ModuleConfig.debugLog()
}
