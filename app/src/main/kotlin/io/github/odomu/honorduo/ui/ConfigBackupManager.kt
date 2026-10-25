package io.github.odomu.honorduo.ui

import android.content.Context
import android.net.Uri
import io.github.odomu.honorduo.model.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore utility: exports all shared preference entries to a JSON
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
