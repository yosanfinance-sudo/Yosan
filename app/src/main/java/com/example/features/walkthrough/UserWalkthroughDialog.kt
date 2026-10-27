package com.example.features.walkthrough

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LendingIndigo

data class WalkthroughModule(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val sections: List<WalkthroughSection>
)

data class WalkthroughSection(
    val heading: String,
    val content: String,
    val bulletPoints: List<String> = emptyList()
)

val WALKTHROUGH_MODULES = listOf(
    WalkthroughModule(
        id = 1,
        title = "1. Introduction to Yosan",
        subtitle = "Offline-first personal & official record keeping",
        icon = Icons.Default.AccountBalanceWallet,
        iconTint = Color(0xFF2563EB),
        sections = listOf(
            WalkthroughSection(
                heading = "What Yosan Finance Does",
                content = "Yosan Finance helps you record and organize personal expenses, company/official expenses, income, borrowing, lending, investments, and rotational transfers in one single, organized place."
            ),
            WalkthroughSection(
                heading = "Authoritative Local Data",
                content = "The application operates offline-first. Summaries and charts are derived strictly from records entered or imported into the local database.",
                bulletPoints = listOf(
                    "No automatic bank scraping unless explicitly supported and configured.",
                    "No unauthorized data transmission to external servers.",
                    "Full user control over backup files and exports."
                )
            )
        )
    ),
    WalkthroughModule(
        id = 2,
        title = "2. Dashboard & Summaries",
        subtitle = "High-level overview of financial flows",
        icon = Icons.Default.Dashboard,
        iconTint = Color(0xFF0284C7),
        sections = listOf(
            WalkthroughSection(
                heading = "Real-Time Calculated Summaries",
                content = "The Dashboard calculates your Current Balance, Total Income, Personal Expenses, Money Lent, and Company Claims from stored local transactions."
            ),
            WalkthroughSection(
                heading = "Date Filters & Unclassified Records",
                content = "Date filters let you inspect specific periods without distorting historical balances. Unclassified records remain distinctly visible until you review them."
            )
        )
    ),
    WalkthroughModule(
        id = 3,
        title = "3. Personal Expenses",
        subtitle = "Tracking individual living costs",
        icon = Icons.Default.Person,
        iconTint = ExpenseRed,
        sections = listOf(
            WalkthroughSection(
                heading = "Separating Personal from Official",
                content = "Only transactions explicitly classified as Personal Expenses appear in your personal spending totals. Debits are not assumed to be personal expenses by default."
            ),
            WalkthroughSection(
                heading = "Recorded Fields",
                content = "Each personal expense records: Amount, Date, Description/Narration, Category (e.g. Food, Utilities, Shopping), and Associated Bank Account."
            )
        )
    ),
    WalkthroughModule(
        id = 4,
        title = "4. Official Expenses & Claims",
        subtitle = "Corporate claims, advances & reimbursements",
        icon = Icons.Default.BusinessCenter,
        iconTint = Color(0xFF06B6D4),
        sections = listOf(
            WalkthroughSection(
                heading = "The Official Claims Lifecycle",
                content = "Expenses incurred on behalf of your company follow a distinct workflow to keep them separate from personal spending:",
                bulletPoints = listOf(
                    "Pending Claim: Official expenses recorded but not yet submitted for reimbursement.",
                    "Applied Claim: Claims submitted to your company awaiting approval or payment.",
                    "Approved vs Reimbursed: Distinguishes eligible claim amount from payments received.",
                    "Outstanding Reimbursement: (Approved Amount - Confirmed Reimbursements received).",
                    "Company Advance: Tracks advance funds received and their utilization."
                )
            )
        )
    ),
    WalkthroughModule(
        id = 5,
        title = "5. Spending by Category",
        subtitle = "Categorized analysis and trends",
        icon = Icons.Default.PieChart,
        iconTint = Color(0xFFF59E0B),
        sections = listOf(
            WalkthroughSection(
                heading = "Category Grouping",
                content = "Expenses are aggregated into user-defined or system categories (Food, Travel, Bills, Shopping, etc.). Tapping any category displays its underlying transactions."
            ),
            WalkthroughSection(
                heading = "Integrity Safeguard",
                content = "Borrowing principal, lending outlays, investments, and internal transfers are strictly excluded from ordinary spending totals to prevent double counting."
            )
        )
    ),
    WalkthroughModule(
        id = 6,
        title = "6. Borrow (Money Borrowed)",
        subtitle = "Tracking funds owed to others",
        icon = Icons.Default.CallReceived,
        iconTint = Color(0xFFDC2626),
        sections = listOf(
            WalkthroughSection(
                heading = "Borrowing Records",
                content = "Record borrowings with lender name, date, amount, and notes. Borrowed principal is never counted as ordinary income."
            ),
            WalkthroughSection(
                heading = "Repayment Tracking",
                content = "Repayments reduce your outstanding debt balance. Principal repayments are not treated as everyday expenses."
            )
        )
    ),
    WalkthroughModule(
        id = 7,
        title = "7. Income by Category",
        subtitle = "Tracking salaries, freelance & inflows",
        icon = Icons.Default.TrendingUp,
        iconTint = IncomeGreen,
        sections = listOf(
            WalkthroughSection(
                heading = "Explicit Income Classification",
                content = "Income is classified by source (Salary, Business, Freelance, Dividends). Credits are not automatically treated as income without classification."
            ),
            WalkthroughSection(
                heading = "Exclusions",
                content = "Borrowing receipts, internal self-transfers, company advances, and returned lending principal are excluded from ordinary income totals."
            )
        )
    ),
    WalkthroughModule(
        id = 8,
        title = "8. Lend (Money Lent)",
        subtitle = "Tracking funds owed to you",
        icon = Icons.Default.CallMade,
        iconTint = LendingIndigo,
        sections = listOf(
            WalkthroughSection(
                heading = "Lending Records",
                content = "Record money lent to friends, family, or colleagues with contact name, phone, due date, and amount. Lending principal is not an everyday expense."
            ),
            WalkthroughSection(
                heading = "Repayment Collections",
                content = "Track partial or full repayments. Returned principal does not inflate your regular income totals."
            )
        )
    ),
    WalkthroughModule(
        id = 9,
        title = "9. Investments & Wealth Assets",
        subtitle = "Tracking capital allocations",
        icon = Icons.Default.ShowChart,
        iconTint = Color(0xFF10B981),
        sections = listOf(
            WalkthroughSection(
                heading = "Capital Contributions",
                content = "Record capital allocated to Mutual Funds, SIPs, Stocks, Fixed Deposits, and Gold. Investment contributions are separated from daily living expenses."
            ),
            WalkthroughSection(
                heading = "No Speculative Valuation",
                content = "The app records historical contributions and withdrawals; it does not fabricate live market prices or investment advice without a verified live broker feed."
            )
        )
    ),
    WalkthroughModule(
        id = 10,
        title = "10. Rotational / Transfers",
        subtitle = "Inter-account self movements",
        icon = Icons.Default.SyncAlt,
        iconTint = Color(0xFF64748B),
        sections = listOf(
            WalkthroughSection(
                heading = "Self-Transfer Balancing",
                content = "When moving funds between your own bank accounts, mark the transaction as Transfer/Rotational."
            ),
            WalkthroughSection(
                heading = "Zero Net Effect",
                content = "Internal transfers are excluded from both Income and Expense totals so moving ₹10,000 from Savings to Current does not double count as spending."
            )
        )
    ),
    WalkthroughModule(
        id = 11,
        title = "11. Manual Addition & Validation",
        subtitle = "Recording cash or manual transactions",
        icon = Icons.Default.EditNote,
        iconTint = Color(0xFF8B5CF6),
        sections = listOf(
            WalkthroughSection(
                heading = "Form Validation",
                content = "Manual entry requires a valid positive amount, date, and transaction classification. Duplicate submissions are automatically prevented."
            ),
            WalkthroughSection(
                heading = "Preserving Edits on Error",
                content = "If validation fails, your entered data is preserved with a clear error description rather than discarding your inputs."
            )
        )
    ),
    WalkthroughModule(
        id = 12,
        title = "12. AI Financial Assistant",
        subtitle = "Observations, queries & summaries",
        icon = Icons.Default.AutoAwesome,
        iconTint = Color(0xFF7C3AED),
        sections = listOf(
            WalkthroughSection(
                heading = "Assisted Observations",
                content = "The AI Assistant summarizes spending trends and answers questions (e.g. 'How much did I spend on dining this month?')."
            ),
            WalkthroughSection(
                heading = "Deterministic Accounting Authority",
                content = "All authoritative totals and calculations are computed deterministically by the application engine, never by AI guesswork."
            )
        )
    ),
    WalkthroughModule(
        id = 13,
        title = "Settings & Privacy Guide",
        subtitle = "Managing preferences, backups & security",
        icon = Icons.Default.Settings,
        iconTint = Color(0xFF475569),
        sections = listOf(
            WalkthroughSection(
                heading = "8 Settings Sections",
                content = "1. User Profile (Avatar, Name, Email, Drive status)\n2. Finance Categorization (Safe rule and category management)\n3. Appearance & Layout (Dark mode, Privacy blur, Biometric lock)\n4. Bank Accounts (Local accounts management)\n5. Backup & Restore (Drive snapshots, Excel sync, Undo)\n6. System & Updates (Check updates, Feedback)\n7. Danger Zone (Account deletion vs Logout)\n8. Developer Details"
            ),
            WalkthroughSection(
                heading = "Permissions & Financial Privacy",
                content = "• Google Drive: Optional backup only; separate from sign-in.\n• Biometrics: Verified by your phone's secure hardware; no templates stored.\n• Privacy Blur: Masks balances in public places using ₹••••.\n• File Picker: Uses Android's system picker for secure import and export."
            )
        )
    )
)

