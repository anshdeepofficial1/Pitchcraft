package com.inferno.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inferno.gallery.data.IndexingProgressManager
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.ai.FaceRecognitionEngine
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.FaceEntity
import com.inferno.gallery.data.db.PersonClusterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class FaceIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "pitchcraft_face_index"
        private const val NOTIFICATION_ID = 77
        private const val DISTANCE_THRESHOLD = 0.24f
    }

    private data class ClusterState(
        val id: Long,
        var centroid: FloatArray,
        var count: Int,
        var name: String?,
        var coverFaceId: Long?,
        var coverMediaId: Long?,
        var coverCropPath: String?,
        var favorite: Boolean,
        var hidden: Boolean
    )

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foreground("Preparing local face scan…")

    private fun foreground(message: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "People indexing", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Pitchcraft People")
            .setContentText(message)
            .setSilent(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.getInstance(applicationContext)
        if (!settings.faceIndexingEnabledFlow.first()) return Result.success()

        val db = DatabaseProvider.getDatabase(applicationContext)
        val excluded = settings.excludedFoldersFlow.first()
        val performance = settings.processingPerformanceFlow.first()

        val eligible = withContext(Dispatchers.IO) {
            db.mediaDao().getAllMedia()
                .filter { !it.isVideo && it.bucketName != "Trash" && !excluded.contains(it.bucketName) }
        }
        val unindexed = eligible.filter { !it.isIndexedFaces }
        val already = eligible.size - unindexed.size

        if (unindexed.isEmpty()) {
            IndexingProgressManager.updateFaceProgress(
                isIndexing = false,
                progress = eligible.size,
                total = eligible.size,
                currentImageName = null,
                stage = "Complete"
            )
            return Result.success()
        }

        val clusterStates = db.faceDao().getAllClusters().associate { cluster ->
            cluster.clusterId to ClusterState(
                id = cluster.clusterId,
                centroid = cluster.centroid.copyOf(),
                count = cluster.faceCount,
                name = cluster.name,
                coverFaceId = cluster.coverFaceId,
                coverMediaId = cluster.coverMediaId,
                coverCropPath = cluster.coverCropPath,
                favorite = cluster.isFavorite,
                hidden = cluster.isHidden
            )
        }.toMutableMap()

        var processed = 0
        var facesFound = db.faceDao().getAllFaces().size
        var failed = 0
        val recentUris = ArrayDeque<String>(6)

        IndexingProgressManager.updateFaceProgress(
            isIndexing = true,
            progress = already,
            total = eligible.size,
            currentImageName = "Loading face model…",
            facesFound = facesFound,
            failed = 0,
            stage = "Preparing"
        )
        setForeground(foreground("Loading local face model…"))

        FaceRecognitionEngine(applicationContext, performance).use { engine ->
            try {
                engine.prepare()
            } catch (t: Throwable) {
                IndexingProgressManager.updateFaceProgress(
                    isIndexing = false,
                    progress = already,
                    total = eligible.size,
                    currentImageName = t.message,
                    facesFound = facesFound,
                    failed = 1,
                    stage = "Model error"
                )
                return Result.retry()
            }

            for (media in unindexed) {
                if (isStopped) break

                IndexingProgressManager.updateFaceProgress(
                    isIndexing = true,
                    progress = already + processed,
                    total = eligible.size,
                    currentImageName = media.name,
                    recentUris = recentUris.toList(),
                    facesFound = facesFound,
                    failed = failed,
                    stage = "Detecting & grouping"
                )

                try {
                    val results = engine.analyze(media.id, android.net.Uri.parse(media.uriString))
                    db.withTransaction {
                        for (face in results) {
                            val cluster = findOrCreateCluster(db, clusterStates, face.embedding)
                            val faceId = db.faceDao().insertFace(
                                FaceEntity(
                                    mediaId = media.id,
                                    clusterId = cluster.id,
                                    boundingBoxLeft = face.left,
                                    boundingBoxTop = face.top,
                                    boundingBoxRight = face.right,
                                    boundingBoxBottom = face.bottom,
                                    cropCachePath = face.cropPath,
                                    embedding = face.embedding,
                                    confidence = 1f
                                )
                            )
                            facesFound++
                            updateClusterAfterFace(
                                db = db,
                                state = cluster,
                                faceId = faceId,
                                mediaId = media.id,
                                cropPath = face.cropPath,
                                embedding = face.embedding
                            )
                        }
                        db.mediaDao().updateFaceIndexStatus(media.id, true)
                    }
                } catch (t: Throwable) {
                    failed++
                    runCatching { db.mediaDao().updateFaceIndexStatus(media.id, true) }
                }

                processed++
                recentUris.addLast(media.uriString)
                if (recentUris.size > 6) recentUris.removeFirst()

                val current = already + processed
                IndexingProgressManager.updateFaceProgress(
                    isIndexing = true,
                    progress = current,
                    total = eligible.size,
                    currentImageName = media.name,
                    recentUris = recentUris.toList(),
                    facesFound = facesFound,
                    failed = failed,
                    stage = "Detecting & grouping"
                )
                setProgress(
                    workDataOf(
                        "progress" to current,
                        "total" to eligible.size,
                        "current_image" to media.name,
                        "faces_found" to facesFound,
                        "failed" to failed,
                        "recent_uris" to recentUris.toTypedArray()
                    )
                )
                if (processed == 1 || processed % 4 == 0 || processed == unindexed.size) {
                    setForeground(foreground("$current / ${eligible.size} • $facesFound faces"))
                }
            }
        }

        IndexingProgressManager.updateFaceProgress(
            isIndexing = false,
            progress = already + processed,
            total = eligible.size,
            currentImageName = null,
            recentUris = recentUris.toList(),
            facesFound = facesFound,
            failed = failed,
            stage = if (isStopped) "Paused" else "Complete"
        )
        return Result.success(
            workDataOf(
                "progress" to (already + processed),
                "total" to eligible.size,
                "faces_found" to facesFound,
                "failed" to failed
            )
        )
    }

    private suspend fun findOrCreateCluster(
        db: com.inferno.gallery.data.db.GalleryDatabase,
        states: MutableMap<Long, ClusterState>,
        embedding: FloatArray
    ): ClusterState {
        var best: ClusterState? = null
        var bestDistance = Float.MAX_VALUE
        for (cluster in states.values) {
            val distance = cosineDistance(cluster.centroid, embedding)
            if (distance < bestDistance) {
                bestDistance = distance
                best = cluster
            }
        }

        if (best != null && bestDistance <= DISTANCE_THRESHOLD) return best

        val id = db.faceDao().insertCluster(
            PersonClusterEntity(
                faceCount = 0,
                centroid = embedding.copyOf()
            )
        )
        return ClusterState(
            id = id,
            centroid = embedding.copyOf(),
            count = 0,
            name = null,
            coverFaceId = null,
            coverMediaId = null,
            coverCropPath = null,
            favorite = false,
            hidden = false
        ).also { states[id] = it }
    }

    private suspend fun updateClusterAfterFace(
        db: com.inferno.gallery.data.db.GalleryDatabase,
        state: ClusterState,
        faceId: Long,
        mediaId: Long,
        cropPath: String,
        embedding: FloatArray
    ) {
        val oldCount = state.count
        val newCount = oldCount + 1
        val combined = FloatArray(embedding.size)
        for (i in embedding.indices) {
            combined[i] = state.centroid[i] * oldCount + embedding[i]
        }
        state.centroid = l2(combined)
        state.count = newCount
        if (state.coverFaceId == null) {
            state.coverFaceId = faceId
            state.coverMediaId = mediaId
            state.coverCropPath = cropPath
        }

        db.faceDao().updateCluster(
            PersonClusterEntity(
                clusterId = state.id,
                name = state.name,
                coverFaceId = state.coverFaceId,
                coverMediaId = state.coverMediaId,
                coverCropPath = state.coverCropPath,
                faceCount = state.count,
                centroid = state.centroid,
                isFavorite = state.favorite,
                isHidden = state.hidden,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until n) dot += a[i] * b[i]
        return 1f - dot.coerceIn(-1f, 1f)
    }

    private fun l2(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).coerceAtLeast(1e-12f)
        for (i in v.indices) v[i] /= norm
        return v
    }
}
