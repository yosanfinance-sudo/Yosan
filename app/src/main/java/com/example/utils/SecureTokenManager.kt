package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long? = null) {
        val editor = sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            val expiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            editor.putLong(KEY_EXPIRES_AT, expiresAtMs)
        }
        editor.apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

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
        emailVerified: Boolean = false
    ) {
        sharedPreferences.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_GOOGLE_SUB, googleSub)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_PROFILE_PIC, profilePicture)
            .putBoolean(KEY_EMAIL_VERIFIED, emailVerified)
            .apply()
    }

    fun getUserId(): Long = sharedPreferences.getLong(KEY_USER_ID, -1L)
    fun getGoogleSub(): String? = sharedPreferences.getString(KEY_GOOGLE_SUB, null)
    fun getUserEmail(): String? = sharedPreferences.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)
    fun getProfilePic(): String? = sharedPreferences.getString(KEY_PROFILE_PIC, null)
    fun isEmailVerified(): Boolean = sharedPreferences.getBoolean(KEY_EMAIL_VERIFIED, false)
    fun setEmailVerified(verified: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_EMAIL_VERIFIED, verified).apply()
    }

    // Google Drive dedicated OAuth credentials
    fun saveDriveTokens(accessToken: String, refreshToken: String? = null, expiresInSeconds: Long? = null, driveEmail: String? = null) {
        val editor = sharedPreferences.edit()
            .putString(KEY_DRIVE_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_REFRESH_TOKEN, refreshToken)
        }
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            editor.putLong(KEY_DRIVE_TOKEN_EXPIRES_AT, System.currentTimeMillis() + (expiresInSeconds * 1000L))
        }
        if (!driveEmail.isNullOrBlank()) {
            editor.putString(KEY_DRIVE_EMAIL, driveEmail.trim().lowercase())
        }
        editor.apply()
    }

    fun getDriveAccessToken(): String? = sharedPreferences.getString(KEY_DRIVE_ACCESS_TOKEN, null)
    fun getDriveRefreshToken(): String? = sharedPreferences.getString(KEY_DRIVE_REFRESH_TOKEN, null)
    fun getDriveTokenExpiresAt(): Long = sharedPreferences.getLong(KEY_DRIVE_TOKEN_EXPIRES_AT, 0L)
    fun getDriveEmail(): String? = sharedPreferences.getString(KEY_DRIVE_EMAIL, null)
    fun isDriveConnected(): Boolean = !getDriveAccessToken().isNullOrBlank() || !getDriveRefreshToken().isNullOrBlank()

    fun clearDriveTokens() {
        sharedPreferences.edit()
            .remove(KEY_DRIVE_ACCESS_TOKEN)
            .remove(KEY_DRIVE_REFRESH_TOKEN)
            .remove(KEY_DRIVE_TOKEN_EXPIRES_AT)
            .remove(KEY_DRIVE_EMAIL)
            .apply()
    }

    // Storage Preference: "LOCAL", "DRIVE", "SYNC"
    fun getStoragePreference(): String = sharedPreferences.getString(KEY_STORAGE_PREFERENCE, "LOCAL") ?: "LOCAL"
    fun setStoragePreference(pref: String) {
        sharedPreferences.edit().putString(KEY_STORAGE_PREFERENCE, pref).apply()
    }

    fun isLoggedIn(): Boolean {
        return !getUserEmail().isNullOrBlank() && !getGoogleSub().isNullOrBlank()
    }

    fun clearSession() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .remove(KEY_GOOGLE_SUB)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_PROFILE_PIC)
            .remove(KEY_EMAIL_VERIFIED)
            .remove(KEY_DRIVE_ACCESS_TOKEN)
            .remove(KEY_DRIVE_REFRESH_TOKEN)
            .remove(KEY_DRIVE_TOKEN_EXPIRES_AT)
            .remove(KEY_DRIVE_EMAIL)
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

        // Google Drive keys
        private const val KEY_DRIVE_ACCESS_TOKEN = "drive_access_token"
        private const val KEY_DRIVE_REFRESH_TOKEN = "drive_refresh_token"
        private const val KEY_DRIVE_TOKEN_EXPIRES_AT = "drive_token_expires_at"
        private const val KEY_DRIVE_EMAIL = "drive_email"
        private const val KEY_BACKUP_PASSPHRASE = "secure_backup_passphrase"
        private const val KEY_OAUTH_STATE_NONCE = "oauth_state_nonce"
    }
}
