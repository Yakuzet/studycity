package com.derscalisma.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Uygulama silinse bile kalan yedek: İndirilenler/StudyCity/studycity-yedek.json (Android 10+).
 * Uygulama kendi oluşturduğu dosyayı izin istemeden güncelleyebilir.
 */
object ExternalBackup {
    const val FILE_NAME = "studycity-yedek.json"
    const val FOLDER = "StudyCity"
    private const val PREFS = "backup"
    private const val KEY_LAST = "last_external"
    const val INTERVAL_MS = 30 * 60_000L

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun lastBackupAt(ctx: Context): Long = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    fun write(ctx: Context, text: String): Boolean {
        if (!supported) return false
        return try {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
            val existing: Uri? = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(rel, FILE_NAME),
                null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
            val uri = existing ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                },
            ) ?: return false
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: return false
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
