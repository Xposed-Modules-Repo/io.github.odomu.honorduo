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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import java.lang.reflect.Field

/**
 * Bluetooth headset battery, read from the cache the stock icon already fills.
 *
 * `HwPhoneStatusBarPolicy.updateBluetooth` writes `mBluetooth.mBluetoothBatteryLevel`
 * right before this hook returns, so reading it costs nothing and stays in step with
 * the system icon.
 *
 * Two behaviours are borrowed from the newer HonourDuo One hook:
 * - the level is seeded once through the HEADSET profile proxy when the native cache
 *   has no answer yet (a SystemUI restart happens before the first battery broadcast),
 *   without writing the cache or touching the native icon;
 * - a level change only asks for a coalesced repaint of the module's own hosts, never
 *   the full status-bar resync - TWS earbuds and the case each report separately, and
 *   repainting the whole bar for every one of those reads as dropped frames.
 */
internal object BtBatteryHook {

    private const val CLS_POLICY = "com.android.systemui.statusbar.phone.HwPhoneStatusBarPolicy"

    /** One level change may arrive per earbud and per case within a frame. */
    private const val SEED_TIMEOUT_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var sBluetoothField: Field? = null
    @Volatile private var sLevelField: Field? = null
    @Volatile private var sContextField: Field? = null

    /** Guards the profile-proxy seeding from re-entering on every update. */
    @Volatile private var sSeeding = false

    fun install(module: XposedModule, cl: ClassLoader): Int {
        val policy = Refl.cls(CLS_POLICY, cl) ?: return 0
        val update = Refl.methodNamed(policy, "updateBluetooth", 0) ?: return 0
        return SystemUiHook.hook(module, update, "honorduo-bluetooth-battery") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self != null) {
                runCatching { onUpdateBluetooth(self) }
            }
            result
        }
    }

    private fun onUpdateBluetooth(policy: Any) {
        val level = readLevel(policy)
        if (level != StatusState.sBluetoothBatteryLevel) {
            StatusState.sBluetoothBatteryLevel = level
            // The level is always kept current, so switching the indicator on later
            // needs no extra event; only a change the user can see pays for a repaint.
            if (inUse()) StatusBarRowManager.invalidateBluetooth()
        }
        if (level !in 0..100 && inUse()) {
            seed(policy)
        }
    }

    /** Whether anything on screen consumes the headset level right now. */
    private fun inUse(): Boolean {
        val a = ModuleConfig.appearance()
        return a.enabled && (a.btBatteryMode > 0 || a.placed(Prefs.PART_BT) || a.perfBt)
    }

    private fun readLevel(policy: Any): Int {
        val bluetooth = sBluetoothField
            ?: Refl.field(policy.javaClass, "mBluetooth")?.also { sBluetoothField = it }
            ?: return -1
        val controller = Refl.get(bluetooth, policy) ?: return -1
        val level = sLevelField
            ?: Refl.field(controller.javaClass, "mBluetoothBatteryLevel")?.also { sLevelField = it }
            ?: return -1
        return (Refl.get(level, controller) as? Number)?.toInt() ?: -1
    }

    /**
     * Reads the level through the HEADSET proxy while the native cache is empty.
     *
     * Deliberately read-only: the proxy's answer goes into this module's own state and
     * never into the host's cache, so the stock icon keeps whatever the host decided.
     * A timeout releases the guard when the proxy callback never arrives (Bluetooth
     * switching off, or the proxy request being refused).
     */
    private fun seed(policy: Any) {
        if (sSeeding) return
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        if (adapter == null || !runCatching { adapter.isEnabled }.getOrDefault(false)) {
            mainHandler.post {
                StatusState.sBluetoothBatteryLevel = -1
                if (inUse()) StatusBarRowManager.invalidateBluetooth()
            }
            return
        }
        val contextField = sContextField
            ?: Refl.field(policy.javaClass, "mContext")?.also { sContextField = it }
            ?: return
        val context = Refl.get(contextField, policy) as? Context ?: return
        sSeeding = true
        mainHandler.postDelayed({ sSeeding = false }, SEED_TIMEOUT_MS)
        runCatching {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    var level = -1
                    if (profile == BluetoothProfile.HEADSET) {
                        val device = runCatching { proxy.connectedDevices?.firstOrNull() }.getOrNull()
                        level = device?.let {
                            runCatching {
                                it.javaClass.getMethod("getBatteryLevel").invoke(it) as Int
                            }.getOrDefault(-1)
                        } ?: -1
                    }
                    runCatching { adapter.closeProfileProxy(profile, proxy) }
                    mainHandler.post {
                        StatusState.sBluetoothBatteryLevel = level
                        if (inUse()) StatusBarRowManager.invalidateBluetooth()
                        sSeeding = false
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    sSeeding = false
                }
            }, BluetoothProfile.HEADSET)
        }.onFailure { sSeeding = false }
    }
}
