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

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.core.Refl
import io.github.odomu.honorduo.hook.core.StatusHooks
import io.github.odomu.honorduo.hook.tracker.BarVisibility.shown
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker.isScreenOn
import io.github.odomu.honorduo.hook.tracker.WeatherManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.IconRenderer
import io.github.odomu.honorduo.ui.view.BarClockView
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Calendar
import kotlin.math.min
/**
 * Manages the custom status bar clock (two-line date/weekday/lunar/time),
 * its lifecycle, view mounting, overflow clipping, and stepping cadence.
 * Restored 100% to original classic logic.
 */
internal object BarClockManager {

    private const val BAR_CLOCK_CLIP_DEPTH = 3
    internal const val BAR_CLOCK_WIDTH_SHARE = 0.35f
    internal const val BAR_CLOCK_GAP = 0.28f

    /**
     * Gap between the pieces of the date line - date, lunar, weekday - as a share of
     * their text size.
     *
     * Tighter than [BAR_CLOCK_GAP], which separates a caption from the time: three short
     * CJK runs at the caption's own gap read as three separate items rather than one line.
     */
    internal const val BAR_CLOCK_WORD_GAP = 0.16f
    internal const val BAR_CLOCK_LINE_SPACING = 0.86f
    private const val BAR_CLOCK_MINUTE_LAG_MS = 250L

    private var sBarClock: WeakReference<TextView>? = null
    private var sBarClockView: BarClockView? = null
    private var sBarClockLoop = false
    private var sBarClockToken = 0
    private var sBarClockPending = false
    private val sBarClockClipped = ArrayList<Clipped>()

    private var sOperatorWidth: Field? = null
    private var sPaddingLeft: Field? = null
    private var sPaddingRight: Field? = null

    val isClockAttached: Boolean
        get() = sBarClockView?.isAttachedToWindow == true

    fun noteNativeClock(clock: TextView) {
        sBarClock = WeakReference(clock)
    }

