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

import android.view.View
import android.view.ViewParent
import android.view.ViewTreeObserver
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.render.WeatherIconDrawer
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * The notification shade (left pull) and the control center (right pull): the two
 * header buttons the module can take away, and the extra readings it can print on
 * the shade's date line.
 *
 * Both panels are Compose hosts on MagicOS, so nothing here touches a text view:
 * the buttons are ordinary views embedded through `AndroidView`, and the date line
 * is produced by one instance method the panel re-reads on every recomposition.
 */
internal object ShadeHook {

    private const val CLS_CONTROL_HEADER = "com.android.systemui.controllercenter.HnControlCenterHeader"
    private const val CLS_NOTIF_BELL = "com.android.systemui.statusbar.notification.panelheader.NotifMgeBtn"
    private const val CLS_NOTIF_INTERACTOR =
        "com.android.systemui.statusbar.notification.panelheader.NotifCenterHeaderInteractorImpl"

    private const val FIELD_POWER_BUTTON = "mPowerButton"
    private const val METHOD_CLOCK_DATE = "getCurrentClockDate"

    /** Views already carrying a re-assert listener, so one view is wired once. */
    private val watched = ConcurrentHashMap<Int, WeakReference<View>>()

    fun install(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        n += hookPowerButton(module, cl)
        n += hookBell(module, cl)
        n += hookDateLine(module, cl)
        return n
    }

    /**
     * The control center's power button.
     *
     * The host reads `hw_hide_statusbar_power` into a field and never uses it, so
     * the button has to be collapsed here. It sits in a `LinearLayout`, which closes
     * up around a `GONE` child on its own.
     */
    private fun hookPowerButton(module: XposedModule, cl: ClassLoader): Int {
        val header = Refl.cls(CLS_CONTROL_HEADER, cl) ?: return 0
        return TrioHooks.hook(module, Refl.method(header, "onFinishInflate"), "honorduo-cc-power") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject as? View
            if (self != null) {
                val button = Refl.get(Refl.field(self.javaClass, FIELD_POWER_BUTTON), self) as? View
                if (button != null) watch(button) { a -> a.shadeHidePower }
            }
            result
        }
    }

    /**
     * The bell beside the shade's clock, a plain view inside the Compose header.
     *
     * `onAttachedToWindow` is declared on `View`, so the hook sees every view in the
     * process - the class-name guard in front of the work is what keeps that from
     * costing anything: attaches are rare, and everything else returns at once.
     */
    private fun hookBell(module: XposedModule, cl: ClassLoader): Int {
        if (Refl.cls(CLS_NOTIF_BELL, cl) == null) return 0
        val attach = Refl.method(View::class.java, "onAttachedToWindow") ?: return 0
        return TrioHooks.hook(module, attach, "honorduo-shade-attach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject as? View
            if (self != null && self.javaClass.name == CLS_NOTIF_BELL) {
                watch(self) { a -> a.shadeHideBell }
            }
            result
        }
    }

    /**
     * Collapses [view] while [hidden] answers true, and keeps it collapsed.
     *
     * The answer is re-read every frame rather than captured, so flipping the switch
     * brings the button back without a restart, and a host that re-shows the view in
     * its own refresh is overruled on the next frame. The listener unhooks itself once
     * the view leaves the hierarchy, so a rebuilt panel does not stack them.
     */
    private fun watch(view: View, hidden: (TrioAppearance) -> Boolean) {
        applyVisibility(view, hidden(TrioConfig.appearance()))
        val key = System.identityHashCode(view)
        if (watched.putIfAbsent(key, WeakReference(view)) != null) return
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val parent: ViewParent? = view.parent
                if (parent == null) {
                    runCatching { view.viewTreeObserver.removeOnPreDrawListener(this) }
                    watched.remove(key)
                    return true
                }
                applyVisibility(view, hidden(TrioConfig.appearance()))
                return true
            }
        })
    }

    private fun applyVisibility(view: View, hidden: Boolean) {
        val wanted = if (hidden) View.GONE else View.VISIBLE
        if (view.visibility != wanted) {
            view.visibility = wanted
        }
    }

    /**
     * The shade's date line.
     *
     * The host builds the string once per minute (and on language or time-zone
     * changes), so appending here costs one read and one format per minute rather
     * than anything per frame.
     */
    private fun hookDateLine(module: XposedModule, cl: ClassLoader): Int {
        val interactor = Refl.cls(CLS_NOTIF_INTERACTOR, cl) ?: return 0
        val method = TrioHooks.methodNamed(interactor, METHOD_CLOCK_DATE, 1)
            ?: TrioHooks.methodNamed(interactor, METHOD_CLOCK_DATE, 0)
            ?: return 0
        return TrioHooks.hook(module, method, "honorduo-shade-date") { chain ->
            val original = chain.proceed() as? String
            val a = TrioConfig.appearance()
            if (original.isNullOrEmpty() || (!a.shadeLunar && !a.shadeWeather)) {
                original
            } else {
                decorateDateLine(original, a.shadeLunar, a.shadeWeather)
            }
        }
    }

    /**
     * [line] with the lunar date and weather appended.
     *
     * Appended, not prepended as a second line: the host renders this string in a
     * one-line box, so a `\n` there pushed the real date out of view instead of
     * adding a row above it. The separators keep the three pieces readable and the
     * date itself is never touched.
     */
    private fun decorateDateLine(line: String, lunar: Boolean, weather: Boolean): String {
        val extra = StringBuilder()
        if (lunar) {
            val l = ClockTicker.formatLunar(java.util.Date())
            if (l.isNotEmpty()) extra.append(l)
        }
        if (weather) {
            val w = weatherText()
            if (w.isNotEmpty()) {
                if (extra.isNotEmpty()) extra.append(' ')
                extra.append(w)
            }
        }
        if (extra.isEmpty()) return line
        return "$line ${extra}"
    }

    /**
     * The weather in the chosen style. This line is plain text, so every icon style
     * ends up as the system's symbol: there is nothing here that can take a canvas.
     */
    private fun weatherText(): String {
        val data = WeatherManager.data ?: return ""
        val style = TrioConfig.appearance().shadeWeatherStyle
        if (style == Prefs.WEATHER_STYLE_TEXT) return data.text()
        val glyph = WeatherIconDrawer.glyphFor(data.condition)
        return when (style) {
            Prefs.WEATHER_STYLE_ICON, Prefs.WEATHER_STYLE_SYMBOL -> "$glyph ${data.tempText()}"
            else -> "$glyph ${data.text()}"
        }
    }
}
