package com.example.features.auth

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.*
import com.example.features.drive.DriveOperationResult
import com.example.features.drive.GoogleDriveService
import com.example.features.drive.GoogleDriveAuthManager
import com.example.features.drive.DriveAuthTokenResult
import com.example.features.sync.StorageMode
import com.example.features.sync.SyncManager
import com.example.features.sync.SyncStatusInfo
import com.example.utils.BackupService
import com.example.utils.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val isNewUser: Boolean = false) : AuthState()
    object Unauthenticated : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AuthViewModel"
    private val tokenManager = SecureTokenManager(application)
    private val database: AppDatabase
        get() = AppDatabase.getActiveDatabase(getApplication(), viewModelScope)
    private val authRepository: com.example.features.auth.data.AuthRepository
        get() = com.example.features.auth.data.AuthRepository(database)
    private val driveService = GoogleDriveService(application, tokenManager)
    private var _syncManager: SyncManager? = null
    val syncManager: SyncManager
        get() {
            val sm = _syncManager
            if (sm != null) return sm
            val newSm = SyncManager(getApplication(), database, tokenManager)
            _syncManager = newSm
            return newSm
        }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _isNewUser = MutableStateFlow(false)
    val isNewUser: StateFlow<Boolean> = _isNewUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _driveStatus = MutableStateFlow<DriveStatusResponse?>(null)
    val driveStatus: StateFlow<DriveStatusResponse?> = _driveStatus.asStateFlow()

    private val _otpCooldown = MutableStateFlow(0)
    val otpCooldown: StateFlow<Int> = _otpCooldown.asStateFlow()

    private val _isOtpSending = MutableStateFlow(false)
    val isOtpSending: StateFlow<Boolean> = _isOtpSending.asStateFlow()

    private val _isOtpVerifying = MutableStateFlow(false)
    val isOtpVerifying: StateFlow<Boolean> = _isOtpVerifying.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _rememberedAccounts = MutableStateFlow<List<com.example.utils.RememberedAccount>>(emptyList())
    val rememberedAccounts: StateFlow<List<com.example.utils.RememberedAccount>> = _rememberedAccounts.asStateFlow()

    fun refreshRememberedAccounts() {
        val app = getApplication<Application>()
        _rememberedAccounts.value = com.example.utils.AppPreferences(app).getRememberedAccounts()
    }

    val storageMode: StateFlow<StorageMode> = syncManager.storageMode
    val syncStatus: StateFlow<SyncStatusInfo> = syncManager.syncStatus

    private var cooldownJob: Job? = null

    init {
        refreshRememberedAccounts()
        checkSession()
        refreshDriveStatus()
        viewModelScope.launch {
            syncManager.loadInitialStatus()
        }
    }

    /**
     * Checks session validity using OAuth 2.0 / OpenID Connect tokens:
     * 1. If user explicitly logged out, immediately sets Unauthenticated.
     * 2. If valid session exists, verifies token expiration and refreshes if needed.
     * 3. Syncs profile and sets Authenticated state.
     * 4. If offline mode was completed without login, loads local profile.
     */
    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)

                // 1. Check stored session credentials
                val storedAccessToken = tokenManager.getAccessToken()
                val storedRefreshToken = tokenManager.getRefreshToken()
                val userEmail = tokenManager.getUserEmail()
                val googleSub = tokenManager.getGoogleSub()

                // Explicit logout check - check per-user status if logged in, or fallback to global
                val isExplicitlyLoggedOut = if (!userEmail.isNullOrBlank()) {
                    prefs.isLoggedOutForUser(userEmail)
                } else {
                    prefs.isLoggedOut
                }

                if (isExplicitlyLoggedOut) {
                    _user.value = null
                    _authState.value = AuthState.Unauthenticated
                    return@launch
                }

                // If user is not logged in via credentials, check if offline mode was completed
                if (storedAccessToken.isNullOrBlank() || userEmail.isNullOrBlank() || googleSub.isNullOrBlank()) {
                    AppDatabase.setActiveUserKey("guest")
                    if (prefs.isOfflineMode && prefs.isOnboardingCompleted) {
                        val savedProfile = authRepository.getProfileOnce()
                        if (savedProfile != null) {
                            _user.value = buildLocalUser(savedProfile)
                            _authState.value = AuthState.Authenticated(isNewUser = false)
                            return@launch
                        }
                    }
                    _user.value = null
                    _authState.value = AuthState.Unauthenticated
                    return@launch
                }

                // 3. User is logged in: Check token expiration and refresh with backend if needed
                if (tokenManager.isSessionExpired() && !storedRefreshToken.isNullOrBlank()) {
                    Log.i(TAG, "Access token expired. Refreshing OAuth 2.0 session...")
                    try {
                        val authApi = ApiClient.getAuthApi(app)
                        val refreshRes = authApi.refreshSession(RefreshTokenRequest(refreshToken = storedRefreshToken))
                        if (refreshRes.isSuccessful && refreshRes.body() != null) {
                            val auth = refreshRes.body()!!
                            tokenManager.saveTokens(
                                accessToken = auth.accessToken,
                                refreshToken = auth.refreshToken.ifBlank { storedRefreshToken },
                                expiresInSeconds = null
                            )
                            Log.i(TAG, "OAuth 2.0 session refreshed successfully")
                        } else if (refreshRes.code() == 401) {
                            Log.w(TAG, "Refresh token rejected by server (401). Invalidating session.")
                            tokenManager.clearSession()
                            prefs.isLoggedOut = true
                            _user.value = null
                            _authState.value = AuthState.Unauthenticated
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Session refresh network check skipped (offline fallback): ${e.message}")
                    }
                }

                // 4. Load authenticated user profile
                AppDatabase.setActiveUserKey(userEmail)
                val savedProfile = authRepository.getProfileOnce()
                val resolvedName = tokenManager.getUserName() ?: savedProfile?.name ?: prefs.lastUserName.ifBlank { "User" }
                val resolvedPic = tokenManager.getProfilePic() ?: savedProfile?.profilePictureUrl ?: prefs.lastUserProfilePic
                val isVerified = tokenManager.isEmailVerified() || (savedProfile?.emailVerified == true)
                val resolvedPref = tokenManager.getStoragePreference()

                val activeProfile = (savedProfile ?: UserProfileEntity(id = 1)).copy(
                    name = resolvedName,
                    email = userEmail,
                    googleSub = googleSub,
                    profilePictureUrl = resolvedPic,
                    emailVerified = isVerified,
                    storagePreference = resolvedPref,
                    updatedAt = System.currentTimeMillis()
                )
                authRepository.updateProfile(activeProfile)

                syncManager.loadInitialStatus()
                _isEmailVerified.value = isVerified
                _user.value = buildLocalUser(activeProfile)
                _authState.value = AuthState.Authenticated(isNewUser = !activeProfile.isOnboardingCompleted)
            } catch (e: Exception) {
                Log.e(TAG, "checkSession error: ${e.message}", e)
                _user.value = null
                _authState.value = AuthState.Unauthenticated
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Authenticates user using Google ID Token with OAuth 2.0 / OpenID Connect.
     * 1. Obtains ID token from Google Credential Manager (or browser OAuth fallback).
     * 2. Exchanges ID token with Backend Server (/api/auth/google) or falls back to verified offline token.
     * 3. Remembers previous account credentials and primary bank name on this device.
     */
    fun signInWithGoogle(
        context: Context,
        onFallbackPicker: (() -> Unit)? = null,
        onComplete: ((GoogleClaims) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val googleAuthManager = GoogleAuthManager(context)
                when (val result = googleAuthManager.getGoogleIdToken()) {
                    is GoogleAuthResult.RequiresBrowserOAuth -> {
                        Log.i(TAG, "Native credentials require browser OAuth: ${result.message}")
                        launchGoogleOAuth2Flow(context)
                    }
                    is GoogleAuthResult.FallbackAccountPicker -> {
                        Log.i(TAG, "Credential Manager unavailable, delegating to system picker: ${result.message}")
                        _isLoading.value = false
                        _errorMessage.value = null
                        if (onFallbackPicker != null) {
                            onFallbackPicker.invoke()
                        } else {
                            launchGoogleOAuth2Flow(context)
                        }
                        return@launch
                    }
                    is GoogleAuthResult.Success -> {
                        val app = getApplication<Application>()
                        val idToken = result.idToken

                        if (idToken.isBlank()) {
                            _errorMessage.value = "Google Play Services returned an empty ID token."
                            return@launch
                        }

                        val localClaims = decodeGoogleIdTokenLocally(idToken)
                        val email = (result.email ?: localClaims?.email ?: "").trim().lowercase()
                        val name = result.displayName ?: localClaims?.name ?: email.substringBefore("@").ifBlank { "User" }
                        val photoUrl = result.photoUrl ?: localClaims?.picture ?: ""
                        val sub = localClaims?.sub ?: email.ifBlank { "google_user" }

                        var accessToken = ""
                        var refreshToken = ""
                        val expiresIn: Long? = null

                        // 1. Authenticate with Backend Server OAuth 2.0 endpoint (/api/auth/google)
                        try {
                            val authApi = ApiClient.getAuthApi(app)
                            Log.i(TAG, "Exchanging Google ID token with Backend Server (/api/auth/google)...")
                            val serverRes = authApi.authenticateGoogle(GoogleAuthRequest(idToken = idToken))
                            if (serverRes.isSuccessful && serverRes.body() != null) {
                                val body = serverRes.body()!!
                                accessToken = body.accessToken
                                refreshToken = body.refreshToken
                                Log.i(TAG, "Backend Server OAuth authentication successful")
                            } else {
                                Log.w(TAG, "Backend server auth returned ${serverRes.code()}: ${serverRes.message()}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Backend Server offline or unreachable: ${e.message}. Using offline-first verified token.")
                        }

                        // 2. Offline-First fallback token generation if backend is unreachable
                        if (accessToken.isBlank()) {
                            accessToken = idToken
                            refreshToken = "yosan_ref_${com.example.data.local.AppDatabase.sanitizeUserKey(email)}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "")}"
                        }

                        val sessionToken = SecureTokenManager.generateSessionToken(email)
                        tokenManager.saveTokens(accessToken, refreshToken, expiresIn, email = email)
                        tokenManager.saveUserSession(
                            userId = 1L,
                            googleSub = sub,
                            email = email,
                            name = name,
                            profilePicture = photoUrl,
                            emailVerified = true,
                            sessionToken = sessionToken,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresInSeconds = expiresIn
                        )
                        _isEmailVerified.value = true

                        // Switch database to this Google user's isolated partition
                        AppDatabase.setActiveUserKey(email)

                        val prefs = com.example.utils.AppPreferences(app)
                        var activeAccounts = database.accountDao().getAllAccountsList()
                        if (activeAccounts.isEmpty()) {
                            try {
                                val driveAuth = GoogleDriveAuthManager(app)
                                val tokenRes = driveAuth.fetchAccessTokenForEmail(email)
                                if (tokenRes is DriveAuthTokenResult.Success) {
                                    tokenManager.saveDriveTokens(tokenRes.token, driveEmail = email)
                                    val restoreRes = driveService.restoreLatestFromDrive(database, email)
                                    if (restoreRes is DriveOperationResult.Success) {
                                        Log.i(TAG, "Cross-device auto-restored Google Drive data on login for $email")
                                        activeAccounts = database.accountDao().getAllAccountsList()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Drive auto-restore on login note: ${e.message}")
                            }
                        }

                        val primaryBank = activeAccounts.firstOrNull { it.isDefault }?.bankName
                            ?: activeAccounts.firstOrNull()?.bankName
                            ?: prefs.lastBankName

                        val rememberedAccount = com.example.utils.RememberedAccount(
                            email = email,
                            name = name,
                            profilePicture = photoUrl,
                            googleSub = sub,
                            bankName = primaryBank,
                            lastUsedTimestamp = System.currentTimeMillis(),
                            sessionToken = sessionToken
                        )
                        prefs.saveRememberedAccount(rememberedAccount)
                        prefs.isLoggedOut = false

                        // Update Room profile
                        val existing = authRepository.getProfileOnce()
                        val hasBankAccount = activeAccounts.any { it.accountType != com.example.data.local.entity.AccountType.CASH }
                        val isReturningUser = hasBankAccount && (
                            (existing?.isOnboardingCompleted == true) ||
                            prefs.isOnboardingCompletedForUser(email)
                        )

                        val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                            googleSub = sub,
                            name = name,
                            email = email,
                            profilePictureUrl = photoUrl,
                            emailVerified = true,
                            isOnboardingCompleted = isReturningUser,
                            updatedAt = System.currentTimeMillis()
                        )
                        authRepository.updateProfile(updatedProfile)

                        prefs.isOnboardingCompleted = isReturningUser
                        if (isReturningUser) {
                            withContext(Dispatchers.IO) {
                                database.userProfileDao().updateOnboardingCompleted(true)
                            }
                        }

                        syncManager.loadInitialStatus()
                        _user.value = buildLocalUser(updatedProfile)
                        _authState.value = AuthState.Authenticated(
                            isNewUser = !isReturningUser
                        )

                        val claims = GoogleClaims(
                            sub = sub,
                            email = email,
                            name = name,
                            picture = photoUrl
                        )
                        onComplete?.invoke(claims)
                        return@launch
                    }
                    is GoogleAuthResult.Cancelled -> {
                        Log.d(TAG, "Google Sign-In was cancelled by user")
                    }
                    is GoogleAuthResult.Error -> {
                        Log.e(TAG, "Google Sign-In error: ${result.message}")
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in exception: ${e.message}", e)
                _errorMessage.value = "Sign-in error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Launches the system browser or Chrome Custom Tab for standard Google OAuth 2.0.
     */
    fun launchGoogleOAuth2Flow(
        context: Context,
        includeDriveScope: Boolean = false,
        loginHint: String? = null
    ) {
        val googleAuthManager = GoogleAuthManager(context)
        val launched = googleAuthManager.launchOAuth2Browser(
            context = context,
            redirectUri = "yosan://auth-callback",
            includeDriveScope = includeDriveScope,
            loginHint = loginHint
        )
        if (!launched) {
            _errorMessage.value = "Unable to open web browser for Google sign in. Please verify a browser is installed."
        }
    }

    /**
     * Restores active session for a specific remembered account on this device,
     * maintaining that account's profile identity and associated bank name.
     */
    fun restorePreviousAccount(account: com.example.utils.RememberedAccount, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)
                val currentProfile = authRepository.getProfileOnce()

                val email = account.email.ifBlank { prefs.lastUserEmail.ifBlank { currentProfile?.email ?: "" } }
                if (email.isNotBlank()) {
                    AppDatabase.setActiveUserKey(email)
                } else {
                    AppDatabase.setActiveUserKey("guest")
                }
                val name = account.name.ifBlank { prefs.lastUserName.ifBlank { currentProfile?.name ?: "User" } }
                val pic = account.profilePicture.ifBlank { prefs.lastUserProfilePic.ifBlank { currentProfile?.profilePictureUrl ?: "" } }
                val sub = account.googleSub.ifBlank { prefs.lastGoogleSub.ifBlank { currentProfile?.googleSub ?: email.ifBlank { "yosan_user" } } }
                val bank = account.bankName.ifBlank { prefs.lastBankName.ifBlank { "Indian Bank" } }

                val restoredAccessToken = "offline_restored_${System.currentTimeMillis()}"
                val restoredRefreshToken = "offline_refresh_${System.currentTimeMillis()}"
                tokenManager.saveTokens(
                    accessToken = restoredAccessToken,
                    refreshToken = restoredRefreshToken,
                    email = email
                )
                tokenManager.saveUserSession(
                    userId = 1L,
                    googleSub = sub,
                    email = email,
                    name = name,
                    profilePicture = pic,
                    emailVerified = true,
                    accessToken = restoredAccessToken,
                    refreshToken = restoredRefreshToken
                )
                _isEmailVerified.value = true

                val updatedProfile = (currentProfile ?: UserProfileEntity(id = 1)).copy(
                    name = name,
                    email = email,
                    profilePictureUrl = pic,
                    googleSub = sub,
                    isOnboardingCompleted = true,
                    updatedAt = System.currentTimeMillis()
                )
                authRepository.updateProfile(updatedProfile)
                withContext(Dispatchers.IO) {
                    database.userProfileDao().updateOnboardingCompleted(true)
                }

                // Update device preferences and remembered account
                prefs.saveRememberedAccount(
                    account.copy(
                        email = email,
                        name = name,
                        profilePicture = pic,
                        googleSub = sub,
                        bankName = bank,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                )
                prefs.lastBankName = bank
                prefs.isLoggedOut = false
                prefs.isOnboardingCompleted = true
                if (email.isBlank()) {
                    prefs.isOfflineMode = true
                }

                syncManager.loadInitialStatus()
                _user.value = buildLocalUser(updatedProfile)
                _authState.value = AuthState.Authenticated(isNewUser = false)
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore previous account: ${e.message}", e)
                _errorMessage.value = "Failed to restore account: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Re-authenticates and restores active session for the previously remembered account
     * on this device without requiring repetitive onboarding or duplicate account entry.
     */
    fun restorePreviousSession(onComplete: (() -> Unit)? = null) {
        val app = getApplication<Application>()
        val prefs = com.example.utils.AppPreferences(app)
        val latestAccount = prefs.getRememberedAccounts().firstOrNull() ?: com.example.utils.RememberedAccount(
            email = prefs.lastUserEmail,
            name = prefs.lastUserName.ifBlank { "User" },
            profilePicture = prefs.lastUserProfilePic,
            googleSub = prefs.lastGoogleSub,
            bankName = prefs.lastBankName
        )
        restorePreviousAccount(latestAccount, onComplete)
    }

    /**
     * Directly authenticates or switches to a Google Account by email,
     * maintaining the primary bank name and saving to remembered accounts.
     */
    fun signInWithGoogleAccount(
        email: String,
        name: String? = null,
        bankName: String? = null,
        onComplete: ((GoogleClaims) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val cleanEmail = email.trim().lowercase()
                if (cleanEmail.isBlank()) {
                    _errorMessage.value = "Please enter a valid Google Account email."
                    return@launch
                }
                // Switch database partition for this Google account
                AppDatabase.setActiveUserKey(cleanEmail)
                val resolvedName = name?.trim()?.ifBlank { null }
                    ?: cleanEmail.substringBefore("@").replace(".", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        .ifBlank { "User" }
                val sub = "google_${cleanEmail.replace("@", "_").replace(".", "_")}"

                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)
                val existingAccounts = database.accountDao().getAllAccountsList()
                val primaryBank = bankName?.ifBlank { null }
                    ?: existingAccounts.firstOrNull { it.isDefault }?.bankName
                    ?: existingAccounts.firstOrNull()?.bankName
                    ?: prefs.lastBankName

                val sessionToken = SecureTokenManager.generateSessionToken(cleanEmail)
                val dynamicAccessToken = "yosan_tok_${com.example.data.local.AppDatabase.sanitizeUserKey(cleanEmail)}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "")}"
                val dynamicRefreshToken = "yosan_ref_${com.example.data.local.AppDatabase.sanitizeUserKey(cleanEmail)}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "")}"

                tokenManager.saveTokens(
                    accessToken = dynamicAccessToken,
                    refreshToken = dynamicRefreshToken,
                    email = cleanEmail
                )
                tokenManager.saveUserSession(
                    userId = 1L,
                    googleSub = sub,
                    email = cleanEmail,
                    name = resolvedName,
                    profilePicture = "",
                    emailVerified = true,
                    sessionToken = sessionToken,
                    accessToken = dynamicAccessToken,
                    refreshToken = dynamicRefreshToken
                )
                _isEmailVerified.value = true

                val remembered = com.example.utils.RememberedAccount(
                    email = cleanEmail,
                    name = resolvedName,
                    profilePicture = "",
                    googleSub = sub,
                    bankName = primaryBank,
                    lastUsedTimestamp = System.currentTimeMillis(),
                    sessionToken = sessionToken
                )
                prefs.saveRememberedAccount(remembered)
                prefs.isLoggedOut = false

                val existing = authRepository.getProfileOnce()
                val hasBankAccount = existingAccounts.any { it.accountType != com.example.data.local.entity.AccountType.CASH }
                val isReturningUser = hasBankAccount && (
                    (existing?.isOnboardingCompleted == true) ||
                    prefs.isOnboardingCompletedForUser(cleanEmail)
                )
                prefs.isOnboardingCompleted = isReturningUser

                val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                    googleSub = sub,
                    name = resolvedName,
                    email = cleanEmail,
                    emailVerified = true,
                    isOnboardingCompleted = isReturningUser,
                    updatedAt = System.currentTimeMillis()
                )
                authRepository.updateProfile(updatedProfile)
                if (isReturningUser) {
                    withContext(Dispatchers.IO) {
                        database.userProfileDao().updateOnboardingCompleted(true)
                    }
                }

                syncManager.loadInitialStatus()
                _user.value = buildLocalUser(updatedProfile)
                _authState.value = AuthState.Authenticated(isNewUser = !isReturningUser)

                val claims = GoogleClaims(
                    sub = sub,
                    email = cleanEmail,
                    name = resolvedName,
                    picture = ""
                )
                onComplete?.invoke(claims)
            } catch (e: Exception) {
                Log.e(TAG, "signInWithGoogleAccount error: ${e.message}", e)
                _errorMessage.value = "Sign in failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handles OAuth redirects via deep links (yosan://auth-callback).
     * Extracts tokens from fragment (#access_token=...&id_token=...) or exchanges authorization code (?code=...).
     */
    fun handleDeepLink(uri: Uri, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                val fragment = uri.fragment
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")

                if (!error.isNullOrBlank()) {
                    _errorMessage.value = error
                    onComplete?.invoke(false, error)
                    return@launch
                }

                // 1. Implicit tokens in fragment: #access_token=...&id_token=...
                if (!fragment.isNullOrBlank() && (fragment.contains("access_token=") || fragment.contains("id_token="))) {
                    val params = fragment.split("&").associate {
                        val parts = it.split("=")
                        if (parts.size >= 2) parts[0] to parts[1] else parts[0] to ""
                    }
                    val accessToken = params["access_token"] ?: ""
                    val refreshToken = params["refresh_token"] ?: ""
                    val expiresIn = params["expires_in"]?.toLongOrNull()
                    val idToken = params["id_token"] ?: ""

                    var email = ""
                    var name = "User"
                    var avatar = ""
                    var uid = ""

                    if (idToken.isNotBlank()) {
                        val claims = decodeGoogleIdTokenLocally(idToken)
                        if (claims != null) {
                            email = claims.email
                            name = claims.name
                            avatar = claims.picture
                            uid = claims.sub
                        }
                    }

                    if (params["scope"]?.contains("drive") == true || uri.getQueryParameter("scope")?.contains("drive") == true) {
                        tokenManager.saveDriveTokens(accessToken, refreshToken, expiresIn, email)
                    }

                    if (accessToken.isNotBlank() || idToken.isNotBlank()) {
                        val sessionToken = SecureTokenManager.generateSessionToken(email)
                        val effectiveAccess = accessToken.ifBlank { idToken }
                        tokenManager.saveTokens(effectiveAccess, refreshToken, expiresIn, email = email)
                        tokenManager.saveUserSession(
                            userId = 1L,
                            googleSub = uid.ifBlank { email.ifBlank { "google_user" } },
                            email = email,
                            name = name,
                            profilePicture = avatar,
                            emailVerified = true,
                            sessionToken = sessionToken,
                            accessToken = effectiveAccess,
                            refreshToken = refreshToken,
                            expiresInSeconds = expiresIn
                        )
                        _isEmailVerified.value = true

                        // Switch database partition to this user
                        AppDatabase.setActiveUserKey(email)

                        val prefs = com.example.utils.AppPreferences(app)
                        val existing = authRepository.getProfileOnce()
                        val existingAccounts = database.accountDao().getAllAccountsList()
                        val hasBankAccount = existingAccounts.any { it.accountType != com.example.data.local.entity.AccountType.CASH }
                        val isReturningUser = hasBankAccount && (
                            (existing?.isOnboardingCompleted == true) ||
                            prefs.isOnboardingCompletedForUser(email)
                        )

                        if (email.isNotBlank()) prefs.lastUserEmail = email
                        if (name.isNotBlank()) prefs.lastUserName = name
                        if (avatar.isNotBlank()) prefs.lastUserProfilePic = avatar
                        if (uid.isNotBlank()) prefs.lastGoogleSub = uid
                        prefs.isLoggedOut = false

                        val primaryBank = existingAccounts.firstOrNull { it.isDefault }?.bankName
                            ?: existingAccounts.firstOrNull()?.bankName
                            ?: prefs.lastBankName.ifBlank { "Indian Bank" }
                        prefs.lastBankName = primaryBank

                        val remembered = com.example.utils.RememberedAccount(
                            email = email,
                            name = name,
                            profilePicture = avatar,
                            googleSub = uid,
                            bankName = primaryBank,
                            lastUsedTimestamp = System.currentTimeMillis(),
                            sessionToken = sessionToken
                        )
                        prefs.saveRememberedAccount(remembered)

                        val updated = (existing ?: UserProfileEntity(id = 1)).copy(
                            googleSub = uid.ifBlank { email.ifBlank { "google_user" } },
                            email = email,
                            name = name,
                            profilePictureUrl = avatar,
                            emailVerified = true,
                            isOnboardingCompleted = isReturningUser || existing?.isOnboardingCompleted == true,
                            updatedAt = System.currentTimeMillis()
                        )
                        authRepository.updateProfile(updated)

                        if (isReturningUser) {
                            prefs.isOnboardingCompleted = true
                            withContext(Dispatchers.IO) {
                                database.userProfileDao().updateOnboardingCompleted(true)
                            }
                        }

                        syncManager.loadInitialStatus()
                        _user.value = buildLocalUser(updated)
                        _authState.value = AuthState.Authenticated(isNewUser = !isReturningUser)
                        onComplete?.invoke(true, null)
                        return@launch
                    }
                }

                // 2. PKCE Authorization Code (?code=...)
                if (!code.isNullOrBlank()) {
                    try {
                        val authApi = ApiClient.getAuthApi(app)
                        val response = authApi.authenticateGoogle(GoogleAuthRequest(idToken = code))
                        if (response.isSuccessful && response.body() != null) {
                            val body = response.body()!!
                            val user = body.user
                            val sessionToken = SecureTokenManager.generateSessionToken(user.email)
                            tokenManager.saveTokens(body.accessToken, body.refreshToken, null, email = user.email)
                            tokenManager.saveUserSession(
                                userId = user.id,
                                googleSub = user.googleSub,
                                email = user.email,
                                name = user.name,
                                profilePicture = user.profilePictureUrl ?: "",
                                emailVerified = user.emailVerified,
                                sessionToken = sessionToken,
                                accessToken = body.accessToken,
                                refreshToken = body.refreshToken
                            )
                            _isEmailVerified.value = user.emailVerified

                            val prefs = com.example.utils.AppPreferences(app)
                            prefs.isLoggedOut = false
                            if (user.email.isNotBlank()) prefs.lastUserEmail = user.email
                            if (user.name.isNotBlank()) prefs.lastUserName = user.name

                            val existingAccounts = database.accountDao().getAllAccountsList()
                            val primaryBank = existingAccounts.firstOrNull { it.isDefault }?.bankName
                                ?: existingAccounts.firstOrNull()?.bankName
                                ?: prefs.lastBankName.ifBlank { "Indian Bank" }
                            prefs.lastBankName = primaryBank

                            val remembered = com.example.utils.RememberedAccount(
                                email = user.email,
                                name = user.name.ifBlank { "User" },
                                profilePicture = user.profilePictureUrl ?: "",
                                googleSub = user.googleSub,
                                bankName = primaryBank,
                                lastUsedTimestamp = System.currentTimeMillis()
                            )
                            prefs.saveRememberedAccount(remembered)

                            val existing = authRepository.getProfileOnce()
                            val updated = (existing ?: UserProfileEntity(id = 1)).copy(
                                googleSub = user.googleSub,
                                email = user.email,
                                name = user.name,
                                profilePictureUrl = user.profilePictureUrl ?: "",
                                emailVerified = user.emailVerified,
                                updatedAt = System.currentTimeMillis()
                            )
                            authRepository.updateProfile(updated)

                            syncManager.loadInitialStatus()
                            _user.value = buildLocalUser(updated)
                            _authState.value = AuthState.Authenticated(isNewUser = !updated.isOnboardingCompleted)
                            onComplete?.invoke(true, null)
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Code exchange exception: ${e.message}", e)
                    }
                }

                onComplete?.invoke(false, "Unrecognized callback parameter")
            } catch (e: Exception) {
                Log.e(TAG, "handleDeepLink error: ${e.message}", e)
                val err = e.localizedMessage ?: "Failed to process authentication redirect"
                _errorMessage.value = err
                onComplete?.invoke(false, err)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Storage Preferences Management ──────────────────────────────────────

    fun setStoragePreference(mode: StorageMode, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                syncManager.setStorageMode(mode)
                val current = authRepository.getProfileOnce()
                if (current != null) {
                    val updated = current.copy(storagePreference = mode.key, updatedAt = System.currentTimeMillis())
                    authRepository.updateProfile(updated)
                    _user.value = buildLocalUser(updated)
                }

                onResult?.invoke(true, "Storage mode set to ${mode.displayName}")
            } catch (e: Exception) {
                onResult?.invoke(false, "Failed to update storage preference: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Google Drive Integration ────────────────────────────────────────────

    fun connectGoogleDriveWithValidation(
        selectedDriveEmail: String,
        authCode: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null,
        allowDifferentAccount: Boolean = false,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val localProfile = authRepository.getProfileOnce()
                val registeredEmail = localProfile?.email?.trim()?.lowercase() ?: _user.value?.email?.trim()?.lowercase() ?: ""
                val cleanDriveEmail = selectedDriveEmail.trim().lowercase()

                if (!allowDifferentAccount && registeredEmail.isNotEmpty() && cleanDriveEmail.isNotEmpty() && registeredEmail != cleanDriveEmail) {
                    val errorMsg = "Email Mismatch: Selected Google Drive account ($cleanDriveEmail) does not match your registered account ($registeredEmail)."
                    _errorMessage.value = errorMsg
                    onResult?.invoke(false, errorMsg)
                    _isLoading.value = false
                    return@launch
                }

                val effectiveToken = accessToken ?: tokenManager.getDriveAccessToken()
                if (!effectiveToken.isNullOrBlank()) {
                    tokenManager.saveDriveTokens(
                        accessToken = effectiveToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresInSeconds,
                        driveEmail = cleanDriveEmail
                    )
                }

                // Activate Google Drive storage mode in SyncManager
                syncManager.setStorageMode(com.example.features.sync.StorageMode.GOOGLE_DRIVE)

                // Immediately verify and provision Google Drive folders ("Yosan-Backup" & "versions")
                val tokenForFolder = effectiveToken ?: driveService.getValidDriveToken()
                if (!tokenForFolder.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        driveService.ensureDriveFoldersExist(tokenForFolder)
                    }
                }

                try {
                    val api = ApiClient.getAuthApi(getApplication())
                    api.connectDrive(
                        DriveConnectRequest(
                            authCode = authCode ?: effectiveToken ?: "direct_drive_token",
                            driveEmail = cleanDriveEmail
                        )
                    )
                } catch (_: Exception) {}

                refreshDriveStatus()
                onResult?.invoke(true, "Google Drive connected and folders initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect Google Drive: ${e.message}", e)
                onResult?.invoke(false, e.localizedMessage ?: "Failed to connect Google Drive")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshDriveStatus() {
        viewModelScope.launch {
            try {
                if (tokenManager.isDriveConnected()) {
                    val driveEmail = tokenManager.getDriveEmail() ?: tokenManager.getUserEmail()
                    val lastBackupStr = database.syncDao().getMetadataValue("last_backup_timestamp")
                    val versionStr = database.syncDao().getMetadataValue("current_backup_version")

                    _driveStatus.value = DriveStatusResponse(
                        isConnected = true,
                        driveEmail = driveEmail,
                        lastBackupAt = lastBackupStr?.toLongOrNull(),
                        currentBackupVersion = versionStr?.toIntOrNull() ?: 1
                    )
                    return@launch
                }

                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getDriveStatus()
                if (response.isSuccessful && response.body() != null) {
                    _driveStatus.value = response.body()
                } else {
                    _driveStatus.value = DriveStatusResponse(isConnected = false)
                }
            } catch (_: Exception) {
                if (tokenManager.isDriveConnected()) {
                    _driveStatus.value = DriveStatusResponse(
                        isConnected = true,
                        driveEmail = tokenManager.getDriveEmail()
                    )
                } else {
                    _driveStatus.value = DriveStatusResponse(isConnected = false)
                }
            }
        }
    }

    fun backupNow(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            syncManager.triggerSync { success, message ->
                _isLoading.value = false
                refreshDriveStatus()
                onResult?.invoke(success, message)
            }
        }
    }

    fun getBackupHistory(onResult: (Boolean, List<DriveBackupVersionDto>, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getBackupHistory()
                if (response.isSuccessful && response.body() != null) {
                    onResult(true, response.body()!!.backups, null)
                } else {
                    val versionStr = database.syncDao().getMetadataValue("current_backup_version")
                    val v = versionStr?.toIntOrNull() ?: 1
                    val localList = listOf(
                        DriveBackupVersionDto(
                            version = v,
                            fileName = "detailsaboutbackup.json",
                            fileId = database.syncDao().getMetadataValue("current_backup_file_id") ?: "local_current",
                            isCurrent = true,
                            createdTime = database.syncDao().getMetadataValue("last_backup_timestamp")?.let { ts ->
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                }.format(java.util.Date(ts.toLongOrNull() ?: System.currentTimeMillis()))
                            } ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(java.util.Date())
                        )
                    )
                    onResult(true, localList, null)
                }
            } catch (e: Exception) {
                onResult(false, emptyList(), e.localizedMessage ?: "Failed to load backup history")
            }
        }
    }

    fun restoreBackupVersion(version: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getBackupVersion(version)
                if (response.isSuccessful && response.body() != null && response.body()!!.backupJson.isNotBlank()) {
                    val jsonStr = response.body()!!.backupJson
                    val restoreResult = withContext(Dispatchers.IO) {
                        BackupService.restoreBackupJson(jsonStr, database, clearExisting = true)
                    }
                    if (restoreResult.success) {
                        database.syncDao().setMetadata(
                            com.example.data.local.entity.SyncMetadataEntity(
                                key = "current_backup_version",
                                value = version.toString(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onResult(true, "Restored snapshot v$version successfully! ${restoreResult.message}")
                    } else {
                        onResult(false, restoreResult.message)
                    }
                } else {
                    onResult(false, "Could not download snapshot for version $version")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Failed to restore version snapshot")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreLatestBackup(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Try direct Drive download if Drive token is active
                if (driveService.isConnected() && !tokenManager.getDriveAccessToken().isNullOrBlank()) {
                    val driveRes = driveService.restoreLatestFromDrive(database, tokenManager.getUserEmail())
                    when (driveRes) {
                        is DriveOperationResult.Success -> {
                            onResult?.invoke(true, driveRes.message)
                            return@launch
                        }
                        is DriveOperationResult.Error -> {
                            Log.w(TAG, "Direct restore note: ${driveRes.message}")
                        }
                    }
                }

                // 2. Fallback to backend route
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getLatestBackup()
                if (response.isSuccessful && response.body()?.hasBackup == true && !response.body()?.backupJson.isNullOrBlank()) {
                    val jsonStr = response.body()!!.backupJson!!
                    val restoreResult = withContext(Dispatchers.IO) {
                        BackupService.restoreBackupJson(jsonStr, database, clearExisting = true)
                    }
                    if (restoreResult.success) {
                        onResult?.invoke(true, "Latest Google Drive backup restored successfully! ${restoreResult.message}")
                    } else {
                        onResult?.invoke(false, restoreResult.message)
                    }
                } else {
                    onResult?.invoke(false, "No backup found in your Google Drive.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, e.localizedMessage ?: "Failed to restore backup")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun disconnectGoogleDrive(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                tokenManager.clearDriveTokens()
                val api = ApiClient.getAuthApi(getApplication())
                api.disconnectDrive()
            } catch (_: Exception) {}
            _driveStatus.value = DriveStatusResponse(isConnected = false)
            onResult?.invoke(true, "Google Drive disconnected")
        }
    }

    fun checkDriveOrCloudBackup(email: String, onResult: (Boolean, LatestBackupResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val driveAuth = GoogleDriveAuthManager(app)
                val driveToken = when (val res = driveAuth.fetchAccessTokenForEmail(email)) {
                    is DriveAuthTokenResult.Success -> {
                        tokenManager.saveDriveTokens(res.token, driveEmail = email)
                        res.token
                    }
                    else -> null
                }
                if (!driveToken.isNullOrBlank()) {
                    val json = driveService.getLatestBackupContent(driveToken, email)
                    if (!json.isNullOrBlank()) {
                        val jsonObj = org.json.JSONObject(json)
                        val accountsCount = jsonObj.optJSONArray("accounts")?.length() ?: 0
                        val txCount = jsonObj.optJSONArray("transactions")?.length() ?: 0
                        val dateStr = jsonObj.optString("created_at", "Recent")
                        onResult(true, LatestBackupResponse(
                            hasBackup = true,
                            backupDate = dateStr,
                            accountsCount = accountsCount,
                            transactionsCount = txCount,
                            backupJson = json
                        ))
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkDriveOrCloudBackup direct check failed: ${e.message}")
            }
            checkForExistingBackup(onResult)
        }
    }

    fun checkForExistingBackup(onResult: (Boolean, LatestBackupResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getLatestBackup()
                if (response.isSuccessful && response.body()?.hasBackup == true) {
                    onResult(true, response.body())
                } else {
                    onResult(false, null)
                }
            } catch (_: Exception) {
                onResult(false, null)
            }
        }
    }

    fun setStorageMode(mode: StorageMode) {
        viewModelScope.launch {
            syncManager.setStorageMode(mode)
        }
    }

    fun syncWithDriveIfNewer(onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val restored = syncManager.syncWithDriveIfNewer(tokenManager.getUserEmail())
            onResult?.invoke(restored)
        }
    }

    // ─── Email OTP Authentication & Verification ──────────────────────────────

    fun sendOtp(email: String, purpose: String = "LOGIN", onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            val err = "Please enter a valid email address."
            _errorMessage.value = err
            onResult?.invoke(false, err)
            return
        }

        viewModelScope.launch {
            _isOtpSending.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                val api = ApiClient.getAuthApi(app)
                val response = api.sendOtp(SendOtpRequest(email = cleanEmail, purpose = purpose))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    startOtpCooldown(body.cooldownSeconds)
                    onResult?.invoke(true, "Verification code sent to $cleanEmail.")
                } else {
                    val err = parseErrorMessage(response.errorBody()?.string()) ?: "Failed to send verification code."
                    _errorMessage.value = err
                    onResult?.invoke(false, err)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: Unable to reach verification server."
                onResult?.invoke(false, _errorMessage.value)
            } finally {
                _isOtpSending.value = false
            }
        }
    }

    fun verifyOtp(email: String, otp: String, name: String = "", purpose: String = "LOGIN", onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6) {
            val err = "Please enter the 6-digit verification code."
            _errorMessage.value = err
            onResult?.invoke(false, err)
            return
        }

        viewModelScope.launch {
            _isOtpVerifying.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                val api = ApiClient.getAuthApi(app)
                val response = api.verifyOtp(VerifyOtpRequest(email = cleanEmail, otp = cleanOtp, purpose = purpose, name = name.ifBlank { null }))
                if (response.isSuccessful && response.body() != null) {
                    val authRes = response.body()!!
                    val sessionToken = SecureTokenManager.generateSessionToken(cleanEmail)
                    tokenManager.saveTokens(authRes.accessToken, authRes.refreshToken, email = authRes.user.email)
                    tokenManager.saveUserSession(
                        userId = authRes.user.id,
                        googleSub = authRes.user.googleSub,
                        email = authRes.user.email,
                        name = authRes.user.name,
                        profilePicture = authRes.user.profilePictureUrl ?: "",
                        emailVerified = true,
                        sessionToken = sessionToken,
                        accessToken = authRes.accessToken,
                        refreshToken = authRes.refreshToken
                    )
                    _isEmailVerified.value = true

                    // Switch database partition to this user BEFORE reading accounts or profile
                    AppDatabase.setActiveUserKey(authRes.user.email)

                    val prefs = com.example.utils.AppPreferences(app)
                    prefs.isLoggedOut = false
                    if (authRes.user.email.isNotBlank()) prefs.lastUserEmail = authRes.user.email
                    if (authRes.user.name.isNotBlank()) prefs.lastUserName = authRes.user.name

                    val existingAccounts = database.accountDao().getAllAccountsList()
                    val primaryBank = existingAccounts.firstOrNull { it.isDefault }?.bankName
                        ?: existingAccounts.firstOrNull()?.bankName
                        ?: prefs.lastBankName.ifBlank { "Indian Bank" }
                    prefs.lastBankName = primaryBank

                    val remembered = com.example.utils.RememberedAccount(
                        email = authRes.user.email,
                        name = authRes.user.name.ifBlank { "User" },
                        profilePicture = authRes.user.profilePictureUrl ?: "",
                        googleSub = authRes.user.googleSub,
                        bankName = primaryBank,
                        lastUsedTimestamp = System.currentTimeMillis(),
                        sessionToken = sessionToken
                    )
                    prefs.saveRememberedAccount(remembered)

                    val existing = authRepository.getProfileOnce()
                    val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                        googleSub = authRes.user.googleSub,
                        email = authRes.user.email,
                        name = authRes.user.name.ifBlank { existing?.name ?: "User" },
                        profilePictureUrl = authRes.user.profilePictureUrl ?: existing?.profilePictureUrl ?: "",
                        emailVerified = true,
                        isOnboardingCompleted = !authRes.isNewUser || existing?.isOnboardingCompleted == true,
                        updatedAt = System.currentTimeMillis()
                    )
                    authRepository.updateProfile(updatedProfile)

                    syncManager.loadInitialStatus()
                    _user.value = authRes.user
                    _authState.value = AuthState.Authenticated(isNewUser = !updatedProfile.isOnboardingCompleted)
                    onResult?.invoke(true, null)
                } else {
                    val err = parseErrorMessage(response.errorBody()?.string()) ?: "Verification failed. Check your code."
                    _errorMessage.value = err
                    onResult?.invoke(false, err)
                }
            } catch (e: Exception) {
                val err = "Network error: Unable to verify code with server."
                _errorMessage.value = err
                onResult?.invoke(false, err)
            } finally {
                _isOtpVerifying.value = false
            }
        }
    }

    fun requestEmailChange(newEmail: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = newEmail.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onResult?.invoke(false, "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _isOtpSending.value = true
            try {
                val app = getApplication<Application>()
                val api = ApiClient.getAuthApi(app)
                val response = api.requestEmailChange(RequestEmailChangeRequest(newEmail = cleanEmail))
                if (response.isSuccessful && response.body()?.success == true) {
                    startOtpCooldown(60)
                    onResult?.invoke(true, "Verification code sent to $cleanEmail. Check your inbox.")
                } else {
                    onResult?.invoke(false, parseErrorMessage(response.errorBody()?.string()) ?: "Failed to request email change.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, "Network error: Unable to request email change.")
            } finally {
                _isOtpSending.value = false
            }
        }
    }

    fun verifyEmailChange(newEmail: String, otp: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = newEmail.trim().lowercase()
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6) {
            onResult?.invoke(false, "Verification code must be 6 digits.")
            return
        }

        viewModelScope.launch {
            _isOtpVerifying.value = true
            try {
                val app = getApplication<Application>()
                val api = ApiClient.getAuthApi(app)
                val response = api.verifyEmailChange(VerifyEmailChangeRequest(newEmail = cleanEmail, otp = cleanOtp))
                if (response.isSuccessful && response.body()?.success == true) {
                    val current = authRepository.getProfileOnce()
                    if (current != null) {
                        val updated = current.copy(email = cleanEmail, emailVerified = true, updatedAt = System.currentTimeMillis())
                        authRepository.updateProfile(updated)
                        _user.value = buildLocalUser(updated)
                    }
                    tokenManager.saveUserSession(
                        userId = tokenManager.getUserId(),
                        googleSub = tokenManager.getGoogleSub() ?: "",
                        email = cleanEmail,
                        name = tokenManager.getUserName() ?: "User",
                        profilePicture = tokenManager.getProfilePic() ?: "",
                        emailVerified = true
                    )
                    onResult?.invoke(true, "Email successfully updated to $cleanEmail")
                } else {
                    onResult?.invoke(false, parseErrorMessage(response.errorBody()?.string()) ?: "Verification failed.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, "Network error: Unable to verify code with server.")
            } finally {
                _isOtpVerifying.value = false
            }
        }
    }

    fun completeOnboarding(customName: String? = null) {
        _isNewUser.value = false
        _authState.value = AuthState.Authenticated(isNewUser = false)
        val prefs = com.example.utils.AppPreferences(getApplication())
        prefs.isOnboardingCompleted = true
        prefs.isLoggedOut = false
        val email = prefs.lastUserEmail.ifBlank { "guest" }
        prefs.setOnboardingCompletedForUser(email, true)
        viewModelScope.launch(Dispatchers.IO) {
            val profile = database.userProfileDao().getUserProfileOnce()
            if (profile != null) {
                database.userProfileDao().insertOrUpdateProfile(
                    profile.copy(
                        name = customName ?: profile.name,
                        isOnboardingCompleted = true
                    )
                )
            } else {
                database.userProfileDao().insertOrUpdateProfile(
                    UserProfileEntity(
                        id = 1,
                        name = customName ?: "User",
                        isOnboardingCompleted = true
                    )
                )
            }
            val active = database.userProfileDao().getUserProfileOnce()
            if (active != null) {
                _user.value = buildLocalUser(active)
            }
        }
    }

    /**
     * Logout: revokes backend session, clears encrypted token storage, and
     * sets unauthenticated state. Preserves financial database and remembers
     * previous account identity (email, name, picture, bank) for seamless return.
     */
    fun logout(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val token = tokenManager.getAccessToken()
            val refreshToken = tokenManager.getRefreshToken()

            val prefs = com.example.utils.AppPreferences(app)
            val currentProfile = authRepository.getProfileOnce()
            val accounts = database.accountDao().getAllAccountsList()

            // Hold and remember previous account details and primary bank name on this device
            val email = tokenManager.getUserEmail() ?: currentProfile?.email ?: prefs.lastUserEmail
            val name = tokenManager.getUserName() ?: currentProfile?.name ?: prefs.lastUserName
            val pic = tokenManager.getProfilePic() ?: currentProfile?.profilePictureUrl ?: prefs.lastUserProfilePic
            val sub = tokenManager.getGoogleSub() ?: currentProfile?.googleSub ?: prefs.lastGoogleSub
            val primaryBank = accounts.firstOrNull { it.isDefault }?.bankName
                ?: accounts.firstOrNull()?.bankName
                ?: prefs.lastBankName.ifBlank { "Indian Bank" }
            prefs.lastBankName = primaryBank

            val currentSessionToken = tokenManager.getSessionToken() ?: SecureTokenManager.generateSessionToken(email)
            val remembered = com.example.utils.RememberedAccount(
                email = email,
                name = name,
                profilePicture = pic,
                googleSub = sub,
                bankName = primaryBank,
                lastUsedTimestamp = System.currentTimeMillis(),
                sessionToken = currentSessionToken
            )
            prefs.saveRememberedAccount(remembered)

            // Revoke Backend server session if online
            if (!token.isNullOrBlank() || !refreshToken.isNullOrBlank()) {
                try {
                    val authApi = ApiClient.getAuthApi(app)
                    authApi.logout(LogoutRequest(refreshToken))
                } catch (_: Exception) {}
            }

            // Close active user's isolated database cleanly and switch active partition to guest
            val logoutEmail = email.takeIf { !it.isNullOrBlank() } ?: _user.value?.email ?: tokenManager.getUserEmail()
            if (!logoutEmail.isNullOrBlank()) {
                AppDatabase.closeDatabaseForUser(app, logoutEmail)
                prefs.setLoggedOutForUser(logoutEmail, true)
            } else {
                prefs.isLoggedOut = true
            }
            AppDatabase.setActiveUserKey("guest")
            prefs.isOfflineMode = false

            // Clear active session slot while keeping remembered user session for account switching
            tokenManager.clearActiveSession(preserveSavedSession = true)
            _driveStatus.value = DriveStatusResponse(isConnected = false)
            refreshDriveStatus()
            prefs.clearSession(preserveDeviceIdentity = true)
            _user.value = null
            _isNewUser.value = false
            _isEmailVerified.value = false
            _authState.value = AuthState.Unauthenticated
            refreshRememberedAccounts()
            onComplete?.invoke()
        }
    }

    /**
     * Switches the active session to another user account on this device.
     * Restores the selected user's tokens, session, and isolated database cleanly.
     * Prevents previous user details from showing in the switched account.
     */
    fun switchAccount(email: String, onComplete: (() -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        val isTargetOffline = cleanEmail.isBlank() || cleanEmail == "guest" || cleanEmail == "offline"

        viewModelScope.launch {
            _isLoading.value = true
            _syncManager = null
            var completed = false
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)

                // 1. Close current database partition cleanly before switching
                val currentEmail = tokenManager.getUserEmail()?.trim()?.lowercase()
                if (!currentEmail.isNullOrBlank() && !currentEmail.equals(cleanEmail, ignoreCase = true)) {
                    AppDatabase.closeDatabaseForUser(app, currentEmail)
                }

                if (isTargetOffline) {
                    tokenManager.clearActiveSession(preserveSavedSession = true)
                    _driveStatus.value = DriveStatusResponse(isConnected = false)
                    AppDatabase.setActiveUserKey("guest")

                    val guestDb = AppDatabase.getDatabaseForUser(app, viewModelScope, "guest")
                    val guestProfile = guestDb.userProfileDao().getUserProfileOnce()
                    val guestAccounts = guestDb.accountDao().getAllAccountsList()

                    val resolvedName = guestProfile?.name?.ifBlank { null }
                        ?: prefs.lastUserName.takeIf { prefs.isOfflineMode && it.isNotBlank() }
                        ?: "Offline User"
                    val primaryBank = guestAccounts.firstOrNull { it.isDefault }?.bankName
                        ?: guestAccounts.firstOrNull()?.bankName
                        ?: prefs.lastBankName.ifBlank { "Indian Bank" }

                    val activeProfile = (guestProfile ?: UserProfileEntity(id = 1)).copy(
                        email = "",
                        name = resolvedName,
                        profilePictureUrl = "",
                        isOnboardingCompleted = guestAccounts.isNotEmpty() || guestProfile?.isOnboardingCompleted == true,
                        updatedAt = System.currentTimeMillis()
                    )
                    guestDb.userProfileDao().insertOrUpdateProfile(activeProfile)

                    prefs.isLoggedOut = false
                    prefs.setLoggedOutForUser("guest", false)
                    prefs.isOfflineMode = true
                    prefs.lastUserEmail = ""
                    prefs.lastUserName = resolvedName
                    prefs.lastBankName = primaryBank
                    prefs.isOnboardingCompleted = activeProfile.isOnboardingCompleted
                    prefs.setOnboardingCompletedForUser("guest", activeProfile.isOnboardingCompleted)

                    val remembered = com.example.utils.RememberedAccount(
                        email = "",
                        name = resolvedName,
                        profilePicture = "",
                        googleSub = "",
                        bankName = primaryBank,
                        lastUsedTimestamp = System.currentTimeMillis(),
                        sessionToken = ""
                    )
                    prefs.saveRememberedAccount(remembered)

                    _isEmailVerified.value = true
                    _user.value = buildLocalUser(activeProfile)
                    _authState.value = AuthState.Authenticated(isNewUser = !activeProfile.isOnboardingCompleted)
                    refreshDriveStatus()
                    syncManager.loadInitialStatus()

                    completed = true
                    onComplete?.invoke()
                    return@launch
                }

                // 2. Switch session tokens and active user identity in SecureTokenManager
                val switched = tokenManager.switchUserSession(cleanEmail)
                if (!switched) {
                    Log.w(TAG, "No stored session found for $cleanEmail. Initializing session.")
                    val sessionToken = SecureTokenManager.generateSessionToken(cleanEmail)
                    tokenManager.saveUserSession(
                        userId = 1L,
                        googleSub = "user_${AppDatabase.sanitizeUserKey(cleanEmail)}",
                        email = cleanEmail,
                        name = cleanEmail.substringBefore("@").replace(".", " ")
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            .ifBlank { "User" },
                        profilePicture = "",
                        emailVerified = true,
                        sessionToken = sessionToken
                    )
                }

                // 3. Set active database partition to target user
                AppDatabase.setActiveUserKey(cleanEmail)
                _driveStatus.value = DriveStatusResponse(isConnected = false)

                // 4. Load target user's isolated profile
                val userDb = AppDatabase.getDatabaseForUser(app, viewModelScope, cleanEmail)
                val targetProfile = userDb.userProfileDao().getUserProfileOnce()
                val targetAccounts = userDb.accountDao().getAllAccountsList()

                val resolvedName = tokenManager.getUserName()
                    ?: targetProfile?.name
                    ?: cleanEmail.substringBefore("@")
                val resolvedPic = tokenManager.getProfilePic()
                    ?: targetProfile?.profilePictureUrl ?: ""

                val activeProfile = (targetProfile ?: UserProfileEntity(id = 1)).copy(
                    email = cleanEmail,
                    name = resolvedName,
                    profilePictureUrl = resolvedPic,
                    googleSub = tokenManager.getGoogleSub() ?: targetProfile?.googleSub ?: "",
                    emailVerified = tokenManager.isEmailVerified() || targetProfile?.emailVerified == true,
                    isOnboardingCompleted = targetAccounts.isNotEmpty() || targetProfile?.isOnboardingCompleted == true,
                    updatedAt = System.currentTimeMillis()
                )
                userDb.userProfileDao().insertOrUpdateProfile(activeProfile)

                prefs.isLoggedOut = false
                prefs.setLoggedOutForUser(cleanEmail, false)
                prefs.isOfflineMode = false
                prefs.lastUserEmail = cleanEmail
                prefs.lastUserName = resolvedName
                prefs.lastUserProfilePic = resolvedPic

                val primaryBank = targetAccounts.firstOrNull { it.isDefault }?.bankName
                    ?: targetAccounts.firstOrNull()?.bankName
                    ?: "Indian Bank"
                prefs.lastBankName = primaryBank
                prefs.isOnboardingCompleted = activeProfile.isOnboardingCompleted
                prefs.setOnboardingCompletedForUser(cleanEmail, activeProfile.isOnboardingCompleted)

                // Update remembered accounts registry
                val remembered = com.example.utils.RememberedAccount(
                    email = cleanEmail,
                    name = resolvedName,
                    profilePicture = resolvedPic,
                    googleSub = activeProfile.googleSub,
                    bankName = primaryBank,
                    lastUsedTimestamp = System.currentTimeMillis(),
                    sessionToken = tokenManager.getSessionToken() ?: ""
                )
                prefs.saveRememberedAccount(remembered)

                // 5. Update auth state and sync managers
                _isEmailVerified.value = activeProfile.emailVerified
                _user.value = buildLocalUser(activeProfile)
                _authState.value = AuthState.Authenticated(isNewUser = !activeProfile.isOnboardingCompleted)
                refreshDriveStatus()
                syncManager.loadInitialStatus()
                refreshRememberedAccounts()

                completed = true
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "switchAccount error: ${e.message}", e)
                _errorMessage.value = "Failed to switch account: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
                if (!completed) {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Prepares the app to add another user account on the same phone.
     * Preserves existing user sessions in SecureTokenManager and remembered accounts.
     * Resets active session and memory state to a clean slate so previous user details
     * are never shown when adding or logging into the new account.
     */
    fun prepareAddAnotherAccount(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)

                // Close active user's database cleanly
                val currentEmail = tokenManager.getUserEmail()?.trim()?.lowercase()
                if (!currentEmail.isNullOrBlank()) {
                    AppDatabase.closeDatabaseForUser(app, currentEmail)
                    prefs.setLoggedOutForUser(currentEmail, true)
                }

                // Preserve existing user sessions, but clear active session slot
                tokenManager.clearActiveSession(preserveSavedSession = true)

                // Reset active DB partition to guest
                AppDatabase.setActiveUserKey("guest")
                prefs.isLoggedOut = true
                prefs.isOfflineMode = false

                _user.value = null
                _isNewUser.value = true
                _isEmailVerified.value = false
                _errorMessage.value = null
                _authState.value = AuthState.Unauthenticated
                refreshRememberedAccounts()

                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "prepareAddAnotherAccount error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Removes a user account session from this device.
     */
    fun removeAccount(email: String, onComplete: (() -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        val isOffline = cleanEmail.isBlank() || cleanEmail == "offline" || cleanEmail == "guest"

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)

                // 1. Remove from SecureTokenManager and AppPreferences
                if (!isOffline) {
                    tokenManager.removeUserSession(cleanEmail)
                }
                prefs.removeRememberedAccount(if (isOffline) "" else cleanEmail)

                // 2. Close isolated database
                AppDatabase.closeDatabaseForUser(app, if (isOffline) "guest" else cleanEmail)

                // 3. If currently active user was removed, switch to another account or unauthenticate
                val currentActive = _user.value?.email?.trim()?.lowercase() ?: ""
                val isCurrentActiveOffline = currentActive.isBlank() || currentActive == "offline" || currentActive == "guest"
                val wasCurrentActiveRemoved = if (isOffline) isCurrentActiveOffline else cleanEmail.equals(currentActive, ignoreCase = true)

                if (wasCurrentActiveRemoved) {
                    val remaining = prefs.getRememberedAccounts()
                    if (remaining.isNotEmpty()) {
                        switchAccount(remaining.first().email, onComplete)
                        refreshRememberedAccounts()
                        return@launch
                    } else {
                        AppDatabase.setActiveUserKey("guest")
                        prefs.isLoggedOut = true
                        prefs.isOfflineMode = false
                        _user.value = null
                        _authState.value = AuthState.Unauthenticated
                    }
                }
                refreshRememberedAccounts()
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "removeAccount error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Safely links an offline profile to a Google account without wiping local financial data.
     * Preserves existing accounts, transactions, loans, and categories intact.
     */
    fun linkOfflineProfileToGoogle(
        claims: GoogleClaims,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val prefs = com.example.utils.AppPreferences(app)

                // 1. Export local guest data from guest database
                val guestDb = AppDatabase.getDatabaseForUser(app, viewModelScope, "guest")
                val guestBackupJson = com.example.utils.BackupService.generateBackupJsonObject(guestDb).toString()

                val cleanEmail = claims.email.trim().lowercase()
                // 2. Switch active database to this user
                AppDatabase.setActiveUserKey(cleanEmail)
                val userDb = AppDatabase.getDatabaseForUser(app, viewModelScope, cleanEmail)

                // 3. Restore/migrate guest records into user's database without duplicates
                com.example.utils.BackupService.restoreBackupJson(guestBackupJson, userDb, clearExisting = false)

                // 4. Update authenticated profile
                val existingProfile = userDb.userProfileDao().getUserProfileOnce()
                val currentName = existingProfile?.name?.takeIf { it.isNotBlank() && it != "User" }
                    ?: claims.name.ifBlank { cleanEmail.substringBefore("@") }

                val updatedProfile = (existingProfile ?: UserProfileEntity(id = 1)).copy(
                    email = cleanEmail,
                    name = currentName,
                    googleSub = claims.sub,
                    profilePictureUrl = claims.picture.ifBlank { existingProfile?.profilePictureUrl ?: "" },
                    emailVerified = true,
                    isOnboardingCompleted = true,
                    updatedAt = System.currentTimeMillis()
                )

                userDb.userProfileDao().insertOrUpdateProfile(updatedProfile)
                withContext(Dispatchers.IO) {
                    userDb.userProfileDao().updateOnboardingCompleted(true)
                    // Reset guest DB so future guest sessions start clean
                    com.example.data.local.DatabaseInitializer.purgeLegacyDemoData(guestDb)
                }

                tokenManager.saveUserSession(
                    userId = 1L,
                    googleSub = claims.sub,
                    email = cleanEmail,
                    name = currentName,
                    profilePicture = claims.picture,
                    emailVerified = true
                )

                prefs.isLoggedOut = false
                prefs.isOfflineMode = false
                prefs.isOnboardingCompleted = true
                _user.value = buildLocalUser(updatedProfile)
                _authState.value = AuthState.Authenticated(isNewUser = false)
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "linkOfflineProfileToGoogle failed: ${e.message}", e)
                onComplete?.invoke(false, e.localizedMessage ?: "Failed to link profile")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAccount(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val app = getApplication<Application>()
            val currentEmail = _user.value?.email?.trim()?.lowercase() ?: ""
            withContext(Dispatchers.IO) {
                try {
                    database.statementImportDao().deleteAllImports()
                    database.transactionDao().deleteAllTransactions()
                    database.loanDao().deleteAllLoans()
                    database.loanRepaymentDao().deleteAllRepayments()
                    database.companyExpenseDao().deleteAllCompanyExpenses()
                    database.accountDao().deleteAllAccounts()
                    database.categorizationRuleDao().deleteAllRules()
                    database.undoDao().clearUndoHistory()
                    database.syncDao().clearAllDeletedTransactions()
                    database.syncDao().deleteAllMetadata()
                    database.categoryDao().deleteAllCategories()
                    database.clearAllTables()
                } catch (_: Exception) {}
            }
            val prefs = com.example.utils.AppPreferences(app)
            if (currentEmail.isNotBlank()) {
                tokenManager.removeUserSession(currentEmail)
                prefs.removeRememberedAccount(currentEmail)
                prefs.setLoggedOutForUser(currentEmail, true)
                prefs.setOnboardingCompletedForUser(currentEmail, false)
            } else {
                tokenManager.clearActiveSession(preserveSavedSession = true)
                prefs.removeRememberedAccount("")
                prefs.isLoggedOut = true
            }
            tokenManager.clearDriveTokens()

            val targetPartition = currentEmail.ifBlank { "guest" }
            AppDatabase.closeDatabaseForUser(app, targetPartition)
            withContext(Dispatchers.IO) {
                try {
                    val dbFile = AppDatabase.getDatabaseFileName(app, targetPartition)
                    app.deleteDatabase(dbFile)
                } catch (_: Exception) {}
            }

            refreshRememberedAccounts()
            val remainingAccounts = prefs.getRememberedAccounts()
            if (remainingAccounts.isNotEmpty()) {
                val nextAccount = remainingAccounts.first()
                switchAccount(nextAccount.email) {
                    _isLoading.value = false
                    onComplete?.invoke()
                }
                return@launch
            } else {
                AppDatabase.setActiveUserKey("guest")
                prefs.isLoggedOut = true
                prefs.isOfflineMode = false
                _user.value = null
                _isNewUser.value = true
                _isEmailVerified.value = false
                _authState.value = AuthState.Unauthenticated
                _isLoading.value = false
                onComplete?.invoke()
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startOtpCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _otpCooldown.value = seconds
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _otpCooldown.value = remaining
            }
        }
    }

    private fun parseErrorMessage(jsonStr: String?): String? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(jsonStr)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("msg").takeIf { it.isNotBlank() }
                ?: obj.optString("error_description").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLocalUser(profile: UserProfileEntity): UserDto = UserDto(
        id = profile.id,
        googleSub = profile.googleSub.ifEmpty { "local_${profile.id}" },
        email = profile.email,
        name = profile.name.ifBlank { "User" },
        profilePictureUrl = profile.profilePictureUrl.takeIf { !it.isNullOrBlank() },
        emailVerified = profile.emailVerified,
        currencySymbol = profile.currencySymbol,
        isDriveConnected = tokenManager.isDriveConnected() || (_driveStatus.value?.isConnected == true),
        driveEmail = tokenManager.getDriveEmail() ?: _driveStatus.value?.driveEmail,
        lastBackupAt = _driveStatus.value?.lastBackupAt
    )

    fun decodeGoogleIdToken(idToken: String): GoogleClaims? = decodeGoogleIdTokenLocally(idToken)

    companion object {
        fun decodeGoogleIdTokenLocally(idToken: String): GoogleClaims? {
            return try {
                val parts = idToken.split(".")
                if (parts.size < 2) return null
                val decoded = android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                val json = JSONObject(String(decoded, Charsets.UTF_8))
                GoogleClaims(
                    sub = json.optString("sub", ""),
                    email = json.optString("email", ""),
                    name = json.optString("name", ""),
                    picture = json.optString("picture", "")
                )
            } catch (_: Exception) { null }
        }
    }
}

data class GoogleClaims(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String
)