    fun hookPivotXY(module: XposedModule, cl: ClassLoader): Int {
        val controllerCls = Refl.cls("com.android.systemui.statusbar.phone.HnPhoneStatusBarAnimController", cl)
            ?: return 0
        val contentsCls = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl)
        val method = Refl.methodNamed(controllerCls, "setPivotXY", 3) ?: return 0
        sOperatorWidth = Refl.field(controllerCls, "mOperatorViewWidth")
        sPaddingLeft = Refl.field(contentsCls, "mCurrentPaddingLeft")
        sPaddingRight = Refl.field(contentsCls, "mCurrentPaddingRight")
        return StatusHooks.hook(module, method, "honorduo-pivot") { chain ->
            val first = chain.getArg(0) as? ViewGroup
            val second = chain.getArg(1) as? ViewGroup
            if (first != null && second != null) {
                setPivotXY(
                    chain.thisObject, first, second,
                    chain.getArg(2) as? Boolean ?: false,
                )
            }
            null
        }
    }

    private fun setPivotXY(controller: Any?, a: ViewGroup, b: ViewGroup, operator: Boolean) {
        val res = a.resources
        val rtl = b.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val clockId = StatusBarIconManager.clockId
        val clock = if (clockId != 0) b.findViewById<View>(clockId) else null
        val left = if (rtl) (Refl.get(sPaddingLeft, b) as? Number)?.toInt() ?: 0 else clock?.left ?: 0
        val right = if (rtl) clock?.right ?: 0 else b.width - ((Refl.get(sPaddingRight, b) as? Number)?.toInt() ?: 0)
        val operatorId = Refl.resId(res, "operator_name_container", "id", b.context.packageName)
        val systemIconId = Refl.resId(res, "system_icon_area", "id", b.context.packageName)
        val operatorWidth = (Refl.get(sOperatorWidth, controller) as? Number)?.toFloat() ?: 0f

        val count = min(a.childCount, b.childCount)
        for (i in 0 until count) {
            val from = a.getChildAt(i) ?: continue
            val to = b.getChildAt(i) ?: continue
            val mirrored = (to.layoutDirection == View.LAYOUT_DIRECTION_RTL) == (systemIconId != 0 && to.id == systemIconId)
            from.pivotX = when {
                !mirrored && operator && operatorId != 0 && from.id == operatorId -> operatorWidth
                !mirrored -> (right - to.left).toFloat()
                operator && operatorId != 0 && from.id == operatorId -> 0f
                else -> (left - to.left).toFloat()
            }
            from.pivotY = to.height / 2f
        }
    }

    fun sync(a: StatusAppearance = ModuleConfig.appearance()) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { sync(a) }
            return
        }
        try {
            syncNow(a)
        } catch (_: Throwable) {}
    }

    private fun syncNow(a: StatusAppearance) {
        var native = sBarClock?.get()
        if (native == null) {
            val contents = StatusBarIconManager.contentsViewGroup
            val clockId = StatusBarIconManager.clockId
            if (contents != null && clockId != 0) {
                native = contents.findViewById(clockId) as? TextView
                if (native != null) {
                    sBarClock = WeakReference(native)
                }
            }
        }
        if (!a.enabled || native == null || a.barClockHidden || (a.glyph && a.hidesStatusClock()) ||
            !a.drawsBarClock()
        ) {
            removeBarClock()
            // The clock's visibility is decided by ownership, never by this branch:
            // when the arrangement draws the time itself the bar's copy has to stay
            // hidden, and "handing it back" here was what put it back in the corner.
            native?.let { StatusBarIconManager.settleClock(it, a) }
            return
        }
        val view = ensureBarClock(native)
        if (view == null) {
            if (sBarClockView == null && !sBarClockPending) {
                sBarClockPending = true
                native.post {
                    sBarClockPending = false
                    sync(ModuleConfig.appearance())
                }
            }
            return
        }
        updateBarClock(view, a, native)
        view.invalidate()
        startBarClockLoop(view)
    }

    fun dropNow() {
        sBarClockLoop = false
        sBarClockToken++
        val view = sBarClockView ?: return
        sBarClockView = null
        removeFromParent(view)
        restoreClipping()
    }

    fun onScreenStateChanged(screenOn: Boolean) {
        if (!screenOn) {
            sBarClockLoop = false
        } else if (shown) {
            sync(ModuleConfig.appearance())
        }
    }

    fun onVisibilityChanged(shown: Boolean) {
        if (!shown) {
            sBarClockLoop = false
        } else if (isScreenOn) {
            sync(ModuleConfig.appearance())
        }
    }

    private fun removeBarClock() {
        sBarClockLoop = false
        sBarClockToken++
        val view = sBarClockView
        sBarClockView = null
        if (view != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                removeFromParent(view)
                restoreClipping()
            } else {
                view.post {
                    removeFromParent(view)
                    restoreClipping()
                }
            }
        }
        sBarClock?.get()?.let { StatusBarIconManager.settleClock(it, ModuleConfig.appearance()) }
    }

    /** The bar's own clock, for the suppression's re-assert; null until it has attached. */
    val nativeClock: TextView?
        get() = sBarClock?.get()

    private fun ensureBarClock(native: TextView): BarClockView? {
        val parent = native.parent as? ViewGroup ?: return null
        val existing = sBarClockView
        if (existing != null && existing.parent === parent) {
            removeStrayClocks(parent, keep = existing)
            return existing
        }
        if (parent.isInLayout) {
            if (!sBarClockPending) {
                sBarClockPending = true
                parent.post {
                    sBarClockPending = false
                    sync(ModuleConfig.appearance())
                }
            }
            return null
        }
        val index = parent.indexOfChild(native)
        if (index < 0) return null
        if (existing != null) {
            removeFromParent(existing)
        }
        removeStrayClocks(parent, keep = null)
        val view = BarClockView(native.context)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = lp
        sBarClockView = view
        return try {
            parent.addView(view, index, lp)
            allowOverflow(view)
            StatusBarIconManager.hide(native)
            view
        } catch (_: Throwable) {
            sBarClockView = null
            null
        }
    }

    private fun removeStrayClocks(parent: ViewGroup, keep: BarClockView?) {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child is BarClockView && child !== keep) {
                try {
                    parent.removeViewAt(i)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun removeFromParent(view: View) {
        try {
            (view.parent as? ViewGroup)?.removeView(view)
        } catch (_: Throwable) {}
    }

    private fun updateBarClock(view: BarClockView, a: StatusAppearance, native: TextView) {
        val texts = ClockTicker.barTexts(
            a.clockDate, a.clockWeekday, a.clockDateFormat, a.clockWeekdayFormat,
        )
        val time = texts[0]
        val date = texts[1]
        val weekday = texts[2]
        val lunar = if (a.clockLunar) texts[3] else ""
        view.bindInk(native)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val baseSize = native.textSize * userScale
        val density = view.resources.displayMetrics.density
        val isDark = IconRenderer.isDarkBackground(native.currentTextColor)
        val wkColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight
        val dtColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight
        val lnColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight
        val wtColor = if (isDark) a.clockWeatherColorOnDark else a.clockWeatherColorOnLight
        val wData = if (a.clockWeather) WeatherManager.data else null
        view.apply(
            time, date, weekday, lunar,
            weatherCondition = wData?.condition.orEmpty(),
            weatherTemp = wData?.tempText().orEmpty(),
            weatherStyle = a.clockWeatherStyle,
            clockStyle = a.clockStyle,
            weight = a.clockWeight,
            ink = native.currentTextColor,
            weekdayColor = wkColor, dateColor = dtColor, lunarColor = lnColor, weatherColor = wtColor,
            baseSize = baseSize, maxHeight = barHeight(view),
            topOffsetX = a.clockTopOffsetX * density, bottomOffsetX = a.clockBottomOffsetX * density,
            lunarGap = a.clockLunarGap * density, rowGap = a.clockRowGap * density,
        )
        // Outside `apply`, which early-outs when nothing else changed: an array cannot take
        // part in that comparison, so a colour change would otherwise never reach the view.
        view.digitColors = if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight
        view.invalidate()
        view.requestLayout()
        (view.parent as? View)?.requestLayout()
    }

    private fun startBarClockLoop(view: BarClockView) {
        sBarClockLoop = true
        val token = ++sBarClockToken
        val handler = view.handler ?: Handler(Looper.getMainLooper())
        handler.postDelayed({ stepBarClock(token) }, nextBarClockDelay())
    }

    private fun stepBarClock(token: Int) {
        if (!sBarClockLoop || token != sBarClockToken) return
        val view = sBarClockView
        if (view == null || !view.isAttachedToWindow ||
            !isScreenOn ||
            !shown
        ) {
            sBarClockLoop = false
            return
        }
        val a = ModuleConfig.appearance()
        val native = sBarClock?.get()
        if (!a.enabled || native == null || (a.glyph && a.hidesStatusClock()) || !a.drawsBarClock()) {
            removeBarClock()
            return
        }
        try {
            updateBarClock(view, a, native)
        } catch (_: Throwable) {
        } finally {
            if (sBarClockLoop && token == sBarClockToken) {
                val handler = view.handler ?: Handler(Looper.getMainLooper())
                handler.postDelayed({ stepBarClock(token) }, nextBarClockDelay())
            }
        }
    }

    private fun nextBarClockDelay(): Long {
        val now = Calendar.getInstance()
        val a = ModuleConfig.appearance()
        if (a.clockSeconds) {
            return maxOf(1L, 1000L - now.get(Calendar.MILLISECOND))
        }
        val elapsed = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        return maxOf(1L, 60_000L - elapsed + BAR_CLOCK_MINUTE_LAG_MS)
    }

    @SuppressLint("InternalInsetResource")
    private fun barHeight(view: View): Int {
        val id = Refl.resId(view.resources, "status_bar_height", "dimen", "android")
        return if (id != 0) view.resources.getDimensionPixelSize(id) else 0
    }

    private fun allowOverflow(view: View) {
        var current: ViewParent? = view.parent
        var depth = 0
        while (current is ViewGroup && depth < BAR_CLOCK_CLIP_DEPTH) {
            val group = current
            sBarClockClipped.add(Clipped(group, group.clipChildren, group.clipToPadding))
            group.clipChildren = false
            group.clipToPadding = false
            current = group.parent
            depth++
        }
    }

    private fun restoreClipping() {
        for (clipped in sBarClockClipped) {
            clipped.group.clipChildren = clipped.children
            clipped.group.clipToPadding = clipped.padding
        }
        sBarClockClipped.clear()
    }

    private class Clipped(val group: ViewGroup, val children: Boolean, val padding: Boolean)
}
