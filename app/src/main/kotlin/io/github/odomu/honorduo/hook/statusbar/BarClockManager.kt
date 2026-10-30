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
import io.github.odomu.honorduo.hook.core.SystemUiHook
import io.github.odomu.honorduo.hook.tracker.BarVisibility.shown
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker.isScreenOn
import io.github.odomu.honorduo.hook.tracker.WeatherManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.GlyphInk
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
        return SystemUiHook.hook(module, method, "honorduo-pivot") { chain ->
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
        // The clock host is chosen separately from the icon tree: the lock screen keeps
        // its own `HwStatusbarContents` (with a view of the same `clock` id) attached
        // while it is merely invisible, and it already shows the time in the middle of
        // the screen. Drawing a second clock there duplicated it and took the real bar's
        // copy away, so [clockHostViewGroup] never returns that tree - icon settlement,
        // which the lock screen does need for the merged glyph, still uses every tree.
        val contents = StatusBarIconManager.clockHostViewGroup
        val cached = sBarClock?.get()
        if (cached != null && (!cached.isAttachedToWindow || !cached.inTreeOf(contents))) {
            sBarClock = null
        }
        val owner = contents
        val native = sBarClock?.get() ?: owner?.let {
            val clockId = StatusBarIconManager.clockId
            if (clockId == 0) null else (it.findViewById(clockId) as? TextView)?.also { found ->
                sBarClock = WeakReference(found)
            }
        }
        val suppress = !a.enabled || a.barClockHidden || StatusBarRowManager.suppressed ||
            (a.glyph && a.hidesStatusClock()) || !a.drawsBarClock()
        if (owner == null || suppress) {
            // No tree to draw in, or nothing to draw: drop the replacement and hand the
            // bar's own clock back, so the lock screen keeps its time.
            removeBarClock()
            native?.let { StatusBarIconManager.settleClock(it, a) }
            return
        }
        if (native == null) return
        val view = ensureBarClock(native)
        if (view == null) {
            // Only a clock that exists but is not laid out yet is worth retrying. A
            // parentless one is re-resolved by the next row layout (see [requestOnLayout]);
            // posting a retry for it resent the same failing pass forever on the main thread.
            if (sBarClockView == null && !sBarClockPending && native.parent != null) {
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

    /**
     * The bar's own clock is replaced whenever the host re-inflates the row, and no hook
     * of ours sees that swap. A layout pass does, and this is what it asks: cheap enough
     * to run on every layout (two booleans unless the view is missing), and it re-adds
     * the clock instead of waiting for the next settings change to do it.
     */
    fun requestOnLayout(a: StatusAppearance = ModuleConfig.appearance()) {
        if (!needsView(a)) return
        // Only when the view is missing or belongs to a different tree: a clock that is
        // already parented is left alone, so a layout pass that this file's own
        // requestLayout triggered cannot come back here.
        val parent = sBarClock?.get()?.parent
        val view = sBarClockView
        if (view != null && view.isAttachedToWindow && parent != null && view.parent === parent) return
        sync(a)
    }

    /** Whether this object should be drawing the bar's clock for [a] at all. */
    private fun needsView(a: StatusAppearance): Boolean =
        a.enabled && a.drawsBarClock() && !a.barClockHidden &&
            !StatusBarRowManager.suppressed && !(a.glyph && a.hidesStatusClock())

    /** Whether [view] is [root] itself or a descendant of it. */
    private fun View.inTreeOf(root: View?): Boolean {
        if (root == null) return false
        var current: View? = this
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
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
        val isDark = GlyphInk.isDarkBackground(native.currentTextColor)
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
        if (!a.enabled || native == null || !native.isAttachedToWindow ||
            (a.glyph && a.hidesStatusClock()) || !a.drawsBarClock()
        ) {
            // `native.parent == null` is not "nothing to draw": it is the host having
            // replaced the clock under us. Resolving again is what puts a clock back;
            // dropping the view here was the second half of the missing-clock bug.
            if (native == null || !native.isAttachedToWindow) sync(a) else removeBarClock()
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
