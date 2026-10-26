/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.ui.screen

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_LOG_LINES = 2000
private val LOG_LEVEL_REGEX = Regex("""\s([VDIWE])/LSPosedFramework""")
private val LOG_TIME_REGEX = Regex("""\[\s*\d{4}-(\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})\.\d+""")
private val LOG_PID_REGEX = Regex("""\s+(\d+:\s*\d+):""")
private val LOG_HOST_REGEX = Regex("""\((com\.[^)]+)\)""")
private val LOG_MODULE_REGEX = Regex("""\[([^,\s]+),([^,\s]+),.*?\]""")
private const val MODULE_LOG_GLOB = "/data/adb/lspd/log/modules_*.log"
private fun isNoiseLog(line: String): Boolean {
    val isHonorDuo = line.contains("honorduo", ignoreCase = true) ||
        line.contains("io.github.odomu.honorduo", ignoreCase = true)
    if (!isHonorDuo) return true
    return line.startsWith("grep:") || line.startsWith("==>") || line.startsWith("tail:")
}

/** Whether a raw line passes the level tab and the search box. */
private fun String.matchesFilter(level: Int, query: String): Boolean {
    if (isNoiseLog(this)) return false
    val passesLevel = when (level) {
        Prefs.LOG_LEVEL_ERROR -> contains(" E/LSPosed") || contains(" ERROR ") || contains(" E/")
        Prefs.LOG_LEVEL_WARN -> contains(" E/LSPosed") || contains(" W/LSPosed") || contains(" ERROR ") || contains(" WARN ")
        Prefs.LOG_LEVEL_INFO -> contains(" E/LSPosed") || contains(" W/LSPosed") || contains(" I/LSPosed") || !contains(" D/LSPosed")
        else -> true
    }
    return passesLevel && (query.isBlank() || contains(query, ignoreCase = true))
}
/** Inspects current byte length of the newest module log file. */
private fun getModuleLogSize(): Long = runCatching {
    runSuCommand("stat -c %s $MODULE_LOG_GLOB 2>/dev/null")
        .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
}.getOrDefault(0L)

/** Reads the latest snapshot from the log tail, returning lines and current size. */
private fun readSnapshot(all: Boolean): LogReadResult = runCatching {
    val size = getModuleLogSize()
    val cmd = "tail -n $MAX_LOG_LINES $MODULE_LOG_GLOB 2>/dev/null | grep -ih 'honorduo'"
    val rawLines = runSuCommand(cmd)
    val lines = rawLines
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(::isNoiseLog)
    LogReadResult(lines, size)
}.getOrDefault(LogReadResult(emptyList(), 0L))

/**
 * Incrementally reads only bytes written after [afterOffset].
 * If file shrank (rotated/cleared), returns a fresh full snapshot.
 */
private fun readIncremental(all: Boolean, afterOffset: Long): LogReadResult {
    val currentSize = getModuleLogSize()
    if (currentSize <= 0L) return LogReadResult(emptyList(), 0L)
    if (currentSize < afterOffset) {
        // Log rotated or truncated, resync snapshot
        return readSnapshot(all)
    }
    if (currentSize == afterOffset) {
        // Nothing new, zero parsing cost
        return LogReadResult(emptyList(), currentSize)
    }
    return runCatching {
        val startByte = afterOffset + 1L
        val cmd = "tail -c +$startByte $MODULE_LOG_GLOB 2>/dev/null | grep -ih 'honorduo'"
        val rawLines = runSuCommand(cmd)
        val lines = rawLines
            .map(String::trim)
            .filter(String::isNotEmpty)
        .filterNot(::isNoiseLog)
        LogReadResult(lines, currentSize)
    }.getOrDefault(LogReadResult(emptyList(), currentSize))
}

