package `in`.growurdhan.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Append-only diagnostics. Passwords, PAN, PDF bytes and vault blobs are never written.
 * Public copy: Download/GrowUrDhan-diagnostics.txt
 * Private copy: app external files (survives if Downloads insert fails).
 */
object Diag {
    const val FILE_NAME = "GrowUrDhan-diagnostics.txt"
    @Volatile var current: String = "app"
    @Volatile var publicPath: String = "Download/$FILE_NAME"
    @Volatile var privatePath: String = ""
    @Volatile var lastError: String = ""

    fun throwable(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString().replace("\n", " | ").take(1800)
    }

    fun enter(context: Context, service: String) {
        current = service
        log(context, service, "enter")
    }

    fun banner(context: Context) {
        val abi = Build.SUPPORTED_ABIS.joinToString(",")
        log(
            context,
            "boot",
            "sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} model=${Build.MODEL} abi=$abi pkg=${context.packageName}"
        )
    }

    fun log(context: Context, service: String, message: String) {
        val stamp = java.time.Instant.now().toString()
        val clean = redact(message).replace("\n", " | ").take(3500)
        val line = "$stamp $service $clean\n"
        Log.i("GrowUrDhan", line.trim())
        synchronized(this) {
            try {
                appendPrivate(context, line)
            } catch (e: Exception) {
                Log.e("GrowUrDhan", "private diag failed", e)
            }
            try {
                appendDownloads(context, line)
                lastError = ""
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
                Log.e("GrowUrDhan", "downloads diag failed", e)
                try {
                    appendPrivate(context, "$stamp diag-write-failed ${lastError}\n")
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun redact(message: String): String {
        return message
            .replace(Regex("(?i)(password|pan|pdfBase64|bytesB64|bytes_b64)\\s*[:=]\\s*\\S+"), "$1=<redacted>")
    }

    private fun appendPrivate(context: Context, line: String) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, FILE_NAME)
        trimFile(file)
        file.appendText(line)
        privatePath = file.absolutePath
    }

    private fun trimFile(file: File) {
        if (!file.exists() || file.length() < 400_000) return
        val text = file.readText()
        val keep = text.takeLast(180_000)
        file.writeText(keep)
    }

    private fun appendDownloads(context: Context, line: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            appendMediaStore(context, line)
            publicPath = "Download/$FILE_NAME"
            return
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, FILE_NAME)
        trimFile(file)
        file.appendText(line)
        publicPath = file.absolutePath
    }

    private fun appendMediaStore(context: Context, line: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var uri = findExisting(resolver, collection)
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore insert returned null")
        }
        val extra = line.toByteArray(Charsets.UTF_8)
        try {
            resolver.openOutputStream(uri, "wa")?.use { it.write(extra) }
                ?: throw IllegalStateException("openOutputStream wa returned null")
        } catch (appendErr: Exception) {
            val existing = try {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
            val merged = if (existing.size + extra.size > 400_000) {
                val start = (existing.size - 180_000).coerceAtLeast(0)
                existing.copyOfRange(start, existing.size) + extra
            } else {
                existing + extra
            }
            resolver.openOutputStream(uri, "wt")?.use { it.write(merged) }
                ?: throw IllegalStateException(
                    "Could not rewrite Download/$FILE_NAME after ${appendErr.javaClass.simpleName}"
                )
        }
    }

    private fun findExisting(resolver: android.content.ContentResolver, collection: Uri): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(FILE_NAME),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }
}
