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
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconRenderer.FLOW_LIFT
import io.github.odomu.honorduo.ui.render.IconRenderer.RING_POINT
import io.github.odomu.honorduo.ui.render.IconRenderer.TEXT
import io.github.odomu.honorduo.ui.render.IconRenderer.animPhase
import io.github.odomu.honorduo.ui.render.IconRenderer.batteryRing
import io.github.odomu.honorduo.ui.render.IconRenderer.boltMoving
import io.github.odomu.honorduo.ui.render.IconRenderer.drawType
import io.github.odomu.honorduo.ui.render.IconRenderer.fitSize
import io.github.odomu.honorduo.ui.render.IconRenderer.quickCharge
import io.github.odomu.honorduo.ui.render.IconRenderer.textInkHeight
import io.github.odomu.honorduo.ui.view.GlyphView
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the status glyph (battery ring + Wi-Fi arcs + signal dots + level indicator)
 * into a host view's canvas, in the 120x120 design space of [IconGeometry].
 */
object IconRenderer {

    private val STROKE = Paint(Paint.ANTI_ALIAS_FLAG)
    private val FILL = Paint(Paint.ANTI_ALIAS_FLAG)
    private val TEXT = Paint(Paint.ANTI_ALIAS_FLAG)
    private val BOLT = buildBolt()
    private val WIFI_PATHS = IconGeometry.buildWifiPaths()
    private val WIFI_DOT = WIFI_PATHS[0]
    private val WIFI_MIDDLE = WIFI_PATHS[1]
    private val WIFI_OUTER = WIFI_PATHS[2]
    private val ARC = RectF()
    /**
     * The bolt path's own width.
     *
     * The icon is fitted by HEIGHT (IconGeometry.capBoltScale), so its width follows from
     * the path rather than from a separate constant; anchoring it to a cell's edge needs
     * that number.
     */
    private val BOLT_INK_W: Float by lazy {
        val bounds = RectF()
        BOLT.computeBounds(bounds, true)
        bounds.width()
    }

    /** The bolt's bounds, measured once - its path is built from constants. */
    private val BOLT_BOUNDS = RectF()

    /** Follows the bolt's outline for the spark style; the path never changes. */
    private val PATH_MEASURE = PathMeasure()

    /** One spark's position, rewritten per spark; never kept. */
    private val SPARK_POS = FloatArray(2)

    /** One loop of every bolt animation, in milliseconds. */
    private const val BOLT_CYCLE_MS = 1400L

    /**
     * The same loop while a fast charger is attached.
     *
     * Half the normal period. The light travelling the ring is the one thing on the
     * glyph that can say "fast" rather than merely "charging", and the only way it can
     * say it without a second colour or a second shape is with speed.
     */
    private const val QUICK_CYCLE_MS = BOLT_CYCLE_MS / 2

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
     * The flowing light on the ring: how many trail segments, and how long each is.
     *
     * Many short segments rather than a few long ones, because a segment carries one
     * alpha: at four segments the 60-step drop between them reads as four dashes, not
     * as one tail. They abut exactly - no overlap - because an overlap would composite
     * two trail alphas into a brighter band, which is the same defect in the other
     * direction.
     */
    private const val RING_FLOW_SEGMENTS = 24
    private const val RING_FLOW_SPAN = 0.013f

    /** How far the running light is lifted towards white from the charged colour. */
    private const val FLOW_LIFT = 0.8f

    /** Sparks travelling the ring, and their radius against the ring's stroke. */
    private const val RING_SPARK_COUNT = 3
    private const val RING_SPARK_SCALE = 0.55f

    /** Capsule sparks, and their radius against the outline's stroke. */
    private const val CAPSULE_SPARK_COUNT = 3
    private const val CAPSULE_SPARK_SCALE = 0.6f
    private const val DUAL_SIGNAL_HALF_SPAN_RAD = 0.42760567f

    /** One point on the battery arc, rewritten per frame; never kept. */
    private val RING_POINT = FloatArray(2)

    /**
     * The ring's drawable segments, as `(startDeg, sweepDeg)` pairs; rewritten per call.
     *
     * Four segments is the most any opening combination can produce (two flanks, the top
     * mouth and the bottom opening), and [buildRingSegments] refuses to write past the end.
     */
    private val SEGMENTS = FloatArray(8)

    /**
     * The rings openings for one call, as `(startDeg, endDeg)` pairs; rewritten per call.
     *
     * At most four (two mouths, two flank cuts); [addRingGap] refuses to write past the end.
     */
    private val GAPS = FloatArray(8)

    /** One travelling segment of the capsule's outline; rewritten per segment. */
    private val FLOW_PATH = Path()

    /**
     * The Bluetooth level the cached digit text was built for, and that text.
     *
     * The readout is drawn every frame and [Canvas.drawText] needs a String, so the level's
     * text used to be rebuilt 60 times a second for a value that only changes on a Bluetooth
     * broadcast.
     */
    private var btTextLevel = Int.MIN_VALUE
    private var btText = ""

    private fun btLevelText(level: Int): String {
        if (level != btTextLevel) {
            btTextLevel = level
            btText = level.toString()
        }
        return btText
    }

    /**
     * Whether the frame being drawn sits on a fast charger.
     *
     * Every moving piece on the glyph reads the same [animPhase], so the speed has to
     * belong to the frame rather than to one drawing call: an outline running at one
     * rate and the bolt inside it at another reads as broken. Written once per frame
     * at the top of [drawInto], before anything animates.
     */
    private var quickCharge = false

    /**
     * The loop's speed multiplier for this frame.
     *
     * Like [quickCharge], it belongs to the frame rather than to one drawing call: the
     * outline and the bolt read the same [animPhase], so they have to be told the same
     * speed or they drift apart mid-animation.
     */
    private var animSpeed = 1f

    /**
     * Whether this frame's bolt animation is running.
     *
     * Not the same question as "is a style selected": a full battery stops the loop (the
     * view stops asking for frames), so the *drawing* has to stop moving too. Without
     * this flag the bolt kept taking its phase from the clock, and every unrelated
     * repaint - a battery broadcast, the minute tick - drew a different phase. That is
     * exactly the judder a full battery showed in every style.
     */
    private var boltMoving = false

    /** Scratch for the rectangular battery bar; rewritten every frame, never kept. */
    private val RECT_BAR = Path()

    /**
     * Scratch for the capsule's outline; rebuilt each pass because the gap the
     * dot rows cut changes with the stroke and with the mobile switch.
     */
    private val CAPSULE = Path()
    private val RECT_RECT = RectF()

    /**
     * Scratch for the ink box [textInkHeight] measures; never kept.
     *
     * A [Rect], not a `RectF`: the platform's only `getTextBounds` overload that
     * takes a box is `getTextBounds(String, int, int, Rect)`, and it reports whole
     * pixels. The desktop shim used to declare a `RectF` variant, which made a
     * signature that does not exist on the device compile happily in the harness -
     * it built there and failed only against the real SDK.
     */
    private val TEXT_INK = Rect()

    /**
     * Corner radii of the lit bar segment: rounded on the left so it follows the
     * track's capsule, square on the right so the level ends on a clean vertical
     * edge. Order is the [Path.addRoundRect] order - TL, TR, BR, BL.
     */
    private val RECT_RADII = FloatArray(8)
    private val DRAW_TRANSFORM = FloatArray(3)

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
    private fun textInkHeight(text: String, size: Float): Float {
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
    private fun inkCentredBaseline(text: String?, size: Float, slotCentre: Float): Float {
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

    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure - [GlyphView] hands the icon view's own bounds through, and
     * the slot it paints into can be asked for nothing else.
     */
    internal fun drawState(canvas: Canvas, w: Int, h: Int, s: StatusState?, cfg: ModuleSettings?) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return
        }
        drawInto(
            canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
            if (s.wifiPresent) s.wifiLevel else -1, s.mobileLevel, s.slotLevels, s.mobileType,
            s.foreground(), cfg, true,
        )
    }

    /**
     * Renders the glyph into any canvas of the given size. Kept free of [View] so
     * the settings app can preview the exact same drawing code instead of a
     * hand-maintained copy of it.
     */
    fun drawInto(
        canvas: Canvas, w: Int, h: Int, level: Int, charging: Boolean,
        powerSave: Boolean, low: Boolean, wifiLevel: Int, mobileLevel: Int,
        fg: Int, cfg: ModuleSettings,
    ) {
        drawInto(
            canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
            null, null, fg, cfg, true,
        )
    }

