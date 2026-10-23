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

package io.github.odomu.honorduo.ui


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The module's settings screen.
 *
 * Reachable both from the launcher (through the activity alias) and from the
 * LSPosed manager, which launches the activity advertising
 * `de.robv.android.xposed.category.MODULE_SETTINGS`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Explicit because targetSdk 37 draws every app edge to edge: this is what keeps
        // the bar icons following the theme rather than the flags baked into AppTheme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsRepository(applicationContext) }
            MiuixTheme(controller = remember { ThemeController() }) {
                SettingsScreen(repository)
            }
        }
    }
}
