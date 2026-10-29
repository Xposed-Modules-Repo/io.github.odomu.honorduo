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
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified independent logger for both App process and hooked SystemUI process.
 * Standardized logging with 3-day retention and automatic rotation.
 * 100% pure Android standard implementation, root-free and independent of LSPosed.
 */
object LogManager {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HonorDuo-LogDispatcher").apply { isDaemon = true }
    }

    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

    /**
     * The line timestamp format, rebuilt when the system locale changes.
     *
     * Cached because a fresh `SimpleDateFormat` per line is an allocation plus a locale
     * lookup on the caller's thread; re-derived from [Locale.getDefault] so a locale switch
     * while SystemUI is alive still formats with the new one.
     */
    private var timeFormat = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
    private var timeLocale: Locale = Locale.getDefault()

    private fun stamp(now: Long): String {
        val locale = Locale.getDefault()
        if (locale != timeLocale) {
            timeLocale = locale
            timeFormat = SimpleDateFormat(TIME_PATTERN, locale)
        }
        return synchronized(timeFormat) { timeFormat.format(Date(now)) }
    }

    /** How long a burst of lines is allowed to collect before one write. */
    private const val FLUSH_DELAY_MS = 200L

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

        val line = "[${stamp(System.currentTimeMillis())}] [$level] [$tag] $msg"
        synchronized(pending) {
            pending.append(line).append('\n')
        }
        scheduleFlush()
    }

    private val pending = StringBuilder()
    private val flushScheduled = AtomicBoolean(false)

    /** Queues one flush for the lines collected so far, if one is not already queued. */
    private fun scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            executor.execute { flush() }
        }
    }

    private fun flush() {
        try {
            // Coalesce a burst: a settings page changing several values in a row would
            // otherwise pay one binder round trip per line.
            Thread.sleep(FLUSH_DELAY_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        flushScheduled.set(false)
        val batch = synchronized(pending) {
            val text = pending.toString()
            pending.setLength(0)
            text
        }
        if (batch.isEmpty()) return
        val ctx = StatusState.appContext()
        if (ctx == null) return
        runCatching {
            val extras = Bundle().apply { putString(AppLogProvider.EXTRA_LINE, batch) }
            ctx.contentResolver.call(AppLogProvider.CONTENT_URI, AppLogProvider.METHOD_WRITE, batch, extras)
        }
        // A line that landed after the drain has no queued flush behind it, and the next log
        // call may be far away; queue one for it.
        if (synchronized(pending) { pending.isNotEmpty() }) scheduleFlush()
    }

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
     * The last [byteCount] bytes of the concatenated active log files.
     *
     * The reader keeps the log's byte length as its cursor, so both the snapshot and the
     * incremental read are "give me the tail from here". Reading the tail instead of every
     * file is what makes following the log cost the size of the append rather than the
     * size of three days of it: retention allows 3 x 2 MB, and the viewer used to re-read
     * all of it once a second.
     *
     * A cut that lands inside a line drops that partial head line, so the caller never
     * sees half a record.
     */
    private fun readTail(context: Context, byteCount: Long): List<String> {
        if (byteCount <= 0L) return emptyList()
        val files = AppLogProvider.getActiveLogFiles(context)
        var remaining = byteCount.coerceAtMost(MAX_TAIL_BYTES)
        // Chunks are collected newest-first and reversed at the end, so the oldest piece is
        // at index 0 without an O(n^2) insert.
        val chunks = ArrayList<ByteArray>()
        var partialHead = false
        for (i in files.indices.reversed()) {
            if (remaining <= 0L) break
            val file = files[i]
            val length = runCatching { file.length() }.getOrDefault(0L)
            if (length <= 0L) continue
            val take = kotlin.math.min(length, remaining)
            if (take < length) partialHead = true
            val buffer = ByteArray(take.toInt())
            val read = runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(length - take)
                    raf.readFully(buffer)
                }
            }.isSuccess
            if (!read) continue
            chunks.add(buffer)
            remaining -= take
        }
        if (chunks.isEmpty()) return emptyList()
        val text = StringBuilder()
        for (i in chunks.indices.reversed()) {
            text.append(String(chunks[i], Charsets.UTF_8))
        }
        // A read that began inside a line produced half a record at its head.
        val body = if (partialHead) text.toString().substringAfter('\n', "") else text.toString()
        if (body.isEmpty()) return emptyList()
        return body.split('\n').filter { it.isNotEmpty() }
    }

    /** The largest tail read, so one pathological append cannot stall the reader. */
    private const val MAX_TAIL_BYTES = 1L * 1024 * 1024

    /**
     * Reads latest snapshot from active log files (up to 3 days), returning lines and combined size.
     */
    fun readSnapshot(context: Context, maxLines: Int = 2000): Pair<List<String>, Long> {
        val totalSize = getTotalLogSize(context)
        // A generous tail is enough for [maxLines] of any real line; reading the whole
        // history was the reason this ran for hundreds of milliseconds.
        val allLines = readTail(context, MAX_TAIL_BYTES)
        val result = if (allLines.size > maxLines) allLines.takeLast(maxLines) else allLines
        return Pair(result, totalSize)
    }

    /**
     * Reads the lines appended since [afterOffset], and the new total size.
     *
     * A shrunken size means the log was cleared or rotated, which is the one case that
     * still needs a fresh snapshot.
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
        // Only the bytes that landed since the last read, never the history behind them.
        val appended = readTail(context, totalSize - afterOffset)
        val result = if (appended.size > maxLines) appended.takeLast(maxLines) else appended
        return Pair(result, totalSize)
    }
}
