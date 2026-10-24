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

package io.github.odomu.honorduo.hook

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioConfig
import kotlin.math.roundToInt

/**
 * Samples and publishes the real-time frame rate using Choreographer VSYNC
 * frame counting, falling back to the display's native refresh rate.
 *
 * Runs only while `perfFps` is active and the screen is on.
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
    private var pollScheduled = false

    private var frameCount = 0
    private var windowStartMs = 0L
    private var callbackRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFpsActive()) {
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
            ScreenStateTracker.addListener { screenOn ->
                handler.post { onScreenStateChanged(screenOn) }
            }
            schedulePoll(0L)
        } catch (_: Throwable) {
            main = null
        }
    }

    private fun isFpsActive(): Boolean {
        val a = TrioConfig.appearance()
        return ScreenStateTracker.isScreenOn && a.enabled && (a.perfFps || (!a.glyph && a.hasRingPart(Prefs.PART_FPS)))
    }

    private fun onScreenStateChanged(screenOn: Boolean) {
        if (!screenOn) {
            stopSampling()
        } else if (isFpsActive()) {
            schedulePoll(0L)
        }
    }

    fun onConfigChanged() {
        val handler = main ?: return
        handler.post {
            if (isFpsActive()) {
                schedulePoll(0L)
            } else {
                stopSampling()
            }
        }
    }

    private fun stopSampling() {
        callbackRunning = false
        val handler = main ?: return
        handler.removeCallbacks(pollRunnable)
        pollScheduled = false
    }

    private val pollRunnable = Runnable { poll() }

    private fun schedulePoll(delayMs: Long) {
        val handler = main ?: return
        handler.removeCallbacks(pollRunnable)
        pollScheduled = true
        if (delayMs <= 0L) {
            handler.post(pollRunnable)
        } else {
            handler.postDelayed(pollRunnable, delayMs)
        }
    }
    private fun poll() {
        pollScheduled = false
        if (!isFpsActive()) {
            callbackRunning = false
            return
        }
        try {
            if (!callbackRunning) {
                callbackRunning = true
                windowStartMs = SystemClock.uptimeMillis()
                frameCount = 0
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
            sample()
        } catch (_: Throwable) {
        } finally {
            if (isFpsActive()) {
                schedulePoll(INTERVAL_MS)
            } else {
                callbackRunning = false
            }
        }
    }

    private fun sample() {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - windowStartMs
        windowStartMs = now
        val count = frameCount
        frameCount = 0
        val fps = if (elapsed >= 300L && count > 0) {
            ((count * 1000f) / elapsed).roundToInt()
        } else {
            val id = displayId
            val dm = manager
            dm?.getDisplay(id)?.refreshRate?.roundToInt() ?: 0
        }
        if (fps <= 0 || fps == last) return
        last = fps
        onSample?.invoke(fps)
    }

    private const val INTERVAL_MS = 1_000L
}
