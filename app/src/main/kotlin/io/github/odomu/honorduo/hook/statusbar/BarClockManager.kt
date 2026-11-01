/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.odomu.honorduo.hook.statusbar

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.core.Refl
import io.github.odomu.honorduo.hook.core.SystemUiHook
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker
import io.github.odomu.honorduo.hook.tracker.WeatherManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.GlyphInk
import io.github.odomu.honorduo.ui.view.BarClockView
import java.util.WeakHashMap

/**
 * Custom clock pixels inside the native clock's own measured box.
 * SystemUI retains the View, id, parent, visibility, tint and animation targets.
 * Each native clock has its own renderer; no View moves between bar trees.
 */
internal object BarClockManager {
    internal const val BAR_CLOCK_WIDTH_SHARE = 0.35f
    internal const val BAR_CLOCK_GAP = 0.28f
    internal const val BAR_CLOCK_WORD_GAP = 0.16f
    internal const val BAR_CLOCK_LINE_SPACING = 0.86f

    private val clipped = WeakHashMap<ViewGroup, BooleanArray>()
    private val clocks = WeakHashMap<TextView, BarClockView>()
    private val modes = WeakHashMap<TextView, Int>()

    /** A block fitted to the bar is taller than the clock's padded box; stop the clip. */
    private fun allowOverflow(view: View) {
        var parent = view.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < 3) {
            if (!clipped.containsKey(parent)) {
                clipped[parent] = booleanArrayOf(parent.clipChildren, parent.clipToPadding)
            }
            parent.clipChildren = false
            parent.clipToPadding = false
            parent = parent.parent as? ViewGroup
            depth++
        }
    }

    private fun restoreOverflow() {
        for ((group, flags) in clipped) {
            group.clipChildren = flags[0]
            group.clipToPadding = flags[1]
        }
        clipped.clear()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { sync() }

    fun install(module: XposedModule, clockClass: Class<*>): Int {
        val measure = Refl.methodNamed(clockClass, "onMeasure", 2) ?: return 0
        val draw = Refl.methodNamed(clockClass, "onDraw", 1) ?: return 0
        val dimensions = Refl.methodNamed(View::class.java, "setMeasuredDimension", 2) ?: return 0
        var count = 0

        count += SystemUiHook.hook(module, measure, "honorduo-clock-measure") { chain ->
            val result = chain.proceed()
            val native = chain.thisObject as? TextView
            val a = ModuleConfig.appearance()
            if (native != null && clockClass.isInstance(native) && hidesPixels(native, a)) {
                dimensions.invoke(native, 0, native.measuredHeight)
                return@hook result
            }
            if (native != null && clockClass.isInstance(native) && usesRenderer(native, a)) {
                allowOverflow(native)
                val renderer = clocks.getOrPut(native) { BarClockView(native.context) }
                renderer.setPadding(native.paddingLeft, native.paddingTop, native.paddingRight, native.paddingBottom)
                val widthSpec = (chain.getArg(0) as Number).toInt()
                val heightSpec = (chain.getArg(1) as Number).toInt()
                updateBarClock(renderer, a, native, View.MeasureSpec.getSize(widthSpec))
                renderer.measure(widthSpec, heightSpec)
                dimensions.invoke(native, renderer.measuredWidth, View.resolveSize(renderer.measuredHeight, heightSpec))
            }
            result
        }
        count += SystemUiHook.hook(module, draw, "honorduo-clock-draw") { chain ->
            val native = chain.thisObject as? TextView
            val a = ModuleConfig.appearance()
            if (native != null && clockClass.isInstance(native) && hidesPixels(native, a)) return@hook null
            val renderer = if (native != null && clockClass.isInstance(native) && usesRenderer(native, a)) clocks[native] else null
            if (native == null || renderer == null) {
                chain.proceed()
            } else {
                drawRenderer(native, renderer, chain.getArg(0) as Canvas)
                null
            }
        }
        count += SystemUiHook.hook(module, Refl.methodNamed(clockClass, "onAttachedToWindow", 0), "honorduo-clock-attach") { chain ->
            val result = chain.proceed()
            (chain.thisObject as? TextView)?.let { noteNativeClock(it) }
            result
        }
        count += SystemUiHook.hook(module, Refl.methodNamed(clockClass, "onDetachedFromWindow", 0), "honorduo-clock-detach") { chain ->
            val result = chain.proceed()
            (chain.thisObject as? TextView)?.let { clocks.remove(it) }
            (chain.thisObject as? TextView)?.let { modes.remove(it) }
            if (clocks.isEmpty()) restoreOverflow()
            scheduleTick()
            result
        }
        return count
    }

    internal fun drawRenderer(native: TextView, renderer: BarClockView, canvas: Canvas) {
        // Sync padding every draw: it may have changed between the last measure pass
        // and this draw call (a one-frame delay that briefly misaligns the block).
        renderer.setPadding(
            native.paddingLeft, native.paddingTop,
            native.paddingRight, native.paddingBottom,
        )
        if (renderer.width != native.width || renderer.height != native.height) {
            renderer.measure(
                View.MeasureSpec.makeMeasureSpec(native.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(native.height, View.MeasureSpec.EXACTLY),
            )
        }
        // Always layout so the draw coordinate system matches the current native size
        // even when a layout pass ran after the last measure hook (capsule collapse).
        renderer.layout(0, 0, native.width, native.height)
        val save = canvas.save()
        try {
            canvas.translate(native.scrollX.toFloat(), native.scrollY.toFloat())
            renderer.draw(canvas)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    fun noteNativeClock(clock: TextView) {
        StatusBarIconManager.resolveIds(clock.resources)
        if (clock.id != StatusBarIconManager.clockId || StatusBarIconManager.isKeyguardTree(clock)) return
        clocks.getOrPut(clock) { BarClockView(clock.context) }
        allowOverflow(clock)
        clock.typeface?.let { GlyphInk.adoptSystemTypeface(it) }
        sync()
    }

    private fun usesRenderer(clock: TextView, a: StatusAppearance): Boolean =
        a.enabled && !StatusBarRowManager.suppressed && !a.barClockHidden &&
            !a.hidesStatusClock() && a.drawsBarClock() &&
            clock.id == StatusBarIconManager.clockId &&
            !StatusBarIconManager.isKeyguardTree(clock) && StatusBarIconManager.insideOwnedContainer(clock)

    private fun hidesPixels(clock: TextView, a: StatusAppearance): Boolean =
        a.enabled && !StatusBarRowManager.suppressed &&
            clock.id == StatusBarIconManager.clockId &&
            !StatusBarIconManager.isKeyguardTree(clock) && StatusBarIconManager.insideOwnedContainer(clock) &&
            (a.barClockHidden || a.hidesStatusClock())

    private fun mode(clock: TextView, a: StatusAppearance): Int = when {
        usesRenderer(clock, a) -> 1
        hidesPixels(clock, a) -> 2
        else -> 0
    }
    fun sync(a: StatusAppearance = ModuleConfig.appearance()) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { sync() }
            return
        }
        for ((native, renderer) in clocks) {
            val nextMode = mode(native, a)
            val oldMode = modes.put(native, nextMode)
            val geometry = renderer.geometryVersion
            if (nextMode == 1) updateBarClock(renderer, a, native)
            if (oldMode != nextMode || geometry != renderer.geometryVersion) native.requestLayout()
            native.invalidate()
        }
        scheduleTick()
    }

    fun onScreenStateChanged(screenOn: Boolean) {
        if (!screenOn) handler.removeCallbacks(tick)
    }

    fun onVisibilityChanged(shown: Boolean) {
        if (!shown) handler.removeCallbacks(tick)
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        val a = ModuleConfig.appearance()
        if (!ScreenStateTracker.isScreenOn || !BarVisibility.shown ||
            clocks.keys.none { it.isShown && usesRenderer(it, a) }
        ) return
        val period = if (a.clockSeconds) 1_000L else 60_000L
        val now = System.currentTimeMillis()
        handler.postDelayed(tick, period - now % period + 20L)
    }

    private fun updateBarClock(view: BarClockView, a: StatusAppearance, native: TextView, availableWidth: Int = native.measuredWidth) {
        val texts = ClockTicker.barTexts(a.clockDate, a.clockWeekday, a.clockDateFormat, a.clockWeekdayFormat, a.clockLunar)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val ink = StatusBarIconManager.foreground(native, native.currentTextColor)
        val isDark = GlyphInk.isDarkBackground(ink)
        val weather = if (a.clockWeather) WeatherManager.data else null
        val density = native.resources.displayMetrics.density
        view.apply(
            texts[0], texts[1], texts[2], texts[3],
            weatherCondition = weather?.condition.orEmpty(), weatherTemp = weather?.tempText().orEmpty(),
            weatherStyle = a.clockWeatherStyle, clockStyle = a.clockStyle, weight = a.clockWeight,
            ink = ink,
            weekdayColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight,
            dateColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight,
            lunarColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight,
            weatherColor = if (isDark) a.clockWeatherColorOnDark else a.clockWeatherColorOnLight,
            baseSize = native.textSize * userScale, maxHeight = barHeight(native),
            topOffsetX = a.clockTopOffsetX * density, bottomOffsetX = a.clockBottomOffsetX * density,
            lunarGap = a.clockLunarGap * density, rowGap = a.clockRowGap * density,
            availableWidth = availableWidth,
        )
        view.digitColors = if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight
    }

    @SuppressLint("InternalInsetResource")
    private fun barHeight(view: View): Int {
        val id = Refl.resId(view.resources, "status_bar_height", "dimen", "android")
        return if (id != 0) view.resources.getDimensionPixelSize(id) else view.height
    }
}
