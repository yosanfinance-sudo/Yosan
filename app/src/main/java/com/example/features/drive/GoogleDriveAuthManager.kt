package com.example.features.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DriveAuthTokenResult {
    data class Success(val token: String, val email: String) : DriveAuthTokenResult()
    data class NeedsUserConsent(val consentIntent: Intent?, val email: String) : DriveAuthTokenResult()
    data class Error(val message: String) : DriveAuthTokenResult()
}

class GoogleDriveAuthManager(private val context: Context) {

    private val TAG = "GoogleDriveAuthManager"
    private val driveFileScope = Scope(DRIVE_FILE_SCOPE)

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val OAUTH2_DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
    }

    /**
     * Builds a native Google Play Services sign-in intent requesting access
     * only to files created by Yosan Finance (drive.file scope).
     */
    fun getDriveSignInIntent(preferredEmail: String? = null): Intent {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveFileScope)

        if (!preferredEmail.isNullOrBlank()) {
            builder.setAccountName(preferredEmail.trim().lowercase())
        }

        val gso = builder.build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    /**
     * Retrieves an OAuth 2.0 access token for an authenticated GoogleSignInAccount.
     */
    suspend fun fetchAccessToken(account: GoogleSignInAccount): DriveAuthTokenResult = withContext(Dispatchers.IO) {
        val email = account.email ?: ""
        val androidAccount = account.account ?: Account(email, "com.google")
        fetchAccessTokenForAccount(androidAccount, email)
    }

    /**
     * Retrieves an OAuth 2.0 access token for a Google Account email.
     */
    suspend fun fetchAccessTokenForEmail(email: String): DriveAuthTokenResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val account = Account(cleanEmail, "com.google")
        fetchAccessTokenForAccount(account, cleanEmail)
    }

    private fun fetchAccessTokenForAccount(account: Account, email: String): DriveAuthTokenResult {
        return try {
            Log.i(TAG, "Requesting Drive OAuth token for ${account.name}...")
            val token = GoogleAuthUtil.getToken(context, account, OAUTH2_DRIVE_SCOPE)
            if (!token.isNullOrBlank()) {
                Log.i(TAG, "Google Drive OAuth token acquired successfully")
                DriveAuthTokenResult.Success(token, email)
            } else {
                DriveAuthTokenResult.Error("Google Play Services returned an empty Drive token.")
            }
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "Drive authorization requires user consent dialog: ${recoverable.message}")
            DriveAuthTokenResult.NeedsUserConsent(recoverable.intent, email)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Drive OAuth token: ${e.message}", e)
            DriveAuthTokenResult.Error(e.localizedMessage ?: "Failed to authorize Google Drive")
        }
    }

    /**
     * Clears cached token to force re-authorization when needed.
     */
    suspend fun clearToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "clearToken warning: ${e.message}")
        }
    }

    /**
     * Signs out from Google Play Services client.
     */
    fun signOut(onComplete: (() -> Unit)? = null) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "signOut warning: ${e.message}")
            onComplete?.invoke()
        }
    }
}
