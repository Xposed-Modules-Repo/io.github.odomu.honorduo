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

package io.github.odomu.honorduo.ui.manager

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.odomu.honorduo.model.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages backup export and restore operations in a self-contained controller.
 *
 * Encapsulates Android storage access framework (SAF) launcher registration,
 * background JSON serialization/deserialization, and dispatching result callbacks.
 */
class BackupManager(
    private val context: Context,
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onExportSuccess: () -> Unit,
    private val onImportSuccess: (count: Int) -> Unit,
    private val onError: (message: String) -> Unit,
) {
    internal lateinit var exportLauncher: ActivityResultLauncher<String>
    internal lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    fun exportBackup() {
        exportLauncher.launch(ConfigBackupManager.createBackupFileName())
    }

    fun importBackup() {
        importLauncher.launch(arrayOf("application/json"))
    }

    internal fun handleExport(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        val json = ConfigBackupManager.exportToJson(context)
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Cannot open output stream")
                }
            }
            result.fold(
                onSuccess = { onExportSuccess() },
                onFailure = { onError(it.message.orEmpty()) },
            )
        }
    }

    internal fun handleImport(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ConfigBackupManager.importFromJson(context, uri, repository)
                }
            }
            result.fold(
                onSuccess = { count -> onImportSuccess(count) },
                onFailure = { onError(it.message.orEmpty()) },
            )
        }
    }
}

/**
 * Remembers a [BackupManager] instance wired with ActivityResult launchers.
 */
@Composable
fun rememberBackupManager(
    context: Context,
    repository: SettingsRepository,
    onExportSuccess: () -> Unit,
    onImportSuccess: (count: Int) -> Unit,
    onError: (message: String) -> Unit,
): BackupManager {
    val scope = rememberCoroutineScope()
    val manager = remember(context, repository, onExportSuccess, onImportSuccess, onError) {
        BackupManager(context, repository, scope, onExportSuccess, onImportSuccess, onError)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> manager.handleExport(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> manager.handleImport(uri) }

    manager.exportLauncher = exportLauncher
    manager.importLauncher = importLauncher
    return manager
}

/**
 * Backup and restore serialization utility: exports all shared preference entries to a JSON
 * document, and restores preferences from an external backup JSON file.
 */
internal object ConfigBackupManager {

    private const val FILE_PREFIX = "HonorDuo_Backup_"
    private const val BACKUP_VERSION = 1

    fun createBackupFileName(): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "$FILE_PREFIX$date.json"
    }

    /**
     * Serializes all current preferences to a formatted JSON string.
     */
    fun exportToJson(context: Context): String {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())
        root.put("package", context.packageName)

        val data = JSONObject()
        for ((key, value) in all) {
            when (value) {
                is Boolean -> data.put(key, value)
                is Int -> data.put(key, value)
                is Long -> data.put(key, value)
                is Float -> data.put(key, value.toDouble())
                is String -> data.put(key, value)
            }
        }
        root.put("preferences", data)
        return root.toString(2)
    }

    /**
     * Reads a backup JSON payload from [uri] and restores preferences.
     *
     * @return Count of restored keys, or throws on read/parse error.
     */
    fun importFromJson(context: Context, uri: Uri, repository: SettingsRepository): Int {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } ?: throw IllegalArgumentException("Cannot open backup file")

        val root = JSONObject(jsonString)
        val data = root.optJSONObject("preferences")
            ?: throw IllegalArgumentException("Invalid backup format: missing preferences object")

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var count = 0

        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = data.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
            count++
        }
        editor.apply()

        // Push restored preferences to the framework service and refresh active state
        repository.syncAllToFramework()
        return count
    }
}
