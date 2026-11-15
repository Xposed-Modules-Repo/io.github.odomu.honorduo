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

package io.github.odomu.honorduo.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.View
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer
import io.github.odomu.honorduo.ui.render.IconRenderer
import io.github.odomu.honorduo.ui.render.geometry.CapsuleGeometry
import io.github.odomu.honorduo.ui.render.geometry.IconGeometry
import kotlin.math.roundToInt

/**
 * The status glyph, drawn by a view of the module's own inside MagicOS' icon row.
 *
 * The status bar's right-hand group is `HwSystemIcons`, a plain horizontal
 * `LinearLayout` holding the notification icons, the network-speed slots,
 * `signalIcons` (the Wi-Fi + mobile cluster) and the battery. Adding one more
 * child to it is safe - nothing in that row casts its children to an interface of
 * its own - and it buys two things a drawable handed to the battery could not:
 *
 * - the glyph owns its **width** as well as its height, so the row reserves
 *   exactly the space the arrangement needs (a capsule is 2.3x wider than the
 *   ring) and closes up around it on its own;
 * - the canvas, its bounds and its clip are still the platform's. This is a child
 *   view, not an overlay window, so nothing is read as a tap-jacking surface and
 *   nothing has to be kept in step with the bar.
 *
 * The height is the battery's own box - `lock_charge_height`, 17dp - so the
 * glyph stands exactly as tall as the icon it replaces.
 */
internal class GlyphView(context: Context) : View(context) {

    /**
     * The row this glyph belongs to.
     */
    var state: StatusState? = null

    /** Set once the state has been refreshed for the first measure; see [onMeasure]. */
    private var primed = false

    /**
     * How much taller the measured box is than the reference glyph, before the user's
     * size setting.
     *
     * The box is the only thing allowed to grow: the ink is clamped to whatever room
     * the box actually got, so an oversized setting cannot spill past the bar or over
     * the components drawn beside the glyph.
     */
    private var boxFit = 1f

