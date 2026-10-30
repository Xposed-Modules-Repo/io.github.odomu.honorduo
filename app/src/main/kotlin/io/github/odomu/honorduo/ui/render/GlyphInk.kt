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

package io.github.odomu.honorduo.ui.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.withSave
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconRenderer.animPhase
import io.github.odomu.honorduo.ui.render.IconRenderer.boltMoving
import io.github.odomu.honorduo.ui.render.IconRenderer.drawState
import io.github.odomu.honorduo.ui.render.IconRenderer.pulseWave
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import io.github.odomu.honorduo.ui.render.geometry.RectGeometry
import java.util.Locale

/**
 * The ink every arrangement draws with: the shared paints, the Wi-Fi arc paths, the
 * bolt path, the text fit and the type helper, plus the readout strings.
 *
 * One object because every one of those is stateful: the paints carry the stroke and
 * the colour, and rebuilding them per glyph would put an allocation on the frame
 * path. This is the only place they are created.
 */
internal object GlyphInk {

    internal val STROKE = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val FILL = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val TEXT = Paint(Paint.ANTI_ALIAS_FLAG)

    private val BOLT = buildBolt()

    private val WIFI_PATHS = IconGeometry.buildWifiPaths()

    internal val WIFI_DOT = WIFI_PATHS[0]

    internal val WIFI_MIDDLE = WIFI_PATHS[1]

    internal val WIFI_OUTER = WIFI_PATHS[2]

    internal val ARC = RectF()

    /**
     * The bolt path's own width.
     *
     * The icon is fitted by HEIGHT (CapsuleGeometry.capBoltScale), so its width follows from
     * the path rather than from a separate constant; anchoring it to a cell's edge needs
     * that number.
     */
    internal val BOLT_INK_W: Float by lazy {
        val bounds = RectF()
        BOLT.computeBounds(bounds, true)
        bounds.width()
    }


    /** The bolt's bounds, measured once - its path is built from constants. */
    private val BOLT_BOUNDS = RectF()


    /** Follows the bolt's outline for the spark style; the path never changes. */
    internal val PATH_MEASURE = PathMeasure()


    /** One spark's position, rewritten per spark; never kept. */
    internal val SPARK_POS = FloatArray(2)


    /** One loop of every bolt animation, in milliseconds. */
    internal const val BOLT_CYCLE_MS = 1400L


    /** The unlit bolt under the sweeping band, out of 255. */
    private const val BOLT_FLOW_DIM = 55


    /** The sweeping band's width, as a share of the bolt's own width. */
    private const val BOLT_FLOW_BAND = 0.5f


    /** The bolt under the sparks, out of 255. */
    private const val BOLT_SPARK_BASE = 80


    /** How many sparks travel the bolt's outline at once. */
    private const val BOLT_SPARKS = 3


    /**
     * A spark's radius, in design units.
     *
     * The bolt is 12.5 units wide, so this is a bead on a wire rather than a dot that
     * swallows the shape - at the glyph's own scale it inks about a pixel and a half.
     */
    private const val BOLT_SPARK_R = 3f


    /**
     * Scratch for the ink box [textInkHeight] measures; never kept.
     *
     * A [Rect], not a `RectF`: the platform's only `getTextBounds` overload that
     * takes a box is `getTextBounds(String, int, int, Rect)`, and it reports whole
     * pixels. The desktop shim used to declare a `RectF` variant, which made a
     * signature that does not exist on the device compile happily in the harness -
     * it built there and failed only against the real SDK.
     */
    internal val TEXT_INK = Rect()


    /**
     * The height of the ink [text] actually paints at [size], in the same units.
     *
     * Deliberately not `ascent + descent`. Those bounds describe the line box a
     * typesetter reserves - leading above the capitals, room below for descenders -
     * and a digits-only reading draws none of it. At 36pt the metrics are 45 units
     * against 26.3 units of ink, so using them to decide whether a line fits makes
     * every string look about 1.7x taller than it is, and the fit shrinks it
     * accordingly.
     *
     * [Paint.getTextBounds] reports the real drawn extent, which is what "does this
     * fit in that slot" should be asking.
     */
    internal fun textInkHeight(text: String, size: Float): Float {
        TEXT.textSize = size
        TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
        return (TEXT_INK.bottom - TEXT_INK.top).toFloat()
    }


