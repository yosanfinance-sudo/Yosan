package com.example.features.rules.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.CategorizationRuleDao
import com.example.data.local.entity.CategorizationRuleEntity
import kotlinx.coroutines.flow.Flow

class RulesRepository(private val database: AppDatabase) {
    private val ruleDao: CategorizationRuleDao = database.categorizationRuleDao()

    val allRules: Flow<List<CategorizationRuleEntity>> = ruleDao.getAllRules()

    suspend fun getActiveRulesList() = ruleDao.getActiveRulesList()
    suspend fun insertRule(rule: CategorizationRuleEntity) = ruleDao.insertRule(rule)
    suspend fun toggleRule(id: Long, isActive: Boolean) = ruleDao.toggleRuleActive(id, isActive)
    suspend fun deleteRule(id: Long) = ruleDao.deleteRule(id)
}
