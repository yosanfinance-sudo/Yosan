package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class UserSession(
    val userId: Long = 1L,
    val googleSub: String = "",
    val email: String = "",
    val name: String = "",
    val profilePicture: String = "",
    val emailVerified: Boolean = false,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val driveAccessToken: String? = null,
    val driveRefreshToken: String? = null,
    val driveTokenExpiresAt: Long = 0L,
    val driveEmail: String? = null,
    val storagePreference: String = "LOCAL",
    val sessionToken: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
) {
    val isSessionExpired: Boolean
        get() {
            if (expiresAt <= 0L) return false
            return System.currentTimeMillis() >= (expiresAt - 60_000L)
        }

    val isDriveConnected: Boolean
        get() = !driveAccessToken.isNullOrBlank() || !driveRefreshToken.isNullOrBlank()
}

class SecureTokenManager(context: Context) {

    private var isUsingFallback = false
    private val fallbackSeed: String by lazy {
        try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                ?: "yosan_token_secure_seed"
        } catch (_: Throwable) {
            "yosan_token_secure_seed"
        }
    }

    private fun encryptIfFallback(value: String?): String? {
        if (value.isNullOrBlank() || !isUsingFallback) return value
        return try {
            BackupEncryptionUtil.encrypt(value, fallbackSeed)
        } catch (_: Throwable) {
            value
        }
    }

    private fun decryptIfFallback(value: String?): String? {
        if (value.isNullOrBlank() || !isUsingFallback) return value
        return try {
            if (BackupEncryptionUtil.isEncrypted(value)) {
                BackupEncryptionUtil.decrypt(value, fallbackSeed)
            } else {
                value
            }
        } catch (_: Throwable) {
            value
        }
    }

    private val sharedPreferences: SharedPreferences = run {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_auth_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            // Fallback for JVM/Robolectric unit tests or devices where KeyStore is unavailable/corrupt
            context.getSharedPreferences("secure_auth_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long? = null,
        email: String? = null
    ) {
        val expiresAtMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)
        }

        val targetEmail = email?.trim()?.lowercase()
        val currentActive = getUserEmail()?.trim()?.lowercase()

        // If targetEmail is omitted, default to current active email (e.g. ApiClient token refresh)
        val effectiveEmail = targetEmail ?: currentActive

        // Update active sharedPreferences if target is the active user or if no user is active yet
        if (currentActive.isNullOrBlank() || targetEmail == null || targetEmail == currentActive) {
            val editor = sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, encryptIfFallback(accessToken))
                .putString(KEY_REFRESH_TOKEN, encryptIfFallback(refreshToken))
            if (expiresInSeconds != null && expiresInSeconds > 0) {
                editor.putLong(KEY_EXPIRES_AT, expiresAtMs)
            }
            editor.apply()
        }

        // Update persistent session record for the target user if specified or active
        if (!effectiveEmail.isNullOrBlank()) {
            val existing = getUserSession(effectiveEmail)
            if (existing != null) {
                val updated = existing.copy(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAtMs,
                    lastActiveAt = System.currentTimeMillis()
                )
                saveSessionInternal(updated)
                registerUserSessionEmail(effectiveEmail)
            }
        }
    }

    fun getAccessToken(): String? = decryptIfFallback(sharedPreferences.getString(KEY_ACCESS_TOKEN, null))

    fun getRefreshToken(): String? = decryptIfFallback(sharedPreferences.getString(KEY_REFRESH_TOKEN, null))

    fun getExpiresAt(): Long = sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)

    fun isSessionExpired(): Boolean {
        val expiresAt = getExpiresAt()
        if (expiresAt <= 0L) return false
        // Treat as expired 60 seconds before actual expiry to allow refreshing
        return System.currentTimeMillis() >= (expiresAt - 60_000L)
    }

    fun saveUserSession(
        userId: Long,
        googleSub: String,
        email: String,
        name: String,
        profilePicture: String,
        emailVerified: Boolean = false,
        sessionToken: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null
    ) {
        val cleanEmail = email.trim().lowercase()
        val existing = if (cleanEmail.isNotBlank()) getUserSession(cleanEmail) else null
        val effectiveSessionToken = sessionToken?.takeIf { it.isNotBlank() }
            ?: existing?.sessionToken?.takeIf { it.isNotBlank() }
            ?: generateSessionToken(cleanEmail)

        val expiresAtMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            getExpiresAt().takeIf { it > 0 } ?: existing?.expiresAt ?: 0L
        }

        val currentActive = getUserEmail()?.trim()?.lowercase()
        val effectiveAccessToken = accessToken?.takeIf { it.isNotBlank() }
            ?: (if (currentActive == cleanEmail) getAccessToken() else null)?.takeIf { it.isNotBlank() }
            ?: existing?.accessToken?.takeIf { it.isNotBlank() }
            ?: getAccessToken()
            ?: ""

        val effectiveRefreshToken = refreshToken?.takeIf { it.isNotBlank() }
            ?: (if (currentActive == cleanEmail) getRefreshToken() else null)?.takeIf { it.isNotBlank() }
            ?: existing?.refreshToken?.takeIf { it.isNotBlank() }
            ?: getRefreshToken()
            ?: ""

        val currentDriveAccess = getDriveAccessToken() ?: existing?.driveAccessToken
        val currentDriveRefresh = getDriveRefreshToken() ?: existing?.driveRefreshToken
        val currentDriveExpiresAt = getDriveTokenExpiresAt().takeIf { it > 0 } ?: existing?.driveTokenExpiresAt ?: 0L
        val currentDriveEmail = getDriveEmail() ?: existing?.driveEmail
        val currentStoragePref = getStoragePreference()

        val updatedSession = UserSession(
            userId = userId,
            googleSub = googleSub,
            email = cleanEmail,
            name = name,
            profilePicture = profilePicture,
            emailVerified = emailVerified,
            accessToken = effectiveAccessToken,
            refreshToken = effectiveRefreshToken,
            expiresAt = expiresAtMs,
            driveAccessToken = currentDriveAccess,
            driveRefreshToken = currentDriveRefresh,
            driveTokenExpiresAt = currentDriveExpiresAt,
            driveEmail = currentDriveEmail,
            storagePreference = currentStoragePref,
            sessionToken = effectiveSessionToken,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )

        if (cleanEmail.isNotBlank()) {
            saveSessionInternal(updatedSession)
            registerUserSessionEmail(cleanEmail)
        }

        val editor = sharedPreferences.edit()
            .putString(KEY_ACTIVE_USER_EMAIL, cleanEmail)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_GOOGLE_SUB, googleSub)
            .putString(KEY_USER_EMAIL, cleanEmail)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_PROFILE_PIC, profilePicture)
            .putBoolean(KEY_EMAIL_VERIFIED, emailVerified)
            .putString(KEY_SESSION_TOKEN, effectiveSessionToken)

        if (effectiveAccessToken.isNotBlank()) {
            editor.putString(KEY_ACCESS_TOKEN, effectiveAccessToken)
        }
        if (effectiveRefreshToken.isNotBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, effectiveRefreshToken)
        }
        if (expiresAtMs > 0L) {
            editor.putLong(KEY_EXPIRES_AT, expiresAtMs)
        }
        if (!currentDriveAccess.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_ACCESS_TOKEN, currentDriveAccess)
        }
        if (!currentDriveRefresh.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_REFRESH_TOKEN, currentDriveRefresh)
        }
        if (currentDriveExpiresAt > 0L) {
            editor.putLong(KEY_DRIVE_TOKEN_EXPIRES_AT, currentDriveExpiresAt)
        }
        if (!currentDriveEmail.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_EMAIL, currentDriveEmail)
        }
        editor.apply()
    }

    fun getUserId(): Long = sharedPreferences.getLong(KEY_USER_ID, -1L)
    fun getGoogleSub(): String? = sharedPreferences.getString(KEY_GOOGLE_SUB, null)
    fun getUserEmail(): String? = sharedPreferences.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)
    fun getProfilePic(): String? = sharedPreferences.getString(KEY_PROFILE_PIC, null)
    fun isEmailVerified(): Boolean = sharedPreferences.getBoolean(KEY_EMAIL_VERIFIED, false)
    fun setEmailVerified(verified: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_EMAIL_VERIFIED, verified).apply()
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            getUserSession(activeEmail)?.let {
                saveSessionInternal(it.copy(emailVerified = verified, lastActiveAt = System.currentTimeMillis()))
            }
        }
    }

    fun getSessionToken(): String? {
        val active = sharedPreferences.getString(KEY_SESSION_TOKEN, null)
        if (!active.isNullOrBlank()) return active
        val email = getUserEmail()?.trim()?.lowercase()
        if (!email.isNullOrBlank()) {
            val session = getUserSession(email)
            if (session != null && session.sessionToken.isNotBlank()) {
                sharedPreferences.edit().putString(KEY_SESSION_TOKEN, session.sessionToken).apply()
                return session.sessionToken
            }
        }
        return null
    }

    // Google Drive dedicated OAuth credentials
    fun saveDriveTokens(
        accessToken: String,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null,
        driveEmail: String? = null
    ) {
        val expiresAtMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            sharedPreferences.getLong(KEY_DRIVE_TOKEN_EXPIRES_AT, 0L)
        }

        val editor = sharedPreferences.edit()
            .putString(KEY_DRIVE_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_REFRESH_TOKEN, encryptIfFallback(refreshToken))
        }
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            editor.putLong(KEY_DRIVE_TOKEN_EXPIRES_AT, expiresAtMs)
        }
        if (!driveEmail.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_EMAIL, driveEmail.trim().lowercase())
        }
        editor.apply()

        // Also update per-user session
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            val existing = getUserSession(activeEmail)
            if (existing != null) {
                val updated = existing.copy(
                    driveAccessToken = accessToken,
                    driveRefreshToken = refreshToken ?: existing.driveRefreshToken,
                    driveTokenExpiresAt = expiresAtMs,
                    driveEmail = driveEmail?.trim()?.lowercase() ?: existing.driveEmail,
                    lastActiveAt = System.currentTimeMillis()
                )
                saveSessionInternal(updated)
            }
        }
    }

    fun getDriveAccessToken(): String? {
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            return getUserSession(activeEmail)?.driveAccessToken
        }
        return decryptIfFallback(sharedPreferences.getString(KEY_DRIVE_ACCESS_TOKEN, null))
    }

    fun getDriveRefreshToken(): String? {
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            return getUserSession(activeEmail)?.driveRefreshToken
        }
        return decryptIfFallback(sharedPreferences.getString(KEY_DRIVE_REFRESH_TOKEN, null))
    }

    fun getDriveTokenExpiresAt(): Long {
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            return getUserSession(activeEmail)?.driveTokenExpiresAt ?: 0L
        }
        return sharedPreferences.getLong(KEY_DRIVE_TOKEN_EXPIRES_AT, 0L)
    }

    fun getDriveEmail(): String? {
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            return getUserSession(activeEmail)?.driveEmail
        }
        return sharedPreferences.getString(KEY_DRIVE_EMAIL, null)?.trim()?.lowercase()
    }

    fun isDriveConnected(): Boolean = !getDriveAccessToken().isNullOrBlank() || !getDriveRefreshToken().isNullOrBlank()

    fun clearDriveTokens() {
        sharedPreferences.edit()
            .remove(KEY_DRIVE_ACCESS_TOKEN)
            .remove(KEY_DRIVE_REFRESH_TOKEN)
            .remove(KEY_DRIVE_TOKEN_EXPIRES_AT)
            .remove(KEY_DRIVE_EMAIL)
            .apply()

        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            getUserSession(activeEmail)?.let {
                saveSessionInternal(
                    it.copy(
                        driveAccessToken = null,
                        driveRefreshToken = null,
                        driveTokenExpiresAt = 0L,
                        driveEmail = null,
                        lastActiveAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // Storage Preference: "LOCAL", "DRIVE", "SYNC"
    fun getStoragePreference(): String = sharedPreferences.getString(KEY_STORAGE_PREFERENCE, "LOCAL") ?: "LOCAL"
    fun setStoragePreference(pref: String) {
        sharedPreferences.edit().putString(KEY_STORAGE_PREFERENCE, pref).apply()
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!activeEmail.isNullOrBlank()) {
            getUserSession(activeEmail)?.let {
                saveSessionInternal(it.copy(storagePreference = pref, lastActiveAt = System.currentTimeMillis()))
            }
        }
    }

    fun isLiveSyncEnabled(): Boolean {
        if (sharedPreferences.contains("is_live_sync_enabled")) {
            return sharedPreferences.getBoolean("is_live_sync_enabled", true)
        }
        return getStoragePreference() != "LOCAL" || !getDriveAccessToken().isNullOrBlank()
    }

    fun setLiveSyncEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("is_live_sync_enabled", enabled).apply()
        if (enabled && getStoragePreference() == "LOCAL") {
            setStoragePreference("DRIVE")
        }
    }

    fun isLoggedIn(): Boolean {
        return !getUserEmail().isNullOrBlank() && !getGoogleSub().isNullOrBlank()
    }

    // ==========================================
    // Multi-Account Session Management
    // ==========================================

    fun switchUserSession(email: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val session = getUserSession(cleanEmail) ?: return false
        val updated = session.copy(lastActiveAt = System.currentTimeMillis())
        saveSessionInternal(updated)

        val editor = sharedPreferences.edit()
        editor.putString(KEY_ACTIVE_USER_EMAIL, cleanEmail)
        editor.putString(KEY_ACCESS_TOKEN, updated.accessToken)
        editor.putString(KEY_REFRESH_TOKEN, updated.refreshToken)
        editor.putLong(KEY_EXPIRES_AT, updated.expiresAt)
        editor.putLong(KEY_USER_ID, updated.userId)
        editor.putString(KEY_GOOGLE_SUB, updated.googleSub)
        editor.putString(KEY_USER_EMAIL, updated.email)
        editor.putString(KEY_USER_NAME, updated.name)
        editor.putString(KEY_PROFILE_PIC, updated.profilePicture)
        editor.putBoolean(KEY_EMAIL_VERIFIED, updated.emailVerified)
        editor.putString(KEY_STORAGE_PREFERENCE, updated.storagePreference)
        editor.putString(KEY_SESSION_TOKEN, updated.sessionToken)

        if (!updated.driveAccessToken.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_ACCESS_TOKEN, updated.driveAccessToken)
        } else {
            editor.remove(KEY_DRIVE_ACCESS_TOKEN)
        }
        if (!updated.driveRefreshToken.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_REFRESH_TOKEN, updated.driveRefreshToken)
        } else {
            editor.remove(KEY_DRIVE_REFRESH_TOKEN)
        }
        editor.putLong(KEY_DRIVE_TOKEN_EXPIRES_AT, updated.driveTokenExpiresAt)
        if (!updated.driveEmail.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_EMAIL, updated.driveEmail)
        } else {
            editor.remove(KEY_DRIVE_EMAIL)
        }
        editor.apply()
        return true
    }

    fun removeUserSession(email: String) {
        val cleanEmail = email.trim().lowercase()
        val sanitized = sanitizeKey(cleanEmail)
        unregisterUserSessionEmail(cleanEmail)
        sharedPreferences.edit()
            .remove(KEY_SESSION_PREFIX + sanitized)
            .apply()

        val currentActive = getUserEmail()?.trim()?.lowercase()
        if (cleanEmail.equals(currentActive, ignoreCase = true)) {
            clearActiveSessionPreferences()
        }
    }

    private fun clearActiveSessionPreferences() {
        sharedPreferences.edit()
            .remove(KEY_ACTIVE_USER_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .remove(KEY_GOOGLE_SUB)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_PROFILE_PIC)
            .remove(KEY_EMAIL_VERIFIED)
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_DRIVE_ACCESS_TOKEN)
            .remove(KEY_DRIVE_REFRESH_TOKEN)
            .remove(KEY_DRIVE_TOKEN_EXPIRES_AT)
            .remove(KEY_DRIVE_EMAIL)
            .apply()
    }

    fun clearActiveSession(preserveSavedSession: Boolean = true) {
        val activeEmail = getUserEmail()?.trim()?.lowercase()
        if (!preserveSavedSession && !activeEmail.isNullOrBlank()) {
            val sanitized = sanitizeKey(activeEmail)
            unregisterUserSessionEmail(activeEmail)
            sharedPreferences.edit().remove(KEY_SESSION_PREFIX + sanitized).apply()
        }
        clearActiveSessionPreferences()
    }

    fun clearSession() {
        clearActiveSession(preserveSavedSession = true)
    }

    fun getUserSession(email: String): UserSession? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        val sanitized = sanitizeKey(cleanEmail)
        val rawJson = sharedPreferences.getString(KEY_SESSION_PREFIX + sanitized, null) ?: return null
        val json = decryptIfFallback(rawJson) ?: return null
        return try {
            val obj = JSONObject(json)
            UserSession(
                userId = obj.optLong("userId", 1L),
                googleSub = obj.optString("googleSub", ""),
                email = obj.optString("email", cleanEmail),
                name = obj.optString("name", "User"),
                profilePicture = obj.optString("profilePicture", ""),
                emailVerified = obj.optBoolean("emailVerified", false),
                accessToken = obj.optString("accessToken", ""),
                refreshToken = obj.optString("refreshToken", ""),
                expiresAt = obj.optLong("expiresAt", 0L),
                driveAccessToken = obj.optString("driveAccessToken", "").takeIf { it.isNotBlank() },
                driveRefreshToken = obj.optString("driveRefreshToken", "").takeIf { it.isNotBlank() },
                driveTokenExpiresAt = obj.optLong("driveTokenExpiresAt", 0L),
                driveEmail = obj.optString("driveEmail", "").takeIf { it.isNotBlank() },
                storagePreference = obj.optString("storagePreference", "LOCAL"),
                sessionToken = obj.optString("sessionToken", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                lastActiveAt = obj.optLong("lastActiveAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAllUserSessions(): List<UserSession> {
        val emails = getRegisteredSessionEmails()
        val sessions = emails.mapNotNull { getUserSession(it) }
        return sessions.sortedByDescending { it.lastActiveAt }
    }

    fun getRegisteredSessionEmails(): List<String> {
        val json = sharedPreferences.getString(KEY_SESSION_EMAILS_INDEX, null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val email = array.optString(i, "").trim().lowercase()
                if (email.isNotBlank() && !list.contains(email)) {
                    list.add(email)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun registerUserSessionEmail(email: String) {
        val cleanEmail = email.trim().lowercase()
        val list = getRegisteredSessionEmails().toMutableSet()
        list.add(cleanEmail)
        val array = JSONArray()
        list.forEach { array.put(it) }
        sharedPreferences.edit().putString(KEY_SESSION_EMAILS_INDEX, array.toString()).apply()
    }

    private fun unregisterUserSessionEmail(email: String) {
        val cleanEmail = email.trim().lowercase()
        val list = getRegisteredSessionEmails().toMutableSet()
        list.remove(cleanEmail)
        val array = JSONArray()
        list.forEach { array.put(it) }
        sharedPreferences.edit().putString(KEY_SESSION_EMAILS_INDEX, array.toString()).apply()
    }

    private fun saveSessionInternal(session: UserSession) {
        val cleanEmail = session.email.trim().lowercase()
        if (cleanEmail.isBlank()) return
        val sanitized = sanitizeKey(cleanEmail)
        val json = JSONObject().apply {
            put("userId", session.userId)
            put("googleSub", session.googleSub)
            put("email", session.email)
            put("name", session.name)
            put("profilePicture", session.profilePicture)
            put("emailVerified", session.emailVerified)
            put("accessToken", session.accessToken)
            put("refreshToken", session.refreshToken)
            put("expiresAt", session.expiresAt)
            put("driveAccessToken", session.driveAccessToken ?: "")
            put("driveRefreshToken", session.driveRefreshToken ?: "")
            put("driveTokenExpiresAt", session.driveTokenExpiresAt)
            put("driveEmail", session.driveEmail ?: "")
            put("storagePreference", session.storagePreference)
            put("sessionToken", session.sessionToken)
            put("createdAt", session.createdAt)
            put("lastActiveAt", session.lastActiveAt)
        }.toString()

        sharedPreferences.edit()
            .putString(KEY_SESSION_PREFIX + sanitized, encryptIfFallback(json))
            .apply()
    }

    fun saveBackupPassphrase(passphrase: String?) {
        if (passphrase.isNullOrBlank()) {
            sharedPreferences.edit().remove(KEY_BACKUP_PASSPHRASE).apply()
        } else {
            sharedPreferences.edit().putString(KEY_BACKUP_PASSPHRASE, passphrase).apply()
        }
    }

    fun getBackupPassphrase(): String? = sharedPreferences.getString(KEY_BACKUP_PASSPHRASE, null)

    fun saveOAuthStateNonce(nonce: String) {
        sharedPreferences.edit().putString(KEY_OAUTH_STATE_NONCE, nonce).apply()
    }

    fun getOAuthStateNonce(): String? = sharedPreferences.getString(KEY_OAUTH_STATE_NONCE, null)

    fun clearOAuthStateNonce() {
        sharedPreferences.edit().remove(KEY_OAUTH_STATE_NONCE).apply()
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACTIVE_USER_EMAIL = "active_user_email"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_GOOGLE_SUB = "google_sub"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_PIC = "profile_picture_url"
        private const val KEY_EMAIL_VERIFIED = "email_verified"
        private const val KEY_STORAGE_PREFERENCE = "storage_preference"
        private const val KEY_SESSION_TOKEN = "session_token"

        // Google Drive keys
        private const val KEY_DRIVE_ACCESS_TOKEN = "drive_access_token"
        private const val KEY_DRIVE_REFRESH_TOKEN = "drive_refresh_token"
        private const val KEY_DRIVE_TOKEN_EXPIRES_AT = "drive_token_expires_at"
        private const val KEY_DRIVE_EMAIL = "drive_email"
        private const val KEY_BACKUP_PASSPHRASE = "secure_backup_passphrase"
        private const val KEY_OAUTH_STATE_NONCE = "oauth_state_nonce"

        // Multi-Account Keys
        private const val KEY_SESSION_PREFIX = "user_sess_"
        private const val KEY_SESSION_EMAILS_INDEX = "user_sessions_emails_index"

        fun generateSessionToken(email: String = ""): String {
            val clean = if (email.isNotBlank()) {
                email.trim().lowercase()
                    .replace("@", "_at_")
                    .replace(".", "_")
                    .replace(Regex("[^a-zA-Z0-9_]"), "")
            } else {
                "session"
            }
            val randomBytes = ByteArray(24)
            SecureRandom().nextBytes(randomBytes)
            val randomHex = randomBytes.joinToString("") { "%02x".format(it) }
            val timestamp = System.currentTimeMillis()
            return "yosan_sess_${clean}_${timestamp}_$randomHex"
        }

        private fun sanitizeKey(email: String): String {
            return email.trim().lowercase()
                .replace("@", "_at_")
                .replace(".", "_")
                .replace(Regex("[^a-zA-Z0-9_]"), "")
        }
    }
}
