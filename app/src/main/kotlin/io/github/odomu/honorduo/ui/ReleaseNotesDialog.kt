package io.github.odomu.honorduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.odomu.honorduo.BuildConfig
import io.github.odomu.honorduo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Represents a parsed Markdown block element for changelog presentation.
 */
internal sealed interface MdNode {
    data class Heading(val level: Int, val text: String) : MdNode
    data class ListItem(val title: String, val content: String) : MdNode
    data class Paragraph(val text: String) : MdNode
    data class Quote(val text: String) : MdNode
    data object Divider : MdNode
}

/**
 * Parsed changelog document containing target version and sequential content nodes.
 */
internal data class ReleaseNoteDoc(
    val version: String,
    val nodes: List<MdNode>,
)

/**
 * Dynamically fetches changelog Markdown documents from remote GitHub repository endpoints.
 */
internal object ReleaseNotesLoader {
    private var cachedDoc: ReleaseNoteDoc? = null

    /**
     * Fetches the latest or target version changelog from GitHub API or raw repository files.
     *
     * @param fallbackNotes In-flight release notes string provided by [UpdateInfo].
     * @param targetVersion Optional target version string to resolve specific release notes.
     * @param forceRefresh True to bypass in-memory caching and re-query network endpoints.
     */
    suspend fun fetch(
        fallbackNotes: String? = null,
        targetVersion: String? = null,
        forceRefresh: Boolean = false,
    ): ReleaseNoteDoc? {
        if (!forceRefresh && !fallbackNotes.isNullOrBlank()) {
            val parsed = parseMarkdownDoc(fallbackNotes)
            if (parsed.nodes.isNotEmpty()) {
                cachedDoc = parsed
                return parsed
            }
        }
        if (!forceRefresh) {
            cachedDoc?.let { return it }
        }

        val lspRepo = "Xposed-Modules-Repo/io.github.odomu.honorduo"
        val repo = BuildConfig.REPO_SLUG
        val repos = listOfNotNull(lspRepo, repo.takeIf { it.isNotBlank() && it != lspRepo })
        return withContext(Dispatchers.IO) {
            val endpoints = buildList {
                for (r in repos) {
                    if (!targetVersion.isNullOrBlank()) {
                        add("https://gh-proxy.com/https://api.github.com/repos/$r/releases/tags/v$targetVersion")
                        add("https://api.github.com/repos/$r/releases/tags/v$targetVersion")
                        add("https://gh-proxy.com/https://raw.githubusercontent.com/$r/fork/docs/release-notes/release-notes-$targetVersion.md")
                        add("https://raw.githubusercontent.com/$r/fork/docs/release-notes/release-notes-$targetVersion.md")
                        add("https://gh-proxy.com/https://raw.githubusercontent.com/$r/main/docs/release-notes/release-notes-$targetVersion.md")
                        add("https://raw.githubusercontent.com/$r/main/docs/release-notes/release-notes-$targetVersion.md")
                    }
                    add("https://gh-proxy.com/https://api.github.com/repos/$r/releases/latest")
                    add("https://api.github.com/repos/$r/releases/latest")
                    add("https://gh-proxy.com/https://api.github.com/repos/$r/releases")
                    add("https://api.github.com/repos/$r/releases")
                }
            }
            for (urlStr in endpoints) {
                val fetched = runCatching {
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "HonorDuo-Changelog")
                        setRequestProperty("Accept", "application/vnd.github+json")
                    }
                    try {
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            conn.inputStream.bufferedReader().use { it.readText() }
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()

                if (!fetched.isNullOrBlank()) {
                    // Try parsing as JSON object (release details).
                    val json = runCatching { JSONObject(fetched) }.getOrNull()
                    if (json != null) {
                        val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
                        val body = json.optString("body").trim()
                        if (body.isNotBlank()) {
                            val parsed = parseMarkdownDoc(body).let {
                                if (it.version.isBlank() && tag.isNotBlank()) it.copy(version = tag) else it
                            }
                            if (parsed.nodes.isNotEmpty()) {
                                cachedDoc = parsed
                                return@withContext parsed
                            }
                        }
                    }

                    // Try parsing as JSON array (releases list).
                    val jsonArray = runCatching { JSONArray(fetched) }.getOrNull()
                    if (jsonArray != null && jsonArray.length() > 0) {
                        for (i in 0 until jsonArray.length()) {
                            val releaseObj = jsonArray.optJSONObject(i) ?: continue
                            val tag = releaseObj.optString("tag_name").removePrefix("v").removePrefix("V")
                            val body = releaseObj.optString("body").trim()
                            if (body.isNotBlank()) {
                                val parsed = parseMarkdownDoc(body).let {
                                    if (it.version.isBlank() && tag.isNotBlank()) it.copy(version = tag) else it
                                }
                                if (parsed.nodes.isNotEmpty()) {
                                    cachedDoc = parsed
                                    return@withContext parsed
                                }
                            }
                        }
                    }

                    // Try parsing directly as raw Markdown text.
                    if (fetched.startsWith("#") || fetched.contains("##")) {
                        val parsed = parseMarkdownDoc(fetched)
                        if (parsed.nodes.isNotEmpty()) {
                            cachedDoc = parsed
                            return@withContext parsed
                        }
                    }
                }
            }
            null
        }
    }
}

