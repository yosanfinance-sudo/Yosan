package com.example.features.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.verticalScroll
import com.example.data.local.entity.StatementAuditStatus
import com.example.data.local.entity.StatementImportEntity
import com.example.ui.components.CategoryChip
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.HapticHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    viewModel: FinanceViewModel,
    onImportSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewResult by viewModel.importPreview.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var customText by remember { mutableStateOf("") }
    var skipDuplicates by remember { mutableStateOf(true) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfDocPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPdfPage by remember { mutableIntStateOf(0) }
    var isRenderingPdf by remember { mutableStateOf(false) }
    val statementImports by viewModel.statementImports.collectAsState()
    val isPasswordPromptRequired by viewModel.isPasswordPromptRequired.collectAsState()
    var pdfPasswordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var selectedScreenTab by remember { mutableIntStateOf(0) }
    var showAuditDetailsDialog by remember { mutableStateOf(false) }
    var importToDelete by remember { mutableStateOf<StatementImportEntity?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pdfDocPages.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
        }
    }

    LaunchedEffect(selectedPdfUri) {
        val uri = selectedPdfUri
        if (uri != null) {
            isRenderingPdf = true
            val pages = withContext(Dispatchers.IO) {
                val resultList = mutableListOf<Bitmap>()
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val count = renderer.pageCount.coerceAtMost(30)
                            for (i in 0 until count) {
                                renderer.openPage(i).use { page ->
                                    val width = (page.width * 1.4f).toInt()
                                    val height = (page.height * 1.4f).toInt()
                                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    resultList.add(bmp)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                resultList
            }
            pdfDocPages.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            pdfDocPages = pages
            currentPdfPage = 0
            isRenderingPdf = false
        } else {
            pdfDocPages.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            pdfDocPages = emptyList()
            isRenderingPdf = false
        }
    }

    // Auto-detect account from file name when file is selected
    LaunchedEffect(selectedFileName, accounts) {
        if (accounts.isNotEmpty()) {
            if (selectedFileName != null) {
                val matched = accounts.firstOrNull { acc ->
                    selectedFileName!!.contains(acc.bankName, ignoreCase = true) ||
                    selectedFileName!!.contains(acc.accountName, ignoreCase = true)
                }
                if (matched != null) {
                    selectedAccountId = matched.id
                } else if (selectedAccountId == null || accounts.none { it.id == selectedAccountId }) {
                    selectedAccountId = accounts.firstOrNull()?.id
                }
            } else if (selectedAccountId == null || accounts.none { it.id == selectedAccountId }) {
                selectedAccountId = accounts.firstOrNull()?.id
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val resolved = resolveStatementFileInfo(context, it)
            if (resolved.isSupported) {
                selectedFileName = resolved.fileName
                if (resolved.fileName.endsWith(".pdf", ignoreCase = true)) {
                    selectedPdfUri = it
                } else {
                    selectedPdfUri = null
                }
                viewModel.parseStatementUri(context, it, resolved.fileName)
            } else {
                viewModel.showMessage("Invalid file format. Please select an Excel (.xlsx, .xls) or PDF (.pdf) statement.")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("import_statement_screen")
    ) {
        // Tab Row: 0 = Import Statement, 1 = History
        TabRow(
            selectedTabIndex = selectedScreenTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedScreenTab == 0,
                onClick = { selectedScreenTab = 0 },
                text = { Text("Import Statement") },
                icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedScreenTab == 1,
                onClick = { selectedScreenTab = 1 },
                text = { Text("History (${statementImports.size})") },
                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (selectedScreenTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Header with Bank Support Notice
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Upload bank statements in Excel (.xlsx, .xls) or PDF (.pdf) formats with automated mathematical reconciliation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Upload Document Card (PDF, Excel, CSV)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upload Bank Statement (Excel / PDF)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Select an Excel spreadsheet (.xlsx, .xls) or PDF statement from your device storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedFileName != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedFileName!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.setExternalIntentActive(true)
                                    filePickerLauncher.launch(SUPPORTED_STATEMENT_MIMES)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Statement File (.xlsx, .xls, .pdf)")
                            }
                        }
                    }
                }

                // 2b. Document Preview (Multi-page display for uploaded PDF statements)
                if (isRenderingPdf || pdfDocPages.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Document Preview",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (pdfDocPages.isNotEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Page ${currentPdfPage + 1} of ${pdfDocPages.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isRenderingPdf && pdfDocPages.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (pdfDocPages.isNotEmpty()) {
                                    // Multi-page navigation controls
                                    if (pdfDocPages.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = { if (currentPdfPage > 0) currentPdfPage-- },
                                                enabled = currentPdfPage > 0,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Previous")
                                            }

                                            Text(
                                                text = "Page ${currentPdfPage + 1} / ${pdfDocPages.size}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            FilledTonalButton(
                                                onClick = { if (currentPdfPage < pdfDocPages.size - 1) currentPdfPage++ },
                                                enabled = currentPdfPage < pdfDocPages.size - 1,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Next")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    val currentBmp = pdfDocPages.getOrNull(currentPdfPage) ?: pdfDocPages.first()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp)
                                        ) {
                                            Image(
                                                bitmap = currentBmp.asImageBitmap(),
                                                contentDescription = "Statement Document Page ${currentPdfPage + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }

                                    // Multi-page thumbnail strip for fast page switching
                                    if (pdfDocPages.size > 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "All Document Pages (${pdfDocPages.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            itemsIndexed(pdfDocPages) { idx, bmp ->
                                                val isSelected = idx == currentPdfPage
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { currentPdfPage = idx }
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(4.dp)
                                                ) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Thumbnail Page ${idx + 1}",
                                                        modifier = Modifier
                                                            .height(80.dp)
                                                            .width(56.dp)
                                                            .clip(RoundedCornerShape(4.dp)),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Page ${idx + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

                // 3. Custom Paste Input Area
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Or Paste Statement Text",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                placeholder = { Text("Txn Date,Description,Debit,Credit,Balance\n2026-02-01,STORE PURCHASE,340,0,45000") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.previewStatement(customText, "pasted_statement.csv") },
                                enabled = customText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Parse & Preview Text")
                            }
                        }
                    }
                }

                // 4. Preview & Validation Section
                if (previewResult != null) {
                    val preview = previewResult!!
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // 4a. Detected Bank & Override Selector
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Bank: ${preview.detectedBank.displayName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Bank Override",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            SupportedBank.INDIAN_BANK to "Indian Bank",
                                            SupportedBank.STATE_BANK to "State Bank",
                                            SupportedBank.YES_BANK to "YES Bank",
                                            SupportedBank.INDUSIND_BANK to "IndusInd Bank"
                                        ).forEach { (bankEnum, bankLabel) ->
                                            val isSelected = preview.detectedBank == bankEnum
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticHelper.vibrateClick(context)
                                                    viewModel.setManualBankOverride(bankEnum)
                                                },
                                                label = { Text(bankLabel) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4b. Mathematical Audit Result Banner
                            preview.auditReport?.let { audit ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (audit.status) {
                                            StatementAuditStatus.PASS -> IncomeGreen.copy(alpha = 0.12f)
                                            StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber.copy(alpha = 0.15f)
                                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        }
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        when (audit.status) {
                                            StatementAuditStatus.PASS -> IncomeGreen.copy(alpha = 0.4f)
                                            StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = when (audit.status) {
                                                        StatementAuditStatus.PASS -> Icons.Default.CheckCircle
                                                        StatementAuditStatus.REVIEW_REQUIRED -> Icons.Default.Warning
                                                        else -> Icons.Default.Info
                                                    },
                                                    contentDescription = null,
                                                    tint = when (audit.status) {
                                                        StatementAuditStatus.PASS -> IncomeGreen
                                                        StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber
                                                        else -> MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Audit: ${audit.status}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (audit.status) {
                                                        StatementAuditStatus.PASS -> IncomeGreen
                                                        StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = { showAuditDetailsDialog = true },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Audit Details", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "Valid: ${audit.validCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(text = "Debits: ${CurrencyFormatter.formatInrCompact(audit.totalDebits)}", color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                                            Text(text = "Credits: ${CurrencyFormatter.formatInrCompact(audit.totalCredits)}", color = IncomeGreen, style = MaterialTheme.typography.bodySmall)
                                        }

                                        if (audit.discrepancies.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "${audit.discrepancies.size} discrepancy flagged. Review items below before confirming import.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OutstandingAmber,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = skipDuplicates,
                                    onCheckedChange = { skipDuplicates = it }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Skip duplicate transactions", style = MaterialTheme.typography.bodyMedium)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val defaultBankAccountId = accounts.firstOrNull { it.isDefault && it.accountType != com.example.data.local.entity.AccountType.CASH }?.id
                                        ?: accounts.firstOrNull { it.accountType != com.example.data.local.entity.AccountType.CASH }?.id
                                        ?: accounts.firstOrNull()?.id
                                        ?: 1L
                                    val accountId = selectedAccountId ?: defaultBankAccountId
                                    viewModel.confirmImport(
                                        skipDuplicates = skipDuplicates,
                                        targetAccountId = accountId,
                                        onComplete = onImportSuccess
                                    )
                                },
                                enabled = !isImporting && preview.validRows.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Confirm & Import ${preview.validRows.size} Transactions", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Transactions Preview (${preview.validRows.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(preview.validRows) { row ->
                        ImportRowPreviewCard(row = row, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        } else {
            // Tab 1: Statement Import History
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Statement Import History & Audits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review previously parsed statements, audit outcomes, or safely delete an imported batch and its transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (statementImports.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Imported Statements Yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Statements imported from Indian Bank, State Bank, YES Bank, or IndusInd Bank will appear here along with mathematical audit logs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(statementImports, key = { it.id }) { item ->
                        StatementImportHistoryCard(
                            item = item,
                            onDeleteClick = { importToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Statement Audit Details Dialog
    if (showAuditDetailsDialog && previewResult?.auditReport != null) {
        StatementAuditDetailsDialog(
            audit = previewResult!!.auditReport!!,
            onDismiss = { showAuditDetailsDialog = false }
        )
    }

    // Dialog 2: Delete Import Confirmation Dialog
    if (importToDelete != null) {
        val target = importToDelete!!
        AlertDialog(
            onDismissRequest = { importToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ExpenseRed
                )
            },
            title = {
                Text(
                    text = "Delete Imported Transactions?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are about to delete ${target.importedCount} transactions associated with this import:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "File: ${target.fileName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(text = "Bank: ${target.bankName}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Imported: ${DateUtils.formatDateTime(target.importTimestamp)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        text = "This action will remove all ${target.importedCount} imported transactions from your account and automatically recalculate running balances. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        importToDelete = null
                        viewModel.deleteStatementImport(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete Transactions")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { importToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isPasswordPromptRequired) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissStatementPasswordPrompt()
                pdfPasswordInput = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Password Protected PDF", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "This bank statement PDF is encrypted. Enter your document password (commonly Date of Birth in DDMMYYYY or registered phone/PAN number).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pdfPasswordInput,
                        onValueChange = { pdfPasswordInput = it },
                        label = { Text("Statement Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pdfPasswordInput.isNotBlank()) {
                            com.example.utils.HapticHelper.vibrateClick(context)
                            viewModel.submitStatementPassword(context, pdfPasswordInput)
                            pdfPasswordInput = ""
                        }
                    },
                    enabled = pdfPasswordInput.isNotBlank()
                ) {
                    Text("Unlock & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.example.utils.HapticHelper.vibrateClick(context)
                    viewModel.dismissStatementPasswordPrompt()
                    pdfPasswordInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class ResolvedStatementFile(
    val fileName: String,
    val isSupported: Boolean
)

private val SUPPORTED_STATEMENT_MIMES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/x-excel",
    "application/msexcel",
    "application/excel",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream"
)

private fun resolveStatementFileInfo(context: Context, uri: Uri): ResolvedStatementFile {
    var fileName = ""

    // 1. Try OpenableColumns.DISPLAY_NAME
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: ""
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Fallback to URI lastPathSegment
    if (fileName.isBlank()) {
        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank()) {
            fileName = lastSegment.substringAfterLast('/').substringAfterLast(':')
        }
    }
    if (fileName.isBlank()) {
        fileName = "statement"
    }

    val lower = fileName.lowercase()
    val hasValidExt = lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".pdf") || lower.endsWith(".csv")
    if (hasValidExt) {
        return ResolvedStatementFile(fileName, true)
    }

    // 3. Inspect ContentResolver MIME type
    val mimeType = try {
        context.contentResolver.getType(uri)?.lowercase()
    } catch (_: Exception) { null }

    when {
        mimeType == "application/pdf" -> return ResolvedStatementFile("$fileName.pdf", true)
        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> return ResolvedStatementFile("$fileName.xlsx", true)
        mimeType == "application/vnd.ms-excel" || mimeType == "application/x-excel" || mimeType == "application/msexcel" || mimeType == "application/excel" -> return ResolvedStatementFile("$fileName.xls", true)
        mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> return ResolvedStatementFile("$fileName.csv", true)
    }

    // 4. Magic bytes inspection from stream
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(16)
            val read = stream.read(header)
            if (read >= 4) {
                // PDF: %PDF
                if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
                    return ResolvedStatementFile("$fileName.pdf", true)
                }
                // ZIP / XLSX: PK\x03\x04
                if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    return ResolvedStatementFile("$fileName.xlsx", true)
                }
                // OLE2 / XLS: 0xD0, 0xCF, 0x11, 0xE0
                if (read >= 8 && header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
                    return ResolvedStatementFile("$fileName.xls", true)
                }
            }
        }
    } catch (_: Exception) {}

    return ResolvedStatementFile(fileName, false)
}

@Composable
fun ImportRowPreviewCard(row: ParsedImportRow, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isDuplicate) OutstandingAmber.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Line 1: Description on left, Amount on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val isIncome = row.creditAmount > 0
                Text(
                    text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatInr(row.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) IncomeGreen else ExpenseRed
                )
            }

            // Line 2: Reference number on left, Running Balance on right
            if (row.referenceNumber.isNotBlank() || row.balance != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.referenceNumber.isNotBlank()) {
                        Text(
                            text = "Ref: ${row.referenceNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (row.balance != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bal: ${CurrencyFormatter.formatInrCompact(row.balance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Line 3: Category Chip + Date + Duplicate Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(
                    categoryName = row.suggestedCategory,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = DateUtils.formatShortDisplay(row.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                if (row.isDuplicate) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Duplicate",
                        color = OutstandingAmber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                if (row.hasDiscrepancy) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Audit Flag",
                        color = ExpenseRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatementAuditDetailsDialog(
    audit: StatementAuditReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (audit.status) {
                        StatementAuditStatus.PASS -> Icons.Default.CheckCircle
                        StatementAuditStatus.REVIEW_REQUIRED -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (audit.status) {
                        StatementAuditStatus.PASS -> IncomeGreen
                        StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Statement Audit Report", fontWeight = FontWeight.Bold)
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
                    color = when (audit.status) {
                        StatementAuditStatus.PASS -> IncomeGreen.copy(alpha = 0.12f)
                        StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Audit Outcome: ${audit.status}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = when (audit.status) {
                                StatementAuditStatus.PASS -> IncomeGreen
                                StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = "Detected Bank: ${audit.detectedBank.displayName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                HorizontalDivider()

                Text("Audit Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Parsed Transactions:", style = MaterialTheme.typography.bodySmall)
                    Text("${audit.validCount}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Duplicate Transactions:", style = MaterialTheme.typography.bodySmall)
                    Text("${audit.duplicateCount}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Withdrawals (Debits):", style = MaterialTheme.typography.bodySmall)
                    Text("-${CurrencyFormatter.formatInr(audit.totalDebits)}", fontWeight = FontWeight.SemiBold, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Deposits (Credits):", style = MaterialTheme.typography.bodySmall)
                    Text("+${CurrencyFormatter.formatInr(audit.totalCredits)}", fontWeight = FontWeight.SemiBold, color = IncomeGreen, style = MaterialTheme.typography.bodySmall)
                }

                if (audit.openingBalance != null || audit.closingBalance != null) {
                    HorizontalDivider()
                    Text("Balance Reconciliation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    if (audit.openingBalance != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opening Balance:", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.formatInr(audit.openingBalance), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (audit.closingBalance != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Statement Closing Balance:", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.formatInr(audit.closingBalance), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (audit.calculatedClosingBalance != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reconciled Balance:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                CurrencyFormatter.formatInr(audit.calculatedClosingBalance),
                                fontWeight = FontWeight.Bold,
                                color = if (audit.balanceReconciliationPassed == true) IncomeGreen else ExpenseRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Reconciliation Check:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (audit.balanceReconciliationPassed == true) "PASSED" else if (audit.balanceReconciliationPassed == false) "FAILED" else "INCOMPLETE",
                            fontWeight = FontWeight.Bold,
                            color = if (audit.balanceReconciliationPassed == true) IncomeGreen else if (audit.balanceReconciliationPassed == false) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (audit.discrepancies.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Flagged Discrepancies (${audit.discrepancies.size})", fontWeight = FontWeight.Bold, color = OutstandingAmber, style = MaterialTheme.typography.bodyMedium)
                    audit.discrepancies.forEach { disc ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${disc.field}${if (disc.rowIndex > 0) " (Row ${disc.rowIndex})" else ""}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "${disc.actual}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = disc.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun StatementImportHistoryCard(
    item: StatementImportEntity,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.bankName} • ${DateUtils.formatDateTime(item.importTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when (item.auditStatus) {
                        StatementAuditStatus.PASS -> IncomeGreen.copy(alpha = 0.15f)
                        StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = item.auditStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (item.auditStatus) {
                            StatementAuditStatus.PASS -> IncomeGreen
                            StatementAuditStatus.REVIEW_REQUIRED -> OutstandingAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.importedCount} txns imported (${item.totalParsed} parsed)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.duplicateCount > 0) {
                    Text(
                        text = "${item.duplicateCount} duplicates",
                        style = MaterialTheme.typography.bodySmall,
                        color = OutstandingAmber
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Debits:", style = MaterialTheme.typography.bodySmall)
                    Text("-${CurrencyFormatter.formatInr(item.totalDebits)}", color = ExpenseRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Credits:", style = MaterialTheme.typography.bodySmall)
                    Text("+${CurrencyFormatter.formatInr(item.totalCredits)}", color = IncomeGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                if (item.openingBalance != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opening Balance:", style = MaterialTheme.typography.bodySmall)
                        Text(CurrencyFormatter.formatInr(item.openingBalance), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (item.closingBalance != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Closing Balance:", style = MaterialTheme.typography.bodySmall)
                        Text(CurrencyFormatter.formatInr(item.closingBalance), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Import & Transactions")
                }
            }
        }
    }
}
