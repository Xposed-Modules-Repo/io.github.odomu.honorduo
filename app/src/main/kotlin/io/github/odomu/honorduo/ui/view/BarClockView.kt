package io.github.odomu.honorduo.ui.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import io.github.odomu.honorduo.hook.statusbar.BarClockManager
import io.github.odomu.honorduo.hook.statusbar.ClockTicker
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.render.GlyphInk
import io.github.odomu.honorduo.ui.render.WeatherIconDrawer
import io.github.odomu.honorduo.ui.render.geometry.RectGeometry
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.max

internal class BarClockView(context: Context) : View(context) {

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

        /**
         * Per-character colours of the time, by index in the drawn string; `0` = unset.
         *
         * Assigned after [apply] rather than compared inside it: `apply` skips a call that
         * changes nothing else, and an array cannot be compared with `==` in that early-out.
         */
        var digitColors: IntArray = IntArray(0)

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

        /**
         * The time, one character at a time, so each glyph can take its own colour.
         *
         * One `drawText` cannot: a paint has a single colour. Advances come from the same
         * paint, so the run measures exactly like the plain draw it replaces.
         */
        private fun drawTimeAt(canvas: Canvas, x: Float, baseline: Float, inkFallback: Int) {
            if (time.isEmpty()) return
            var cursor = x
            for (i in time.indices) {
                val picked = if (i < digitColors.size) digitColors[i] else 0
                paint.color = if (picked != 0) picked else inkFallback
                val advance = paint.measureText(time, i, i + 1)
                canvas.drawText(time, i, i + 1, cursor, baseline, paint)
                cursor += advance
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
                this.baseSize == baseSize && (maxHeight <= 0 || this.maxHeight == maxHeight) &&
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
            // A zero height is "not resolved yet", not a zero-height bar: keeping the last
            // real one is what stops the clamp from flapping on the frame a rotation or a
            // shade pull leaves the bar unmeasured.
            if (maxHeight > 0) this.maxHeight = maxHeight
            this.topOffsetX = topOffsetX
            this.bottomOffsetX = bottomOffsetX
            this.lunarGap = lunarGap
            this.rowGap = rowGap
            val targetW = if (width > 0) width else (resources.displayMetrics.widthPixels * BarClockManager.BAR_CLOCK_WIDTH_SHARE).toInt()
            val oldW = ceil(blockWidth).toInt()
            val oldH = ceil(blockHeight).toInt()
            solve(targetW)
            val newW = ceil(blockWidth).toInt()
            val newH = ceil(blockHeight).toInt()
            if (oldW != newW || oldH != newH) {
                requestLayout()
                (parent as? View)?.requestLayout()
            }
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
                vectorWeather() -> sz * RectGeometry.WEATHER_ICON_SCALE + sz * 0.25f
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
            paint.typeface = GlyphInk.typefaceFor(weight)
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
            // Only a real height is a bound. `maxHeight` reads 0 whenever the bar height
            // cannot be resolved - before the first layout, and on the layout pass that
            // runs while the shade is opening - and clamping to it set the scale to zero,
            // which made the clock draw nothing at all. A zero here means "unknown", so
            // the block keeps its natural size and the row clips it for the one frame
            // that is wrong rather than for as long as the value stays zero.
            val room = maxHeight.toFloat()
            if (room > 0f && needed > room) {
                scale = room / needed
            }
            val budget = if (availW > 0) {
                availW.toFloat()
            } else {
                resources.displayMetrics.widthPixels * BarClockManager.BAR_CLOCK_WIDTH_SHARE
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
            return (metrics.descent - metrics.ascent) * BarClockManager.BAR_CLOCK_LINE_SPACING
        }

        private fun gap(size: Float): Float = size * BarClockManager.BAR_CLOCK_GAP

        private fun wordGap(size: Float): Float = size * BarClockManager.BAR_CLOCK_WORD_GAP

        override fun onDraw(canvas: Canvas) {
            if (time.isEmpty() || timeSize <= 0f) return
            paint.typeface = GlyphInk.typefaceFor(weight)
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
                        val iconSz = sz * RectGeometry.WEATHER_ICON_SCALE
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
                    drawTimeAt(canvas, x, baseline, currentInk)
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
                    drawTimeAt(canvas, x, timeBaseline, currentInk)
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
