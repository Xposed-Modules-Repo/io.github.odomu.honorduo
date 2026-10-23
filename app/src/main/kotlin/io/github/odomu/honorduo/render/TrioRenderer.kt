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

package io.github.odomu.honorduo.render

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
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioSettings
import io.github.odomu.honorduo.model.TrioState
import io.github.odomu.honorduo.render.TrioRenderer.RING_POINT
import io.github.odomu.honorduo.render.TrioRenderer.TEXT
import io.github.odomu.honorduo.render.TrioRenderer.animPhase
import io.github.odomu.honorduo.render.TrioRenderer.batteryRing
import io.github.odomu.honorduo.render.TrioRenderer.boltMoving
import io.github.odomu.honorduo.render.TrioRenderer.drawType
import io.github.odomu.honorduo.render.TrioRenderer.fitSize
import io.github.odomu.honorduo.render.TrioRenderer.quickCharge
import io.github.odomu.honorduo.render.TrioRenderer.textInkHeight
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the "trio" glyph (battery ring + Wi-Fi arcs + signal dots + level indicator)
 * into a host view's canvas, in the 120x120 design space of [TrioGeometry].
 */
object TrioRenderer {

    private val STROKE = Paint(Paint.ANTI_ALIAS_FLAG)
    private val FILL = Paint(Paint.ANTI_ALIAS_FLAG)
    private val TEXT = Paint(Paint.ANTI_ALIAS_FLAG)
    private val BOLT = buildBolt()
    private val WIFI_PATHS = TrioGeometry.buildWifiPaths()
    private val WIFI_DOT = WIFI_PATHS[0]
    private val WIFI_MIDDLE = WIFI_PATHS[1]
    private val WIFI_OUTER = WIFI_PATHS[2]
    private val ARC = RectF()
    /**
     * The bolt path's own width.
     *
     * The icon is fitted by HEIGHT (TrioGeometry.capBoltScale), so its width follows from
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

    /** One point on the battery arc, rewritten per frame; never kept. */
    private val RING_POINT = FloatArray(2)

