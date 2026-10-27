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

import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Process
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.odomu.honorduo.hook.core.Refl
import io.github.odomu.honorduo.model.ModuleConfig
import java.lang.ref.WeakReference

private const val ACTION_MEDIA_OUTPUT = "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
private const val PACKAGE_SYSTEMUI = "com.android.systemui"

/**
 * A long press on the control center's volume slider opens the system media-output
 * dialog. MagicOS puts no such gesture on the slider, so the press is timed here.
 */
internal object SliderPress {

    private var sTarget: WeakReference<View>? = null
    private var sDownX = 0f
    private var sDownY = 0f

    fun isSlider(view: View): Boolean {
        // The slider view itself is the volume container: `dispatchTouchEvent` is
        // declared on the base `HnCentralSliderView`, so the instance handed to the
        // hook is the `QsVolumeContainer` subclass, and its own name is the marker.
        var node: View? = view
        while (node != null) {
            val name = node.javaClass.name
            if (name.contains("QsVolumeContainer") || name.contains("Volume")) return true
            node = node.parent as? View
        }
        return false
    }

    fun onTouch(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                if (!ModuleConfig.appearance().ccSliderLongPress) return
                sTarget = WeakReference(view)
                sDownX = event.x
                sDownY = event.y
                view.postDelayed(press, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (sTarget?.get() !== view) return
                val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                if (Math.abs(event.x - sDownX) > slop || Math.abs(event.y - sDownY) > slop) cancel()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }
    }

    private val press = Runnable {
        val view = sTarget?.get()
        sTarget = null
        if (view == null || !ModuleConfig.appearance().ccSliderLongPress) return@Runnable
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        trigger(view)
    }

    private fun cancel() {
        sTarget?.get()?.removeCallbacks(press)
        sTarget = null
    }

    /**
     * Opens the vendor's media-output dialog, the same one the media card shows.
     *
     * `MediaOutputSwitcherDialogUI` is registered as a `CommandQueue` callback and
     * does two things the plain dialog does not: it pre-inflates `HnMediaDialogEx`
     * and asks the manager for its last-argument-set build, which is the rounded
     * MagicOS list. `CommandQueue.showMediaOutputSwitcher` is the public entry that
     * reaches it, and its only caller guard is "system server or uid 1000", which
     * SystemUI is. The AOSP receiver is kept as the fallback.
     */
    private fun trigger(view: View) {
        if (openVendorDialog(view)) return
        runCatching {
            view.context.sendBroadcast(Intent(ACTION_MEDIA_OUTPUT).setPackage(PACKAGE_SYSTEMUI))
        }
    }

    private var sSwitcher: WeakReference<Any>? = null

    fun remember(switcher: Any) {
        sSwitcher = WeakReference(switcher)
    }

    /**
     * Asks the switcher the host started at boot for the dialog.
     *
     * Called on the instance rather than through `CommandQueue`: that entry only
     * accepts uid 0/1000 and SystemUI has its own uid, so it threw and the AOSP
     * dialog came up instead.
     */
    private fun openVendorDialog(view: View): Boolean = runCatching {
        val switcher = sSwitcher?.get() ?: return false
        val show = Refl.methodNamed(switcher.javaClass, "showMediaOutputSwitcher", 3) ?: return false
        val session = runCatching {
            view.context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(null)?.firstOrNull()
        }.getOrNull()
        show.invoke(
            switcher,
            session?.packageName ?: PACKAGE_SYSTEMUI,
            Process.myUserHandle(),
            session?.sessionToken,
        )
        true
    }.getOrDefault(false)
}
