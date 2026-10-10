package com.anshdeep.pitchcraft.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max

data class VisualIndexResult(
    val text: String,
    val labels: List<String>
)

class VisualIndexEngine(private val context: Context) : AutoCloseable {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.55f)
            .build()
    )

    suspend fun analyze(uri: Uri): VisualIndexResult = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(uri) ?: return@withContext VisualIndexResult("", emptyList())
        try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val textResult = runCatching { textRecognizer.process(input).await().text }.getOrDefault("")
            val labels = runCatching {
                labeler.process(input).await()
                    .sortedByDescending { it.confidence }
                    .take(12)
                    .map { it.text }
                    .distinct()
            }.getOrDefault(emptyList())
            VisualIndexResult(textResult, labels)
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val sample = resolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            var s = 1
            while (max(opts.outWidth / s, opts.outHeight / s) > 1600) s *= 2
            s
        } ?: 1
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }
    }

    override fun close() {
        textRecognizer.close()
        labeler.close()
    }
}
