package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.StatementImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY importTimestamp DESC, id DESC")
    fun getAllImports(): Flow<List<StatementImportEntity>>

    @Query("SELECT * FROM statement_imports ORDER BY importTimestamp DESC")
    suspend fun getAllImportsList(): List<StatementImportEntity>

    @Query("SELECT * FROM statement_imports WHERE id = :id LIMIT 1")
    suspend fun getImportById(id: Long): StatementImportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImport(statementImport: StatementImportEntity): Long

    @Update
    suspend fun updateImport(statementImport: StatementImportEntity)

    @Query("DELETE FROM statement_imports WHERE id = :id")
    suspend fun deleteImport(id: Long)

    @Query("DELETE FROM statement_imports WHERE bankName = :bankName")
    suspend fun deleteImportsByBankName(bankName: String)

    @Query("DELETE FROM statement_imports")
    suspend fun deleteAllImports()

    @Query("SELECT COUNT(*) FROM statement_imports")
    suspend fun getImportCount(): Int
}
