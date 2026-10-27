package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CategorizationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorizationRuleDao {
    @Query("SELECT * FROM categorization_rules ORDER BY priority DESC, id ASC")
    fun getAllRules(): Flow<List<CategorizationRuleEntity>>

    @Query("SELECT * FROM categorization_rules ORDER BY priority DESC, id ASC")
    suspend fun getAllRulesList(): List<CategorizationRuleEntity>

    @Query("SELECT * FROM categorization_rules WHERE isActive = 1 ORDER BY priority DESC, id ASC")
    suspend fun getActiveRulesList(): List<CategorizationRuleEntity>

    @Query("SELECT * FROM categorization_rules WHERE isActive = 1 ORDER BY priority DESC, id ASC")
    fun getActiveRules(): Flow<List<CategorizationRuleEntity>>

    @Query("SELECT * FROM categorization_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): CategorizationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategorizationRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<CategorizationRuleEntity>)

    @Update
    suspend fun updateRule(rule: CategorizationRuleEntity)

    @Query("UPDATE categorization_rules SET isActive = :isActive WHERE id = :id")
    suspend fun toggleRuleActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM categorization_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM categorization_rules")
    suspend fun deleteAllRules()

    @Query("SELECT COUNT(*) FROM categorization_rules")
    suspend fun getRuleCount(): Int
}
