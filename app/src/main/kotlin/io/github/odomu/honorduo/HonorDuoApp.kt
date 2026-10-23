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

package io.github.odomu.honorduo

import android.app.Application
import android.util.Log
import io.github.odomu.honorduo.ui.SettingsRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Holds the connection to the Xposed framework and keeps the module's remote
 * preferences in step with the settings the user picks here.
 *
 * The service can bind late (the framework process comes up whenever it
 * likes) and can die at any moment, so everything that wants to write has to go
 * through [#xposedService] rather than capture a reference once.
 */
class HonorDuoApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        // Must happen in onCreate: the framework broadcasts its binder to
        // whoever registered by the time the process starts.
        runCatching { XposedServiceHelper.registerListener(this) }
            .onFailure { Log.w(TAG, "cannot register service listener", it) }
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        // The app may have been configured while the framework was absent, so
        // replay everything instead of relying on write-through alone.
        runCatching { SettingsRepository(this).syncAllToFramework() }
            .onFailure { Log.w(TAG, "cannot push settings to framework", it) }
        listeners.forEach { it(service) }
    }

    override fun onServiceDied(service: XposedService) {
        // Only clear if nobody rebound in the meantime.
        if (xposedService === service) {
            xposedService = null
        }
        listeners.forEach { it(null) }
    }

    companion object {
        private const val TAG = "HonorDuo"

        @Volatile
        var xposedService: XposedService? = null
            private set

        /** Notified with the current service (or null) whenever the binding changes. */
        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

        fun addServiceListener(listener: (XposedService?) -> Unit) {
            listeners.add(listener)
            listener(xposedService)
        }

        fun removeServiceListener(listener: (XposedService?) -> Unit) {
            listeners.remove(listener)
        }
    }
}
