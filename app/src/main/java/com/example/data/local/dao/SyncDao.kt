package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.DeletedTransactionEntity
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM deleted_transactions")
    suspend fun getAllDeletedTransactions(): List<DeletedTransactionEntity>

    @Query("SELECT syncId FROM deleted_transactions")
    suspend fun getAllDeletedSyncIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedTransaction(entity: DeletedTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedTransactions(entities: List<DeletedTransactionEntity>)

    @Query("DELETE FROM deleted_transactions WHERE syncId = :syncId")
    suspend fun removeDeletedTransaction(syncId: String)

    @Query("DELETE FROM deleted_transactions WHERE syncId IN (:syncIds)")
    suspend fun removeDeletedTransactions(syncIds: List<String>)

    @Query("DELETE FROM deleted_transactions")
    suspend fun clearAllDeletedTransactions()

    // Sync Metadata
    @Query("SELECT value FROM sync_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getMetadataValue(key: String): String?

    @Query("SELECT * FROM sync_metadata")
    fun getAllMetadataFlow(): Flow<List<SyncMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun deleteMetadata(key: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAllMetadata()
}