@Composable
fun UserWalkthroughDialog(
    onDismissRequest: () -> Unit,
    initialModuleId: Int = 1,
    onCompleted: (dontShowAgain: Boolean) -> Unit = {}
) {
    var currentModuleIndex by remember { mutableIntStateOf((initialModuleId - 1).coerceIn(0, WALKTHROUGH_MODULES.size - 1)) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val currentModule = WALKTHROUGH_MODULES[currentModuleIndex]
    val isLastModule = currentModuleIndex == WALKTHROUGH_MODULES.size - 1

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("user_walkthrough_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header: Step Indicator & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(currentModule.iconTint.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentModule.icon,
                            contentDescription = null,
                            tint = currentModule.iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "In-App User Walkthrough",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step ${currentModuleIndex + 1} of ${WALKTHROUGH_MODULES.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        onCompleted(dontShowAgain)
                        onDismissRequest()
                    }) {
                        Text("Skip")
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentModuleIndex + 1).toFloat() / WALKTHROUGH_MODULES.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = currentModule.iconTint,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Scrollable Content for this Module
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = currentModule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentModule.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    currentModule.sections.forEach { section ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = section.heading,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = currentModule.iconTint
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = section.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )

                                if (section.bulletPoints.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    section.bulletPoints.forEach { pt ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "• ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = currentModule.iconTint,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = pt,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // "Don't show again" checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Don't show this walkthrough automatically again",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Nav Buttons: Previous, Next / Finish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentModuleIndex > 0) currentModuleIndex--
                        },
                        enabled = currentModuleIndex > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }

                    Button(
                        onClick = {
                            if (isLastModule) {
                                onCompleted(dontShowAgain)
                                onDismissRequest()
                            } else {
                                currentModuleIndex++
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = currentModule.iconTint)
                    ) {
                        Text(if (isLastModule) "Finish" else "Next", fontWeight = FontWeight.Bold)
                        if (!isLastModule) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
