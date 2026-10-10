package com.anshdeep.pitchcraft.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.anshdeep.pitchcraft.data.MediaItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FaceEmbeddingResult(
    val thumbnailPath: String,
    val embedding: FloatArray,
    val score: Float
)

class FaceEngine(private val context: Context) : AutoCloseable {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.05f)
        .enableTracking()
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    suspend fun analyze(item: MediaItem): List<FaceEmbeddingResult> = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(item.uri) ?: return@withContext emptyList()
        try {
            val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            if (faces.isEmpty()) return@withContext emptyList()
            ensureSession()
            val out = mutableListOf<FaceEmbeddingResult>()
            faces.forEachIndexed { index, face ->
                val crop = cropFace(bitmap, face.boundingBox, face.headEulerAngleZ)
                try {
                    val embedding = embed(crop)
                    val thumb = saveThumbnail(crop, item.id, index)
                    out += FaceEmbeddingResult(thumb.absolutePath, embedding, face.trackingId?.toFloat() ?: 1f)
                } finally {
                    crop.recycle()
                }
            }
            out
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun ensureSession() {
        if (session != null) return
        val model = ensureModel()
        session = env.createSession(model.absolutePath, OrtSession.SessionOptions())
    }

    private suspend fun ensureModel(): File = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models").apply { mkdirs() }
        val file = File(modelDir, MODEL_NAME)
        if (file.exists() && sha256(file).equals(MODEL_SHA256, ignoreCase = true)) {
            return@withContext file
        }
        file.delete()
        val temp = File(modelDir, MODEL_NAME + ".part")
        temp.delete()
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Pitchcraft/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) error("Model download failed: HTTP " + conn.responseCode)
            conn.inputStream.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
        if (!sha256(temp).equals(MODEL_SHA256, ignoreCase = true)) {
            temp.delete()
            error("Face model verification failed")
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        file
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val sampleSize = resolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            var sample = 1
            var w = options.outWidth
            var h = options.outHeight
            while (max(w / sample, h / sample) > 1800) sample *= 2
            sample
        } ?: 1
        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: return null
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream -> ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun cropFace(bitmap: Bitmap, rect: Rect, roll: Float): Bitmap {
        val padX = (rect.width() * 0.28f).toInt()
        val padY = (rect.height() * 0.36f).toInt()
        val left = max(0, rect.left - padX)
        val top = max(0, rect.top - padY)
        val right = min(bitmap.width, rect.right + padX)
        val bottom = min(bitmap.height, rect.bottom + padY)
        var crop = Bitmap.createBitmap(bitmap, left, top, max(1, right - left), max(1, bottom - top))
        if (kotlin.math.abs(roll) > 1f) {
            val matrix = Matrix().apply { postRotate(-roll) }
            val rotated = Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, matrix, true)
            if (rotated !== crop) crop.recycle()
            crop = rotated
        }
        val side = min(crop.width, crop.height)
        val x = (crop.width - side) / 2
        val y = (crop.height - side) / 2
        val square = Bitmap.createBitmap(crop, x, y, side, side)
        if (square !== crop) crop.recycle()
        return Bitmap.createScaledBitmap(square, 112, 112, true).also {
            if (it !== square) square.recycle()
        }
    }

    private fun embed(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(112 * 112)
        bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        val input = FloatArray(112 * 112 * 3)
        var p = 0
        pixels.forEach { color ->
            input[p++] = ((color shr 16) and 0xFF) / 127.5f - 1f
            input[p++] = ((color shr 8) and 0xFF) / 127.5f - 1f
            input[p++] = (color and 0xFF) / 127.5f - 1f
        }
        val activeSession = session ?: error("Face session is not ready")
        val inputName = activeSession.inputNames.first()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 112, 112, 3)).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val vector = flatten(result[0].value)
                var norm = 0f
                vector.forEach { norm += it * it }
                norm = sqrt(norm).takeIf { it > 0f } ?: 1f
                return FloatArray(vector.size) { vector[it] / norm }
            }
        }
    }

    private fun flatten(value: Any?): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                val parts = value.map { flatten(it) }
                val size = parts.sumOf { it.size }
                FloatArray(size).also { out ->
                    var offset = 0
                    parts.forEach { part ->
                        part.copyInto(out, offset)
                        offset += part.size
                    }
                }
            }
            else -> error("Unsupported face model output")
        }
    }

    private fun saveThumbnail(bitmap: Bitmap, mediaId: Long, index: Int): File {
        val dir = File(context.filesDir, "face_thumbs").apply { mkdirs() }
        val file = File(dir, "f_" + mediaId + "_" + index + ".jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        detector.close()
        session?.close()
        session = null
    }

    companion object {
        private const val MODEL_NAME = "mobilefacenet_portable_static_b1.onnx"
        private const val MODEL_URL = "https://models.ente.com/mobilefacenet_portable_static_b1.onnx"
        private const val MODEL_SHA256 = "0763fc33f54e138476194da95987e133b3e976075a6b1d3e1b2caedb251b1a36"
    }
}
