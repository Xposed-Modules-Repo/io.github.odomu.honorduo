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

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import io.github.odomu.honorduo.hook.core.SystemBarHook

/**
 * The two inputs the effective scope needs: the lockscreen from the platform's own
 * callback (no hook), the pull-down panel from the one transition-only hook.
 */
internal object ScopeTracker {

    private const val PANEL_SHADE = 1
    private const val PANEL_CONTROL_CENTER = 2
    private const val PANEL_THIRD = 3
    private const val PANEL_SHADE_OVER_KEYGUARD = 4

    @Volatile
    private var panelType = 0

    @Volatile
    private var keyguardActive = false

    private var keyguardWatched = false

    val shadeActive: Boolean
        get() = panelType == PANEL_SHADE || panelType == PANEL_THIRD ||
            panelType == PANEL_SHADE_OVER_KEYGUARD

    val controlCenterActive: Boolean
        get() = panelType == PANEL_CONTROL_CENTER

    val lockscreenActive: Boolean
        get() = keyguardActive

    @android.annotation.SuppressLint("NewApi", "MissingPermission")
    fun start(context: Context?) {
        val app = context?.applicationContext ?: context ?: return
        if (keyguardWatched) return
        val manager = app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
        keyguardWatched = true
        // Platform callback: no hook and no polling. `isKeyguardLocked` already folds
        // in the occluded case, where an activity is drawn over the lockscreen.
        val listener = KeyguardManager.KeyguardLockedStateListener { locked ->
            if (locked == keyguardActive) return@KeyguardLockedStateListener
            keyguardActive = locked
            SystemBarHook.updateEffectiveScopeState()
        }
        try {
            manager.addKeyguardLockedStateListener(app.mainExecutor, listener)
            keyguardActive = manager.isKeyguardLocked
        } catch (_: Throwable) {
            keyguardWatched = false
            return
        }
        try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT)
            val unlockReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                    keyguardActive = false
                    SystemBarHook.updateEffectiveScopeState()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(unlockReceiver, filter)
            }
        } catch (_: Throwable) {
        }
        SystemBarHook.updateEffectiveScopeState()
    }
    /** Fed by the panel hook; the type walks 1/2 for the two panels, 3/4 over keyguard. */
    fun onPanelType(type: Int) {
        if (type == panelType) return
        panelType = type
        SystemBarHook.updateEffectiveScopeState()
    }

    fun describe(): String = "panel=$panelType keyguard=$keyguardActive"
}
