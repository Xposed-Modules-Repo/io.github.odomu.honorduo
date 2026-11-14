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

package io.github.odomu.honorduo.hook.core

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.ModuleConfig

/**
 * Two removals at the native signal cluster's own data sources.
 *
 * - **SIM 2 icon**: `MobileDataControllerImpl.updateSubs(List)` is the single rebuild
 *   point every signal cluster goes through, so handing it a copy without the slot-1
 *   subscription means the icon is never built. Only with two subscriptions - a lone
 *   secondary card still shows.
 * - **Network-type label**: `SignalIconBinder.bind` builds the per-card view, and the
 *   type `StateFlow` re-shows the label through `IconViewBinder.bind`. Both points are
 *   hit: the first hides the label before it is shown, the second short-circuits the
 *   later re-show.
 *
 * Both switches are read at call time, so flipping one applies without a restart, and
 * every callback fails soft - nothing may throw into the host.
 */
internal object NativeSignalTweakHook {

    private const val CLS_MOBILE_DATA_CONTROLLER =
        "com.android.systemui.plugins.signal.MobileDataControllerImpl"
    private const val CLS_SIGNAL_ICON_BINDER =
        "com.android.systemui.statusbar.pipeline.mobile.ui.binder.SignalIconBinder"
    private const val CLS_ICON_VIEW_BINDER =
        "com.android.systemui.common.ui.binder.IconViewBinder"

    /** The secondary SIM is slot 2, `slotId` being 0-based. */
    private const val HIDDEN_SLOT_ID = 1

    /** The type label's view-id entry names (normal and UCT layouts). */
    private val TYPE_VIEW_NAMES = setOf("mobile_type", "mobile_signal_type")

    @Volatile private var sHideSim2 = false
    @Volatile private var sHideNetType = false

    fun install(module: XposedModule, cl: ClassLoader): Int {
        val a = ModuleConfig.appearance()
        sHideSim2 = a.hideSim2
        sHideNetType = a.hideNetType
        var hooked = 0
        hooked += hookUpdateSubs(module, cl)
        hooked += hookSignalIconBinder(module, cl)
        hooked += hookIconViewBinder(module, cl)
        return hooked
    }

    /** Applies a changed configuration; the next matching call sees the new switches. */
    fun updateConfig(hideSim2: Boolean, hideNetType: Boolean) {
        sHideSim2 = hideSim2
        sHideNetType = hideNetType
    }

    /**
     * `updateSubs` has two one-parameter overloads, `(List)` and `(MobileCallBack)`;
     * only the list one rebuilds the per-card icons, so the parameter type selects it.
     */
    private fun hookUpdateSubs(module: XposedModule, cl: ClassLoader): Int {
        val c = Refl.cls(CLS_MOBILE_DATA_CONTROLLER, cl) ?: return 0
        val method = runCatching {
            c.declaredMethods.firstOrNull {
                it.name == "updateSubs" && it.parameterTypes.size == 1 &&
                    List::class.java.isAssignableFrom(it.parameterTypes[0])
            }?.also { it.isAccessible = true }
        }.getOrNull()
        return SystemUiHook.hook(module, method, "honorduo-hide-sim2") { chain ->
            // A void method: the callback must return null - returning the proceed result
            // throws a ClassCastException inside the framework and crashes the host.
            if (!sHideSim2) {
                chain.proceed()
                return@hook null
            }
            val kept = runCatching { filterSubs(chain.getArg(0) as? List<*>) }.getOrNull()
            if (kept == null) {
                chain.proceed()
            } else {
                try {
                    chain.proceedWith(chain.thisObject, arrayOf<Any>(kept))
                } catch (_: Throwable) {
                    // Fall back to the unfiltered list rather than dropping the update.
                    runCatching { chain.proceed() }
                }
            }
            null
        }
    }

    /**
     * The list minus the slot-1 entry, or null when nothing should change: under two
     * subscriptions, or when `slotId` is unreadable (a silent no-op beats a wrong one).
     */
    private fun filterSubs(orig: List<*>?): List<*>? {
        if (orig == null || orig.size < 2) return null
        val kept = orig.filter { sub ->
            val slot = sub?.let {
                runCatching { it.javaClass.getField("slotId").getInt(it) }.getOrNull()
            }
            slot != HIDDEN_SLOT_ID
        }
        return if (kept.size == orig.size) null else kept
    }

    // ---- Hide network type ----

    /**
     * `SignalIconBinder.bind`'s second argument is the per-card view; the label is set
     * GONE there before the host hands it to its coroutine bindings.
     */
    private fun hookSignalIconBinder(module: XposedModule, cl: ClassLoader): Int {
        val c = Refl.cls(CLS_SIGNAL_ICON_BINDER, cl) ?: return 0
        val m = Refl.methodNamed(c, "bind", 6) ?: return 0
        return SystemUiHook.hook(module, m, "honorduo-hide-net-type-bind") { chain ->
            runCatching {
                if (sHideNetType) {
                    (chain.getArg(1) as? View)?.let { hideMobileType(it) }
                }
            }
            chain.proceed()
            null
        }
    }

    /**
     * The type `StateFlow` re-shows the label through `IconViewBinder.bind`: a hit goes
     * GONE and the call is short-circuited, so the host's following `setVisibility(0)`
     * acts on a drawable-less, margin-less view.
     */
    private fun hookIconViewBinder(module: XposedModule, cl: ClassLoader): Int {
        val c = Refl.cls(CLS_ICON_VIEW_BINDER, cl) ?: return 0
        val m = Refl.methodNamed(c, "bind", 2) ?: return 0
        return SystemUiHook.hook(module, m, "honorduo-hide-net-type-icon") { chain ->
            val hit = runCatching {
                if (!sHideNetType) {
                    null
                } else {
                    (0..1).firstNotNullOfOrNull { i ->
                        (chain.getArg(i) as? ImageView)?.takeIf { isTypeIconView(it) }
                    }
                }
            }.getOrNull()
            if (hit != null) {
                runCatching { hit.visibility = View.GONE }
            } else {
                chain.proceed()
            }
            null
        }
    }

    /** A type-label view is recognised by its id's resource entry name (ids drift per build). */
    private fun isTypeIconView(view: View): Boolean {
        val name = runCatching {
            val id = view.id
            if (id == 0) null else view.resources.getResourceEntryName(id)
        }.getOrNull()
        return name != null && name in TYPE_VIEW_NAMES
    }

    /** Sets the card's `mobile_type` child GONE and clears its margin, so no slot remains. */
    private fun hideMobileType(cardView: View) {
        val typeView = findByEntryName(cardView, "mobile_type") ?: return
        runCatching {
            typeView.visibility = View.GONE
            (typeView.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = 0
        }
    }

    /** Recursive child lookup by resource entry name; numeric ids are never hard-coded. */
    private fun findByEntryName(root: View, entryName: String): View? {
        val res = root.resources
        val targetId = runCatching {
            res.getIdentifier(entryName, "id", root.context.packageName)
        }.getOrNull() ?: 0
        if (targetId == 0) return null
        if (root.id == targetId) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findByEntryName(root.getChildAt(i), entryName)?.let { return it }
            }
        }
        return null
    }
}
