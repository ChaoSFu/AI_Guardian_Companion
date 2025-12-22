package com.example.ai_guardian_companion.storage.dao

import androidx.room.*
import com.example.ai_guardian_companion.storage.entity.ImageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 图像 DAO
 */
@Dao
interface ImageDao {

    /**
     * 插入新图像
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity): Long

    /**
     * 批量插入图像
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)

    /**
     * 根据 ID 获取图像
     */
    @Query("SELECT * FROM images WHERE imageId = :imageId")
    suspend fun getImageById(imageId: Long): ImageEntity?

    /**
     * 获取 turn 的所有图像
     */
    @Query("SELECT * FROM images WHERE turnId = :turnId ORDER BY timestamp ASC")
    fun getImagesByTurn(turnId: Long): Flow<List<ImageEntity>>

    /**
     * 获取会话的所有图像
     */
    @Query("SELECT * FROM images WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getImagesBySession(sessionId: String): Flow<List<ImageEntity>>

    /**
     * 获取特定角色的图像
     */
    @Query("SELECT * FROM images WHERE sessionId = :sessionId AND role = :role ORDER BY timestamp ASC")
    suspend fun getImagesByRole(sessionId: String, role: String): List<ImageEntity>

    /**
     * 删除图像
     */
    @Delete
    suspend fun deleteImage(image: ImageEntity)

    /**
     * 删除 turn 的所有图像
     */
    @Query("DELETE FROM images WHERE turnId = :turnId")
    suspend fun deleteImagesByTurn(turnId: Long)
}
