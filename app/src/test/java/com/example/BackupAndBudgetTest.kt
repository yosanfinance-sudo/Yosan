package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.ApiClient
import com.example.features.reports.*
import com.example.features.updater.UpdateManager
import com.example.utils.BackupEncryptionUtil
import com.example.utils.BudgetPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndBudgetTest {

    @Test
    fun testBackupEncryptionAndDecryption() {
        val samplePlainJson = """{"application":"Yosan Finance","accounts":[{"name":"Main Bank","balance":25000.0}]}"""
        val passphrase = "my-secure-password-123"

        // 1. Encrypt
        val encryptedJson = BackupEncryptionUtil.encrypt(samplePlainJson, passphrase)
        assertTrue(BackupEncryptionUtil.isEncrypted(encryptedJson))
        assertFalse(encryptedJson.contains("Main Bank"))

        // 2. Decrypt with correct passphrase
        val decryptedJson = BackupEncryptionUtil.decrypt(encryptedJson, passphrase)
        assertEquals(samplePlainJson, decryptedJson)
    }

    @Test
    fun testDecryptionFailsWithWrongPassphrase() {
        val samplePlainJson = """{"application":"Yosan Finance","secret":"sensitive_banking_data"}"""
        val encryptedJson = BackupEncryptionUtil.encrypt(samplePlainJson, "correct_password")

        try {
            BackupEncryptionUtil.decrypt(encryptedJson, "wrong_password")
            fail("Expected exception when decrypting with incorrect password")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun testTransparentHandlingOfPlainJson() {
        val plainJson = """{"application":"Yosan Finance","version":1}"""
        assertFalse(BackupEncryptionUtil.isEncrypted(plainJson))
        // Calling decrypt on plain JSON returns it intact
        val result = BackupEncryptionUtil.decrypt(plainJson, null)
        assertEquals(plainJson, result)
    }

    @Test
    fun testBudgetPreferencesAndEvaluation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val budgetPrefs = BudgetPreferences(context)

        budgetPrefs.monthlyBudget = 50000.0
        assertEquals(50000.0, budgetPrefs.monthlyBudget, 0.01)

        budgetPrefs.setCategoryBudget("Dining", 6000.0)
        assertEquals(6000.0, budgetPrefs.getCategoryBudget("Dining"), 0.01)

        val evalNormal = budgetPrefs.evaluateCategoryBudget("Dining", 3000.0)
        assertNotNull(evalNormal)
        assertFalse(evalNormal!!.isOverBudget)
        assertFalse(evalNormal.isNearBudget)
        assertEquals(50.0, evalNormal.percentageUsed, 0.01)

        val evalNear = budgetPrefs.evaluateCategoryBudget("Dining", 5000.0)
        assertNotNull(evalNear)
        assertFalse(evalNear!!.isOverBudget)
        assertTrue(evalNear.isNearBudget)

        val evalOver = budgetPrefs.evaluateCategoryBudget("Dining", 7500.0)
        assertNotNull(evalOver)
        assertTrue(evalOver!!.isOverBudget)
    }

    @Test
    fun testFinancialInsightsWithBudgetAlerts() {
        val summary = DashboardSummary(
            totalIncome = 80000.0,
            totalExpense = 65000.0,
            netSavings = 15000.0,
            totalOutstanding = 0.0,
            companyExpense = 0.0
        )
        val breakdown = listOf(
            CategoryExpenseItem(categoryName = "Food & Dining", amount = 25000.0, percentage = 38.46f, colorHex = "#EF4444", count = 10)
        )

        // Case 1: Total expense (65,000) exceeds monthly budget (60,000)
        val insightsOver = FinancialCalculationService.generateInsights(
            summary = summary,
            categoryBreakdown = breakdown,
            monthlyTrends = emptyList(),
            monthlyBudget = 60000.0,
            categoryBudgets = mapOf("food & dining" to 20000.0)
        )

        assertTrue(insightsOver.any { it.title.contains("Monthly Budget Exceeded") })
        assertTrue(insightsOver.any { it.title.contains("Over Budget: Food & Dining") })

        // Case 2: Total expense within budget
        val insightsUnder = FinancialCalculationService.generateInsights(
            summary = summary,
            categoryBreakdown = breakdown,
            monthlyTrends = emptyList(),
            monthlyBudget = 100000.0,
            categoryBudgets = mapOf("food & dining" to 30000.0)
        )
        assertFalse(insightsUnder.any { it.title.contains("Monthly Budget Exceeded") })
        assertFalse(insightsUnder.any { it.title.contains("Over Budget") })
    }

    @Test
    fun testCustomBaseUrlAllowsLocalhostAndLanIps() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Setting local IP for developer testing
        ApiClient.setCustomBaseUrl(context, "http://192.168.1.15:3000")
        assertEquals("http://192.168.1.15:3000/", ApiClient.getBaseUrl(context))

        // 2. Setting emulator IP
        ApiClient.setCustomBaseUrl(context, "http://10.0.2.2:3000")
        assertEquals("http://10.0.2.2:3000/", ApiClient.getBaseUrl(context))

        // 3. Resetting back to default
        ApiClient.setCustomBaseUrl(context, null)
        assertFalse(ApiClient.getBaseUrl(context).contains("10.0.2.2"))
    }

    @Test
    fun testUpdateManagerSemanticVersionComparison() {
        // Higher minor/patch version
        assertTrue(UpdateManager.isRemoteVersionNewer("v1.2.0", "v1.1.0"))
        assertTrue(UpdateManager.isRemoteVersionNewer("v1.0.5", "v1.0.4"))
        assertTrue(UpdateManager.isRemoteVersionNewer("v2.0.0", "v1.9.9"))

        // Equal version
        assertFalse(UpdateManager.isRemoteVersionNewer("v1.0.0", "v1.0.0"))

        // Older remote version
        assertFalse(UpdateManager.isRemoteVersionNewer("v1.0.0", "v1.0.1"))
    }
}
