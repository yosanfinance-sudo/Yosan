package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.utils.AppPreferences
import com.example.utils.RememberedAccount
import com.example.utils.SecureTokenManager
import com.example.utils.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiUserSessionTest {

    private lateinit var context: Context
    private lateinit var tokenManager: SecureTokenManager
    private lateinit var appPreferences: AppPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tokenManager = SecureTokenManager(context)
        tokenManager.clearCredentials()
        appPreferences = AppPreferences(context)
        appPreferences.clearAll()
        AppDatabase.closeAllDatabases()
    }

    @After
    fun tearDown() {
        if (::tokenManager.isInitialized) {
            tokenManager.clearCredentials()
        }
        if (::appPreferences.isInitialized) {
            appPreferences.clearAll()
        }
        AppDatabase.closeAllDatabases()
    }

    @Test
    fun testDynamicSessionTokenGeneration() {
        val email1 = "user1@example.com"
        val email2 = "user2@example.com"

        val token1a = SecureTokenManager.generateSessionToken(email1)
        val token1b = SecureTokenManager.generateSessionToken(email1)
        val token2 = SecureTokenManager.generateSessionToken(email2)

        assertTrue("Session token must contain user identifier", token1a.contains("user1_at_example_com"))
        assertTrue("Session token must contain user identifier", token2.contains("user2_at_example_com"))
        assertNotEquals("Each generated session token must be unique (not hardcoded)", token1a, token1b)
        assertNotEquals("Session tokens between users must be unique", token1a, token2)
        assertTrue("Session token must follow secure format prefix", token1a.startsWith("yosan_sess_"))
    }

    @Test
    fun testMultiUserSessionStorageAndSwitching() {
        val userA = "alice@example.com"
        val userB = "bob@example.com"

        val tokenA = SecureTokenManager.generateSessionToken(userA)
        val tokenB = SecureTokenManager.generateSessionToken(userB)

        // 1. Save Alice session
        tokenManager.saveTokens("access_token_alice", "refresh_token_alice", 3600L, email = userA)
        tokenManager.saveUserSession(
            userId = 101L,
            googleSub = "google_sub_alice",
            email = userA,
            name = "Alice Smith",
            profilePicture = "https://example.com/alice.jpg",
            emailVerified = true,
            sessionToken = tokenA,
            accessToken = "access_token_alice",
            refreshToken = "refresh_token_alice",
            expiresInSeconds = 3600L
        )

        assertEquals(userA, tokenManager.getUserEmail())
        assertEquals("Alice Smith", tokenManager.getUserName())
        assertEquals("access_token_alice", tokenManager.getAccessToken())
        assertEquals(tokenA, tokenManager.getSessionToken())

        // 2. Save Bob session (simulating second user adding account on same mobile)
        tokenManager.saveTokens("access_token_bob", "refresh_token_bob", 7200L, email = userB)
        tokenManager.saveUserSession(
            userId = 102L,
            googleSub = "google_sub_bob",
            email = userB,
            name = "Bob Jones",
            profilePicture = "https://example.com/bob.jpg",
            emailVerified = true,
            sessionToken = tokenB,
            accessToken = "access_token_bob",
            refreshToken = "refresh_token_bob",
            expiresInSeconds = 7200L
        )

        assertEquals(userB, tokenManager.getUserEmail())
        assertEquals("Bob Jones", tokenManager.getUserName())
        assertEquals("access_token_bob", tokenManager.getAccessToken())
        assertEquals(tokenB, tokenManager.getSessionToken())

        // 3. Verify both sessions are persisted in all sessions index
        val allSessions = tokenManager.getAllUserSessions()
        assertEquals(2, allSessions.size)
        assertTrue(allSessions.any { it.email == userA && it.sessionToken == tokenA })
        assertTrue(allSessions.any { it.email == userB && it.sessionToken == tokenB })

        // 4. Switch back to Alice
        val switchSuccess = tokenManager.switchUserSession(userA)
        assertTrue("Switching to Alice session must succeed", switchSuccess)
        assertEquals(userA, tokenManager.getUserEmail())
        assertEquals("Alice Smith", tokenManager.getUserName())
        assertEquals("access_token_alice", tokenManager.getAccessToken())
        assertEquals(tokenA, tokenManager.getSessionToken())

        // 5. Switch back to Bob
        val switchBobSuccess = tokenManager.switchUserSession(userB)
        assertTrue("Switching to Bob session must succeed", switchBobSuccess)
        assertEquals(userB, tokenManager.getUserEmail())
        assertEquals("Bob Jones", tokenManager.getUserName())
        assertEquals("access_token_bob", tokenManager.getAccessToken())
        assertEquals(tokenB, tokenManager.getSessionToken())
    }

    @Test
    fun testDataIsolationBetweenMultipleAccounts() = runBlocking {
        val userA = "alice@example.com"
        val userB = "bob@example.com"

        // Step 1: User A logs in and adds financial records
        AppDatabase.setActiveUserKey(userA)
        val dbA = AppDatabase.getDatabaseForUser(context, scope, userA)
        val accIdA = dbA.accountDao().insertAccount(
            AccountEntity(
                accountName = "Alice Private HDFC",
                bankName = "HDFC Bank",
                accountNumberMasked = "XX1234",
                currentBalance = 75000.0
            )
        )
        dbA.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = accIdA,
                transactionDate = "2026-10-27",
                description = "Confidential Salary Credit",
                amount = 75000.0,
                creditAmount = 75000.0,
                transactionType = TransactionType.INCOME
            )
        )

        assertEquals(1, dbA.accountDao().getAllAccountsList().size)
        assertEquals(1, dbA.transactionDao().getAllTransactionsList().size)

        // Step 2: User B adds their account on the same phone
        AppDatabase.setActiveUserKey(userB)
        val dbB = AppDatabase.getDatabaseForUser(context, scope, userB)

        // Step 3: Verify User B has ZERO of Alice's accounts and transactions
        val txsB = dbB.transactionDao().getAllTransactionsList()
        val accsB = dbB.accountDao().getAllAccountsList()
        assertEquals("User B must see ZERO of Alice's transactions", 0, txsB.size)
        assertEquals("User B must see ZERO of Alice's accounts", 0, accsB.size)

        // Step 4: User B adds their own records
        val accIdB = dbB.accountDao().insertAccount(
            AccountEntity(
                accountName = "Bob SBI Wallet",
                bankName = "SBI",
                accountNumberMasked = "XX9876",
                currentBalance = 12000.0
            )
        )
        dbB.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = accIdB,
                transactionDate = "2026-10-27",
                description = "Lunch at Bistro",
                amount = 450.0,
                debitAmount = 450.0,
                transactionType = TransactionType.EXPENSE
            )
        )

        assertEquals(1, dbB.accountDao().getAllAccountsList().size)
        assertEquals(1, dbB.transactionDao().getAllTransactionsList().size)

        // Step 5: Switch back to User A and verify zero contamination
        AppDatabase.setActiveUserKey(userA)
        val dbAReturn = AppDatabase.getDatabaseForUser(context, scope, userA)
        val txsAReturn = dbAReturn.transactionDao().getAllTransactionsList()
        val accsAReturn = dbAReturn.accountDao().getAllAccountsList()

        assertEquals("Alice must have her 1 transaction", 1, txsAReturn.size)
        assertEquals("Confidential Salary Credit", txsAReturn[0].description)
        assertFalse("Alice must NEVER see Bob's Lunch at Bistro transaction", txsAReturn.any { it.description.contains("Bistro") })
        assertEquals("Alice Private HDFC", accsAReturn[0].accountName)
    }

    @Test
    fun testRemoveUserAccountPreservesRemainingAccount() {
        val userA = "alice@example.com"
        val userB = "bob@example.com"

        val tokenA = SecureTokenManager.generateSessionToken(userA)
        val tokenB = SecureTokenManager.generateSessionToken(userB)

        tokenManager.saveTokens("token_alice", "refresh_alice", email = userA)
        tokenManager.saveUserSession(1L, "sub_a", userA, "Alice", "", true, tokenA, accessToken = "token_alice", refreshToken = "refresh_alice")

        tokenManager.saveTokens("token_bob", "refresh_bob", email = userB)
        tokenManager.saveUserSession(2L, "sub_b", userB, "Bob", "", true, tokenB, accessToken = "token_bob", refreshToken = "refresh_bob")

        appPreferences.saveRememberedAccount(RememberedAccount(email = userA, name = "Alice", sessionToken = tokenA))
        appPreferences.saveRememberedAccount(RememberedAccount(email = userB, name = "Bob", sessionToken = tokenB))

        assertEquals(2, tokenManager.getAllUserSessions().size)
        assertEquals(2, appPreferences.getRememberedAccounts().size)

        // Remove Alice
        tokenManager.removeUserSession(userA)
        appPreferences.removeRememberedAccount(userA)

        val remainingSessions = tokenManager.getAllUserSessions()
        val remainingAccounts = appPreferences.getRememberedAccounts()

        assertEquals(1, remainingSessions.size)
        assertEquals(userB, remainingSessions[0].email)
        assertEquals(1, remainingAccounts.size)
        assertEquals(userB, remainingAccounts[0].email)

        // Bob's session can still be switched to and used
        val switchSuccess = tokenManager.switchUserSession(userB)
        assertTrue(switchSuccess)
        assertEquals(userB, tokenManager.getUserEmail())
        assertEquals("token_bob", tokenManager.getAccessToken())
        assertEquals(tokenB, tokenManager.getSessionToken())
    }

    @Test
    fun testRemoveActiveUserDoesNotCauseRecursionCrash() {
        val userA = "alice@example.com"
        val tokenA = SecureTokenManager.generateSessionToken(userA)

        tokenManager.saveTokens("token_alice", "refresh_alice", email = userA)
        tokenManager.saveUserSession(1L, "sub_a", userA, "Alice", "", true, tokenA, accessToken = "token_alice", refreshToken = "refresh_alice")

        assertEquals(userA, tokenManager.getUserEmail())

        // Removing the active user must clean active preferences without infinite recursion or StackOverflow
        tokenManager.removeUserSession(userA)

        assertNull("Active user email must be cleared after removing active user", tokenManager.getUserEmail())
        assertNull("Active access token must be cleared", tokenManager.getAccessToken())
        assertNull("Active session token must be cleared", tokenManager.getSessionToken())
        assertTrue("Saved sessions must be empty", tokenManager.getAllUserSessions().isEmpty())
    }

    @Test
    fun testDriveTokenRetrievalWithDifferentDriveEmail() {
        val userA = "alice@workplace.com"
        val driveEmail = "alice.personal@gmail.com"
        val tokenA = SecureTokenManager.generateSessionToken(userA)

        tokenManager.saveTokens("token_alice", "refresh_alice", email = userA)
        tokenManager.saveUserSession(1L, "sub_a", userA, "Alice", "", true, tokenA, accessToken = "token_alice", refreshToken = "refresh_alice")
        tokenManager.saveDriveTokens(
            accessToken = "drive_tok_123",
            refreshToken = "drive_ref_456",
            expiresInSeconds = 3600L,
            driveEmail = driveEmail
        )

        assertEquals("drive_tok_123", tokenManager.getDriveAccessToken())
        assertEquals("drive_ref_456", tokenManager.getDriveRefreshToken())
        assertEquals(driveEmail, tokenManager.getDriveEmail())
        assertTrue(tokenManager.isDriveConnected())
    }

    @Test
    fun testPerUserLogoutAndOnboardingPreferences() {
        val userA = "alice@example.com"
        val userB = "bob@example.com"

        // Step 1: User A completes onboarding and logs in
        appPreferences.setOnboardingCompletedForUser(userA, true)
        appPreferences.setLoggedOutForUser(userA, false)

        // Step 2: User B is added as a new user
        appPreferences.setOnboardingCompletedForUser(userB, false)
        appPreferences.setLoggedOutForUser(userB, false)

        assertTrue("User A onboarding must be completed", appPreferences.isOnboardingCompletedForUser(userA))
        assertFalse("User B onboarding must NOT be marked completed before bank setup", appPreferences.isOnboardingCompletedForUser(userB))

        // Step 3: User A logs out
        appPreferences.setLoggedOutForUser(userA, true)
        assertTrue("User A must be logged out", appPreferences.isLoggedOutForUser(userA))
        assertFalse("User B must NOT be affected by User A's logout", appPreferences.isLoggedOutForUser(userB))
    }
}
