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
 *
 * The panel type is resolved into two separate facts - *which* panel is expanded and
 * whether it is expanded over the lockscreen. Keeping them separate is what stops the
 * scope from going ambiguous: the old encoding mapped only type 2 to the control center,
 * so a control center pulled down over the lockscreen (type 4) was read as the
 * notification panel and "exclude the control center" silently stopped applying there.
 */
internal object ScopeTracker {

    private const val PANEL_NONE = 0
    private const val PANEL_SHADE = 1
    private const val PANEL_CONTROL_CENTER = 2
    private const val PANEL_SHADE_OVER_KEYGUARD = 3
    private const val PANEL_CONTROL_CENTER_OVER_KEYGUARD = 4

    @Volatile
    private var panelType = PANEL_NONE

    @Volatile
    private var keyguardActive = false

    private var keyguardWatched = false

    /** A panel is expanded, whichever page it is and whether it is over the lockscreen. */
    val panelExpanded: Boolean
        get() = panelType != PANEL_NONE

    val shadeActive: Boolean
        get() = panelType == PANEL_SHADE || panelType == PANEL_SHADE_OVER_KEYGUARD

    val controlCenterActive: Boolean
        get() = panelType == PANEL_CONTROL_CENTER || panelType == PANEL_CONTROL_CENTER_OVER_KEYGUARD

    /**
     * The lockscreen scope, which counts only while no panel is expanded over it.
     *
     * The two scopes can be true at once - the panel can be pulled down on the lockscreen -
     * and whichever answer the module gives, the setting would read ambiguously: "show in
     * the notification panel" would do nothing there while "hide on the lockscreen" was
     * unticked. So the panel wins, because an expanded panel is what the user is looking at;
     * the lockscreen row describes its own status bar only.
     */
    val lockscreenActive: Boolean
        get() = keyguardActive && !panelExpanded

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
    /**
     * Fed by the panel hook.
     *
     * The platform's encoding is 0 collapsed, 1 the notification panel, 2 the control
     * center, and 3/4 the same two pulled down over the lockscreen - in that order, which
     * is why the two "over keyguard" codes are read as panel types rather than as a
     * separate lockscreen flag.
     */
    fun onPanelType(type: Int) {
        if (type == panelType) return
        panelType = type
        SystemBarHook.updateEffectiveScopeState()
    }

    fun describe(): String =
        "panel=$panelType shade=$shadeActive cc=$controlCenterActive " +
            "keyguard=$keyguardActive lockscreen=$lockscreenActive"
}
