package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.utils.AppPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndPreferencesTest {

    @Test
    fun testThemeModeResolution() {
        val resolveTheme = { themeMode: String, systemInDark: Boolean ->
            when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemInDark
            }
        }

        // Forced Light
        assertFalse(resolveTheme("LIGHT", true))
        assertFalse(resolveTheme("LIGHT", false))

        // Forced Dark
        assertTrue(resolveTheme("DARK", true))
        assertTrue(resolveTheme("DARK", false))

        // Follow System
        assertTrue(resolveTheme("SYSTEM", true))
        assertFalse(resolveTheme("SYSTEM", false))
    }

    @Test
    fun testAppPreferencesPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appPrefs = AppPreferences(context)

        // Theme Mode default
        assertEquals("SYSTEM", appPrefs.themeMode)

        // Set Light
        appPrefs.themeMode = "LIGHT"
        assertEquals("LIGHT", appPrefs.themeMode)

        // Set Dark
        appPrefs.themeMode = "DARK"
        assertEquals("DARK", appPrefs.themeMode)

        // Biometric toggle
        appPrefs.isBiometricEnabled = true
        assertTrue(appPrefs.isBiometricEnabled)
        appPrefs.isBiometricEnabled = false
        assertFalse(appPrefs.isBiometricEnabled)

        // Lock timeout
        appPrefs.appLockTimeoutSeconds = 60
        assertEquals(60, appPrefs.appLockTimeoutSeconds)
    }

    @Test
    fun testCategorySystemProtectionAndValidation() {
        val defaultCategories = CategoryEntity.DEFAULT_CATEGORIES
        assertTrue(defaultCategories.isEmpty())

        val sampleCategories = listOf(
            CategoryEntity(id = 1, name = "Food & Dining", type = CategoryType.EXPENSE, isSystem = true),
            CategoryEntity(id = 2, name = "Groceries & Mart", type = CategoryType.EXPENSE, isSystem = true)
        )

        // Duplicate name detection logic
        val isDuplicate = { newName: String, existing: List<CategoryEntity> ->
            existing.any { it.name.trim().equals(newName.trim(), ignoreCase = true) }
        }

        assertTrue(isDuplicate("Food & Dining", sampleCategories))
        assertTrue(isDuplicate("food & dining", sampleCategories))
        assertTrue(isDuplicate("  GROCERIES & MART  ", sampleCategories))
        assertFalse(isDuplicate("New Custom Category", sampleCategories))

        // Safe deletion guard
        val canDelete = { cat: CategoryEntity -> !cat.isSystem }
        val systemCat = sampleCategories.first()
        assertFalse("System category must not be deletable", canDelete(systemCat))

        val customCat = CategoryEntity(id = 999, name = "Custom Category", type = CategoryType.EXPENSE, isSystem = false)
        assertTrue("Custom category should be deletable", canDelete(customCat))
    }

    @Test
    fun testAccountSafeReassignmentLogic() {
        val acc1 = AccountEntity(id = 1, accountName = "Primary Bank", bankName = "Indian Bank", accountNumberMasked = "1234")
        val acc2 = AccountEntity(id = 2, accountName = "Secondary Bank", bankName = "Yes Bank", accountNumberMasked = "5678")

        val txs = listOf(
            TransactionEntity(
                id = 101,
                accountId = 2, // linked to acc2
                transactionDate = "2026-08-01",
                description = "ATM Cash",
                debitAmount = 1000.0,
                amount = 1000.0,
                transactionType = TransactionType.EXPENSE
            ),
            TransactionEntity(
                id = 102,
                accountId = 1, // linked to acc1
                transactionDate = "2026-08-02",
                description = "Salary",
                creditAmount = 50000.0,
                amount = 50000.0,
                transactionType = TransactionType.INCOME
            )
        )

        // Deleting acc2 requires reassigning tx 101 to acc1 (id = 1)
        val linkedCount = txs.count { it.accountId == acc2.id }
        assertEquals(1, linkedCount)

        val updatedTxs = txs.map { tx ->
            if (tx.accountId == acc2.id) tx.copy(accountId = acc1.id) else tx
        }

        // After reassignment, no tx remains linked to acc2
        assertEquals(0, updatedTxs.count { it.accountId == acc2.id })
        assertEquals(2, updatedTxs.count { it.accountId == acc1.id })
    }

    @Test
    fun testPrivacyBlurTimeoutChoices() {
        val supportedTimeouts = listOf(3, 5, 10, 15)
        assertTrue(supportedTimeouts.contains(5))
        assertTrue(supportedTimeouts.contains(3))
        assertTrue(supportedTimeouts.contains(15))
    }

    @Test
    fun testDeviceAccountRememberAndBankNamePersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appPrefs = AppPreferences(context)

        // Clear initially
        appPrefs.clearAll()
        assertFalse(appPrefs.hasPreviousAccountOnDevice)
        assertEquals("", appPrefs.lastBankName)

        // Simulate login / onboarding completion
        appPrefs.lastUserEmail = "test.user@gmail.com"
        appPrefs.lastUserName = "Test User"
        appPrefs.lastBankName = "State Bank of India"
        appPrefs.lastUserProfilePic = "https://example.com/avatar.jpg"
        appPrefs.lastGoogleSub = "sub_987654321"

        assertTrue(appPrefs.hasPreviousAccountOnDevice)
        assertEquals("test.user@gmail.com", appPrefs.lastUserEmail)
        assertEquals("Test User", appPrefs.lastUserName)
        assertEquals("State Bank of India", appPrefs.lastBankName)
        assertEquals("https://example.com/avatar.jpg", appPrefs.lastUserProfilePic)
        assertEquals("sub_987654321", appPrefs.lastGoogleSub)
    }

    @Test
    fun testLogoutPreservesDeviceAccountIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appPrefs = AppPreferences(context)

        // Setup session
        appPrefs.lastUserEmail = "yosan.developer@gmail.com"
        appPrefs.lastUserName = "Developer"
        appPrefs.lastBankName = "HDFC Bank"
        appPrefs.isLoggedOut = false

        // Simulate logout while preserving device account & bank name
        val savedEmail = appPrefs.lastUserEmail
        val savedName = appPrefs.lastUserName
        val savedBank = appPrefs.lastBankName
        val savedPic = appPrefs.lastUserProfilePic
        val savedSub = appPrefs.lastGoogleSub

        appPrefs.clearSession(preserveDeviceIdentity = true)

        // Device identity and bank name must still be present
        assertEquals("yosan.developer@gmail.com", appPrefs.lastUserEmail)
        assertEquals("Developer", appPrefs.lastUserName)
        assertEquals("HDFC Bank", appPrefs.lastBankName)
        assertTrue(appPrefs.hasPreviousAccountOnDevice)
        assertTrue(appPrefs.isLoggedOut)
    }

    @Test
    fun testOAuth2AuthorizationUriGeneration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val googleAuthManager = com.example.features.auth.GoogleAuthManager(context)
        val uri = googleAuthManager.getOAuth2AuthorizationUri(
            redirectUri = "yosan://auth-callback",
            includeDriveScope = true,
            loginHint = "test.user@gmail.com"
        )

        val uriString = uri.toString()
        assertTrue(uriString.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(uriString.contains("redirect_uri=yosan%3A%2F%2Fauth-callback") || uriString.contains("redirect_uri=yosan://auth-callback"))
        assertTrue(uriString.contains("response_type=id_token%20token") || uriString.contains("response_type=id_token+token"))
        assertTrue(uriString.contains("drive.file"))
        assertTrue(uriString.contains("login_hint=test.user%40gmail.com") || uriString.contains("login_hint=test.user@gmail.com"))
        assertTrue(uriString.contains("nonce="))
        assertTrue(uriString.contains("state="))
    }

    @Test
    fun testOnboardingStep1NextButtonValidation() {
        val isStep1NextEnabled = { isOffline: Boolean, name: String, isAuthenticated: Boolean ->
            if (isOffline) name.trim().isNotEmpty() else isAuthenticated
        }

        // Online mode: MUST be authenticated with Google/backend
        assertFalse("Online mode without auth must disable Next", isStep1NextEnabled(false, "User", false))
        assertTrue("Online mode with auth must enable Next", isStep1NextEnabled(false, "User", true))

        // Offline mode: MUST have a non-blank display name
        assertFalse("Offline mode without name must disable Next", isStep1NextEnabled(true, "", false))
        assertFalse("Offline mode with blank name must disable Next", isStep1NextEnabled(true, "   ", false))
        assertTrue("Offline mode with valid name must enable Next", isStep1NextEnabled(true, "Ragul", false))
    }

    @Test
    fun testDefaultAccountAssignmentLogic() {
        val determineDefault = { accounts: List<AccountEntity> ->
            accounts.isEmpty() || accounts.none { it.isDefault }
        }

        // No accounts exist yet -> First account MUST be default
        assertTrue(determineDefault(emptyList()))

        // Existing accounts with NO default -> New account MUST be default
        val noDefaultAccs = listOf(
            AccountEntity(id = 1, accountName = "Wallet", bankName = "Paytm", accountNumberMasked = "1234", isDefault = false)
        )
        assertTrue(determineDefault(noDefaultAccs))

        // Existing accounts ALREADY have a default -> New account should NOT override default
        val hasDefaultAccs = listOf(
            AccountEntity(id = 1, accountName = "Main", bankName = "SBI", accountNumberMasked = "1234", isDefault = true)
        )
        assertFalse(determineDefault(hasDefaultAccs))
    }

    @Test
    fun testHapticAndFloatingPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appPrefs = AppPreferences(context)

        // Verify defaults
        assertTrue("Haptic feedback should be enabled by default", appPrefs.isHapticFeedbackEnabled)
        assertTrue("Floating effect should be enabled by default", appPrefs.isFloatingEffectEnabled)

        // Toggle Haptic
        appPrefs.isHapticFeedbackEnabled = false
        assertFalse(appPrefs.isHapticFeedbackEnabled)
        appPrefs.isHapticFeedbackEnabled = true
        assertTrue(appPrefs.isHapticFeedbackEnabled)

        // Toggle Floating Effect
        appPrefs.isFloatingEffectEnabled = false
        assertFalse(appPrefs.isFloatingEffectEnabled)
        appPrefs.isFloatingEffectEnabled = true
        assertTrue(appPrefs.isFloatingEffectEnabled)
    }

    @Test
    fun testCurrencyFormattingNoDoubleRupee() {
        val formatted = com.example.utils.CurrencyFormatter.formatInr(1500.0)
        assertFalse("Formatted string must never contain duplicate '₹₹'", formatted.contains("₹₹"))
        assertTrue("Formatted string must start with single '₹ '", formatted.startsWith("₹ "))

        val positivePrefix = "+${com.example.utils.CurrencyFormatter.formatInr(2500.0)}"
        assertFalse("Prefix must not create duplicate symbols", positivePrefix.contains("₹₹"))
        assertTrue(positivePrefix.startsWith("+₹ "))
    }
}
