package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.UndoHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UndoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUndoAction(action: UndoHistoryEntity)

    @Query("SELECT * FROM undo_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUndoActions(limit: Int = 30): Flow<List<UndoHistoryEntity>>

    @Query("SELECT * FROM undo_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUndoAction(): UndoHistoryEntity?

    @Query("SELECT * FROM undo_history WHERE actionId = :actionId LIMIT 1")
    suspend fun getUndoActionById(actionId: String): UndoHistoryEntity?

    @Query("DELETE FROM undo_history WHERE actionId = :actionId")
    suspend fun deleteUndoAction(actionId: String)

    @Query("DELETE FROM undo_history")
    suspend fun clearUndoHistory()
}
