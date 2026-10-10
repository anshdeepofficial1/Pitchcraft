package com.inferno.gallery.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.inferno.gallery.data.ProcessingPerformance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.sqrt

data class LocalFaceResult(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cropPath: String,
    val embedding: FloatArray
)

class FaceRecognitionEngine(
    private val context: Context,
    private val performance: ProcessingPerformance
) : AutoCloseable {
    companion object {
        private const val TAG = "FaceRecognitionEngine"
        private const val MODEL_NAME = "mobilefacenet_portable_static_b1.onnx"
        private const val MODEL_URL = "https://models.ente.com/mobilefacenet_portable_static_b1.onnx"
        private const val MODEL_SHA256 = "0763fc33f54e138476194da95987e133b3e976075a6b1d3e1b2caedb251b1a36"
        private const val MODEL_SIZE = 5_278_803L
        private const val FACE_SIZE = 112
        private const val MAX_DECODE_DIMENSION = 2400
    }

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.04f)
            .build()
    )

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    suspend fun prepare() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext
        val model = ensureModel()
        val options = OrtSession.SessionOptions()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val threads = when (performance) {
            ProcessingPerformance.BALANCED -> minOf(2, cores)
            ProcessingPerformance.FAST -> minOf(4, cores)
            ProcessingPerformance.MAX -> minOf(8, cores)
        }
        runCatching { options.setIntraOpNumThreads(threads) }
        runCatching { options.setInterOpNumThreads(1) }
        session = env.createSession(model.absolutePath, options)
        Log.d(TAG, "MobileFaceNet loaded with $threads CPU threads")
    }

    suspend fun analyze(mediaId: Long, uri: Uri): List<LocalFaceResult> {
        prepare()
        val bitmap = decodeBitmap(uri) ?: return emptyList()
        try {
            val faces = detectFaces(bitmap)
            if (faces.isEmpty()) return emptyList()

            val out = ArrayList<LocalFaceResult>(faces.size)
            for ((index, face) in faces.withIndex()) {
                val aligned = alignFace(bitmap, face) ?: continue
                try {
                    val embedding = embed(aligned)
                    val path = saveFaceCrop(mediaId, index, aligned)
                    val box = face.boundingBox
                    out += LocalFaceResult(
                        left = (box.left.toFloat() / bitmap.width).coerceIn(0f, 1f),
                        top = (box.top.toFloat() / bitmap.height).coerceIn(0f, 1f),
                        right = (box.right.toFloat() / bitmap.width).coerceIn(0f, 1f),
                        bottom = (box.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f),
                        cropPath = path,
                        embedding = embedding
                    )
                } finally {
                    aligned.recycle()
                }
            }
            return out
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > MAX_DECODE_DIMENSION) {
                    decoder.setTargetSampleSize(ceil(maxDim / MAX_DECODE_DIMENSION.toDouble()).toInt())
                }
            }.let { sourceBitmap ->
                if (sourceBitmap.config == Bitmap.Config.ARGB_8888 && sourceBitmap.isMutable) {
                    sourceBitmap
                } else {
                    sourceBitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                        if (it !== sourceBitmap) sourceBitmap.recycle()
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Decode failed for $uri", it) }.getOrNull()
    }

    private fun alignFace(source: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        val output = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        return try {
            if (leftEye != null && rightEye != null) {
                val src = floatArrayOf(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
                val dst = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f)
                val matrix = Matrix()
                if (!matrix.setPolyToPoly(src, 0, dst, 0, 2)) {
                    output.recycle()
                    fallbackCrop(source, face)
                } else {
                    canvas.drawBitmap(source, matrix, paint)
                    output
                }
            } else {
                output.recycle()
                fallbackCrop(source, face)
            }
        } catch (t: Throwable) {
            output.recycle()
            Log.w(TAG, "Face alignment failed", t)
            null
        }
    }

    private fun fallbackCrop(source: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val expandX = box.width() * 0.28f
        val expandTop = box.height() * 0.38f
        val expandBottom = box.height() * 0.20f
        val l = (box.left - expandX).toInt().coerceIn(0, source.width - 1)
        val t = (box.top - expandTop).toInt().coerceIn(0, source.height - 1)
        val r = (box.right + expandX).toInt().coerceIn(l + 1, source.width)
        val b = (box.bottom + expandBottom).toInt().coerceIn(t + 1, source.height)
        if (r <= l || b <= t) return null
        val crop = Bitmap.createBitmap(source, l, t, r - l, b - t)
        return Bitmap.createScaledBitmap(crop, FACE_SIZE, FACE_SIZE, true).also {
            if (it !== crop) crop.recycle()
        }
    }

    private fun embed(face: Bitmap): FloatArray {
        val current = session ?: error("Face model not loaded")
        val pixels = IntArray(FACE_SIZE * FACE_SIZE)
        face.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)
        val input = FloatArray(FACE_SIZE * FACE_SIZE * 3)
        var o = 0
        for (pixel in pixels) {
            input[o++] = (((pixel shr 16) and 0xff) / 127.5f) - 1f
            input[o++] = (((pixel shr 8) and 0xff) / 127.5f) - 1f
            input[o++] = ((pixel and 0xff) / 127.5f) - 1f
        }

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, FACE_SIZE.toLong(), FACE_SIZE.toLong(), 3)
        )
        tensor.use {
            val inputName = current.inputNames.first()
            current.run(mapOf(inputName to tensor)).use { result ->
                val value = result[0].value
                val raw = when (value) {
                    is Array<*> -> {
                        val first = value.firstOrNull()
                        when (first) {
                            is FloatArray -> first
                            is Array<*> -> first.firstOrNull() as? FloatArray
                            else -> null
                        }
                    }
                    is FloatArray -> value
                    else -> null
                } ?: error("Unexpected MobileFaceNet output: ${value?.javaClass}")
                return l2(raw.copyOf())
            }
        }
    }

    private fun l2(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val n = sqrt(sum).coerceAtLeast(1e-12f)
        for (i in v.indices) v[i] /= n
        return v
    }

    private fun saveFaceCrop(mediaId: Long, index: Int, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "face_crops").apply { mkdirs() }
        val file = File(dir, "${mediaId}_${index}_${System.nanoTime()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    private fun ensureModel(): File {
        val dir = File(context.filesDir, "face_models").apply { mkdirs() }
        val target = File(dir, MODEL_NAME)
        if (target.exists() && target.length() == MODEL_SIZE && sha256(target) == MODEL_SHA256) return target
        target.delete()

        val copied = runCatching {
            context.assets.open("models/$MODEL_NAME").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.length() == MODEL_SIZE && sha256(target) == MODEL_SHA256
        }.getOrDefault(false)
        if (copied) return target
        target.delete()

        val temp = File(dir, "$MODEL_NAME.tmp")
        temp.delete()
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Pitchcraft/1.0")
        connection.inputStream.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output, 128 * 1024) }
        }
        connection.disconnect()
        check(temp.length() == MODEL_SIZE) { "Face model size mismatch" }
        check(sha256(temp) == MODEL_SHA256) { "Face model checksum mismatch" }
        check(temp.renameTo(target)) { "Unable to activate face model" }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        detector.close()
        session?.close()
        session = null
    }
}
