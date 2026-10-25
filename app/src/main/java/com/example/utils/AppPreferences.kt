package com.example.utils

import android.content.Context
import android.content.SharedPreferences

import org.json.JSONArray
import org.json.JSONObject

data class RememberedAccount(
    val email: String,
    val name: String,
    val profilePicture: String = "",
    val googleSub: String = "",
    val bankName: String = "",
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
        }

    var isDarkMode: Boolean?
        get() {
            return if (prefs.contains(KEY_DARK_MODE)) {
                prefs.getBoolean(KEY_DARK_MODE, false)
            } else {
                null
            }
        }
        set(value) {
            if (value != null) {
                prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            } else {
                prefs.edit().remove(KEY_DARK_MODE).apply()
            }
        }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var appLockTimeoutSeconds: Int
        get() = prefs.getInt(KEY_APP_LOCK_TIMEOUT_SECONDS, 0)
        set(value) = prefs.edit().putInt(KEY_APP_LOCK_TIMEOUT_SECONDS, value).apply()

    var isExternalIntentActive: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_INTENT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_EXTERNAL_INTENT_ACTIVE, value).apply()

    var isIntroNoteDismissed: Boolean
        get() = prefs.getBoolean(KEY_INTRO_NOTE_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO_NOTE_DISMISSED, value).apply()

    var isWalkthroughCompleted: Boolean
        get() = prefs.getBoolean(KEY_WALKTHROUGH_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_WALKTHROUGH_COMPLETED, value).apply()

    var isAutoCheckUpdatesEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()

    var isBackupEncryptionEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_ENCRYPTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_ENCRYPTION_ENABLED, value).apply()

    var isHapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, value).apply()

    var isFloatingEffectEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_EFFECT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_EFFECT_ENABLED, value).apply()

    var backupPassphrase: String?
        get() = prefs.getString(KEY_BACKUP_PASSPHRASE, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_BACKUP_PASSPHRASE, value).apply()
            else prefs.edit().remove(KEY_BACKUP_PASSPHRASE).apply()
        }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var isLoggedOut: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_OUT, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_OUT, value).apply()

    var isOfflineMode: Boolean
        get() = prefs.getBoolean(KEY_IS_OFFLINE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_OFFLINE_MODE, value).apply()

    var lastUserEmail: String
        get() = prefs.getString(KEY_LAST_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_USER_EMAIL, value).apply()

    var lastUserName: String
        get() = prefs.getString(KEY_LAST_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_USER_NAME, value).apply()

    var lastUserProfilePic: String
        get() = prefs.getString(KEY_LAST_USER_PROFILE_PIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_USER_PROFILE_PIC, value).apply()

    var lastBankName: String
        get() = prefs.getString(KEY_LAST_BANK_NAME, "") ?: ""
        set(value) {
            val trimmed = value.trim()
            prefs.edit().putString(KEY_LAST_BANK_NAME, trimmed).apply()
            if (trimmed.isNotBlank()) {
                val currentList = getRememberedAccounts().toMutableList()
                val targetEmail = lastUserEmail.trim()
                val targetSub = lastGoogleSub.trim()
                val index = currentList.indexOfFirst {
                    (targetEmail.isNotBlank() && it.email.equals(targetEmail, ignoreCase = true)) ||
                    (targetSub.isNotBlank() && it.googleSub == targetSub)
                }
                if (index in currentList.indices) {
                    val old = currentList[index]
                    currentList[index] = old.copy(
                        bankName = trimmed,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                    persistRememberedAccounts(currentList)
                }
            }
        }

    var lastGoogleSub: String
        get() = prefs.getString(KEY_LAST_GOOGLE_SUB, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_GOOGLE_SUB, value).apply()

    val hasPreviousAccountOnDevice: Boolean
        get() = getRememberedAccounts().isNotEmpty() ||
                lastUserEmail.isNotBlank() ||
                lastUserName.isNotBlank() ||
                lastBankName.isNotBlank()

    private fun persistRememberedAccounts(list: List<RememberedAccount>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("email", item.email)
                    put("name", item.name)
                    put("profilePicture", item.profilePicture)
                    put("googleSub", item.googleSub)
                    put("bankName", item.bankName)
                    put("lastUsedTimestamp", item.lastUsedTimestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_REMEMBERED_ACCOUNTS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRememberedAccounts(): List<RememberedAccount> {
        val jsonStr = prefs.getString(KEY_REMEMBERED_ACCOUNTS, null)
        val list = mutableListOf<RememberedAccount>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val email = obj.optString("email", "").trim()
                    val name = obj.optString("name", "").trim()
                    if (email.isNotBlank() || name.isNotBlank()) {
                        list.add(
                            RememberedAccount(
                                email = email,
                                name = name,
                                profilePicture = obj.optString("profilePicture", ""),
                                googleSub = obj.optString("googleSub", ""),
                                bankName = obj.optString("bankName", ""),
                                lastUsedTimestamp = obj.optLong("lastUsedTimestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // Migrate legacy scalar fields if present and not in list
        if (list.isEmpty() && (lastUserEmail.isNotBlank() || lastUserName.isNotBlank() || lastBankName.isNotBlank())) {
            val legacy = RememberedAccount(
                email = lastUserEmail,
                name = lastUserName.ifBlank { "User" },
                profilePicture = lastUserProfilePic,
                googleSub = lastGoogleSub,
                bankName = lastBankName,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            list.add(legacy)
            persistRememberedAccounts(list)
        }

        return list.sortedByDescending { it.lastUsedTimestamp }
    }

    fun saveRememberedAccount(account: RememberedAccount) {
        val cleanEmail = account.email.trim()
        val cleanName = account.name.trim()
        if (cleanEmail.isBlank() && cleanName.isBlank()) return

        val currentList = getRememberedAccounts().toMutableList()
        val index = currentList.indexOfFirst {
            (cleanEmail.isNotBlank() && it.email.equals(cleanEmail, ignoreCase = true)) ||
            (account.googleSub.isNotBlank() && it.googleSub == account.googleSub)
        }

        val updated = if (index >= 0) {
            val old = currentList[index]
            account.copy(
                email = if (cleanEmail.isNotBlank()) cleanEmail else old.email,
                name = if (cleanName.isNotBlank()) cleanName else old.name,
                profilePicture = account.profilePicture.ifBlank { old.profilePicture },
                googleSub = account.googleSub.ifBlank { old.googleSub },
                bankName = account.bankName.ifBlank { old.bankName },
                lastUsedTimestamp = System.currentTimeMillis()
            )
        } else {
            account.copy(
                email = cleanEmail,
                name = cleanName,
                lastUsedTimestamp = System.currentTimeMillis()
            )
        }

        if (index >= 0) {
            currentList.removeAt(index)
        }
        currentList.add(0, updated)

        persistRememberedAccounts(currentList)

        // Keep scalar properties synced to most recently used account
        prefs.edit().apply {
            putString(KEY_LAST_USER_EMAIL, updated.email)
            putString(KEY_LAST_USER_NAME, updated.name)
            putString(KEY_LAST_USER_PROFILE_PIC, updated.profilePicture)
            putString(KEY_LAST_GOOGLE_SUB, updated.googleSub)
            if (updated.bankName.isNotBlank()) {
                putString(KEY_LAST_BANK_NAME, updated.bankName)
            }
            apply()
        }
    }

    fun removeRememberedAccount(email: String) {
        val currentList = getRememberedAccounts().toMutableList()
        currentList.removeAll { it.email.equals(email.trim(), ignoreCase = true) }

        persistRememberedAccounts(currentList)

        if (currentList.isNotEmpty()) {
            val latest = currentList.first()
            prefs.edit().apply {
                putString(KEY_LAST_USER_EMAIL, latest.email)
                putString(KEY_LAST_USER_NAME, latest.name)
                putString(KEY_LAST_USER_PROFILE_PIC, latest.profilePicture)
                putString(KEY_LAST_GOOGLE_SUB, latest.googleSub)
                putString(KEY_LAST_BANK_NAME, latest.bankName)
                apply()
            }
        } else {
            prefs.edit().apply {
                putString(KEY_LAST_USER_EMAIL, "")
                putString(KEY_LAST_USER_NAME, "")
                putString(KEY_LAST_USER_PROFILE_PIC, "")
                putString(KEY_LAST_GOOGLE_SUB, "")
                putString(KEY_LAST_BANK_NAME, "")
                apply()
            }
        }
    }

    fun clearAllRememberedAccounts() {
        prefs.edit().apply {
            remove(KEY_REMEMBERED_ACCOUNTS)
            remove(KEY_LAST_USER_EMAIL)
            remove(KEY_LAST_USER_NAME)
            remove(KEY_LAST_USER_PROFILE_PIC)
            remove(KEY_LAST_BANK_NAME)
            remove(KEY_LAST_GOOGLE_SUB)
            apply()
        }
    }

    fun clearSession(preserveDeviceIdentity: Boolean = true) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_OUT, true)
            putBoolean(KEY_IS_OFFLINE_MODE, false)
            if (!preserveDeviceIdentity) {
                remove(KEY_REMEMBERED_ACCOUNTS)
                remove(KEY_LAST_USER_EMAIL)
                remove(KEY_LAST_USER_NAME)
                remove(KEY_LAST_USER_PROFILE_PIC)
                remove(KEY_LAST_BANK_NAME)
                remove(KEY_LAST_GOOGLE_SUB)
            }
            apply()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "yosan_app_preferences"
        private const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BIOMETRIC_ENABLED = "is_biometric_enabled"
        private const val KEY_APP_LOCK_TIMEOUT_SECONDS = "app_lock_timeout_seconds"
        private const val KEY_EXTERNAL_INTENT_ACTIVE = "external_intent_active"
        private const val KEY_INTRO_NOTE_DISMISSED = "is_intro_note_dismissed"
        private const val KEY_WALKTHROUGH_COMPLETED = "is_walkthrough_completed"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates_enabled"
        private const val KEY_BACKUP_ENCRYPTION_ENABLED = "backup_encryption_enabled"
        private const val KEY_BACKUP_PASSPHRASE = "backup_passphrase"
        private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        private const val KEY_FLOATING_EFFECT_ENABLED = "floating_effect_enabled"
        private const val KEY_IS_LOGGED_OUT = "is_logged_out"
        private const val KEY_IS_OFFLINE_MODE = "is_offline_mode"
        private const val KEY_LAST_USER_EMAIL = "last_user_email"
        private const val KEY_LAST_USER_NAME = "last_user_name"
        private const val KEY_LAST_USER_PROFILE_PIC = "last_user_profile_pic"
        private const val KEY_LAST_BANK_NAME = "last_bank_name"
        private const val KEY_LAST_GOOGLE_SUB = "last_google_sub"
        private const val KEY_REMEMBERED_ACCOUNTS = "remembered_accounts"
    }
}

