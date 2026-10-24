package io.github.odomu.honorduo.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Copies a font the user picked into a place the hooked SystemUI process can
 * read, and reports what happened.
 */
internal object FontInstaller {

    /** Subdirectory under the app's external media dir. */
    private const val DIR_NAME = "fonts"

    /** Read permission for everyone: the hooked process is a different uid. */
    private const val WORLD_READ = true

    internal sealed interface Result {
        /** [path] is where the font now lives; [name] is what to show for it. */
        data class Installed(val path: String, val name: String) : Result

        /** The picked file is not a font. */
        data object NotAFont : Result

        /** The copy could not be made; [detail] is for the log, not the user. */
        data class Failed(val detail: String?) : Result
    }

    internal suspend fun install(context: Context, source: Uri): Result =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: return@withContext Result.Failed("cannot open the picked file")
                input.use { raw ->
                    val stream = BufferedInputStream(raw, MARK_LIMIT)
                    if (!isFont(stream)) {
                        return@withContext Result.NotAFont
                    }
                    val dir = destinationDir(context)
                        ?: return@withContext Result.Failed("external storage is unavailable")
                    val name = displayName(context, source)
                    val target = File(dir, name)
                    writeVerified(stream, target)?.let { why ->
                        return@withContext Result.Failed(why)
                    }
                    target.setReadable(WORLD_READ, false)
                    dir.setReadable(WORLD_READ, false)
                    dir.setExecutable(true, false)
                    Result.Installed(target.absolutePath, name)
                }
            } catch (t: Throwable) {
                Result.Failed(t.message ?: t.javaClass.simpleName)
            }
        }

    internal suspend fun remove(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        try {
            val file = File(path)
            val dir = file.parentFile ?: return@withContext
            if (dir.name == DIR_NAME) {
                file.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun displayName(context: Context, source: Uri): String {
        val raw = queryName(context, source) ?: "font"
        val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._\\u4e00-\\u9fff-]"), "_")
            .take(64)
        return when {
            cleaned.isBlank() -> "font.ttf"
            cleaned.contains('.') -> cleaned
            else -> "$cleaned.ttf"
        }
    }

    private fun queryName(context: Context, source: Uri): String? = try {
        context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun destinationDir(context: Context): File? {
        val roots = context.externalMediaDirs
        for (root in roots) {
            if (root == null) continue
            val dir = File(root, DIR_NAME)
            if (dir.isDirectory || dir.mkdirs()) {
                return dir
            }
        }
        return null
    }

    private fun writeVerified(input: InputStream, target: File): String? {
        try {
            target.outputStream().use { out ->
                input.copyTo(out)
                out.flush()
            }
        } catch (t: Throwable) {
            return "cannot write " + target.absolutePath + ": " + (t.message ?: t.javaClass.simpleName)
        }
        val length = if (target.isFile) target.length() else -1L
        if (length <= 0L) {
            return StringBuilder("the copy did not land: ")
                .append(target.absolutePath)
                .append(" readable=").append(target.isFile)
                .append(" length=").append(length)
                .toString()
        }
        return try {
            Typeface.createFromFile(target)
            null
        } catch (t: Throwable) {
            StringBuilder("the copied file is not a usable font (length=")
                .append(length)
                .append("): ")
                .append(t.message ?: t.javaClass.simpleName)
                .toString()
        }
    }

    private fun isFont(input: InputStream): Boolean {
        input.mark(MARK_LIMIT)
        val head = ByteArray(4)
        var read = 0
        try {
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
        } finally {
            input.reset()
        }
        if (read < head.size) return false
        return when {
            head[0] == 0x00.toByte() && head[1] == 0x01.toByte() &&
                head[2] == 0x00.toByte() && head[3] == 0x00.toByte() -> true          // TrueType
            head[0] == 'O'.code.toByte() && head[1] == 'T'.code.toByte() &&
                head[2] == 'T'.code.toByte() && head[3] == 'O'.code.toByte() -> true  // OpenType/CFF
            head[0] == 't'.code.toByte() && head[1] == 't'.code.toByte() &&
                head[2] == 'c'.code.toByte() && head[3] == 'f'.code.toByte() -> true  // collection
            else -> false
        }
    }

    private const val MARK_LIMIT = 1024
}
