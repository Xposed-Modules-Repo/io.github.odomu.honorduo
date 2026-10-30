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
import io.github.odomu.honorduo.model.StatusState
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
        val a = ModuleConfig.appearance()
        val hasBottomStacked = !a.capsule && !a.rect && !a.dataSimOnly && (
            (a.ringZones.getOrNull(2)?.size ?: 0) > 1 ||
            (a.ringZones.getOrNull(2)?.contains(Prefs.PART_MOBILE) == true && a.dualSim)
        )
        // Reserve clearance for stacked signal 2 at bottom
        val extraStackH = if (hasBottomStacked) 1.5f * density else 0f
        val wanted = if (a.capsule) {
            nominal + (2f * CapsuleGeometry.CAP_BAND_H * nominal / CapsuleGeometry.CAP_INK_H).roundToInt()
        } else {
            ((20.5f * density) + extraStackH).roundToInt()
        }
        val height = if (offered > 0) minOf(offered, wanted) else wanted
        val baseWidth = (height * IconGeometry.hostAspect(a, StatusState.wifiInkLevel())).roundToInt()
        // Dynamically widen view as ring spacing widens to push sibling views and avoid clipping
        val extraSpacingW = if (a.ring && a.ringSpacingX > 0) (2f * a.ringSpacingX * density).roundToInt() else 0
        val width = 1.coerceAtLeast(baseWidth + extraSpacingW)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val state = state ?: return
        if (!ModuleConfig.get().enabled || width <= 0 || height <= 0) return
        // Read the live state on every frame: battery, signal and ink all change
        // without anybody telling this view, and every one of those changes
        // invalidates it through SystemUiHook.
        state.refresh()
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
