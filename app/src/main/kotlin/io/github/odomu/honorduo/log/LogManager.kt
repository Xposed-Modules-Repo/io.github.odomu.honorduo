/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.log

import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.StatusState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Unified independent logger for both App process and hooked SystemUI process.
 * Standardized logging with 3-day retention and automatic rotation.
 * 100% pure Android standard implementation, root-free and independent of LSPosed.
 */
object LogManager {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HonorDuo-LogDispatcher").apply { isDaemon = true }
    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun v(tag: String, msg: String) = log("V", tag, msg)
    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log("W", tag, if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log("E", tag, if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg)

    fun log(level: String, tag: String, msg: String) {
        // Output to standard Android logcat for real-time ADB inspection
        when (level) {
            "V" -> Log.v(tag, msg)
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> Log.w(tag, msg)
            "E" -> Log.e(tag, msg)
        }

        val shouldPersist = ModuleConfig.debugLog() || level == "W" || level == "E"
        if (!shouldPersist) return

        val timestamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val line = "[$timestamp] [$level] [$tag] $msg"

        executor.execute {
            val ctx = StatusState.appContext()
            if (ctx != null) {
                runCatching {
                    val extras = Bundle().apply {
                        putString(AppLogProvider.EXTRA_LINE, line)
                    }
                    ctx.contentResolver.call(AppLogProvider.CONTENT_URI, AppLogProvider.METHOD_WRITE, line, extras)
                }
            }
        }
    }

    /**
     * Reads all lines across active log files (up to 3 days), ordered chronologically.
     * 100% root-free.
     */
    fun readLogs(context: Context): List<String> = runCatching {
        AppLogProvider.cleanExpiredLogs(context)
        val files = AppLogProvider.getActiveLogFiles(context)
        val allLines = ArrayList<String>()
        for (f in files) {
            if (f.exists() && f.length() > 0) {
                allLines.addAll(f.readLines())
            }
        }
        allLines
    }.getOrDefault(emptyList())

    /**
     * Completely and truly clears all log files on disk.
     */
    fun clearLogs(context: Context) {
        runCatching {
            AppLogProvider.clearAllLogs(context)
            context.contentResolver.call(AppLogProvider.CONTENT_URI, AppLogProvider.METHOD_CLEAR, null, null)
        }
    }

    /**
     * Computes the total combined byte length of all active log files.
     */
    fun getTotalLogSize(context: Context): Long = runCatching {
        AppLogProvider.getActiveLogFiles(context).sumOf { it.length() }
    }.getOrDefault(0L)

    /**
     * Reads latest snapshot from active log files (up to 3 days), returning lines and combined size.
     */
    fun readSnapshot(context: Context, maxLines: Int = 2000): Pair<List<String>, Long> {
        val totalSize = getTotalLogSize(context)
        val allLines = readLogs(context)
        val result = if (allLines.size > maxLines) allLines.takeLast(maxLines) else allLines
        return Pair(result, totalSize)
    }

    /**
     * Reads incremental lines from active log files if total size grew.
     * If total size shrank (cleared or rotated), returns fresh full snapshot.
     */
    fun readIncremental(context: Context, afterOffset: Long, maxLines: Int = 2000): Pair<List<String>, Long> {
        val totalSize = getTotalLogSize(context)
        if (totalSize <= 0L) return Pair(emptyList(), 0L)
        if (totalSize < afterOffset) {
            // Log file rotated or cleared, reload snapshot
            return readSnapshot(context, maxLines)
        }
        if (totalSize == afterOffset) {
            // Nothing changed
            return Pair(emptyList(), totalSize)
        }
        // Size grew, read updated snapshot
        return readSnapshot(context, maxLines)
    }
}
