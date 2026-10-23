package com.ophoto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ophoto.data.local.entity.CaptureRecordEntity
import com.ophoto.data.local.entity.EditHistoryEntity
import com.ophoto.data.local.entity.FieldProgressEntity
import com.ophoto.data.local.entity.FilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {

    @Query("SELECT * FROM filter ORDER BY is_favorite DESC, sort_order ASC, created_at DESC")
    fun observeAll(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filter ORDER BY is_favorite DESC, sort_order ASC, created_at DESC")
    suspend fun getAllOnce(): List<FilterEntity>

    @Query("SELECT * FROM filter WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FilterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FilterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FilterEntity>)

    @Query("UPDATE filter SET is_favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE filter SET is_hidden = :hidden, updated_at = :updatedAt WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, updatedAt: Long)

    @Query("DELETE FROM filter WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM filter WHERE source != 'builtin'")
    suspend fun deleteAllUserImported()
}

@Dao
interface CaptureRecordDao {

    @Query("SELECT * FROM capture_record ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CaptureRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CaptureRecordEntity): Long

    @Query("DELETE FROM capture_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM capture_record")
    suspend fun clearAll()
}

@Dao
interface EditHistoryDao {

    @Query("SELECT * FROM edit_history WHERE media_id = :mediaId ORDER BY version DESC")
    fun observeByMedia(mediaId: String): Flow<List<EditHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: EditHistoryEntity): Long

    @Query("DELETE FROM edit_history WHERE media_id = :mediaId")
    suspend fun clearForMedia(mediaId: String)
}

@Dao
interface FieldProgressDao {

    @Query("SELECT * FROM field_progress")
    fun observeAll(): Flow<List<FieldProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FieldProgressEntity)
}
