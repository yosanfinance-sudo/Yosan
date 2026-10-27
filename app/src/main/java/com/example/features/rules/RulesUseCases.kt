package com.example.features.rules

import com.example.data.local.entity.CategorizationRuleEntity
import com.example.features.rules.data.RulesRepository
import kotlinx.coroutines.flow.Flow

class GetRulesUseCase(private val repository: RulesRepository) {
    operator fun invoke(): Flow<List<CategorizationRuleEntity>> = repository.allRules
}

class AddRuleUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke(rule: CategorizationRuleEntity) = repository.insertRule(rule)
}

class ToggleRuleUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke(id: Long, isActive: Boolean) = repository.toggleRule(id, isActive)
}

class DeleteRuleUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteRule(id)
}
