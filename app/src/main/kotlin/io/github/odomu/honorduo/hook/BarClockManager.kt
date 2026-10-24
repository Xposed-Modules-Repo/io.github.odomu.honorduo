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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.render.TrioGeometry
import io.github.odomu.honorduo.render.TrioRenderer
import io.github.odomu.honorduo.render.WeatherIconDrawer
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Manages the custom status bar clock (two-line date/weekday/lunar/time),
 * its lifecycle, view mounting, overflow clipping, and stepping cadence.
 * Restored 100% to original classic logic.
 */
internal object BarClockManager {

    private const val BAR_CLOCK_CLIP_DEPTH = 3
    private const val BAR_CLOCK_WIDTH_SHARE = 0.35f
    private const val BAR_CLOCK_GAP = 0.28f

    /**
     * Gap between the pieces of the date line - date, lunar, weekday - as a share of
     * their text size.
     *
     * Tighter than [BAR_CLOCK_GAP], which separates a caption from the time: three short
     * CJK runs at the caption's own gap read as three separate items rather than one line.
     */
    private const val BAR_CLOCK_WORD_GAP = 0.16f
    private const val BAR_CLOCK_LINE_SPACING = 0.86f
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
        val method = TrioHooks.methodNamed(controllerCls, "setPivotXY", 3) ?: return 0
        sOperatorWidth = Refl.field(controllerCls, "mOperatorViewWidth")
        sPaddingLeft = Refl.field(contentsCls, "mCurrentPaddingLeft")
        sPaddingRight = Refl.field(contentsCls, "mCurrentPaddingRight")
        return TrioHooks.hook(module, method, "honorduo-pivot") { chain ->
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
        val operatorId = res.getIdentifier("operator_name_container", "id", b.context.packageName)
        val systemIconId = res.getIdentifier("system_icon_area", "id", b.context.packageName)
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

    fun sync(a: TrioAppearance = TrioConfig.appearance()) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { sync(a) }
            return
        }
        try {
            syncNow(a)
        } catch (_: Throwable) {}
    }

    private fun syncNow(a: TrioAppearance) {
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
            // Releasing would undo the user's own "hide the bar clock" switch, which is
            // the one case where nothing stands in for the view.
            if (!a.barClockHidden) native?.let { StatusBarIconManager.release(it) }
            return
        }
        val view = ensureBarClock(native)
        if (view == null) {
            if (sBarClockView == null && !sBarClockPending) {
                sBarClockPending = true
                native.post {
                    sBarClockPending = false
                    sync(TrioConfig.appearance())
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
        } else {
            sync(TrioConfig.appearance())
        }
    }

    private fun removeBarClock() {
        sBarClockLoop = false
        sBarClockToken++
        val view = sBarClockView
        sBarClockView = null
        if (view != null) {
            view.post {
                removeFromParent(view)
                restoreClipping()
            }
        }
        if (!TrioConfig.appearance().barClockHidden) {
            sBarClock?.get()?.let { StatusBarIconManager.release(it) }
        }
    }

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
                    sync(TrioConfig.appearance())
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

    private fun updateBarClock(view: BarClockView, a: TrioAppearance, native: TextView) {
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
        val isDark = TrioRenderer.isDarkBackground(native.currentTextColor)
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
        if (view == null || !view.isAttachedToWindow) {
            sBarClockLoop = false
            return
        }
        val a = TrioConfig.appearance()
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
        val a = TrioConfig.appearance()
        if (a.clockSeconds) {
            return maxOf(1L, 1000L - now.get(Calendar.MILLISECOND))
        }
        val elapsed = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        return maxOf(1L, 60_000L - elapsed + BAR_CLOCK_MINUTE_LAG_MS)
    }

    @SuppressLint("InternalInsetResource")
    private fun barHeight(view: View): Int {
        val id = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) view.resources.getDimensionPixelSize(id) else 0
    }

    private fun allowOverflow(view: View) {
        var current: ViewParent? = view.parent
        var depth = 0
        while (current is ViewGroup && depth < BAR_CLOCK_CLIP_DEPTH) {
            val group = current as ViewGroup
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

    private class BarClockView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        /** Scratch for tight text bounds; drawing runs on the main thread only. */
        private val bounds = Rect()

        private var time = ""
        private var date = ""
        private var weekday = ""
        private var lunar = ""
        private var weatherCondition = ""
        private var weatherTemp = ""
        private var weatherStyle = Prefs.DEF_CLOCK_WEATHER_STYLE
        private var clockStyle = Prefs.DEF_CLOCK_STYLE
        private var weatherColor = 0
        private var weight = Prefs.DEF_CLOCK_WEIGHT
        private var ink = 0
        private var weekdayColor = 0
        private var dateColor = 0
        private var lunarColor = 0

        private var inkHost: WeakReference<TextView>? = null

        private var baseSize = 0f
        private var maxHeight = 0
        private var topOffsetX = 0f
        private var bottomOffsetX = 0f
        private var lunarGap = 0f
        private var rowGap = 0f

        private var timeSize = 0f
        private var lineSize = 0f
        private var blockWidth = 0f
        private var blockHeight = 0f

        fun bindInk(host: TextView) {
            inkHost = WeakReference(host)
        }

        fun refreshInk() {
            val host = inkHost?.get() ?: return
            ink = host.currentTextColor
            invalidate()
        }

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun setTranslationX(translationX: Float) {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                super.setTranslationX(0f)
            } else {
                super.setTranslationX(translationX)
            }
        }

        fun apply(
            time: String, date: String, weekday: String, lunar: String,
            weatherCondition: String, weatherTemp: String,
            weatherStyle: Int, clockStyle: Int,
            weight: Int, ink: Int,
            weekdayColor: Int, dateColor: Int, lunarColor: Int, weatherColor: Int,
            baseSize: Float, maxHeight: Int,
            topOffsetX: Float, bottomOffsetX: Float,
            lunarGap: Float, rowGap: Float,
        ) {
            if (this.time == time && this.date == date && this.weekday == weekday &&
                this.lunar == lunar && this.weatherCondition == weatherCondition &&
                this.weatherTemp == weatherTemp && this.weatherStyle == weatherStyle &&
                this.clockStyle == clockStyle && this.weight == weight &&
                this.ink == ink && this.weekdayColor == weekdayColor &&
                this.dateColor == dateColor && this.lunarColor == lunarColor &&
                this.weatherColor == weatherColor &&
                this.baseSize == baseSize && this.maxHeight == maxHeight &&
                this.topOffsetX == topOffsetX && this.bottomOffsetX == bottomOffsetX &&
                this.lunarGap == lunarGap && this.rowGap == rowGap
            ) {
                return
            }
            this.time = time
            this.date = date
            this.weekday = weekday
            this.lunar = lunar
            this.weatherCondition = weatherCondition
            this.weatherTemp = weatherTemp
            this.weatherStyle = weatherStyle
            this.clockStyle = clockStyle
            this.weight = weight
            this.ink = ink
            this.weekdayColor = weekdayColor
            this.dateColor = dateColor
            this.lunarColor = lunarColor
            this.weatherColor = weatherColor
            this.baseSize = baseSize
            this.maxHeight = maxHeight
            this.topOffsetX = topOffsetX
            this.bottomOffsetX = bottomOffsetX
            this.lunarGap = lunarGap
            this.rowGap = rowGap
            val targetW = if (width > 0) width else (resources.displayMetrics.widthPixels * BAR_CLOCK_WIDTH_SHARE).toInt()
            solve(targetW)
            requestLayout()
            (parent as? View)?.requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availW = MeasureSpec.getSize(widthMeasureSpec)
            solve(availW)
            val wantedW = ceil(blockWidth).toInt() + paddingLeft + paddingRight
            val wantedH = ceil(blockHeight).toInt() + paddingTop + paddingBottom
            val width = if (availW > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                minOf(max(1, wantedW), availW)
            } else {
                max(1, wantedW)
            }
            setMeasuredDimension(width, max(1, wantedH))
        }

        /** True for the styles that draw the module's own vector icon. */
        private fun vectorWeather(): Boolean =
            weatherStyle == Prefs.WEATHER_STYLE_ICON || weatherStyle == Prefs.WEATHER_STYLE_ICON_TEXT

        /** True for the styles that use the system's weather glyph instead. */
        private fun symbolWeather(): Boolean =
            weatherStyle == Prefs.WEATHER_STYLE_SYMBOL || weatherStyle == Prefs.WEATHER_STYLE_SYMBOL_TEXT

        /** The weather caption as drawn: the style decides whether the condition is in it. */
        private fun weatherLabel(): String {
            if (weatherTemp.isEmpty()) return ""
            if (weatherStyle == Prefs.WEATHER_STYLE_ICON || weatherStyle == Prefs.WEATHER_STYLE_SYMBOL) {
                return weatherTemp
            }
            return if (weatherCondition.isNotEmpty()) "$weatherCondition $weatherTemp" else weatherTemp
        }

        private fun weatherWidth(sz: Float): Float {
            val txt = weatherLabel()
            if (txt.isEmpty()) return 0f
            paint.textSize = sz
            val iconW = when {
                vectorWeather() -> sz * TrioGeometry.WEATHER_ICON_SCALE + sz * 0.25f
                symbolWeather() -> paint.measureText(WeatherIconDrawer.glyphFor(weatherCondition)) + sz * 0.25f
                else -> 0f
            }
            return iconW + paint.measureText(txt)
        }

        private fun solve(availW: Int) {
            if (time.isEmpty() || baseSize <= 0f) {
                timeSize = 0f
                lineSize = 0f
                blockWidth = 0f
                blockHeight = 0f
                return
            }
            paint.typeface = TrioRenderer.typefaceFor(weight)
            var scale = 1f
            val isBalanced = clockStyle == Prefs.CLOCK_STYLE_BALANCED
            val topFactor = if (isBalanced) ClockTicker.BAR_DATE_SCALE else 1f
            val bottomFactor = if (isBalanced) 1f else ClockTicker.BAR_DATE_SCALE
            // A row that exists is measured and drawn; one whose every piece is switched
            // off takes no space at all, so the surviving row grows into what it freed.
            val hasTopRow = if (isBalanced) {
                date.isNotEmpty() || weekday.isNotEmpty() || weatherTemp.isNotEmpty()
            } else {
                true
            }
            val hasBottomRow = if (isBalanced) {
                true
            } else {
                date.isNotEmpty() || lunar.isNotEmpty() || weekday.isNotEmpty()
            }

            var needed = 0f
            if (hasTopRow) needed += lineHeight(baseSize * topFactor)
            if (hasBottomRow) {
                needed += lineHeight(baseSize * bottomFactor) + (if (hasTopRow) rowGap else 0f)
            }
            val room = maxHeight.toFloat()
            if (needed > 0f && needed > room) {
                scale = room / needed
            }
            val budget = if (availW > 0) {
                availW.toFloat()
            } else {
                resources.displayMetrics.widthPixels * BAR_CLOCK_WIDTH_SHARE
            }
            timeSize = baseSize * scale
            lineSize = timeSize * ClockTicker.BAR_DATE_SCALE


            val step = gap(lineSize)
            val wordStep = wordGap(lineSize)
            var widest = 0f
            if (isBalanced) {
                if (hasTopRow) {
                    paint.textSize = lineSize
                    val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
                    val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday)
                    val wtW = weatherWidth(lineSize)
                    widest = max(widest, rowWidth(floatArrayOf(dtW, wkW, wtW), wordStep))
                }
                if (hasBottomRow) {
                    paint.textSize = lineSize
                    val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
                    paint.textSize = timeSize
                    val tmW = paint.measureText(widestSample(time))
                    widest = max(widest, rowWidth(floatArrayOf(lnW, tmW), step))
                }
            } else {
                if (hasTopRow) {
                    paint.textSize = timeSize
                    val tmW = paint.measureText(widestSample(time))
                    paint.textSize = lineSize
                    val wtW = weatherWidth(lineSize)
                    widest = max(widest, rowWidth(floatArrayOf(tmW, wtW), step))
                }
                if (hasBottomRow) {
                    paint.textSize = lineSize
                    val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
                    val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
                    val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday)
                    val dateLunarGap = max(lunarGap, wordStep)
                    var botW = dtW
                    if (lnW > 0f) {
                        if (botW > 0f) botW += dateLunarGap
                        botW += lnW
                    }
                    if (wkW > 0f) {
                        if (botW > 0f) botW += wordStep
                        botW += wkW
                    }
                    widest = max(widest, botW)
                }
            }
            if (budget > 0 && widest > budget) {
                val s2 = budget / widest
                timeSize *= s2
                lineSize *= s2
                widest = budget
            }
            blockWidth = widest
            blockHeight = (if (hasTopRow) lineHeight(timeSize * topFactor) else 0f) +
                (if (hasBottomRow) {
                    lineHeight(timeSize * bottomFactor) + (if (hasTopRow) rowGap else 0f)
                } else {
                    0f
                })
        }

        /**
         * One row's ink width: the measured parts plus one gap between each adjacent
         * pair, so an empty part costs nothing instead of leaving a stray gap.
         */
        private fun rowWidth(parts: FloatArray, step: Float): Float {
            var sum = 0f
            var drawn = 0
            for (w in parts) {
                if (w <= 0f) continue
                if (drawn > 0) sum += step
                sum += w
                drawn++
            }
            return sum
        }

        private fun widestSample(text: String): String {
            if (text.isEmpty()) return text
            val sb = StringBuilder(text.length)
            for (ch in text) sb.append(if (ch.isDigit()) '8' else ch)
            return sb.toString()
        }

        private fun lineHeight(size: Float): Float {
            if (size <= 0f) return 0f
            paint.textSize = size
            val metrics = paint.fontMetrics
            return (metrics.descent - metrics.ascent) * BAR_CLOCK_LINE_SPACING
        }

        private fun gap(size: Float): Float = size * BAR_CLOCK_GAP

        private fun wordGap(size: Float): Float = size * BAR_CLOCK_WORD_GAP

        override fun onDraw(canvas: Canvas) {
            if (time.isEmpty() || timeSize <= 0f) return
            paint.typeface = TrioRenderer.typefaceFor(weight)
            val currentInk = inkHost?.get()?.currentTextColor ?: ink
            val wkDrawColor = if (weekdayColor != 0) weekdayColor else currentInk
            val dtDrawColor = if (dateColor != 0) dateColor else currentInk
            val lnDrawColor = if (lunarColor != 0) lunarColor else currentInk
            val wtDrawColor = if (weatherColor != 0) weatherColor else currentInk

            fun drawWeatherAt(startX: Float, yBaseline: Float, sz: Float): Float {
                val txt = weatherLabel()
                if (txt.isEmpty()) return startX
                var x = startX
                paint.textSize = sz
                // Centre on a digit's box, not on the whole caption's: the "°" reaches
                // higher than the numerals, and centring the mixed string pushed the
                // numerals (and this icon) below the time's digits.
                paint.getTextBounds("0", 0, 1, bounds)
                val inkCentre = yBaseline + (bounds.top + bounds.bottom) * 0.5f
                when {
                    vectorWeather() -> {
                        val iconSz = sz * TrioGeometry.WEATHER_ICON_SCALE
                        WeatherIconDrawer.drawWeatherIcon(
                            canvas, WeatherIconDrawer.parseWeatherType(weatherCondition),
                            x, inkCentre - iconSz * 0.5f, iconSz, wtDrawColor,
                        )
                        x += iconSz + sz * 0.25f
                    }
                    symbolWeather() -> {
                        // A glyph sits on the text baseline, so it needs no centring of
                        // its own: the font's own metrics place it beside the numbers.
                        val glyph = WeatherIconDrawer.glyphFor(weatherCondition)
                        paint.color = wtDrawColor
                        canvas.drawText(glyph, x, yBaseline, paint)
                        x += paint.measureText(glyph) + sz * 0.25f
                    }
                }
                paint.color = wtDrawColor
                canvas.drawText(txt, x, yBaseline, paint)
                return x + paint.measureText(txt)
            }

            val isBalanced = clockStyle == Prefs.CLOCK_STYLE_BALANCED
            val topFactor = if (isBalanced) ClockTicker.BAR_DATE_SCALE else 1f
            val bottomFactor = if (isBalanced) 1f else ClockTicker.BAR_DATE_SCALE
            val hasTopRow = if (isBalanced) {
                date.isNotEmpty() || weekday.isNotEmpty() || weatherTemp.isNotEmpty()
            } else {
                true
            }
            val hasBottomRow = if (isBalanced) {
                true
            } else {
                date.isNotEmpty() || lunar.isNotEmpty() || weekday.isNotEmpty()
            }

            val topSize = timeSize * topFactor
            val bottomSize = timeSize * bottomFactor
            val topInk = if (hasTopRow) lineHeight(topSize) else 0f
            val bottomInk = if (hasBottomRow) lineHeight(bottomSize) else 0f
            val rowGapPx = if (hasTopRow && hasBottomRow) rowGap else 0f
            val totalH = topInk + bottomInk + rowGapPx
            val innerLeft = paddingLeft.toFloat()
            val innerW = (width - paddingLeft - paddingRight).toFloat()
            val step = gap(lineSize)
            val wordStep = wordGap(lineSize)

            // Each row is centred in the box on its own, so a row that lost a piece
            // stays centred instead of hanging off the left; the two offsets nudge a
            // row away from that centred position rather than setting its origin.
            var rowTop = (paddingTop + height - paddingBottom) / 2f - totalH / 2f
            paint.textAlign = Paint.Align.LEFT

            if (hasTopRow) {
                if (isBalanced) {
                    paint.textSize = topSize
                    val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
                    val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday)
                    val wtW = weatherWidth(topSize)
                    var x = innerLeft + (innerW - rowWidth(floatArrayOf(dtW, wkW, wtW), wordStep)) / 2f + topOffsetX
                    val baseline = rowTop - paint.fontMetrics.ascent
                    if (date.isNotEmpty()) {
                        paint.color = dtDrawColor
                        canvas.drawText(date, x, baseline, paint)
                        x += paint.measureText(date) + wordStep
                    }
                    if (weekday.isNotEmpty()) {
                        paint.color = wkDrawColor
                        canvas.drawText(weekday, x, baseline, paint)
                        x += paint.measureText(weekday) + wordStep
                    }
                    drawWeatherAt(x, baseline, topSize)
                } else {
                    paint.textSize = timeSize
                    val tmW = paint.measureText(time)
                    paint.textSize = lineSize
                    val wtW = weatherWidth(lineSize)
                    var x = innerLeft + (innerW - rowWidth(floatArrayOf(tmW, wtW), step)) / 2f + topOffsetX
                    val baseline = rowTop - paint.fontMetrics.ascent
                    // Centre the caption on the digits' ink, not on the font box: the
                    // box reserves descender space the digits never use, so centring on
                    // it left the caption sitting low under the time.
                    paint.textSize = timeSize
                    paint.getTextBounds("0", 0, 1, bounds)
                    val timeInkCentre = baseline + (bounds.top + bounds.bottom) * 0.5f
                    val label = weatherLabel()
                    var captionBaseline = baseline
                    if (label.isNotEmpty()) {
                        paint.textSize = lineSize
                        paint.getTextBounds("0", 0, 1, bounds)
                        captionBaseline = timeInkCentre - (bounds.top + bounds.bottom) * 0.5f
                    }
                    paint.textSize = timeSize
                    paint.color = currentInk
                    canvas.drawText(time, x, baseline, paint)
                    x += paint.measureText(time) + step
                    drawWeatherAt(x, captionBaseline, lineSize)
                }
                rowTop += topInk + rowGapPx
            }

            if (hasBottomRow) {
                if (isBalanced) {
                    // Measure each piece at the size it is drawn at: the lunar is a
                    // lineSize caption beside a timeSize clock, and measuring it at the
                    // clock's size over-booked width and pushed the whole row left.
                    paint.textSize = lineSize
                    val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
                    paint.textSize = timeSize
                    val tmW = paint.measureText(time)
                    var x = innerLeft + (innerW - rowWidth(floatArrayOf(lnW, tmW), step)) / 2f + bottomOffsetX
                    val timeBaseline = rowTop - paint.fontMetrics.ascent
                    if (lunar.isNotEmpty()) {
                        // Centre on each string's own ink: CJK glyphs reach below the
                        // baseline further than digits, so a digit-box centre left the
                        // lunar visibly lower than the time beside it.
                        paint.textSize = timeSize
                        paint.getTextBounds(time, 0, time.length, bounds)
                        val timeCentre = timeBaseline + (bounds.top + bounds.bottom) * 0.5f
                        paint.textSize = lineSize
                        paint.getTextBounds(lunar, 0, lunar.length, bounds)
                        val lunarBaseline = timeCentre - (bounds.top + bounds.bottom) * 0.5f
                        paint.color = lnDrawColor
                        canvas.drawText(lunar, x, lunarBaseline, paint)
                        x += paint.measureText(lunar) + step
                    }
                    paint.textSize = timeSize
                    paint.color = currentInk
                    canvas.drawText(time, x, timeBaseline, paint)
                } else {
                    paint.textSize = bottomSize
                    val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
                    val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
                    val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday)
                    val dateLunarGap = max(lunarGap, wordStep)
                    var rowW = dtW
                    if (lnW > 0f) {
                        if (rowW > 0f) rowW += dateLunarGap
                        rowW += lnW
                    }
                    if (wkW > 0f) {
                        if (rowW > 0f) rowW += wordStep
                        rowW += wkW
                    }
                    var x = innerLeft + (innerW - rowW) / 2f + bottomOffsetX
                    val baseline = rowTop - paint.fontMetrics.ascent
                    if (date.isNotEmpty()) {
                        paint.color = dtDrawColor
                        canvas.drawText(date, x, baseline, paint)
                        x += paint.measureText(date) + (if (lunar.isNotEmpty()) dateLunarGap else wordStep)
                    }
                    if (lunar.isNotEmpty()) {
                        paint.color = lnDrawColor
                        canvas.drawText(lunar, x, baseline, paint)
                        x += paint.measureText(lunar) + wordStep
                    }
                    if (weekday.isNotEmpty()) {
                        paint.color = wkDrawColor
                        canvas.drawText(weekday, x, baseline, paint)
                    }
                }
            }
        }
    }
}
