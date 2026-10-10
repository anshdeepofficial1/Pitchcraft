package com.inferno.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inferno.gallery.data.db.CoreMediaEntity
import com.inferno.gallery.data.db.DatabaseProvider
import android.graphics.BitmapFactory
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine

class OcrIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "OcrIndexWorker"
        private const val NOTIFICATION_ID = 44
        private const val CHANNEL_ID = "ocr_indexing_channel"
        private const val WRITE_BATCH_SIZE = 1
        private const val LOAD_CHANNEL_CAPACITY = 2
        private const val EMBED_CHANNEL_CAPACITY = 2
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing…")

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Text Indexing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while indexing photos for text search" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Text (OCR) Indexing")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun safeSetForeground(progress: String) {
        try {
            setForeground(createForegroundInfo(progress))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set/update foreground service: ${e.message}")
        }
    }

    private data class LoadedImage(
        val entity: CoreMediaEntity,
        val bitmap: Bitmap
    )

    private data class EmbeddedImage(
        val entity: CoreMediaEntity,
        val extractedText: String?
    )

    private fun openInputStream(context: Context, filePath: String, uriString: String): InputStream {
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(uriString))
            if (input != null) return input
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "ContentResolver failed to open Uri $uriString: ${e.message}")
        }

        val file = java.io.File(filePath)
        if (!file.exists() || !file.canRead()) {
            throw java.io.FileNotFoundException("File does not exist or cannot be read: $filePath")
        }
        return file.inputStream()
    }

    private fun decodeOcrBitmap(context: Context, filePath: String, uriString: String): Bitmap? {
        var input: InputStream? = null
        try {
            input = openInputStream(context, filePath, uriString)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            // Downsample if either dimension exceeds 1600px
            val maxDim = 1600
            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxDim || h > maxDim) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            input = openInputStream(context, filePath, uriString)
            val decoded = BitmapFactory.decodeStream(input, null, decodeOptions)
            return decoded
        } finally {
            try {
                input?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override suspend fun doWork(): Result {
        com.inferno.gallery.data.IndexingProgressManager.updateOcrProgress(
            isIndexing = true,
            progress = 0,
            total = 0,
            currentImageName = "Initializing..."
        )
        try {
            safeSetForeground("Starting…")

            val db = DatabaseProvider.getDatabase(applicationContext)

            val excludedFolders = com.inferno.gallery.data.SettingsRepository
                .getInstance(applicationContext)
                .excludedFoldersFlow
                .first()

            val unindexed = withContext(Dispatchers.IO) {
                db.mediaDao().getUnindexedOcrMedia()
                    .filterNot { excludedFolders.contains(it.bucketName) }
            }

            if (unindexed.isEmpty()) {
                Log.d(TAG, "Nothing to index.")
                return Result.success()
            }

            val totalImageCount = withContext(Dispatchers.IO) {
                db.mediaDao().getAllMedia()
                    .count { !it.isVideo && it.bucketName != "Trash" && !excludedFolders.contains(it.bucketName) }
            }
            val alreadyIndexed = (totalImageCount - unindexed.size).coerceAtLeast(0)
            var processedCount = 0
            val recentUris = ArrayDeque<String>(5)

            Log.d(TAG, "OCR indexing started. Unindexed: ${unindexed.size}, total: $totalImageCount")

            com.inferno.gallery.data.IndexingProgressManager.updateOcrProgress(
                isIndexing = true,
                progress = alreadyIndexed,
                total = totalImageCount,
                currentImageName = "Starting..."
            )

            setProgress(workDataOf(
                "progress" to alreadyIndexed,
                "total" to totalImageCount,
                "recent_uris" to emptyArray<String>()
            ))

            val performance = com.inferno.gallery.data.SettingsRepository
                .getInstance(applicationContext)
                .processingPerformanceFlow
                .first()
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val recognizerCount = when (performance) {
                com.inferno.gallery.data.ProcessingPerformance.BALANCED -> 1
                com.inferno.gallery.data.ProcessingPerformance.FAST -> minOf(2, cores)
                com.inferno.gallery.data.ProcessingPerformance.MAX -> minOf(4, maxOf(2, cores / 2))
            }
            val channelCapacity = maxOf(2, recognizerCount * 2)
            val loadChannel  = Channel<LoadedImage>(channelCapacity)
            val embedChannel = Channel<EmbeddedImage>(channelCapacity)

            coroutineScope {
                launch(Dispatchers.IO) {
                    for (entity in unindexed) {
                        if (isStopped) break
                        try {
                            val bitmap = decodeOcrBitmap(applicationContext, entity.filePath, entity.uriString)
                            if (bitmap != null) {
                                loadChannel.send(LoadedImage(entity, bitmap))
                            } else {
                                Log.w(TAG, "Bitmap decoding returned null for OCR: ${entity.name}. Marking as indexed.")
                                db.mediaDao().updateOcrIndexStatus(entity.id, true)
                            }

                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: java.io.FileNotFoundException) {
                            Log.e(TAG, "FileNotFoundException loading ${entity.id} for OCR (${e.message}). Marking as indexed.")
                            runCatching { 
                                db.mediaDao().updateOcrIndexStatus(entity.id, true)
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "SecurityException loading ${entity.id} for OCR: ${e.message}. Skipping to retry later.")
                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage1 load failed for ${entity.name}: ${e.message}")
                        }
                    }
                    loadChannel.close()
                }

                val inferenceJobs = List(recognizerCount) {
                    launch(Dispatchers.Default) {
                        val textRecognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                        )
                        try {
                            for (loaded in loadChannel) {
                                if (isStopped) {
                                    loaded.bitmap.recycle()
                                    break
                                }
                                try {
                                    val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(
                                        loaded.bitmap,
                                        0
                                    )
                                    val extractedText = suspendCancellableCoroutine { cont ->
                                        textRecognizer.process(inputImage)
                                            .addOnSuccessListener { visionText ->
                                                cont.resumeWith(kotlin.Result.success(visionText.text))
                                            }
                                            .addOnFailureListener {
                                                cont.resumeWith(kotlin.Result.success(null))
                                            }
                                    }

                                    embedChannel.send(EmbeddedImage(loaded.entity, extractedText))

                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    Log.e(TAG, "Stage2 inference failed for ${loaded.entity.name}: ${e.message}")
                                } finally {
                                    loaded.bitmap.recycle()
                                }
                            }
                        } finally {
                            textRecognizer.close()
                        }
                    }
                }

                launch {
                    inferenceJobs.joinAll()
                    embedChannel.close()
                }

                launch(Dispatchers.IO) {
                    val ocrIndexedBatch  = mutableListOf<Long>()

                    suspend fun flush() {
                        if (ocrIndexedBatch.isNotEmpty()) {
                            db.mediaDao().markOcrIndexed(ocrIndexedBatch.toList())
                        }
                        ocrIndexedBatch.clear()
                    }

                    for (embedded in embedChannel) {
                        if (isStopped) break

                        if (!embedded.extractedText.isNullOrBlank()) {
                            DatabaseProvider.insertFtsRow(db, embedded.entity.id, embedded.extractedText, "")
                        }
                        // Always mark as indexed — even if text is null (ML failure) or blank (no text found).
                        // Without this, failed images stay "unindexed" forever, causing the progress
                        // to get stuck at 99% / last item since they re-fail on every retry.
                        ocrIndexedBatch.add(embedded.entity.id)

                        processedCount++
                        recentUris.addLast(embedded.entity.uriString)
                        if (recentUris.size > 5) recentUris.removeFirst()

                        if (ocrIndexedBatch.size >= WRITE_BATCH_SIZE) {
                            flush()
                        }

                        val current = alreadyIndexed + processedCount
                        com.inferno.gallery.data.IndexingProgressManager.updateOcrProgress(
                            isIndexing = true,
                            progress = current,
                            total = totalImageCount,
                            currentImageName = embedded.entity.name,
                            recentUris = recentUris.toList(),
                            stage = "Text recognition"
                        )
                        setProgress(workDataOf(
                            "progress" to current,
                            "total" to totalImageCount,
                            "current_image" to embedded.entity.name,
                            "recent_uris" to recentUris.toTypedArray()
                        ))
                        safeSetForeground("$current / $totalImageCount images")
                    }

                    flush()
                }
            } 

            Log.d(TAG, "OCR indexing complete. Indexed $processedCount images.")
            return Result.success()

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "OcrIndexWorker failed fatally: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        } finally {
            val db = DatabaseProvider.getDatabase(applicationContext)
            val excluded = runCatching {
                com.inferno.gallery.data.SettingsRepository.getInstance(applicationContext)
                    .excludedFoldersFlow.first()
            }.getOrDefault(emptySet())
            val total = runCatching {
                db.mediaDao().getAllMedia().count { !it.isVideo && it.bucketName != "Trash" && !excluded.contains(it.bucketName) }
            }.getOrDefault(0)
            val unindexed = runCatching {
                db.mediaDao().getUnindexedOcrMedia().count { !excluded.contains(it.bucketName) }
            }.getOrDefault(0)
            com.inferno.gallery.data.IndexingProgressManager.updateOcrProgress(
                isIndexing = false,
                progress = (total - unindexed).coerceAtLeast(0),
                total = total,
                currentImageName = null
            )
        }
    }
}
