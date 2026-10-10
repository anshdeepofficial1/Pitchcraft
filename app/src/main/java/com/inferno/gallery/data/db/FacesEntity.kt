package com.inferno.gallery.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "faces",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["clusterId"])
    ]
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val clusterId: Long,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    val cropCachePath: String?,
    val embedding: FloatArray,
    val confidence: Float = 1f
)

@Entity(tableName = "person_clusters")
data class PersonClusterEntity(
    @PrimaryKey(autoGenerate = true) val clusterId: Long = 0,
    val name: String? = null,
    val coverFaceId: Long? = null,
    val coverMediaId: Long? = null,
    val coverCropPath: String? = null,
    val faceCount: Int = 0,
    val centroid: FloatArray,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCluster(cluster: PersonClusterEntity): Long

    @Update
    suspend fun updateCluster(cluster: PersonClusterEntity)

    @Query("SELECT * FROM person_clusters ORDER BY faceCount DESC")
    suspend fun getAllClusters(): List<PersonClusterEntity>

    @Query("SELECT * FROM person_clusters WHERE clusterId = :clusterId LIMIT 1")
    suspend fun getCluster(clusterId: Long): PersonClusterEntity?

    @Query("SELECT * FROM person_clusters WHERE isHidden = 0 AND faceCount >= :minFaces ORDER BY isFavorite DESC, faceCount DESC")
    fun observePeople(minFaces: Int = 2): Flow<List<PersonClusterEntity>>

    @Query("SELECT * FROM faces WHERE clusterId = :clusterId ORDER BY id DESC")
    fun observeFacesForCluster(clusterId: Long): Flow<List<FaceEntity>>

    @Query("SELECT DISTINCT mediaId FROM faces WHERE clusterId = :clusterId")
    suspend fun getMediaIdsForCluster(clusterId: Long): List<Long>

    @Query("UPDATE person_clusters SET name = :name, updatedAt = :updatedAt WHERE clusterId = :clusterId")
    suspend fun renameCluster(clusterId: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE person_clusters SET isHidden = :hidden, updatedAt = :updatedAt WHERE clusterId = :clusterId")
    suspend fun setClusterHidden(clusterId: Long, hidden: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE person_clusters SET isFavorite = :favorite, updatedAt = :updatedAt WHERE clusterId = :clusterId")
    suspend fun setClusterFavorite(clusterId: Long, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE faces SET clusterId = :intoClusterId WHERE clusterId = :fromClusterId")
    suspend fun moveFacesToCluster(fromClusterId: Long, intoClusterId: Long)

    @Query("DELETE FROM person_clusters WHERE clusterId = :clusterId")
    suspend fun deleteCluster(clusterId: Long)

    @Query("DELETE FROM faces")
    suspend fun clearFaces()

    @Query("DELETE FROM person_clusters")
    suspend fun clearClusters()

    @Query("SELECT COUNT(*) FROM faces")
    fun observeFaceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM person_clusters WHERE isHidden = 0 AND faceCount >= 2")
    fun observePeopleCount(): Flow<Int>
}
