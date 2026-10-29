/*
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
import android.content.Intent
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.ClockTicker
import io.github.odomu.honorduo.hook.tracker.HiddenViews
import io.github.odomu.honorduo.hook.tracker.WeatherManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.WeatherIconDrawer
import java.lang.ref.WeakReference
import java.util.Date

/**
 * Hooks for the MagicOS notification shade (left pull panel).
 *
 * Responsibilities:
 * - Hides the management bell beside the header clock.
 * - Appends lunar date and real-time weather to the header date line.
 * - Suppresses persistent charging, mobile hotspot, and USB debugging notifications.
 * - Opens weather app upon date line click when requested.
 */
internal object NotifCenterHook {

    private const val TAG = "HonorDuo-Shade"
    private fun logDebug(msg: String) {
        if (ModuleConfig.debugLog()) Log.i(TAG, msg)
    }
    private const val CLS_NOTIF_BELL = "com.android.systemui.statusbar.notification.panelheader.NotifMgeBtn"
    private const val CLS_NOTIF_INTERACTOR = "com.android.systemui.statusbar.notification.panelheader.NotifCenterHeaderInteractorImpl"
    private const val CLS_HEADER_VM = "com.android.systemui.statusbar.notification.panelheader.viewmodel.NotifCenterHeaderViewModelImpl"
    private const val CLS_NOTIF_FEATURE = "com.android.systemui.statusbar.notification.HwNotificationFeatureImpl"
    private const val CLS_NOTIF_FILTER = "com.android.systemui.statusbar.notification.HwNotificationFilterImpl"
    private const val WEATHER_PACKAGE = "com.hihonor.android.totemweather"

    @Volatile private var sTimeManager: WeakReference<Any>? = null

    fun install(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        n += hookBell(module, cl)
        n += hookDate(module, cl)
        n += hookDateClick(module, cl)
        n += hookNotificationFilter(module, cl)
        logDebug("NotifCenterHook installed, total hooks=$n")
        return n
    }