/** Executes a command in root shell via stdin without parameter escaping issues. */
private fun runSuCommand(command: String): List<String> = try {
    val process = ProcessBuilder("su").redirectErrorStream(true).start()
    process.outputStream.bufferedWriter(Charsets.UTF_8).use {
        it.write(command)
        it.write("\nexit\n")
        it.flush()
    }
    val lines = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
    process.waitFor()
    lines
} catch (_: Throwable) {
    emptyList()
}
private data class LogReadResult(
    val lines: List<String>,
    val newSize: Long,
)
/**
 * Log viewer page:
 * - TopAppBar with quick action buttons: Toggle scroll (top/bottom) and Export log
 * - Fixed search bar and fixed log level filter tabs pinned at top (non-scrollable)
 * - Independent scrolling log list container
 * - Monospace console logs with color-coded tags and level filters
 */
@Composable
internal fun LogViewerPage(
    repository: SettingsRepository,
    onBack: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(false) }
    var isTruncated by remember { mutableStateOf(false) }
    var currentLevel by remember { mutableStateOf(repository.getLogLevel()) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(repository.getDebugLog()) }
    var lastOffset by remember { mutableLongStateOf(0L) }
    var isParsedView by remember { mutableStateOf(true) }
    var wrapText by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    val contextGetStringExport = stringResource(R.string.log_export_copied)

    // Only capture when "record" is explicitly enabled:
    // 1. Off by default: shows a clear prompt directing the user to start recording.
    // 2. Turning recording on seeds the page once and streams incremental appends.
    // 3. Turning recording off stops following, but leaves the captured lines in the
    //    buffer untouched so the user can inspect or export them.
    LaunchedEffect(recording) {
        if (!recording) {
            isLoading = false
            return@LaunchedEffect
        }
        if (logs.isEmpty()) {
            isLoading = true
            isTruncated = false
            val snapshot = withContext<LogReadResult>(Dispatchers.IO) { readSnapshot(all = true) }
            lastOffset = snapshot.newSize
            logs.addAll(snapshot.lines)
            isLoading = false
        }

        while (isActive) {
            delay(1000.milliseconds)
            val delta = withContext<LogReadResult>(Dispatchers.IO) { readIncremental(all = true, lastOffset) }
            lastOffset = delta.newSize
            if (delta.lines.isNotEmpty()) {
                logs.addAll(delta.lines)
                if (logs.size > MAX_LOG_LINES) {
                    logs.removeRange(0, logs.size - MAX_LOG_LINES)
                    isTruncated = true
                }
            }
        }
    }
    val filteredLogs by remember {
        androidx.compose.runtime.derivedStateOf {
            logs.filter { it.matchesFilter(currentLevel, searchQuery) }
        }
    }

    // Default to bottom: on entry, instantly scroll to the newest log at the tail.
    // After that, follow only while the user is already at the bottom.
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            if (!initialScrollDone) {
                listState.scrollToItem(filteredLogs.lastIndex)
                initialScrollDone = true
            } else if (!listState.canScrollForward) {
                listState.scrollToItem(filteredLogs.lastIndex)
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.logs_title),
                color = MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    // Quick Action 1: Export Logs
                    IconButton(
                        onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    val directory = File(context.cacheDir, "logs").apply { mkdirs() }
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                                    File(directory, "honorduo_$stamp.txt").apply {
                                        writeText(filteredLogs.joinToString("\n"), Charsets.UTF_8)
                                    }
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TITLE, file.name)
                                    clipData = android.content.ClipData.newUri(
                                        context.contentResolver, file.name, uri,
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, contextGetStringExport))
                            }
                        },
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_share),
                            contentDescription = stringResource(R.string.export_log),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }

                    // Quick Action 2: More Menu (round grey container modeled after LSPosed manager)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_more_vertical),
                                contentDescription = null,
                                tint = if (showMenu) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        if (showMenu) {
                            LogMoreMenuPopup(
                                isDark = isDark,
                                isParsedView = isParsedView,
                                wrapText = wrapText,
                                onToggleParsedView = { isParsedView = !isParsedView },
                                onToggleWrapText = { wrapText = !wrapText },
                                onScrollTop = { scope.launch { listState.animateScrollToItem(0) } },
                                onScrollBottom = {
                                    scope.launch {
                                        if (filteredLogs.isNotEmpty()) listState.animateScrollToItem(filteredLogs.lastIndex)
                                    }
                                },
                                onClearNow = { showClearConfirmation = true },
                                onDismiss = { showMenu = false },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        ClearLogsConfirmation(
            show = showClearConfirmation,
            onDismiss = { showClearConfirmation = false },
            onConfirm = {
                showClearConfirmation = false
                // Like Cirno: clearing resets the view window by moving offset to current size,
                // without destroying the framework's shared log on disk.
                scope.launch(Dispatchers.IO) {
                    lastOffset = getModuleLogSize()
                }
                logs.clear()
                initialScrollDone = false
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            // FIXED HEADER: Search bar & Level Tabs (never scrolls with log stream)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Unified Capsule Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索日志内容…",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Fixed Log Level Filter Tabs. The record switch sits to the left of
                // "All": it is the one control that changes what gets written, not what
                // gets shown, so it stays out of the filter run.
                val levelTabs = listOf(
                    stringResource(R.string.log_all) to Prefs.LOG_LEVEL_OFF,
                    stringResource(R.string.log_level_label_debug) to Prefs.LOG_LEVEL_DEBUG,
                    stringResource(R.string.log_level_label_info) to Prefs.LOG_LEVEL_INFO,
                    stringResource(R.string.log_level_label_warn) to Prefs.LOG_LEVEL_WARN,
                    stringResource(R.string.log_level_label_error) to Prefs.LOG_LEVEL_ERROR,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val next = !recording
                            recording = next
                            // The toggle owns the debug log only; the stream restarts
                            // by itself because `recording` is its key. The filter tabs
                            // stay where the user left them.
                            repository.setDebugLog(next)
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_record),
                            contentDescription = stringResource(
                                if (recording) R.string.log_record_off else R.string.log_record_on,
                            ),
                            tint = if (recording) {
                                Color(0xFFE53935)
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    items(levelTabs) { (title, level) ->
                        val isSelected = currentLevel == level
                        val activeBg = if (isSelected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        }
                        val activeTextColor = if (isSelected) {
                            MiuixTheme.colorScheme.onPrimary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(activeBg)
                                .clickable {
                                    currentLevel = level
                                    repository.setLogFilter(level)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = activeTextColor,
                            )
                        }
                    }
                    }
                }
            }

            // INDEPENDENT SCROLLING LOG STREAM
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 32.dp)
                    }
                } else if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                searchQuery.isNotEmpty() -> stringResource(R.string.log_no_search_results)
                                !recording -> stringResource(R.string.log_not_recording)
                                else -> stringResource(R.string.log_empty)
                            },
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = navPadding + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (isParsedView) 6.dp else 2.dp),
                        ) {
                            items(filteredLogs) { line ->
                                if (isParsedView) {
                                    val parsed = remember(line) { parseLspLine(line) }
                                    ParsedLogCard(parsed = parsed)
                                } else {
                                    val scrollState = rememberScrollState()
                                    Text(
                                        text = line,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp,
                                        softWrap = wrapText,
                                        color = when {
                                            line.contains(" E/LSPosed") || line.contains("ERROR") -> Color(0xFFEF5350)
                                            line.contains(" W/LSPosed") || line.contains("WARN") -> Color(0xFFFFA726)
                                            line.contains(" D/LSPosed") || line.contains("DEBUG") -> Color(0xFF66BB6A)
                                            else -> MiuixTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!wrapText) Modifier.horizontalScroll(scrollState) else Modifier)
                                            .padding(vertical = 1.dp),
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
}

@Composable
private fun ClearLogsConfirmation(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.log_clear),
        summary = stringResource(R.string.log_clear_confirm),
        onDismissRequest = onDismiss,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.log_clear))
            }
        }
    }
}

