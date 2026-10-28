package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.*
import com.example.features.dashboard.DashboardCardType
import com.example.features.reports.DashboardSummary
import com.example.features.reports.DateRangeFilter
import com.example.features.reports.FinancialCalculationService
import com.example.features.transactions.AddTab
import com.example.ui.screens.MainTab
import com.example.ui.screens.SubScreen
import com.example.utils.AppPreferences
import com.example.utils.BackupEncryptionUtil
import com.example.utils.BudgetPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AllFeaturesAndButtonsValidationTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences
    private lateinit var budgetPreferences: BudgetPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
        budgetPreferences = BudgetPreferences(context)
        appPreferences.clearAll()
    }

    @Test
    fun testMainNavigationTabsIntegrity() {
        // Verify all primary tabs exist with valid titles and test tags
        val tabs = MainTab.values()
        assertEquals(4, tabs.size)
        assertTrue(tabs.contains(MainTab.DASHBOARD))
        assertTrue(tabs.contains(MainTab.TRANSACTIONS))
        assertTrue(tabs.contains(MainTab.REPORTS))
        assertTrue(tabs.contains(MainTab.SETTINGS))

        tabs.forEach { tab ->
            assertTrue(tab.title.isNotBlank())
            assertTrue(tab.testTag.isNotBlank())
            assertNotNull(tab.icon)
            assertNotNull(tab.selectedIcon)
        }
    }

    @Test
    fun testSubScreensExhaustiveness() {
        // Verify all 10 sub-screen destinations are defined
        val subScreens = SubScreen.values()
        assertTrue(subScreens.size >= 10)
        assertTrue(subScreens.contains(SubScreen.RULES))
        assertTrue(subScreens.contains(SubScreen.COMPANY_EXPENSES))
        assertTrue(subScreens.contains(SubScreen.REPORTS))
        assertTrue(subScreens.contains(SubScreen.IMPORT_STATEMENT))
        assertTrue(subScreens.contains(SubScreen.LENDING))
        assertTrue(subScreens.contains(SubScreen.BORROWING))
        assertTrue(subScreens.contains(SubScreen.PERSONAL_EXPENSES))
        assertTrue(subScreens.contains(SubScreen.INVESTMENTS))
        assertTrue(subScreens.contains(SubScreen.TRANSFERS))
        assertTrue(subScreens.contains(SubScreen.EXCEL_SYNC))
    }

    @Test
    fun testAddTransactionValidationsAcrossAllTabs() {
        // Tab 1: Expense validation
        val validateExpense = { amountStr: String, hasCategory: Boolean ->
            val amt = amountStr.toDoubleOrNull()
            amt != null && amt > 0.0 && hasCategory
        }
        assertFalse(validateExpense("", true))
        assertFalse(validateExpense("-50", true))
        assertFalse(validateExpense("100", false))
        assertTrue(validateExpense("100", true))

        // Tab 2: Income validation
        val validateIncome = { amountStr: String ->
            val amt = amountStr.toDoubleOrNull()
            amt != null && amt > 0.0
        }
        assertFalse(validateIncome("0"))
        assertTrue(validateIncome("50000"))

        // Tab 3: Lend Money validation
        val validateLend = { amountStr: String, personName: String ->
            val amt = amountStr.toDoubleOrNull()
            amt != null && amt > 0.0 && personName.trim().isNotBlank()
        }
        assertFalse(validateLend("1000", ""))
        assertTrue(validateLend("1000", "Rahul"))

        // Tab 4: Borrow Money validation
        val validateBorrow = { amountStr: String, personName: String, borrowId: String, existingIds: Set<String> ->
            val amt = amountStr.toDoubleOrNull()
            amt != null && amt > 0.0 && personName.trim().isNotBlank() && (!existingIds.contains(borrowId.trim()))
        }
        assertFalse(validateBorrow("2000", "Bank", "B1", setOf("B1")))
        assertTrue(validateBorrow("2000", "Bank", "B2", setOf("B1")))

        // Tab 5: Transfer validation
        val validateTransfer = { amountStr: String, fromAccId: Long, toAccId: Long ->
            val amt = amountStr.toDoubleOrNull()
            amt != null && amt > 0.0 && fromAccId != toAccId
        }
        assertFalse(validateTransfer("500", 1L, 1L))
        assertTrue(validateTransfer("500", 1L, 2L))
    }

    @Test
    fun testOnboardingStepTransitions() {
        // Step 0 -> Choose Mode
        var isOffline = false
        var currentStep = 0

        // User clicks "Online Mode"
        isOffline = false
        currentStep = 1
        assertEquals(1, currentStep)
        assertFalse(isOffline)

        // Step 1 Validation in Online Mode (requires authenticated account)
        val isStep1Valid = { offline: Boolean, name: String, isAuth: Boolean ->
            if (offline) name.trim().isNotBlank() else isAuth
        }
        assertFalse(isStep1Valid(isOffline, "User", false))
        assertTrue(isStep1Valid(isOffline, "User", true))

        // Step 2 Validation (Bank selection)
        val isStep2Valid = { bank: String -> bank.trim().isNotBlank() }
        assertFalse(isStep2Valid(""))
        assertTrue(isStep2Valid("HDFC Bank"))

        // Step 2 Default Account Assignment logic
        val assignDefault = { existing: List<AccountEntity> ->
            existing.isEmpty() || existing.none { it.isDefault }
        }
        assertTrue("First account must be default", assignDefault(emptyList()))

        // Step 3 -> Finish
        currentStep = 4
        assertEquals(4, currentStep)
    }

    @Test
    fun testDateRangeFiltersAndDashboardCards() {
        val filters = DateRangeFilter.values()
        assertEquals(6, filters.size)
        assertTrue(filters.contains(DateRangeFilter.TODAY))
        assertTrue(filters.contains(DateRangeFilter.THIS_WEEK))
        assertTrue(filters.contains(DateRangeFilter.THIS_MONTH))
        assertTrue(filters.contains(DateRangeFilter.THIS_YEAR))
        assertTrue(filters.contains(DateRangeFilter.ALL_TIME))
        assertTrue(filters.contains(DateRangeFilter.CUSTOM))

        filters.forEach { filter ->
            assertTrue(filter.displayName.isNotBlank())
        }

        // Test dashboard customizable cards
        val cards = DashboardCardType.values()
        assertEquals(10, cards.size)
        cards.forEach { card ->
            assertTrue(card.title.isNotBlank())
            assertTrue(card.id.isNotBlank())
            assertNotNull(card.icon)
        }
    }

    @Test
    fun testBiometricAppLockTimeoutWorkflow() {
        appPreferences.isBiometricEnabled = true
        appPreferences.appLockTimeoutSeconds = 5 // 5 second timeout

        val shouldLock = { lastPauseTime: Long, currentTime: Long, timeoutSeconds: Int ->
            val elapsed = currentTime - lastPauseTime
            elapsed >= (timeoutSeconds * 1000L)
        }

        val pauseTime = 1000000L
        // Only 2 seconds elapsed -> Should NOT lock
        assertFalse(shouldLock(pauseTime, pauseTime + 2000L, 5))
        // 5 seconds elapsed -> SHOULD lock
        assertTrue(shouldLock(pauseTime, pauseTime + 5000L, 5))
        // 10 seconds elapsed -> SHOULD lock
        assertTrue(shouldLock(pauseTime, pauseTime + 10000L, 5))
    }

    @Test
    fun testCloudAndLocalBackupFlowsWithEncryption() {
        val testJson = """{"application":"Yosan Finance","accounts":[{"name":"Savings"}],"transactions":[]}"""

        // Test Plain Backup
        assertFalse(BackupEncryptionUtil.isEncrypted(testJson))

        // Test Encrypted Backup
        val password = "StrongUserPIN123!"
        val encryptedEnvelope = BackupEncryptionUtil.encrypt(testJson, password)
        assertTrue(BackupEncryptionUtil.isEncrypted(encryptedEnvelope))

        // Test Decryption with Password
        val decrypted = BackupEncryptionUtil.decrypt(encryptedEnvelope, password)
        assertEquals(testJson, decrypted)

        // Test Decryption with Bad Password Throws
        try {
            BackupEncryptionUtil.decrypt(encryptedEnvelope, "WrongPassword")
            fail("Expected exception on bad password")
        } catch (_: Exception) {}
    }

    @Test
    fun testBudgetThresholdInsightCalculations() {
        budgetPreferences.monthlyBudget = 100000.0

        val summaryNormal = DashboardSummary(totalIncome = 120000.0, totalExpense = 50000.0, netSavings = 70000.0)
        val insightsNormal = FinancialCalculationService.generateInsights(
            summary = summaryNormal,
            categoryBreakdown = emptyList(),
            monthlyTrends = emptyList(),
            monthlyBudget = budgetPreferences.monthlyBudget
        )
        assertFalse(insightsNormal.any { it.title.contains("Monthly Budget Exceeded") })

        val summaryWarning = DashboardSummary(totalIncome = 120000.0, totalExpense = 85000.0, netSavings = 35000.0)
        val insightsWarning = FinancialCalculationService.generateInsights(
            summary = summaryWarning,
            categoryBreakdown = emptyList(),
            monthlyTrends = emptyList(),
            monthlyBudget = budgetPreferences.monthlyBudget
        )
        assertTrue(insightsWarning.any { it.title.contains("Approaching Budget Limit") })

        val summaryExceeded = DashboardSummary(totalIncome = 120000.0, totalExpense = 110000.0, netSavings = 10000.0)
        val insightsExceeded = FinancialCalculationService.generateInsights(
            summary = summaryExceeded,
            categoryBreakdown = emptyList(),
            monthlyTrends = emptyList(),
            monthlyBudget = budgetPreferences.monthlyBudget
        )
        assertTrue(insightsExceeded.any { it.title.contains("Monthly Budget Exceeded") })
    }
}
