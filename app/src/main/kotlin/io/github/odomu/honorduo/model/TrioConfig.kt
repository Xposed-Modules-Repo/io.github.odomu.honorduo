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

package io.github.odomu.honorduo.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.odomu.honorduo.hook.TrioHooks
import io.github.odomu.honorduo.model.TrioConfig.prefs
import io.github.odomu.honorduo.model.TrioConfig.publish
import io.github.odomu.honorduo.model.TrioConfig.snapshot
import io.github.libxposed.api.XposedInterface

/**
 * Hook-side snapshot of the user's settings.
 *
 * The remote [SharedPreferences] handed to a hooked process is read-only, but it
 * is live: the framework pushes `put`/`delete` bundles over Binder and re-fires the
 * change listeners. A single listener is registered once and every change applies
 * the changed key to the published snapshot, then pokes the rendered hosts so the
 * next frame uses the new value.
 *
 * Every read falls back to the [Prefs] default, which keeps the module working
 * when the framework is embedded and cannot serve remote preferences at all.
 */
object TrioConfig {

    /** Notified (on whatever thread the framework used) after a value changed. */
    fun interface Listener {
        fun onConfigChanged()
    }

    private val lock = Any()

    /**
     * Registered listeners, held strongly and on purpose.
     *
     * A weak list is tempting but silently wrong here: the only listeners are lambdas
     * created inside [TrioHooks.install], so nothing else keeps them reachable. They were
     * collected on the first GC after install, after which a settings change updated the
     * snapshot but repainted nothing. The module lives in the hooked process for its whole
     * lifetime, so a strong reference cannot outlive its usefulness.
     */
    private val listeners = ArrayList<Listener>()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var debugLog = Prefs.DEF_DEBUG_LOG

    /** Registration of the reload receiver must happen exactly once. */
    @Volatile
    private var receiverInstalled = false

    // Values are published together, so a reader never sees a mixed snapshot.
    @Volatile
    private var snapshot: TrioSettings = TrioSettings.defaults()

    /**
     * [snapshot] resolved into the drawing rules, kept in step by [publish].
     *
     * Cached because [appearance] is read from hot paths - every `setVisibility` in
     * SystemUI goes through the suppression rule - and resolving it allocates.
     */
    @Volatile
    private var appearanceCache: TrioAppearance = TrioAppearance.of(snapshot)

    /** Publishes a new snapshot and its resolved rules; the rules first, so a reader
     *  that sees the new snapshot never pairs it with the old rules. */
    private fun publish(next: TrioSettings) {
        appearanceCache = TrioAppearance.of(next)
        snapshot = next
    }

    /**
     * Binds to the framework's remote preferences. Safe to call repeatedly; only the
     * first successful call registers a listener.
     */
    @JvmStatic
    fun install(xposed: XposedInterface) {
        if (prefs != null) return
        val remote = try {
            xposed.getRemotePreferences(Prefs.NAME)
        } catch (t: Throwable) {
            // Embedded framework (or a very old one): run on defaults.
            TrioHooks.log(TrioHooks.LOG_WARN, "remote preferences unavailable: $t")
            return
        }

        prefs = remote
        reload()
        try {
            remote.registerOnSharedPreferenceChangeListener(listener)
        } catch (t: Throwable) {
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot watch preferences: $t")
        }

        // Re-read once the framework has had time to serve the user's values.
        //
        // install() runs inside the first hook callback of a booting SystemUI, and on a
        // cold start the remote map can still hold defaults for a few seconds: the daemon
        // has not pushed the user's file across yet. The first [reload] then publishes an
        // all-defaults snapshot, so the early layout passes do nothing and the native icons
        // stay on the bar. The change listener cannot heal that - it only fires on writes,
        // and nobody writes at boot - so one delayed re-read closes the window.
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({ reloadIfChanged() }, RESYNC_DELAY_MS)
        main.postDelayed({ reloadIfChanged() }, RESYNC_DELAY_MS * 4)
    }

    /** How long after install to re-read the remote map, in milliseconds. */
    private const val RESYNC_DELAY_MS = 5_000L

    /**
     * Re-reads everything and notifies only when something actually moved - the boot-time
     * backstop for the early read having seen defaults.
     */
    private fun reloadIfChanged() {
        val previous = snapshot
        reload()
        val now = snapshot
        if (previous === now || previous == now) return
        notifyChanged()
    }

    /** Immutable view of every setting the renderer cares about. */
    @JvmStatic
    fun get(): TrioSettings = snapshot

    /** The same snapshot resolved into the drawing and suppression rules. */
    @JvmStatic
    fun appearance(): TrioAppearance = appearanceCache

    @JvmStatic
    fun debugLog(): Boolean = debugLog

    @JvmStatic
    fun addListener(listener: Listener?) {
        if (listener == null) return
        synchronized(lock) { listeners.add(listener) }
    }

