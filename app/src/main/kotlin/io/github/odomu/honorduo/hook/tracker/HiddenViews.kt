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

import android.view.View
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.StatusAppearance
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Views the module hides inside the shade and control-center headers.
 *
 * Reapplies on layout and on attach rather than from an `OnPreDraw` listener: that
 * fires on every drawn frame for as long as the panel window lives, which is a
 * permanent per-frame cost for a value the host only changes when it rebuilds the
 * header. A visibility change from the host moves the view's bounds, so layout
 * catches it, and a rebuilt header arrives as a new view through the attach path.
 */
internal object HiddenViews {

    private class Entry(val view: WeakReference<View>, val rule: (StatusAppearance) -> Boolean)

    private val entries = ConcurrentHashMap<Int, Entry>()

    fun register(view: View, rule: (StatusAppearance) -> Boolean) {
        val key = System.identityHashCode(view)
        val fresh = entries.putIfAbsent(key, Entry(WeakReference(view), rule)) == null
        apply(view, rule(ModuleConfig.appearance()))
        if (fresh) {
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                apply(v, rule(ModuleConfig.appearance()))
            }
        }
    }

    fun applyAll(a: StatusAppearance) {
        val it = entries.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val view = entry.value.view.get()
            if (view == null || view.parent == null) {
                it.remove()
            } else {
                apply(view, entry.value.rule(a))
                view.requestLayout()
                (view.parent as? View)?.requestLayout()
            }
        }
    }

    private fun apply(view: View, hidden: Boolean) {
        val wanted = if (hidden) View.GONE else View.VISIBLE
        if (view.visibility != wanted) view.visibility = wanted
    }
}