    /** One travelling segment of the capsule's outline; rewritten per segment. */
    private val FLOW_PATH = Path()

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
        TEXT.letterSpacing = TrioGeometry.VALUE_LETTER_SPACING
    }

    internal fun draw(canvas: Canvas, host: View, s: TrioState) {
        draw(canvas, host, s, TrioConfig.get())
    }

    /**
     * Draws the glyph with the user's current settings. The text size is applied
     * here rather than in the static initialiser so a live settings change is
     * picked up by the very next frame.
     */
    internal fun draw(canvas: Canvas, host: View, s: TrioState, cfg: TrioSettings) {
        drawState(canvas, host.width, host.height, s, cfg)
    }

    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure - [GlyphView] hands the icon view's own bounds through, and
     * the slot it paints into can be asked for nothing else.
     */
    internal fun drawState(canvas: Canvas, w: Int, h: Int, s: TrioState?, cfg: TrioSettings?) {
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
        fg: Int, cfg: TrioSettings,
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
        fg: Int, cfg: TrioSettings?, clearWhenDone: Boolean,
    ) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return
        }
        // The frame's speed and motion are settled here, before any of the moving pieces
        // ask for the phase, so the outline and the bolt cannot end up on different
        // clocks or at different speeds.
        quickCharge = quickCharging
        // Resolve what is on screen once, here, from the single rule source.
        val appearance = TrioAppearance(cfg)
        // The network type steps aside while Wi-Fi is up. Decided HERE rather than in
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
        val transform = FloatArray(3)
        if (!designTransform(w, h, appearance, transform)) {
            return
        }
        val scale = transform[0]
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
            if (slotLevels != null && slotLevels.size >= TrioState.SIM_SLOTS) slotLevels else null
        // Only the two real slots count; a longer array is not a third card.
        val sims = if (slots == null) {
            0
        } else {
            (if (slots[0] >= 0) 1 else 0) + (if (slots[1] >= 0) 1 else 0)
        }

        canvas.withTranslation(transform[1], transform[2]) {
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
     * Which piece goes where is not decided here - [TrioAppearance.Ring] has already
     * resolved the three slots. This method only draws.
     */
    private fun drawRingLayout(
        canvas: Canvas, level: Int, charging: Boolean,
        quickCharging: Boolean, wifiLevel: Int, mobileLevel: Int,
        slotLevels: IntArray?, sims: Int, mobileType: String?, role: Int,
        fg: Int, a: TrioAppearance,
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
        a: TrioAppearance, r: TrioAppearance.Ring,
    ) {
        drawBattery(c, level, role, fg, a, r.gapStart(), r.gapEnd(), r.sweep(), r.arcStart(), charging)
        for (z in 0 until 3) {
            val part = r.parts[z]
            if (part == Prefs.PART_NONE) continue
            val cx = TrioGeometry.B_CX
            val cy = TrioGeometry.ZONE_CY[z]
            // Every piece is fitted to this box, and the box was the zone's full extent:
            // a piece at its ceiling touched the ring's inner face. The inset is what
            // leaves the margin the shape needs to read as a bezel rather than a frame.
            val width = 78f - a.ringStroke - 2f * RING_SLOT_INSET
            val height = when (z) {
                0 -> TrioGeometry.DOT_BOX_TOP
                1 -> TrioGeometry.DOT_BOX_CENTRE
                else -> TrioGeometry.DOT_BOX_BOTTOM
            }
            c.withTranslation(cx, cy) {
                if (part == Prefs.PART_WIFI) {
                    // The reference bounds include the default stroke. Extra thickness
                    // must count before scaling, otherwise the arcs escape their slot.
                    val extra = max(0f, a.arcStroke - TrioGeometry.W_STROKE)
                    // Fitted to the band, then enlarged: the reference's icon is bigger than a third.
                    val scale = min(
                        width / (TrioGeometry.W_INK_W + extra),
                        height / (TrioGeometry.W_INK_H + extra),
                    ) * RING_WIFI_BOOST * a.wifiScaleFactor()
                    c.scale(scale, scale)
                    c.translate(-TrioGeometry.W_CX, -TrioGeometry.W_INK_CY)
                    drawWifiArcs(c, wifiLevel, fg, a)
                } else if (part == Prefs.PART_BOLT) {
                    // The bolt is fitted to its slot, then multiplied by the user's size. The product is NOT re-clamped to the slot.
                    val fit = min(width / 15f, height / TrioGeometry.BOLT_INK_H)
                    val scale = fit * a.boltScaleFactor()
                    c.scale(scale, scale)
                    c.translate(-TrioGeometry.BOLT_CX_REF, -TrioGeometry.BOLT_INK_CY)
                    drawBoltShape(
                        c, if (quickCharging) TrioGeometry.QUICK_CHARGE else fg, a,
                    )
                } else if (part == Prefs.PART_MOBILE ||
                    part == Prefs.PART_SIM_1 || part == Prefs.PART_SIM_2
                ) {
                    // All three are the same drawing: one row of four level beads.
                    val rowLevel = when (part) {
                        Prefs.PART_MOBILE -> mobileLevel
                        else -> levels?.getOrNull(if (part == Prefs.PART_SIM_1) 0 else 1) ?: -1
                    }
                    val active = if (rowLevel < 0) 0 else min(4, rowLevel)
                    // The user's size multiplies the reference bead.
                    drawArcDotRow(
                        c, active, z,
                        TrioGeometry.arcRowDotR(z, a.ringStroke, width, a.dotScaleFactor()),
                        fg, a,
                    )
                } else {
                    val text = when (part) {
                        Prefs.PART_VALUE -> valueText(level, a)
                        Prefs.PART_TYPE -> type
                        Prefs.PART_POWER -> powerText()
                        Prefs.PART_RUNNING -> powerText()
                        Prefs.PART_TEMP -> tempText()
                        Prefs.PART_FPS -> fpsText()
                        else -> TrioState.sClockText
                    }
                    if (!text.isNullOrEmpty()) {
                        val weight = when (part) {
                            Prefs.PART_VALUE -> a.valueWeight
                            Prefs.PART_TYPE -> a.typeWeight
                            Prefs.PART_POWER, Prefs.PART_RUNNING,
                            Prefs.PART_TEMP, Prefs.PART_FPS,
                            -> a.perfTypeWeight()
                            else -> a.clockWeight
                        }
                        val requested = when (part) {
                            Prefs.PART_VALUE -> a.valueSize
                            Prefs.PART_TYPE -> a.typeSize
                            Prefs.PART_POWER, Prefs.PART_RUNNING,
                            Prefs.PART_TEMP, Prefs.PART_FPS,
                            -> a.perfReadingSize()
                            else -> a.clockSize
                        }.toFloat()
                        applyWeight(weight)
                        TEXT.textSize = requested
                        // Type is fitted to the THIRD it sits in, not to the small placement box that positions a drawn figure (28/44/20 units).
                        val room = TrioGeometry.zoneTextRoomH(z) - 2f * RING_SLOT_INSET
                        val suffixScale = if (part == Prefs.PART_TYPE) a.typeSuffixScale else 100
                        // The height cap is a straight ratio at the requested size.
                        val inkAtRequested = max(1f, textInkHeight(text, requested))
                        val maxByHeight = requested * room / inkAtRequested
                        // The width cap is NOT independent of the answer.
                        var fitted = maxByHeight
                        for (pass in 0 until 4) {
                            val inkH = max(1f, textInkHeight(text, fitted))
                            val maxByWidth = fitSize(
                                text, requested,
                                TrioGeometry.zoneTextRoomW(z, a.ringStroke, inkH) -
                                    2f * RING_SLOT_INSET,
                                suffixScale,
                            )
                            val next = min(maxByHeight, maxByWidth)
                            if (abs(next - fitted) < 0.05f) {
                                fitted = next
                                break
                            }
                            fitted = next
                        }
                        // Centred in the band, and on its INK rather than its metrics.
                        val slotCentre = TrioGeometry.zoneTextCy(z, a.ringStroke) -
                                TrioGeometry.ZONE_CY[z]
                        val baseline = inkCentredBaseline(text, fitted, slotCentre)
                        if (part == Prefs.PART_TYPE) {
                            drawType(c, text, 0f, baseline, fitted, fg, a)
                        } else {
                            TEXT.textAlign = Paint.Align.CENTER
                            TEXT.textSize = fitted
                            val tempIsHigh = part == Prefs.PART_TEMP &&
                                TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN &&
                                TrioState.sBatteryTempC >= a.tempThreshold
                            TEXT.color = if (tempIsHigh) (if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight) else fg
                            c.drawText(text, 0f, baseline, TEXT)
                        }
                    }
                }
            }
        }
    }

    /**
     * One card's four level dots, laid along the ring's own curve.
     *
     * Drawn on the arc that passes through the slot, concentric with the battery
     * ring, rather than on a horizontal line. The shipped level dots already bend
     * (`TrioGeometry.DOTS` is 104.2, 111.2, 111.7, 105.8 - lowest in the middle),
     * and a placed card that drew a straight row would read as a foreign shape
     * inside a round glyph.
     *
     * Which way the curve bends depends on where the third sits. A row in the bottom
     * third belongs to the ring's lower opening and bows *downward*, matching
     * `DOTS`; a row in the top third belongs to the 12 o'clock opening and bows the
     * other way, so it stays concentric instead of dipping toward the ring centre.
     * The middle third has no opening of its own, so its row follows the ring's side
     * curvature and bows toward the centre.
     *
     * The sagitta is derived from the arc's radius rather than picked: for a chord of
     * half-width `w` on a circle of radius `R`, the rise is `R - sqrt(R^2 - w^2)`.
     *
     * @param active how many of the four dots are lit, 0..4
     * @param zone `0` top, `1` middle, `2` bottom
     * @param radius dot radius, already fitted to the slot
     */
    private fun drawArcDotRow(
        c: Canvas, active: Int, zone: Int, radius: Float,
        fg: Int, a: TrioAppearance,
    ) {
        val inactive = withAlpha(fg, a.trackAlpha)
        if (zone == 1) {
            // The middle third is not a notch: the ring runs past it on both sides, so there is no bare arc to thread beads onto.
            val step = radius * 3f
            for (dot in 0 until 4) {
                FILL.color = if (dot < active) fg else inactive
                c.drawCircle((dot - 1.5f) * step, 0f, radius, FILL)
            }
            return
        }
        // In the top and bottom thirds the dots are threaded onto the arc's own centre line, inside the notch where the ring carries no ink - so a bead as thick as the ring reads as the outline cut into segments.
        val rho = TrioGeometry.B_R
        // The bare arc this row sits in: the 12 o'clock notch above, the ring's own
        // opening below. Both are GAP_WIDTH_DEG wide.
        val openingRad = Math.toRadians(TrioGeometry.GAP_WIDTH_DEG.toDouble()).toFloat()
        // Angular inset for the arc's cap plus the end bead's own radius, and a further
        // unit so the two antialiased edges do not meet.
        val inset = (radius + a.ringStroke * 0.5f + 1f) / rho
        val halfAngle = max(0f, openingRad * 0.5f - inset)
        // The row keeps its own span and does NOT grow with the opening.
        val beadHalfAngle = Math.toRadians((TrioGeometry.BEAD_SPAN_DEG * 0.5f).toDouble())
            .toFloat()
        val halfCentreAngle = min(halfAngle, beadHalfAngle)
        for (dot in 0 until 4) {
            val t = (dot - 1.5f) / 1.5f
            val angle = t * halfCentreAngle
            // Top third: the notch straddles 12 o'clock, above the ring centre.
            // Bottom third: the opening straddles 6 o'clock, below it.
            val sign = if (zone == 2) 1f else -1f
            val ringX = TrioGeometry.B_CX + rho * sin(angle)
            val ringY = TrioGeometry.B_CY + sign * rho * cos(angle)
            FILL.color = if (dot < active) fg else inactive
            c.drawCircle(
                ringX - TrioGeometry.B_CX,
                ringY - TrioGeometry.ZONE_CY[zone], radius, FILL,
            )
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
        fg: Int, a: TrioAppearance,
    ) {
        val r = a.rect(level, charging, wifiLevel, mobileType, sims)
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a)
        }
        drawRectBatteryBar(canvas, level, role, fg, a)

        // One slot at the top, and only one thing in it.
        if (r.bolt) {
            drawRectBolt(canvas, if (quickCharging) TrioGeometry.QUICK_CHARGE else fg, a)
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
        a: TrioAppearance,
    ) {
        val stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO
        val cap = a.capsule(level, charging, wifiLevel, mobileType, sims)

        // The track first, in full, then the charged part over it. `level < 0` is "no reading yet" and fills nothing.
        val right = a.capsuleRight()
        val left = TrioGeometry.capLeftFor(right, TrioGeometry.capPairRight(a))
        STROKE.strokeWidth = stroke
        STROKE.style = Paint.Style.STROKE
        // One reach per opening, because the two openings no longer necessarily hold the same thing.
        val topReach = openingReach(cap, TrioZones.Zone.TOP, stroke, level, mobileType)
        val bottomReach = openingReach(cap, TrioZones.Zone.BOTTOM, stroke, level, mobileType)
        buildCapsuleOutline(CAPSULE, topReach, bottomReach, left, right)
        val animated = a.animatesOutline(charging, level)
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f
        STROKE.color = withAlpha(fg, a.trackAlpha)
        canvas.drawPath(CAPSULE, STROKE)

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            drawCapsuleFill(canvas, min(1f, level / 100f), topReach, bottomReach, left, right)
        }

        if (animated) {
            when (a.boltAnim) {
                Prefs.BOLT_ANIM_FLOW -> {
                    val head = lighten(role, FLOW_LIFT)
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

        // Three bands, the same three the ring has: the two openings and the interior.
        drawCapsuleCell(
            canvas, stroke, TrioZones.Zone.TOP, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, TrioZones.Zone.CENTRE, cap, level, quickCharging,
            wifiLevel, mobileType, fg, a, left, right,
        )
        drawCapsuleCell(
            canvas, stroke, TrioZones.Zone.BOTTOM, cap, level, quickCharging,
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
        c: Canvas, stroke: Float, band: TrioZones.Zone,
        cap: TrioAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: TrioAppearance, left: Float, right: Float,
    ) {
        if (band == TrioZones.Zone.CENTRE) {
            drawCapsuleBody(c, stroke, cap, level, quickCharging, wifiLevel, type, fg, a, left, right)
            return
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return
        }
        drawCapsulePiece(
            c, stroke, part, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = TrioGeometry.capDotCx(left, right),
            cy = TrioGeometry.capOpeningCy(band == TrioZones.Zone.TOP, stroke),
            align = CellAlign.CENTRE,
            width = TrioGeometry.capOpeningWidth(right),
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
        c: Canvas, stroke: Float, cap: TrioAppearance.Capsule, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: TrioAppearance, left: Float, right: Float,
    ) {
        val first = cap.parts[TrioZones.Zone.CENTRE.ordinal]
        if (first == Prefs.PART_NONE) {
            return
        }
        val second = cap.second[TrioZones.Zone.CENTRE.ordinal]
        val cy = TrioGeometry.capInteriorCy(stroke)
        // The two cells are independent components on one track, not a width divided
        // between them: each hangs off its own edge of the interior and grows inwards,
        // so placing a clock on the left cannot squeeze the number on the right. The
        // track is what each is fitted against, which is why a reading can be set past
        // the ceiling a fixed half-slot used to impose.
        // The margin the ink keeps from each cap IS the spacing setting - one control, not
        // a constant beside it. 100% is the reference inset; 0% lets a reading run to the
        // cap, which is also the only way two pieces can meet in the middle.
        val inset = CAP_CELL_PADDING * a.capGap / 100f
        val track = TrioGeometry.capReadingsWidth(stroke, right) - 2f * inset
        val edgeLeft = TrioGeometry.capTextLeft(stroke) + inset
        val edgeRight = TrioGeometry.capTextRight(stroke, right) - inset
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
        drawCapsulePiece(
            c, stroke, second, level, quickCharging, wifiLevel, type, fg, a,
            anchorX = edgeRight + a.capsuleRightDx,
            cy = cy + a.capsuleRightDy,
            align = CellAlign.RIGHT, width = track, heightHint = -1f,
        )
    }

    /** Draws one piece in a cell of the given width and height budget. */
    private fun drawCapsulePiece(
        c: Canvas, stroke: Float, part: Int, level: Int,
        quickCharging: Boolean, wifiLevel: Int, type: String?,
        fg: Int, a: TrioAppearance,
        anchorX: Float, cy: Float, align: CellAlign, width: Float, heightHint: Float,
    ) {
        when (part) {
            Prefs.PART_BOLT -> {
                val ink = BOLT_INK_W * TrioGeometry.capBoltScale(stroke) *
                    a.boltScaleFactor()
                drawCapsuleBolt(
                    c, stroke, if (quickCharging) TrioGeometry.QUICK_CHARGE else fg, a,
                    alignedCx(anchorX, ink, align), cy,
                )
            }
            Prefs.PART_WIFI -> {
                val ink = TrioGeometry.W_INK_W * TrioGeometry.capWifiScale(stroke) *
                    a.wifiScaleFactor()
                drawCapsuleWifi(
                    c, stroke, wifiLevel, fg, a, alignedCx(anchorX, ink, align), cy,
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
            Prefs.PART_POWER, Prefs.PART_RUNNING, Prefs.PART_TEMP, Prefs.PART_FPS ->
                drawCapsulePerf(
                    c, stroke, capsulePerfText(part), fg, a, anchorX, cy, align,
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
        c: Canvas, stroke: Float, text: String?, fg: Int, a: TrioAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        if (text.isNullOrEmpty()) {
            return
        }
        applyWeight(a.perfTypeWeight())
        val size = capsuleTextSize(
            text, a.perfReadingSize(), width, 100,
            effectiveHeightLimit(heightHint, stroke),
        )
        TEXT.textSize = size
        val tempIsHigh = text == tempText() &&
            TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN &&
            TrioState.sBatteryTempC >= a.tempThreshold
        TEXT.color = if (tempIsHigh) (if (isDarkBackground(fg)) a.highTempOnDark else a.highTempOnLight) else fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun openingRoom(band: TrioZones.Zone, stroke: Float): Float {
        // An EM, not an ink height: everything downstream of this treats it as a font size.
        // See TrioGeometry.capOpeningTextEm.
        return if (band == TrioZones.Zone.CENTRE) -1f else TrioGeometry.capOpeningTextEm(stroke)
    }

    /** `hint < 0` means "use the interior's own limit". */
    private fun effectiveHeightLimit(hint: Float, stroke: Float): Float =
        if (hint < 0f) TrioGeometry.capTextMaxSize(stroke) else hint

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
        cap: TrioAppearance.Capsule, band: TrioZones.Zone,
        stroke: Float, level: Int, type: String?,
    ): Float {
        if (cap.dotsIn(band)) {
            return TrioGeometry.capDotReach(stroke)
        }
        val part = cap.parts[band.ordinal]
        if (part == Prefs.PART_NONE) {
            return 0f
        }
        return TrioGeometry.capPieceReach(
            part, level, type ?: "", stroke,
            TrioGeometry.capOpeningTextRoomH(stroke),
        ) + TrioGeometry.capDotClearance() + stroke * 0.5f
    }

    private fun buildCapsuleOutline(
        p: Path, topReach: Float, bottomReach: Float, left: Float, right: Float,
    ) {
        val top = TrioGeometry.CAP_TOP
        val bottom = TrioGeometry.CAP_BOTTOM
        val r = TrioGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f
        val k = 0.55228475f * r

        p.reset()
        p.moveTo(cx + topReach, top)
        p.lineTo(right - r, top)
        p.cubicTo(right - r + k, top, right, cy - k, right, cy)
        p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom)
        p.lineTo(cx + bottomReach, bottom)
        p.moveTo(cx - bottomReach, bottom)
        p.lineTo(left + r, bottom)
        p.cubicTo(left + r - k, bottom, left, cy + k, left, cy)
        p.cubicTo(left, cy - k, left + r - k, top, left + r, top)
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
    ) {
        val top = TrioGeometry.CAP_TOP
        val bottom = TrioGeometry.CAP_BOTTOM
        val r = TrioGeometry.CAP_RADIUS
        val cy = (top + bottom) * 0.5f
        val cx = (left + right) * 0.5f

        // Four straight runs and two half-circle caps, in travel order. The gaps
        // are not segments: they carry no ink in either pass.
        val topLeft = (cx - topReach) - (left + r)
        val bottomLeft = (cx - bottomReach) - (left + r)
        val bottomRight = (right - r) - (cx + bottomReach)
        val topRight = (right - r) - (cx + topReach)
        val capLen = PI.toFloat() * r
        val total = topLeft + capLen + bottomLeft + bottomRight + capLen + topRight
        if (total <= 0f) {
            return
        }
        var left0 = total * fraction

        // The six pieces, clockwise from the bottom gap's left edge. Each starts exactly where the previous one ended, so the fill has no seam.
        left0 = run(c, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom)
        left0 = cap(c, left0, capLen, left + r, cy, r, 90f, 180f)
        left0 = run(c, left0, topLeft, left + r, top, cx - topReach, top)
        left0 = run(c, left0, topRight, cx + topReach, top, right - r, top)
        left0 = cap(c, left0, capLen, right - r, cy, r, 270f, 180f)
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
     * They are separate placements now - [TrioAppearance.Capsule.dotsIn] - so a
     * capsule can draw SIM 1 above and nothing below. The **outline's** gap is still
     * cut at both openings while any row exists, because a stroke interrupted on one
     * side only reads as damage rather than as a row between two segments.
     */
    private fun drawCapsuleDots(
        c: Canvas, level: Int, slotLevels: IntArray?, dual: Boolean,
        fg: Int, a: TrioAppearance, cap: TrioAppearance.Capsule?, left: Float, right: Float,
    ) {
        val topRow = cap == null || cap.dotsIn(TrioZones.Zone.TOP)
        val bottomRow = cap == null || cap.dotsIn(TrioZones.Zone.BOTTOM)
        val top = if (dual && slotLevels != null) slotLevels[0] else level
        val bottom = if (dual && slotLevels != null) slotLevels[1] else level
        val inactive = withAlpha(fg, a.trackAlpha)
        val topActive = if (top < 0) 0 else min(TrioGeometry.RECT_DOT_ROWS, top)
        val bottomActive = if (bottom < 0) 0 else min(TrioGeometry.RECT_DOT_ROWS, bottom)
        val topY = TrioGeometry.capDotTopY()
        val bottomY = TrioGeometry.capDotBottomY()
        val first = TrioGeometry.capDotCx(left, right) - TrioGeometry.CAP_DOT_PITCH * 1.5f
        FILL.style = Paint.Style.FILL

        for (i in 0 until TrioGeometry.RECT_DOT_ROWS) {
            val x = first + i * TrioGeometry.CAP_DOT_PITCH
            if (topRow) {
                FILL.color = if (i < topActive) fg else inactive
                c.drawCircle(x, topY, TrioGeometry.CAP_DOT_R, FILL)
            }
            if (bottomRow) {
                FILL.color = if (i < bottomActive) fg else inactive
                c.drawCircle(x, bottomY, TrioGeometry.CAP_DOT_R, FILL)
            }
        }
    }

    /** The time of day, in a capsule cell centred on `(cx, cy)`. */
    private fun drawCapsuleTime(
        c: Canvas, stroke: Float, fg: Int, a: TrioAppearance,
        anchorX: Float, cy: Float, align: CellAlign, heightHint: Float, width: Float,
    ) {
        val text = TrioState.sClockText
        if (text.isEmpty()) {
            return
        }
        // The clock's own size and weight, not the value's.
        applyWeight(a.clockWeight)
        val size = capsuleTextSize(
            text, a.clockSize, width, 100,
            effectiveHeightLimit(heightHint, stroke),
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun drawCapsuleValue(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: TrioAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        val text = valueText(level, a)
        applyWeight(a.valueWeight)
        val size = capsuleTextSize(
            text, a.valueSize, width, 100,
            effectiveHeightLimit(heightHint, stroke),
        )
        TEXT.textSize = size
        TEXT.color = fg
        TEXT.textAlign = Paint.Align.CENTER
        val x = alignedCx(anchorX, TEXT.measureText(text), align)
        c.drawText(text, x, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT)
    }

    private fun drawCapsuleType(
        c: Canvas, stroke: Float, type: String?, fg: Int,
        a: TrioAppearance, anchorX: Float, cy: Float, align: CellAlign,
        heightHint: Float, width: Float,
    ) {
        applyWeight(a.typeWeight)
        // The width of the row it is in, not of the interior's right-hand slot. See
        // TrioGeometry.capOpeningWidth: an opening spans the capsule, a slot does not.
        val size = capsuleTextSize(
            type, a.typeSize, width, a.typeSuffixScale,
            effectiveHeightLimit(heightHint, stroke),
        )
        // The trailing "A" is drawn smaller, so the box the label occupies is measured the
        // way drawType lays it out rather than with a plain measureText.
        val ink = measureType(type, size, a.typeSuffixScale)
        drawType(
            c, type, alignedCx(anchorX, ink, align),
            cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f,
            size, fg, a,
        )
    }

    /**
     * The size a capsule reading is drawn at: its own setting, capped by the height the
     * interior can hold, then fitted to the track it was handed.
     *
     * [slotWidth] is the track, not a share of it - see TrioGeometry.capReadingsWidth.
     * A reading is fitted to the room it could use if it were alone, which is what keeps
     * one cell's content from resizing the other.
     */
    private fun capsuleTextSize(
        text: String?, setting: Int, slotWidth: Float, suffixPercent: Int,
        heightLimit: Float,
    ): Float {
        val requested = min(setting * TrioGeometry.CAP_TEXT_SCALE, heightLimit)
        return fitSize(text, requested, slotWidth, suffixPercent)
    }

    private fun drawCapsuleWifi(
        c: Canvas, stroke: Float, level: Int, fg: Int,
        a: TrioAppearance, cx: Float, cy: Float,
    ) {
        // The user's size multiplies the slot's own fit, and the offset is rebuilt
        // from the resulting scale: adding the difference instead would translate the
        // group rather than grow it about its ink centre.
        val scale = TrioGeometry.capWifiScale(stroke) * a.wifiScaleFactor()
        c.withTranslation(
            cx - TrioGeometry.W_CX * scale,
            cy - TrioGeometry.W_INK_CY * scale,
        ) {
            c.scale(scale, scale)
            drawWifiArcs(c, level, fg, a)
        }
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
        c: Canvas, stroke: Float, color: Int, a: TrioAppearance,
        cx: Float, cy: Float,
    ) {
        val factor = a.boltScaleFactor()
        val scale = TrioGeometry.capBoltScale(stroke) * factor
        c.withTranslation(
            cx - TrioGeometry.BOLT_CX_REF * scale,
            cy - TrioGeometry.BOLT_INK_CY * scale,
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
        fg: Int, a: TrioAppearance,
    ) {
        val left = if (dual && slotLevels != null) slotLevels[0] else level
        val right = if (dual && slotLevels != null) slotLevels[1] else level
        val inactive = withAlpha(fg, a.trackAlpha)
        val leftActive = if (left < 0) 0 else min(TrioGeometry.RECT_DOT_ROWS, left)
        val rightActive = if (right < 0) 0 else min(TrioGeometry.RECT_DOT_ROWS, right)
        for (i in 0 until TrioGeometry.RECT_DOT_ROWS) {
            val y = TrioGeometry.RECT_DOT_TOP + i * TrioGeometry.RECT_DOT_STEP
            FILL.color = if (i < leftActive) fg else inactive
            c.drawCircle(TrioGeometry.RECT_DOT_LX, y, TrioGeometry.RECT_DOT_R, FILL)
            FILL.color = if (i < rightActive) fg else inactive
            c.drawCircle(TrioGeometry.RECT_DOT_RX, y, TrioGeometry.RECT_DOT_R, FILL)
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
        a: TrioAppearance,
    ) {
        val left = TrioGeometry.RECT_BAR_LEFT
        val right = TrioGeometry.RECT_BAR_RIGHT
        val r = a.ringStroke * 0.5f
        val top = TrioGeometry.RECT_BAR_CY - r
        val bottom = TrioGeometry.RECT_BAR_CY + r

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
    private fun drawRectWifi(c: Canvas, level: Int, fg: Int, a: TrioAppearance) {
        val factor = a.wifiScaleFactor()
        c.withTranslation(
            TrioGeometry.rectWifiOffsetX(factor),
            TrioGeometry.rectWifiOffsetY(a.arcStroke.toFloat(), factor)
        ) {
            c.scale(TrioGeometry.RECT_WIFI_SCALE * factor, TrioGeometry.RECT_WIFI_SCALE * factor)
            drawWifiArcs(c, level, fg, a)
        }
    }

    private fun drawRectBolt(c: Canvas, color: Int, a: TrioAppearance) {
        c.withSave {
            val factor = a.boltScaleFactor()
            c.translate(TrioGeometry.rectBoltOffsetX(factor), TrioGeometry.rectBoltOffsetY(factor))
            c.scale(TrioGeometry.BOLT_SCALE * factor, TrioGeometry.BOLT_SCALE * factor)
            drawBoltShape(c, color, a)
        }
    }

    /**
     * The bolt's outline, in whatever local space the caller has scaled into.
     *
     * One place for the three arrangements and the four motion styles, so the ring, the
     * capsule and the rectangle cannot drift apart, and the still bolt stays the plain
     * filled path it always was. Every style loops for as long as the charger is
     * attached; a frame asks for the next one only while [TrioAppearance.animatesOutline]
     * says so, which is also the flag [boltMoving] carries.
     */
    private fun drawBoltShape(c: Canvas, color: Int, a: TrioAppearance) {
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
     * Sized against [TrioGeometry.RECT_TYPE_SCALE] like the percentage is against its
     * own, so the two read as a pair, and shrunk by [fitSize] if the slot is too
     * narrow for it.
     */
    private fun drawRectType(c: Canvas, type: String?, fg: Int, a: TrioAppearance) {
        val requested = a.typeSize * TrioGeometry.RECT_TYPE_SCALE
        applyWeight(a.typeWeight)
        val size = fitSize(type, requested, TrioGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale)
        drawType(c, type, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_TYPE_BASELINE, size, fg, a)
    }

    /** The percentage, in the middle of the arrangement and at its own scale. */
    /** The battery number as drawn: bare, or with the optional percent sign. */
    private fun valueText(level: Int, a: TrioAppearance): String =
        if (a.showPercent) "$level%" else level.toString()

    /**
     * The wattage as a slot piece: "9.6W", or empty while the reading is unknown.
     *
     * One formatter for both watt pieces - the charging and the running one differ in
     * *when* they appear (see [TrioAppearance]'s ink rules), never in what they say.
     */
    private fun powerText(): String? =
        if (TrioState.sChargePowerW.isNaN()) {
            null
        } else {
            String.format(Locale.US, "%.1fW", TrioState.sChargePowerW)
        }

    /** The display's refresh rate as a slot piece: "120", or empty before it is read. */
    private fun fpsText(): String? =
        if (TrioState.sFps <= 0) null else TrioState.sFps.toString()

    /** The one reading a capsule cell holds, which is one piece rather than a pair. */
    private fun capsulePerfText(part: Int): String? = when (part) {
        Prefs.PART_TEMP -> tempText()
        Prefs.PART_FPS -> fpsText()
        else -> powerText()
    }

    /** The battery temperature as a slot piece: "32°C", or empty when it is unknown. */
    private fun tempText(): String? =
        if (TrioState.sBatteryTempC == TrioState.TEMP_UNKNOWN) {
            null
        } else {
            TrioState.sBatteryTempC.toString() + "\u00B0C"
        }

    private fun drawRectValue(c: Canvas, level: Int, fg: Int, a: TrioAppearance) {
        val text = valueText(level, a)
        val requested = a.valueSize * TrioGeometry.RECT_VALUE_SCALE
        applyWeight(a.valueWeight)
        val size = fitSize(text, requested, TrioGeometry.RECT_TEXT_CLEAR)
        TEXT.textSize = size
        TEXT.color = fg
        c.drawText(text, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_VALUE_BASELINE, TEXT)
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
        a: TrioAppearance?, level: Int, charging: Boolean,
        powerSave: Boolean, low: Boolean, fg: Int,
    ): Int {
        if (a == null || !a.roleColors) {
            return fg
        }
        val dark = isDarkBackground(fg)
        if (level >= 0 && level < TrioGeometry.CRITICAL_LEVEL) {
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
        a: TrioAppearance, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float, charging: Boolean,
    ) {
        STROKE.strokeWidth = a.ringStroke.toFloat()
        val animated = a.animatesOutline(charging, level)
        // Breathing moves the whole ring; the other two move a light along it. One
        // vocabulary per style, so the outline and the bolt always read as one piece.
        val wave = if (animated && a.boltAnim == Prefs.BOLT_ANIM_PULSE) pulseWave() else 1f

        STROKE.color = withAlpha(fg, a.trackAlpha)
        batteryRing(c, 0f, 1f, gapStart, gapEnd, sweep, start)

        if (level > 0) {
            STROKE.color = withAlpha(role, (255 * wave).toInt())
            batteryRing(c, 0f, min(1f, level / 100f), gapStart, gapEnd, sweep, start)
        }
        if (!animated) {
            return
        }
        val phase = animPhase()
        when (a.boltAnim) {
            Prefs.BOLT_ANIM_FLOW -> {
                // A light running the ring, in the charged colour lifted towards white.
                val head = lighten(role, FLOW_LIFT)
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

    /** `color` mixed towards white by [amount], keeping its alpha: a highlight on an arc. */
    private fun lighten(color: Int, amount: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt()
        return Color.argb(Color.alpha(color), r, g, b)
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
        RING_POINT[0] = (TrioGeometry.B_CX + TrioGeometry.B_R * cos(angle)).toFloat()
        RING_POINT[1] = (TrioGeometry.B_CY + TrioGeometry.B_R * sin(angle)).toFloat()
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
     * [sweep]/[start] are passed in rather than read from [TrioGeometry], because
     * the arc's angular span depends on whether anything occupies the bottom of the
     * ring this frame - see `bottomOpen`.
     */
    private fun batteryRing(
        c: Canvas, fromIn: Float, toIn: Float, gapStart: Float, gapEnd: Float,
        sweep: Float, start: Float,
    ) {
        val from = clamp01(fromIn)
        val to = clamp01(toIn)
        if (to <= from) {
            return
        }
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
                c, visibleFrom, firstEnd, TrioGeometry.B_CX, TrioGeometry.B_CY,
                TrioGeometry.B_R, sweep, start,
            )
        }
        // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
        val secondFrom = max(visibleFrom, mouthStart)
        if (visibleTo > secondFrom) {
            arcByProgress(
                c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                TrioGeometry.B_CX, TrioGeometry.B_CY, TrioGeometry.B_R, sweep, start,
            )
        }
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
    private fun drawWifiArcs(c: Canvas, level: Int, fg: Int, a: TrioAppearance) {
        val strokeWidth = min(a.arcStroke.toFloat(), TrioGeometry.W_MAX_STROKE)
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
        if (slotLevels != null && slotLevels.size >= TrioState.SIM_SLOTS) {
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
        fg: Int, a: TrioAppearance, type: String? = null,
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
                height * 0.78f / TrioGeometry.W_INK_H,
                width * 0.78f / TrioGeometry.W_INK_W,
            ) * a.wifiScaleFactor()
            c.withTranslation(width / 2f, height / 2f) {
                c.scale(scale, scale)
                c.translate(-TrioGeometry.W_CX, -TrioGeometry.W_INK_CY)
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
                height / 2f + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_TYPE) {
            // Passed in rather than read from TrioState: the settings process never
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
                height / 2f + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT,
            )
            return true
        }
        if (part == Prefs.PART_CLOCK) {
            val text = TrioState.sClockText
            if (text.isEmpty()) {
                return false
            }
            val sample = TrioState.sClockBarText.ifEmpty { text }
            val dateStr = if (a.clockDate) TrioState.sClockDate else ""
            val wkStr = if (a.clockWeekday) TrioState.sClockWeekday else ""
            val lunarStr = if (a.clockLunar) TrioState.sClockLunar else ""
            drawPreviewClock(c, width, height, sample, dateStr, wkStr, lunarStr, a, fg)
            return true
        }
        return false
    }

    /** The clock sheet's sample: fitted as one block according to offsets and gaps. */
    private fun drawPreviewClock(
        c: Canvas, width: Int, height: Int, time: String,
        date: String, weekday: String, lunar: String,
        a: TrioAppearance, fg: Int,
    ) {
        applyWeight(a.clockWeight)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val isDark = isDarkBackground(fg)
        val wkColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight
        val dtColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight
        val lnColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight
        val wkDrawColor = if (wkColor != 0) wkColor else fg
        val dtDrawColor = if (dtColor != 0) dtColor else fg
        val lnDrawColor = if (lnColor != 0) lnColor else fg

        var timeSize = height * 0.45f * userScale
        var lineSize = timeSize * PREVIEW_DATE_SCALE
        val lunarGap = a.clockLunarGap.toFloat()
        val rowGap = a.clockRowGap.toFloat()
        val topOffsetX = a.clockTopOffsetX.toFloat()
        val bottomOffsetX = a.clockBottomOffsetX.toFloat()

        TEXT.textSize = lineSize
        val wkW = if (weekday.isEmpty()) 0f else TEXT.measureText(weekday) + lineSize * 0.2f
        val dtW = if (date.isEmpty()) 0f else TEXT.measureText(date)
        val effectiveLunarGap = if (date.isNotEmpty() && lunar.isNotEmpty()) max(lunarGap, lineSize * 0.2f) else 0f
        val lnW = if (lunar.isEmpty()) 0f else TEXT.measureText(lunar)
        TEXT.textSize = timeSize
        val tmW = TEXT.measureText(time)
        val topW = wkW + tmW
        val botW = dtW + effectiveLunarGap + lnW
        val widest = max(topW, botW)
        val clear = width * 0.9f
        if (widest > clear) {
            val scale = clear / widest
            timeSize *= scale
            lineSize *= scale
        }
        val gap = max(rowGap, timeSize * 0.12f)
        TEXT.textSize = timeSize
        val timeInk = TEXT.fontMetrics.let { it.descent - it.ascent }
        TEXT.textSize = lineSize
        val lineInk = TEXT.fontMetrics.let { it.descent - it.ascent }
        val hasBottom = date.isNotEmpty() || lunar.isNotEmpty()
        val totalH = timeInk + (if (hasBottom) lineInk + gap else 0f)
        var top = height / 2f - totalH / 2f
        val cx = width / 2f

        // Top row
        val topStartX = cx - topW / 2f + topOffsetX
        var currX = topStartX
        TEXT.textAlign = Paint.Align.LEFT
        if (weekday.isNotEmpty()) {
            TEXT.textSize = lineSize
            TEXT.color = wkDrawColor
            c.drawText(weekday, currX, top + (timeInk - lineInk) - TEXT.fontMetrics.ascent, TEXT)
            currX += TEXT.measureText(weekday) + lineSize * 0.2f
        }
        TEXT.textSize = timeSize
        TEXT.color = fg
        c.drawText(time, currX, top - TEXT.fontMetrics.ascent, TEXT)

        // Bottom row
        if (hasBottom) {
            top += timeInk + gap
            val botStartX = cx - botW / 2f + bottomOffsetX
            var botX = botStartX
            TEXT.textSize = lineSize
            if (date.isNotEmpty()) {
                TEXT.color = dtDrawColor
                c.drawText(date, botX, top - TEXT.fontMetrics.ascent, TEXT)
                botX += TEXT.measureText(date) + effectiveLunarGap
            }
            if (lunar.isNotEmpty()) {
                TEXT.color = lnDrawColor
                c.drawText(lunar, botX, top - TEXT.fontMetrics.ascent, TEXT)
            }
        }
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
    fun rowsStyle(a: TrioAppearance?, dual: Boolean): Boolean =
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

    /** Margin between a placed piece and the ring's inner face, in design units. */
    private const val RING_SLOT_INSET = 2.5f

    private const val RING_WIFI_BOOST = 1.25f
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
    fun outSignalHeight(a: TrioAppearance?, sizeDp: Int, density: Float, dual: Boolean): Int {
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
        height: Int, a: TrioAppearance?, type: String?, type2: String?,
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
    private fun outPitch(a: TrioAppearance?, dual: Boolean = true): Float {
        val rows = rowsStyle(a, dual)
        return if (rows) {
            // The compact style's own reference: 1.6dp bars on a 0.9dp gap across a
            // 6.2dp row, i.e. everything is a fixed fraction of the row height.
            TrioGeometry.STACK_ROW_H * (ROW_BAR + ROW_GAP)
        } else {
            // The reference's own pitch: 39-unit bars on a 28-unit gap.
            TrioGeometry.STACK_PITCH
        }
    }

    /** Columns per row: 4 (default) or 5, adapting proportions harmoniously. */
    private fun outColumns(a: TrioAppearance?): Int = a?.signalColumns?.coerceIn(4, 5) ?: 4

    private fun outBarHeights(columns: Int): FloatArray =
        if (columns == 5) TrioGeometry.STACK_BAR_H_5 else TrioGeometry.STACK_BAR_H

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

    private fun level4(level: Int): Int = level.coerceIn(0, TrioGeometry.STACK_COLUMNS)

    private fun outBarWidth(a: TrioAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            TrioGeometry.STACK_ROW_H * ROW_BAR
        } else {
            TrioGeometry.STACK_BAR_W
        }

    private fun outLabelGap(a: TrioAppearance?, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            TrioGeometry.STACK_ROW_H * ROW_LABEL_GAP
        } else {
            TrioGeometry.STACK_LABEL_GAP
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
    private fun rowGap(a: TrioAppearance, baseInk: Float): Float =
        TrioGeometry.STACK_DOT_GAP * (a.outSignalMargin.coerceAtLeast(0) / Prefs.DEF_OUT_SIGNAL_MARGIN.toFloat())

    // --- DOTS STYLE CONSTANTS ---
    private const val DOTS_ACTIVITY_ARROW_W = 1.35f
    private const val DOTS_ACTIVITY_GAP = 0.6f
    private const val DOTS_ACTIVITY_GAP_SHARE = 0.25f
    /** Dedicated activity arrow height for single-card, independent from dual-card */
    private const val SINGLE_ACTIVITY_PAIR_H = 44f

    private fun dotsActivityBlockWidth(barW: Float): Float =
        barW * (DOTS_ACTIVITY_ARROW_W + DOTS_ACTIVITY_GAP)

    private fun rowsGap(a: TrioAppearance): Float =
        TrioGeometry.STACK_ROW_H * 0.12f

    // --- ROWS STYLE CONSTANTS ---
    private const val ROWS_ACTIVITY_ARROW_W = 1.15f
    private const val ROWS_ACTIVITY_GAP = 0.5f
    private const val ROWS_ACTIVITY_GAP_SHARE = 0.22f

    private fun rowsActivityBlockWidth(barW: Float): Float =
        barW * (ROWS_ACTIVITY_ARROW_W + ROWS_ACTIVITY_GAP)

    /** Width of the bars alone, in design units: what the label can overhang. */
    fun outSignalBarsWidth(a: TrioAppearance?, dual: Boolean = true): Float {
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
        a: TrioAppearance?, type: String?, type2: String?, dual: Boolean = true,
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
        a: TrioAppearance?, type: String?, type2: String?, dual: Boolean = true,
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

    fun outSignalInkHeight(a: TrioAppearance?, dual: Boolean = true): Float {
        if (a != null && rowsStyle(a, dual)) {
            val rowH = TrioGeometry.STACK_ROW_H
            return rowH * 2f + rowsGap(a)
        }
        val dotD = TrioGeometry.STACK_DOT_D * ((a?.outDotSize ?: 100) / 100f)
        val barsH = TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1]
        if (!dual) return barsH
        val gap = if (a != null) rowGap(a, barsH + dotD) else TrioGeometry.STACK_DOT_GAP
        return barsH + gap + dotD
    }

    /**
     * Caption size in design units when the network type is shown beside the reading (ALONE mode).
     *
     * In ALONE mode, the baseline font size is 12dp on screen (by default), contrasting with
     * the 14dp default used when the caption is positioned directly above the signal bar (BESIDE mode).
     * It scales proportionally whenever the user adjusts the outTypeSize setting (12dp * outTypeSize / 14).
     */
    private fun aloneCaptionSize(a: TrioAppearance, dual: Boolean = true): Float {
        val totalDesignH = if (rowsStyle(a, dual)) {
            val rowH = TrioGeometry.STACK_ROW_H
            rowH * 2f + rowsGap(a)
        } else {
            val columns = outColumns(a)
            val barHeights = outBarHeights(columns)
            val barsH = barHeights[columns - 1]
            val dotScale = a.outDotSize / 100f
            val baseDotD = if (columns == 5) 31f else TrioGeometry.STACK_DOT_D
            val dotD = baseDotD * dotScale
            val gap = rowGap(a, barsH + dotD)
            barsH + gap + dotD
        }
        // Maps 14dp setting to visual 12dp size (12/14 scale ratio) so the default setting renders the ideal size
        val scaleFactor = a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat()
        return totalDesignH * 0.55f * scaleFactor
    }

    private fun outCaptionSize(a: TrioAppearance, dual: Boolean = true): Float =
        outSignalLabelSize(a, dual) * (a.outTypeSize.coerceAtLeast(1) / Prefs.DEF_OUT_TYPE_SIZE_DP.toFloat())

    private fun outSignalLabelSize(a: TrioAppearance, dual: Boolean = true): Float =
        if (rowsStyle(a, dual)) {
            TrioGeometry.STACK_ROW_H * 0.72f
        } else {
            TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1] * LABEL_TO_BAR
        }

    private const val LABEL_TO_BAR = 0.65f

    private fun labelAbove(a: TrioAppearance): Boolean =
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
        fg: Int, a: TrioAppearance, dual: Boolean,
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
        fg: Int, a: TrioAppearance,
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
        val baseDotD = if (columns == 5) 31f else TrioGeometry.STACK_DOT_D
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
            val actIn = TrioState.sDataActivity == 1 || TrioState.sDataActivity == 3
            val actOut = TrioState.sDataActivity == 2 || TrioState.sDataActivity == 3
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
        fg: Int, a: TrioAppearance, type: String?, type2: String?,
    ): Boolean {
        if (bars < 0) {
            return false
        }
        val columns = outColumns(a)
        val barW = outBarWidth(a)
        val pitch = outPitch(a)
        val rowH = TrioGeometry.STACK_ROW_H
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
        val actIn = TrioState.sDataActivity == 1 || TrioState.sDataActivity == 3
        val actOut = TrioState.sDataActivity == 2 || TrioState.sDataActivity == 3

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
        fg: Int, a: TrioAppearance, dual: Boolean = true, hang: Boolean = false,
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
        if (weight == sTextWeight) {
            return
        }
        TEXT.typeface = typefaceFor(weight)
        sTextWeight = weight
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
        size: Float, fg: Int, a: TrioAppearance,
    ) {
        if (type == null || !TrioGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
            TEXT.textAlign = Paint.Align.CENTER
            TEXT.textSize = size
            TEXT.color = fg
            c.drawText(type ?: "", x, baseline, TEXT)
            return
        }
        val base = TrioGeometry.typeBase(type)
        val suffix = TrioGeometry.TYPE_SUFFIX.toString()
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
        if (text == null || !TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.textSize = size
            return TEXT.measureText(text ?: "")
        }
        TEXT.textSize = size
        val base = TEXT.measureText(TrioGeometry.typeBase(text))
        TEXT.textSize = size * scalePercent / 100f
        val suffix = TEXT.measureText(TrioGeometry.TYPE_SUFFIX.toString())
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
        inkScale(w, h, TrioGeometry.INK_W, TrioGeometry.INK_H)

    /**
     * The fit for whichever arrangement is selected. The settings preview draws its
     * own label beside the glyph, so it has to know the same factor [drawInto] will
     * use - and that depends on the style.
     */
    fun inkScale(w: Int, h: Int, rect: Boolean): Float =
        if (rect) {
            inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
        } else {
            inkScale(w, h)
        }

    /**
     * The same fit, chosen by the stored style.
     *
     * Distinct from the boolean overload above because the capsule is a third ink box
     * rather than a variant of one of the other two.
     */
    fun inkScale(w: Int, h: Int, appearance: TrioAppearance?): Float {
        if (appearance?.rect == true) {
            return inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
        }
        if (appearance?.capsule == true) {
            // The banded box: the capsule plus the strip either opening draws in. Its width
            // follows the user's extra run, which is why this takes the frame's answer
            // rather than the stored style id.
            return inkScale(
                w, h, TrioGeometry.capInkW(appearance),
                TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP,
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
    fun designTransform(w: Int, h: Int, appearance: TrioAppearance?, out: FloatArray?): Boolean {
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
            val boxH = TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP
            out[1] = (w - TrioGeometry.capInkW(appearance) * scale) / 2f -
                TrioGeometry.CAP_INK_X * scale
            out[2] = (h - boxH * scale) / 2f - TrioGeometry.CAP_INK_TOP * scale
        } else if (rect) {
            out[1] = (w - TrioGeometry.RECT_INK_W * scale) / 2f -
                TrioGeometry.RECT_INK_X * scale
            out[2] = (h - TrioGeometry.RECT_INK_H * scale) / 2f -
                TrioGeometry.RECT_INK_Y * scale
        } else {
            out[1] = (w - TrioGeometry.INK_W * scale) / 2f - TrioGeometry.INK_X * scale
            out[2] = (h - TrioGeometry.INK_H * scale) / 2f - TrioGeometry.INK_Y * scale
        }
        return true
    }

    /**
     * The shared text face at a numeric weight. The settings app and the hooked side
     * have to agree on it, and so do the in-canvas glyph and the label view drawn
     * beside it.
     */
    fun typefaceFor(weight: Int): Typeface = Typeface.create(TEXT_BASE, weight, false)

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
        val lum = (0.2126f * Color.red(color) +
            0.7152f * Color.green(color) +
            0.0722f * Color.blue(color)) / 255f
        return lum > 0.5f
    }
}