/**
 * Parses raw Markdown text into structured AST nodes for UI rendering.
 */
internal fun parseMarkdownDoc(markdown: String): ReleaseNoteDoc {
    var detectedVersion = ""
    val nodes = mutableListOf<MdNode>()

    for (rawLine in markdown.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        // Horizontal dividers
        if (line == "---" || line == "***" || line == "___") {
            nodes.add(MdNode.Divider)
            continue
        }

        // Heading tags (#, ##, ###, ...)
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val headingText = line.drop(level).trim()
            val verMatch = Regex("""(?i)(?:HonorDuo\s*)?(?:v)?(\d+\.\d+(?:\.\d+)?)""").find(headingText)
            if (verMatch != null && detectedVersion.isEmpty()) {
                detectedVersion = verMatch.groupValues[1]
            }
            if (!isRedundantVersionHeading(headingText)) {
                nodes.add(MdNode.Heading(level, headingText))
            }
            continue
        }

        // Blockquotes
        if (line.startsWith(">")) {
            nodes.add(MdNode.Quote(line.removePrefix(">").trim()))
            continue
        }

        // Bullet lists and numbered lists
        val isBullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")
        val isNumbered = Regex("""^\d+\.\s+""").containsMatchIn(line)

        if (isBullet || isNumbered) {
            val itemRaw = if (isBullet) {
                line.substring(2).trim()
            } else {
                line.replaceFirst(Regex("""^\d+\.\s+"""), "").trim()
            }

            val boldMatch = Regex("""\*\*(.*?)\*\*[:：]?(.*)""").find(itemRaw)
            if (boldMatch != null) {
                val title = boldMatch.groupValues[1].trim()
                val content = boldMatch.groupValues[2].trim()
                nodes.add(MdNode.ListItem(title, content))
            } else {
                nodes.add(MdNode.ListItem("", itemRaw))
            }
            continue
        }

        // Standard body paragraphs
        nodes.add(MdNode.Paragraph(line))
    }

    return ReleaseNoteDoc(detectedVersion, nodes)
}

private fun isRedundantVersionHeading(text: String): Boolean {
    val clean = text.trim()
    if (Regex("""^(?i)(?:HonorDuo\s*)?(?:v)?\d+\.\d+(?:\.\d+)?$""").matches(clean)) return true
    if (Regex("""^(?i)(?:HonorDuo\s+)?(?:v)?(?:\d+\.\d+(?:\.\d+)?\s+)?(?:更新日志|版本说明|Changelog|Release Notes)$""").matches(clean)) return true
    return false
}

private fun formatNoteSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L * 1024L -> "%.1f KB".format(Locale.ROOT, bytes / 1024f)
    else -> "%.1f MB".format(Locale.ROOT, bytes / (1024f * 1024f))
}

/**
 * Dialog displaying parsed changelog notes with download progress and action buttons.
 */
