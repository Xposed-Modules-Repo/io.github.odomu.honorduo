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

package com.honor.duo

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import java.io.File
import kotlin.math.roundToInt

/**
 * Samples and publishes the real-time frame rate.
 *
 * It combines two complementary sources:
 * 1. Hardware panel measured_fps sysfs node (instantaneous hardware refresh rate on
 *    Qualcomm/Honor devices);
 * 2. Choreographer VSYNC frame callbacks counting actual rendered frames per second.
 *
 * It runs only while `perfFps` is switched on: when off, the Choreographer callback
 * detaches completely to avoid unnecessary wakeups.
 */
internal object FpsTicker {

    @Volatile
    @JvmStatic
    var onSample: ((Int) -> Unit)? = null

    private var main: Handler? = null
    private var manager: DisplayManager? = null
    private var displayId = -1
    private var last = 0
    private var started = false

    private var frameCount = 0
    private var windowStartMs = 0L
    private var callbackRunning = false

    private val SYSFS_FPS_NODES = arrayOf(
        "/sys/class/drm/sde-crtc-0/measured_fps",
        "/sys/class/graphics/fb0/measured_fps",
        "/sys/class/drm/card0/sde-crtc-0/measured_fps",
        "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/sde-crtc-0/measured_fps",
        "/sys/devices/virtual/graphics/fb0/measured_fps",
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!TrioConfig.appearance().perfFps) {
                callbackRunning = false
                return
            }
            frameCount++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        try {
            val app = context.applicationContext ?: context
            manager = app.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayId = manager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.displayId ?: -1
            val handler = Handler(Looper.getMainLooper())
            main = handler
            handler.post { poll() }
        } catch (_: Throwable) {
            main = null
        }
    }

    private fun poll() {
        val handler = main ?: return
        try {
            val enabled = TrioConfig.appearance().perfFps
            if (enabled) {
                if (!callbackRunning) {
                    callbackRunning = true
                    windowStartMs = SystemClock.uptimeMillis()
                    frameCount = 0
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
                sample()
            } else {
                callbackRunning = false
            }
        } catch (_: Throwable) {
            // keep ticking
        } finally {
            handler.postDelayed({ poll() }, INTERVAL_MS)
        }
    }

    private fun sample() {
        // 1. Try sysfs hardware measured fps first
        val sysfsFps = readSysfsFps()
        val fps = if (sysfsFps != null && sysfsFps > 0) {
            sysfsFps
        } else {
            // 2. Calculate real-time FPS from Choreographer frame count
            val now = SystemClock.uptimeMillis()
            val elapsed = now - windowStartMs
            windowStartMs = now
            val count = frameCount
            frameCount = 0
            if (elapsed >= 300L && count > 0) {
                ((count * 1000f) / elapsed).roundToInt()
            } else {
                // 3. Fallback to display mode rate if no frames counted yet
                val id = displayId
                val dm = manager
                dm?.getDisplay(id)?.refreshRate?.roundToInt() ?: 0
            }
        }
        if (fps <= 0 || fps == last) return
        last = fps
        onSample?.invoke(fps)
    }

    private fun readSysfsFps(): Int? {
        for (path in SYSFS_FPS_NODES) {
            try {
                val file = File(path)
                if (!file.canRead()) continue
                val text = file.readText().trim()
                // Format can be "fps: 120.0" or "120" or "fps: 60"
                val num = text.substringAfter("fps:").substringAfter("fps").trim()
                    .takeWhile { it.isDigit() || it == '.' }.toFloatOrNull()
                    ?: text.takeWhile { it.isDigit() || it == '.' }.toFloatOrNull()
                if (num != null && num > 0f) {
                    return num.roundToInt()
                }
            } catch (_: Throwable) {
                // try next
            }
        }
        return null
    }

    private const val INTERVAL_MS = 1_000L
}