@Composable
private fun LogMoreMenuPopup(
    isDark: Boolean,
    isParsedView: Boolean,
    wrapText: Boolean,
    onToggleParsedView: () -> Unit,
    onToggleWrapText: () -> Unit,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onClearNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val xOffset = with(density) { (-8).dp.roundToPx() }
    val yOffset = with(density) { 48.dp.roundToPx() }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(x = xOffset, y = yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier.width(136.dp),
            colors = CardDefaults.defaultColors(
                color = if (isDark) Color(0xFF2B2B30) else Color.White,
            ),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                MenuEntry(
                    title = if (isParsedView) stringResource(R.string.log_menu_raw_view)
                            else stringResource(R.string.log_menu_parsed_view),
                    onClick = {
                        onDismiss()
                        onToggleParsedView()
                    },
                )
                if (!isParsedView) {
                    MenuEntry(
                        title = stringResource(R.string.log_menu_wrap_text),
                        trailingChecked = wrapText,
                        onClick = {
                            onDismiss()
                            onToggleWrapText()
                        },
                    )
                }
                MenuDivider()
                MenuEntry(
                    title = stringResource(R.string.scroll_to_top),
                    onClick = {
                        onDismiss()
                        onScrollTop()
                    },
                )
                MenuEntry(
                    title = stringResource(R.string.scroll_to_bottom),
                    onClick = {
                        onDismiss()
                        onScrollBottom()
                    },
                )
                MenuDivider()
                MenuEntry(
                    title = stringResource(R.string.log_menu_clear_now),
                    onClick = {
                        onDismiss()
                        onClearNow()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuEntry(
    title: String,
    isDestructive: Boolean = false,
    trailingChecked: Boolean? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = if (isDestructive) Color(0xFFEF5350) else MiuixTheme.colorScheme.onSurface,
        )
        if (trailingChecked != null && trailingChecked) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_check),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .height(0.6.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

private data class ParsedLspLog(
    val levelLabel: String,
    val levelColor: Color,
    val time: String,
    val header: String,
    val subHeader: String?,
    val message: String,
)

@Composable
private fun ParsedLogCard(parsed: ParsedLspLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(parsed.levelColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = parsed.levelLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = parsed.header,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (parsed.time.isNotEmpty()) {
                    Text(
                        text = parsed.time,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (!parsed.subHeader.isNullOrBlank()) {
                Text(
                    text = parsed.subHeader,
                    fontSize = 11.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = parsed.message,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun parseLspLine(raw: String): ParsedLspLog {
    val levelChar = LOG_LEVEL_REGEX.find(raw)?.groupValues?.get(1) ?: "I"
    val (levelLabel, levelColor) = when (levelChar) {
        "E" -> "错误" to Color(0xFFEF5350)
        "W" -> "警告" to Color(0xFFFFA726)
        "D", "V" -> "调试" to Color(0xFF66BB6A)
        else -> "信息" to Color(0xFF42A5F5)
    }
    val timeMatch = LOG_TIME_REGEX.find(raw)
    val time = if (timeMatch != null) "${timeMatch.groupValues[1]} ${timeMatch.groupValues[2]}" else ""
    val pidMatch = LOG_PID_REGEX.find(raw)?.groupValues?.get(1)?.replace(" ", "") ?: ""
    val hostMatch = LOG_HOST_REGEX.find(raw)?.groupValues?.get(1)
    val moduleMatch = LOG_MODULE_REGEX.find(raw)
    val modulePkg = moduleMatch?.groupValues?.get(1)
    val moduleName = moduleMatch?.groupValues?.get(2)

    val header = when {
        moduleName != null && pidMatch.isNotEmpty() -> "$moduleName ($pidMatch)"
        moduleName != null -> moduleName
        pidMatch.isNotEmpty() -> "System ($pidMatch)"
        else -> "LSPosed"
    }
    val subHeader = if (modulePkg != null && hostMatch != null) {
        "$modulePkg → $hostMatch"
    } else hostMatch

    val message = if (raw.contains("] ")) raw.substringAfterLast("] ").trim() else raw.trim()
    return ParsedLspLog(
        levelLabel = levelLabel,
        levelColor = levelColor,
        time = time,
        header = header,
        subHeader = subHeader,
        message = message,
    )
}
