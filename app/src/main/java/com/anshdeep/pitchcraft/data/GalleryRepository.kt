package com.anshdeep.pitchcraft.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val dateMillis: Long,
    val bucket: String,
    val size: Long,
    val width: Int,
    val height: Int
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

class GalleryRepository(private val context: Context) {

    fun loadMedia(): List<MediaItem> =
        (queryImages() + queryVideos()).sortedByDescending { it.dateMillis }

    fun loadImages(): List<MediaItem> = queryImages().sortedByDescending { it.dateMillis }

    private fun queryImages(): List<MediaItem> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        idColumn = MediaStore.Images.Media._ID
    )

    private fun queryVideos(): List<MediaItem> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        idColumn = MediaStore.Video.Media._ID
    )

    private fun query(collection: Uri, idColumn: String): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val projection = arrayOf(
            idColumn,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val mime = cursor.getString(mimeIndex) ?: "image/*"
                    val taken = if (takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val added = cursor.getLong(addedIndex) * 1000L
                    result += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameIndex) ?: "Media " + id,
                        mime = mime,
                        dateMillis = if (taken > 0L) taken else added,
                        bucket = if (bucketIndex >= 0) cursor.getString(bucketIndex) ?: "Other" else "Other",
                        size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                        width = if (widthIndex >= 0) cursor.getInt(widthIndex) else 0,
                        height = if (heightIndex >= 0) cursor.getInt(heightIndex) else 0
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return result
    }
}
