package com.example.features.settings.dialogs

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.UndoHistoryEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.features.dashboard.DashboardCardConfigManager
import com.example.features.dashboard.DashboardCardType
import com.example.features.transactions.FinanceViewModel
import androidx.compose.ui.draw.scale
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.data.local.entity.CategoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardDialog(
    userProfile: UserProfileEntity?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cards by remember(userProfile?.dashboardCardsConfig) {
        mutableStateOf(DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig))
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Customize Dashboard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Reorder cards using the arrows or toggle switches to customize your Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                cards.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isEnabled) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Card Title & Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.type.icon,
                                            contentDescription = null,
                                            tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.type.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.type.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Up/Down reorder controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (index < cards.size - 1) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index < cards.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (index < cards.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Toggle Switch
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { checked ->
                                        val newList = cards.toMutableList()
                                        newList[index] = item.copy(isEnabled = checked)
                                        cards = newList
                                        onSave(DashboardCardConfigManager.serialize(newList))
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        cards = DashboardCardConfigManager.DEFAULT_CONFIG
                        onSave(DashboardCardConfigManager.serialize(DashboardCardConfigManager.DEFAULT_CONFIG))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset to Default Layout")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndoHistoryDialog(
    undoHistory: List<UndoHistoryEntity>,
    onRevert: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Undo History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all recorded action history. Transactions and rules will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = LendingIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Undo & Recovery", fontWeight = FontWeight.Bold)
                }
                if (undoHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (undoHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Recent Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Edits, deletions, and rule applications will appear here for safe recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(undoHistory.size, key = { undoHistory[it].actionId }) { index ->
                        val action = undoHistory[index]
                        val icon = when (action.actionType) {
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> Icons.Default.Category
                            "DELETE_TRANSACTION", "BULK_DELETE" -> Icons.Default.DeleteOutline
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> Icons.Default.AutoAwesome
                            "CHANGE_TYPE" -> Icons.Default.SyncAlt
                            else -> Icons.Default.Edit
                        }
                        val iconTint = when (action.actionType) {
                            "DELETE_TRANSACTION", "BULK_DELETE" -> ExpenseRed
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> LendingIndigo
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val timeStr = remember(action.timestamp) {
                            val diff = System.currentTimeMillis() - action.timestamp
                            when {
                                diff < 60_000L -> "Just now"
                                diff < 3600_000L -> "${diff / 60_000L}m ago"
                                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(action.timestamp))
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = iconTint.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = action.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onRevert(action.actionId) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Revert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    categories: List<CategoryEntity>,
    allTransactions: List<com.example.data.local.entity.TransactionEntity>,
    onAddCategory: (String, CategoryType, String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onReassignAndDelete: (Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<CategoryType?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDeleteWithReassign by remember { mutableStateOf<CategoryEntity?>(null) }

    val filteredCategories = remember(categories, selectedFilter) {
        if (selectedFilter == null) categories else categories.filter { it.type == selectedFilter }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Categories", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showAddCategoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Category",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category Type Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${categories.size})") }
                    )
                    CategoryType.values().forEach { cType ->
                        val count = categories.count { it.type == cType }
                        FilterChip(
                            selected = selectedFilter == cType,
                            onClick = { selectedFilter = cType },
                            label = { Text("${cType.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredCategories.size) { idx ->
                        val cat = filteredCategories[idx]
                        val txCount = remember(cat, allTransactions) {
                            allTransactions.count { it.categoryId == cat.id || it.categoryName.equals(cat.name, ignoreCase = true) }
                        }
                        val colorParsed = remember(cat.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF10B981)
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = colorParsed,
                                        shape = CircleShape,
                                        modifier = Modifier.size(16.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (cat.isSystem) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "System",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${cat.type.name} • $txCount transactions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingCategory = cat },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Category",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!cat.isSystem) {
                                                if (txCount > 0) {
                                                    categoryToDeleteWithReassign = cat
                                                } else {
                                                    onDeleteCategory(cat.id)
                                                }
                                            }
                                        },
                                        enabled = !cat.isSystem,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Category",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (cat.isSystem) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || editingCategory != null) {
        val isEditing = editingCategory != null
        var catName by remember { mutableStateOf(editingCategory?.name ?: "") }
        var catType by remember { mutableStateOf(editingCategory?.type ?: CategoryType.EXPENSE) }
        var catColor by remember { mutableStateOf(editingCategory?.colorHex ?: "#3B82F6") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val palette = listOf(
            "#EF4444", "#10B981", "#3B82F6", "#8B5CF6",
            "#F59E0B", "#EC4899", "#06B6D4", "#64748B", "#14B8A6"
        )

        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                editingCategory = null
            },
            title = {
                Text(if (isEditing) "Edit Category" else "Add New Category", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = {
                            catName = it
                            errorMessage = null
                        },
                        label = { Text("Category Name") },
                        placeholder = { Text("e.g. Subscriptions") },
                        singleLine = true,
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Text("Classification Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.INVESTMENT, CategoryType.OTHER).forEach { t ->
                            FilterChip(
                                selected = catType == t,
                                onClick = { catType = t },
                                label = { Text(t.name) }
                            )
                        }
                    }

                    Text("Category Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSel = catColor.equals(hex, ignoreCase = true)
                            Surface(
                                color = c,
                                shape = CircleShape,
                                border = if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { catColor = hex }
                            ) {}
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = catName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Category name cannot be empty"
                            return@Button
                        }
                        val duplicate = categories.any {
                            it.name.trim().equals(trimmed, ignoreCase = true) && (!isEditing || it.id != editingCategory?.id)
                        }
                        if (duplicate) {
                            errorMessage = "Category '$trimmed' already exists"
                            return@Button
                        }
                        if (isEditing) {
                            editingCategory?.let { current ->
                                onUpdateCategory(
                                    current.copy(
                                        name = trimmed,
                                        type = catType,
                                        colorHex = catColor,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            onAddCategory(trimmed, catType, catColor)
                        }
                        showAddCategoryDialog = false
                        editingCategory = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEditing) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    editingCategory = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Deletion with Reassignment Dialog
    categoryToDeleteWithReassign?.let { targetCat ->
        val candidateFallbacks = remember(categories, targetCat) {
            categories.filter { it.id != targetCat.id }
        }
        var selectedFallbackId by remember(targetCat) {
            mutableStateOf(candidateFallbacks.firstOrNull()?.id ?: 1L)
        }
        val count = allTransactions.count { it.categoryId == targetCat.id || it.categoryName.equals(targetCat.name, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { categoryToDeleteWithReassign = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Reassign Category Records", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Category '${targetCat.name}' has $count linked transactions.\n\nTo prevent orphan transactions or calculation distortion, choose the category to reassign these records to:"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Target Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        candidateFallbacks.forEach { fallback ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFallbackId = fallback.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedFallbackId == fallback.id,
                                    onClick = { selectedFallbackId = fallback.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(fallback.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fallbackName = candidateFallbacks.firstOrNull { it.id == selectedFallbackId }?.name ?: "General"
                        onReassignAndDelete(targetCat.id, selectedFallbackId, fallbackName)
                        categoryToDeleteWithReassign = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reassign & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteWithReassign = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeveloperDetailsDialog(
    onOpenWalkthrough: () -> Unit,
    onOpenIntroNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Details About the Developer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Application", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Yosan Finance Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version v${BuildConfig.VERSION_NAME} • Release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Engineering Team", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Rooparaj & Gokulraj", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }


            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}