@Composable
internal fun ReleaseNotesDialog(
    show: Boolean,
    info: UpdateInfo? = null,
    currentVersion: String? = null,
    downloadProgress: Float? = null,
    onDismiss: () -> Unit,
    onDownload: ((UpdateInfo) -> Unit)? = null,
) {
    if (!show) return
    var doc by remember(info) {
        mutableStateOf(info?.notes?.takeIf { it.isNotBlank() }?.let { parseMarkdownDoc(it) })
    }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(info) {
        if (doc == null || doc?.nodes.isNullOrEmpty()) {
            isRefreshing = true
            val remoteDoc = ReleaseNotesLoader.fetch(info?.notes, currentVersion)
            if (remoteDoc != null) {
                doc = remoteDoc
            }
            isRefreshing = false
        }
    }

    val dialogTitle = if (info != null) {
        stringResource(R.string.update_dialog_title, info.version)
    } else {
        stringResource(R.string.release_notes_title)
    }

    val currentDoc = doc
    val dialogSummary = if (info != null && currentVersion != null) {
        stringResource(R.string.update_dialog_summary, currentVersion, formatNoteSize(info.apkSize))
    } else if (currentDoc != null && currentDoc.version.isNotEmpty()) {
        "v${currentDoc.version}"
    } else {
        null
    }

    OverlayDialog(
        show = true,
        title = dialogTitle,
        summary = dialogSummary,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            if (isRefreshing && currentDoc == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.release_notes_loading),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (currentDoc != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (node in currentDoc.nodes) {
                        RenderMarkdownNode(node = node)
                    }
                }
            }

            if (downloadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val pct = (downloadProgress * 100f).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.update_downloading, pct),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = downloadProgress.takeIf { it > 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (info != null && onDownload != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.update_dialog_later),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onDownload(info) },
                        enabled = downloadProgress == null,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (downloadProgress != null) {
                                stringResource(R.string.update_downloading, (downloadProgress * 100f).toInt())
                            } else {
                                stringResource(R.string.update_download)
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.release_notes_close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Renders individual Markdown AST nodes into styled Compose components.
 */
@Composable
private fun RenderMarkdownNode(node: MdNode) {
    when (node) {
        is MdNode.Heading -> {
            when (node.level) {
                1 -> {
                    Text(
                        text = node.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                2 -> {
                    val chipColor = when {
                        node.text.contains("添加") || node.text.contains("Add", ignoreCase = true) || node.text.contains("核心") ->
                            Color(0xFF2E7D32)
                        node.text.contains("修复") || node.text.contains("Fix", ignoreCase = true) ->
                            Color(0xFFE65100)
                        node.text.contains("变更") || node.text.contains("Change", ignoreCase = true) ->
                            Color(0xFF5E35B1)
                        else -> MiuixTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(chipColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = node.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = chipColor,
                        )
                    }
                }
                3 -> {
                    Text(
                        text = node.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                else -> {
                    Text(
                        text = node.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
            }
        }

        is MdNode.ListItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.5.dp, end = 8.dp)
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
                val annotatedText = buildAnnotatedString {
                    if (node.title.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                        ) {
                            append(node.title)
                        }
                        if (node.content.isNotEmpty()) {
                            withStyle(
                                SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 13.sp,
                                )
                            ) {
                                append("：")
                                append(renderInlineMarkdown(node.content))
                            }
                        }
                    } else {
                        withStyle(
                            SpanStyle(
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                        ) {
                            append(renderInlineMarkdown(node.content))
                        }
                    }
                }
                Text(
                    text = annotatedText,
                    lineHeight = 18.5.sp,
                )
            }
        }

        is MdNode.Paragraph -> {
            Text(
                text = renderInlineMarkdown(node.text),
                fontSize = 13.sp,
                lineHeight = 18.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        is MdNode.Quote -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = renderInlineMarkdown(node.text),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        is MdNode.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Strips raw Markdown formatting marks for clean body rendering.
 */
private fun renderInlineMarkdown(text: String): String {
    return text.replace("**", "").replace("*", "").replace("`", "")
}