    /**
     * The status bar's own height, or 0 when the ROM has no such resource.
     *
     * `HwSystemIcons` is sometimes measured against the whole window, so its offered
     * height is not a ceiling for the glyph; using it directly let an enlarged glyph
     * grow past the bar. The bar's height is the real limit.
     */
    private val barHeightPx: Int by lazy {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    init {
        setWillNotDraw(false)
        // Confine this view's per-frame CLEAR to a layer of its own.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Never a touch target: MagicOS resolves the pull-down pivot from the view
        // under the finger, and one it did not create comes back null.
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val offered = MeasureSpec.getSize(heightMeasureSpec)
        val nominal = (NOMINAL_HEIGHT_DP * density).roundToInt()
        // A freshly mounted row has no readings yet (level -1), and a width measured from
        // that state is narrower than the one the first draw will need: the capsule shrank
        // and then jumped wider. One refresh here settles both on the same readings.
        state?.let {
            if (!primed && it.level < 0) {
                primed = true
                it.refresh()
            }
        }
        val a = ModuleConfig.appearance()
        val hasBottomStacked = !a.capsule && !a.rect && !a.dataSimOnly && (
            (a.ringZones.getOrNull(2)?.size ?: 0) > 1 ||
            (a.ringZones.getOrNull(2)?.contains(Prefs.PART_MOBILE) == true && a.dualSim)
        )
        // Reserve clearance for stacked signal 2 at bottom
        val extraStackH = if (hasBottomStacked) 1.5f * density else 0f
        val fit = a.glyphFit()
        val grow = if (fit > 1f) fit else 1f
        val wantedBase = if (a.capsule) {
            nominal + (2f * CapsuleGeometry.CAP_BAND_H * nominal / CapsuleGeometry.CAP_INK_H).roundToInt()
        } else {
            ((20.5f * density) + extraStackH).roundToInt()
        }
        // Growing needs a bigger box or the view would clip its own drawing. The bar's
        // height - less a small margin so the rounded strokes keep their edge - is the
        // hard ceiling; the parent's offered height can be the whole window.
        val wanted = (wantedBase * grow).roundToInt()
        val barCap = if (barHeightPx > 0) (barHeightPx - 2f * density).roundToInt() else Int.MAX_VALUE
        val offeredCap = if (offered > 0) offered else Int.MAX_VALUE
        // The bar only caps growth; at or below 100% the box keeps its 1.0.7 size even
        // on a ROM whose bar reads shorter than the glyph. Above 100% the ceiling is the
        // bar itself: `HwSystemIcons` offers the icon box, and honouring that spec threw
        // away every step of the size slider.
        val height = if (grow > 1f) {
            minOf(wanted, maxOf(wantedBase, barCap))
        } else {
            minOf(offeredCap, wanted)
        }.coerceAtLeast(1)
        boxFit = if (wantedBase > 0) height.toFloat() / wantedBase else 1f
        val growth = if (a.capsule) growthFor(a) else 0f
        val baseWidth = if (a.capsule) {
            capsuleWidthPx(a, growth)
        } else {
            (height * IconGeometry.hostAspect(a, StatusState.wifiInkLevel())).roundToInt()
        }
        // Dynamically widen view as ring spacing widens to push sibling views and avoid clipping
        val extraSpacingW = if (a.ring && a.ringSpacingX > 0) (2f * a.ringSpacingX * density).roundToInt() else 0
        val width = 1.coerceAtLeast(baseWidth + extraSpacingW)
        // Above 100% the box itself is the answer: the parent's AT_MOST is the icon row,
        // which is exactly the size the glyph has to leave behind to grow.
        val measuredHeight = if (grow > 1f) {
            height
        } else {
            resolveSize(height, heightMeasureSpec)
        }
        // The capsule's width follows from its height and its design aspect; clamping it to
        // the icon row's AT_MOST made the fit shrink the whole glyph, so a longer capsule
        // or a larger size looked unchanged in the bar while the preview showed the real
        // shape. The row clips at the bar, which is the bound that matters.
        val measuredWidth = if (a.capsule || grow > 1f) {
            width
        } else {
            resolveSize(width, widthMeasureSpec)
        }
        setMeasuredDimension(
            measuredWidth,
            measuredHeight,
        )
    }

    /**
     * Extra capsule width the two centre pieces need, in design units.
     *
     * Measured with the renderer's own paint and size rules, so the reserved box and the
     * drawn outline come from the same answer.
     */
    private fun growthFor(a: StatusAppearance): Float {
        val s = state ?: return 0f
        val wifi = StatusState.wifiInkLevel()
        val shownType = if (a.typeHideOnWifi && wifi >= 1) null else s.mobileType
        val cap = a.capsule(s.level, s.charging, wifi, shownType, 0)
        val measured = CapsuleRenderer.requiredCapsuleRight(a, cap, shownType, s.level)
        return measured - a.capsuleRight()
    }

    /**
     * Asks for a fresh measure only when the capsule's required width actually changed.
     *
     * Called after a state refresh: the width depends on what is placed and on the
     * network-type label, not on the readings' values, so this almost never fires. An
     * unconditional request here re-laid the row on every refresh and made the capsule
     * move under the user.
     */
    fun refreshCapsuleWidth() {
        if (height <= 0) return
        val a = ModuleConfig.appearance()
        if (!a.capsule) return
        // Compare the width itself rather than a key built from the inputs: a piece that
        // gains ink (the third centre reading, a value that just arrived) changes the
        // required width without changing any of the key's fields, and the view then kept
        // the box it was first measured with while the outline was drawn wider - the
        // capsule spilled to the right and its clock looked pushed off centre.
        // `requiredCapsuleRight` caches its own answer, so a still frame costs one
        // comparison.
        val wanted = capsuleWidthPx(a, growthFor(a))
        if (wanted != measuredWidth) requestLayout()
    }

    /**
     * The capsule's width for the current box height, in pixels.
     *
     * The divisor is the box the fit uses, bands included; dividing by the outline's own
     * height made the view 38% wider than its ink, and that surplus showed as a blank
     * strip beside the glyph.
     */
    private fun capsuleWidthPx(a: StatusAppearance, growth: Float): Int =
        (height * ((CapsuleGeometry.capInkW(a, StatusState.wifiInkLevel()) + growth) /
            CapsuleGeometry.capInkBoxH())).roundToInt()

    override fun onDraw(canvas: Canvas) {
        val state = state ?: return
        if (!ModuleConfig.get().enabled || width <= 0 || height <= 0) return
        // Read the live state on every frame: battery, signal and ink all change
        // without anybody telling this view, and every one of those changes
        // invalidates it through SystemUiHook.
        state.refresh()
        // The capsule's width follows which pieces have ink, and that is only known after
        // the refresh above. Asking for a measure here keeps a piece appearing or leaving
        // from leaving the outline at the old length for a whole second; the check inside
        // only fires when the width really differs, so a still frame costs nothing.
        refreshCapsuleWidth()
        // The glyph owns this view's whole box, and a plain View is not cleared
        // for it between frames: the arcs' track and the unlit dots are
        // translucent, so without this every repaint would stack a copy on the
        // last one.
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        IconRenderer.drawInto(
            canvas, width, height,
            state.level, state.charging, state.quickCharging, state.powerSave, state.low,
            if (state.wifiPresent) state.wifiLevel else -1,
            state.mobileLevel, state.slotLevels, state.mobileType,
            state.foreground(), ModuleConfig.get(), false,
            // Never ask the ink to be larger than the box that actually exists: the box
            // is capped by the bar, and scaling past it is exactly what overflowed the
            // bar and crowded the components beside the glyph.
            fitOverride = run {
                val fit = ModuleConfig.appearance().glyphFit().coerceAtLeast(0.01f)
                val grown = boxFit.coerceAtLeast(0.01f)
                minOf(fit, grown) / grown
            },
        )
        // One frame asks for the next only while the bolt is actually moving: a still
        // bolt, or an unplugged one, costs nothing.
        if (ScreenStateTracker.isScreenOn && BarVisibility.shown && ModuleConfig.appearance().animatesOutline(state.charging, state.level)) {
            postInvalidateOnAnimation()
        }
    }

    /** The battery box's height on this ROM, in dp. */
    private companion object {
        const val NOMINAL_HEIGHT_DP = 17f
    }
}
