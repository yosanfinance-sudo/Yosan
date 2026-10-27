package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseInitializer
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.utils.BackupService
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
class MultiUserDatabaseIsolationTest {

    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeAllDatabases()
    }

    @After
    fun tearDown() {
        AppDatabase.closeAllDatabases()
    }

    @Test
    fun testUserDatabaseFileNamingSanitization() {
        val aliceFile = AppDatabase.getDatabaseFileName(context, "alice.smith@gmail.com")
        val bobFile = AppDatabase.getDatabaseFileName(context, "BOB_123@yahoo.co.uk")
        val guestFile = AppDatabase.getDatabaseFileName(context, "guest")
        val offlineFile = AppDatabase.getDatabaseFileName(context, "offline")

        assertEquals("finance_db_user_alice_smith_at_gmail_com.db", aliceFile)
        assertEquals("finance_db_user_bob_123_at_yahoo_co_uk.db", bobFile)
        assertTrue("Guest and offline identities map to guest database", guestFile.contains("guest") || guestFile.contains("finance_manager_db"))
        assertEquals(guestFile, offlineFile)

        assertNotEquals("Alice and Bob must have distinct database files", aliceFile, bobFile)
        assertNotEquals("Alice and Guest must have distinct database files", aliceFile, guestFile)
    }

    @Test
    fun testDualUserCompleteDataIsolation() = runBlocking {
        val userAEmail = "alice@example.com"
        val userBEmail = "bob@example.com"

        // 1. User A logs in and creates records
        val dbA = AppDatabase.getDatabaseForUser(context, scope, userAEmail)
        val accIdA = dbA.accountDao().insertAccount(
            AccountEntity(
                accountName = "Alice HDFC Savings",
                bankName = "HDFC Bank",
                accountNumberMasked = "XX1234",
                currentBalance = 50000.0
            )
        )
        dbA.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = accIdA,
                transactionDate = "2026-10-25",
                description = "Alice Salary",
                amount = 50000.0,
                creditAmount = 50000.0,
                transactionType = TransactionType.INCOME
            )
        )

        val txsA = dbA.transactionDao().getAllTransactionsList()
        val accsA = dbA.accountDao().getAllAccountsList()
        assertEquals(1, txsA.size)
        assertEquals("Alice Salary", txsA[0].description)
        assertEquals(1, accsA.size)
        assertEquals("Alice HDFC Savings", accsA[0].accountName)

        // 2. User A logs out, and User B logs in
        val dbB = AppDatabase.getDatabaseForUser(context, scope, userBEmail)

        // 3. Verify User B sees 0 of Alice's accounts and transactions
        val txsBInitial = dbB.transactionDao().getAllTransactionsList()
        val accsBInitial = dbB.accountDao().getAllAccountsList()
        assertEquals("User B must see ZERO of Alice's transactions", 0, txsBInitial.size)
        assertEquals("User B must see ZERO of Alice's accounts", 0, accsBInitial.size)

        // 4. User B adds their own records
        val accIdB = dbB.accountDao().insertAccount(
            AccountEntity(
                accountName = "Bob SBI Current",
                bankName = "State Bank of India",
                accountNumberMasked = "XX9876",
                currentBalance = 15000.0
            )
        )
        dbB.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = accIdB,
                transactionDate = "2026-10-25",
                description = "Bob Office Coffee",
                amount = 250.0,
                debitAmount = 250.0,
                transactionType = TransactionType.EXPENSE
            )
        )

        val txsBAfter = dbB.transactionDao().getAllTransactionsList()
        assertEquals(1, txsBAfter.size)
        assertEquals("Bob Office Coffee", txsBAfter[0].description)

        // 5. User B logs out, and User A returns
        val dbAReturn = AppDatabase.getDatabaseForUser(context, scope, userAEmail)
        val txsAReturn = dbAReturn.transactionDao().getAllTransactionsList()
        val accsAReturn = dbAReturn.accountDao().getAllAccountsList()

        assertEquals("Alice must have her 1 transaction intact", 1, txsAReturn.size)
        assertEquals("Alice Salary", txsAReturn[0].description)
        assertEquals(1, accsAReturn.size)
        assertEquals("Alice HDFC Savings", accsAReturn[0].accountName)
        assertFalse("Alice must NEVER see Bob's transactions", txsAReturn.any { it.description.contains("Bob") })
    }

    @Test
    fun testOfflineGuestToGoogleUserMigration() = runBlocking {
        // 1. Guest creates records in offline database
        val guestDb = AppDatabase.getDatabaseForUser(context, scope, "guest")
        val guestAccId = guestDb.accountDao().insertAccount(
            AccountEntity(
                accountName = "Offline Cash Wallet",
                bankName = "Cash",
                accountNumberMasked = "CASH",
                currentBalance = 5000.0
            )
        )
        guestDb.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = guestAccId,
                transactionDate = "2026-10-25",
                description = "Offline Grocery",
                amount = 1200.0,
                debitAmount = 1200.0,
                transactionType = TransactionType.EXPENSE
            )
        )

        val initialGuestTxs = guestDb.transactionDao().getAllTransactionsList()
        assertEquals(1, initialGuestTxs.size)

        // 2. Export guest backup JSON
        val guestBackupJson = BackupService.generateBackupJsonObject(guestDb).toString()
        assertTrue(guestBackupJson.contains("Offline Grocery"))

        // 3. User signs into Google Account
        val googleUserEmail = "migrated.user@gmail.com"
        val userDb = AppDatabase.getDatabaseForUser(context, scope, googleUserEmail)

        // 4. Restore guest backup into userDb
        val restoreResult = BackupService.restoreBackupJson(guestBackupJson, userDb, clearExisting = false)
        assertTrue(restoreResult.success)

        // 5. Purge guest DB for future guest sessions
        DatabaseInitializer.purgeLegacyDemoData(guestDb)

        // 6. Verify userDb has the migrated data
        val userTxs = userDb.transactionDao().getAllTransactionsList()
        val userAccs = userDb.accountDao().getAllAccountsList()
        assertEquals(1, userTxs.size)
        assertEquals("Offline Grocery", userTxs[0].description)
        assertEquals(1, userAccs.size)
        assertEquals("Offline Cash Wallet", userAccs[0].accountName)

        // 7. Verify guestDb is clean and empty of financial data
        val cleanGuestTxs = guestDb.transactionDao().getAllTransactionsList()
        val cleanGuestAccs = guestDb.accountDao().getAllAccountsList()
        assertEquals("Guest DB transactions must be purged after migration", 0, cleanGuestTxs.size)
        assertEquals("Guest DB accounts must be purged after migration", 0, cleanGuestAccs.size)
    }
}