    /**
     * Listens for the settings app's explicit reload broadcast, the
     * callback-independent second path for a changed setting.
     *
     * The framework's remote-preference callback is the intended mechanism and stays in
     * place, but it has been observed to stop reaching the hooked process: the daemon
     * database held the new `enabled` value while SystemUI kept drawing from the old one.
     * The broadcast carries the whole snapshot, so it cannot half-apply.
     *
     * Idempotent: whichever hooked view calls it first wins; later calls are no-ops, so
     * registering from every relevant lifecycle callback is safe.
     */
    @JvmStatic
    fun installReceiver(context: Context?) {
        if (context == null || receiverInstalled) return
        // The view this is called from can live in a window context that SystemUI tears down
        // and rebuilds, and a receiver registered on one of those dies with it. The
        // application context lives as long as the process, which is what the one-shot
        // registration below assumes.
        val target = context.applicationContext ?: context
        receiverInstalled = true
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    onReloadBroadcast(intent)
                }
            }
            val filter = IntentFilter(Prefs.ACTION_RELOAD)
            // Exported: the sender is another app. ContextCompat picks the overload the
            // running platform has, which is also what keeps the flag explicit on 13+.
            ContextCompat.registerReceiver(
                target, receiver, filter, ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (t: Throwable) {
            receiverInstalled = false
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot register reload receiver: $t")
        }
    }

    /**
     * Applies a snapshot delivered by broadcast.
     *
     * Deliberately does not touch [prefs]: the local cache is not writable from here.
     * When the framework's own callback does work it delivers the same values, so the two
     * paths agree, and a later callback for an unrelated key leaves this snapshot intact
     * instead of reverting it.
     */
    private fun onReloadBroadcast(intent: Intent?) {
        if (intent == null) return
        try {
            val extras: Bundle = intent.extras ?: run {
                TrioHooks.log(TrioHooks.LOG_WARN, "reload broadcast without extras, ignored")
                return
            }
            if (!extras.containsKey(Prefs.KEY_ENABLED)) {
                // A bundle without the master switch is not one this module sent and would
                // move state on a stranger's behalf.
                TrioHooks.log(TrioHooks.LOG_WARN, "reload broadcast without extras, ignored")
                return
            }
            // Seeded from the current snapshot: a key the sender omitted keeps the value it
            // already had instead of snapping back to a shared default.
            val previous = snapshot
            val incoming = TrioSettings.fromBundle(extras, previous)
            debugLog = incoming.debugLog
            publish(incoming)
            if (debugLog) {
                TrioHooks.log(
                    TrioHooks.LOG_INFO,
                    "reload broadcast: enabled=${incoming.enabled} ring=${incoming.ringStroke}" +
                        " arc=${incoming.arcStroke} size=${incoming.valueSize}" +
                        " (was enabled=${previous.enabled})",
                )
            }
            notifyChanged()
        } catch (t: Throwable) {
            TrioHooks.log(TrioHooks.LOG_WARN, "bad reload broadcast: $t")
        }
    }

    /**
     * The framework fires once per changed key. Only that key's value is guaranteed fresh,
     * so it is applied on its own rather than by re-reading the whole snapshot, which would
     * re-publish stale values for every key whose diff never arrived.
     */
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
        if (!applyKey(changed, key)) return@OnSharedPreferenceChangeListener
        if (debugLog) {
            TrioHooks.log(
                TrioHooks.LOG_INFO,
                "remote pref changed: $key -> enabled=${snapshot.enabled}" +
                    " ring=${snapshot.ringStroke} arc=${snapshot.arcStroke}" +
                    " size=${snapshot.valueSize}",
            )
        }
        notifyChanged()
    }

    /** Copies one key's fresh value into the published snapshot. */
    private fun applyKey(changed: SharedPreferences?, key: String?): Boolean {
        if (changed == null || key == null) return false
        return try {
            val next = snapshot.copy()
            // Not a key this module renders with; nothing to repaint.
            if (!next.applyKeyFrom(TrioSettings.from(changed), key)) return false
            debugLog = next.debugLog
            publish(next)
            true
        } catch (t: Throwable) {
            // A type mismatch in the framework's cast must never break drawing.
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot read preferences: $t")
            false
        }
    }

    /**
     * Reads the whole configuration once, when the framework connects. A full read is right
     * here and only here: the map is authoritative at install time and nothing has been
     * published yet that it could be stale against.
     */
    private fun reload() {
        val changed = prefs ?: return
        try {
            publish(TrioSettings.from(changed))
            debugLog = changed.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG)
        } catch (t: Throwable) {
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot read preferences: $t")
        }
    }

    private fun notifyChanged() {
        val alive: List<Listener>
        synchronized(lock) { alive = ArrayList(listeners) }
        for (l in alive) {
            try {
                l.onConfigChanged()
            } catch (t: Throwable) {
                // One bad listener must not stop the others.
                TrioHooks.log(TrioHooks.LOG_WARN, "config listener failed: $t")
            }
        }
    }
}