    /** Hides the notification-management bell beside the header clock. */
    private fun hookBell(module: XposedModule, cl: ClassLoader): Int {
        if (Refl.cls(CLS_NOTIF_BELL, cl) == null) return 0
        val attach = Refl.method(View::class.java, "onAttachedToWindow") ?: return 0
        return SystemUiHook.hook(module, attach, "honorduo-notif-bell") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View && self.javaClass.name == CLS_NOTIF_BELL) {
                HiddenViews.register(self) { a -> a.shadeHideBell }
            }
            result
        }
    }

    /** The date flow calls this on every tick; decorates date line with lunar and weather. */
    private fun hookDate(module: XposedModule, cl: ClassLoader): Int {
        val interactor = Refl.cls(CLS_NOTIF_INTERACTOR, cl) ?: return 0
        val method = Refl.methodNamed(interactor, "getCurrentClockDate", 1) ?: return 0
        return SystemUiHook.hook(module, method, "honorduo-notif-date") { chain ->
            val line = chain.proceed() as? String
            val a = ModuleConfig.appearance()
            if (!a.enabled || line.isNullOrEmpty() || (!a.shadeLunar && !a.shadeWeather)) {
                line
            } else {
                decorateDate(line, a.shadeLunar, a.shadeWeather)
            }
        }
    }

    private fun decorateDate(line: String, lunar: Boolean, weather: Boolean): String {
        val extra = StringBuilder()
        if (lunar) {
            val l = ClockTicker.formatLunar(Date())
            if (l.isNotEmpty()) extra.append(l)
        }
        if (weather) {
            val w = weatherText()
            if (w.isNotEmpty()) {
                if (extra.isNotEmpty()) extra.append(' ')
                extra.append(w)
            }
        }
        return if (extra.isEmpty()) line else "$line $extra"
    }

    /** Optionally opens the weather app when tapping the date line. */
    private fun hookDateClick(module: XposedModule, cl: ClassLoader): Int {
        val vmClass = Refl.cls(CLS_HEADER_VM, cl) ?: return 0
        val clickMethod = Refl.methodNamed(vmClass, "onClickDate", 0) ?: return 0
        return SystemUiHook.hook(module, clickMethod, "honorduo-notif-date-click") { chain ->
            val a = ModuleConfig.appearance()
            if (a.enabled && a.shadeWeather && a.shadeWeatherOpensApp) {
                launchWeatherApp()
                null
            } else {
                chain.proceed()
            }
        }
    }

    /**
     * Hooks notification filtering to suppress Charging, Hotspot, and USB Debugging notifications.
     */
    private fun hookNotificationFilter(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        val featureClass = Refl.cls(CLS_NOTIF_FEATURE, cl)
        if (featureClass != null) {
            val shadeMethod = featureClass.declaredMethods.firstOrNull {
                it.name == "isDisableShownInShade" && it.parameterCount == 1
            }
            if (shadeMethod != null) {
                n += SystemUiHook.hook(module, shadeMethod, "honorduo-shade-hide-notif") { chain ->
                    val sbn = chain.args.getOrNull(0)
                    val a = ModuleConfig.appearance()
                    if (sbn != null && matchesHiddenNotif(sbn, a)) {
                        logDebug("isDisableShownInShade suppressed notification")
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        val filterClass = Refl.cls(CLS_NOTIF_FILTER, cl)
        if (filterClass != null) {
            val filterMethod = filterClass.declaredMethods.firstOrNull {
                it.name == "shouldFilterOut" && it.parameterCount == 1
            }
            if (filterMethod != null) {
                n += SystemUiHook.hook(module, filterMethod, "honorduo-filter-hide-notif") { chain ->
                    val entry = chain.args.getOrNull(0)
                    val sbn = entry?.let { Refl.get(Refl.field(it.javaClass, "mSbn"), it) }
                    val a = ModuleConfig.appearance()
                    if (sbn != null && matchesHiddenNotif(sbn, a)) {
                        logDebug("shouldFilterOut suppressed notification")
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
        return n
    }

    private fun matchesHiddenNotif(sbn: Any, a: StatusAppearance): Boolean {
        if (!a.enabled) return false
        // Every check below is a reflective invoke on the StatusBarNotification, and the
        // host calls this once per notification per ranking pass. With all three switches
        // off (the shipped default) the answer is known before the first one.
        if (!a.shadeHideChargeNotif && !a.shadeHideHotspotNotif && !a.shadeHideAdbNotif) return false
        val pkg = Refl.methodNamed(sbn.javaClass, "getPackageName", 0)?.invoke(sbn) as? String ?: return false
        val notifId = (Refl.methodNamed(sbn.javaClass, "getId", 0)?.invoke(sbn) as? Number)?.toInt()
        // Only a package and a switch that can actually match are asked for the channel or
        // the group: those are further reflective invokes, one of them on the Notification
        // object. The channel is needed by two different rules on two different packages,
        // so the package each rule names has to be part of the condition.
        val needChannel = (a.shadeHideChargeNotif && pkg == "com.android.settings") ||
            (a.shadeHideAdbNotif && pkg == "android")
        val channel = if (needChannel) {
            val notification = Refl.methodNamed(sbn.javaClass, "getNotification", 0)?.invoke(sbn)
            if (notification != null) {
                Refl.methodNamed(notification.javaClass, "getChannelId", 0)?.invoke(notification) as? String
            } else {
                null
            }
        } else {
            null
        }
        val group = if (a.shadeHideHotspotNotif && pkg == "android") {
            Refl.methodNamed(sbn.javaClass, "getGroup", 0)?.invoke(sbn) as? String
        } else {
            null
        }

        // 1. Hide Charging Notification
        if (a.shadeHideChargeNotif && notifId != null) {
            if (pkg == "com.hihonor.systemmanager" && notifId == 200001) {
                logDebug("matchesHiddenNotif: charging notification suppressed (pkg=$pkg, id=$notifId)")
                return true
            }
            if (pkg == "com.android.settings" && notifId == 1000002 && channel == "SETTINGS_USB") {
                logDebug("matchesHiddenNotif: USB charging notification suppressed (pkg=$pkg, id=$notifId)")
                return true
            }
        }
        // 2. Hide Hotspot Notification
        if (a.shadeHideHotspotNotif && pkg == "android" && group == "TetheredNotification") {
            logDebug("matchesHiddenNotif: hotspot notification suppressed (pkg=$pkg, group=$group)")
            return true
        }
        // 3. Hide USB Debugging Notification
        if (a.shadeHideAdbNotif && pkg == "android" && channel == "DEVELOPER_IMPORTANT") {
            logDebug("matchesHiddenNotif: USB debugging notification suppressed (pkg=$pkg, channel=$channel)")
            return true
        }
        return false
    }

    private fun launchWeatherApp() {
        val ctx = StatusState.appContext() ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(WEATHER_PACKAGE)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(WEATHER_PACKAGE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchFromStatusBar(ctx, intent)) return
        runCatching { ctx.startActivity(intent) }
    }

    /** The bar's own dismiss-then-launch, so the shade closes behind the app. */
    private fun launchFromStatusBar(ctx: Context, intent: Intent): Boolean = runCatching {
        val loader = ctx.classLoader
        val statusBarClass = Refl.cls("com.android.systemui.statusbar.phone.StatusBar", loader) ?: return false
        val dependency = Refl.cls("com.android.systemui.plugins.Dependency", loader) ?: return false
        val statusBar = Refl.method(dependency, "get", Class::class.java)?.invoke(null, statusBarClass)
            ?: return false
        val launch = statusBarClass.methods.firstOrNull {
            it.name.contains("startActivityDismissingKeyguard") && it.parameterCount >= 3
        } ?: return false
        if (launch.parameterCount == 4) {
            launch.invoke(statusBar, intent, true, true, 536870912)
        } else {
            launch.invoke(statusBar, intent, true, true)
        }
        true
    }.getOrDefault(false)

    private fun weatherText(): String {
        val data = WeatherManager.data
        if (data == null) {
            // Not forced: the manager's stamp backs off after a failed query too, so this
            // costs at most one attempt per cache window however often the date line is
            // rebuilt (every time tick).
            StatusState.appContext()?.let { WeatherManager.fetchAsync(it) }
            return ""
        }
        val style = ModuleConfig.appearance().shadeWeatherStyle
        val glyph = WeatherIconDrawer.glyphFor(data.condition)
        val text = when (style) {
            Prefs.WEATHER_STYLE_TEXT -> data.text()
            Prefs.WEATHER_STYLE_ICON, Prefs.WEATHER_STYLE_SYMBOL -> "$glyph ${data.tempText()}"
            Prefs.WEATHER_STYLE_ICON_TEXT, Prefs.WEATHER_STYLE_SYMBOL_TEXT -> "$glyph ${data.text()}"
            else -> data.text()
        }
        logDebug("weatherText resolved: '$text' (condition=${data.condition}, temp=${data.tempC}, style=$style)")
        return text
    }

    fun onConfigChanged(a: StatusAppearance) {
        logDebug("onConfigChanged: shadeWeather=${a.shadeWeather}, shadeLunar=${a.shadeLunar}")
        HiddenViews.applyAll(a)
        refreshDateLine()
    }

    /** A new weather reading landed; updates the date line immediately. */
    fun onWeatherChanged() {
        logDebug("onWeatherChanged received, refreshing date line")
        refreshDateLine()
    }

    /**
     * Recomposes the date line via TimeManager time tick.
     */
    private fun refreshDateLine() {
        runCatching {
            val manager = timeManager() ?: return@runCatching
            Refl.methodNamed(manager.javaClass, "updateNextTimeMinute", 1)?.invoke(manager, 0L)
            Refl.methodNamed(manager.javaClass, "updateNextTimeSecond", 1)?.invoke(manager, 0L)
        }
    }

    private fun timeManager(): Any? {
        sTimeManager?.get()?.let { return it }
        val loader = StatusState.appContext()?.classLoader ?: return null
        val dependency = Refl.cls("com.android.systemui.plugins.Dependency", loader) ?: return null
        val managerClass = Refl.cls("com.android.systemui.time.TimeManager", loader) ?: return null
        val manager = runCatching {
            Refl.method(dependency, "get", Class::class.java)?.invoke(null, managerClass)
        }.getOrNull() ?: return null
        sTimeManager = WeakReference(manager)
        return manager
    }
}
