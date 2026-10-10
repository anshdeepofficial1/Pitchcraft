package com.anshdeep.pitchcraft.ml

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anshdeep.pitchcraft.data.FaceDatabase
import com.anshdeep.pitchcraft.data.GalleryRepository

class FaceScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = FaceDatabase.get(applicationContext)
        val images = GalleryRepository(applicationContext).loadImages()
        db.setMeta("scan_running", "1")
        db.setMeta("scan_error", "")
        setForeground(notification(0, images.size, "Preparing local face index"))

        return try {
            FaceEngine(applicationContext).use { faceEngine ->
                VisualIndexEngine(applicationContext).use { visualEngine ->
                    images.forEachIndexed { index, item ->
                        if (isStopped) return@use
                        db.setMeta("scan_current", item.name)

                        if (!db.isScanned(item.id)) {
                            try {
                                val faces = faceEngine.analyze(item)
                                faces.forEach { face ->
                                    db.addFace(
                                        mediaId = item.id,
                                        uri = item.uri.toString(),
                                        thumbPath = face.thumbnailPath,
                                        embedding = face.embedding,
                                        score = face.score
                                    )
                                }
                                db.markScanned(item.id)
                            } catch (t: Throwable) {
                                db.markScanned(item.id, t.message ?: t.javaClass.simpleName)
                            }
                        }

                        if (!db.isVisualIndexed(item.id)) {
                            try {
                                val visual = visualEngine.analyze(item.uri)
                                db.saveVisualIndex(item.id, visual.text, visual.labels)
                            } catch (_: Throwable) {
                                // OCR/labels are supplemental; face indexing keeps going.
                            }
                        }

                        if (index % 5 == 0 || index == images.lastIndex) {
                            setForeground(notification(index + 1, images.size, item.name))
                            setProgress(androidx.work.workDataOf("done" to (index + 1), "total" to images.size))
                        }
                    }
                }
            }
            db.setMeta("scan_running", "0")
            db.setMeta("scan_current", "")
            Result.success()
        } catch (t: Throwable) {
            db.setMeta("scan_running", "0")
            db.setMeta("scan_error", t.message ?: "Face indexing failed")
            Result.failure()
        }
    }

    private fun notification(done: Int, total: Int, current: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Face indexing", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Pitchcraft is organizing People")
            .setContentText(current)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0) builder.setProgress(total, done.coerceAtMost(total), false)
        else builder.setProgress(0, 0, true)
        return ForegroundInfo(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val UNIQUE_WORK = "pitchcraft-face-index"
        private const val CHANNEL_ID = "face_index"
        private const val NOTIFICATION_ID = 4102

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FaceScanWorker>().build()
            )
        }
    }
}