    /**
     * @param quickCharging `true` while the host reports a fast charger; only the
     *   bolt's colour changes, never its shape or size.
     * @param slotLevels the per-SIM signal levels by slot - SIM 1 first - or `null`
     *   when the caller has none, which keeps the single row.
     * @param clearWhenDone `true` when this glyph owns the whole canvas (the hooked
     *   view case); `false` when drawing onto a canvas that already holds the
     *   settings app's background.
     */
    fun drawInto(
        canvas: Canvas, w: Int, h: Int, level: Int, charging: Boolean,
        quickCharging: Boolean, powerSave: Boolean, low: Boolean,
        wifiLevel: Int, mobileLevel: Int, slotLevels: IntArray?, mobileType: String?,
        fg: Int, cfg: ModuleSettings?, clearWhenDone: Boolean,
    ) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return
        }
        // The frame's speed and motion are settled here, before any of the moving pieces
        // ask for the phase, so the outline and the bolt cannot end up on different
        // clocks or at different speeds.
        quickCharge = quickCharging
        // Resolve what is on screen once, here, from the single rule source.
        val appearance = if (cfg === ModuleConfig.get()) ModuleConfig.appearance() else StatusAppearance.of(cfg)
        // the appearance snapshot: whether Wi-Fi is connected this instant is runtime
        // state, and folding it in there would make the preview (a fixed scene) and the
        // bar (live) draw one stored setting two ways.
        //
        // Suppressing the STRING rather than a per-arrangement flag keeps this in one
        // place: every arrangement already treats "no type" as "draw none of it", so the
        // ring, the rectangle and the capsule all obey without knowing this setting.
        val shownType = if (appearance.typeHideOnWifi && wifiLevel >= 1) null else mobileType
        animSpeed = appearance.boltSpeedFactor()
        boltMoving = appearance.animatesOutline(charging, level)
        // The transform is asked for rather than worked out here, because the settings screen's tappable zones have to land on the very pixels this drawing lands on.
        if (!designTransform(w, h, appearance, DRAW_TRANSFORM)) {
            return
        }
        val scale = DRAW_TRANSFORM[0]
        setFont(appearance.fontPath)
        // The two arrangements have different ink extents, so each is fitted to
        // the host view with its own box.
        val rect = appearance.rect
        val capsule = appearance.capsule

        if (clearWhenDone) {
            // Clear unconditionally: our glyph is the sole content of this view, so
            // the framework's battery drawable must not show through underneath.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        }

        val role = roleColor(appearance, level, charging, powerSave, low, fg)

        // A slot with no reading counts as absent, so a single-SIM device - or a
        // poll that has not answered yet - is not mistaken for a second SIM.
        val slots: IntArray? =
            if (slotLevels != null && slotLevels.size >= StatusState.SIM_SLOTS) slotLevels else null
        // Only the two real slots count; a longer array is not a third card.
        val sims = if (slots == null) {
            0
        } else {
            (if (slots[0] >= 0) 1 else 0) + (if (slots[1] >= 0) 1 else 0)
        }

        canvas.withTranslation(DRAW_TRANSFORM[1], DRAW_TRANSFORM[2]) {
            canvas.scale(scale, scale)

            when {
                capsule -> drawCapsuleLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel, shownType,
                    slots, sims, role, fg, appearance,
                )

                rect -> drawRectLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance,
                )

                else -> drawRingLayout(
                    canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance,
                )
            }

        }
    }

    /**
     * The ring arrangement.
     *
     * The canvas is already scaled into the design space and positioned on the ink
     * box, so everything here is plain design-unit drawing.
     *
     * Which piece goes where is not decided here - [StatusAppearance.Ring] has already
     * resolved the three slots. This method only draws.
     */
    private fun drawRingLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int,
        slotLevels: IntArray?, sims: Int, mobileType: String?, role: Int,
        fg: Int, a: StatusAppearance,
    ) {
        val r = a.ring(level, charging, wifiLevel, mobileType, sims)
        drawFreeRing(
            canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
            slotLevels, mobileType, role, fg, a, r,
        )
    }

    /** Explicit slots share the editor's thirds and fit ink on BOTH axes. */
    private fun drawFreeRing(
        c: Canvas, level: Int, charging: Boolean, quickCharging: Boolean, wifiLevel: Int,
        mobileLevel: Int, levels: IntArray?, type: String?, role: Int, fg: Int,
        a: StatusAppearance, r: StatusAppearance.Ring,
    ) {
        val hasLeftDots = r.hasLeftDots
        val hasRightDots = r.hasRightDots
        val hasLeftWifi = r.hasLeftWifi
        val hasRightWifi = r.hasRightWifi
        drawBattery(
            c, level, role, fg, a, r.gapStart(), r.gapEnd(), r.sweep(), r.arcStart(), charging,
            openLeft = hasLeftDots, openRight = hasRightDots,
            topHalfDeg = r.topHalfDeg, bottomHalfDeg = r.bottomHalfDeg,
        )
        for (z in 0 until 3) {
            val chain = a.ringZones.getOrNull(z)
            val hasDotInZone = chain != null && chain.any { p ->
                p == Prefs.PART_MOBILE || p == Prefs.PART_SIM_1 || p == Prefs.PART_SIM_2
            }
            val part = r.parts[z]
            if (part == Prefs.PART_NONE && !hasDotInZone) continue

            val cx = IconGeometry.B_CX
            val cy = when (z) {
                0 -> IconGeometry.notchCy(0, a)
                1 -> IconGeometry.ZONE_CY[1]
                else -> IconGeometry.notchCy(2, a)
            }
            // The same box the mouth is cut for (see IconGeometry.notchHalfDeg), so a piece
            // can never be fitted wider than the opening it was measured against.
            val width = IconGeometry.notchSlotW(a.ringStroke)
            val height = when (z) {
                0 -> IconGeometry.notchSlotH(0, part)
                1 -> IconGeometry.DOT_BOX_CENTRE
                else -> IconGeometry.notchSlotH(2, part)
            }
            c.withTranslation(cx, cy) {
                if (part == Prefs.PART_WIFI) {
                    // Capped by the slot, not multiplied past it: a size setting above 100%
                    // used to draw the fan out of the ink box, where the host clips it.
                    val scale = IconGeometry.notchWifiScale(z, a)
                    c.scale(scale, scale)
                    c.translate(-IconGeometry.W_CX, -IconGeometry.W_INK_CY)
                    drawWifiArcs(c, wifiLevel, fg, a)
                } else if (part == Prefs.PART_BOLT) {
                    val scale = IconGeometry.notchBoltScale(z, a)
                    c.scale(scale, scale)
                    c.translate(-IconGeometry.BOLT_CX_REF, -IconGeometry.BOLT_INK_CY)
                    drawBoltShape(c, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
                } else if (hasDotInZone || part == Prefs.PART_MOBILE || part == Prefs.PART_SIM_1 || part == Prefs.PART_SIM_2) {
                    val btLevel = StatusState.sBluetoothBatteryLevel
                    val btActive = z == 2 && a.btBatteryMode > 0 && btLevel in 0..100
                    if (btActive) {
                        if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                            TEXT.textSize = 22f
                            TEXT.typeface = Typeface.DEFAULT_BOLD
                            TEXT.textAlign = Paint.Align.CENTER
                            TEXT.color = fg
                            c.drawText(btLevelText(btLevel), 0f, 6f, TEXT)
                        } else {
                            val rho = IconGeometry.B_R
                            ARC.set(-rho, -rho, rho, rho)
                            STROKE.style = Paint.Style.STROKE
                            STROKE.strokeWidth = 6f
                            STROKE.strokeCap = Paint.Cap.ROUND
                            STROKE.color = withAlpha(fg, a.trackAlpha)
                            val startDeg = 65f
                            val sweepDeg = 50f
                            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
                            if (btLevel > 0) {
                                STROKE.color = fg
                                val progressDeg = sweepDeg * (btLevel / 100f)
                                c.drawArc(ARC, startDeg + sweepDeg - progressDeg, progressDeg, false, STROKE)
                            }
                        }
                    } else {
                    val columns = a.signalColumns.coerceIn(4, 5)
                    val baseDotR = IconGeometry.arcRowDotR(z, a.ringStroke, width, a.dotScaleFactor())

                    val dataLevel = StatusState.dataSimSignalLevel()
                    val sim1Level = if (levels != null && levels.isNotEmpty() && levels[0] >= 0) levels[0] else dataLevel
                    val sim2Level = if (levels != null && levels.size > 1 && levels[1] >= 0) levels[1] else dataLevel

                    val isStackedDual = !a.dataSimOnly && chain != null && (
                        (chain.contains(Prefs.PART_SIM_1) && chain.contains(Prefs.PART_SIM_2)) ||
                        (chain.contains(Prefs.PART_MOBILE) && a.dualSim && (levels?.size ?: 0) >= 2)
                    )

                    val ringStroke = a.ringStroke * IconGeometry.RING_STROKE_RATIO
                    val rhoMid = IconGeometry.B_R - ringStroke * 0.5f
                    val baseCapDotR = if (columns == 5) 4.2f else 4.9f
                    val dotR = baseCapDotR * a.dotScaleFactor()
                    val sign = if (z == 2) 1f else -1f
                    val inactive = withAlpha(fg, a.trackAlpha)
                    val halfDiv = (columns - 1) * 0.5f

                    if (isStackedDual) {
                        val firstPart = chain.firstOrNull { p -> p == Prefs.PART_SIM_1 || p == Prefs.PART_SIM_2 || p == Prefs.PART_MOBILE }
                        val isSim2First = firstPart == Prefs.PART_SIM_2
                        val l1 = if (isSim2First) sim2Level else sim1Level
                        val l2 = if (isSim2First) sim1Level else sim2Level
                        val active1 = if (l1 < 0) 0 else levelMapped(l1, columns)
                        val active2 = if (l2 < 0) 0 else levelMapped(l2, columns)

                        val inward = 3.8f
                        val r1 = rhoMid - inward
                        val dr = dotR * 2.65f
                        val r2 = r1 + dr
                        val halfSpanAngle = DUAL_SIGNAL_HALF_SPAN_RAD

                        // Row 1: Order 1 (shifted inward for comfortable spacing)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfSpanAngle
                            val x = r1 * kotlin.math.sin(angle)
                            val y = (IconGeometry.B_CY + sign * r1 * kotlin.math.cos(angle)) - IconGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active1) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                        // Row 2: Order 2 (parallel outside row 1)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfSpanAngle
                            val x = r2 * kotlin.math.sin(angle)
                            val y = (IconGeometry.B_CY + sign * r2 * kotlin.math.cos(angle)) - IconGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active2) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                    } else {
                        val rowLevel = when (part) {
                            Prefs.PART_SIM_1 -> sim1Level
                            Prefs.PART_SIM_2 -> sim2Level
                            else -> dataLevel
                        }
                        val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                        val rho = IconGeometry.B_R
                        val openingRad = Math.toRadians(IconGeometry.GAP_WIDTH_DEG.toDouble()).toFloat()
                        val inset = (dotR + ringStroke * 0.5f + 1f) / rho
                        val halfAngle = maxOf(0f, openingRad * 0.5f - inset)
                        val beadHalfAngle = Math.toRadians((IconGeometry.BEAD_SPAN_DEG * 0.5f).toDouble()).toFloat()
                        val halfCentreAngle = minOf(halfAngle, beadHalfAngle)
                        for (dot in 0 until columns) {
                            val t = (dot - halfDiv) / halfDiv
                            val angle = t * halfCentreAngle
                            val x = rho * kotlin.math.sin(angle)
                            val y = (IconGeometry.B_CY + sign * rho * kotlin.math.cos(angle)) - IconGeometry.ZONE_CY[z]
                            FILL.color = if (dot < active) fg else inactive
                            c.drawCircle(x, y, dotR, FILL)
                        }
                    }
                    }
                } else {
                    drawRingText(c, part, type, level, fg, a, z, 0f)
                }
            }
        }
        if (hasLeftDots || hasRightDots) {
            val columns = a.signalColumns.coerceIn(4, 5)
            val dataLevel = StatusState.dataSimSignalLevel()
            val sim1Level = if (levels != null && levels.isNotEmpty() && levels[0] >= 0) levels[0] else dataLevel
            val sim2Level = if (levels != null && levels.size > 1 && levels[1] >= 0) levels[1] else dataLevel
            val dotR = IconGeometry.flankDotR(columns, a.dotScaleFactor())
            val inactive = withAlpha(fg, a.trackAlpha)
            val rho = IconGeometry.B_R
            val halfDiv = (columns - 1) * 0.5f
            // The row's own span, not the notch's: the arc is cut wider than the row, so the
            // end beads keep their clearance instead of merging into the arc's round caps.
            val rowHalfAngle = Math.toRadians(IconGeometry.flankRowHalfDeg(columns, dotR).toDouble()).toFloat()
            if (hasLeftDots) {
                val part = r.parts[Prefs.ZONE_LEFT]
                val rowLevel = when (part) {
                    Prefs.PART_SIM_1 -> sim1Level
                    Prefs.PART_SIM_2 -> sim2Level
                    else -> dataLevel
                }
                val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                for (dot in 0 until columns) {
                    val t = (dot - halfDiv) / halfDiv
                    val angle = Math.toRadians(180.0).toFloat() + t * rowHalfAngle
                    val x = IconGeometry.B_CX + rho * kotlin.math.cos(angle)
                    val y = IconGeometry.B_CY + rho * kotlin.math.sin(angle)
                    FILL.color = if (dot < active) fg else inactive
                    c.drawCircle(x, y, dotR, FILL)
                }
            }
            if (hasRightDots) {
                val part = r.parts[Prefs.ZONE_RIGHT]
                val rowLevel = when (part) {
                    Prefs.PART_SIM_2 -> sim2Level
                    Prefs.PART_SIM_1 -> sim1Level
                    else -> dataLevel
                }
                val active = if (rowLevel < 0) 0 else levelMapped(rowLevel, columns)
                for (dot in 0 until columns) {
                    val t = (dot - halfDiv) / halfDiv
                    val angle = t * rowHalfAngle
                    val x = IconGeometry.B_CX + rho * kotlin.math.cos(angle)
                    val y = IconGeometry.B_CY + rho * kotlin.math.sin(angle)
                    FILL.color = if (dot < active) fg else inactive
                    c.drawCircle(x, y, dotR, FILL)
                }
            }
        }
        if (hasLeftWifi) {
            drawRingFlankWifi(c, wifiLevel, fg, a, isLeft = true)
        }
        if (hasRightWifi) {
            drawRingFlankWifi(c, wifiLevel, fg, a, isLeft = false)
        }
        // Every other piece may hold a flank too: drawn in the mouth the ring was cut for it,
        // centred on 9 or 3 o'clock.
        drawFlankPiece(c, level, quickCharging, type, fg, a, r, Prefs.ZONE_LEFT, isLeft = true)
        drawFlankPiece(c, level, quickCharging, type, fg, a, r, Prefs.ZONE_RIGHT, isLeft = false)
    }

    /**
     * One non-bead, non-Wi-Fi piece on a flank: a reading, the clock, the bolt.
     *
     * The mouth there is cut by [StatusAppearance.Ring.flankHalfDeg], and the piece is fitted
     * to the flat of that mouth, so the two can never disagree about how much room exists.
     */
    private fun drawFlankPiece(
        c: Canvas, level: Int, quickCharging: Boolean, type: String?,
        fg: Int, a: StatusAppearance, r: StatusAppearance.Ring, zone: Int, isLeft: Boolean,
    ) {
        val part = r.parts[zone]
        if (part == Prefs.PART_NONE || Prefs.isSignalRow(part) || part == Prefs.PART_WIFI) return
        val half = r.flankHalfDeg(zone)
        if (half <= 0f) return
        val cx = if (isLeft) IconGeometry.B_CX - IconGeometry.B_R else IconGeometry.B_CX + IconGeometry.B_R
        c.withTranslation(cx, IconGeometry.B_CY) {
            if (part == Prefs.PART_BOLT) {
                val roomW = IconGeometry.flankCellWidth(half) - 2f * IconGeometry.NOTCH_PIECE_CLEARANCE
                val roomH = IconGeometry.FLANK_CELL_ROOM_H
                val fit = min(roomW / 15f, roomH / IconGeometry.BOLT_INK_H)
                val scale = fit * a.boltScaleFactor()
                c.scale(scale, scale)
                c.translate(-IconGeometry.BOLT_CX_REF, -IconGeometry.BOLT_INK_CY)
                drawBoltShape(c, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
            } else {
                drawRingText(c, part, type, level, fg, a, -1, half)
            }
        }
    }

    /**
     * One text reading in the ring: a band's third, or a flank's cell.
     *
     * `band` names the zone the reading sits in; passing a negative value means "a flank",
     * where the room is the cell cut for it rather than a third's chord. Shared so the two
     * places cannot fit the same string two different ways.
     */
    private fun drawRingText(
        c: Canvas, part: Int, type: String?, level: Int, fg: Int,
        a: StatusAppearance, band: Int, flankHalfDeg: Float,
    ) {
        val flank = band < 0
        val text = when (part) {
            Prefs.PART_VALUE -> valueText(level, a)
            Prefs.PART_TYPE -> type
            Prefs.PART_POWER, Prefs.PART_RUNNING -> powerText()
            Prefs.PART_TEMP -> tempText()
            Prefs.PART_CURRENT -> currentText(a)
            Prefs.PART_FPS -> fpsText()
            else -> StatusState.sClockText
        }
        if (text.isNullOrEmpty()) return
        val weight = when (part) {
            Prefs.PART_VALUE -> a.valueWeight
            Prefs.PART_TYPE -> a.typeWeight
            Prefs.PART_POWER, Prefs.PART_RUNNING,
            Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS,
            -> a.perfTypeWeight()
            else -> a.clockWeight
        }
        val requested = when (part) {
            Prefs.PART_VALUE -> maxOf(a.valueSize.toFloat(), 50f)
            Prefs.PART_TYPE -> maxOf(a.typeSize.toFloat(), 46f)
            Prefs.PART_POWER, Prefs.PART_RUNNING,
            Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS,
            -> maxOf(a.perfReadingSize().toFloat(), 46f)
            else -> maxOf(a.clockSize.toFloat(), 46f)
        }
        applyWeight(weight)
        TEXT.textSize = requested
        val room = if (flank) {
            IconGeometry.FLANK_CELL_ROOM_H
        } else {
            IconGeometry.zoneTextRoomH(band)
        }
        val suffixScale = if (part == Prefs.PART_TYPE) a.typeSuffixScale else 100
        // The height cap is a straight ratio at the requested size.
        val inkAtRequested = max(1f, textInkHeight(text, requested))
        val maxByHeight = requested * room / inkAtRequested
        // The width cap is NOT independent of the answer: the third's room is a chord that
        // narrows as the line grows, the flank's is the flat of its mouth and does not.
        val flankRoomW = IconGeometry.flankCellWidth(flankHalfDeg) - 2f * IconGeometry.NOTCH_PIECE_CLEARANCE
        var fitted = maxByHeight
        for (pass in 0 until 4) {
            val inkH = max(1f, textInkHeight(text, fitted))
            val roomW = if (flank) flankRoomW else IconGeometry.zoneTextRoomW(band, a.ringStroke, inkH)
            val next = min(maxByHeight, fitSize(text, requested, roomW, suffixScale))
            if (abs(next - fitted) < 0.05f) {
                fitted = next
                break
            }
            fitted = next
        }
        // Centred on its INK rather than on its metrics.
        val slotCentre = if (flank) 0f else IconGeometry.zoneTextCy(band, a.ringStroke) -
            IconGeometry.ZONE_CY[band]
        val baseline = inkCentredBaseline(text, fitted, slotCentre)
        if (part == Prefs.PART_TYPE) {
            drawType(c, text, 0f, baseline, fitted, fg, a)
            return
        }
        TEXT.textAlign = Paint.Align.CENTER
        TEXT.textSize = fitted
        if (part == Prefs.PART_CLOCK) {
            drawTimeGlyphs(
                c, TEXT, text, 0f, baseline,
                if (isDarkBackground(fg)) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight,
                fg,
            )
        } else {
            val tempIsHigh = a.roleColorsTemp && part == Prefs.PART_TEMP && StatusState.sBatteryHot
            TEXT.color = if (tempIsHigh) {
                if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight
            } else {
                fg
            }
            c.drawText(text, 0f, baseline, TEXT)
        }
    }

    /**
     * The rectangular arrangement: two columns of four level dots, the top slot
     * between them, the enlarged percentage below that, and the battery bar across
     * the bottom.
     *
     * Draw order is back to front and does not depend on which slot is used, because
     * none of the pieces overlap: the bar and the dots are the frame, and the value
     * never reaches either.
     */
    private fun drawRectLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int,
        slotLevels: IntArray?, sims: Int, mobileType: String?, role: Int,
        fg: Int, a: StatusAppearance,
    ) {
        val r = a.rect(level, charging, wifiLevel, mobileType, sims)
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a)
        }
        drawRectBatteryBar(canvas, level, role, fg, a)

        // One slot at the top, and only one thing in it.
        if (r.bolt) {
            drawRectBolt(canvas, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a)
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a)
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a)
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a)
        }
    }

    /**
     * The capsule arrangement: a stadium outline that *is* the battery gauge,
     * broken at 12 and 6 o'clock by two four-dot rows, with the time on the left
     * inside and the percentage, the arcs or the bolt on the right.
     *
     * The order of the two outline passes is the whole design. The track is drawn
     * first, in full, in the track colour; then the charged part is drawn over it in
     * the role colour, covering the fraction of the outline's length the level asks
     * for. That is what reads as one line filling up rather than as two lines
     * meeting.
     */
    private fun drawCapsuleLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int, mobileType: String?,
        slotLevels: IntArray?, sims: Int, role: Int, fg: Int,
        a: StatusAppearance,
    ) {
        val stroke = a.ringStroke * IconGeometry.CAP_STROKE_RATIO
        val cap = a.capsule(level, charging, wifiLevel, mobileType, sims)

        // The track first, in full, then the charged part over it. `level < 0` is "no reading yet" and fills nothing.
        val right = a.capsuleRight()
        val left = IconGeometry.capLeftFor(right, IconGeometry.capPairRight(a))
        STROKE.strokeWidth = stroke
        STROKE.style = Paint.Style.STROKE
        // One reach per opening, because the two openings no longer necessarily hold the same thing.
        val topReach = openingReach(cap, IconZones.Zone.TOP, stroke, level, mobileType, a)
        val bottomReach = openingReach(cap, IconZones.Zone.BOTTOM, stroke, level, mobileType, a)
        val openLeft = cap.dotsIn(IconZones.Zone.LEFT)
        val openRight = cap.dotsIn(IconZones.Zone.RIGHT)
        val hasLeftWifi = cap.parts[IconZones.Zone.LEFT.ordinal] == Prefs.PART_WIFI
        val hasRightWifi = cap.parts[IconZones.Zone.RIGHT.ordinal] == Prefs.PART_WIFI
        buildCapsuleOutline(CAPSULE, topReach, bottomReach, left, right, openLeft, openRight)
        val animated = a.animatesOutline(charging, level)
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f
        STROKE.color = withAlpha(fg, a.trackAlpha)
        canvas.drawPath(CAPSULE, STROKE)

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            drawCapsuleFill(canvas, min(1f, level / 100f), topReach, bottomReach, left, right, openLeft, openRight)
        }

        if (animated) {
            when (a.boltAnim) {
                Prefs.BOLT_ANIM_FLOW -> {
                    val head = flowHeadColor(role)
                    drawCapsuleFlow(canvas, head, stroke)
                }
                // The spark style moves the outline as well as the bolt. It was drawn
                // only for the flow style, which left this one with a completely still
                // capsule - the one style whose whole point is that the ink travels.
                // Ink colour, for the same reason as the ring: it is what reads on both
                // the background and the charged outline.
                Prefs.BOLT_ANIM_SPARK -> drawCapsuleSparks(canvas, fg, stroke)
                else -> Unit
            }
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, mobileLevel, slotLevels, cap.dual, fg, a, cap, left, right)
        }
        if (hasLeftWifi) {
            drawCapsuleFlankWifi(canvas, stroke, wifiLevel, fg, a, left, right, isLeft = true)
        }
        if (hasRightWifi) {
            drawCapsuleFlankWifi(canvas, stroke, wifiLevel, fg, a, left, right, isLeft = false)
        }

        // Three bands, the same three the ring has: the two openings and the interior.
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.TOP, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.CENTRE, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, IconZones.Zone.BOTTOM, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
    }

    /**
     * Draws whatever `band` holds, at that band's own centre.
     *
     * The centre comes from the zone, not from the piece: a bolt and a percentage are
     * laid out differently inside the capsule, but they agree about where each band
     * is, which is the whole point of putting them in bands.
     */
    private fun drawCapsuleCell(
        c: Canvas, stroke: Float, band: IconZones.Zone,
        cap: StatusAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance, left: Float, right: Float,
    ) {
        if (band == IconZones.Zone.CENTRE) {
            drawCapsuleBody(c, stroke, cap, level, quickCharging, wifiLevel, type, fg, a, left, right)
            return
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return
        }
        drawCapsulePiece(
            c, stroke, part, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = IconGeometry.capDotCx(left, right),
            cy = IconGeometry.capOpeningCy(band == IconZones.Zone.TOP, stroke),
            align = CellAlign.CENTRE,
            width = IconGeometry.capOpeningWidth(right),
            heightHint = openingRoom(band, stroke),
        )
    }

    /**
     * The body: one piece centred on the interior, or the two the user placed there
     * side by side.
     *
     * The outline is already as wide as the arrangement needs - `capsuleRight` reads
     * the stored layout, so a second piece widens it for every frame, including the
     * ones where that piece has no ink. When it has none, the first stays centred
     * rather than sitting in the left half with nothing beside it.
     */
    private fun drawCapsuleBody(
        c: Canvas, stroke: Float, cap: StatusAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance, left: Float, right: Float,
    ) {
        val first = cap.parts[IconZones.Zone.CENTRE.ordinal]
        if (first == Prefs.PART_NONE) {
            return
        }
        val second = cap.second[IconZones.Zone.CENTRE.ordinal]
        val cy = IconGeometry.capInteriorCy(stroke)
        // The two cells are independent components on one track, not a width divided
        // between them: each hangs off its own edge of the interior and grows inwards,
        // so placing a clock on the left cannot squeeze the number on the right. The
        // track is what each is fitted against, which is why a reading can be set past
        // the ceiling a fixed half-slot used to impose.
        // The margin the ink keeps from each cap IS the spacing setting - one control, not
        // a constant beside it. 100% is the reference inset; 0% lets a reading run to the
        // cap, which is also the only way two pieces can meet in the middle.
        val baseInset = CAP_CELL_PADDING * a.capGap / 100f
        val leftExtra = if (cap.dotsIn(IconZones.Zone.LEFT)) 8f else 0f
        val rightExtra = if (cap.dotsIn(IconZones.Zone.RIGHT)) 8f else 0f
        val track = IconGeometry.capReadingsWidth(stroke, right) - 2f * baseInset - leftExtra - rightExtra
        val edgeLeft = IconGeometry.capTextLeft(stroke) + baseInset + leftExtra
        val edgeRight = IconGeometry.capTextRight(stroke, right) - baseInset - rightExtra
        // A lone piece occupies the first cell, which reads from the interior's left edge.
        drawCapsulePiece(
            c, stroke, first, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = edgeLeft + a.capsuleLeftDx,
            cy = cy + a.capsuleLeftDy,
            align = CellAlign.LEFT, width = track, heightHint = -1f,
        )
        if (second == Prefs.PART_NONE) {
            return
        }
        val isIcon = second == Prefs.PART_BOLT || second == Prefs.PART_WIFI
        val secondAnchorX = if (isIcon) {
            val inkHalf = when (second) {
                Prefs.PART_BOLT -> BOLT_INK_W * IconGeometry.capBoltScale(stroke) *
                    a.boltScaleFactor() * 0.5f
                else -> IconGeometry.W_INK_W * IconGeometry.capWifiScale(stroke) *
                    a.wifiScaleFactor() * 0.5f
            }
            IconGeometry.capRightIconCx(stroke, right, baseInset + rightExtra, inkHalf) + a.capsuleRightDx
        } else {
            edgeRight + a.capsuleRightDx
        }
        drawCapsulePiece(
            c, stroke, second, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = secondAnchorX,
            cy = cy + a.capsuleRightDy,
            align = if (isIcon) CellAlign.CENTRE else CellAlign.RIGHT,
            width = track, heightHint = -1f,
        )
    }

    /** Draws one piece in a cell of the given width and height budget. */
    private fun drawCapsulePiece(
        c: Canvas, stroke: Float, part: Int, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, width: Float, heightHint: Float,
    ) {
        when (part) {
            Prefs.PART_BOLT -> {
                // An opening gives an icon the whole lane it is centred on; the body gives it
                // the capsule's own reference scale.
                val room = if (heightHint > 0f) IconGeometry.capOpeningIconRoomH(stroke) else -1f
                val baseScale = if (room > 0f) {
                    val maxW = if (width > 0f) width else 40f
                    minOf(maxW / 15f, room / IconGeometry.BOLT_INK_H)
                } else {
                    IconGeometry.capBoltScale(stroke)
                }
                // Capped by the room: multiplying past it drew the bolt out of the ink box,
                // where the host clipped it.
                val scale = if (room > 0f) {
                    IconGeometry.fittedScale(baseScale, a.boltScaleFactor())
                } else {
                    baseScale * a.boltScaleFactor()
                }
                val ink = BOLT_INK_W * scale
                drawCapsuleBolt(
                    c, stroke, if (quickCharging) IconGeometry.QUICK_CHARGE else fg, a,
                    alignedCx(anchorX, ink, align), cy, heightHint, width, scale,
                )
            }
            Prefs.PART_WIFI -> {
                val extra = max(0f, a.arcStroke - IconGeometry.W_STROKE)
                val room = if (heightHint > 0f) IconGeometry.capOpeningIconRoomH(stroke) else -1f
                val baseScale = if (room > 0f) {
                    val maxW = if (width > 0f) width else 60f
                    minOf(
                        maxW / (IconGeometry.W_INK_W + extra),
                        room / (IconGeometry.W_INK_H + extra),
                    )
                } else {
                    IconGeometry.capWifiScale(stroke)
                }
                val scale = if (room > 0f) {
                    IconGeometry.fittedScale(baseScale, a.wifiScaleFactor())
                } else {
                    baseScale * a.wifiScaleFactor()
                }
                val ink = IconGeometry.W_INK_W * scale
                drawCapsuleWifi(
                    c, stroke, wifiLevel, fg, a, alignedCx(anchorX, ink, align), cy, heightHint, width, scale,
                )
            }
            Prefs.PART_VALUE -> drawCapsuleValue(
                c, stroke, level, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_TYPE -> drawCapsuleType(
                c, stroke, type, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_CLOCK -> drawCapsuleTime(
                c, stroke, fg, a, anchorX, cy, align, heightHint, width,
            )
            Prefs.PART_POWER, Prefs.PART_RUNNING, Prefs.PART_TEMP, Prefs.PART_CURRENT, Prefs.PART_FPS ->
                drawCapsulePerf(
                    c, stroke, capsulePerfText(part, a), fg, a, anchorX, cy, align,
                    heightHint, width,
                )
            else -> Unit
        }
    }

    /**
     * Which edge of the interior a cell hangs from.
     *
     * The left cell reads left-aligned, the right cell right-aligned, so a reading is
     * placed by the edge it starts (or ends) at rather than by a centre handed out in
     * advance. The openings still centre their piece on the band.
     */
    private enum class CellAlign { LEFT, RIGHT, CENTRE }

    /** Turns an anchor and the ink it will draw into the centre the drawers take. */
    private fun alignedCx(anchorX: Float, inkW: Float, align: CellAlign): Float = when (align) {
        CellAlign.LEFT -> anchorX + inkW * 0.5f
        CellAlign.RIGHT -> anchorX - inkW * 0.5f
        CellAlign.CENTRE -> anchorX
    }
    /**
     * A readout as its own capsule cell: the wattage or the temperature, never both.
     *
     * The cell holds one line, because the piece is one reading: the out-of-ring strip
     * stacks the pair, and a cell that did the same would be the thing this piece
     * exists to avoid.
     */
    private fun drawCapsulePerf(
        c: Canvas, stroke: Float, text: String?, fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        if (text.isNullOrEmpty()) {
            return
        }
        applyWeight(a.perfTypeWeight())
        val size = capsuleTextSize(
            text, a.perfReadingSize(), width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        val tempIsHigh = a.roleColorsTemp && text == tempText() &&
            StatusState.sBatteryHot
        TEXT.color = if (tempIsHigh) (if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight) else fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun openingRoom(band: IconZones.Zone, stroke: Float): Float {
        // An EM, not an ink height: everything downstream of this treats it as a font size.
        // See IconGeometry.capOpeningTextEm.
        return if (band == IconZones.Zone.CENTRE) -1f else IconGeometry.capOpeningTextEm(stroke)
    }

    /**
     * How far the outline is cut back at one opening: the half-width of whatever
     * that opening draws.
     *
     * A dot row is fixed - four beads on their own pitch - but a reading is not:
     * "73" and "100" and "5GA" are three different widths, and a gap cut for the
     * widest of them would leave the line interrupted across half the capsule for
     * the narrowest. So the reach is measured per frame from the piece that band
     * actually holds, and zero when it holds nothing.
     */
    private fun openingReach(
        cap: StatusAppearance.Capsule, band: IconZones.Zone,
        stroke: Float, level: Int, type: String?, a: StatusAppearance,
    ): Float {
        if (cap.dotsIn(band)) {
            return IconGeometry.capDotReach(stroke)
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return 0f
        }
        val factor = when (part) {
            Prefs.PART_WIFI -> a.wifiScaleFactor()
            Prefs.PART_BOLT -> a.boltScaleFactor()
            else -> 1f
        }
        return IconGeometry.capPieceReach(
            part, level, type ?: "", stroke,
            IconGeometry.capOpeningTextRoomH(stroke), factor,
        ) + IconGeometry.capDotClearance() + stroke * 0.5f
    }

    private fun buildCapsuleOutline(
        p: Path, topReach: Float, bottomReach: Float, left: Float, right: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
    ) {
        val top = IconGeometry.CAP_TOP
        val bottom = IconGeometry.CAP_BOTTOM
        val r = IconGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f
        val k = 0.55228475f * r

        p.reset()
        p.moveTo(cx + topReach, top)
        p.lineTo(right - r, top)
        if (!openRight) {
            p.cubicTo(right - r + k, top, right, cy - k, right, cy)
            p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom)
        } else {
            p.moveTo(right - r, bottom)
        }
        p.lineTo(cx + bottomReach, bottom)
        p.moveTo(cx - bottomReach, bottom)
        p.lineTo(left + r, bottom)
        if (!openLeft) {
            p.cubicTo(left + r - k, bottom, left, cy + k, left, cy)
            p.cubicTo(left, cy - k, left + r - k, top, left + r, top)
        } else {
            p.moveTo(left + r, top)
        }
        p.lineTo(cx - topReach, top)
    }

    /**
     * The charged part of the outline, walked segment by segment.
     *
     * Drawn as its own geometry rather than by dashing the track's path: a dash
     * pattern would depend on the platform measuring the path for us, and the fill's
     * end would land wherever that measurement said. Walking the outline explicitly
     * means the fraction drawn is the fraction asked for.
     *
     * **Where it fills from.** The two dot rows divide the outline into exactly two
     * arcs - a left one and a right one - and the level is read off them as two
     * bars: full is entirely lit, and the charge is spent from the *bottom of the
     * right bar*, travelling counterclockwise. That is why the walk below runs
     * *clockwise from the bottom gap's left edge*: it visits the lit arc in the
     * order the level grows through it.
     */
    private fun drawCapsuleFill(
        c: Canvas, fraction: Float, topReach: Float, bottomReach: Float,
        left: Float, right: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
    ) {
        val top = IconGeometry.CAP_TOP
        val bottom = IconGeometry.CAP_BOTTOM
        val r = IconGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f

        // Four straight runs and two half-circle caps, in travel order. The gaps
        // are not segments: they carry no ink in either pass.
        val topLeft = (cx - topReach) - (left + r)
        val bottomLeft = (cx - bottomReach) - (left + r)
        val bottomRight = (right - r) - (cx + bottomReach)
        val topRight = (right - r) - (cx + topReach)
        val leftCapLen = if (openLeft) 0f else PI.toFloat() * r
        val rightCapLen = if (openRight) 0f else PI.toFloat() * r
        val total = topLeft + leftCapLen + bottomLeft + bottomRight + rightCapLen + topRight
        if (total <= 0f) {
            return
        }
        var left0 = total * fraction

        // The six pieces, clockwise from the bottom gap's left edge. Each starts exactly where the previous one ended, so the fill has no seam.
        left0 = run(c, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom)
        if (!openLeft) {
            left0 = cap(c, left0, leftCapLen, left + r, cy, r, 90f, 180f)
        }
        left0 = run(c, left0, topLeft, left + r, top, cx - topReach, top)
        left0 = run(c, left0, topRight, cx + topReach, top, right - r, top)
        if (!openRight) {
            left0 = cap(c, left0, rightCapLen, right - r, cy, r, 270f, 180f)
        }
        run(c, left0, bottomRight, right - r, bottom, cx + bottomReach, bottom)
    }

    /**
     * Draws as much of one straight run as `remaining` allows, and answers what is
     * left.
     */
    private fun run(
        c: Canvas, remaining: Float, length: Float,
        x1: Float, y1: Float, x2: Float, y2: Float,
    ): Float {
        if (remaining <= 0f || length <= 0f) {
            return remaining
        }
        if (remaining >= length) {
            c.drawLine(x1, y1, x2, y2, STROKE)
            return remaining - length
        }
        val t = remaining / length
        c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, STROKE)
        return 0f
    }

    /**
     * The same for one half-circle cap.
     *
     * `startDeg` is the angle the cap is entered at and `sweepDeg` is signed, so a
     * partial cap grows from the end the walk arrives at rather than from the
     * middle.
     */
    private fun cap(
        c: Canvas, remaining: Float, length: Float,
        cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float,
    ): Float {
        if (remaining <= 0f || length <= 0f) {
            return remaining
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r)
        if (remaining >= length) {
            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
            return remaining - length
        }
        c.drawArc(ARC, startDeg, sweepDeg * (remaining / length), false, STROKE)
        return 0f
    }

    /**
     * The same flowing current as the ring's, on the capsule's outline.
     *
     * A stroked segment travelling the outline, not a bead on it: the outline is a thin
     * line, and a dot wide enough to see sits on the line like a knot. The outline is
     * two contours - the right half and the left half, with both mouths left open - so
     * each half carries its own light, the second offset by half a lap: one light
     * arriving at a mouth as the other leaves is a flow, while both starting together
     * at the two mouths is a blink.
     *
     * Each half's light wraps past its own end rather than restarting there, the same
     * way the ring's does, so neither half ever has a lap with no tail.
     */
    private fun drawCapsuleFlow(c: Canvas, color: Int, stroke: Float) {
        PATH_MEASURE.setPath(CAPSULE, false)
        STROKE.strokeWidth = stroke
        var half = 0
        do {
            val length = PATH_MEASURE.length
            if (length > 0f) {
                val phase = (animPhase() + half * 0.5f) % 1f
                val span = length * RING_FLOW_SPAN
                for (i in 0 until RING_FLOW_SEGMENTS) {
                    val head = phase * length - i * span
                    STROKE.color = withAlpha(color, flowAlpha(i, RING_FLOW_SEGMENTS))
                    capsuleFlowSegment(c, head - span, head, length)
                }
            }
            half++
        } while (PATH_MEASURE.nextContour())
    }

    /** One segment of the capsule's light, in path length that may run outside 0..length. */
    private fun capsuleFlowSegment(c: Canvas, fromIn: Float, toIn: Float, length: Float) {
        val from = fromIn - floor(fromIn / length) * length
        val to = from + (toIn - fromIn)
        if (to <= length) {
            flowPiece(c, from, to)
            return
        }
        flowPiece(c, from, length)
        flowPiece(c, 0f, to - length)
    }

    /** Draws the piece of the outline between two path lengths, if it has any. */
    private fun flowPiece(c: Canvas, from: Float, to: Float) {
        if (to <= from) {
            return
        }
        FLOW_PATH.reset()
        if (PATH_MEASURE.getSegment(from, to, FLOW_PATH, true)) {
            c.drawPath(FLOW_PATH, STROKE)
        }
    }

    /**
     * Sparks travelling the capsule's outline, the same idea as the bolt's.
     *
     * Each contour is walked on its own: the outline is two halves with the mouths
     * between them, and a spark crossing a mouth would read as the ring being closed.
     */
    private fun drawCapsuleSparks(c: Canvas, color: Int, stroke: Float) {
        PATH_MEASURE.setPath(CAPSULE, false)
        val phase = animPhase()
        val radius = stroke * CAPSULE_SPARK_SCALE
        do {
            val length = PATH_MEASURE.length
            if (length <= 0f) continue
            for (i in 0 until CAPSULE_SPARK_COUNT) {
                val at = ((phase + i / CAPSULE_SPARK_COUNT.toFloat()) % 1f) * length
                // Trailing sparks
                for (tail in 2 downTo 1) {
                    val tat = ((at - tail * 0.022f * length + length) % length)
                    if (PATH_MEASURE.getPosTan(tat, SPARK_POS, null)) {
                        FILL.color = withAlpha(color, if (tail == 1) 140 else 60)
                        c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * (if (tail == 1) 0.7f else 0.45f), FILL)
                    }
                }
                // Main spark with glowing aura and brilliant core
                if (PATH_MEASURE.getPosTan(at, SPARK_POS, null)) {
                    FILL.color = withAlpha(color, 120)
                    c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * 1.4f, FILL)
                    FILL.color = Color.WHITE
                    c.drawCircle(SPARK_POS[0], SPARK_POS[1], radius * 0.85f, FILL)
                }
            }
        } while (PATH_MEASURE.nextContour())
    }

    /**
     * The two openings' dot rows, each drawn only if its band holds one.
     *
     * They are separate placements now - [StatusAppearance.Capsule.dotsIn] - so a
     * capsule can draw SIM 1 above and nothing below. The **outline's** gap is still
     * cut at both openings while any row exists, because a stroke interrupted on one
     * side only reads as damage rather than as a row between two segments.
     */
    private fun drawCapsuleDots(
        c: Canvas, level: Int, slotLevels: IntArray?, dual: Boolean,
        fg: Int, a: StatusAppearance, cap: StatusAppearance.Capsule?, left: Float, right: Float,
    ) {
        val dataLevel = StatusState.dataSimSignalLevel()
        val top = if (a.dataSimOnly) dataLevel else if (dual && slotLevels != null) slotLevels[0] else dataLevel
        val bottom = if (a.dataSimOnly) -1 else if (dual && slotLevels != null) slotLevels[1] else -1
        val topRow = cap == null || cap.dotsIn(IconZones.Zone.TOP)
        val bottomRow = !a.dataSimOnly && (cap == null || cap.dotsIn(IconZones.Zone.BOTTOM))
        val leftFlank = cap?.dotsIn(IconZones.Zone.LEFT) == true
        val rightFlank = cap?.dotsIn(IconZones.Zone.RIGHT) == true
        val inactive = withAlpha(fg, a.trackAlpha)
        val columns = a.signalColumns.coerceIn(4, 5)
        val topActive = if (top < 0) 0 else levelMapped(top, columns)
        val bottomActive = if (bottom < 0) 0 else levelMapped(bottom, columns)
        val topY = IconGeometry.capDotTopY()
        val bottomY = IconGeometry.capDotBottomY()
        val pitch = if (columns == 5) IconGeometry.CAP_DOT_PITCH * 0.82f else IconGeometry.CAP_DOT_PITCH
        val halfSpan = (columns - 1) * pitch * 0.5f
        val first = IconGeometry.capDotCx(left, right) - halfSpan
        val dotR = if (columns == 5) IconGeometry.CAP_DOT_R * 0.85f else IconGeometry.CAP_DOT_R
        val btLevel = StatusState.sBluetoothBatteryLevel
        val btActive = a.btBatteryMode > 0 && btLevel in 0..100
        if (btActive && topRow) {
            val cx = IconGeometry.capDotCx(left, right)
            if (a.btBatteryMode == Prefs.BT_BATTERY_DIGIT) {
                TEXT.textSize = 20f
                TEXT.typeface = Typeface.DEFAULT_BOLD
                TEXT.textAlign = Paint.Align.CENTER
                TEXT.color = fg
                c.drawText(btLevelText(btLevel), cx, topY + 6f, TEXT)
            } else {
                val halfBar = 16f
                STROKE.style = Paint.Style.STROKE
                STROKE.strokeWidth = 4f
                STROKE.strokeCap = Paint.Cap.ROUND
                STROKE.color = inactive
                c.drawLine(cx - halfBar, topY, cx + halfBar, topY, STROKE)
                if (btLevel > 0) {
                    STROKE.color = fg
                    c.drawLine(cx - halfBar, topY, cx - halfBar + (2f * halfBar * (btLevel / 100f)), topY, STROKE)
                }
            }
        }
        FILL.style = Paint.Style.FILL
        for (i in 0 until columns) {
            val x = first + i * pitch
            if (topRow && !btActive) {
                FILL.color = if (i < topActive) fg else inactive
                c.drawCircle(x, topY, dotR, FILL)
            }
            if (bottomRow) {
                FILL.color = if (i < bottomActive) fg else inactive
                c.drawCircle(x, bottomY, dotR, FILL)
            }
        }
        if (leftFlank || rightFlank) {
            val capCy = (IconGeometry.CAP_TOP + IconGeometry.CAP_BOTTOM) * 0.5f
            val sim1Level = if (slotLevels != null && slotLevels.isNotEmpty() && slotLevels[0] >= 0) slotLevels[0] else dataLevel
            val sim2Level = if (slotLevels != null && slotLevels.size > 1 && slotLevels[1] >= 0) slotLevels[1] else dataLevel
            val capR = IconGeometry.CAP_RADIUS
            val halfDiv = (columns - 1) * 0.5f
            val arcHalfAngle = Math.toRadians(55.0).toFloat()
            val flankDotR = (if (columns == 5) 4.0f else 4.6f) * a.dotScaleFactor()
            if (leftFlank) {
                val leftPart = a.ringZones.getOrNull(Prefs.ZONE_LEFT)?.firstOrNull { Prefs.isSignalRow(it) }
                val lLevel = when (leftPart) {
                    Prefs.PART_SIM_1 -> sim1Level
                    Prefs.PART_SIM_2 -> sim2Level
                    else -> dataLevel
                }
                val lActive = if (lLevel < 0) 0 else levelMapped(lLevel, columns)
                val arcCenterCx = left + capR
                for (i in 0 until columns) {
                    val t = (i - halfDiv) / halfDiv
                    val angle = Math.toRadians(180.0).toFloat() + t * arcHalfAngle
                    val x = arcCenterCx + capR * kotlin.math.cos(angle)
                    val y = capCy + capR * kotlin.math.sin(angle)
                    FILL.color = if (i < lActive) fg else inactive
                    c.drawCircle(x, y, flankDotR, FILL)
                }
            }
            if (rightFlank) {
                val rightPart = a.ringZones.getOrNull(Prefs.ZONE_RIGHT)?.firstOrNull { Prefs.isSignalRow(it) }
                val rLevel = when (rightPart) {
                    Prefs.PART_SIM_2 -> sim2Level
                    Prefs.PART_SIM_1 -> sim1Level
                    else -> dataLevel
                }
                val rActive = if (rLevel < 0) 0 else levelMapped(rLevel, columns)
                val arcCenterCx = right - capR
                for (i in 0 until columns) {
                    val t = (i - halfDiv) / halfDiv
                    val angle = 0f + t * arcHalfAngle
                    val x = arcCenterCx + capR * kotlin.math.cos(angle)
                    val y = capCy + capR * kotlin.math.sin(angle)
                    FILL.color = if (i < rActive) fg else inactive
                    c.drawCircle(x, y, flankDotR, FILL)
                }
            }
        }
    }
    /**
     * Draws the time one character at a time so each glyph can take its own colour.
     *
     * A single `drawText` cannot: one paint has one colour. The advances come from the
     * same paint, so the run measures exactly like the plain draw it replaces. A glyph
     * with no colour of its own (index beyond the list, or `0`) keeps [fallback].
     *
     * @param x the anchor the paint's own alignment is measured from
     */
    private fun drawTimeGlyphs(
        c: Canvas, paint: Paint, text: String, x: Float, baseline: Float,
        colors: IntArray, fallback: Int,
    ) {
        if (text.isEmpty()) return
        val align = paint.textAlign
        val width = paint.measureText(text)
        val start = when (align) {
            Paint.Align.CENTER -> x - width * 0.5f
            Paint.Align.RIGHT -> x - width
            else -> x
        }
        paint.textAlign = Paint.Align.LEFT
        var cursor = start
        for (i in text.indices) {
            val picked = if (i < colors.size) colors[i] else 0
            paint.color = if (picked != 0) picked else fallback
            paint.getTextWidths(text, i, i + 1, GLYPH_ADVANCE)
            c.drawText(text, i, i + 1, cursor, baseline, paint)
            cursor += GLYPH_ADVANCE[0]
        }
        paint.textAlign = align
    }

    private val GLYPH_ADVANCE = FloatArray(1)

    /** The time, in a capsule cell centred on `(cx, cy)`. */
    private fun drawCapsuleTime(
        c: Canvas, stroke: Float, fg: Int, a: StatusAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        val text = StatusState.sClockText
        if (text.isEmpty()) {
            return
        }
        // The clock's own size and weight, not the value's.
        applyWeight(a.clockWeight)
        val size = capsuleTextSize(
            text, a.clockSize, width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        val baseline = cy + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f
        drawTimeGlyphs(
            c, TEXT, text, x, baseline,
            if (isDarkBackground(fg)) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight,
            fg,
        )
    }

    private fun drawCapsuleValue(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: StatusAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        val text = valueText(level, a)
        applyWeight(a.valueWeight)
        val size = capsuleTextSize(
            text, a.valueSize, width, 100,
            heightHint, stroke,
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun drawCapsuleType(
        c: Canvas, stroke: Float, type: String?, fg: Int,
        a: StatusAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        applyWeight(a.typeWeight)
        // The width of the row it is in, not of the interior's right-hand slot. See
        // IconGeometry.capOpeningWidth: an opening spans the capsule, a slot does not.
        val size = capsuleTextSize(
            type, a.typeSize, width, a.typeSuffixScale,
            heightHint, stroke,
        )
        // The trailing "A" is drawn smaller, so the box the label occupies is measured the
        // way drawType lays it out rather than with a plain measureText.
        val ink = measureType(type, size, a.typeSuffixScale)
        drawType(
            c, type, alignedCx(anchorX, ink, align),
            cy + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f,
            size, fg, a,
        )
    }

    /**
     * The size a capsule reading is drawn at: its own setting, capped by the height the
     * cell can hold, then fitted to the track it was handed.
     *
     * The two cells size themselves differently, and the difference is the whole point:
     *
     *  - In the **interior** (a negative hint) the setting is an absolute size, capped by
     *    what a line of the interior's height can be.
     *  - In an **opening** the band is the size: `hint` is the largest line the band can
     *    hold, and the setting scales that. Measured against an absolute em instead - which
     *    is what this did - every opening label was clamped to the band's own height and the
     *    size slider did nothing at all up there, which is exactly what was reported.
     *
     * [slotWidth] is the track, not a share of it - see IconGeometry.capReadingsWidth.
     * A reading is fitted to the room it could use if it were alone, which is what keeps
     * one cell's content from resizing the other.
     */
    private fun capsuleTextSize(
        text: String?, setting: Int, slotWidth: Float, suffixPercent: Int,
        heightHint: Float, stroke: Float,
    ): Float {
        val requested = if (heightHint < 0f) {
            min(
                setting * IconGeometry.CAP_TEXT_SCALE,
                IconGeometry.capTextMaxSize(stroke),
            )
        } else {
            heightHint * setting / Prefs.DEF_VALUE_SIZE
        }
        return fitSize(text, requested, slotWidth, suffixPercent)
    }

    private fun drawCapsuleWifi(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: StatusAppearance, cx: Float, cy: Float,
        heightHint: Float = -1f, width: Float = -1f, scaleIn: Float = -1f,
    ) {
        val extra = max(0f, a.arcStroke - IconGeometry.W_STROKE)
        val scale = if (scaleIn > 0f) {
            scaleIn
        } else {
            val baseScale = if (heightHint > 0f) {
                val maxW = if (width > 0f) width else 60f
                minOf(
                    maxW / (IconGeometry.W_INK_W + extra),
                    heightHint / (IconGeometry.W_INK_H + extra),
                ) * 0.95f
            } else {
                IconGeometry.capWifiScale(stroke)
            }
            baseScale * a.wifiScaleFactor()
        }
        c.withTranslation(
            cx - IconGeometry.W_CX * scale,
            cy - IconGeometry.W_INK_CY * scale,
        ) {
            c.scale(scale, scale)
            drawWifiArcs(c, level, fg, a)
        }
    }

    private fun drawCapsuleFlankWifi(
        c: Canvas, stroke: Float, wifiLevel: Int, fg: Int, a: StatusAppearance,
        left: Float, right: Float, isLeft: Boolean,
    ) {
        val capR = IconGeometry.CAP_RADIUS
        val capCy = (IconGeometry.CAP_TOP + IconGeometry.CAP_BOTTOM) * 0.5f
        val arcCenterCx = if (isLeft) left + capR else right - capR
        val inactive = withAlpha(fg, a.trackAlpha)
        val strokeWidth = stroke * 0.78f
        STROKE.strokeWidth = strokeWidth
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.style = Paint.Style.STROKE

        val arcParams = if (isLeft) arrayOf(Triple(156f, 48f, 9.0f), Triple(144f, 72f, 19.0f)) else arrayOf(Triple(-24f, 48f, 9.0f), Triple(-36f, 72f, 19.0f))

        for (idx in arcParams.indices) {
            val (startAngle, sweepAngle, offset) = arcParams[idx]
            val r = capR + offset
            ARC.set(arcCenterCx - r, capCy - r, arcCenterCx + r, capCy + r)
            val active = if (idx == 0) wifiLevel >= 1 else wifiLevel >= 3
            STROKE.color = if (active) fg else inactive
            c.drawArc(ARC, startAngle, sweepAngle, false, STROKE)
        }
    }

    private fun drawRingFlankWifi(
        c: Canvas, wifiLevel: Int, fg: Int, a: StatusAppearance, isLeft: Boolean,
    ) {
        val cx = IconGeometry.B_CX
        val cy = IconGeometry.B_CY
        val inactive = withAlpha(fg, a.trackAlpha)
        val ringStroke = a.ringStroke * IconGeometry.RING_STROKE_RATIO
        val strokeWidth = IconGeometry.flankWifiStroke(ringStroke)
        val innerR = IconGeometry.flankWifiInnerR(ringStroke, IconGeometry.B_R)
        val outerR = IconGeometry.flankWifiOuterR(ringStroke, IconGeometry.B_R)
        STROKE.strokeWidth = strokeWidth
        STROKE.strokeCap = Paint.Cap.ROUND
        STROKE.style = Paint.Style.STROKE

        // The fan keeps its own sweeps; only their radii come from the ring, so the small arc
        // stays clear of the ring's stroke instead of being drawn onto its edge. Scalars, not
        // a list of triples: this runs for every frame the glyph draws.
        val innerSweep = IconGeometry.FLANK_WIFI_SWEEP_INNER
        val outerSweep = IconGeometry.FLANK_WIFI_SWEEP_OUTER
        val centreDeg = if (isLeft) 180f else 0f

        ARC.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        STROKE.color = if (wifiLevel >= 1) fg else inactive
        c.drawArc(ARC, centreDeg - innerSweep * 0.5f, innerSweep, false, STROKE)

        ARC.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        STROKE.color = if (wifiLevel >= 3) fg else inactive
        c.drawArc(ARC, centreDeg - outerSweep * 0.5f, outerSweep, false, STROKE)
    }

    /**
     * The charging bolt, in a capsule cell centred on `(cx, cy)`.
     *
     * The cell's own fit is the base; the user's size multiplies inside that room.
     * The offsets are recomputed from the resulting scale rather than added to the
     * existing pair, because `offset = centre - reference * scale` only keeps the
     * ink centred while the scale it was derived from is the scale being drawn.
     */
    private fun drawCapsuleBolt(
        c: Canvas, stroke: Float, color: Int, a: StatusAppearance,
        cx: Float, cy: Float,
        heightHint: Float = -1f, width: Float = -1f, scaleIn: Float = -1f,
    ) {
        val scale = if (scaleIn > 0f) {
            scaleIn
        } else {
            val baseScale = if (heightHint > 0f) {
                val maxW = if (width > 0f) width else 40f
                minOf(maxW / 15f, heightHint / IconGeometry.BOLT_INK_H) * 0.95f
            } else {
                IconGeometry.capBoltScale(stroke)
            }
            baseScale * a.boltScaleFactor()
        }
        c.withTranslation(
            cx - IconGeometry.BOLT_CX_REF * scale,
            cy - IconGeometry.BOLT_INK_CY * scale,
        ) {
            c.scale(scale, scale)
            FILL.style = Paint.Style.FILL
            drawBoltShape(c, color, a)
        }
    }

    /**
     * The two mirrored dot columns. Both carry the same level, and drawing two of them
     * is what makes the arrangement symmetrical around the text rather than a list of
     * items on one side.
     *
     * In the dual-SIM reading that symmetry becomes the point: the columns are
     * already two, so the left one reports SIM 1 and the right one SIM 2.
     *
     * @param slotLevels per-SIM levels, or `null` to repeat [level]
     */
    private fun drawRectDots(
        c: Canvas, level: Int, slotLevels: IntArray?, dual: Boolean,
        fg: Int, a: StatusAppearance,
    ) {
        val left = if (dual && slotLevels != null) slotLevels[0] else level
        val right = if (dual && slotLevels != null) slotLevels[1] else level
        val inactive = withAlpha(fg, a.trackAlpha)
        val leftActive = if (left < 0) 0 else min(IconGeometry.RECT_DOT_ROWS, left)
        val rightActive = if (right < 0) 0 else min(IconGeometry.RECT_DOT_ROWS, right)
        for (i in 0 until IconGeometry.RECT_DOT_ROWS) {
            val y = IconGeometry.RECT_DOT_TOP + i * IconGeometry.RECT_DOT_STEP
            FILL.color = if (i < leftActive) fg else inactive
            c.drawCircle(IconGeometry.RECT_DOT_LX, y, IconGeometry.RECT_DOT_R, FILL)
            FILL.color = if (i < rightActive) fg else inactive
            c.drawCircle(IconGeometry.RECT_DOT_RX, y, IconGeometry.RECT_DOT_R, FILL)
        }
    }

    /**
     * The battery bar: the track as one capsule across the full width, then the lit
     * part on top of it.
     *
     * The lit part is rounded on the left and square on the right, so its head
     * follows the track's cap while its tail stays a vertical edge wherever the level
     * happens to fall.
     */
    private fun drawRectBatteryBar(
        c: Canvas, level: Int, role: Int, fg: Int,
        a: StatusAppearance,
    ) {
        val left = IconGeometry.RECT_BAR_LEFT
        val right = IconGeometry.RECT_BAR_RIGHT
        val r = a.ringStroke * 0.5f
        val top = IconGeometry.RECT_BAR_CY - r
        val bottom = IconGeometry.RECT_BAR_CY + r

        FILL.color = withAlpha(fg, a.trackAlpha)
        RECT_RECT.set(left, top, right, bottom)
        c.drawRoundRect(RECT_RECT, r, r, FILL)

        if (level <= 0) {
            return
        }
        val cut = left + (right - left) * clamp01(level / 100f)
        RECT_RECT.set(left, top, cut, bottom)
        RECT_RADII[0] = r
        RECT_RADII[1] = r
        RECT_RADII[2] = 0f
        RECT_RADII[3] = 0f
        RECT_RADII[4] = 0f
        RECT_RADII[5] = 0f
        RECT_RADII[6] = r
        RECT_RADII[7] = r
        RECT_BAR.reset()
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW)
        FILL.color = role
        c.drawPath(RECT_BAR, FILL)
    }

    /** The Wi-Fi group moved into the top slot; the arcs themselves are unchanged. */
    private fun drawRectWifi(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val factor = a.wifiScaleFactor()
        c.withTranslation(
            IconGeometry.rectWifiOffsetX(factor),
            IconGeometry.rectWifiOffsetY(a.arcStroke.toFloat(), factor)
        ) {
            c.scale(IconGeometry.RECT_WIFI_SCALE * factor, IconGeometry.RECT_WIFI_SCALE * factor)
            drawWifiArcs(c, level, fg, a)
        }
    }

    private fun drawRectBolt(c: Canvas, color: Int, a: StatusAppearance) {
        c.withSave {
            val factor = a.boltScaleFactor()
            c.translate(IconGeometry.rectBoltOffsetX(factor), IconGeometry.rectBoltOffsetY(factor))
            c.scale(IconGeometry.BOLT_SCALE * factor, IconGeometry.BOLT_SCALE * factor)
            drawBoltShape(c, color, a)
        }
    }

    /**
     * The bolt's outline, in whatever local space the caller has scaled into.
     *
     * One place for the three arrangements and the four motion styles, so the ring, the
     * capsule and the rectangle cannot drift apart, and the still bolt stays the plain
     * filled path it always was. Every style loops for as long as the charger is
     * attached; a frame asks for the next one only while [StatusAppearance.animatesOutline]
     * says so, which is also the flag [boltMoving] carries.
     */
    private fun drawBoltShape(c: Canvas, color: Int, a: StatusAppearance) {
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

    /**
     * The network type in the top slot, between the dot columns.
     *
     * Sized against [IconGeometry.RECT_TYPE_SCALE] like the percentage is against its
     * own, so the two read as a pair, and shrunk by [fitSize] if the slot is too
     * narrow for it.
     */
    private fun drawRectType(c: Canvas, type: String?, fg: Int, a: StatusAppearance) {
        val requested = a.typeSize * IconGeometry.RECT_TYPE_SCALE
        applyWeight(a.typeWeight)
        val size = fitSize(type, requested, IconGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale)
        drawType(c, type, IconGeometry.RECT_VALUE_X, IconGeometry.RECT_TYPE_BASELINE, size, fg, a)
    }

    /** The percentage, in the middle of the arrangement and at its own scale. */
    /** The battery number as drawn: bare, or with the optional percent sign. */
    private fun valueText(level: Int, a: StatusAppearance): String =
        if (a.showPercent) "$level%" else level.toString()

    /**
     * The wattage as a slot piece: "9.6W", or empty while the reading is unknown.
     *
     * One formatter for both watt pieces - the charging and the running one differ in
     * *when* they appear (see [StatusAppearance]'s ink rules), never in what they say.
     */
    private fun powerText(): String? =
        if (StatusState.sChargePowerW.isNaN()) {
            null
        } else {
            String.format(Locale.US, "%.1fW", StatusState.sChargePowerW)
        }

    /** The display's refresh rate as a slot piece: "120", or empty before it is read. */
    private fun fpsText(): String? =
        if (StatusState.sFps <= 0) "120" else StatusState.sFps.toString()

    /** The one reading a capsule cell holds, which is one piece rather than a pair. */
    private fun capsulePerfText(part: Int, a: StatusAppearance): String? = when (part) {
        Prefs.PART_TEMP -> tempText()
        Prefs.PART_CURRENT -> currentText(a)
        Prefs.PART_FPS -> fpsText()
        else -> powerText()
    }

    /** The battery temperature as a slot piece: "32°C", or empty when it is unknown. */
    private fun tempText(): String? =
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
    private fun currentText(a: StatusAppearance): String? =
        StatusState.currentText(a.currentUnit, a.currentPositive)

    private fun drawRectValue(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val text = valueText(level, a)
        val requested = a.valueSize * IconGeometry.RECT_VALUE_SCALE
        applyWeight(a.valueWeight)
        val size = fitSize(text, requested, IconGeometry.RECT_TEXT_CLEAR)
        TEXT.textSize = size
        TEXT.color = fg
        c.drawText(text, IconGeometry.RECT_VALUE_X, IconGeometry.RECT_VALUE_BASELINE, TEXT)
    }

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
        if (level >= 0 && level < IconGeometry.CRITICAL_LEVEL) {
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

    private fun drawBattery(
        c: Canvas, level: Int, role: Int, fg: Int,
        a: StatusAppearance, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float, charging: Boolean,
        openLeft: Boolean = false, openRight: Boolean = false,
        topHalfDeg: Float = 0f, bottomHalfDeg: Float = 0f,
        leftFlankHalfDeg: Float = 0f, rightFlankHalfDeg: Float = 0f,
    ) {
        val ringStroke = a.ringStroke * IconGeometry.RING_STROKE_RATIO
        STROKE.strokeWidth = ringStroke
        val animated = a.animatesOutline(charging, level)
        // Breathing moves the whole ring; the other two move a light along it. One
        // vocabulary per style, so the outline and the bolt always read as one piece.
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f

        STROKE.color = withAlpha(fg, a.trackAlpha)
        batteryRing(
            c, 0f, 1f, gapStart, gapEnd, sweep, start, openLeft, openRight,
            leftFlankHalfDeg, rightFlankHalfDeg, topHalfDeg, bottomHalfDeg,
        )

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            batteryRing(
                c, 0f, min(1f, level / 100f), gapStart, gapEnd, sweep, start, openLeft, openRight,
                leftFlankHalfDeg, rightFlankHalfDeg, topHalfDeg, bottomHalfDeg,
            )
        }
        if (!animated) {
            return
        }
        val phase = animPhase()
        when (a.boltAnim) {
            Prefs.BOLT_ANIM_FLOW -> {
                // A light running the ring, in the charged colour lifted towards white.
                val head = flowHeadColor(role)
                for (i in 0 until RING_FLOW_SEGMENTS) {
                    val to = phase - i * RING_FLOW_SPAN
                    STROKE.color = withAlpha(head, flowAlpha(i, RING_FLOW_SEGMENTS))
                    flowArc(c, to - RING_FLOW_SPAN, to, gapStart, gapEnd, sweep, start)
                }
            }
            Prefs.BOLT_ANIM_SPARK -> {
                // Electric sparks with radiant white core, charging aura, and fading comet trail
                val radius = a.ringStroke * RING_SPARK_SCALE
                for (i in 0 until RING_SPARK_COUNT) {
                    val p = (phase + i / RING_SPARK_COUNT.toFloat()) % 1f
                    // Trailing sparks
                    for (tail in 2 downTo 1) {
                        val tp = (p - tail * 0.022f + 1f) % 1f
                        if (ringPoint(tp, gapStart, gapEnd, sweep, start)) {
                            FILL.color = withAlpha(role, if (tail == 1) 140 else 60)
                            c.drawCircle(RING_POINT[0], RING_POINT[1], radius * (if (tail == 1) 0.7f else 0.45f), FILL)
                        }
                    }
                    // Main spark with glowing aura and brilliant core
                    if (ringPoint(p, gapStart, gapEnd, sweep, start)) {
                        FILL.color = withAlpha(role, 120)
                        c.drawCircle(RING_POINT[0], RING_POINT[1], radius * 1.4f, FILL)
                        FILL.color = Color.WHITE
                        c.drawCircle(RING_POINT[0], RING_POINT[1], radius * 0.85f, FILL)
                    }
                }
            }
            else -> Unit
        }
    }

    private var sLastLightenRole = 0
    private var sCachedLightenHead = 0

    /** The charged colour lifted towards white by [FLOW_LIFT]; cached against [role] to avoid per-frame recomputation. */
    private fun flowHeadColor(role: Int): Int {
        if (role == sLastLightenRole) return sCachedLightenHead
        sLastLightenRole = role
        val a = (role ushr 24) and 0xFF
        val r = (role ushr 16) and 0xFF
        val g = (role ushr 8) and 0xFF
        val b = role and 0xFF
        val nr = r / 5 + 204
        val ng = g / 5 + 204
        val nb = b / 5 + 204
        val head = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        sCachedLightenHead = head
        return head
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
     * Where this frame sits in the loop, 0..1. One clock for the bolt and the outline.
     *
     * A fast charger halves the period rather than changing the drawing: the same
     * light, the same trail, twice the speed - which is the one difference the glyph
     * can show without a second colour or a second shape.
     */
    private fun animPhase(): Float {
        val base = if (quickCharge) QUICK_CYCLE_MS else BOLT_CYCLE_MS
        // Speed is a multiplier on the loop, not on the drawing: every piece reads this
        // phase, so scaling the period here moves all of them together. Clamped to a
        // minimum period so a runaway setting cannot ask for a frame per millisecond.
        val cycle = max(120L, (base / animSpeed).roundToInt().toLong())
        return (SystemClock.uptimeMillis() % cycle) / cycle.toFloat()
    }

    /** The breathing curve: no corner at the wrap, unlike a linear ramp. */
    private fun pulseWave(): Float {
        val phase = animPhase()
        return 0.35f + 0.65f * (0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
    }

    /**
     * The trail's alpha [i] steps behind its head, out of 255.
     *
     * Squared, not linear: a linear tail ends on a visible edge, and that edge is what
     * makes a running light read as a chain of dashes rather than as one comet.
     */
    private fun flowAlpha(i: Int, count: Int): Int {
        if (i == 0) return 210
        val t = 1f - i / count.toFloat()
        return (255f * t * t * 1.05f).toInt().coerceIn(0, 255)
    }

    /**
     * One segment of the ring's running light, in progress space that may run outside
     * 0..1.
     *
     * The light does not start over at the wrap: a segment that crosses the seam is
     * drawn as its two ends, so the stream is unbroken from one lap into the next. This
     * is what the old `if (from < 0f) continue` got wrong - every lap began with the
     * tail missing, which is the stutter that made the flow look broken.
     */
    private fun flowArc(
        c: Canvas, from: Float, to: Float, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float,
    ) {
        val a = from - floor(from)
        val b = a + (to - from)
        if (b <= 1f) {
            batteryRing(c, a, b, gapStart, gapEnd, sweep, start)
            return
        }
        batteryRing(c, a, 1f, gapStart, gapEnd, sweep, start)
        batteryRing(c, 0f, b - 1f, gapStart, gapEnd, sweep, start)
    }

    /**
     * The point on the drawn arc at visual progress [p], written into [RING_POINT].
     *
     * The mapping is the one [batteryRing] uses: progress counts only the ink, so the
     * mouth is stepped over rather than walked through.
     */
    private fun ringPoint(
        p: Float, gapStart: Float, gapEnd: Float, sweep: Float, start: Float,
    ): Boolean {
        if (p !in 0f..1f) return false
        val q = if (p <= gapStart) p else gapEnd + (p - gapStart)
        val angle = Math.toRadians((start + q * sweep).toDouble())
        RING_POINT[0] = (IconGeometry.B_CX + IconGeometry.B_R * kotlin.math.cos(angle)).toFloat()
        RING_POINT[1] = (IconGeometry.B_CY + IconGeometry.B_R * kotlin.math.sin(angle)).toFloat()
        return true
    }

    /**
     * Draws the open battery arc from progress [from] to [to], skipping the segment
     * hidden behind the top gap.
     *
     * Both ends are progress of the arc that is *actually drawn*, not of the full
     * path: the two segments the mouth leaves behind divide the level between them,
     * so 50% ends exactly where the left segment does and the mouth never consumes
     * any of it.
     *
     * [sweep]/[start] are passed in rather than read from [IconGeometry], because
     * the arc's angular span depends on whether anything occupies the bottom of the
     * ring this frame - see `bottomOpen`.
     */
    private fun batteryRing(
        c: Canvas, fromIn: Float, toIn: Float, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float,
        openLeft: Boolean = false, openRight: Boolean = false,
        leftFlankHalfDeg: Float = 0f, rightFlankHalfDeg: Float = 0f,
        topHalfDeg: Float = 0f, bottomHalfDeg: Float = 0f,
    ) {
        val from = clamp01(fromIn)
        val to = clamp01(toIn)
        if (to <= from) {
            return
        }
        // The split path only describes a mouth at 12 o'clock; a bottom mouth or a flank cut
        // needs the segment walk.
        if (!openLeft && !openRight && sweep >= 360f) {
            val mouthStart = clamp01(gapStart)
            val mouthEnd = clamp01(gapEnd)
            // Path length that actually carries ink; the level is spread over it.
            val drawn = mouthStart + (1f - mouthEnd)
            if (drawn <= 0f) {
                return
            }
            val visibleFrom = from * drawn
            val visibleTo = to * drawn
            // First segment: visible progress [0, mouthStart] is path [0, mouthStart].
            val firstEnd = min(visibleTo, mouthStart)
            if (firstEnd > visibleFrom) {
                arcByProgress(
                    c, visibleFrom, firstEnd, IconGeometry.B_CX, IconGeometry.B_CY,
                    IconGeometry.B_R, sweep, start,
                )
            }
            // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
            val secondFrom = max(visibleFrom, mouthStart)
            if (visibleTo > secondFrom) {
                arcByProgress(
                    c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    IconGeometry.B_CX, IconGeometry.B_CY, IconGeometry.B_R, sweep, start,
                )
            }
            return
        }

        // `SEGMENTS` is a scratch buffer reused by every call: this runs twice per drawn
        // frame, and the segment list used to be a List<Pair<Float,Float>> (plus its
        // filter copy) thrown away immediately.
        val segCount = buildRingSegments(
            SEGMENTS, GAPS, start, topHalfDeg, bottomHalfDeg, openLeft, openRight,
            leftFlankHalfDeg, rightFlankHalfDeg,
            Math.toDegrees((STROKE.strokeWidth / IconGeometry.B_R).toDouble()).toFloat(),
        )
        var totalDrawn = 0f
        for (i in 0 until segCount) totalDrawn += SEGMENTS[i * 2 + 1]
        if (totalDrawn <= 0f) return

        val startDrawn = from * totalDrawn
        val endDrawn = to * totalDrawn
        var curDist = 0f

        for (i in 0 until segCount) {
            val segStartDeg = SEGMENTS[i * 2]
            val segSweepDeg = SEGMENTS[i * 2 + 1]
            val segEndDist = curDist + segSweepDeg

            val drawStart = max(startDrawn, curDist)
            val drawEnd = min(endDrawn, segEndDist)

            if (drawEnd > drawStart) {
                val segDrawStartDeg = segStartDeg + (drawStart - curDist)
                val segDrawSweep = drawEnd - drawStart
                arc(c, IconGeometry.B_CX, IconGeometry.B_CY, IconGeometry.B_R, segDrawStartDeg, segDrawSweep)
            }
            curDist = segEndDist
        }
    }

    /**
     * Fills [out] with the ring's drawable segments as `(startDeg, sweepDeg)` pairs and
     * answers how many were written.
     *
     * The openings are collected as angular gaps and the arcs are their complement, so the
     * 12 and 6 o'clock mouths, the two flank cuts and any overlap between them are handled by
     * one rule instead of by hard-coded angles. Caller-owned buffers, because this runs for
     * the track and for the fill of every frame.
     */
    private fun buildRingSegments(
        out: FloatArray, gaps: FloatArray, arcStart: Float, topHalf: Float, bottomHalf: Float,
        openLeft: Boolean, openRight: Boolean,
        leftFlankHalf: Float, rightFlankHalf: Float, minSegDeg: Float,
    ): Int {
        var gapCount = 0
        if (topHalf > 0f) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 270f - topHalf, 270f + topHalf)
        }
        if (bottomHalf > 0f) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 90f - bottomHalf, 90f + bottomHalf)
        }
        if (openLeft) {
            gapCount = addRingGap(gaps, gapCount, arcStart, 180f - leftFlankHalf, 180f + leftFlankHalf)
        }
        if (openRight) {
            gapCount = addRingGap(gaps, gapCount, arcStart, -rightFlankHalf, rightFlankHalf)
        }
        // At most four gaps, so an insertion sort over the starts is the simplest thing that
        // is obviously correct.
        for (i in 1 until gapCount) {
            var j = i
            while (j > 0 && gaps[j * 2] < gaps[(j - 1) * 2]) {
                val s = gaps[j * 2]
                val e = gaps[j * 2 + 1]
                gaps[j * 2] = gaps[(j - 1) * 2]
                gaps[j * 2 + 1] = gaps[(j - 1) * 2 + 1]
                gaps[(j - 1) * 2] = s
                gaps[(j - 1) * 2 + 1] = e
                j--
            }
        }
        val lapEnd = arcStart + 360f
        var count = 0
        var cursor = arcStart
        for (i in 0 until gapCount) {
            val from = gaps[i * 2]
            val to = gaps[i * 2 + 1]
            if (from > cursor) {
                count = addSegment(out, count, cursor, from - cursor, minSegDeg)
            }
            if (to > cursor) cursor = to
        }
        if (cursor < lapEnd) {
            count = addSegment(out, count, cursor, lapEnd - cursor, minSegDeg)
        }
        return count
    }

    /**
     * Records one opening of the ring as a gap inside `[anchor, anchor + 360)`.
     *
     * @return the new gap count
     */
    private fun addRingGap(
        gaps: FloatArray, count: Int, anchor: Float, fromDeg: Float, toDeg: Float,
    ): Int {
        if (count * 2 + 1 >= gaps.size) return count
        var from = fromDeg
        while (from < anchor) from += 360f
        while (from >= anchor + 360f) from -= 360f
        val to = minOf(from + (toDeg - fromDeg), anchor + 360f)
        gaps[count * 2] = from
        gaps[count * 2 + 1] = to
        return count + 1
    }

    /**
     * Appends one segment unless it is too short to draw as a piece of the ring.
     *
     * A wide flank cut can leave a sliver between itself and the next opening. Shorter than
     * its own two round caps it draws as a blob rather than as arc, so the two openings are
     * allowed to merge instead of leaving a nub.
     */
    private fun addSegment(
        out: FloatArray, count: Int, startDeg: Float, sweepDeg: Float, minSegDeg: Float,
    ): Int {
        if (sweepDeg <= 0f || sweepDeg < minSegDeg) return count
        if (count * 2 + 1 >= out.size) return count
        out[count * 2] = startDeg
        out[count * 2 + 1] = sweepDeg
        return count + 1
    }

    private fun arcByProgress(
        c: Canvas, from: Float, to: Float, cx: Float, cy: Float, r: Float,
        sweep: Float, start: Float,
    ) {
        arc(c, cx, cy, r, start + from * sweep, (to - from) * sweep)
    }

    private fun arc(c: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
        ARC.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE)
    }

    /**
     * The arcs and the dot at their reference coordinates, redesigned referencing
     * CombinedStatus [RoundedWifiGeometry].
     *
     * 3-level structure matching MagicOS signal (0..3):
     * - Level 1: Dot only
     * - Level 2: Dot + middle arc
     * - Level 3: Dot + middle arc + outer arc
     *
     * Inactive levels are rendered using trackAlpha when trackAlpha > 0.
     */
    private fun drawWifiArcs(c: Canvas, level: Int, fg: Int, a: StatusAppearance) {
        val strokeWidth = min(a.arcStroke.toFloat(), IconGeometry.W_MAX_STROKE)
        STROKE.strokeWidth = strokeWidth
        val inactive = withAlpha(fg, a.trackAlpha)
        val hasTrack = a.trackAlpha > 0

        // Outer arc (level >= 3)
        if (level >= 3) {
            STROKE.color = fg
            c.drawPath(WIFI_OUTER, STROKE)
        } else if (hasTrack) {
            STROKE.color = inactive
            c.drawPath(WIFI_OUTER, STROKE)
        }

        // Middle arc (level >= 2)
        if (level >= 2) {
            STROKE.color = fg
            c.drawPath(WIFI_MIDDLE, STROKE)
        } else if (hasTrack) {
            STROKE.color = inactive
            c.drawPath(WIFI_MIDDLE, STROKE)
        }

        // Dot (level >= 1)
        if (level >= 1) {
            FILL.color = fg
            c.drawPath(WIFI_DOT, FILL)
        } else if (hasTrack) {
            FILL.color = inactive
            c.drawPath(WIFI_DOT, FILL)
        }
    }

    /**
     * Scratch for one capsule bar; rewritten every draw, never kept.
     *
     * The out-of-ring reading is painted on a view of its own rather than inside the
     * glyph's `onDraw`, but never reentrantly: it is one view per battery container,
     * and a container paints its children one at a time.
     */
    private val OUT_BAR = RectF()

    /** Scratch path for the out-of-ring activity arrows; same single-draw rule. */

    /**
     * Resolves the two readings the out-of-ring glyph draws: the capsule row, and
     * the dot row beneath it.
     *
     * Row one is the first SIM that answered and row two the second, which is also
     * why the dot row disappears when only one SIM answered. Four ascending bars is
     * what a lone reading looks like, so a single SIM must not be split across two
     * rows just to fill the space.
     *
     * [dataSimOnly] collapses the pair the other way: the reading that matters is
     * the data SIM's, and the other one is dropped whether or not it answered.
     *
     * When telephony never answered, the aggregate reading the glyph itself uses is
     * the last resort, so a missing subscription list does not leave the out-of-ring
     * glyph blank while the in-ring one still reads a level.
     *
     * @param out receives `{barLevel, dotLevel}`; a dot level of `-1` means "no
     *   second row", and a bar level of `-1` means "nothing to read"
     */
    fun outSignalLevels(
        dataSimOnly: Boolean, mobileLevel: Int, slotLevels: IntArray?,
        dataSlot: Int, sims: Int, out: IntArray,
    ) {
        var slot0 = -1
        var slot1 = -1
        if (slotLevels != null && slotLevels.size >= StatusState.SIM_SLOTS) {
            slot0 = slotLevels[0]
            slot1 = slotLevels[1]
        }
        val firstAnswered = if (slot0 >= 0) slot0 else slot1
        var bars: Int
        var dots = -1
        if (dataSimOnly) {
            bars = firstAnswered
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1
            }
        } else if (slot0 >= 0 && sims >= 2) {
            // The second card's row is drawn whenever that card exists, even unlit.
            bars = slot0
            dots = maxOf(0, slot1)
        } else {
            bars = firstAnswered
        }
        if (bars < 0) {
            bars = mobileLevel
        }
        out[0] = bars
        out[1] = dots
    }

    /**
     * One component, drawn alone and centred: the sample of a piece that has been handed
     * back to the system, so its sheet still has something to size and colour.
     *
     * Placed pieces preview inside the arrangement itself; only the released ones use
     * this, and they must not draw a ring they are no longer part of.
     *
     * @return whether the piece is one this can draw
     */
    fun drawPartOnly(
        c: Canvas, width: Int, height: Int, part: Int, wifiLevel: Int,
        fg: Int, a: StatusAppearance, type: String? = null,
    ): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }
        // A single part is previewed without a charger state, so it must not inherit the
        // speed of whatever frame was drawn last.
        quickCharge = false
        // A single piece is a still sample: it must not inherit the last frame's motion
        // or its speed either, or a cell could be caught mid-loop.
        boltMoving = false
        animSpeed = 1f
        if (part == Prefs.PART_WIFI) {
            val scale = min(
                height * 0.78f / IconGeometry.W_INK_H,
                width * 0.78f / IconGeometry.W_INK_W,
            ) * a.wifiScaleFactor()
            c.withTranslation(width / 2f, height / 2f) {
                c.scale(scale, scale)
                c.translate(-IconGeometry.W_CX, -IconGeometry.W_INK_CY)
                drawWifiArcs(c, wifiLevel, fg, a)
            }
            return true
        }
        if (part == Prefs.PART_FPS) {
            // Its own cell in the component library: one line of digits, at the reading's
            // own weight.
            val text = fpsText() ?: return false
            applyWeight(a.perfTypeWeight())
            TEXT.color = fg
            TEXT.textAlign = Paint.Align.CENTER
            var size = height * 0.62f
            TEXT.textSize = size
            val measured = TEXT.measureText(text)
            if (measured > width * 0.9f) {
                size *= width * 0.9f / measured
                TEXT.textSize = size
            }
            c.drawText(
                text, width / 2f,
                height / 2f + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_TYPE) {
            // Passed in rather than read from StatusState: the settings process never
            // receives a mobile type, so the sheet's own sample would be blank there.
            val text = type ?: return false
            if (text.isEmpty()) {
                return false
            }
            applyWeight(a.typeWeight)
            TEXT.color = fg
            TEXT.textAlign = Paint.Align.CENTER
            var size = height * 0.62f
            TEXT.textSize = size
            val measured = TEXT.measureText(text)
            if (measured > width * 0.9f) {
                size *= width * 0.9f / measured
                TEXT.textSize = size
            }
            c.drawText(
                text, width / 2f,
                height / 2f + (size * IconGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_CLOCK) {
            val text = StatusState.sClockText
            if (text.isEmpty()) {
                return false
            }
            val sample = StatusState.sClockBarText.ifEmpty { text }
            val dateStr = if (a.clockDate) StatusState.sClockDate else ""
            val wkStr = if (a.clockWeekday) StatusState.sClockWeekday else ""
            val lunarStr = if (a.clockLunar) StatusState.sClockLunar else ""
            val weatherStr = if (a.clockWeather) (StatusState.sWeather.ifEmpty { "雾 18°C" }) else ""
            drawPreviewClock(c, width, height, sample, dateStr, wkStr, lunarStr, weatherStr, a, fg)
            return true
        }
        return false
    }

    /** The clock sheet's sample: fitted as one block according to offsets and gaps. */
    private fun drawPreviewClock(
        c: Canvas, width: Int, height: Int, time: String,
        date: String, weekday: String, lunar: String, weather: String,
        a: StatusAppearance, fg: Int,
    ) {
        applyWeight(a.clockWeight)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val isDark = isDarkBackground(fg)
        val wkColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight
        val dtColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight
        val lnColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight
        val wtColor = if (isDark) a.clockWeatherColorOnDark else a.clockWeatherColorOnLight
        val wkDrawColor = if (wkColor != 0) wkColor else fg
        val dtDrawColor = if (dtColor != 0) dtColor else fg
        val lnDrawColor = if (lnColor != 0) lnColor else fg
        val wtDrawColor = if (wtColor != 0) wtColor else fg
        val inkBounds = TEXT_INK

        val isBalanced = a.clockStyle == Prefs.CLOCK_STYLE_BALANCED
        var timeSize = height * 0.45f * userScale
        var lineSize = timeSize * PREVIEW_DATE_SCALE
        val lunarGap = a.clockLunarGap.toFloat()
        val rowGap = a.clockRowGap.toFloat()
        val topOffsetX = a.clockTopOffsetX.toFloat()
        val bottomOffsetX = a.clockBottomOffsetX.toFloat()

        val previewCondition = if (weather.contains(" ")) weather.substringBefore(" ") else weather
        val previewTemp = if (weather.contains(" ")) weather.substringAfter(" ") else ""

        fun vectorWeather(): Boolean =
            a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON || a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON_TEXT

        fun symbolWeather(): Boolean =
            a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL || a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL_TEXT

        fun weatherLabel(): String {
            if (weather.isEmpty()) return ""
            if (a.clockWeatherStyle == Prefs.WEATHER_STYLE_ICON || a.clockWeatherStyle == Prefs.WEATHER_STYLE_SYMBOL) {
                return previewTemp.ifEmpty { weather }
            }
            return weather
        }

        fun weatherWidth(sz: Float): Float {
            val txt = weatherLabel()
            if (txt.isEmpty()) return 0f
            TEXT.textSize = sz
            val iconW = when {
                vectorWeather() -> sz * IconGeometry.WEATHER_ICON_SCALE + sz * 0.25f
                symbolWeather() -> TEXT.measureText(WeatherIconDrawer.glyphFor(previewCondition)) + sz * 0.25f
                else -> 0f
            }
            return iconW + TEXT.measureText(txt)
        }

        fun drawWeather(startX: Float, yBaseline: Float, sz: Float) {
            val txt = weatherLabel()
            if (txt.isEmpty()) return
            var x = startX
            TEXT.textSize = sz
            TEXT.getTextBounds("0", 0, 1, inkBounds)
            val inkCentre = yBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
            when {
                vectorWeather() -> {
                    val iconSz = sz * IconGeometry.WEATHER_ICON_SCALE
                    WeatherIconDrawer.drawWeatherIcon(
                        c, WeatherIconDrawer.parseWeatherType(previewCondition),
                        x, inkCentre - iconSz * 0.5f, iconSz, wtDrawColor,
                    )
                    x += iconSz + sz * 0.25f
                }
                symbolWeather() -> {
                    val glyph = WeatherIconDrawer.glyphFor(previewCondition)
                    TEXT.color = wtDrawColor
                    c.drawText(glyph, x, yBaseline, TEXT)
                    x += TEXT.measureText(glyph) + sz * 0.25f
                }
            }
            TEXT.color = wtDrawColor
            c.drawText(txt, x, yBaseline, TEXT)
        }

        if (isBalanced) {
            val step = lineSize * 0.28f
            val wordStep = lineSize * 0.16f
            val hasTop = date.isNotEmpty() || weekday.isNotEmpty() || weather.isNotEmpty()
            TEXT.textSize = lineSize
            val dtW = if (date.isEmpty()) 0f else TEXT.measureText(date)
            val wkW = if (weekday.isEmpty()) 0f else TEXT.measureText(weekday)
            val wtW = weatherWidth(lineSize)
            val topW = rowWidth(floatArrayOf(dtW, wkW, wtW), wordStep)

            val lnW = if (lunar.isEmpty()) 0f else TEXT.measureText(lunar)
            TEXT.textSize = timeSize
            val tmW = TEXT.measureText(time)
            val botW = rowWidth(floatArrayOf(lnW, tmW), step)

            val widest = max(topW, botW)
            val clear = width * 0.9f
            if (widest > clear) {
                val scale = clear / widest
                timeSize *= scale
                lineSize *= scale
            }
            val gap = rowGap
            TEXT.textSize = timeSize
            val timeInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            TEXT.textSize = lineSize
            val lineInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            val totalH = (if (hasTop) lineInk + gap else 0f) + timeInk
            var rowTop = height / 2f - totalH / 2f
            val cx = width / 2f

            if (hasTop) {
                TEXT.textAlign = Paint.Align.LEFT
                val baseline = rowTop - TEXT.fontMetrics.ascent
                var x = cx - topW / 2f + topOffsetX
                if (date.isNotEmpty()) {
                    TEXT.color = dtDrawColor
                    c.drawText(date, x, baseline, TEXT)
                    x += TEXT.measureText(date) + wordStep
                }
                if (weekday.isNotEmpty()) {
                    TEXT.color = wkDrawColor
                    c.drawText(weekday, x, baseline, TEXT)
                    x += TEXT.measureText(weekday) + wordStep
                }
                drawWeather(x, baseline, lineSize)
                rowTop += lineInk + gap
            }

            // Lunar then Time: one line, the time at its own size, the lunar centred on
            // the time's digits.
            TEXT.textSize = timeSize
            val timeBaseline = rowTop - TEXT.fontMetrics.ascent
            var botX = cx - botW / 2f + bottomOffsetX
            if (lunar.isNotEmpty()) {
                TEXT.getTextBounds(time, 0, time.length, inkBounds)
                val timeCentre = timeBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
                TEXT.textSize = lineSize
                TEXT.getTextBounds(lunar, 0, lunar.length, inkBounds)
                val lunarBaseline = timeCentre - (inkBounds.top + inkBounds.bottom) * 0.5f
                TEXT.color = lnDrawColor
                c.drawText(lunar, botX, lunarBaseline, TEXT)
                botX += TEXT.measureText(lunar) + step
            }
            TEXT.textSize = timeSize
            TEXT.color = fg
            drawTimeGlyphs(
                c, TEXT, time, botX, timeBaseline,
                if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight, fg,
            )
        } else {
            val step = lineSize * 0.2f
            val wordStep = lineSize * 0.16f
            TEXT.textSize = lineSize
            val wkW = if (weekday.isEmpty()) 0f else TEXT.measureText(weekday)
            val dtW = if (date.isEmpty()) 0f else TEXT.measureText(date)
            val effectiveLunarGap = if (date.isNotEmpty() && lunar.isNotEmpty()) max(lunarGap, wordStep) else 0f
            val lnW = if (lunar.isEmpty()) 0f else TEXT.measureText(lunar)
            val wtW = weatherWidth(lineSize)
            TEXT.textSize = timeSize
            val tmW = TEXT.measureText(time)
            val topW = rowWidth(floatArrayOf(tmW, wtW), step)
            var botW = dtW
            if (lnW > 0f) {
                if (botW > 0f) botW += effectiveLunarGap
                botW += lnW
            }
            if (wkW > 0f) {
                if (botW > 0f) botW += wordStep
                botW += wkW
            }
            val widest = max(topW, botW)
            val clear = width * 0.9f
            if (widest > clear) {
                val scale = clear / widest
                timeSize *= scale
                lineSize *= scale
            }
            val gap = rowGap
            TEXT.textSize = timeSize
            val timeInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            TEXT.textSize = lineSize
            val lineInk = TEXT.fontMetrics.let { it.descent - it.ascent }
            val hasBottom = date.isNotEmpty() || lunar.isNotEmpty() || weekday.isNotEmpty()
            val totalH = timeInk + (if (hasBottom) lineInk + gap else 0f)
            var top = height / 2f - totalH / 2f
            val cx = width / 2f

            // Row 1: Time + Weather, the caption centred on the time's ink.
            TEXT.textAlign = Paint.Align.LEFT
            var currX = cx - topW / 2f + topOffsetX
            val timeBaseline = top - TEXT.fontMetrics.ascent
            TEXT.textSize = timeSize
            TEXT.getTextBounds("0", 0, 1, inkBounds)
            val timeInkCentre = timeBaseline + (inkBounds.top + inkBounds.bottom) * 0.5f
            val label = weatherLabel()
            var captionBaseline = timeBaseline
            if (label.isNotEmpty()) {
                TEXT.textSize = lineSize
                TEXT.getTextBounds("0", 0, 1, inkBounds)
                captionBaseline = timeInkCentre - (inkBounds.top + inkBounds.bottom) * 0.5f
            }
            TEXT.textSize = timeSize
            TEXT.color = fg
            drawTimeGlyphs(
                c, TEXT, time, currX, timeBaseline,
                if (isDark) a.clockDigitColorsOnDark else a.clockDigitColorsOnLight, fg,
            )
            currX += TEXT.measureText(time) + step
            drawWeather(currX, captionBaseline, lineSize)

            // Row 2: Date + Lunar + Weekday.
            if (hasBottom) {
                top += timeInk + gap
                var botX = cx - botW / 2f + bottomOffsetX
                val yBaseline = top - TEXT.fontMetrics.ascent
                TEXT.textSize = lineSize
                if (date.isNotEmpty()) {
                    TEXT.color = dtDrawColor
                    c.drawText(date, botX, yBaseline, TEXT)
                    botX += TEXT.measureText(date) + (if (lunar.isNotEmpty()) effectiveLunarGap else wordStep)
                }
                if (lunar.isNotEmpty()) {
                    TEXT.color = lnDrawColor
                    c.drawText(lunar, botX, yBaseline, TEXT)
                    botX += TEXT.measureText(lunar) + wordStep
                }
                if (weekday.isNotEmpty()) {
                    TEXT.color = wkDrawColor
                    c.drawText(weekday, botX, yBaseline, TEXT)
                }
            }
        }
    }

    /** A row's ink width: measured parts plus one gap between each adjacent pair. */
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

    /** The preview's date line, as a share of the time's size. */
    private const val PREVIEW_DATE_SCALE = 0.74f

    /**
     * Whether the reading is drawn as the compact two-row style.
     *
     * Only a two-card reading has rows to stack. With one card this style has a
     * single row to work with, and its short, wide proportions read as a squat block
     * beside the dot style. A lone card therefore draws exactly the dot style's bars,
     * whichever style is selected: the styles differ in how they show the *second*
     * card, not in how they show the first.
     */
    fun rowsStyle(a: StatusAppearance?, dual: Boolean): Boolean =
        dual && a != null && a.stackStyle == Prefs.STACK_STYLE_ROWS

    /**
     * The pixel height of the reading's box at the user's [sizeDp], on a display of
     * the given [density].
     *
     * Deliberately independent of any view: the reading's size used to be a share of
     * the row it stood in, so the system laying that row out taller made the reading
     * grow with it. A dp is fixed.
     */
    /**
     * The inset a capsule cell keeps from its cap at 100% spacing, in design units.
     *
     * The spacing setting scales this; there is no second, fixed padding to keep in step
     * with it, and the value is not applied to the outline.
     */
    private const val CAP_CELL_PADDING = 6f

    private const val LONE_ROW_FILL = 0.74f
    private val ACTIVITY_UP = Path()
    private val ACTIVITY_DOWN = Path()

    /**
     * The pixel height of the reading's box at the user's [sizeDp], on a display of
     * the given [density].
     *
     * Deliberately independent of any view: the reading's size used to be a share of
     * the row it stood in, so the system laying that row out taller made the reading
     * grow with it. A dp is fixed.
     */
    fun outSignalHeight(a: StatusAppearance?, sizeDp: Int, density: Float, dual: Boolean): Int {
        if (sizeDp <= 0 || density <= 0f) {
            return 0
        }
        // Two rows and their gap cost height, so the bars get a boost at the same setting.
        if (rowsStyle(a, dual)) {
            return max(1, (sizeDp * density * 1.58f).roundToInt())
        }
        return max(1, (sizeDp * density).roundToInt())
    }

    /**
     * The pixel width of a reading [height] pixels tall, including the type labels.
     *
     * The label column is part of the reading, so the view has to be that much wider:
     * sizing it for the bars alone would clip the label off the left edge.
     */
    fun outSignalWidth(
        height: Int, a: StatusAppearance?, type: String?, type2: String?,
        dual: Boolean = true,
    ): Int {
        if (height <= 0) {
            return 0
        }
        val inkW = outSignalInkWidth(a, type, type2, dual)
        val inkH = outSignalInkHeight(a, dual)
        val fill = if (dual) 1f else LONE_ROW_FILL
        return max(1, (height * fill * inkW / inkH).roundToInt())
    }

    /**
     * Column pitch for a style.
     *
     * The two looks are not proportioned alike in the reference: the dot style's bars
     * are spaced by 0.85 of their width, the compact two-row style's by 1.56.
     */
    private fun outPitch(a: StatusAppearance?, dual: Boolean = true): Float {
        val rows = rowsStyle(a, dual)
        return if (rows) {
            // The compact style's own reference: 1.6dp bars on a 0.9dp gap across a
            // 6.2dp row, i.e. everything is a fixed fraction of the row height.
            IconGeometry.STACK_ROW_H * (ROW_BAR + ROW_GAP)
        } else {
            // The reference's own pitch: 39-unit bars on a 28-unit gap.
            IconGeometry.STACK_PITCH
        }
    }

    /** Columns per row: 4 (default) or 5, adapting proportions harmoniously. */
    private fun outColumns(a: StatusAppearance?): Int = a?.signalColumns?.coerceIn(4, 5) ?: 4

    private fun outBarHeights(columns: Int): FloatArray =
        if (columns == 5) IconGeometry.STACK_BAR_H_5 else IconGeometry.STACK_BAR_H

    /** Map signal level (0..4 or 0..5) to lit bar count according to column style. */
    private fun levelMapped(level: Int, columns: Int): Int {
        if (level <= 0) return 0
        if (columns == 5) {
            return when (level) {
                1 -> 1
                2 -> 2
                3 -> 4
                4 -> 5
                else -> level.coerceIn(0, 5)
            }
        }
        return level.coerceIn(0, 4)
    }

    private fun outBarWidth(a: StatusAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            IconGeometry.STACK_ROW_H * ROW_BAR
        } else {
            IconGeometry.STACK_BAR_W
        }

    private fun outLabelGap(a: StatusAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            IconGeometry.STACK_ROW_H * ROW_LABEL_GAP
        } else {
            IconGeometry.STACK_LABEL_GAP
        }

    /**
     * Proportions for the two-row style: a 1.6dp bar on a 0.9dp gap, on a 6.2dp row,
     * plus a 2dp label gap and a 7.5sp label.
     */
    private const val ROW_BAR = 0.34f
    private const val ROW_GAP = 0.18f
    private const val ROW_LABEL_GAP = 0.24f

    /**
     * The gap between the primary and secondary parts of the reading, in design units.
     */
    private fun rowGap(a: StatusAppearance, baseInk: Float): Float =
        IconGeometry.STACK_DOT_GAP * (a.outSignalMargin.coerceAtLeast(0) / Prefs.DEF_OUT_SIGNAL_MARGIN.toFloat())

    // --- DOTS STYLE CONSTANTS ---
    private const val DOTS_ACTIVITY_ARROW_W = 1.35f
    private const val DOTS_ACTIVITY_GAP = 0.6f
    private const val DOTS_ACTIVITY_GAP_SHARE = 0.25f
    /** Dedicated activity arrow height for single-card, independent from dual-card */
    private const val SINGLE_ACTIVITY_PAIR_H = 44f

    private fun dotsActivityBlockWidth(barW: Float): Float =
        barW * (DOTS_ACTIVITY_ARROW_W + DOTS_ACTIVITY_GAP)

    private fun rowsGap(a: StatusAppearance): Float =
        IconGeometry.STACK_ROW_H * 0.12f

    // --- ROWS STYLE CONSTANTS ---
    private const val ROWS_ACTIVITY_ARROW_W = 1.15f
    private const val ROWS_ACTIVITY_GAP = 0.5f
    private const val ROWS_ACTIVITY_GAP_SHARE = 0.22f

    private fun rowsActivityBlockWidth(barW: Float): Float =
        barW * (ROWS_ACTIVITY_ARROW_W + ROWS_ACTIVITY_GAP)

    /** Width of the bars alone, in design units: what the label can overhang. */
    fun outSignalBarsWidth(a: StatusAppearance?, dual: Boolean = true): Float {
        val barsW = outPitch(a, dual) * (outColumns(a) - 1) + outBarWidth(a, dual)
        val actW = if (a?.showMobileActivity == true) {
            if (rowsStyle(a, dual)) {
                rowsActivityBlockWidth(outBarWidth(a, dual))
            } else {
                dotsActivityBlockWidth(outBarWidth(a, dual))
            }
        } else 0f
        return barsW + actW
    }

    /** The reading's own width in design units, label column included. */
    fun outSignalInkWidth(
        a: StatusAppearance?, type: String?, type2: String?, dual: Boolean = true,
    ): Float {
        val bars = outSignalBarsWidth(a, dual)
        if (a == null || !a.labelInReading()) {
            return bars
        }
        if (labelAbove(a)) {
            // The label is centred over bar 1, so the box grows only by its left overhang.
            return bars + outLabelOverhang(a, type, type2, dual)
        }
        val size = aloneCaptionSize(a, dual)
        val second = if (dual) type2 else null
        val widest = max(
            labelWidth(type, size, a.typeSuffixScale),
            labelWidth(second, size, a.typeSuffixScale),
        )
        return if (widest <= 0f) bars else widest + outLabelGap(a, dual) + bars
    }

    /**
     * How far the caption hangs past the bars' left edge, in design units.
     *
     * Zero when the caption already fits over bar 1, which is the usual case: the bars
     * are as wide as the reading, and the caption only borrows from that width.
     */
    fun outLabelOverhang(
        a: StatusAppearance?, type: String?, type2: String?, dual: Boolean = true,
    ): Float {
        if (a == null || !labelAbove(a)) {
            return 0f
        }
        val size = outCaptionSize(a, dual)
        val second = if (dual) type2 else null
        val widest = max(
            labelWidth(type, size, a.typeSuffixScale),
            labelWidth(second, size, a.typeSuffixScale),
        )
        if (widest <= 0f) {
            return 0f
        }
        val barW = outBarWidth(a, dual)
        // The caption is NOT centred on bar 1: its 'G' is (see typeLabelXOverBar1), so the
        // ink reaches `prefix + half of the rest` left of bar 1's centre. Reserving half
        // the whole caption instead left the prefix hanging outside the box, where the
        // view clipped it - which only showed while the activity arrows were off, because
        // their width pushed the reading far enough right to hide the overflow.
        val leftExtent = max(
            labelLeftExtent(type, size, a.typeSuffixScale),
            labelLeftExtent(second, size, a.typeSuffixScale),
        )
        val leftOverhang = leftExtent - barW * 0.5f
        val actW = if (a.showMobileActivity) {
            if (rowsStyle(a, dual)) rowsActivityBlockWidth(barW) else dotsActivityBlockWidth(barW)
        } else 0f
        return max(0f, leftOverhang - actW)
    }

    fun outSignalInkHeight(a: StatusAppearance?, dual: Boolean = true): Float {
        if (a != null && rowsStyle(a, dual)) {
            val rowH = IconGeometry.STACK_ROW_H
            return rowH * 2f + rowsGap(a)
        }
        val dotD = IconGeometry.STACK_DOT_D * ((a?.outDotSize ?: 100) / 100f)
        val barsH = IconGeometry.STACK_BAR_H[IconGeometry.STACK_COLUMNS - 1]
        if (!dual) return barsH
        val gap = if (a != null) rowGap(a, barsH + dotD) else IconGeometry.STACK_DOT_GAP
        return barsH + gap + dotD
    }

    /**
     * Caption size in design units when the network type is shown beside the reading (ALONE mode).
     *
     * In ALONE mode, the baseline font size is 12dp on screen (by default), contrasting with
     * the 14dp default used when the caption is positioned directly above the signal bar (BESIDE mode).
     * It scales proportionally whenever the user adjusts the outTypeSize setting (12dp * outTypeSize / 14).
     */
    private fun aloneCaptionSize(a: StatusAppearance, dual: Boolean = true): Float {
        val totalDesignH = if (rowsStyle(a, dual)) {
            val rowH = IconGeometry.STACK_ROW_H
            rowH * 2f + rowsGap(a)
        } else {
            val columns = outColumns(a)
            val barHeights = outBarHeights(columns)
            val barsH = barHeights[columns - 1]
            val dotScale = a.outDotSize / 100f
            val baseDotD = if (columns == 5) 31f else IconGeometry.STACK_DOT_D
            val dotD = baseDotD * dotScale
            val gap = rowGap(a, barsH + dotD)
            barsH + gap + dotD
        }
        // Maps 14dp setting to visual 12dp size (12/14 scale ratio) so the default setting renders the ideal size
        val scaleFactor = a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat()
        return totalDesignH * 0.55f * scaleFactor
    }

    private fun outCaptionSize(a: StatusAppearance, dual: Boolean = true): Float =
        outSignalLabelSize(a, dual) * (a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat())

    private fun outSignalLabelSize(a: StatusAppearance, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            IconGeometry.STACK_ROW_H * 0.72f
        } else {
            IconGeometry.STACK_BAR_H[IconGeometry.STACK_COLUMNS - 1] * LABEL_TO_BAR
        }

    private const val LABEL_TO_BAR = 0.65f

    private fun labelAbove(a: StatusAppearance): Boolean =
        a.stackTypeShown() && a.stackLabel == Prefs.STACK_LABEL_BESIDE

    /**
     * One card's network type, in the reading's label column.
     *
     * Left-aligned on a shared edge and centred on its own row, so a two-card reading
     * shows both types in one column rather than one label floating beside the pair.
     * The size is the same one the BESIDE caption uses, so switching the option does not
     * change how large the type is.
     */
    private fun drawColumnLabel(
        c: Canvas, text: String?, left: Float, targetY: Float, scale: Float,
        fg: Int, a: StatusAppearance, dual: Boolean,
        align: Int = 0, // 0 = center on targetY, 1 = top edge on targetY, -1 = bottom edge on targetY
    ) {
        if (text.isNullOrEmpty() || scale <= 0f) {
            return
        }
        val size = aloneCaptionSize(a, dual) * scale
        if (size <= 0f) {
            return
        }
        applyWeight(max(a.typeWeight, LABEL_WEIGHT))
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
        val baseline = when (align) {
            1 -> targetY - TEXT_INK.top // top edge of drawn ink touches targetY
            -1 -> targetY - TEXT_INK.bottom // bottom edge of drawn ink touches targetY
            else -> targetY - (TEXT_INK.top + TEXT_INK.bottom) * 0.5f // center of drawn ink aligns with targetY
        }
        val ink = measureType(text, size, a.typeSuffixScale)
        drawType(c, text, left + ink * 0.5f, baseline, size, fg, a)
    }

    /**
     * How far a caption's ink reaches LEFT of the bar its 'G' is centred on.
     *
     * [labelWidth] is the caption's total width; the placement puts the 'G' - not the
     * caption's centre - over bar 1, so the prefix hangs that much further left.
     */
    private fun labelLeftExtent(text: String?, size: Float, suffixScale: Int): Float {
        if (text.isNullOrEmpty() || size <= 0f) {
            return 0f
        }
        val g = text.indexOf('G')
        val prefix = if (g > 0) text.substring(0, g) else ""
        val total = labelWidth(text, size, suffixScale)
        val prefixW = if (prefix.isEmpty()) 0f else {
            applyWeight(LABEL_WEIGHT)
            measureType(prefix, size, 100)
        }
        return prefixW + (total - prefixW) * 0.5f
    }

    fun labelWidth(text: String?, size: Float, suffixScale: Int = 100): Float {
        if (text.isNullOrEmpty() || size <= 0f) {
            return 0f
        }
        applyWeight(LABEL_WEIGHT)
        return measureType(text, size, suffixScale)
    }

    private const val LABEL_WEIGHT = 700

    /** Floor for the idle arrows: the track alpha alone renders them nearly invisible. */
    private const val ACTIVITY_IDLE_ALPHA = 165

    /**
     * Activity arrows for the DOTS style:
     * - Exactly spans the height of the first dual-dot (Bar 1 + Dot 1)
     * - Top of up arrow aligns with top of Bar 1
     * - Bottom of down arrow aligns with bottom of Dot 1
     */
    private fun drawDotsActivityArrows(
        c: Canvas, cx: Float, top: Float, bottom: Float, scale: Float,
        barWDesign: Float, fg: Int, inactive: Int, activeIn: Boolean, activeOut: Boolean,
    ) {
        val w = barWDesign * DOTS_ACTIVITY_ARROW_W * scale
        val pairH = bottom - top
        val gap = pairH * DOTS_ACTIVITY_GAP_SHARE
        val h = max(0f, (pairH - gap) * 0.5f)
        val halfW = w / 2f

        // Up arrow: top at `top` (aligned with top of Bar 1)
        val upTop = top
        val upBottom = upTop + h
        ACTIVITY_UP.reset()
        ACTIVITY_UP.moveTo(cx, upTop)
        ACTIVITY_UP.lineTo(cx - halfW, upBottom)
        ACTIVITY_UP.lineTo(cx + halfW, upBottom)
        ACTIVITY_UP.close()
        FILL.color = if (activeOut) fg else inactive
        c.drawPath(ACTIVITY_UP, FILL)

        // Down arrow: tip lands on `bottom` (aligned with bottom of Dot 1)
        val downTop = bottom - h
        val downTip = bottom + 0.5f
        ACTIVITY_DOWN.reset()
        ACTIVITY_DOWN.moveTo(cx, downTip)
        ACTIVITY_DOWN.lineTo(cx - halfW, downTop)
        ACTIVITY_DOWN.lineTo(cx + halfW, downTop)
        ACTIVITY_DOWN.close()
        FILL.color = if (activeIn) fg else inactive
        c.drawPath(ACTIVITY_DOWN, FILL)
    }

    /**
     * Activity arrows for the ROWS style:
     * - Bottom-aligned with the first signal bar (row1Baseline)
     */
    private fun drawRowsActivityArrows(
        c: Canvas, cx: Float, baseline: Float, bar1H: Float, scale: Float,
        barWDesign: Float, fg: Int, inactive: Int, activeIn: Boolean, activeOut: Boolean,
    ) {
        val w = barWDesign * ROWS_ACTIVITY_ARROW_W * 1.15f * scale
        val pairH = 66f * scale
        val gap = pairH * ROWS_ACTIVITY_GAP_SHARE
        val h = max(0f, (pairH - gap) * 0.5f)
        val halfW = w / 2f

        // Down arrow: bottom tip lands on baseline
        val downTip = baseline + 0.5f
        val downTop = baseline - h
        ACTIVITY_DOWN.reset()
        ACTIVITY_DOWN.moveTo(cx, downTip)
        ACTIVITY_DOWN.lineTo(cx - halfW, downTop)
        ACTIVITY_DOWN.lineTo(cx + halfW, downTop)
        ACTIVITY_DOWN.close()
        FILL.color = if (activeIn) fg else inactive
        c.drawPath(ACTIVITY_DOWN, FILL)

        // Up arrow: above down arrow
        val upBottom = downTop - gap
        val upTop = upBottom - h
        ACTIVITY_UP.reset()
        ACTIVITY_UP.moveTo(cx, upTop)
        ACTIVITY_UP.lineTo(cx - halfW, upBottom)
        ACTIVITY_UP.lineTo(cx + halfW, upBottom)
        ACTIVITY_UP.close()
        FILL.color = if (activeOut) fg else inactive
        c.drawPath(ACTIVITY_UP, FILL)
    }

    /**
     * The primary reading: 4 signal bars on top, 4 dots underneath for secondary SIM.
     * Completely decoupled from the two-row style.
     */
    /**
     * Shifts the caption so that the letter 'G' is centered directly over Bar 1.
     */
    private fun typeLabelXOverBar1(type: String?, bar1Center: Float, size: Float): Float {
        if (type.isNullOrEmpty()) return bar1Center
        val gIdx = type.indexOf('G')
        if (gIdx <= 0) return bar1Center
        val prefix = type.substring(0, gIdx)
        TEXT.textSize = size
        val prefixW = TEXT.measureText(prefix)
        return bar1Center - prefixW * 0.5f
    }

    fun drawOutSignal(
        c: Canvas, width: Int, height: Int, bars: Int, dots: Int,
        fg: Int, a: StatusAppearance,
        type: String? = null, type2: String? = null,
    ): Boolean {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false
        }
        if (rowsStyle(a, dots >= 0)) {
            return drawStackRows(c, width, height, bars, dots, fg, a, type, type2)
        }
        val dotRow = dots >= 0
        val columns = outColumns(a)
        val barHeights = outBarHeights(columns)
        val pitch = outPitch(a, dotRow)
        val barRun = outSignalBarsWidth(a, dotRow)
        val inkW = outSignalInkWidth(a, type, type2, dotRow)
        val labelW = inkW - barRun
        val dotScale = a.outDotSize / 100f
        val baseDotD = if (columns == 5) 31f else IconGeometry.STACK_DOT_D
        val dotD = baseDotD * dotScale
        val barsH = barHeights[columns - 1]
        val gap = if (dotRow) rowGap(a, barsH + dotD) else 0f
        val inkH = if (dotRow) barsH + gap + dotD else barsH
        val fitHeight = height * (if (dotRow) 1f else LONE_ROW_FILL)
        val scale = min(width / inkW, fitHeight / inkH)
        if (scale <= 0f) {
            return false
        }
        val x0 = (width - inkW * scale) / 2f
        val inkTop = (height - inkH * scale) / 2f
        val above = labelAbove(a)
        val barsOffset = if (above) outLabelOverhang(a, type, type2, dotRow) * scale else 0f
        val barWDesign = outBarWidth(a, dotRow)
        val barW = barWDesign * scale
        val actTotal = if (a.showMobileActivity) dotsActivityBlockWidth(barWDesign) * scale else 0f

        // Bars start after barsOffset plus actTotal
        // Where the bars start. BESIDE reserves the caption's overhang on the left; ALONE
        // puts the bars first and reserves the label column on the RIGHT, so the column
        // sits past the reading the way the standalone label used to.
        val barsX = when {
            above -> x0 + barsOffset + actTotal
            a.labelInReading() -> x0 + actTotal
            else -> x0 + labelW * scale + actTotal
        }
        val actCx = if (a.showMobileActivity) {
            barsX - barWDesign * (DOTS_ACTIVITY_GAP + DOTS_ACTIVITY_ARROW_W * 0.5f) * scale
        } else 0f
        val baseline = inkTop + barsH * scale

        // 1. Network type: letter 'G' aligned directly with the first signal bar (Bar 1)
        val bar1Center = barsX + barW * 0.5f
        if (above && !type.isNullOrEmpty()) {
            val capSize = outCaptionSize(a, dotRow) * scale
            val labelX = typeLabelXOverBar1(type, bar1Center, capSize)
            drawOutLabel(
                c, type, labelX,
                inkTop + 4f * scale, scale, fg, a, dual = dotRow, hang = true,
            )
        }

        // 2. The type column, when the setting asks for it beside the reading: one label
        // per card, each centred on its own row (bars above, dots below).
        if (!above && a.labelInReading()) {
            val colLeft = barsX + barRun * scale - actTotal + outLabelGap(a, dotRow) * scale
            val signalTop = baseline - barsH * scale
            val signalBottom = if (dotRow) baseline + (gap + dotD) * scale else baseline
            if (dotRow) {
                // Dual cards: Top label aligns top edge with signal top; bottom label aligns bottom edge with dot bottom
                drawColumnLabel(c, type, colLeft, signalTop, scale, fg, a, true, align = 1)
                drawColumnLabel(c, type2, colLeft, signalBottom, scale, fg, a, true, align = -1)
            } else {
                // Single card: center vertically on the signal cluster
                val midY = (signalTop + signalBottom) * 0.5f
                drawColumnLabel(c, type, colLeft, midY, scale, fg, a, false, align = 0)
            }
        }

        // 3. Activity arrows: independent parameters for dual-card vs single-card
        val actBottom = if (dotRow) baseline + (gap + dotD) * scale else baseline
        val actTop = if (dotRow) {
            // Dual card with dots: height matches Bar 1 + Dot 1
            baseline - barHeights[0] * scale
        } else {
            // Single card: dedicated independent height
            actBottom - SINGLE_ACTIVITY_PAIR_H * scale
        }
        if (a.showMobileActivity) {
            val actIn = StatusState.sDataActivity == 1 || StatusState.sDataActivity == 3
            val actOut = StatusState.sDataActivity == 2 || StatusState.sDataActivity == 3
            val actIdle = withAlpha(fg, maxOf(a.trackAlpha, ACTIVITY_IDLE_ALPHA))
            drawDotsActivityArrows(
                c, actCx, actTop, actBottom, scale, barWDesign,
                fg, actIdle, actIn, actOut,
            )
        }

        val radius = barW / 2f
        val dotR = dotD * scale / 2f
        val dotCy = baseline + (gap + dotD / 2f) * scale
        val inactive = withAlpha(fg, a.trackAlpha)
        for (i in 0 until columns) {
            val left = barsX + i * pitch * scale
            FILL.color = if (i < levelMapped(bars, columns)) fg else inactive
            OUT_BAR.set(
                left, baseline - barHeights[i] * scale,
                left + barW, baseline,
            )
            c.drawRoundRect(OUT_BAR, radius, radius, FILL)
            if (dotRow) {
                FILL.color = if (i < levelMapped(dots, columns)) fg else inactive
                c.drawCircle(left + radius, dotCy, dotR, FILL)
            }
        }
        return true
    }

    /**
     * The compact look: the second card as a second row of bars, not a dot row.
     * Completely decoupled from the dot style.
     */
    private fun drawStackRows(
        c: Canvas, width: Int, height: Int, bars: Int, dots: Int,
        fg: Int, a: StatusAppearance, type: String?, type2: String?,
    ): Boolean {
        if (bars < 0) {
            return false
        }
        val columns = outColumns(a)
        val barW = outBarWidth(a)
        val pitch = outPitch(a)
        val rowH = IconGeometry.STACK_ROW_H
        val dual = dots >= 0
        val above = labelAbove(a)
        val actTotal = if (a.showMobileActivity) rowsActivityBlockWidth(barW) else 0f
        val inkW = outSignalInkWidth(a, type, type2, dual)
        val gap = if (dual) rowsGap(a) else 0f
        val inkH = if (dual) rowH * 2f + gap else rowH
        val scale = min(width / inkW, height.toFloat() / inkH)
        if (scale <= 0f) {
            return false
        }
        val x0 = (width - inkW * scale) / 2f
        val top = (height - inkH * scale) / 2f
        val barsOffset = if (above) outLabelOverhang(a, type, type2, dual) * scale else 0f
        val barWpx = barW * scale
        val barsX = x0 + barsOffset + actTotal * scale
        val actCx = barsX - barW * (ROWS_ACTIVITY_GAP + ROWS_ACTIVITY_ARROW_W * 0.5f) * scale
        val inactive = withAlpha(fg, a.trackAlpha)
        val actIn = StatusState.sDataActivity == 1 || StatusState.sDataActivity == 3
        val actOut = StatusState.sDataActivity == 2 || StatusState.sDataActivity == 3

        // Row 1 (SIM 1)
        val row1Baseline = top + rowH * scale
        val bar1H = (rowH / columns) * scale

        // 1. Network type: letter 'G' aligned directly with the first signal bar (Bar 1)
        val bar1Center = barsX + barWpx * 0.5f
        if (above && !type.isNullOrEmpty()) {
            val capSize = outCaptionSize(a, dual) * scale
            val labelX = typeLabelXOverBar1(type, bar1Center, capSize)
            drawOutLabel(
                c, type, labelX,
                top + 10f * scale, scale, fg, a, dual = true, hang = true,
            )
        } else if (!above && a.labelInReading()) {
            val colLeft = barsX + (pitch * (columns - 1) + barW) * scale +
                outLabelGap(a, true) * scale
            val signalTop = top
            val signalBottom = if (dual) top + (rowH * 2f + gap) * scale else top + rowH * scale
            if (dual) {
                // Dual cards: Top label aligns top edge with Row 1 top; bottom label aligns bottom edge with Row 2 bottom
                drawColumnLabel(c, type, colLeft, signalTop, scale, fg, a, true, align = 1)
                drawColumnLabel(c, type2, colLeft, signalBottom, scale, fg, a, true, align = -1)
            } else {
                // Single card: center vertically on the row
                val midY = (signalTop + signalBottom) * 0.5f
                drawColumnLabel(c, type, colLeft, midY, scale, fg, a, false, align = 0)
            }
        }

        // 2. Activity icon: to the left of Bar 1, bottom-aligned with Bar 1
        if (a.showMobileActivity) {
            drawRowsActivityArrows(
                c, actCx, row1Baseline, bar1H, scale, barW, fg,
                withAlpha(fg, maxOf(a.trackAlpha, ACTIVITY_IDLE_ALPHA)), actIn, actOut,
            )
        }

        // 3. Draw Row 1 bars
        drawRowBars(
            c, levelMapped(bars, columns), barsX, top, rowH * scale, columns,
            barWpx, pitch * scale, fg, inactive,
        )

        // Row 2 (SIM 2)
        if (dual) {
            val secondTop = top + (rowH + gap) * scale
            // Network type of Row 2: letter 'G' aligned directly with Bar 1 of Row 2
            if (above && !type2.isNullOrEmpty()) {
                val capSize = outCaptionSize(a, true) * scale
                val labelX2 = typeLabelXOverBar1(type2, bar1Center, capSize)
                drawOutLabel(
                    c, type2, labelX2,
                    secondTop + 10f * scale, scale, fg, a, dual = true, hang = true,
                )
            }
            drawRowBars(
                c, levelMapped(max(0, dots), columns), barsX, secondTop, rowH * scale, columns,
                barWpx, pitch * scale, fg, inactive,
            )
        }
        return true
    }

    /** One row of the compact style, with four steps matching signal meter levels. */
    private fun drawRowBars(
        c: Canvas, lit: Int, x0: Float, top: Float, rowH: Float, columns: Int,
        barW: Float, pitch: Float, fg: Int, inactive: Int,
    ) {
        val radius = barW / 2f
        val base = top + rowH
        for (i in 0 until columns) {
            val h = rowH * (i + 1) / columns
            val left = x0 + i * pitch
            OUT_BAR.set(left, base - h, left + barW, base)
            FILL.color = if (i < lit) fg else inactive
            c.drawRoundRect(OUT_BAR, radius, radius, FILL)
        }
    }

    /** The caption, centred on [x], sitting entirely above [clearTop]. */
    private fun drawOutLabel(
        c: Canvas, text: String, x: Float, edge: Float, scale: Float,
        fg: Int, a: StatusAppearance, dual: Boolean = true, hang: Boolean = false,
    ) {
        val size = outCaptionSize(a, dual) * scale
        // Small bold label: enough weight to stay legible at 6sp.
        applyWeight(max(a.typeWeight, LABEL_WEIGHT))
        TEXT.textSize = size
        TEXT.color = fg
        // `hang` puts the caption's DRAWN top on `edge`; otherwise its baseline sits a gap
        // above it. The anchor is the ink box from [Paint.getTextBounds], not the font
        // metrics: metrics reserve the face's whole leading above the capitals, so hanging
        // by them dropped the visible glyph a fifth of an em below the top it was meant to
        // touch - which is exactly the "still too low" this caption kept reading as.
        val baseline = if (hang) {
            TEXT.getTextBounds(text, 0, text.length, TEXT_INK)
            edge - TEXT_INK.top
        } else {
            edge - (TEXT.fontMetrics.ascent + TEXT.fontMetrics.descent) / 2f
        }
        // Through [drawType], so a trailing "A" shrinks instead of running into the bars.
        drawType(c, text, x, baseline, size, fg, a)
    }

    /**
     * Installs [weight] on [TEXT], skipping the work when that weight is already the
     * installed one. Every draw runs inside onDraw, so the cached comparison keeps a
     * per-frame [Typeface] allocation off the hot path.
     */
    private fun applyWeight(weight: Int) {
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
    private fun drawType(
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
    private fun fitSize(text: String?, requested: Float, clearWidth: Float): Float =
        fitSize(text, requested, clearWidth, 100)

    /**
     * The same fit, measuring the label the way [drawType] will lay it out: a shrunk
     * trailing "A" carries only [scalePercent] of the main size, so it takes less
     * room and the main run is left correspondingly larger.
     */
    private fun fitSize(
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
    private fun measureType(text: String?, size: Float, scalePercent: Int): Float {
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

    private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    /**
     * Pixels per design unit for a host of this size: the uniform scale that fits
     * the 120x120 design box into the view, i.e. the same factor [drawInto] applies
     * to its canvas.
     */
    fun inkScale(w: Int, h: Int): Float =
        inkScale(w, h, IconGeometry.INK_W, IconGeometry.INK_H)

    /**
     * The fit for whichever arrangement is selected. The settings preview draws its
     * own label beside the glyph, so it has to know the same factor [drawInto] will
     * use - and that depends on the style.
     */
    fun inkScale(w: Int, h: Int, rect: Boolean): Float =
        if (rect) {
            inkScale(w, h, IconGeometry.RECT_INK_W, IconGeometry.RECT_INK_H)
        } else {
            inkScale(w, h)
        }

    /**
     * The same fit, chosen by the stored style.
     *
     * Distinct from the boolean overload above because the capsule is a third ink box
     * rather than a variant of one of the other two.
     */
    fun inkScale(w: Int, h: Int, appearance: StatusAppearance?): Float {
        if (appearance?.rect == true) {
            return inkScale(w, h, IconGeometry.RECT_INK_W, IconGeometry.RECT_INK_H)
        }
        if (appearance?.capsule == true) {
            // The banded box: the capsule plus the strip either opening draws in. Its width
            // follows the user's extra run, which is why this takes the frame's answer
            // rather than the stored style id.
            return inkScale(
                w, h, IconGeometry.capInkW(appearance),
                IconGeometry.CAP_INK_BOTTOM - IconGeometry.CAP_INK_TOP,
            )
        }
        if (appearance?.ring == true) {
            val extraW = maxOf(0f, appearance.ringSpacingX.toFloat() * 2f)
            return inkScale(
                w, h, IconGeometry.INK_W + extraW, IconGeometry.ringInkH(appearance),
            )
        }
        return inkScale(w, h)
    }

    /**
     * The same fit for an arbitrary ink box. The two arrangements have different ink
     * extents, so the scale that fits one does not fit the other.
     */
    fun inkScale(w: Int, h: Int, inkW: Float, inkH: Float): Float {
        if (w <= 0 || h <= 0) {
            return 0f
        }
        return min(w / inkW, h / inkH)
    }

    /**
     * The concrete mapping from the 120x120 design box onto a canvas of this size, as
     * `{scale, translateX, translateY}`.
     *
     * This is the whole point of the method existing separately: the drawing path and
     * the settings screen's tappable zones must agree about where a design-space
     * point lands, or a hot spot ends up beside the thing it names.
     *
     * Callers apply it in this order, exactly as the canvas does: translate by the
     * returned offsets, then scale by the returned factor. Canonically
     * `device = design * scale + translate`.
     *
     * @param out receives `{scale, translateX, translateY}`; untouched when the
     *   method returns false
     * @return false when there is nothing to draw into, with [out] left alone so a
     *   caller cannot accidentally use a stale transform
     */
    fun designTransform(w: Int, h: Int, appearance: StatusAppearance?, out: FloatArray?): Boolean {
        if (w <= 0 || h <= 0 || appearance == null || out == null || out.size < 3) {
            return false
        }
        val rect = appearance.rect
        // The overall-size setting is applied to the fit rather than to the design units.
        val scale = inkScale(w, h, appearance) * appearance.glyphFit()
        if (scale <= 0f) {
            return false
        }
        out[0] = scale
        if (appearance.capsule) {
            val boxH = IconGeometry.CAP_INK_BOTTOM - IconGeometry.CAP_INK_TOP
            out[1] = (w - IconGeometry.capInkW(appearance) * scale) / 2f - IconGeometry.capInkX(appearance) * scale
            out[2] = (h - boxH * scale) / 2f - IconGeometry.CAP_INK_TOP * scale
        } else if (rect) {
            out[1] = (w - IconGeometry.RECT_INK_W * scale) / 2f - IconGeometry.RECT_INK_X * scale
            out[2] = (h - IconGeometry.RECT_INK_H * scale) / 2f - IconGeometry.RECT_INK_Y * scale
        } else {
            val hasBottomStacked = !appearance.dataSimOnly && ((appearance.ringZones.getOrNull(2)?.size ?: 0) > 1 ||
                (appearance.ringZones.getOrNull(2)?.contains(Prefs.PART_MOBILE) == true && appearance.dualSim))
            val stackShiftY = if (hasBottomStacked) 2.0f * scale else 0f
            val inkH = IconGeometry.ringInkH(appearance)
            val inkTop = IconGeometry.ringInkTop(appearance)
            out[1] = (w - IconGeometry.INK_W * scale) / 2f - IconGeometry.INK_X * scale
            out[2] = (h - inkH * scale) / 2f - inkTop * scale - stackShiftY
        }
        return true
    }

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
