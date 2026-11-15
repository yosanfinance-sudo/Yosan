package com.example.features.auth

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.ui.theme.LendingIndigo
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel
import com.example.data.remote.LatestBackupResponse
import com.example.data.remote.ApiClient
import com.example.features.walkthrough.UserWalkthroughDialog
import com.example.utils.HapticHelper
import kotlinx.coroutines.launch


@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModel,
    financeViewModel: FinanceViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: Int = 1
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = remember { com.example.utils.AppPreferences(context) }
    val accounts by financeViewModel.accounts.collectAsState()

    var isOfflineModeSelected by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(if (appPreferences.isLoggedOut) "" else appPreferences.lastUserName) }
    var currencySymbol by remember { mutableStateOf("₹") }
    var selectedThemeMode by remember { mutableStateOf("SYSTEM") }
    var isPrivacyBlurEnabled by remember { mutableStateOf(true) }
    var blurTimeoutSeconds by remember { mutableStateOf(5) }
    var selectedStorageMode by remember { mutableStateOf(com.example.features.sync.StorageMode.LOCAL_ONLY) }
    var emailInput by remember { mutableStateOf(if (appPreferences.isLoggedOut) "" else appPreferences.lastUserEmail) }
    var officialExpenseKeywordInput by remember { mutableStateOf(appPreferences.officialExpenseKeyword.ifBlank { "Company" }) }
    val rememberedAccounts by authViewModel.rememberedAccounts.collectAsState()
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<com.example.utils.RememberedAccount?>(null) }

    LaunchedEffect(Unit) {
        authViewModel.refreshRememberedAccounts()
    }
    var foundBackupInfo by remember { mutableStateOf<LatestBackupResponse?>(null) }
    var showBackupFoundDialog by remember { mutableStateOf(false) }
    var isCheckingBackup by remember { mutableStateOf(false) }
    var isRestoringFromBackup by remember { mutableStateOf(false) }
    var restoreStatusMessage by remember { mutableStateOf<String?>(null) }

    var isRegistrationConfirmed by remember { mutableStateOf(false) }
    var confirmedAccountName by remember { mutableStateOf("") }
    var confirmedAccountEmail by remember { mutableStateOf("") }
    var isConfirmedOffline by remember { mutableStateOf(false) }

    var showRestoreSourceDialog by remember { mutableStateOf(false) }
    var showDriveVersionsDialog by remember { mutableStateOf(false) }
    var driveVersionsList by remember { mutableStateOf<List<com.example.data.remote.DriveBackupVersionDto>>(emptyList()) }
    var isLoadingDriveVersions by remember { mutableStateOf(false) }
    var driveVersionsError by remember { mutableStateOf<String?>(null) }
    var isRestoringSelectedVersion by remember { mutableStateOf(false) }
    var selectedRestoreFileId by remember { mutableStateOf<String?>(null) }

    val loadDriveBackupVersions: () -> Unit = {
        isLoadingDriveVersions = true
        driveVersionsError = null
        showDriveVersionsDialog = true
        authViewModel.getBackupHistory { success, backups, err ->
            isLoadingDriveVersions = false
            if (success) {
                driveVersionsList = backups
            } else {
                driveVersionsError = err ?: "Could not fetch backup versions from Google Drive"
            }
        }
    }

    val driveConsentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val email = emailInput.ifBlank { authViewModel.user.value?.email ?: "" }
        if (res.resultCode == android.app.Activity.RESULT_OK && email.isNotBlank()) {
            isCheckingBackup = true
            authViewModel.checkDriveOrCloudBackup(
                email = email,
                onNeedsConsent = null
            ) { hasBackup, backupResp ->
                isCheckingBackup = false
                if (hasBackup && backupResp != null) {
                    foundBackupInfo = backupResp
                    showBackupFoundDialog = true
                } else {
                    confirmedAccountName = userName.ifBlank { "User" }
                    confirmedAccountEmail = email
                    isConfirmedOffline = false
                    isRegistrationConfirmed = true
                }
            }
        } else {
            isCheckingBackup = false
            confirmedAccountName = userName.ifBlank { "User" }
            confirmedAccountEmail = email
            isConfirmedOffline = false
            isRegistrationConfirmed = true
        }
    }

    val handleSelectedEmail: (String, String?) -> Unit = { rawEmail, explicitName ->
        val cleanEmail = rawEmail.trim().lowercase()
        if (cleanEmail.isNotBlank()) {
            val explicitOrEntered = userName.trim().takeIf { it.length >= 2 }
            val realName = explicitOrEntered
                ?: explicitName?.takeIf { it.isNotBlank() && !it.contains("@") && !it.equals("User", ignoreCase = true) }
                ?: GoogleAuthManager.resolveDisplayName(context, cleanEmail)?.takeIf { it.isNotBlank() && !it.contains("@") }
                ?: rememberedAccounts.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }?.name?.takeIf { it.isNotBlank() && !it.contains("@") }
                ?: GoogleAuthManager.formatEmailAsReadableName(cleanEmail).ifBlank { "User" }
            val autoName = realName

            userName = autoName
            emailInput = cleanEmail
            officialExpenseKeywordInput = appPreferences.getOfficialExpenseKeywordForUser(cleanEmail).ifBlank { "Company" }

            authViewModel.signInWithGoogleAccount(
                email = cleanEmail,
                name = autoName,
                markOnboardingCompleted = false
            ) { finalClaims ->
                val finalName = if (userName.trim().length >= 2) userName.trim()
                    else finalClaims.name.takeIf { it.isNotBlank() && !it.contains("@") && !it.equals("User", ignoreCase = true) && !it.equals(finalClaims.email.substringBefore("@"), ignoreCase = true) }
                    ?: GoogleAuthManager.resolveDisplayName(context, finalClaims.email)?.takeIf { it.isNotBlank() && !it.contains("@") && !it.equals(finalClaims.email.substringBefore("@"), ignoreCase = true) }
                    ?: GoogleAuthManager.formatEmailAsReadableName(finalClaims.email).ifBlank { "User" }
                userName = finalName
                emailInput = finalClaims.email
                financeViewModel.updateUserProfile(finalName, currencySymbol)
                authViewModel.setStoragePreference(selectedStorageMode)
                appPreferences.lastUserName = finalName
                appPreferences.lastUserEmail = finalClaims.email

                // Registration confirmed: show explicit Enter Dashboard button instead of auto-redirecting
                confirmedAccountName = finalName
                confirmedAccountEmail = finalClaims.email
                isConfirmedOffline = false
                isRegistrationConfirmed = true
            }
        }
    }

    val systemAccountPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selectedEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == android.app.Activity.RESULT_OK && !selectedEmail.isNullOrBlank()) {
            handleSelectedEmail(selectedEmail, null)
        }
    }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val googleAuthManager = GoogleAuthManager(context)
            val claims = googleAuthManager.getSignedInAccountFromIntent(result.data)
            val selectedEmail = claims?.email
                ?: result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                handleSelectedEmail(selectedEmail, claims?.name)
            } else {
                // Fall back cleanly to system account chooser only on valid non-claim result
                val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                if (pickerIntent != null) {
                    try {
                        systemAccountPickerLauncher.launch(pickerIntent)
                    } catch (e: Exception) {
                        android.util.Log.e("Onboarding", "Picker fallback error: ${e.message}")
                    }
                }
            }
        }
        // When resultCode == RESULT_CANCELED, do nothing so UI never flickers
    }

    var showWalkthroughDialog by remember { mutableStateOf(false) }

    val authLoading by authViewModel.isLoading.collectAsState()
    val authError by authViewModel.errorMessage.collectAsState()

    var showServerConfigDialog by remember { mutableStateOf(false) }
    var currentServerUrl by remember { mutableStateOf(ApiClient.getBaseUrl(context)) }

    var editedServerUrl by remember(showServerConfigDialog) { mutableStateOf(currentServerUrl) }

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let {
            financeViewModel.restoreBackup(context, it) { success ->
                if (success) {
                    authViewModel.completeOnboarding()
                    onComplete()
                }
            }
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                AnimatedContent(
                    targetState = isRegistrationConfirmed,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(320)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320)))
                            .togetherWith(fadeOut(animationSpec = tween(220)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "RegistrationConfirmedTransition"
                ) { confirmed ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (confirmed) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Registration Confirmed!",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "Welcome to Yosan, $confirmedAccountName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isConfirmedOffline) Icons.Default.PhoneAndroid else Icons.Default.AccountCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = confirmedAccountEmail,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Default Account: Cash in Hand",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            val finalEmail = if (isConfirmedOffline) "guest" else confirmedAccountEmail
                                            financeViewModel.completeOnboarding(
                                                themeMode = selectedThemeMode,
                                                privacyBlurEnabled = isPrivacyBlurEnabled,
                                                timeoutSeconds = blurTimeoutSeconds
                                            )
                                            appPreferences.isLoggedOut = false
                                            appPreferences.lastUserName = confirmedAccountName
                                            appPreferences.lastUserEmail = finalEmail
                                            appPreferences.setLoggedOutForUser(finalEmail, false)
                                            appPreferences.setOnboardingCompletedForUser(finalEmail, true)
                                            appPreferences.isOnboardingCompleted = true
                                            authViewModel.completeOnboarding(confirmedAccountName)
                                            onComplete()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = "Enter Dashboard",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        LendingIndigo
                                                    )
                                                ),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Welcome to Yosan",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "Smart, private personal finance",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Single Page: Display Name input
                                    OutlinedTextField(
                                        value = userName,
                                        onValueChange = { userName = it },
                                        label = { Text("Display Name") },
                                        placeholder = { Text("Enter your name (optional)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { focusManager.clearFocus() }
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 1. Primary Action: Continue with Google
                                    Button(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            val googleAuthManager = GoogleAuthManager(context)
                                            val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                                            if (pickerIntent != null) {
                                                systemAccountPickerLauncher.launch(pickerIntent)
                                            } else {
                                                try {
                                                    val signInIntent = googleAuthManager.getGoogleSignInIntent()
                                                    googleSignInLauncher.launch(signInIntent)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        enabled = !authLoading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        if (authLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Signing In...",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Continue with Google",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Divider OR
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = "OR",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // 2. Secondary Action: Continue Offline
                                    OutlinedButton(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            isOfflineModeSelected = true
                                            authViewModel.setStoragePreference(com.example.features.sync.StorageMode.LOCAL_ONLY)
                                            val finalName = userName.trim().ifBlank { "Offline User" }
                                            com.example.data.local.AppDatabase.setActiveUserKey("guest")
                                            appPreferences.isOfflineMode = true
                                            appPreferences.isLoggedOut = false
                                            appPreferences.lastUserName = finalName
                                            appPreferences.lastUserEmail = "guest"
                                            appPreferences.setLoggedOutForUser("guest", false)
                                            val remembered = com.example.utils.RememberedAccount(
                                                email = "guest",
                                                name = finalName,
                                                profilePicture = "",
                                                googleSub = "guest",
                                                bankName = "",
                                                lastUsedTimestamp = System.currentTimeMillis()
                                            )
                                            appPreferences.saveRememberedAccount(remembered)
                                            financeViewModel.updateUserProfile(finalName, currencySymbol)

                                            // Confirmed offline registration - do not auto-redirect
                                            confirmedAccountName = finalName
                                            confirmedAccountEmail = "Offline Storage (Local Only)"
                                            isConfirmedOffline = true
                                            isRegistrationConfirmed = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Continue Offline",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 3. Switch Account Option
                                    OutlinedButton(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            if (rememberedAccounts.isNotEmpty()) {
                                                showSwitchAccountDialog = true
                                            } else {
                                                val googleAuthManager = GoogleAuthManager(context)
                                                val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                                                if (pickerIntent != null) {
                                                    systemAccountPickerLauncher.launch(pickerIntent)
                                                } else {
                                                    try {
                                                        val signInIntent = googleAuthManager.getGoogleSignInIntent()
                                                        googleSignInLauncher.launch(signInIntent)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwitchAccount,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (rememberedAccounts.isNotEmpty()) "Switch Account (${rememberedAccounts.size})" else "Switch Account",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                authError?.let { err ->
                                    val isDevError = GoogleAuthManager.isDeveloperConsoleError(Exception(err))
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (isDevError) "Google Cloud requires SHA-1 registration for 1-tap sign-in." else err,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            if (isDevError) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val googleAuthManager = GoogleAuthManager(context)
                                                            val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                                                            if (pickerIntent != null) {
                                                                systemAccountPickerLauncher.launch(pickerIntent)
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Text("Choose Account", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            val currentSha1 = GoogleAuthManager.getSigningCertificateSha1(context)
                                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("SHA-1", currentSha1)
                                                            clipboard?.setPrimaryClip(clip)
                                                            android.widget.Toast.makeText(context, "Copied SHA-1 to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Copy SHA-1", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 3. Clean Footer: Restore from Backup & App Overview
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            showRestoreSourceDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restore Backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.width(8.dp))

                                    TextButton(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            showWalkthroughDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("App Overview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }


            }
        }


    if (showBackupFoundDialog && foundBackupInfo != null) {
        val backup = foundBackupInfo!!
        AlertDialog(
            onDismissRequest = { /* Require explicit choice */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Backup Found!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "We found an existing cloud backup associated with your email:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val backupName = backup.fileName?.ifBlank { null }
                                ?: emailInput.takeIf { it.isNotBlank() }?.let { "${it.substringBefore("@")}_backup.json" }
                                ?: "Cloud Backup"
                            Text(
                                text = "📁 Backup File: $backupName",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "📧 Account: ${emailInput.ifBlank { authViewModel.user.value?.email ?: "your account" }}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            backup.backupDate?.let { dateStr ->
                                Text(
                                    text = "📅 Backup Time: $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "📊 Content: ${backup.accountsCount} accounts • ${backup.transactionsCount} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Would you like to restore your accounts, transactions, and categories now?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    restoreStatusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = backup.backupJson
                        if (!json.isNullOrBlank()) {
                            isRestoringFromBackup = true
                            restoreStatusMessage = null
                            financeViewModel.restoreBackupFromJson(json) { success ->
                                isRestoringFromBackup = false
                                if (success) {
                                    showBackupFoundDialog = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val currentAccs = accounts
                                        val primaryBank = currentAccs.firstOrNull { it.isDefault }?.bankName
                                            ?: currentAccs.firstOrNull()?.bankName ?: ""
                                        if (primaryBank.isNotBlank()) appPreferences.lastBankName = primaryBank
                                        if (userName.isNotBlank() && userName != "User") appPreferences.lastUserName = userName
                                        appPreferences.isLoggedOut = false
                                        appPreferences.setLoggedOutForUser(emailInput, false)
                                        appPreferences.isOnboardingCompleted = true
                                        appPreferences.setOnboardingCompletedForUser(emailInput, true)
                                        withContext(Dispatchers.Main) {
                                            authViewModel.completeOnboarding()
                                            onComplete()
                                        }
                                    }
                                } else {
                                    restoreStatusMessage = "Failed to restore backup. You can continue manually."
                                }
                            }
                        }
                    },
                    enabled = !isRestoringFromBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRestoringFromBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Restore Backup", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupFoundDialog = false
                        appPreferences.isLoggedOut = false
                        appPreferences.isOnboardingCompleted = true
                        authViewModel.completeOnboarding(userName)
                        onComplete()
                    },
                    enabled = !isRestoringFromBackup
                ) {
                    Text("Skip & Start Fresh")
                }
            }
        )
    }

    if (showServerConfigDialog) {
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = { Text("Backend Server Configuration", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Configure the backend server URL used for OAuth session verification, email OTPs, and cloud sync.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Backend Server URL:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editedServerUrl,
                        onValueChange = { editedServerUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://10.0.2.2:5000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ApiClient.setCustomBaseUrl(context, editedServerUrl.ifBlank { null })
                        currentServerUrl = ApiClient.getBaseUrl(context)
                        showServerConfigDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ApiClient.setCustomBaseUrl(context, null)
                        currentServerUrl = ApiClient.getBaseUrl(context)
                        showServerConfigDialog = false
                    }
                ) {
                    Text("Reset Default")
                }
            }
        )
    }

    if (showWalkthroughDialog) {
        UserWalkthroughDialog(onDismissRequest = { showWalkthroughDialog = false })
    }

    // 1. Source Dialog (Google Drive vs Local File)
    if (showRestoreSourceDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Backup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose where to restore your financial database from:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option A: Google Drive
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRestoreSourceDialog = false
                                loadDriveBackupVersions()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google Drive (Cloud Versions)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Choose from available snapshot versions stored in Google Drive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option B: Device Storage File
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRestoreSourceDialog = false
                                restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = LendingIndigo,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Device Storage (JSON File)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pick an exported .json backup file from device Downloads or Storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Google Drive Backup Version Selector Dialog
    if (showDriveVersionsDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringSelectedVersion) showDriveVersionsDialog = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Drive Backup Versions", fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { loadDriveBackupVersions() },
                        enabled = !isLoadingDriveVersions && !isRestoringSelectedVersion
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    Text(
                        text = "Select which backup version to restore from Google Drive:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingDriveVersions) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (driveVersionsError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = driveVersionsError ?: "Error loading versions",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { loadDriveBackupVersions() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (driveVersionsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No backup snapshots found in Google Drive.\nSign in or create a backup first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(driveVersionsList) { item ->
                                val isSelectedRestoring = isRestoringSelectedVersion && selectedRestoreFileId == item.fileId
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isCurrent)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (item.isCurrent) Icons.Default.CloudDone else Icons.Default.Restore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (item.isCurrent) "Latest Update (v${item.version})" else "Snapshot v${item.version}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Surface(
                                                color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isCurrent) "LATEST" else "VERSION ${item.version}",
                                                    color = if (item.isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "File: ${item.fileName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        item.createdTime?.let { timeStr ->
                                            Text(
                                                text = "Timestamp: $timeStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                isRestoringSelectedVersion = true
                                                selectedRestoreFileId = item.fileId
                                                authViewModel.restoreBackupVersion(version = item.version, fileId = item.fileId) { success, msg ->
                                                    isRestoringSelectedVersion = false
                                                    if (success) {
                                                        showDriveVersionsDialog = false
                                                        financeViewModel.recalculateAllAccountBalances()
                                                        appPreferences.isLoggedOut = false
                                                        appPreferences.isOnboardingCompleted = true
                                                        val finalEmail = emailInput.ifBlank { authViewModel.user.value?.email ?: "" }
                                                        if (finalEmail.isNotBlank()) {
                                                            appPreferences.setOnboardingCompletedForUser(finalEmail, true)
                                                        }
                                                        authViewModel.completeOnboarding(userName.ifBlank { "User" })
                                                        onComplete()
                                                    } else {
                                                        driveVersionsError = msg ?: "Failed to restore version snapshot"
                                                    }
                                                }
                                            },
                                            enabled = !isRestoringSelectedVersion,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            if (isSelectedRestoring) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Restoring...")
                                            } else {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Restore This Version")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDriveVersionsDialog = false },
                    enabled = !isRestoringSelectedVersion
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwitchAccount,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Switch Account",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select an account to log in. Each user's database and records are kept completely isolated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (rememberedAccounts.isEmpty()) {
                        Text(
                            text = "No saved accounts found on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        rememberedAccounts.forEach { acc ->
                            val isTargetOffline = acc.email.isBlank() ||
                                acc.email.equals("offline", ignoreCase = true) ||
                                acc.email.equals("guest", ignoreCase = true)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticHelper.vibrateClick(context)
                                        showSwitchAccountDialog = false
                                        authViewModel.switchAccount(acc.email) {
                                            val userKey = if (isTargetOffline) "guest" else com.example.data.local.AppDatabase.sanitizeUserKey(acc.email)
                                            appPreferences.isLoggedOut = false
                                            appPreferences.setLoggedOutForUser(acc.email, false)
                                            appPreferences.setOnboardingCompletedForUser(userKey, true)
                                            appPreferences.isOnboardingCompleted = true
                                            financeViewModel.recalculateAllAccountBalances()
                                            authViewModel.completeOnboarding(acc.name.ifBlank { "User" })
                                            onComplete()
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = acc.name.take(1).uppercase().ifBlank { if (isTargetOffline) "O" else "U" },
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.name.ifBlank { if (isTargetOffline) "Offline Profile" else "User" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isTargetOffline) "Offline Profile" else acc.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            HapticHelper.vibrateClick(context)
                                            accountToRemove = acc
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove account",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            showSwitchAccountDialog = false
                            val googleAuthManager = GoogleAuthManager(context)
                            val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                            if (pickerIntent != null) {
                                systemAccountPickerLauncher.launch(pickerIntent)
                            } else {
                                try {
                                    val signInIntent = googleAuthManager.getGoogleSignInIntent()
                                    googleSignInLauncher.launch(signInIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add or Use Another Account")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (accountToRemove != null) {
        val targetAcc = accountToRemove!!
        val displayName = targetAcc.name.ifBlank { targetAcc.email.ifBlank { "Offline Profile" } }
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text("Remove Account", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove $displayName from saved accounts on this device? Your stored data in the database will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        appPreferences.removeRememberedAccount(targetAcc.email)
                        authViewModel.refreshRememberedAccounts()
                        accountToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun IntroFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
