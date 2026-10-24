/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks device screen on/off state to eliminate battery drain during screen-off.
 *
 * When the screen is off (device standby / asleep), all background tickers, watchdogs,
 * frame callbacks and animation repaints are completely paused to allow the CPU
 * and display hardware to enter deep sleep.
 */
internal object ScreenStateTracker {

    @Volatile
    var isScreenOn: Boolean = true
        private set

    private var registered = false
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }


    @Synchronized
    fun start(context: Context) {
        if (registered) return
        val app = context.applicationContext ?: context
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = pm?.isInteractive ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val newScreenOn = when (action) {
                    Intent.ACTION_SCREEN_OFF -> false
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> true
                    else -> isScreenOn
                }
                if (isScreenOn != newScreenOn) {
                    isScreenOn = newScreenOn
                    notifyListeners(newScreenOn)
                }
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (_: Throwable) {
            registered = true
        }
    }

    private fun notifyListeners(screenOn: Boolean) {
        for (listener in listeners) {
            try {
                listener(screenOn)
            } catch (_: Throwable) {

            }
        }
    }
}
