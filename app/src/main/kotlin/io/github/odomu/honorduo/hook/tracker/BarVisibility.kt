/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook.tracker

import io.github.odomu.honorduo.hook.core.SystemUiHook
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Whether the status bar, and with it every row this module paints into, is
 * actually on screen.
 *
 * Screen state is not the same question: an immersive app hides the bar while the
 * screen stays on, and the bar window is already hidden while the shutdown lamps
 * fade. A hidden row is not drawn, so every producer of readings asks this first
 * and does nothing at all when it says no - the battery watchdog, the frame
 * sampler, the clock ticker, the weather query and the anti-burn-in stepping.
 * The one thing that survives being hidden is the state itself, and [SystemUiHook]
 * repaints it once when the bar returns.
 *
 * Maintained by [StatusBarRowManager] from `View.isShown` of its own rows. Kept
 * flat here because `isShown` has to run on the main thread and the producers ask
 * from handlers of their own.
 */
internal object BarVisibility {

    /** False while no row is displayed. Optimistic until a row says otherwise. */
    @Volatile
    var shown: Boolean = true
        private set

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    /** Called from the rows only, on the main thread. */
    fun update(visible: Boolean) {
        if (shown == visible) return
        shown = visible
        for (listener in listeners) {
            try {
                listener(visible)
            } catch (_: Throwable) {
                // One producer must never be able to take the gate down.
            }
        }
    }
}
