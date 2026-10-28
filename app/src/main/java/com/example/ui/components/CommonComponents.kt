package com.example.ui.components

import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.reports.CategoryExpenseItem
import com.example.features.reports.FinancialInsight
import com.example.features.reports.MonthlyTrendItem
import com.example.ui.theme.*
import com.example.features.transactions.SmartRulePrompt
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.HapticHelper
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

val LocalFloatingEffectEnabled = compositionLocalOf { true }

@Composable
fun MetricCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    iconColor: Color,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    customValueText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val isFloating = LocalFloatingEffectEnabled.current
    val context = LocalContext.current
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        HapticHelper.vibrateClick(context)
                        onClick()
                    }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = customValueText ?: CurrencyFormatter.formatInr(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isRevealed: Boolean = false,
    isPrivacyEnabled: Boolean = true,
    onToggleReveal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPositive = transaction.transactionType == TransactionType.INCOME ||
            transaction.transactionType == TransactionType.REFUND ||
            transaction.transactionType == TransactionType.BORROWING ||
            (transaction.transactionType == TransactionType.TRANSFER && transaction.creditAmount > 0)

    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME, TransactionType.REFUND -> IncomeGreen
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.LENDING -> LendingIndigo
        TransactionType.BORROWING -> OutstandingAmber
        TransactionType.INVESTMENT -> OutstandingAmber
        TransactionType.TRANSFER -> if (transaction.creditAmount > 0) IncomeGreen else TransferSlate
        else -> MaterialTheme.colorScheme.onSurface
    }

    val typeIcon = when (transaction.transactionType) {
        TransactionType.INCOME -> Icons.Default.ArrowDownward
        TransactionType.EXPENSE -> Icons.Default.ArrowUpward
        TransactionType.LENDING -> Icons.Default.Handshake
        TransactionType.BORROWING -> Icons.Default.CallReceived
        TransactionType.INVESTMENT -> Icons.Default.TrendingUp
        TransactionType.TRANSFER -> Icons.Default.SyncAlt
        else -> Icons.Default.Receipt
    }

    val isFloating = LocalFloatingEffectEnabled.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    HapticHelper.vibrateClick(context)
                    onClick()
                },
                onLongClick = {
                    HapticHelper.vibrateClick(context)
                    onLongClick?.invoke()
                }
            )
            .testTag("transaction_item_${transaction.id}"),
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category / Type Icon Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(amountColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = transaction.transactionType.name,
                    tint = amountColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Content: 2-row layout with perfect horizontal and vertical alignment
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val displayTitle = remember(transaction) {
                    if (transaction.transactionType == TransactionType.LENDING || transaction.categoryName.equals("Lend", ignoreCase = true) || transaction.categoryName.equals("Loans Given", ignoreCase = true)) {
                        val explicitId = transaction.advanceId?.takeIf { it.startsWith("LEND", ignoreCase = true) }
                            ?: transaction.linkedLoanId?.let { "LEND-$it" }
                            ?: Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(transaction.notes)?.groupValues?.get(1)
                            ?: Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(transaction.description)?.groupValues?.get(1)
                        if (!explicitId.isNullOrBlank()) {
                            val clean = explicitId.uppercase().replace(Regex("[_ ]"), "-")
                            if (clean.startsWith("#")) clean else "#$clean"
                        } else {
                            "#LEND-${transaction.id}"
                        }
                    } else {
                        transaction.description
                    }
                }

                // Line 1: Title on left, Amount on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val prefix = if (isPositive) "+" else if (transaction.transactionType == TransactionType.TRANSFER) "" else "-"
                    PrivacyAmountText(
                        amount = transaction.amount,
                        prefix = prefix,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        isRevealed = isRevealed,
                        isPrivacyEnabled = isPrivacyEnabled,
                        onTap = { onToggleReveal?.invoke() }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Line 2: Category Chip + ID + Date on left, Running Balance on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryChip(
                            categoryName = transaction.categoryName,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val idTag = transaction.advanceId?.takeIf { it.isNotBlank() }
                            ?: transaction.referenceNumber.takeIf { it.startsWith("LEND", true) || it.startsWith("BORROW", true) || it.startsWith("ADV", true) }
                        if (!idTag.isNullOrBlank() && !displayTitle.startsWith("#")) {
                            val (tagBg, tagFg) = when {
                                idTag.startsWith("LEND", true) || transaction.transactionType == TransactionType.LENDING -> LendingIndigo.copy(alpha = 0.15f) to LendingIndigo
                                idTag.startsWith("BORROW", true) || transaction.transactionType == TransactionType.BORROWING -> OutstandingAmber.copy(alpha = 0.15f) to OutstandingAmber
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = tagBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (idTag.startsWith("#")) idTag else "#$idTag",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tagFg,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val isCashTx = transaction.source.equals("CASH", ignoreCase = true) ||
                                transaction.categoryName.contains("cash", ignoreCase = true) ||
                                transaction.description.contains("cash", ignoreCase = true) ||
                                transaction.description.contains("atm", ignoreCase = true)
                        if (isCashTx) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = IncomeGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Cash",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = DateUtils.formatForDisplay(transaction.transactionDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    if (transaction.balanceAfterTransaction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PrivacyAmountText(
                            amount = transaction.balanceAfterTransaction,
                            prefix = "Bal: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = isPrivacyEnabled,
                            onTap = { onToggleReveal?.invoke() }
                        )
                    }
                }
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Transaction",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    categoryName: String,
    modifier: Modifier = Modifier,
    colorHex: String? = null
) {
    val fallbackColor = MaterialTheme.colorScheme.primaryContainer
    val chipColor = remember(colorHex, fallbackColor) {
        if (colorHex != null) {
            try {
                Color(android.graphics.Color.parseColor(colorHex))
            } catch (e: Exception) {
                fallbackColor
            }
        } else {
            fallbackColor
        }
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
        color = chipColor.copy(alpha = 0.18f)
    ) {
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (colorHex != null) chipColor else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PersonLoanCard(
    loan: LoanEntity,
    onRepayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (loan.amount > 0) (loan.amountRepaid / loan.amount).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(600), label = "loanProgress")

    val statusColor = when (loan.status) {
        LoanStatus.ACTIVE -> OutstandingAmber
        LoanStatus.PARTIALLY_PAID -> LendingIndigo
        LoanStatus.PAID -> IncomeGreen
        LoanStatus.OVERDUE -> ExpenseRed
        LoanStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    val isFloating = LocalFloatingEffectEnabled.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 3.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LendingIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = loan.personName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = LendingIndigo
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = loan.personName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val lendIdTag = Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1)
                            if (!lendIdTag.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = LendingIndigo.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "#$lendIdTag",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LendingIndigo,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (loan.personPhone.isNotBlank()) {
                            Text(
                                text = loan.personPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = loan.status.name.replace("_", " "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = IncomeGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Outstanding",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatInr(loan.remainingAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (loan.remainingAmount > 0) OutstandingAmber else IncomeGreen
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Repaid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatInr(loan.amountRepaid),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                }

                if (loan.remainingAmount > 0) {
                    Button(
                        onClick = onRepayClick,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Repay",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Repay", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendChart(
    trends: List<MonthlyTrendItem>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    val maxAmount = (trends.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0).coerceAtLeast(100.0)

    val isFloating = LocalFloatingEffectEnabled.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 3.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cash Flow Trends",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(IncomeGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Income", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ExpenseRed)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Expense", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bars representation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trends.takeLast(6).forEach { item ->
                    val incomeHeightFrac = (item.income / maxAmount).toFloat().coerceIn(0.05f, 1f)
                    val expenseHeightFrac = (item.expense / maxAmount).toFloat().coerceIn(0.05f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Income bar
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(incomeHeightFrac)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(IncomeGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Expense bar
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(expenseHeightFrac)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(ExpenseRed)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonutCategoryChart(
    items: List<CategoryExpenseItem>,
    title: String = "Spending by Category",
    icon: ImageVector = Icons.Default.PieChart,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    showEmptyState: Boolean = false,
    emptyMessage: String = "No categorized transactions recorded.",
    onCategoryClick: ((String) -> Unit)? = null,
    isRevealed: Boolean = false,
    isPrivacyEnabled: Boolean = true,
    onToggleReveal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() && !showEmptyState) return

    var isExpanded by remember { mutableStateOf(false) }
    val displayItems = if (isExpanded || items.size <= 5) items else items.take(5)

    val isFloating = LocalFloatingEffectEnabled.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 3.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onCategoryClick != null && items.isNotEmpty()) {
                    Text(
                        text = "${items.size} Categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (items.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart Canvas - renders all slices
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        var startAngle = -90f
                        val strokeWidth = 18.dp.toPx()

                        items.forEach { item ->
                            val sweep = (item.percentage / 100f) * 360f
                            val color = try {
                                Color(android.graphics.Color.parseColor(item.colorHex))
                            } catch (e: Exception) {
                                ExpenseRed
                            }
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweep.coerceAtLeast(1.5f),
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                            startAngle += sweep
                        }
                    }
                    Text(
                        text = "${items.size}\nCats",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend / Leaderboard
                Column(modifier = Modifier.weight(1f)) {
                    displayItems.forEach { catItem ->
                        val color = try {
                            Color(android.graphics.Color.parseColor(catItem.colorHex))
                        } catch (e: Exception) {
                            ExpenseRed
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = onCategoryClick != null) {
                                    onCategoryClick?.invoke(catItem.categoryName)
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = catItem.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${String.format(java.util.Locale.ENGLISH, "%.1f", catItem.percentage)}% • ${catItem.count} txn${if (catItem.count > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PrivacyAmountText(
                                    amount = catItem.amount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    isRevealed = isRevealed,
                                    isPrivacyEnabled = isPrivacyEnabled,
                                    onTap = { onToggleReveal?.invoke() }
                                )
                            }
                        }
                    }

                    if (items.size > 5) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Show Less" else "View All (${items.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
}



@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SmartRulePromptDialog(
    prompt: SmartRulePrompt,
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editableKeyword by remember(prompt.keyword) { mutableStateOf(prompt.keyword) }

    // Keyword suggestions extracted from transaction description
    val suggestions = remember(prompt.transactionDescription) {
        if (prompt.transactionDescription.isBlank()) emptyList()
        else {
            prompt.transactionDescription
                .split(" ", "\t", "-", "_", "/", ".", ",")
                .map { it.trim().lowercase() }
                .filter { it.length > 2 }
                .distinct()
                .take(8)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = LendingIndigo.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Auto Rule",
                                tint = LendingIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Create Auto-Rule?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Automatically categorize future transactions with matching keyword into ${prompt.categoryName}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = editableKeyword,
                    onValueChange = { editableKeyword = it },
                    label = { Text("Matching Keyword") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Suggestions from description:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { word ->
                            SuggestionChip(
                                onClick = { editableKeyword = word },
                                label = { Text(word, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target Category: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CategoryChip(categoryName = prompt.categoryName)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Not Now")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editableKeyword.isNotBlank()) {
                                onAccept(editableKeyword.trim())
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = editableKeyword.isNotBlank()
                    ) {
                        Text("Save Rule")
                    }
                }
            }
        }
    }
}


@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
fun PrivacyAmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    prefix: String = "",
    isRevealed: Boolean = false,
    isPrivacyEnabled: Boolean = true,
    onTap: () -> Unit = {}
) {
    val shouldMask = isPrivacyEnabled && !isRevealed
    val formatted = prefix + CurrencyFormatter.formatInr(amount)
    val context = LocalContext.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = isPrivacyEnabled) {
                HapticHelper.vibrateClick(context)
                onTap()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (shouldMask) {
            Text(
                text = "₹ • • • •",
                style = style,
                color = if (color != Color.Unspecified) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = fontWeight ?: style.fontWeight,
                fontSize = if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize else style.fontSize
            )
        } else {
            Text(
                text = formatted,
                style = style,
                color = color,
                fontWeight = fontWeight ?: style.fontWeight,
                fontSize = if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize else style.fontSize
            )
        }

    }
}

@Composable
fun OnboardingPreferencesDialog(
    initialIsDark: Boolean,
    initialPrivacyEnabled: Boolean,
    initialTimeoutSeconds: Int,
    onSave: (isDark: Boolean, privacyEnabled: Boolean, timeoutSeconds: Int) -> Unit
) {
    var isDark by remember { mutableStateOf(initialIsDark) }
    var privacyEnabled by remember { mutableStateOf(initialPrivacyEnabled) }
    var timeoutSeconds by remember { mutableStateOf(initialTimeoutSeconds) }

    Dialog(onDismissRequest = { /* Require user to tap Get Started */ }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Welcome to Yosan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set your personal preferences",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Theme Selection
                Text(
                    text = "1. Choose Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = !isDark,
                        onClick = { isDark = false },
                        label = { Text("Light Mode") },
                        leadingIcon = {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isDark,
                        onClick = { isDark = true },
                        label = { Text("Dark Mode") },
                        leadingIcon = {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Privacy Mode (Blur & Hide Balances)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Financial Privacy Blur",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Hide rupee amounts by default; tap to reveal temporarily.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = privacyEnabled,
                        onCheckedChange = { privacyEnabled = it }
                    )
                }

                if (privacyEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Auto-hide reveal timer:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { seconds ->
                            FilterChip(
                                selected = timeoutSeconds == seconds,
                                onClick = { timeoutSeconds = seconds },
                                label = { Text("${seconds}s") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onSave(isDark, privacyEnabled, timeoutSeconds) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryTransactionsDialog(
    categoryName: String,
    transactions: List<com.example.data.local.entity.TransactionEntity>,
    onDismiss: () -> Unit,
    onTransactionClick: (com.example.data.local.entity.TransactionEntity) -> Unit,
    isRevealed: Boolean = false,
    isPrivacyEnabled: Boolean = false,
    onToggleReveal: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${transactions.size} transaction${if (transactions.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions found for this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(transactions.size) { index ->
                        val tx = transactions[index]
                        val isIncome = tx.transactionType == com.example.data.local.entity.TransactionType.INCOME || tx.transactionType == com.example.data.local.entity.TransactionType.REFUND
                        val amountColor = if (isIncome) IncomeGreen else ExpenseRed
                        val prefix = if (isIncome) "+" else "-"

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onTransactionClick(tx)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.description.ifBlank { "Transaction" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tx.transactionDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                PrivacyAmountText(
                                    amount = if (isIncome && tx.creditAmount > 0) tx.creditAmount else if (tx.debitAmount > 0) tx.debitAmount else tx.amount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor,
                                    prefix = prefix,
                                    isRevealed = isRevealed,
                                    isPrivacyEnabled = isPrivacyEnabled,
                                    onTap = { onToggleReveal?.invoke() }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Modern shimmer modifier providing animated skeleton loading effect
 */
@Composable
fun Modifier.shimmerLoading(
    durationMillis: Int = 1200
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseColor = if (isDark) Color(0xFF282C34) else Color(0xFFE2E8F0)
    val highlightColor = if (isDark) Color(0xFF3E4451) else Color(0xFFF1F5F9)

    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor
    )

    return this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 450f, translateAnim - 450f),
            end = Offset(translateAnim, translateAnim)
        )
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerLoading()
    )
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 100.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                SkeletonBox(modifier = Modifier.size(width = 110.dp, height = 16.dp))
                SkeletonBox(modifier = Modifier.size(28.dp), shape = CircleShape)
            }
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonBox(modifier = Modifier.size(width = 160.dp, height = 24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.size(width = 90.dp, height = 12.dp))
        }
    }
}

@Composable
fun SkeletonMetricCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(modifier = Modifier.size(width = 75.dp, height = 12.dp))
                SkeletonBox(modifier = Modifier.size(24.dp), shape = CircleShape)
            }
            Spacer(modifier = Modifier.height(12.dp))
            SkeletonBox(modifier = Modifier.size(width = 100.dp, height = 20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.size(width = 60.dp, height = 10.dp))
        }
    }
}

@Composable
fun SkeletonTransactionItem(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(modifier = Modifier.size(width = 140.dp, height = 16.dp))
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.size(width = 90.dp, height = 12.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            SkeletonBox(modifier = Modifier.size(width = 70.dp, height = 18.dp))
        }
    }
}

@Composable
fun SkeletonDashboardLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 130.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonMetricCard(modifier = Modifier.weight(1f))
            SkeletonMetricCard(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(modifier = Modifier.size(width = 150.dp, height = 18.dp))
        repeat(4) {
            SkeletonTransactionItem()
        }
    }
}

@Composable
fun SkeletonTransactionsLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                SkeletonBox(modifier = Modifier.size(width = 75.dp, height = 32.dp), shape = RoundedCornerShape(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        repeat(5) {
            SkeletonTransactionItem()
        }
    }
}

@Composable
fun SkeletonReportsLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 120.dp)
        SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 200.dp)
        SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 160.dp)
    }
}

@Composable
fun SkeletonOfficialExpensesLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonMetricCard(modifier = Modifier.weight(1f))
            SkeletonMetricCard(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        repeat(4) {
            SkeletonTransactionItem()
        }
    }
}



