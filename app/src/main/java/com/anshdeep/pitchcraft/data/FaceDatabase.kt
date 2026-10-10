package com.anshdeep.pitchcraft.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class PersonGroup(
    val id: Long,
    val name: String?,
    val coverPath: String?,
    val count: Int
)

data class ScanStats(
    val total: Int = 0,
    val scanned: Int = 0,
    val faces: Int = 0,
    val people: Int = 0,
    val failed: Int = 0,
    val visualIndexed: Int = 0,
    val running: Boolean = false,
    val current: String = "",
    val error: String? = null
) {
    val progress: Float get() = if (total <= 0) 0f else scanned.toFloat() / total.toFloat()
}

class FaceDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "pitchcraft.db", null, 2) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scanned(media_id INTEGER PRIMARY KEY, status INTEGER NOT NULL, error TEXT, scanned_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, cover_path TEXT, centroid BLOB NOT NULL, face_count INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE faces(id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER NOT NULL, uri TEXT NOT NULL, person_id INTEGER NOT NULL, thumb_path TEXT, embedding BLOB NOT NULL, score REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_faces_person ON faces(person_id)")
        db.execSQL("CREATE INDEX idx_faces_media ON faces(media_id)")
        db.execSQL("CREATE TABLE favorites(media_id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE visual_index(media_id INTEGER PRIMARY KEY, ocr_text TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', indexed_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS visual_index(media_id INTEGER PRIMARY KEY, ocr_text TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', indexed_at INTEGER NOT NULL)")
        }
    }

    fun isVisualIndexed(mediaId: Long): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM visual_index WHERE media_id=?", arrayOf(mediaId.toString()))
            .use { it.moveToFirst() }

    fun saveVisualIndex(mediaId: Long, ocrText: String, labels: List<String>) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO visual_index(media_id,ocr_text,labels,indexed_at) VALUES(?,?,?,?)",
            arrayOf(mediaId, ocrText.trim(), labels.joinToString(" | "), System.currentTimeMillis())
        )
    }

    fun searchVisualIndex(query: String): Set<Long> {
        val clean = query.trim()
        if (clean.isBlank()) return emptySet()
        val ids = linkedSetOf<Long>()
        val like = "%$clean%"
        readableDatabase.rawQuery(
            "SELECT media_id FROM visual_index WHERE ocr_text LIKE ? COLLATE NOCASE OR labels LIKE ? COLLATE NOCASE",
            arrayOf(like, like)
        ).use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
    }

    fun isScanned(mediaId: Long): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM scanned WHERE media_id=?", arrayOf(mediaId.toString()))
            .use { it.moveToFirst() }

    fun markScanned(mediaId: Long, error: String? = null) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO scanned(media_id,status,error,scanned_at) VALUES(?,?,?,?)",
            arrayOf(mediaId, if (error == null) 1 else 0, error, System.currentTimeMillis())
        )
    }

    fun addFace(mediaId: Long, uri: String, thumbPath: String?, embedding: FloatArray, score: Float) {
        val normalized = normalize(embedding)
        val db = writableDatabase
        db.beginTransaction()
        try {
            var bestId = -1L
            var bestDistance = Float.MAX_VALUE
            var bestCount = 0
            var bestCentroid: FloatArray? = null
            db.rawQuery("SELECT id, centroid, face_count FROM people", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val candidate = bytesToFloats(cursor.getBlob(1))
                    val distance = cosineDistance(normalized, candidate)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestId = cursor.getLong(0)
                        bestCount = cursor.getInt(2)
                        bestCentroid = candidate
                    }
                }
            }

            val personId: Long
            if (bestId > 0 && bestDistance <= 0.24f) {
                personId = bestId
                val old = bestCentroid ?: normalized
                val updated = FloatArray(normalized.size) { index ->
                    ((old[index] * bestCount.toFloat()) + normalized[index]) / (bestCount + 1).toFloat()
                }
                val finalCentroid = normalize(updated)
                db.execSQL(
                    "UPDATE people SET centroid=?, face_count=face_count+1, cover_path=COALESCE(cover_path,?) WHERE id=?",
                    arrayOf(floatsToBytes(finalCentroid), thumbPath, personId)
                )
            } else {
                db.execSQL(
                    "INSERT INTO people(name,cover_path,centroid,face_count,hidden) VALUES(NULL,?,?,1,0)",
                    arrayOf(thumbPath, floatsToBytes(normalized))
                )
                personId = db.rawQuery("SELECT last_insert_rowid()", null).use {
                    it.moveToFirst()
                    it.getLong(0)
                }
            }

            db.execSQL(
                "INSERT INTO faces(media_id,uri,person_id,thumb_path,embedding,score) VALUES(?,?,?,?,?,?)",
                arrayOf(mediaId, uri, personId, thumbPath, floatsToBytes(normalized), score)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPeople(showSingles: Boolean = false): List<PersonGroup> {
        val min = if (showSingles) 1 else 2
        val out = mutableListOf<PersonGroup>()
        readableDatabase.rawQuery(
            "SELECT id,name,cover_path,face_count FROM people WHERE hidden=0 AND face_count>=? ORDER BY face_count DESC",
            arrayOf(min.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += PersonGroup(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3))
            }
        }
        return out
    }

    fun mediaIdsForPerson(personId: Long): Set<Long> {
        val ids = linkedSetOf<Long>()
        readableDatabase.rawQuery(
            "SELECT DISTINCT media_id FROM faces WHERE person_id=?",
            arrayOf(personId.toString())
        ).use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
    }

    fun renamePerson(personId: Long, name: String) {
        writableDatabase.execSQL("UPDATE people SET name=? WHERE id=?", arrayOf(name.trim(), personId))
    }

    fun hidePerson(personId: Long) {
        writableDatabase.execSQL("UPDATE people SET hidden=1 WHERE id=?", arrayOf(personId))
    }

    fun mergePeople(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val embeddings = mutableListOf<FloatArray>()
            db.rawQuery("SELECT embedding FROM faces WHERE person_id IN (?,?)", arrayOf(fromId.toString(), intoId.toString())).use { c ->
                while (c.moveToNext()) embeddings += bytesToFloats(c.getBlob(0))
            }
            db.execSQL("UPDATE faces SET person_id=? WHERE person_id=?", arrayOf(intoId, fromId))
            val centroid = if (embeddings.isEmpty()) floatArrayOf(0f) else {
                val avg = FloatArray(embeddings.first().size)
                embeddings.forEach { e -> e.indices.forEach { i -> avg[i] += e[i] } }
                avg.indices.forEach { i -> avg[i] /= embeddings.size.toFloat() }
                normalize(avg)
            }
            val cover = db.rawQuery("SELECT cover_path FROM people WHERE id=?", arrayOf(intoId.toString())).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            val fallback = db.rawQuery("SELECT cover_path FROM people WHERE id=?", arrayOf(fromId.toString())).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            db.execSQL(
                "UPDATE people SET centroid=?, face_count=?, cover_path=? WHERE id=?",
                arrayOf(floatsToBytes(centroid), embeddings.size, cover ?: fallback, intoId)
            )
            db.execSQL("DELETE FROM people WHERE id=?", arrayOf(fromId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun toggleFavorite(mediaId: Long): Boolean {
        val db = writableDatabase
        val isFav = isFavorite(mediaId)
        if (isFav) db.execSQL("DELETE FROM favorites WHERE media_id=?", arrayOf(mediaId))
        else db.execSQL("INSERT OR IGNORE INTO favorites(media_id) VALUES(?)", arrayOf(mediaId))
        return !isFav
    }

    fun isFavorite(mediaId: Long): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favorites WHERE media_id=?", arrayOf(mediaId.toString()))
            .use { it.moveToFirst() }

    fun favoriteIds(): Set<Long> {
        val ids = linkedSetOf<Long>()
        readableDatabase.rawQuery("SELECT media_id FROM favorites", null).use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        return ids
    }

    fun setMeta(key: String, value: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", arrayOf(key, value))
    }

    fun getMeta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun stats(totalImages: Int): ScanStats {
        val db = readableDatabase
        val scanned = db.rawQuery("SELECT COUNT(*) FROM scanned", null).use { it.moveToFirst(); it.getInt(0) }
        val failed = db.rawQuery("SELECT COUNT(*) FROM scanned WHERE status=0", null).use { it.moveToFirst(); it.getInt(0) }
        val faces = db.rawQuery("SELECT COUNT(*) FROM faces", null).use { it.moveToFirst(); it.getInt(0) }
        val people = db.rawQuery("SELECT COUNT(*) FROM people WHERE face_count>=2 AND hidden=0", null).use { it.moveToFirst(); it.getInt(0) }
        val visualIndexed = db.rawQuery("SELECT COUNT(*) FROM visual_index", null).use { it.moveToFirst(); it.getInt(0) }
        return ScanStats(
            total = totalImages,
            scanned = scanned.coerceAtMost(totalImages),
            faces = faces,
            people = people,
            failed = failed,
            visualIndexed = visualIndexed.coerceAtMost(totalImages),
            running = getMeta("scan_running") == "1",
            current = getMeta("scan_current") ?: "",
            error = getMeta("scan_error")
        )
    }

    fun clearFaceIndex() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM faces")
            db.execSQL("DELETE FROM people")
            db.execSQL("DELETE FROM scanned")
            db.execSQL("DELETE FROM meta")
            db.execSQL("DELETE FROM visual_index")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        File(appContext.filesDir, "face_thumbs").deleteRecursively()
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        var aa = 0f
        var bb = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        if (aa <= 0f || bb <= 0f) return 1f
        return 1f - (dot / (sqrt(aa) * sqrt(bb)))
    }

    private fun normalize(input: FloatArray): FloatArray {
        var sum = 0f
        input.forEach { sum += it * it }
        val norm = sqrt(sum).takeIf { it > 0f } ?: 1f
        return FloatArray(input.size) { input[it] / norm }
    }

    private fun floatsToBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }

    companion object {
        @Volatile private var instance: FaceDatabase? = null
        fun get(context: Context): FaceDatabase =
            instance ?: synchronized(this) {
                instance ?: FaceDatabase(context.applicationContext).also { instance = it }
            }
    }
}
