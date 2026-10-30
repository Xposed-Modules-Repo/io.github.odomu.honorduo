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

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.manager.SettingsStore
import io.github.odomu.honorduo.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.util.Locale

/**
 * The module's settings screen.
 *
 * Reachable both from the launcher (through the activity alias) and from the
 * LSPosed manager, which launches the activity advertising
 * `de.robv.android.xposed.category.MODULE_SETTINGS`.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val mode = prefs.getInt(Prefs.KEY_APP_LANGUAGE, Prefs.DEF_APP_LANGUAGE)
        val context = if (mode != Prefs.APP_LANG_SYSTEM) {
            val locale = if (mode == Prefs.APP_LANG_ZH) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
            Locale.setDefault(locale)
            val config = newBase.resources.configuration
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the bar icons following the theme rather than the flags baked into AppTheme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsStore(applicationContext) }
            MiuixTheme(controller = remember { ThemeController() }) {
                SettingsScreen(repository)
            }
        }
    }
}
