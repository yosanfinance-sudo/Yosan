package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PendingFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: PendingFeedbackEntity)

    @Query("SELECT * FROM pending_feedbacks WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedFeedbacks(): List<PendingFeedbackEntity>

    @Query("SELECT * FROM pending_feedbacks ORDER BY createdAt DESC")
    fun getAllFeedbacks(): Flow<List<PendingFeedbackEntity>>

    @Query("UPDATE pending_feedbacks SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markAsSynced(syncId: String)

    @Query("DELETE FROM pending_feedbacks WHERE isSynced = 1")
    suspend fun deleteSyncedFeedbacks()

    @Query("SELECT COUNT(*) FROM pending_feedbacks WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}
