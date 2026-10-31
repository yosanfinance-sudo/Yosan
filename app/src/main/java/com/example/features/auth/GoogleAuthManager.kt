package com.example.features.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.example.BuildConfig
import android.content.Intent
import android.net.Uri
import java.util.UUID

sealed class GoogleAuthResult {
    data class Success(
        val idToken: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null
    ) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String, val code: String = "GOOGLE_AUTH_FAILED") : GoogleAuthResult()
    data class FallbackAccountPicker(val message: String) : GoogleAuthResult()
    data class RequiresBrowserOAuth(val authUri: Uri, val message: String) : GoogleAuthResult()
}

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val TAG = "GoogleAuthManager"

    fun isConfigured(): Boolean {
        val clientId = getWebClientId()
        return !clientId.contains("example.apps.googleusercontent.com") && clientId.isNotBlank()
    }

    private fun getActivityContext(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    suspend fun getGoogleIdToken(webClientId: String = getWebClientId()): GoogleAuthResult {
        if (!isConfigured()) {
            return GoogleAuthResult.Error(
                "Google Sign-In requires your Google Cloud Web Client ID. Please configure GOOGLE_WEB_CLIENT_ID in your environment.",
                "CONFIG_REQUIRED"
            )
        }
        val targetContext = getActivityContext(context) ?: context

        Log.i(TAG, "Initiating Google Sign-In with Web Client ID: $webClientId")

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.i(TAG, "Requesting credential via GetGoogleIdOption with server client ID: $webClientId")
            val response = credentialManager.getCredential(targetContext, request)

            val credential = response.credential
            Log.i(TAG, "Credential received: class=${credential::class.java.simpleName}")

            var idToken: String? = null
            var googleEmail: String? = null
            var googleDisplayName: String? = null
            var googlePhotoUrl: String? = null

            if (credential is CustomCredential) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    idToken = googleIdTokenCredential.idToken
                    googleEmail = googleIdTokenCredential.id
                    googleDisplayName = googleIdTokenCredential.displayName
                    googlePhotoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                    if (!idToken.isNullOrBlank() && (googleDisplayName.isNullOrBlank() || googleDisplayName.contains("@"))) {
                        try {
                            val claims = AuthViewModel.decodeGoogleIdTokenLocally(idToken)
                            if (!claims?.name.isNullOrBlank() && !claims.name.contains("@")) {
                                googleDisplayName = claims.name
                            }
                        } catch (_: Exception) {}
                    }
                } catch (pe: Exception) {
                    Log.w(TAG, "createFrom parse note (${pe.message}), inspecting data bundle")
                }

                if (idToken.isNullOrBlank()) {
                    idToken = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                        ?: credential.data.getString("id_token")
                        ?: credential.data.getString("idToken")
                        ?: credential.data.getString("token")
                    if (googleEmail.isNullOrBlank()) {
                        googleEmail = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID")
                            ?: credential.data.getString("id")
                            ?: credential.data.getString("email")
                    }
                    if (googleDisplayName.isNullOrBlank()) {
                        googleDisplayName = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_DISPLAY_NAME")
                            ?: credential.data.getString("displayName")
                            ?: credential.data.getString("name")
                    if (googleDisplayName.isNullOrBlank() || googleDisplayName.contains("@")) {
                        googleDisplayName = resolveDisplayName(targetContext, googleEmail)
                    }
                    }
                }
            }

            if (!idToken.isNullOrBlank()) {
                Log.i(TAG, "Google ID Token successfully obtained (length=${idToken.length})")
                GoogleAuthResult.Success(
                    idToken = idToken,
                    email = googleEmail,
                    displayName = googleDisplayName,
                    photoUrl = googlePhotoUrl
                )
            } else {
                Log.e(TAG, "No ID Token returned by Google Play Services")
                GoogleAuthResult.Error(
                    "Google Play Services did not provide an ID token. Verify your Web Client ID configuration.",
                    "INVALID_TOKEN"
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "getGoogleIdToken coroutine was cancelled")
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "GetCredentialCancellationException: user dismissed or cancelled Google Sign-In")
            GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google accounts available on device: ${e.message}")
            GoogleAuthResult.FallbackAccountPicker("No Google accounts found in Credential Manager. Opening device account chooser.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException [type=${e.type}]: ${e.message}", e)
            val typeStr = e.type ?: ""
            if (typeStr.contains("USER_CANCELED", ignoreCase = true) || typeStr.contains("CANCELLED", ignoreCase = true)) {
                GoogleAuthResult.Cancelled
            } else if (isDeveloperConsoleError(e)) {
                val currentSha1 = getSigningCertificateSha1(targetContext)
                Log.w(TAG, "Google Credential Manager developer error detected (code 28444 / DEVELOPER_ERROR)")
                Log.w(TAG, "Package Name: ${targetContext.packageName}")
                Log.w(TAG, "Signing SHA-1: $currentSha1")
                Log.w(TAG, "Web Client ID: $webClientId")
                Log.w(TAG, "To enable 1-tap Credential Manager, add this SHA-1 to Google Cloud Console (Credentials -> Android Client ID).")
                Log.w(TAG, "Seamlessly falling back to native Account Chooser / Browser OAuth so user is not blocked.")
                GoogleAuthResult.FallbackAccountPicker("Developer console SHA-1 setup required for 1-tap. Opening device account selector.")
            } else {
                val rawMsg = e.message ?: "Google Sign-In failed"
                GoogleAuthResult.Error(rawMsg, e.type)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Unexpected auth error: ${e.message}", e)
            if (isDeveloperConsoleError(e)) {
                val currentSha1 = getSigningCertificateSha1(targetContext)
                Log.w(TAG, "Developer console error caught in general catch: ${e.message}")
                Log.w(TAG, "Signing SHA-1: $currentSha1")
                GoogleAuthResult.FallbackAccountPicker("Developer console SHA-1 setup required for 1-tap. Opening device account selector.")
            } else {
                GoogleAuthResult.Error(e.message ?: "An unexpected error occurred during Google Auth")
            }
        }
    }

    /**
     * Builds an Android system Account Chooser intent to select any Google account on this device.
     * Works natively across all Android versions without requiring Google Cloud Console SHA-1 registration.
     */
    fun createSystemAccountPickerIntent(): Intent? {
        return try {
            android.accounts.AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create system account picker intent: ${e.message}", e)
            null
        }
    }

    /**
     * Builds a standard OAuth 2.0 authorization URL for Google Identity & Google Drive.
     * Complies with RFC 6749 & OpenID Connect Core specifications.
     */
    fun getOAuth2AuthorizationUri(
        redirectUri: String = "yosan://auth-callback",
        includeDriveScope: Boolean = false,
        responseType: String = "id_token token",
        state: String = UUID.randomUUID().toString(),
        nonce: String = UUID.randomUUID().toString(),
        loginHint: String? = null
    ): Uri {
        val clientId = getWebClientId()
        val scopes = mutableListOf("openid", "email", "profile")
        if (includeDriveScope) {
            scopes.add("https://www.googleapis.com/auth/drive.file")
        }
        val scopeString = scopes.joinToString(" ")

        val builder = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", responseType)
            .appendQueryParameter("scope", scopeString)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", "select_account")

        if (!loginHint.isNullOrBlank()) {
            builder.appendQueryParameter("login_hint", loginHint)
        }
        return builder.build()
    }

    /**
     * Launches the system browser or Chrome Custom Tab for standard Google OAuth 2.0.
     */
    fun launchOAuth2Browser(
        context: Context,
        redirectUri: String = "yosan://auth-callback",
        includeDriveScope: Boolean = false,
        responseType: String = "id_token token",
        loginHint: String? = null
    ): Boolean {
        return try {
            val authUri = getOAuth2AuthorizationUri(
                redirectUri = redirectUri,
                includeDriveScope = includeDriveScope,
                loginHint = loginHint
            )
            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched OAuth 2.0 browser authorization intent: $authUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch OAuth 2.0 browser flow: ${e.message}", e)
            false
        }
    }

    fun getWebClientId(): String {
        return try {
            val field = BuildConfig::class.java.getField("GOOGLE_WEB_CLIENT_ID")
            val raw = field.get(null) as? String
            if (!raw.isNullOrBlank() && !raw.contains("example.apps.googleusercontent.com")) {
                raw
            } else {
                DEFAULT_CLIENT_ID
            }
        } catch (e: Exception) {
            DEFAULT_CLIENT_ID
        }
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "649210682299-k4cl29pr14o0pkblsodq208iajj2ivad.apps.googleusercontent.com"

        fun resolveDisplayName(ctx: Context, email: String? = null): String? {
            try {
                val lastSignedIn = GoogleSignIn.getLastSignedInAccount(ctx)
                if (lastSignedIn != null) {
                    val targetEmail = email?.trim()?.lowercase()
                    val accountEmail = lastSignedIn.email?.trim()?.lowercase()
                    if (targetEmail.isNullOrBlank() || accountEmail == targetEmail) {
                        val gDisplayName = lastSignedIn.displayName?.trim()?.takeIf { it.isNotBlank() && !it.contains("@") }
                        val gFullName = listOfNotNull(lastSignedIn.givenName, lastSignedIn.familyName).joinToString(" ").trim().takeIf { it.isNotBlank() && !it.contains("@") }
                        val resolved = gDisplayName ?: gFullName
                        if (!resolved.isNullOrBlank()) {
                            return resolved
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val cursor = ctx.contentResolver.query(
                    android.provider.ContactsContract.Profile.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Profile.DISPLAY_NAME),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.ContactsContract.Profile.DISPLAY_NAME)
                        if (idx >= 0) {
                            val deviceName = it.getString(idx)?.trim()
                            if (!deviceName.isNullOrBlank() && !deviceName.contains("@")) {
                                return deviceName
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (!email.isNullOrBlank()) {
                val formatted = formatEmailAsReadableName(email)
                if (formatted.isNotBlank()) {
                    return formatted
                }
            }
            return null
        }

        fun formatEmailAsReadableName(email: String): String {
            val prefix = email.trim().lowercase().substringBefore("@")
            val words = prefix.replace(Regex("[._\\-\\d]+"), " ")
                .split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
            return if (words.isNotEmpty()) {
                words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            } else {
                prefix.replaceFirstChar { c -> c.uppercase() }
            }
        }

        fun getSigningCertificateSha1(ctx: Context): String {
            return try {
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    ctx.packageManager.getPackageInfo(
                        ctx.packageName,
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ctx.packageManager.getPackageInfo(
                        ctx.packageName,
                        android.content.pm.PackageManager.GET_SIGNATURES
                    )
                }
                val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                val cert = signatures?.firstOrNull()?.toByteArray() ?: return ""
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val digest = md.digest(cert)
                digest.joinToString(":") { "%02X".format(it) }
            } catch (e: Exception) {
                ""
            }
        }

        fun isDeveloperConsoleError(e: Throwable): Boolean {
            val msg = (e.message ?: "").lowercase()
            val type = ((e as? GetCredentialException)?.type ?: "").lowercase()
            return msg.contains("developer console") ||
                   msg.contains("28444") ||
                   msg.contains("developer_error") ||
                   msg.contains("statuscode=developer_error") ||
                   msg.contains("statuscode=10") ||
                   msg.contains("code 10") ||
                   type.contains("developer_error") ||
                   type.contains("developer")
        }
    }
}

