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

        val appPreferences = AppPreferences(applicationContext)
        val biometricAuthManager = BiometricAuthManager(applicationContext)

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val financeViewModel: FinanceViewModel = viewModel()
            activeAuthViewModel = authViewModel

            // Check if activity was launched with an OAuth deep link intent
            LaunchedEffect(intent?.data) {
                intent?.data?.let { uri ->
                    authViewModel.handleDeepLink(uri)
                }
            }

            val userProfile by financeViewModel.userProfile.collectAsState()
            val allTransactions by financeViewModel.allTransactions.collectAsState()

            val isBiometricEnabled = userProfile?.isBiometricEnabled ?: appPreferences.isBiometricEnabled

            // GPay-Style Security Gatekeeper state
            var isAppLocked by remember { mutableStateOf(appPreferences.isBiometricEnabled) }
            var lockErrorMessage by remember { mutableStateOf<String?>(null) }
            var lastPauseTimestamp by remember { mutableLongStateOf(0L) }

            val authState by authViewModel.authState.collectAsState()
            val authUser by authViewModel.user.collectAsState()

            val isExistingUser = !appPreferences.isLoggedOut && (
                (authState is AuthState.Authenticated) ||
                (appPreferences.isOfflineMode && appPreferences.isOnboardingCompleted)
            )

            // Keep AppPreferences synchronized with Room UserProfile updates
            LaunchedEffect(userProfile) {
                userProfile?.let { profile ->
                    if (profile.isOnboardingCompleted && !appPreferences.isLoggedOut) {
                        appPreferences.isOnboardingCompleted = true
                    }
                    appPreferences.isBiometricEnabled = profile.isBiometricEnabled
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

            val keyguardManager = remember { getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }

            val deviceCredentialLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    isAppLocked = false
                    lockErrorMessage = null
                } else {
                    lockErrorMessage = "PIN/Pattern authentication failed or was cancelled."
                }
            }

            // Dedicated prompt for Biometric (Fingerprint / Face)
            val triggerBiometricUnlock = remember {
                {
                    lockErrorMessage = null
                    biometricAuthManager.authenticateBiometric(
                        activity = this@MainActivity,
                        title = "Unlock Yosan",
                        subtitle = "Verify your fingerprint or face to continue",
                        onSuccess = {
                            isAppLocked = false
                            lockErrorMessage = null
                        },
                        onError = { errorCode, errString ->
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
                    if (keyguardManager.isDeviceSecure) {
                        @Suppress("DEPRECATION")
                        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            "Unlock Yosan",
                            "Enter your phone PIN, pattern, or password"
                        )
                        if (intent != null) {
                            deviceCredentialLauncher.launch(intent)
                        } else {
                            triggerBiometricUnlock()
                        }
                    } else {
                        lockErrorMessage = "No device PIN, pattern, or password is set on this phone."
                    }
                }
            }

            // Automatically challenge user with biometric prompt when app is in locked state
            LaunchedEffect(isAppLocked, isBiometricEnabled, isExistingUser) {
                if (isAppLocked && isBiometricEnabled && isExistingUser) {
                    triggerBiometricUnlock()
                }
            }

            // Background Auto-lock Watcher with grace period & external intent immunity
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isBiometricEnabled, isExistingUser) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            lastPauseTimestamp = System.currentTimeMillis()
                        }
                        Lifecycle.Event.ON_START -> {
                            if (isBiometricEnabled && isExistingUser) {
                                if (appPreferences.isExternalIntentActive) {
                                    // User returned from an intentional file picker / share sheet
                                    appPreferences.isExternalIntentActive = false
                                } else if (lastPauseTimestamp > 0L) {
                                    val timeoutMillis = appPreferences.appLockTimeoutSeconds * 1000L
                                    val elapsed = System.currentTimeMillis() - lastPauseTimestamp
                                    if (elapsed >= timeoutMillis) {
                                        isAppLocked = true
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
                    else if (isAppLocked && isBiometricEnabled && isExistingUser) {
                        GPayLockScreen(
                            userName = userProfile?.name ?: "User",
                            errorMessage = lockErrorMessage,
                            onFingerprintClick = triggerBiometricUnlock,
                            onPinPatternClick = triggerPinUnlock,
                            onExitApp = { finishAffinity() }
                        )
                    }
                    // Priority 2: Onboarding for fresh installs or Login for logged out users
                    else if (!isExistingUser) {
                        OnboardingScreen(
                            authViewModel = authViewModel,
                            financeViewModel = financeViewModel,
                            initialStep = if (appPreferences.isLoggedOut || appPreferences.hasPreviousAccountOnDevice) 1 else 0,
                            onComplete = {
                                appPreferences.isOnboardingCompleted = true
                                appPreferences.isLoggedOut = false
                                authViewModel.completeOnboarding()
                            }
                        )
                    }
                    // Priority 3: Authenticated Main Container
                    else {
                        MainContainerScreen(
                            viewModel = financeViewModel,
                            authViewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
}
