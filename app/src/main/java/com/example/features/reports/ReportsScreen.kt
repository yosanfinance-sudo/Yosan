package com.example.features.reports

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.sync.ExcelSyncService
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel Flows
    val allTransactions by viewModel.allTransactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val categoryIncomeBreakdown by viewModel.categoryIncomeBreakdown.collectAsState()
    val monthlyTrends by viewModel.monthlyTrends.collectAsState()

    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
    val isMasked = privacyEnabled && !isRevealed

    // Report State
    var selectedReportType by remember { mutableStateOf(ReportType.OVERALL_SUMMARY) }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.ALL_TIME) }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = PDF Document Preview, 1 = Data Records

    var lastRefreshedTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var pdfPreviewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    // Period Label
    val periodLabel = remember(selectedDateRange, customStartDate, customEndDate) {
        when (selectedDateRange) {
            DateRangeFilter.ALL_TIME -> "All Time"
            DateRangeFilter.TODAY -> "Today (${DateUtils.getCurrentDate()})"
            DateRangeFilter.THIS_WEEK -> "This Week"
            DateRangeFilter.THIS_MONTH -> "This Month"
            DateRangeFilter.THIS_YEAR -> "This Year"
            DateRangeFilter.CUSTOM -> {
                if (customStartDate.isNotBlank() && customEndDate.isNotBlank()) "$customStartDate to $customEndDate"
                else if (customStartDate.isNotBlank()) "From $customStartDate"
                else "Custom Range"
            }
        }
    }

    // Filtered Records based on user criteria
    val filteredTransactions = remember(allTransactions, selectedDateRange, customStartDate, customEndDate, selectedAccountId, selectedCategoryName) {
        allTransactions.filter { tx ->
            ExportService.matchesDateRange(tx.transactionDate, selectedDateRange, customStartDate, customEndDate) &&
                    (selectedAccountId == null || tx.accountId == selectedAccountId) &&
                    (selectedCategoryName == null || tx.categoryName.equals(selectedCategoryName, ignoreCase = true))
        }
    }

    val filteredLoans = remember(loans, selectedDateRange, customStartDate, customEndDate) {
        loans.filter { loan ->
            ExportService.matchesDateRange(loan.lentDate, selectedDateRange, customStartDate, customEndDate)
        }
    }

    val filteredCompanyExpenses = remember(companyExpenses, selectedDateRange, customStartDate, customEndDate) {
        companyExpenses.filter { exp ->
            ExportService.matchesDateRange(exp.date, selectedDateRange, customStartDate, customEndDate)
        }
    }

    val filteredSummary = remember(filteredTransactions, filteredLoans, filteredCompanyExpenses, accounts) {
        FinancialCalculationService.calculateSummary(
            transactions = filteredTransactions,
            loans = filteredLoans,
            companyExpenses = filteredCompanyExpenses,
            accounts = accounts
        )
    }

    // Regenerate & Render PDF whenever filters, report type, or sync time change
    LaunchedEffect(
        selectedReportType, selectedDateRange, customStartDate, customEndDate,
        selectedAccountId, selectedCategoryName, filteredTransactions, filteredLoans,
        filteredCompanyExpenses, userProfile?.name, lastRefreshedTime
    ) {
        isRendering = true
        withContext(Dispatchers.IO) {
            try {
                val file = ExportService.generatePdfReport(
                    context = context,
                    summary = filteredSummary,
                    categoryExpenseBreakdown = categoryBreakdown,
                    categoryIncomeBreakdown = categoryIncomeBreakdown,
                    monthlyTrends = monthlyTrends,
                    loans = filteredLoans,
                    allTransactions = filteredTransactions,
                    companyExpenses = filteredCompanyExpenses,
                    userName = userProfile?.name,
                    reportType = selectedReportType,
                    periodLabel = periodLabel
                )
                generatedFile = file

                val bitmaps = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val width = (page.width * 1.5f).toInt()
                                val height = (page.height * 1.5f).toInt()
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                    }
                }
                val oldBitmaps = pdfPreviewBitmaps
                pdfPreviewBitmaps = bitmaps
                oldBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                if (currentPageIndex >= bitmaps.size) {
                    currentPageIndex = 0
                }
            } catch (_: Exception) {}
            isRendering = false
        }
    }

    // SAF Native Document Saver
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && generatedFile != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        generatedFile!!.inputStream().use { inp ->
                            inp.copyTo(out)
                        }
                    }
                    viewModel.showMessage("Report PDF saved successfully!")
                } catch (e: Exception) {
                    viewModel.showMessage("Failed to save PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val excelBytes = ExcelSyncService.generateExcelWorkbook(
                        transactions = filteredTransactions,
                        accounts = accounts,
                        categories = categories,
                        rules = emptyList()
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(excelBytes)
                    }
                    viewModel.showMessage("Report saved as Excel (.xlsx) successfully!")
                    HapticHelper.vibrateSuccess(context)
                } catch (e: Exception) {
                    viewModel.showMessage("Failed to save Excel: ${e.localizedMessage}")
                    HapticHelper.vibrateError(context)
                }
            }
        }
    }

    fun sharePdfReport() {
        scope.launch {
            try {
                val file = generatedFile ?: return@launch
                viewModel.setExternalIntentActive(true)
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Share ${selectedReportType.displayName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                viewModel.showMessage("Could not share PDF: ${e.localizedMessage ?: "No sharing app found"}")
            }
        }
    }

    fun openPdfReport() {
        scope.launch {
            try {
                val file = generatedFile ?: return@launch
                viewModel.setExternalIntentActive(true)
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(viewIntent, "Open ${selectedReportType.displayName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                viewModel.showMessage("Could not open PDF: ${e.localizedMessage ?: "No PDF viewer app found"}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen")
    ) {
        // 1. Live Sync & Refresh Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Live Sync: Device Records Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Refreshed: ${DateUtils.formatDateTime(lastRefreshedTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        HapticHelper.vibrateClick(context)
                        isRefreshing = true
                        lastRefreshedTime = System.currentTimeMillis()
                        viewModel.showMessage("Reports synced with latest financial records.")
                        isRefreshing = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Data",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Report Type Selector Chips
        Text(
            text = "Report Type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportType.values().forEach { rType ->
                val isSelected = selectedReportType == rType
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedReportType = rType },
                    label = { Text(rType.displayName) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
        }

        // 3. Date Range Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateRangeFilter.values().forEach { dFilter ->
                val isSelected = selectedDateRange == dFilter
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedDateRange = dFilter
                        if (dFilter == DateRangeFilter.CUSTOM) {
                            showCustomDateDialog = true
                        }
                    },
                    label = {
                        Text(
                            if (dFilter == DateRangeFilter.CUSTOM && customStartDate.isNotBlank()) {
                                "$customStartDate -> $customEndDate"
                            } else {
                                dFilter.displayName
                            }
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
        }

        // 4. Action Buttons: Save PDF, Save Excel, Share, Open
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    HapticHelper.vibrateClick(context)
                    val defName = generatedFile?.name ?: "Yosan_Report.pdf"
                    savePdfLauncher.launch(defName)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Save PDF", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    HapticHelper.vibrateClick(context)
                    val defName = "Yosan_${selectedReportType.name.lowercase()}_report.xlsx"
                    saveExcelLauncher.launch(defName)
                },
                modifier = Modifier.weight(1.15f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = IncomeGreen)
                Spacer(modifier = Modifier.width(3.dp))
                Text("Save as Excel", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    HapticHelper.vibrateClick(context)
                    sharePdfReport()
                },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Share", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    HapticHelper.vibrateClick(context)
                    openPdfReport()
                },
                modifier = Modifier.weight(0.85f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Open", fontSize = 11.sp, maxLines = 1)
            }
        }

        // 5. Segmented Tabs: [ PDF Document Preview ] vs [ Data Records ]
        val recordCount = when (selectedReportType) {
            ReportType.PERSONAL_EXPENSE -> filteredTransactions.count { it.transactionType == TransactionType.EXPENSE }
            ReportType.COMPANY_EXPENSE -> filteredCompanyExpenses.size
            ReportType.INCOME -> filteredTransactions.count { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
            ReportType.CATEGORY_SPENDING -> categoryBreakdown.size
            ReportType.BORROWING -> ExportService.computeBorrowSummaries(filteredTransactions).size
            ReportType.LENDING -> filteredLoans.size
            ReportType.INVESTMENT -> filteredTransactions.count { it.transactionType == TransactionType.INVESTMENT }
            ReportType.TRANSFERS -> filteredTransactions.count { it.transactionType == TransactionType.TRANSFER }
            ReportType.OVERALL_SUMMARY -> filteredTransactions.size
        }

        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("PDF Preview") },
                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Records ($recordCount)") },
                icon = { Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // 6. Tab Content
        if (selectedTab == 0) {
            // Tab 0: In-App PDF Document Preview
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedReportType.displayName} • $periodLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {


                            if (pdfPreviewBitmaps.size > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Page ${currentPageIndex + 1} of ${pdfPreviewBitmaps.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isRendering && pdfPreviewBitmaps.isEmpty()) {
                        SkeletonReportsLoading(modifier = Modifier.fillMaxWidth())
                    } else if (pdfPreviewBitmaps.isNotEmpty()) {
                        // Multi-page navigation controls
                        if (pdfPreviewBitmaps.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                    enabled = currentPageIndex > 0,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Previous", fontSize = 12.sp)
                                }

                                Text(
                                    text = "Page ${currentPageIndex + 1} / ${pdfPreviewBitmaps.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                FilledTonalButton(
                                    onClick = { if (currentPageIndex < pdfPreviewBitmaps.size - 1) currentPageIndex++ },
                                    enabled = currentPageIndex < pdfPreviewBitmaps.size - 1,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Next", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        val currentBmp = pdfPreviewBitmaps.getOrNull(currentPageIndex) ?: pdfPreviewBitmaps.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = isMasked) { viewModel.revealAmountsTemporarily() },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .blur(if (isMasked) 28.dp else 0.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = "PDF Report Preview",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }

                            if (isMasked) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    shadowElevation = 8.dp,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clickable {
                                            com.example.utils.HapticHelper.vibrateClick(context)
                                            viewModel.revealAmountsTemporarily()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = "Report Hidden - Tap to unhide",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Thumbnail strip
                        if (pdfPreviewBitmaps.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(pdfPreviewBitmaps) { idx, bmp ->
                                    val isSelected = idx == currentPageIndex
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { currentPageIndex = idx }
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Thumb ${idx + 1}",
                                            modifier = Modifier
                                                .height(70.dp)
                                                .width(50.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(text = "P ${idx + 1}", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 1: On-Screen Data Records List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary Metric Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = selectedReportType.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Period: $periodLabel • $recordCount entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            when (selectedReportType) {
                                ReportType.PERSONAL_EXPENSE -> {
                                    val total = filteredTransactions.filter { it.transactionType == TransactionType.EXPENSE }
                                        .sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
                                    Text(
                                        text = "Total Expenses: ${CurrencyFormatter.formatInr(total)}",
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed
                                    )
                                }
                                ReportType.COMPANY_EXPENSE -> {
                                    val total = filteredCompanyExpenses.sumOf { it.amount }
                                    val rem = filteredCompanyExpenses.filter { it.isReimbursed }.sumOf { it.amount }
                                    Text(text = "Total Claimed: ${CurrencyFormatter.formatInr(total)}", fontWeight = FontWeight.Bold)
                                    Text(text = "Reimbursed: ${CurrencyFormatter.formatInr(rem)}", color = IncomeGreen, fontWeight = FontWeight.SemiBold)
                                }
                                ReportType.INCOME -> {
                                    val total = filteredTransactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                                        .sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
                                    Text(
                                        text = "Total Income: ${CurrencyFormatter.formatInr(total)}",
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                }
                                ReportType.LENDING -> {
                                    val total = filteredLoans.sumOf { it.amount }
                                    val due = filteredLoans.sumOf { it.remainingAmount }
                                    Text(text = "Total Lent: ${CurrencyFormatter.formatInr(total)}", fontWeight = FontWeight.Bold)
                                    Text(text = "Outstanding Due: ${CurrencyFormatter.formatInr(due)}", color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                                }
                                ReportType.BORROWING -> {
                                    val borrows = ExportService.computeBorrowSummaries(filteredTransactions)
                                    val total = borrows.sumOf { it.totalBorrowed }
                                    val due = borrows.sumOf { it.remainingDebt }
                                    Text(text = "Total Borrowed: ${CurrencyFormatter.formatInr(total)}", fontWeight = FontWeight.Bold)
                                    Text(text = "Outstanding Debt: ${CurrencyFormatter.formatInr(due)}", color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                                }
                                else -> {
                                    Text(
                                        text = "Net Balance: ${CurrencyFormatter.formatInr(filteredSummary.currentBalance)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Total Income: ${CurrencyFormatter.formatInr(filteredSummary.totalIncome)}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = IncomeGreen
                                    )
                                    Text(
                                        text = "Total Expenses: ${CurrencyFormatter.formatInr(filteredSummary.totalExpense)}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = ExpenseRed
                                    )
                                    Text(
                                        text = "Total Money Lent: ${CurrencyFormatter.formatInr(filteredSummary.moneyLent)}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Total Money Borrowed: ${CurrencyFormatter.formatInr(filteredSummary.borrowingMetrics.totalBorrowed)}",
                                        fontWeight = FontWeight.Bold,
                                        color = OutstandingAmber
                                    )
                                    if (filteredSummary.borrowingMetrics.outstandingBorrowing > 0) {
                                        Text(
                                            text = "Outstanding Debt: ${CurrencyFormatter.formatInr(filteredSummary.borrowingMetrics.outstandingBorrowing)}",
                                            fontWeight = FontWeight.SemiBold,
                                            color = ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Render list items
                if (recordCount == 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterListOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No records found",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No entries match the selected report type and filters for $periodLabel.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    when (selectedReportType) {
                        ReportType.COMPANY_EXPENSE -> {
                            items(filteredCompanyExpenses, key = { it.id }) { exp ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = exp.companyName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(text = "${exp.date} • ${exp.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(text = CurrencyFormatter.formatInr(exp.amount), fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (exp.isReimbursed) "Reimbursed" else "Pending",
                                                color = if (exp.isReimbursed) IncomeGreen else ExpenseRed,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ReportType.LENDING -> {
                            items(filteredLoans, key = { it.id }) { loan ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = loan.personName.ifBlank { "Contact / Borrower" }, fontWeight = FontWeight.Bold)
                                            Text(text = "Lent: ${loan.lentDate} • Status: ${loan.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(text = "Due: ${CurrencyFormatter.formatInr(loan.remainingAmount)}", color = ExpenseRed, fontWeight = FontWeight.Bold)
                                            Text(text = "Lent: ${CurrencyFormatter.formatInr(loan.amount)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            val txList = when (selectedReportType) {
                                ReportType.PERSONAL_EXPENSE -> filteredTransactions.filter { it.transactionType == TransactionType.EXPENSE }
                                ReportType.INCOME -> filteredTransactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                                ReportType.INVESTMENT -> filteredTransactions.filter { it.transactionType == TransactionType.INVESTMENT }
                                ReportType.TRANSFERS -> filteredTransactions.filter { it.transactionType == TransactionType.TRANSFER }
                                else -> filteredTransactions
                            }
                            items(txList, key = { it.id }) { tx ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = tx.description, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(text = "${tx.transactionDate} • ${tx.categoryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        val isIncome = tx.transactionType == TransactionType.INCOME || tx.creditAmount > 0
                                        Text(
                                            text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatInr(tx.amount)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isIncome) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. Custom Date Range Dialog
    if (showCustomDateDialog) {
        var tempStart by remember { mutableStateOf(customStartDate.ifBlank { DateUtils.getCurrentDate() }) }
        var tempEnd by remember { mutableStateOf(customEndDate.ifBlank { DateUtils.getCurrentDate() }) }

        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text("Select Custom Date Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Specify start and end dates (yyyy-MM-dd):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = tempStart,
                        onValueChange = { tempStart = it },
                        label = { Text("From Date (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempEnd,
                        onValueChange = { tempEnd = it },
                        label = { Text("To Date (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    customStartDate = tempStart.trim()
                    customEndDate = tempEnd.trim()
                    showCustomDateDialog = false
                }) {
                    Text("Apply Filter")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
