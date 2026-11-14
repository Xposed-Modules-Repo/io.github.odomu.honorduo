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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.ModuleConfig
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The "keep the bar at its own size while the capsule is docked" switch.
 *
 * MagicOS shrinks the whole status bar when a live capsule sits on the island. The
 * host's own restore spring - `startStatusBarScaleAnim(false, false)`, the entry it
 * uses when the capsule closes - is the way back, so this rides the state change
 * rather than fighting the animation. The layout fallback in
 * [SystemBarHook.resetSubtreeScale] covers paths that set the scale without an
 * animation at all.
 */
internal object CapsuleScaleHook {

    private const val CLS_CONTROLLER =
        "com.android.systemui.statusbar.phone.HnPhoneStatusBarAnimController"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var scaleAnimField: Field? = null
    @Volatile private var expandStateField: Field? = null
    @Volatile private var startScaleAnimMethod: Method? = null

    /** True while the host's restore spring runs; the layout fallback stays out of it. */
    @Volatile private var restoreRunning = false

    fun install(module: XposedModule, cl: ClassLoader): Int {
        val controller = Refl.cls(CLS_CONTROLLER, cl) ?: return 0
        val update = Refl.methodNamed(controller, "updateCapsuleState", 1) ?: return 0
        return SystemUiHook.hook(module, update, "honorduo-capsule-state") { chain ->
            val result = chain.proceed()
            runCatching {
                if (!ModuleConfig.appearance().capsuleNoScale) return@runCatching
                // State 1 is "capsule resident" (the shrink has just started). State 2 is
                // the tap-to-expand, where the bar is meant to be gone; state 0 is the
                // close, which the host springs back itself.
                val state = (chain.getArg(0) as? Number)?.toInt()
                if (state == 1) chain.thisObject?.let { scheduleRestore(it) }
            }
            result
        }
    }

    /**
     * Waits for the shrink animation to end, then triggers the host's restore.
     *
     * A null animator is the forbid path (scale set without an animation), so the state
     * is flipped back at once.
     */
    private fun scheduleRestore(controller: Any) {
        val field = scaleAnimField
            ?: Refl.field(controller.javaClass, "mStatusBarScaleAnim")?.also { scaleAnimField = it }
            ?: return
        val anim = Refl.get(field, controller) as? AnimatorSet
        if (anim != null) {
            restoreRunning = true
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Posted out of the callback stack: updateCapsuleState(0) would
                    // otherwise re-enter through end().
                    mainHandler.post { runCatching { invokeNativeRestore(controller) } }
                }
            })
        } else {
            invokeNativeRestore(controller)
        }
    }

    /** Calls `startStatusBarScaleAnim(false, false)`, the capsule-close restore entry. */
    private fun invokeNativeRestore(controller: Any) {
        // Restore only while the capsule is still resident; when the user already closed
        // it the host springs back on its own.
        val stateField = expandStateField
            ?: Refl.field(controller.javaClass, "mCapsuleExpandState")?.also { expandStateField = it }
        if (stateField != null && Refl.getInt(stateField, controller, -1) != 1) {
            restoreRunning = false
            return
        }
        val method = startScaleAnimMethod ?: runCatching {
            controller.javaClass.getDeclaredMethod(
                "startStatusBarScaleAnim",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).also { it.isAccessible = true }
        }.getOrNull()?.also { startScaleAnimMethod = it } ?: run {
            restoreRunning = false
            return
        }
        runCatching { method.invoke(controller, false, false) }
        val upAnim = scaleAnimField?.let { Refl.get(it, controller) } as? AnimatorSet
        if (upAnim != null) {
            upAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    restoreRunning = false
                }
            })
        } else {
            restoreRunning = false
        }
    }

    /**
     * Layout fallback: snaps the subtree's scale back to 1 for the animation-less
     * paths, which set the scale directly. Skipped while the host's spring runs.
     */
    fun resetSubtreeScale(view: View) {
        if (restoreRunning) return
        if (view.scaleX != 1.0f || view.scaleY != 1.0f) {
            view.scaleX = 1.0f
            view.scaleY = 1.0f
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                view.getChildAt(i)?.let { resetSubtreeScale(it) }
            }
        }
    }
}