    /**
     * A baseline that centres the drawn ink on the slot, given the one the font
     * metrics produce.
     *
     * The metrics version puts the middle of `ascent..descent` on the slot centre,
     * but those bounds include the leading a face reserves above its capitals and
     * below its descenders. A digits-only reading draws none of that, so its ink
     * sits high - enough that a line fitted to its third still reached into the arc
     * above it.
     *
     * [Paint.getTextBounds] reports the real ink box relative to the baseline, which
     * is the same space the font metrics use, so the correction is the difference
     * between the two centres. **Both are negative above the baseline**: mixing in
     * their absolute values, as an earlier version of this did, adds
     * `ascent + descent` instead of subtracting it and pushes the baseline most of a
     * line below the slot.
     */
    internal fun inkCentredBaseline(text: String?, size: Float, slotCentre: Float): Float {
        if (text == null || text.isEmpty() || size <= 0f) {
            return slotCentre
        }
        TEXT.textSize = size
        TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
        // Both boxes are measured in the same space: y grows downward and the baseline is 0, so `top`/`ascent` are negative.
        val inkCentre = (TEXT_INK.top + TEXT_INK.bottom) * 0.5f
        return slotCentre - inkCentre
    }


    /**
     * The weight currently installed on [TEXT]; -1 until first applied.
     *
     * Cached against the installed face rather than per text role: the percentage
     * and the network type can carry different weights and both may be drawn in the
     * same frame, so a role-keyed cache would make the second role skip a swap it
     * still needs. With the shared default (700) this is still zero swaps per frame.
     */
    private var sTextWeight = -1


