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

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.View
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
import kotlin.math.roundToInt

/**
 * The trio glyph, drawn by a view of the module's own inside MagicOS' icon row.
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
    var state: TrioState? = null

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
        // The capsule is taller than the battery box by one band at each end, which is
        // where its two opening readings live; the body itself stays the nominal height.
        val wanted = if (TrioConfig.appearance().capsule) {
            nominal + (2f * TrioGeometry.CAP_BAND_H * nominal / TrioGeometry.CAP_INK_H).roundToInt()
        } else {
            nominal
        }
        val height = if (offered > 0) minOf(offered, wanted) else wanted
        val width =
            1.coerceAtLeast((height * TrioGeometry.hostAspect(TrioConfig.appearance())).roundToInt())
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val state = state ?: return
        if (!TrioConfig.get().enabled || width <= 0 || height <= 0) return
        // Read the live state on every frame: battery, signal and ink all change
        // without anybody telling this view, and every one of those changes
        // invalidates it through TrioHooks.
        state.refresh()
        // The glyph owns this view's whole box, and a plain View is not cleared
        // for it between frames: the arcs' track and the unlit dots are
        // translucent, so without this every repaint would stack a copy on the
        // last one.
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        TrioRenderer.drawInto(
            canvas, width, height,
            state.level, state.charging, state.quickCharging, state.powerSave, state.low,
            if (state.wifiPresent) state.wifiLevel else -1,
            state.mobileLevel, state.slotLevels, state.mobileType,
            state.foreground(), TrioConfig.get(), false,
        )
        // One frame asks for the next only while the bolt is actually moving: a still
        // bolt, or an unplugged one, costs nothing.
        if (TrioConfig.appearance().animatesOutline(state.charging, state.level)) {
            postInvalidateOnAnimation()
        }
    }

    /** The battery box's height on this ROM, in dp. */
    private companion object {
        const val NOMINAL_HEIGHT_DP = 17f
    }
}
