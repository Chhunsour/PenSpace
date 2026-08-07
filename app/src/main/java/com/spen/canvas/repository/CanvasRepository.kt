package com.spen.canvas.repository

import android.content.Context

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.spen.canvas.model.AppSettings
import com.spen.canvas.model.CanvasDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID


/**
 * Local JSON persistence repository for multi-note storage and settings.
 */
class CanvasRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val notesDir: File by lazy {
        File(context.filesDir, "notes").apply { if (!exists()) mkdirs() }
    }
    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
    }

    suspend fun copyImageToLocalStore(uri: Uri): Pair<String, Pair<Int, Int>>? = withContext(Dispatchers.IO) {
        try {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destFile.absolutePath, options)
            val w = if (options.outWidth > 0) options.outWidth else 600
            val h = if (options.outHeight > 0) options.outHeight else 600

            destFile.absolutePath to (w to h)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapToLocalStore(bitmap: Bitmap): Pair<String, Pair<Int, Int>>? = withContext(Dispatchers.IO) {
        try {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)
            destFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            destFile.absolutePath to (bitmap.width to bitmap.height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    suspend fun getAllNotes(): List<CanvasDocument> = withContext(Dispatchers.IO) {
        migrateLegacyNoteIfNeeded()
        val files = notesDir.listFiles { file -> file.extension == "json" && file.name != SETTINGS_FILE } ?: return@withContext emptyList()
        val notes = mutableListOf<CanvasDocument>()
        for (file in files) {
            try {
                val json = file.readText()
                val doc = gson.fromJson(json, CanvasDocument::class.java)
                if (doc != null) notes.add(doc)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notes.sortedWith(compareByDescending<CanvasDocument> { it.isFavorite }.thenByDescending { it.lastModified })
    }

    suspend fun saveDocument(doc: CanvasDocument): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(notesDir, "${doc.id}.json")
            val json = gson.toJson(doc)
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadDocument(id: String): CanvasDocument? = withContext(Dispatchers.IO) {
        try {
            val file = File(notesDir, "$id.json")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            gson.fromJson(json, CanvasDocument::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteDocument(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(notesDir, "$id.json")
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun duplicateDocument(id: String): CanvasDocument? = withContext(Dispatchers.IO) {
        val original = loadDocument(id) ?: return@withContext null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            title = if (original.title.endsWith("(Copy)")) original.title else "${original.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        if (saveDocument(copy)) copy else null
    }

    suspend fun toggleFavorite(id: String): CanvasDocument? = withContext(Dispatchers.IO) {
        val doc = loadDocument(id) ?: return@withContext null
        val updated = doc.copy(isFavorite = !doc.isFavorite, lastModified = System.currentTimeMillis())
        if (saveDocument(updated)) updated else null
    }

    private fun migrateLegacyNoteIfNeeded() {
        val legacyFile = File(notesDir, "current_note.json")
        if (legacyFile.exists()) {
            try {
                val json = legacyFile.readText()
                val legacyDoc = gson.fromJson(json, CanvasDocument::class.java)
                if (legacyDoc != null) {
                    val migratedFile = File(notesDir, "${legacyDoc.id}.json")
                    migratedFile.writeText(gson.toJson(legacyDoc))
                }
                legacyFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveSettings(settings: AppSettings): Boolean =
        withContext(Dispatchers.IO) {
            try {
                File(notesDir, SETTINGS_FILE).writeText(gson.toJson(settings))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    suspend fun loadSettings(): AppSettings? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(notesDir, SETTINGS_FILE)
                if (!file.exists()) return@withContext null
                gson.fromJson(file.readText(), AppSettings::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    companion object {
        private const val SETTINGS_FILE = "app_settings.json"
    }
}

