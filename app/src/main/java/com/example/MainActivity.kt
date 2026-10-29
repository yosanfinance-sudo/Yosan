package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainContainerScreen
import com.example.ui.screens.GPayLockScreen
import com.example.features.auth.OnboardingScreen
import com.example.ui.theme.FinanceManagerTheme
import com.example.features.auth.AuthViewModel
import com.example.features.auth.AuthState
import com.example.features.auth.BiometricAuthManager
import com.example.features.transactions.FinanceViewModel
import com.example.utils.AppPreferences
import com.example.data.remote.ApiClient

class MainActivity : FragmentActivity() {

    private var activeAuthViewModel: AuthViewModel? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            activeAuthViewModel?.handleDeepLink(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Silently wake up Render backend in background if cold-started
        ApiClient.prewarmBackend(applicationContext)

        // Schedule background periodic update checks so notification pops automatically when an update is sent
        com.example.features.updater.UpdateCheckJobService.schedulePeriodicCheck(applicationContext)
        com.example.features.updater.UpdateCheckJobService.scheduleAlarmCheck(applicationContext)

        val appPreferences = AppPreferences(applicationContext)
        val biometricAuthManager = BiometricAuthManager(applicationContext)

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            activeAuthViewModel = authViewModel

            val authState by authViewModel.authState.collectAsState()
            val authUser by authViewModel.user.collectAsState()
            val isAuthenticated = authState is AuthState.Authenticated

            // Multi-user Database Partition Key: Each user/guest has their own isolated ViewModel & Room DB
            val currentEmail = authUser?.email?.trim()?.lowercase()
            val activeUserKey = if (isAuthenticated && !currentEmail.isNullOrBlank()) {
                com.example.data.local.AppDatabase.sanitizeUserKey(currentEmail)
            } else {
                "guest"
            }

            // Explicitly sync AppDatabase partition key synchronously before ViewModel resolution
            com.example.data.local.AppDatabase.setActiveUserKey(activeUserKey)

            val financeViewModel: FinanceViewModel = viewModel(key = "finance_vm_$activeUserKey")

            // Check if activity was launched with an OAuth deep link intent
            LaunchedEffect(intent?.data) {
                intent?.data?.let { uri ->
                    authViewModel.handleDeepLink(uri)
                }
            }

            val userProfile by financeViewModel.userProfile.collectAsState()
            val allTransactions by financeViewModel.allTransactions.collectAsState()
            val userAccounts by financeViewModel.accounts.collectAsState()

            val isBiometricEnabled = userProfile?.isBiometricEnabled ?: appPreferences.isBiometricEnabledForUser(currentEmail)

            // GPay-Style Security Gatekeeper state
            var isAppLocked by remember { mutableStateOf(appPreferences.isBiometricEnabledForUser(currentEmail)) }
            var lockErrorMessage by remember { mutableStateOf<String?>(null) }
            var lastPauseTimestamp by remember { mutableLongStateOf(0L) }

            // Keep AppPreferences synchronized with Room UserProfile updates
            LaunchedEffect(userProfile, activeUserKey) {
                userProfile?.let { profile ->
                    if (profile.isOnboardingCompleted && !appPreferences.isLoggedOutForUser(currentEmail)) {
                        appPreferences.setOnboardingCompletedForUser(currentEmail, true)
                    }
                    appPreferences.setBiometricEnabledForUser(currentEmail, profile.isBiometricEnabled)
                }
            }

            // VAPT Hardening: Screen scraping / task switcher snapshot prevention
            LaunchedEffect(userProfile?.isPrivacyBlurEnabled) {
                val shouldSecure = userProfile?.isPrivacyBlurEnabled ?: true
                if (shouldSecure) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Request Notification Permission on Android 13+ (API 33+) for Update & Sync Alerts
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val keyguardManager = remember { getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }

            var isAuthenticatingScreenLock by remember { mutableStateOf(false) }
            var isPinUnlockPreferred by remember { mutableStateOf(false) }
            var lastUnlockTimestamp by remember { mutableLongStateOf(0L) }

            val deviceCredentialLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                isAuthenticatingScreenLock = false
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    isAppLocked = false
                    lockErrorMessage = null
                    isPinUnlockPreferred = false
                    lastUnlockTimestamp = System.currentTimeMillis()
                    lastPauseTimestamp = 0L
                } else {
                    lockErrorMessage = "PIN/Pattern authentication was cancelled."
                }
            }

