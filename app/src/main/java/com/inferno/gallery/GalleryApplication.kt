package com.inferno.gallery

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inferno.gallery.workers.MediaSyncWorker
import com.inferno.gallery.workers.OcrIndexWorker
import com.inferno.gallery.workers.ReverseGeocodeWorker

import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.MediaStoreThumbnailFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath


class GalleryApplication : Application(), SingletonImageLoader.Factory, androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    override fun onCreate() {
        super.onCreate()

        // CrashActivity runs in a dedicated :crash process. Keep that process tiny.
        if (android.app.Application.getProcessName() != packageName) return

        // IMPORTANT: Do not open Room or enqueue WorkManager jobs here.
        // The launcher must be able to draw a diagnostic UI before any heavy
        // component is initialized. Background work starts only after MainActivity
        // finishes its startup self-check.
        com.inferno.gallery.crash.CrashHandler.install(this)
    }

    @Volatile
    private var backgroundWorkScheduled = false

    @Synchronized
    fun scheduleBackgroundWork() {
        if (backgroundWorkScheduled) return
        backgroundWorkScheduled = true

        val startupExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("GalleryApplication", "Background scheduling error", throwable)
            backgroundWorkScheduled = false
        }

        kotlinx.coroutines.MainScope().launch(startupExceptionHandler) {
            val settingsRepo = SettingsRepository.getInstance(this@GalleryApplication)
            val syncWorkRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()

            val autoMlProcessing = settingsRepo.autoMlProcessingFlow.first()
            val ocrEnabled = settingsRepo.ocrIndexingEnabledFlow.first()
            if (autoMlProcessing && ocrEnabled) {
                val ocrIndexRequest = androidx.work.OneTimeWorkRequestBuilder<OcrIndexWorker>()
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(this@GalleryApplication)
                    .beginUniqueWork(
                        "MediaSyncWorker",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        syncWorkRequest
                    )
                    .then(ocrIndexRequest)
                    .enqueue()
            } else {
                WorkManager.getInstance(this@GalleryApplication)
                    .enqueueUniqueWork(
                        "MediaSyncWorker",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        syncWorkRequest
                    )
            }

            val syncPeriodicRequest =
                androidx.work.PeriodicWorkRequestBuilder<MediaSyncWorker>(
                    6,
                    java.util.concurrent.TimeUnit.HOURS
                ).build()
            WorkManager.getInstance(this@GalleryApplication)
                .enqueueUniquePeriodicWork(
                    "MediaSyncWorkerPeriodic",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    syncPeriodicRequest
                )

            val geocodeRequest =
                androidx.work.OneTimeWorkRequestBuilder<ReverseGeocodeWorker>().build()
            WorkManager.getInstance(this@GalleryApplication)
                .enqueueUniqueWork(
                    "ReverseGeocodeWorker",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    geocodeRequest
                )

            val geocodePeriodicRequest =
                androidx.work.PeriodicWorkRequestBuilder<ReverseGeocodeWorker>(
                    24,
                    java.util.concurrent.TimeUnit.HOURS
                ).build()
            WorkManager.getInstance(this@GalleryApplication)
                .enqueueUniquePeriodicWork(
                    "ReverseGeocodeWorkerPeriodic",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    geocodePeriodicRequest
                )

            if (!autoMlProcessing) {
                WorkManager.getInstance(this@GalleryApplication).cancelUniqueWork("OcrIndexWorker")
                WorkManager.getInstance(this@GalleryApplication).cancelUniqueWork("FaceIndexWorker")
                WorkManager.getInstance(this@GalleryApplication).cancelUniqueWork("SmartSearchIndexWorker")
            }

            val autoCleanEnabled = settingsRepo.autoCleanTrashEnabledFlow.first()
            if (autoCleanEnabled) {
                val workRequest =
                    androidx.work.PeriodicWorkRequestBuilder<com.inferno.gallery.workers.AutoCleanTrashWorker>(
                        24,
                        java.util.concurrent.TimeUnit.HOURS
                    ).build()
                WorkManager.getInstance(this@GalleryApplication)
                    .enqueueUniquePeriodicWork(
                        "AutoCleanTrashWorker",
                        androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                    )
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Support high-performance system thumbnails for local MediaStore items
                add(MediaStoreThumbnailFetcher.Factory(context))
                add(MediaStoreThumbnailFetcher.AndroidUriFactory(context))
                // Support SVGs
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.40) // 40% — extra headroom for large galleries; GPU-resident HARDWARE bitmaps live in GPU memory, so Java heap stays healthy
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(512L * 1024L * 1024L) // 512 MB disk cache
                    .build()
            }
            // NOTE: Global crossfade intentionally removed — it caused 40+ simultaneous
            // crossfade animation frames during fast scroll, flooding the main thread.
            // Per-request crossfade is already disabled in GalleryGridItem (crossfade=false).
            .build()
    }
}
