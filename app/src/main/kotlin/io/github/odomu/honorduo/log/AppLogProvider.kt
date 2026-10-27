/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.log

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Independent cross-process log provider with 3-day automatic log rotation.
 * 100% pure Android standard implementation, no third-party libraries needed.
 * Allows hooked SystemUI process to write diagnostic logs directly into
 * HonorDuo app storage without requiring root permissions or polluting LSPosed logs.
 */
class AppLogProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.odomu.honorduo.log"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        const val METHOD_WRITE = "write"
        const val METHOD_CLEAR = "clear"
        const val EXTRA_LINE = "line"

        const val MAX_RETENTION_DAYS = 3
        private const val RETENTION_MS = MAX_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024L // 2 MB per file
        private const val FILE_PREFIX = "honorduo_"
        private const val FILE_SUFFIX = ".log"
        private const val TAG = "HonorDuo-LogProvider"

        fun getLogDir(context: Context): File {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun getTodayLogFile(context: Context): File {
            val dir = getLogDir(context)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            return File(dir, "$FILE_PREFIX$dateStr$FILE_SUFFIX")
        }

        /**
         * Returns all valid log files within retention period, sorted chronologically (oldest first).
         */
        fun getActiveLogFiles(context: Context): List<File> {
            val dir = getLogDir(context)
            val files = dir.listFiles { f ->
                f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            } ?: return emptyList()
            return files.sortedBy { it.name }
        }

        /**
         * Automatically purges logs older than 3 days, strictly keeping at most 3 days of logs.
         */
        fun cleanExpiredLogs(context: Context) {
            val dir = getLogDir(context)
            val now = System.currentTimeMillis()
            val files = dir.listFiles { f ->
                f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            } ?: return

            for (f in files) {
                if (now - f.lastModified() > RETENTION_MS) {
                    f.delete()
                    Log.i(TAG, "Deleted expired log file (>3 days): ${f.name}")
                }
            }

            val remaining = dir.listFiles { f ->
                f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            }?.sortedBy { it.name } ?: return

            if (remaining.size > MAX_RETENTION_DAYS) {
                val toDelete = remaining.take(remaining.size - MAX_RETENTION_DAYS)
                for (oldFile in toDelete) {
                    oldFile.delete()
                    Log.i(TAG, "Rotated and deleted oldest log file: ${oldFile.name}")
                }
            }
        }

        /**
         * Truly and completely deletes all log files on disk.
         */
        fun clearAllLogs(context: Context) {
            val dir = getLogDir(context)
            val files = dir.listFiles { f ->
                f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            } ?: return
            for (f in files) {
                f.delete()
            }
            Log.i(TAG, "All log files completely cleared on disk")
        }
    }

    private val lock = Any()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        when (method) {
            METHOD_WRITE -> {
                val line = arg ?: extras?.getString(EXTRA_LINE) ?: return null
                writeLine(ctx, line)
            }
            METHOD_CLEAR -> {
                synchronized(lock) {
                    clearAllLogs(ctx)
                }
            }
        }
        return Bundle.EMPTY
    }

    private fun writeLine(ctx: Context, line: String) {
        synchronized(lock) {
            runCatching {
                cleanExpiredLogs(ctx)
                val file = getTodayLogFile(ctx)
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    truncateFile(file)
                }
                FileWriter(file, true).use { writer ->
                    writer.write(line)
                    writer.write("\n")
                }
            }.onFailure {
                Log.w(TAG, "Failed to write log line: ${it.message}")
            }
        }
    }

    private fun truncateFile(file: File) {
        runCatching {
            val lines = file.readLines()
            val keepLines = lines.takeLast(lines.size / 2)
            file.writeText(keepLines.joinToString("\n") + "\n")
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<out String>?): Int = 0
}