            // Dedicated prompt for Biometric (Fingerprint / Face) matching the proven working method
            val triggerBiometricUnlock = remember {
                {
                    lockErrorMessage = null
                    isAuthenticatingScreenLock = true
                    biometricAuthManager.authenticateBiometric(
                        activity = this@MainActivity,
                        title = "Unlock Yosan",
                        subtitle = "Verify your fingerprint or face to continue",
                        onSuccess = {
                            isAuthenticatingScreenLock = false
                            isAppLocked = false
                            isPinUnlockPreferred = false
                            lockErrorMessage = null
                            lastUnlockTimestamp = System.currentTimeMillis()
                            lastPauseTimestamp = 0L
                        },
                        onError = { errorCode, errString ->
                            isAuthenticatingScreenLock = false
                            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                lockErrorMessage = errString
                            }
                        },
                        onFailed = {
                            lockErrorMessage = "Biometric not recognized. Try again or use PIN."
                        }
                    )
                }
            }

            // Dedicated prompt for Device Credential (PIN / Pattern / Password)
            val triggerPinUnlock = remember {
                {
                    lockErrorMessage = null
                    isPinUnlockPreferred = true
                    isAuthenticatingScreenLock = true
                    biometricAuthManager.authenticatePin(
                        activity = this@MainActivity,
                        title = "Unlock Yosan",
                        subtitle = "Enter your phone PIN, pattern, or password",
                        onSuccess = {
                            isAuthenticatingScreenLock = false
                            isAppLocked = false
                            isPinUnlockPreferred = false
                            lockErrorMessage = null
                            lastUnlockTimestamp = System.currentTimeMillis()
                            lastPauseTimestamp = 0L
                        },
                        onError = { errorCode, errString ->
                            isAuthenticatingScreenLock = false
                            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                lockErrorMessage = errString
                            }
                        },
                        onFailed = {
                            lockErrorMessage = "PIN/Password incorrect. Try again."
                        },
                        fallbackLauncher = { intent ->
                            deviceCredentialLauncher.launch(intent)
                        }
                    )
                }
            }

            // Automatically challenge user with biometric prompt when app is in locked state (unless PIN mode was selected)
            LaunchedEffect(isAppLocked, isBiometricEnabled, isAuthenticated, isPinUnlockPreferred) {
                if (isAppLocked && isBiometricEnabled && isAuthenticated && !isAuthenticatingScreenLock && !isPinUnlockPreferred) {
                    kotlinx.coroutines.delay(100)
                    triggerBiometricUnlock()
                }
            }

            // Background Auto-lock Watcher with grace period & external intent immunity
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isBiometricEnabled, isAuthenticated, currentEmail) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            if (!isAuthenticatingScreenLock && !appPreferences.isExternalIntentActive) {
                                val now = System.currentTimeMillis()
                                if (now - lastUnlockTimestamp > 3000L) {
                                    lastPauseTimestamp = now
                                    val userTimeout = appPreferences.getAppLockTimeoutForUser(currentEmail)
                                    if (userTimeout == 0 && isBiometricEnabled && isAuthenticated) {
                                        isAppLocked = true
                                        isPinUnlockPreferred = false
                                    }
                                }
                            }
                            // Schedule background check for updates after app is minimized
                            com.example.features.updater.UpdateCheckJobService.scheduleOneOffCheck(applicationContext, 8_000L)
                        }
                        Lifecycle.Event.ON_START -> {
                            if (isBiometricEnabled && isAuthenticated) {
                                val now = System.currentTimeMillis()
                                if (now - lastUnlockTimestamp < 3000L) {
                                    // User unlocked within grace window (e.g. system dialog dismiss cycle); do not re-lock
                                    lastPauseTimestamp = 0L
                                } else if (isAuthenticatingScreenLock) {
                                    // Awaiting credential result; don't re-lock
                                } else if (appPreferences.isExternalIntentActive) {
                                    // User returned from an intentional file picker / share sheet
                                    appPreferences.isExternalIntentActive = false
                                    lastPauseTimestamp = 0L
                                } else {
                                    val userTimeout = appPreferences.getAppLockTimeoutForUser(currentEmail)
                                    if (userTimeout == 0) {
                                        // "Immediately" timeout: always lock if the app was stopped/backgrounded
                                        if (lastPauseTimestamp > 0L) {
                                            isAppLocked = true
                                            isPinUnlockPreferred = false
                                            lastPauseTimestamp = 0L
                                        }
                                    } else {
                                        val timeoutMillis = userTimeout * 1000L
                                        val elapsed = now - lastPauseTimestamp
                                        if (lastPauseTimestamp > 0L && elapsed >= timeoutMillis) {
                                            isAppLocked = true
                                            isPinUnlockPreferred = false
                                            lastPauseTimestamp = 0L
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val currentThemeMode by financeViewModel.themeMode.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (currentThemeMode.uppercase()) {
                "LIGHT" -> false
                "DARK" -> true
                "SYSTEM" -> systemInDark
                else -> systemInDark
            }

            FinanceManagerTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (authState is AuthState.Loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Priority 1: GPay-Style App Lock Screen
                    else if (isAppLocked && isBiometricEnabled && isAuthenticated) {
                        GPayLockScreen(
                            userName = userProfile?.name ?: "User",
                            errorMessage = lockErrorMessage,
                            onFingerprintClick = {
                                isPinUnlockPreferred = false
                                triggerBiometricUnlock()
                            },
                            onPinPatternClick = triggerPinUnlock,
                            onExitApp = { finishAffinity() },
                            isPinMode = isPinUnlockPreferred,
                            onTogglePinMode = { isPinUnlockPreferred = it }
                        )
                    }
                                        // Priority 2: Authenticated Main Container (with Bank Selection onboarding if not completed)
                    else if (isAuthenticated) {
                        val isUserOnboardingDone = appPreferences.isOnboardingCompletedForUser(currentEmail) ||
                            (userProfile?.isOnboardingCompleted == true && userAccounts.isNotEmpty()) ||
                            userAccounts.isNotEmpty()

                        if (!isUserOnboardingDone) {
                            key(activeUserKey) {
                                OnboardingScreen(
                                    authViewModel = authViewModel,
                                    financeViewModel = financeViewModel,
                                    initialStep = 2,
                                    onComplete = {
                                        appPreferences.setOnboardingCompletedForUser(currentEmail, true)
                                        appPreferences.setLoggedOutForUser(currentEmail, false)
                                        authViewModel.completeOnboarding()
                                    }
                                )
                            }
                        } else {
                            key(activeUserKey) {
                                MainContainerScreen(
                                    viewModel = financeViewModel,
                                    authViewModel = authViewModel
                                )
                            }
                        }
                    }
                    // Priority 3: Onboarding / Login Screen for unauthenticated or logged out users
                    else {
                        OnboardingScreen(
                            authViewModel = authViewModel,
                            financeViewModel = financeViewModel,
                            initialStep = 1,
                            onComplete = {
                                appPreferences.setOnboardingCompletedForUser(currentEmail, true)
                                appPreferences.setLoggedOutForUser(currentEmail, false)
                                authViewModel.completeOnboarding()
                            }
                        )
                    }
                }
            }
        }
    }
}
