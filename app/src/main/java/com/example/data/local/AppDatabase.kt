package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        CategorizationRuleEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class,
        CompanyExpenseEntity::class,
        UserProfileEntity::class,
        DeletedTransactionEntity::class,
        SyncMetadataEntity::class,
        UndoHistoryEntity::class,
        PendingFeedbackEntity::class,
        StatementImportEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categorizationRuleDao(): CategorizationRuleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun loanRepaymentDao(): LoanRepaymentDao
    abstract fun companyExpenseDao(): CompanyExpenseDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun syncDao(): SyncDao
    abstract fun undoDao(): UndoDao
    abstract fun pendingFeedbackDao(): PendingFeedbackDao
    abstract fun statementImportDao(): StatementImportDao

    companion object {
        private val instances = java.util.concurrent.ConcurrentHashMap<String, AppDatabase>()
        @Volatile
        private var currentActiveUserKey: String = "guest"
        private val lock = Any()

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add syncId to transactions table
                db.execSQL("ALTER TABLE transactions ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE transactions SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")

                // Create deleted_transactions table
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_transactions (syncId TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER NOT NULL)")

                // Create sync_metadata table
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `undo_history` (
                        `actionId` TEXT NOT NULL PRIMARY KEY,
                        `actionType` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `affectedTransactionIds` TEXT NOT NULL,
                        `previousStateJson` TEXT NOT NULL,
                        `newStateJson` TEXT NOT NULL,
                        `relatedRuleId` INTEGER,
                        `ruleSnapshotJson` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN advanceId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_feedbacks` (
                        `syncId` TEXT NOT NULL PRIMARY KEY,
                        `category` TEXT NOT NULL,
                        `rating` INTEGER NOT NULL,
                        `subject` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `appVersion` TEXT NOT NULL,
                        `deviceModel` TEXT NOT NULL,
                        `androidVersion` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add claim and advance tracking columns to company_expenses
                db.execSQL("ALTER TABLE `company_expenses` ADD COLUMN `claimStatus` TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE `company_expenses` ADD COLUMN `submittedDate` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `company_expenses` ADD COLUMN `approvedAmount` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `company_expenses` ADD COLUMN `reimbursedAmount` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `company_expenses` ADD COLUMN `advanceId` TEXT DEFAULT NULL")
                db.execSQL("UPDATE `company_expenses` SET `claimStatus` = 'APPROVED', `approvedAmount` = `amount`, `reimbursedAmount` = `amount` WHERE `isReimbursed` = 1")

                // Add importId to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `importId` INTEGER DEFAULT NULL")

                // Create statement_imports table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `statement_imports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `importTimestamp` INTEGER NOT NULL,
                        `totalParsed` INTEGER NOT NULL,
                        `importedCount` INTEGER NOT NULL,
                        `duplicateCount` INTEGER NOT NULL,
                        `auditStatus` TEXT NOT NULL,
                        `openingBalance` REAL,
                        `closingBalance` REAL,
                        `totalDebits` REAL NOT NULL,
                        `totalCredits` REAL NOT NULL,
                        `discrepancyCount` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `storagePreference` TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `supabaseUid` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile_new` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `googleSub` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `profilePictureUrl` TEXT NOT NULL,
                        `emailVerified` INTEGER NOT NULL,
                        `currencySymbol` TEXT NOT NULL,
                        `isDarkMode` INTEGER NOT NULL,
                        `isBiometricEnabled` INTEGER NOT NULL,
                        `isPrivacyBlurEnabled` INTEGER NOT NULL,
                        `blurTimeoutSeconds` INTEGER NOT NULL,
                        `isOnboardingCompleted` INTEGER NOT NULL,
                        `dashboardCardsConfig` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastLoginAt` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `storagePreference` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `user_profile_new` (`id`, `googleSub`, `name`, `email`, `profilePictureUrl`, `emailVerified`, `currencySymbol`, `isDarkMode`, `isBiometricEnabled`, `isPrivacyBlurEnabled`, `blurTimeoutSeconds`, `isOnboardingCompleted`, `dashboardCardsConfig`, `createdAt`, `updatedAt`, `lastLoginAt`, `isActive`, `storagePreference`)
                    SELECT `id`, `googleSub`, `name`, `email`, `profilePictureUrl`, `emailVerified`, `currencySymbol`, `isDarkMode`, `isBiometricEnabled`, `isPrivacyBlurEnabled`, `blurTimeoutSeconds`, `isOnboardingCompleted`, `dashboardCardsConfig`, `createdAt`, `updatedAt`, `lastLoginAt`, `isActive`, `storagePreference` FROM `user_profile`
                """.trimIndent())
                db.execSQL("DROP TABLE `user_profile`")
                db.execSQL("ALTER TABLE `user_profile_new` RENAME TO `user_profile`")
            }
        }

        fun sanitizeUserKey(userKey: String?): String {
            if (userKey.isNullOrBlank()) return "guest"
            val clean = userKey.trim().lowercase()
            if (clean == "guest" || clean == "offline" || clean.startsWith("offline_") || clean == "unauthenticated") {
                return "guest"
            }
            val sanitized = clean.replace("@", "_at_").replace(".", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
            return if (sanitized.isNotBlank()) sanitized else "guest"
        }

        fun getDatabaseFileName(context: Context, userKey: String): String {
            val sanitized = sanitizeUserKey(userKey)
            return if (sanitized == "guest") {
                val legacyFile = context.applicationContext.getDatabasePath("finance_manager_db")
                if (legacyFile.exists()) {
                    "finance_manager_db"
                } else {
                    "finance_db_guest.db"
                }
            } else {
                "finance_db_user_$sanitized.db"
            }
        }

        private fun buildDatabase(context: Context, scope: CoroutineScope, dbName: String): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .addCallback(AppDatabaseCallback(context.applicationContext, scope, dbName))

            // Only allow destructive migration in debug builds; release builds preserve data and trigger emergency backup
            if (com.example.BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration(true)
            }
            return builder.build()
        }

        fun getDatabaseForUser(context: Context, userKey: String): AppDatabase {
            return getDatabaseForUser(context, CoroutineScope(Dispatchers.IO), userKey)
        }

        fun getDatabaseForUser(context: Context, scope: CoroutineScope, userKey: String): AppDatabase {
            val dbName = getDatabaseFileName(context, userKey)
            val existing = instances[dbName]
            if (existing != null && existing.isOpen) {
                return existing
            }
            return synchronized(lock) {
                val current = instances[dbName]
                if (current != null && current.isOpen) {
                    current
                } else {
                    val db = buildDatabase(context, scope, dbName)
                    instances[dbName] = db
                    db
                }
            }
        }

        fun getActiveDatabase(context: Context): AppDatabase {
            return getActiveDatabase(context, CoroutineScope(Dispatchers.IO))
        }

        fun getActiveDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return getDatabaseForUser(context, scope, currentActiveUserKey)
        }

        fun getDatabase(context: Context): AppDatabase {
            return getActiveDatabase(context)
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return getActiveDatabase(context, scope)
        }

        fun setActiveUserKey(userKey: String) {
            currentActiveUserKey = sanitizeUserKey(userKey)
        }

        fun getActiveUserKey(): String = currentActiveUserKey

        fun closeDatabaseForUser(context: Context, userKey: String) {
            val dbName = getDatabaseFileName(context, userKey)
            synchronized(lock) {
                instances.remove(dbName)?.let { db ->
                    try {
                        if (db.isOpen) db.close()
                    } catch (e: Exception) {
                        android.util.Log.w("YosanDB", "Failed to close database $dbName", e)
                    }
                }
            }
        }

        fun closeAllDatabases() {
            synchronized(lock) {
                instances.forEach { (name, db) ->
                    try {
                        if (db.isOpen) db.close()
                    } catch (e: Exception) {
                        android.util.Log.w("YosanDB", "Failed to close database $name during closeAll", e)
                    }
                }
                instances.clear()
            }
        }

        private class AppDatabaseCallback(
            private val context: Context,
            private val scope: CoroutineScope,
            private val dbName: String
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                scope.launch(Dispatchers.IO) {
                    val database = synchronized(lock) { instances[dbName] }
                    database?.let { DatabaseInitializer.seedInitialData(it) }
                }
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                try {
                    val dbFile = context.getDatabasePath(dbName)
                    if (dbFile.exists()) {
                        val timestamp = System.currentTimeMillis()
                        val backupFile = java.io.File(context.filesDir, "emergency_backup_${dbName}_$timestamp.db")
                        dbFile.copyTo(backupFile, overwrite = true)
                        android.util.Log.w("YosanDB", "Emergency database backup created before destructive migration at: ${backupFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("YosanDB", "Failed to create emergency backup before destructive migration", e)
                }
            }
        }

    }
}