    /**
     * Base face the weight is applied to. [Typeface.create] with a style bitmask
     * cannot take a numeric weight, so that has to go through the
     * `(Typeface, int, boolean)` overload with this as its family.
     */
    @Volatile
    private var TEXT_BASE: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)


    /**
     * Adopts the face the status bar draws its own text with, so the readings sit in
     * the same typeface as the clock and the network speed instead of a lookalike.
     *
     * Taken from the bar's clock when it attaches: its `TextView` is the one thing in
     * the row guaranteed to be styled by the ROM.
     */
    fun adoptSystemTypeface(face: Typeface?) {
        if (face != null) TEXT_BASE = face
    }

    init {
        STROKE.style = Paint.Style.STROKE
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.strokeJoin = Paint.Join.ROUND
        FILL.style = Paint.Style.FILL
        TEXT.style = Paint.Style.FILL
        TEXT.textAlign = Paint.Align.CENTER
        TEXT.letterSpacing = IconGeometry.VALUE_LETTER_SPACING
    }


    internal fun draw(canvas: Canvas, host: View, s: StatusState) {
        draw(canvas, host, s, ModuleConfig.get())
    }


    /**
     * Draws the glyph with the user's current settings. The text size is applied
     * here rather than in the static initialiser so a live settings change is
     * picked up by the very next frame.
     */
    internal fun draw(canvas: Canvas, host: View, s: StatusState, cfg: ModuleSettings) {
        drawState(canvas, host.width, host.height, s, cfg)
    }

    internal val GLYPH_ADVANCE = FloatArray(1)


    /**
     * The bolt's outline, in whatever local space the caller has scaled into.
     *
     * One place for the three arrangements and the four motion styles, so the ring, the
     * capsule and the rectangle cannot drift apart, and the still bolt stays the plain
     * filled path it always was. Every style loops for as long as the charger is
     * attached; a frame asks for the next one only while [StatusAppearance.animatesOutline]
     * says so, which is also the flag [boltMoving] carries.
     */
    internal fun drawBoltShape(c: Canvas, color: Int, a: StatusAppearance) {
        FILL.style = Paint.Style.FILL
        val style = a.boltAnim
        if (style == Prefs.BOLT_ANIM_OFF || !boltMoving) {
            FILL.color = color
            c.drawPath(BOLT, FILL)
            return
        }
        val phase = animPhase()
        when (style) {
            Prefs.BOLT_ANIM_PULSE -> {
                // The same curve the outline breathes on, so the bolt and its ring rise
                // and fall together.
                FILL.color = withAlpha(color, (255 * pulseWave()).toInt().coerceIn(0, 255))
                c.drawPath(BOLT, FILL)
            }
            Prefs.BOLT_ANIM_FLOW -> {
                val bounds = boltBounds()
                c.withSave {
                    // The dim bolt, then a lit band clipped to its outline.
                    clipPath(BOLT)
                    FILL.color = withAlpha(color, BOLT_FLOW_DIM)
                    drawPath(BOLT, FILL)
                    val band = bounds.width() * BOLT_FLOW_BAND
                    val left = bounds.left - band + (bounds.width() + band * 2f) * phase
                    clipRect(left, bounds.top, left + band, bounds.bottom)
                    FILL.color = color
                    drawPath(BOLT, FILL)
                }
            }
            Prefs.BOLT_ANIM_SPARK -> {
                // The bolt at reduced alpha with sparks riding along its outline: the
                // style this module shipped. The bolt is dimmed so the sparks have
                // somewhere to read against, and each spark takes the opposite end of the
                // scale from the ink it sits on.
                FILL.color = withAlpha(color, BOLT_SPARK_BASE)
                c.drawPath(BOLT, FILL)
                PATH_MEASURE.setPath(BOLT, false)
                val length = PATH_MEASURE.length
                if (length <= 0f) return
                FILL.color = contrastInk(color)
                for (i in 0 until BOLT_SPARKS) {
                    val at = ((phase + i / BOLT_SPARKS.toFloat()) % 1f) * length
                    if (PATH_MEASURE.getPosTan(at, SPARK_POS, null)) {
                        c.drawCircle(SPARK_POS[0], SPARK_POS[1], BOLT_SPARK_R, FILL)
                    }
                }
            }
            else -> {
                FILL.color = color
                c.drawPath(BOLT, FILL)
            }
        }
    }


    /** The bolt's bounds, measured once: the path is built from constants. */
    private fun boltBounds(): RectF {
        if (BOLT_BOUNDS.isEmpty) {
            BOLT.computeBounds(BOLT_BOUNDS, true)
        }
        return BOLT_BOUNDS
    }


    /** The percentage, in the middle of the arrangement and at its own scale. */
    /** The battery number as drawn: bare, or with the optional percent sign. */
    internal fun valueText(level: Int, a: StatusAppearance): String =
        if (a.showPercent) "$level%" else level.toString()


    /**
     * The wattage as a slot piece: "9.6W", or empty while the reading is unknown.
     *
     * One formatter for both watt pieces - the charging and the running one differ in
     * *when* they appear (see [StatusAppearance]'s ink rules), never in what they say.
     */
    internal fun powerText(): String? =
        if (StatusState.sChargePowerW.isNaN()) {
            null
        } else {
            String.format(Locale.US, "%.1fW", StatusState.sChargePowerW)
        }


    /** The display's refresh rate as a slot piece: "120", or empty before it is read. */
    internal fun fpsText(): String? =
        if (StatusState.sFps <= 0) "120" else StatusState.sFps.toString()


    /** The one reading a capsule cell holds, which is one piece rather than a pair. */
    internal fun capsulePerfText(part: Int, a: StatusAppearance): String? = when (part) {
        Prefs.PART_TEMP -> tempText()
        Prefs.PART_CURRENT -> currentText(a)
        Prefs.PART_FPS -> fpsText()
        else -> powerText()
    }


    /** The battery temperature as a slot piece: "32°C", or empty when it is unknown. */
    internal fun tempText(): String? =
        if (StatusState.sBatteryTempC == StatusState.TEMP_UNKNOWN) {
            null
        } else {
            StatusState.sBatteryTempC.toString() + "\u00B0C"
        }


    /**
     * The battery current as a slot piece: "420 mA" / "1.42 A", or empty when unknown.
     *
     * The magnitude is the point of the reading, so the sign is dropped unless the user
     * asked for it; the unit choice is theirs as well, because milliamps are what a
     * phone's currents are actually talked about in while amps stay readable past 1 A.
     */
    internal fun currentText(a: StatusAppearance): String? =
        StatusState.currentText(a.currentUnit, a.currentPositive)



    /**
     * The colour of the lit part of the ring and the value text.
     *
     * Priority: critical battery, then power-save/low battery, then charging, then
     * the framework's own foreground. [isDarkBackground] tells which variant of a
     * role colour to use: a light icon colour means the status bar is dark, so the
     * light-background variant applies.
     */
    fun roleColor(
        a: StatusAppearance?, level: Int, charging: Boolean,
        powerSave: Boolean, low: Boolean, fg: Int,
    ): Int {
        if (a == null || !a.roleColors) {
            return fg
        }
        val dark = isDarkBackground(fg)
        if (level >= 0 && level < RectGeometry.CRITICAL_LEVEL) {
            return if (dark) a.criticalOnDark else a.criticalOnLight
        }
        if (powerSave || low || (level >= 0 && level <= a.lowThreshold)) {
            return if (dark) a.lowOnDark else a.lowOnLight
        }
        if (charging) {
            return if (dark) a.chargingOnDark else a.chargingOnLight
        }
        return fg
    }



    /**
     * The ink that reads against [base]: white on a dark base, black on a light one.
     *
     * Sparks sit on top of ink that is already there, so they cannot be that same
     * colour - which is exactly what a spark in the arc's own colour was.
     */
    private fun contrastInk(base: Int): Int =
        if (isDarkBackground(base)) Color.BLACK else Color.WHITE


    /**
     * Installs [weight] on [TEXT], skipping the work when that weight is already the
     * installed one. Every draw runs inside onDraw, so the cached comparison keeps a
     * per-frame [Typeface] allocation off the hot path.
     */
    internal fun applyWeight(weight: Int) {
        if (weight == sTextWeight && sTextGeneration == sFontGeneration) {
            return
        }
        TEXT.typeface = typefaceFor(weight)
        TEXT.isFakeBoldText = fakeBoldFor(weight)
        sTextWeight = weight
        sTextGeneration = sFontGeneration
    }


    /**
     * Draws the network type at [x] on [baseline], shrinking the trailing "A" when
     * the user asked for it.
     *
     * [Canvas.drawText] does *not* apply spans - only a `TextPaint` through
     * `StaticLayout` does - so the two sizes are drawn as two runs. The [Paint] is
     * left aligned for the duration: [TEXT] is centred, and centring each run
     * separately would stack them on the same spot instead of setting them side by
     * side.
     *
     * @param x the centre of the whole label
     */
    internal fun drawType(
        c: Canvas, type: String?, x: Float, baseline: Float,
        size: Float, fg: Int, a: StatusAppearance,
    ) {
        if (type == null || !IconGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
            TEXT.textAlign = Paint.Align.CENTER
            TEXT.textSize = size
            TEXT.color = fg
            c.drawText(type ?: "", x, baseline, TEXT)
            return
        }
        val base = IconGeometry.typeBase(type)
        val suffix = IconGeometry.TYPE_SUFFIX.toString()
        val suffixSize = size * a.typeSuffixScale / 100f
        // Measure both runs, then place them as one centred block: total width is the
        // layout, and the label's own centre is what stays put.
        TEXT.textAlign = Paint.Align.LEFT
        TEXT.color = fg
        TEXT.textSize = size
        val baseWidth = TEXT.measureText(base)
        TEXT.textSize = suffixSize
        val suffixWidth = TEXT.measureText(suffix)
        var left = x - (baseWidth + suffixWidth) * 0.5f
        TEXT.textSize = size
        c.drawText(base, left, baseline, TEXT)
        left += baseWidth
        TEXT.textSize = suffixSize
        c.drawText(suffix, left, baseline, TEXT)
        // Restore the shared paint's centred default: every other text draw in this
        // renderer relies on it, and this method is not the last per frame.
        TEXT.textAlign = Paint.Align.CENTER
    }


    /**
     * Largest font size at which [text] fits in [clearWidth] design units, capped by
     * [requested]. "100" is far wider than "9", so a long value shrinks instead of
     * overrunning the ring.
     */
    internal fun fitSize(text: String?, requested: Float, clearWidth: Float): Float =
        fitSize(text, requested, clearWidth, 100)


    /**
     * The same fit, measuring the label the way [drawType] will lay it out: a shrunk
     * trailing "A" carries only [scalePercent] of the main size, so it takes less
     * room and the main run is left correspondingly larger.
     */
    internal fun fitSize(
        text: String?, requested: Float, clearWidth: Float,
        scalePercent: Int,
    ): Float {
        if (clearWidth <= 0f) {
            return requested
        }
        TEXT.textSize = requested
        val width = measureType(text, requested, scalePercent)
        if (width <= clearWidth) {
            return requested
        }
        return requested * (clearWidth / width)
    }


    /**
     * Width of [text] at [size], with the trailing suffix scaled.
     *
     * Leaves [TEXT] at [size], which is what every caller wants next.
     */
    internal fun measureType(text: String?, size: Float, scalePercent: Int): Float {
        if (text == null || !IconGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.textSize = size
            return TEXT.measureText(text ?: "")
        }
        TEXT.textSize = size
        val base = TEXT.measureText(IconGeometry.typeBase(text))
        TEXT.textSize = size * scalePercent / 100f
        val suffix = TEXT.measureText(IconGeometry.TYPE_SUFFIX.toString())
        TEXT.textSize = size
        return base + suffix
    }


    internal fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v


    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)


    /**
     * The shared text face at a numeric weight. The settings app and the hooked side
     * have to agree on it, and so do the in-canvas glyph and the label view drawn
     * beside it.
     */
    @Volatile
    private var sFontPath = ""

    @Volatile
    private var sFontFace: Typeface? = null

    @Volatile
    private var sFontGeneration = 0

    private const val FAKE_BOLD_FROM = 600

    private var sTextGeneration = -1


    @JvmStatic
    fun setFont(path: String?) {
        val want = path ?: ""
        if (want == sFontPath) return
        sFontPath = want
        sFontFace = null
        sFontGeneration++
        if (want.isEmpty()) return
        try {
            sFontFace = Typeface.createFromFile(want)
        } catch (_: Throwable) {
            sFontFace = null
        }
    }


    @JvmStatic
    fun fakeBoldFor(weight: Int): Boolean = sFontFace != null && weight >= FAKE_BOLD_FROM


    @JvmStatic
    fun typefaceFor(weight: Int): Typeface {
        val base = sFontFace ?: TEXT_BASE
        return Typeface.create(base, weight, false)
    }


    /** The charging bolt outline, in 120x120 design units. */
    private fun buildBolt(): Path {
        val p = Path()
        p.moveTo(62.1f, 2.2f)
        p.quadTo(62.8f, 2.5f, 62.6f, 3.3f)
        p.lineTo(61.2f, 7.8f)
        p.lineTo(65.9f, 7.8f)
        p.quadTo(66.9f, 7.8f, 67.3f, 8.6f)
        p.quadTo(67.6f, 9.3f, 67f, 10f)
        p.lineTo(57f, 21.3f)
        p.quadTo(56.4f, 22f, 55.6f, 21.6f)
        p.quadTo(55f, 21.3f, 55.3f, 20.5f)
        p.lineTo(57.4f, 14.1f)
        p.lineTo(52.9f, 14.1f)
        p.quadTo(52f, 14.1f, 51.6f, 13.3f)
        p.quadTo(51.3f, 12.6f, 51.9f, 12f)
        p.lineTo(61.1f, 2.7f)
        p.quadTo(61.6f, 2.1f, 62.1f, 2.2f)
        p.close()
        return p
    }


    /** sRGB luminance test: a light icon colour implies a dark status bar. */
    fun isDarkBackground(color: Int): Boolean {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        return lum > 0.5f
    }
}
