package com.spen.canvas.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.spen.canvas.model.InkStroke
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Modern ML Kit Digital Ink Handwriting Recognition Manager.
 * Supports English ('en') and is modularly prepared for Khmer ('km').
 */
class HandwritingRecognizer {

    private val modelManager = RemoteModelManager.getInstance()

    suspend fun recognizeStrokes(
        strokes: List<InkStroke>,
        languageCode: String = "en"
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val modelIdentifier = try {
                DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageCode)
            } catch (e: Exception) {
                null
            }

            if (modelIdentifier == null) {
                continuation.resume(Result.failure(IllegalArgumentException("Unsupported language code: $languageCode")))
                return@suspendCancellableCoroutine
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    if (isDownloaded) {
                        performRecognition(model, strokes) { textResult ->
                            continuation.resume(textResult)
                        }
                    } else {
                        // Download model on-demand
                        modelManager.download(model, DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                performRecognition(model, strokes) { textResult ->
                                    continuation.resume(textResult)
                                }
                            }
                            .addOnFailureListener { err ->
                                continuation.resume(Result.failure(err))
                            }
                    }
                }
                .addOnFailureListener { err ->
                    continuation.resume(Result.failure(err))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    private fun performRecognition(
        model: DigitalInkRecognitionModel,
        strokes: List<InkStroke>,
        onComplete: (Result<String>) -> Unit
    ) {
        val inkBuilder = Ink.builder()

        for (stroke in strokes) {
            val strokeBuilder = Ink.Stroke.builder()
            for (pt in stroke.points) {
                val point = Ink.Point.create(pt.x, pt.y, pt.timestamp)
                strokeBuilder.addPoint(point)
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val ink = inkBuilder.build()
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )

        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                val candidateText = result.candidates.firstOrNull()?.text ?: ""
                onComplete(Result.success(candidateText))
            }
            .addOnFailureListener { err ->
                onComplete(Result.failure(err))
            }
    }
}
