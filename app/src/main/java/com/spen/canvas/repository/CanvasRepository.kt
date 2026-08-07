package com.spen.canvas.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.spen.canvas.model.CanvasDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local JSON persistence repository for reliable autosaving of canvas documents.
 */
class CanvasRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val notesDir: File by lazy {
        File(context.filesDir, "notes").apply { if (!exists()) mkdirs() }
    }

    suspend fun saveDocument(doc: CanvasDocument, filename: String = "current_note.json"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(notesDir, filename)
                val json = gson.toJson(doc)
                file.writeText(json)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    suspend fun loadDocument(filename: String = "current_note.json"): CanvasDocument? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(notesDir, filename)
                if (!file.exists()) return@withContext null
                val json = file.readText()
                gson.fromJson(json, CanvasDocument::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
