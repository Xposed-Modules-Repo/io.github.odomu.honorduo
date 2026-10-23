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

import io.github.odomu.honorduo.hook.TrioHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * HonorDuo - folds the Wi-Fi signal and the mobile signal into the status bar of
 * Honor's MagicOS, drawing one trio glyph (battery ring + Wi-Fi arcs + signal
 * dots) where the battery icon used to stand.
 *
 * Entry point declared in `META-INF/xposed/java_init.list`; scope in
 * `META-INF/xposed/scope.list`.
 */
class HonorDuoModule : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        try {
            TrioHooks.install(this, param.classLoader)
        } catch (t: Throwable) {
            log(LOG_WARN, "install failed: $t")
        }
    }

    private fun log(priority: Int, message: String) {
        try {
            log(priority, TAG, message)
        } catch (_: Throwable) {
            // Logging must never break the module.
        }
    }

    private companion object {
        const val TAG = "HonorDuo"
        const val LOG_WARN = 5
        const val TARGET_PACKAGE = "com.android.systemui"
    }
}
