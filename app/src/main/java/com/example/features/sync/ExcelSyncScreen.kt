package com.example.features.sync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.FinanceViewModel
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LendingIndigo
import com.example.ui.theme.OutstandingAmber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelSyncScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val selectedUriString by viewModel.selectedExcelUri.collectAsState()
    val selectedFileName by viewModel.selectedExcelFileName.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncResult by viewModel.lastSyncResult.collectAsState()
    val activeConflicts by viewModel.activeConflicts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileNameFromUri(context, it) ?: "transactions_backup.xlsx"
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.setSelectedExcelFile(it.toString(), fileName)
            viewModel.performTwoWaySync(context, it)
        }
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileNameFromUri(context, it) ?: "transactions_backup.xlsx"
            viewModel.setSelectedExcelFile(it.toString(), fileName)
            viewModel.exportToExcel(context, it)
        }
    }

    val selectedUri = remember(selectedUriString) {
        selectedUriString?.let { Uri.parse(it) }
    }

    val formattedLastSync = remember(lastSyncTime) {
        lastSyncTime?.let {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
        } ?: "Never synced"
    }

    val excelPreviewRows by viewModel.excelPreviewRows.collectAsState()
    var spreadsheetSearch by remember { mutableStateOf("") }

    LaunchedEffect(selectedUri) {
        selectedUri?.let { viewModel.loadExcelPreview(context, it) }
    }

    val filteredRows = remember(excelPreviewRows, spreadsheetSearch, selectedCategoryFilter) {
        excelPreviewRows.filter { row ->
            val matchesCat = selectedCategoryFilter == null || row.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = if (spreadsheetSearch.isBlank()) {
                true
            } else {
                val q = spreadsheetSearch.trim().lowercase()
                row.description.lowercase().contains(q) ||
                        row.category.lowercase().contains(q) ||
                        row.account.lowercase().contains(q) ||
                        row.date.contains(q) ||
                        row.amount.toString().contains(q) ||
                        row.syncId.lowercase().contains(q)
            }
            matchesCat && matchesSearch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Header Card - File Selection & Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = IncomeGreen.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Excel Backup & Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Statement, Categories & Rules Sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Selected File info
                    Text(
                        text = "Linked Backup File:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedFileName ?: "No Excel backup file selected yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedFileName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Workbook Structure Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "📋 ${excelPreviewRows.size} Txs",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IncomeGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "🏷️ ${categories.size} Categories",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LendingIndigo.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⚡ ${rules.size} Rules",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LendingIndigo
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "🏦 ${accounts.size} Accounts",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Last Sync Time & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Last Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedLastSync,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                syncStatus.contains("success", true) || syncStatus.contains("completed", true) -> IncomeGreen.copy(alpha = 0.15f)
                                syncStatus.contains("conflict", true) -> OutstandingAmber.copy(alpha = 0.15f)
                                syncStatus.contains("fail", true) || syncStatus.contains("error", true) -> ExpenseRed.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = syncStatus,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    syncStatus.contains("success", true) || syncStatus.contains("completed", true) -> IncomeGreen
                                    syncStatus.contains("conflict", true) -> OutstandingAmber
                                    syncStatus.contains("fail", true) || syncStatus.contains("error", true) -> ExpenseRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary: Sync with Excel (Opens document picker to pull newest cloud file from Drive/OneDrive and syncs instantly)
                        Button(
                            onClick = {
                                viewModel.setExternalIntentActive(true)
                                openFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSyncing) "Syncing..." else "Sync with Excel",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Create New Backup
                        OutlinedButton(
                            onClick = {
                                viewModel.setExternalIntentActive(true)
                                createFileLauncher.launch("transactions_backup.xlsx")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Backup", maxLines = 1)
                        }
                    }

                    if (selectedUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedUri?.let { viewModel.performTwoWaySync(context, it) }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Re-sync", fontSize = 12.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedUri?.let { uri ->
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(android.content.Intent.createChooser(intent, "Open in Excel / OneDrive..."))
                                        } catch (e: Exception) {
                                            viewModel.showMessage("No app found to open Excel files")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open in App", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 2. In-App Excel Spreadsheet Viewer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Excel View (Spreadsheet Preview)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${filteredRows.size} rows",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search field inside Excel View
                    OutlinedTextField(
                        value = spreadsheetSearch,
                        onValueChange = { spreadsheetSearch = it },
                        placeholder = { Text("Search spreadsheet...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (spreadsheetSearch.isNotEmpty()) {
                                IconButton(onClick = { spreadsheetSearch = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Filter Badges
                    val distinctCategories = remember(excelPreviewRows) {
                        excelPreviewRows.map { it.category }.distinct().sorted()
                    }

                    if (distinctCategories.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryFilter == null,
                                    onClick = { selectedCategoryFilter = null },
                                    label = { Text("All Categories (${excelPreviewRows.size})", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            items(distinctCategories) { catName ->
                                val count = excelPreviewRows.count { it.category.equals(catName, ignoreCase = true) }
                                FilterChip(
                                    selected = selectedCategoryFilter.equals(catName, ignoreCase = true),
                                    onClick = {
                                        selectedCategoryFilter = if (selectedCategoryFilter.equals(catName, ignoreCase = true)) null else catName
                                    },
                                    label = { Text("$catName ($count)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedUri == null) "Select an Excel file to view data" else "No transactions matching filter",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Horizontally scrollable spreadsheet table
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(1.dp)
                        ) {
                            val horizontalScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                                    Text("Date", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
                                    Text("Description", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(160.dp))
                                    Text("Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
                                    Text("Type", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                                    Text("Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(120.dp))
                                    Text("Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(110.dp))
                                    Text("Notes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(120.dp))
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // Rows
                                filteredRows.take(150).forEachIndexed { index, row ->
                                    val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    val amtColor = when (row.type) {
                                        TransactionType.INCOME, TransactionType.REFUND -> IncomeGreen
                                        TransactionType.EXPENSE -> ExpenseRed
                                        TransactionType.LENDING -> LendingIndigo
                                        TransactionType.BORROWING -> OutstandingAmber
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Row(
                                        modifier = Modifier
                                            .background(rowBg)
                                            .padding(vertical = 6.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                                        Text(row.date, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                                        Text(row.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(160.dp))
                                        Text("₹${String.format(Locale.ENGLISH, "%.2f", row.amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = amtColor, modifier = Modifier.width(90.dp))
                                        Text(row.type.name, style = MaterialTheme.typography.labelSmall, color = amtColor, modifier = Modifier.width(80.dp))

                                        // Category Chip
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.width(120.dp)
                                        ) {
                                            Text(
                                                text = row.category,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(row.account, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                                        Text(row.notes.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Conflicts Section (if any)
        if (activeConflicts.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = OutstandingAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unresolved Conflicts (${activeConflicts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OutstandingAmber
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "These transactions were modified in both the App and Excel. Choose which version to keep:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(activeConflicts) { conflict ->
                ConflictResolutionCard(
                    conflict = conflict,
                    onKeepApp = { viewModel.resolveConflict(conflict, keepApp = true, context, selectedUri) },
                    onKeepExcel = { viewModel.resolveConflict(conflict, keepApp = false, context, selectedUri) }
                )
            }
        }
    }
}

@Composable
fun SyncMetricChip(
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ConflictResolutionCard(
    conflict: SyncConflict,
    onKeepApp: () -> Unit,
    onKeepExcel: () -> Unit
) {
    val appTx = conflict.appTransaction
    val excelRow = conflict.excelRow

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutstandingAmber.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "ID: ${conflict.syncId.take(12)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App Version Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "📱 App Version",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = appTx.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(text = "Amount: ₹${appTx.amount}", style = MaterialTheme.typography.labelSmall)
                    Text(text = "Category: ${appTx.categoryName}", style = MaterialTheme.typography.labelSmall)
                    Text(text = "Date: ${appTx.transactionDate}", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onKeepApp,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Keep App", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Excel Version Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "📊 Excel Version",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = excelRow.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(text = "Amount: ₹${excelRow.amount}", style = MaterialTheme.typography.labelSmall)
                    Text(text = "Category: ${excelRow.category}", style = MaterialTheme.typography.labelSmall)
                    Text(text = "Date: ${excelRow.date}", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onKeepExcel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Keep Excel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (_: Exception) {}
    return name ?: uri.lastPathSegment
}
