package com.example.features.rules

import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionType
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LendingIndigo
import com.example.ui.theme.OutstandingAmber
import com.example.features.transactions.FinanceViewModel

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val displayCategories = remember(categories) { categories.distinctBy { "${it.name.trim().lowercase()}_${it.type}" } }
    val rules by viewModel.rules.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CategorizationRuleEntity?>(null) }
    var testNarration by remember { mutableStateOf("") }

    var activeTab by remember { mutableStateOf(0) }
    val selectedRuleIds = remember { mutableStateListOf<Long>() }
    val selectedCategoryIds = remember { mutableStateListOf<Long>() }
    var showAddCatDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var catName by remember { mutableStateOf("") }
    var catType by remember { mutableStateOf(CategoryType.EXPENSE) }
    val colorsList = listOf("#EF4444", "#F59E0B", "#3B82F6", "#10B981", "#8B5CF6", "#EC4899", "#06B6D4", "#94A3B8")
    var selectedColorHex by remember { mutableStateOf(colorsList.first()) }

    val testResult = remember(testNarration, rules) {
        if (testNarration.isNotBlank()) {
            CategorizationEngine.categorize(testNarration, rules, false)
        } else null
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.undoSnackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoAction(event.actionId)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("rules_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        item {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = {
                        activeTab = 0
                        selectedCategoryIds.clear()
                    },
                    text = { Text("Auto-Rules", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        activeTab = 1
                        selectedRuleIds.clear()
                    },
                    text = { Text("Categories", fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (activeTab == 0) {
            // 1. Interactive Rule Testing Sandbox
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Test",
                                tint = LendingIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test Rule Sandbox",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testNarration,
                            onValueChange = { testNarration = it },
                            placeholder = { Text("Paste raw bank description (e.g. UPI payment, Store purchase)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (testResult != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Detected: ", style = MaterialTheme.typography.bodySmall)
                                        CategoryChip(categoryName = testResult.categoryName)
                                    }
                                    Text(
                                        text = "Confidence: ${(testResult.confidence * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (testResult.confidence > 0.8f) IncomeGreen else OutstandingAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Rules List Header Row
            item {
                val isRuleSelectionMode = selectedRuleIds.isNotEmpty()
                if (isRuleSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedRuleIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedRuleIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedRuleIds.forEach { id ->
                                    viewModel.deleteRule(id)
                                }
                                selectedRuleIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Rules (${rules.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddRuleDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Rule")
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Rule,
                        title = "No Rules Configured",
                        description = "Create rules so your statements are categorized instantly.",
                        actionText = "Create First Rule",
                        onActionClick = { showAddRuleDialog = true }
                    )
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    val isSelected = selectedRuleIds.contains(rule.id)
                    RuleCard(
                        rule = rule,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedRuleIds.isNotEmpty()) {
                                if (isSelected) {
                                    selectedRuleIds.remove(rule.id)
                                } else {
                                    selectedRuleIds.add(rule.id)
                                }
                            } else {
                                editingRule = rule
                                showAddRuleDialog = true
                            }
                        },
                        onLongClick = {
                            if (!selectedRuleIds.contains(rule.id)) {
                                selectedRuleIds.add(rule.id)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            // Categories Tab
            item {
                val isCatSelectionMode = selectedCategoryIds.isNotEmpty()
                if (isCatSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedCategoryIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedCategoryIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedCategoryIds.forEach { id ->
                                    viewModel.deleteCategory(id)
                                }
                                selectedCategoryIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Categories (${displayCategories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                editingCategory = null
                                catName = ""
                                catType = CategoryType.EXPENSE
                                selectedColorHex = colorsList.first()
                                showAddCatDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Category")
                        }
                    }
                }
            }

            // Info banner explaining how categories work
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Create custom categories for Expense, Income, Investment, or Lending. Use them to organize statements and set up Auto-Rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (displayCategories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Category,
                        title = "No Categories Created",
                        description = "Create custom categories to organize your expenses, income, investments, and lending.",
                        actionText = "Create Category",
                        onActionClick = {
                            editingCategory = null
                            catName = ""
                            catType = CategoryType.EXPENSE
                            selectedColorHex = colorsList.first()
                            showAddCatDialog = true
                        }
                    )
                }
            } else {
                items(displayCategories, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryIds.contains(cat.id)
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selectedCategoryIds.isNotEmpty()) {
                                        if (isSelected) {
                                            selectedCategoryIds.remove(cat.id)
                                        } else {
                                            selectedCategoryIds.add(cat.id)
                                        }
                                    } else {
                                        editingCategory = cat
                                        catName = cat.name
                                        catType = cat.type
                                        selectedColorHex = cat.colorHex
                                        showAddCatDialog = true
                                    }
                                },
                                onLongClick = {
                                    if (!selectedCategoryIds.contains(cat.id)) {
                                        selectedCategoryIds.add(cat.id)
                                    } else {
                                        selectedCategoryIds.remove(cat.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(cat.colorHex)))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = cat.name, fontWeight = FontWeight.Bold)
                                    Text(text = cat.type.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }


        }
    }

    // Add/Edit Rule Dialog
    if (showAddRuleDialog || editingRule != null) {
        var keyword by remember(showAddRuleDialog, editingRule) { mutableStateOf(editingRule?.keyword ?: "") }
        var selectedCategory by remember(showAddRuleDialog, editingRule) { mutableStateOf(categories.firstOrNull { it.id == editingRule?.categoryId } ?: categories.firstOrNull()) }

        // Build all unique words from all transaction descriptions for suggestions
        val allDescriptionWords = remember(allTransactions) {
            allTransactions
                .flatMap { tx ->
                    tx.description.split(" ", "\t", "-", "_", "/", ".", ",")
                        .map { it.trim().lowercase() }
                        .filter { it.length > 2 }
                }
                .distinct()
                .sorted()
        }

        // Real-time filtered suggestions matching what user is typing
        val keywordSuggestions = remember(keyword, allDescriptionWords) {
            if (keyword.isBlank()) emptyList()
            else allDescriptionWords.filter { it.contains(keyword.trim().lowercase()) }.take(10)
        }

        Dialog(onDismissRequest = {
            showAddRuleDialog = false
            editingRule = null
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (editingRule != null) "Edit Categorization Rule" else "Add Categorization Rule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword (e.g. swiggy, salary, hdfc)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Real-time suggestions
                    if (keywordSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Matching keywords from your statements:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            keywordSuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { keyword = suggestion },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Assign to Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(categories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCategory?.id == cat.id,
                                    onClick = { selectedCategory = cat }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(cat.colorHex)))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = cat.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showAddRuleDialog = false
                            editingRule = null
                        }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cat = selectedCategory ?: return@Button
                                val prio = editingRule?.priority ?: 10
                                val mType = editingRule?.matchType ?: MatchType.CONTAINS
                                if (keyword.isNotBlank()) {
                                    if (editingRule != null) {
                                        val resolvedType = when (cat.type) {
                                            CategoryType.INCOME -> TransactionType.INCOME
                                            CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                                            CategoryType.LENDING -> TransactionType.LENDING
                                            CategoryType.BORROWING -> TransactionType.BORROWING
                                            CategoryType.OTHER -> TransactionType.TRANSFER
                                            else -> TransactionType.EXPENSE
                                        }
                                        val updatedRule = editingRule!!.copy(
                                            keyword = keyword.trim(),
                                            categoryId = cat.id,
                                            categoryName = cat.name,
                                            transactionType = resolvedType,
                                            priority = prio,
                                            matchType = mType
                                        )
                                        viewModel.updateRule(updatedRule)
                                        editingRule = null
                                        showAddRuleDialog = false
                                    } else {
                                        viewModel.addRule(keyword.trim(), cat, prio, mType)
                                        showAddRuleDialog = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = keyword.isNotBlank() && selectedCategory != null
                        ) {
                            Text("Save Rule")
                        }
                    }
                }
            }
        }
    }

    if (showAddCatDialog) {
        Dialog(onDismissRequest = {
            showAddCatDialog = false
            editingCategory = null
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (editingCategory != null) "Edit Category" else "Add Category",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Category Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.INVESTMENT, CategoryType.LENDING).forEach { type ->
                            FilterChip(
                                selected = catType == type,
                                onClick = { catType = type },
                                label = { Text(type.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Category Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorsList.forEach { colorHex ->
                            val c = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selectedColorHex.equals(colorHex, ignoreCase = true)) 3.dp else 1.dp,
                                        color = if (selectedColorHex.equals(colorHex, ignoreCase = true)) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorHex }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showAddCatDialog = false
                            editingCategory = null
                        }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (catName.isNotBlank()) {
                                    val usedColors = categories.map { it.colorHex.uppercase() }.toSet()
                                    val finalColor = if (selectedColorHex.isNotBlank()) selectedColorHex
                                    else editingCategory?.colorHex ?: colorsList.firstOrNull { it.uppercase() !in usedColors } ?: colorsList[categories.size % colorsList.size]
                                    if (editingCategory != null) {
                                        val updatedCat = editingCategory!!.copy(
                                            name = catName.trim(),
                                            type = catType,
                                            colorHex = finalColor,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        viewModel.updateCategory(updatedCat)
                                        editingCategory = null
                                    } else {
                                        viewModel.addCategory(catName.trim(), catType, finalColor)
                                    }
                                    showAddCatDialog = false
                                    catName = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = catName.isNotBlank()
                        ) {
                            Text(if (editingCategory != null) "Update" else "Create")
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )
}
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleCard(
    rule: CategorizationRuleEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\"${rule.keyword}\"",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryChip(categoryName = rule.categoryName)
                }
            }
        }
    }
}
