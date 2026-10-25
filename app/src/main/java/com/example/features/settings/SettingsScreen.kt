package com.example.features.settings
import com.example.features.auth.GoogleClaims

import androidx.fragment.app.FragmentActivity
import android.content.Intent
import android.net.Uri
import android.app.Activity
import com.example.features.auth.BiometricAuthManager
import com.example.features.auth.BiometricCapability
import com.example.features.auth.GoogleAuthManager
import com.example.features.auth.GoogleAuthResult
import com.example.features.drive.GoogleDriveAuthManager
import com.example.features.drive.DriveAuthTokenResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.example.features.sync.StorageMode
import com.example.features.sync.SyncState
import com.example.features.sync.SyncStatusInfo
import com.example.utils.AppPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.CategoryEntity
import androidx.compose.ui.draw.scale
import com.example.features.dashboard.DashboardCardConfigManager
import com.example.features.dashboard.DashboardCardItem
import com.example.features.dashboard.DashboardCardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.components.PrivacyAmountText
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.UndoHistoryEntity
import com.example.BuildConfig
import com.example.features.updater.UpdateManager
import com.example.features.updater.UpdateDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateToRules: () -> Unit,
    onNavigateToCompanyExpenses: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToLending: () -> Unit = {},
    onNavigateToExcelSync: () -> Unit = {},
    onOpenWalkthrough: () -> Unit = {},
    onOpenIntroNote: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    val userProfile by viewModel.userProfile.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isAmountTemporarilyRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val undoHistory by viewModel.undoHistory.collectAsState()
    val isHapticFeedbackEnabled by viewModel.isHapticFeedbackEnabled.collectAsState()
    val isFloatingEffectEnabled by viewModel.isFloatingEffectEnabled.collectAsState()

    val authUser by authViewModel?.user?.collectAsState() ?: remember { mutableStateOf(null) }
    val isEmailVerified by authViewModel?.isEmailVerified?.collectAsState() ?: remember { mutableStateOf(false) }
    val storageMode by authViewModel?.storageMode?.collectAsState() ?: remember { mutableStateOf(StorageMode.LOCAL_ONLY) }
    val syncStatus by authViewModel?.syncStatus?.collectAsState() ?: remember { mutableStateOf(SyncStatusInfo()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val updateState by updateManager.uiState.collectAsState()
    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.saveBackupToStorageUri(context, it) {} }
    }

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.restoreBackup(context, it) {} }
    }

    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var showAccountsDialog by remember { mutableStateOf(false) }
    var showCustomizeDashboard by remember { mutableStateOf(false) }
    var showUndoHistorySheet by remember { mutableStateOf(false) }
    
    var showResetWizard by remember { mutableStateOf(false) }
    var resetWizardStep by remember { mutableStateOf(1) }
    var resetConfirmText by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDeveloperDetailsDialog by remember { mutableStateOf(false) }
    var accountToDeleteWithReassign by remember { mutableStateOf<com.example.data.local.entity.AccountEntity?>(null) }

    var statusNotificationMsg by remember { mutableStateOf<String?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var showEnrollmentDialog by remember { mutableStateOf(false) }

    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var driveMismatchError by remember { mutableStateOf<String?>(null) }
    var showDifferentDriveAccountDialog by remember { mutableStateOf(false) }
    var pendingDifferentDriveAccount by remember { mutableStateOf<com.google.android.gms.auth.api.signin.GoogleSignInAccount?>(null) }
    var showLinkOfflineConfirmDialog by remember { mutableStateOf(false) }
    var pendingMigrationClaims by remember { mutableStateOf<GoogleClaims?>(null) }
    var isDriveConnecting by remember { mutableStateOf(false) }
    var isDriveBackingUp by remember { mutableStateOf(false) }
    var isDriveRestoring by remember { mutableStateOf(false) }
    var showBackupHistoryDialog by remember { mutableStateOf(false) }
    var backupHistoryList by remember { mutableStateOf<List<com.example.data.remote.DriveBackupVersionDto>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var historyErrorMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var targetRestoreVersion by remember { mutableStateOf<Int?>(null) }
    var isRestoringVersion by remember { mutableStateOf(false) }

    val driveAuthManager = remember { GoogleDriveAuthManager(context) }
    var pendingConsentEmail by remember { mutableStateOf<String?>(null) }

    val driveConsentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { consentResult ->
        if (consentResult.resultCode == Activity.RESULT_OK) {
            val targetEmail = pendingConsentEmail
            if (!targetEmail.isNullOrBlank()) {
                isDriveConnecting = true
                coroutineScope.launch {
                    when (val tokenRes = driveAuthManager.fetchAccessTokenForEmail(targetEmail)) {
                        is DriveAuthTokenResult.Success -> {
                            authViewModel?.connectGoogleDriveWithValidation(
                                selectedDriveEmail = tokenRes.email,
                                accessToken = tokenRes.token,
                                allowDifferentAccount = true
                            ) { success, msg ->
                                isDriveConnecting = false
                                if (success) {
                                    showGoogleDriveDialog = false
                                    statusNotificationMsg = "Google Drive connected for ${tokenRes.email}!"
                                } else {
                                    driveMismatchError = msg ?: "Connection failed"
                                }
                            }
                        }
                        is DriveAuthTokenResult.Error -> {
                            isDriveConnecting = false
                            driveMismatchError = tokenRes.message
                        }
                        is DriveAuthTokenResult.NeedsUserConsent -> {
                            isDriveConnecting = false
                            driveMismatchError = "Google Drive permissions not granted."
                        }
                    }
                }
            }
        } else {
            isDriveConnecting = false
            driveMismatchError = "Google Drive authorization was cancelled."
        }
    }

    val connectDriveForAccount: (com.google.android.gms.auth.api.signin.GoogleSignInAccount) -> Unit = { account ->
        isDriveConnecting = true
        driveMismatchError = null
        coroutineScope.launch {
            when (val tokenRes = driveAuthManager.fetchAccessToken(account)) {
                is DriveAuthTokenResult.Success -> {
                    authViewModel?.connectGoogleDriveWithValidation(
                        selectedDriveEmail = tokenRes.email,
                        accessToken = tokenRes.token,
                        allowDifferentAccount = true
                    ) { success, msg ->
                        isDriveConnecting = false
                        if (success) {
                            showGoogleDriveDialog = false
                            statusNotificationMsg = "Google Drive connected for ${tokenRes.email}!"
                        } else {
                            driveMismatchError = msg ?: "Connection failed"
                        }
                    }
                }
                is DriveAuthTokenResult.NeedsUserConsent -> {
                    pendingConsentEmail = tokenRes.email
                    if (tokenRes.consentIntent != null) {
                        driveConsentLauncher.launch(tokenRes.consentIntent)
                    } else {
                        isDriveConnecting = false
                        driveMismatchError = "Google Drive consent required."
                    }
                }
                is DriveAuthTokenResult.Error -> {
                    isDriveConnecting = false
                    driveMismatchError = tokenRes.message
                }
            }
        }
    }

    val driveSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(res.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val selectedEmail = (account.email ?: "").trim().lowercase()
                val registeredEmail = userProfile?.email?.trim()?.lowercase() ?: authUser?.email?.trim()?.lowercase() ?: ""
                if (registeredEmail.isNotBlank() && selectedEmail.isNotBlank() && selectedEmail != registeredEmail) {
                    isDriveConnecting = false
                    pendingDifferentDriveAccount = account
                    showDifferentDriveAccountDialog = true
                } else {
                    connectDriveForAccount(account)
                }
            } else {
                isDriveConnecting = false
                driveMismatchError = "No Google account selected."
            }
        } catch (e: ApiException) {
            isDriveConnecting = false
            if (e.statusCode != 12501 && e.statusCode != 4) {
                driveMismatchError = "Google Play Services error: ${e.localizedMessage ?: e.message} (code ${e.statusCode})"
            }
        } catch (e: Exception) {
            isDriveConnecting = false
            driveMismatchError = e.localizedMessage ?: "Failed to connect Google account"
        }
    }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackCategory by remember { mutableStateOf("BUG_REPORT") }
    var feedbackRating by remember { mutableStateOf(5) }
    var feedbackSubject by remember { mutableStateOf("") }
    var feedbackDescription by remember { mutableStateOf("") }
    var feedbackIncludeMetadata by remember { mutableStateOf(true) }
    var isFeedbackSubmitting by remember { mutableStateOf(false) }
    var feedbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var editedServerUrl by remember { mutableStateOf("") }
    var currentServerUrl by remember { mutableStateOf(com.example.data.remote.ApiClient.getBaseUrl(context)) }

    val driveStatus by authViewModel?.driveStatus?.collectAsState() ?: remember { mutableStateOf(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("more_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header Profile Banner (Section 19)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        tempName = userProfile?.name ?: authUser?.name ?: "User"
                        showEditNameDialog = true
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userProfile?.name ?: authUser?.name ?: "U").take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.name ?: authUser?.name ?: "User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val email = userProfile?.email ?: authUser?.email
                            if (!email.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val verified = isEmailVerified || (userProfile?.emailVerified == true) || (authUser?.emailVerified == true)
                                    Surface(
                                        color = if (verified) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (verified) "✓ Verified" else "⚠ Unverified",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (verified) IncomeGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Offline Mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            authViewModel?.signInWithGoogle(
                                                context = context
                                            ) { claims ->
                                                pendingMigrationClaims = claims
                                                showLinkOfflineConfirmDialog = true
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Link Google Account", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isDriveConnected = driveStatus?.isConnected == true
                                if (isDriveConnected) {
                                    Surface(
                                        color = IncomeGreen.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                driveMismatchError = null
                                                showGoogleDriveDialog = true
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Drive Connection",
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Drive: Connected",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = IncomeGreen
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = storageMode.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status Notification Snack Banner
        statusNotificationMsg?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { statusNotificationMsg = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }



        // 3. Navigation: Finance Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Finance",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Categorization Rules",
                        subtitle = "Automatically categorize transactions using your rules",
                        icon = Icons.Default.AutoAwesome,
                        iconColor = LendingIndigo,
                        onClick = onNavigateToRules
                    )
                }
            }
        }

        // 4. Navigation: Appearance & Layout Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Appearance & Layout",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Customize Dashboard",
                        subtitle = "Enable, disable, and reorder cards on your Dashboard",
                        icon = Icons.Default.DashboardCustomize,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { showCustomizeDashboard = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val currentThemeMode by viewModel.themeMode.collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (currentThemeMode) {
                                            "DARK" -> Icons.Default.DarkMode
                                            "LIGHT" -> Icons.Default.LightMode
                                            else -> Icons.Default.BrightnessMedium
                                        },
                                        contentDescription = "Theme",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Theme Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (currentThemeMode) {
                                        "DARK" -> "Dark theme active"
                                        "LIGHT" -> "Light theme active"
                                        else -> "Follow system default"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "LIGHT" to "Light",
                                "DARK" to "Dark",
                                "SYSTEM" to "System"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = currentThemeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = "Haptic Feedback",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Haptic Feedback & Vibration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tactile vibration feedback on touches, gestures, and transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isHapticFeedbackEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setHapticFeedbackEnabled(enabled)
                                if (enabled) {
                                    com.example.utils.HapticHelper.vibrateSuccess(context)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Security & Privacy Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Security & Privacy",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        // 4.1 Navigation: Privacy Blur Settings
        item {
            val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
            val timeoutSeconds = userProfile?.blurTimeoutSeconds ?: 5
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = LendingIndigo.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (privacyEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Privacy Blur",
                                        tint = LendingIndigo,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Financial Privacy Blur",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mask financial values; tap to reveal temporarily",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = privacyEnabled,
                            onCheckedChange = { viewModel.updatePrivacyBlurPreference(it, timeoutSeconds) }
                        )
                    }

                    if (privacyEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Auto-hide reveal timer:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10, 15).forEach { sec ->
                                FilterChip(
                                    selected = timeoutSeconds == sec,
                                    onClick = { viewModel.updatePrivacyBlurPreference(privacyEnabled, sec) },
                                    label = { Text("${sec}s") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4.2 Security: Biometric & App Lock (GPay Style)
        item {
            val appPrefs = remember { AppPreferences(context) }
            val biometricAuthManager = remember { BiometricAuthManager(context) }
            val isBioEnabled = userProfile?.isBiometricEnabled ?: appPrefs.isBiometricEnabled
            val currentTimeout by viewModel.appLockTimeoutSeconds.collectAsState()
            val activity = context as? FragmentActivity

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "App Lock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric & Screen Lock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Require fingerprint, face, or phone PIN to open Yosan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isBioEnabled,
                            onCheckedChange = { targetState ->
                                val cap = biometricAuthManager.checkCapability()
                                when {
                                    cap == BiometricCapability.NO_HARDWARE -> {
                                        viewModel.showMessage("Biometric hardware is not available on this device")
                                    }
                                    cap == BiometricCapability.NOT_ENROLLED && targetState -> {
                                        showEnrollmentDialog = true
                                    }
                                    activity != null -> {
                                        biometricAuthManager.authenticate(
                                            activity = activity,
                                            title = if (targetState) "Enable App Lock" else "Disable App Lock",
                                            subtitle = "Verify your identity to change app security",
                                            onSuccess = {
                                                viewModel.setBiometricLock(targetState)
                                            },
                                            onError = { errorCode, errMsg ->
                                                if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                                    errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                                    viewModel.showMessage(errMsg)
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        viewModel.setBiometricLock(targetState)
                                    }
                                }
                            }
                        )
                    }

                    if (isBioEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Auto-lock timeout:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "Immediately",
                                30 to "30s",
                                60 to "1m",
                                300 to "5m"
                            ).forEach { (sec, label) ->
                                FilterChip(
                                    selected = currentTimeout == sec,
                                    onClick = { viewModel.setAppLockTimeout(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
        }



        // 5. Navigation: Account Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Bank Accounts",
                        subtitle = "${accounts.size} accounts managed",
                        icon = Icons.Default.AccountBalance,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { showAccountsDialog = true }
                    )
                }
            }
        }

        // 6. Navigation: Backup & Data Management
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Backup & Data Management",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    val isDriveConnected = driveStatus?.isConnected == true
                    val connectedDriveEmail = driveStatus?.driveEmail ?: (userProfile?.email ?: authUser?.email ?: "")

                    MoreMenuItem(
                        title = "Google Drive Cloud Backup",
                        subtitle = if (isDriveConnected)
                            "Connected as $connectedDriveEmail • Cloud sync & recovery snapshots"
                        else
                            "Connect Google Drive for automated cloud backup & multi-device restore",
                        icon = if (isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        iconColor = if (isDriveConnected) IncomeGreen else MaterialTheme.colorScheme.primary,
                        onClick = {
                            driveMismatchError = null
                            showGoogleDriveDialog = true
                        },
                        trailing = {
                            Surface(
                                color = if (isDriveConnected) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isDriveConnected) "Connected" else "Not Connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDriveConnected) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Local Device Backup",
                        subtitle = "Export database backup (JSON) to Downloads, storage folder, or share",
                        icon = Icons.Default.SaveAlt,
                        iconColor = IncomeGreen,
                        onClick = {
                            showBackupOptionsDialog = true
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Restore from Local File",
                        subtitle = "Import and restore complete records from a JSON backup file",
                        icon = Icons.Default.Restore,
                        iconColor = LendingIndigo,
                        onClick = {
                            viewModel.setExternalIntentActive(true)
                            restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Undo & Recovery History",
                        subtitle = "${undoHistory.size} actions available to revert",
                        icon = Icons.Default.History,
                        iconColor = LendingIndigo,
                        onClick = { showUndoHistorySheet = true }
                    )
                }
            }
        }

        // 6.5. System & Updates Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "System & Updates",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Check for Updates",
                        subtitle = "Current version: v${BuildConfig.VERSION_NAME}",
                        icon = Icons.Default.SystemUpdate,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            coroutineScope.launch {
                                updateManager.checkForUpdates()
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MoreMenuItem(
                        title = "Send Feedback / Report Issue",
                        subtitle = "Bug reports, statement parsing issues, or feature ideas",
                        icon = Icons.Default.RateReview,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            feedbackSubject = ""
                            feedbackDescription = ""
                            feedbackErrorMessage = null
                            showFeedbackDialog = true
                        }
                    )
                }
            }
        }

        // Help & User Guide Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Help & User Guide",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "How to Use the App",
                        subtitle = "Interactive 12-module walkthrough covering all features & safety",
                        icon = Icons.Default.MenuBook,
                        iconColor = LendingIndigo,
                        onClick = onOpenWalkthrough
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    MoreMenuItem(
                        title = "Welcome Note & Introduction",
                        subtitle = "Review the purpose, getting-started guide, and privacy note",
                        icon = Icons.Default.Info,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenIntroNote
                    )
                }
            }
        }

        // 7. Navigation: Data & Privacy Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Delete Account",
                        subtitle = "Entire account and local data will be permanently deleted",
                        icon = Icons.Default.DeleteOutline,
                        iconColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            resetWizardStep = 1
                            resetConfirmText = ""
                            showResetWizard = true
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    MoreMenuItem(
                        title = "Log Out",
                        subtitle = "End your active session and return to welcome screen",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            showLogoutDialog = true
                        }
                    )
                }
            }
        }

        // 8. App Version & Engineering Credits Footer
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showDeveloperDetailsDialog = true }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "Yosan Logo",
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOSAN FINANCE MANAGER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Designed & Developed by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Rooparaj & Gokulraj",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap for developer info, repository & architecture details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }





    // Backup Options Dialog
    if (showBackupOptionsDialog) {
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val defaultBackupFileName = "yosan_backup_$timeTag.json"

        AlertDialog(
            onDismissRequest = { showBackupOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Local Device Backup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you would like to export and save your financial records backup:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Choose Storage Folder (SAF)
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.setExternalIntentActive(true)
                            createDocumentLauncher.launch(defaultBackupFileName)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = IncomeGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Save to Device Storage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "Choose folder and save JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Quick Save to Downloads
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.saveBackupToDownloads(context) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = LendingIndigo.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = LendingIndigo, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Quick Save to Downloads", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "Instant save to Downloads/Yosan folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 3: Share to Apps
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.createBackup(context) { file ->
                                if (file != null) {
                                    com.example.utils.BackupService.shareBackupFile(context, file)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Share to Other Apps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "WhatsApp, Gmail, Files, etc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBackupOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEnrollmentDialog) {
        val biometricAuthManager = remember { BiometricAuthManager(context) }
        val activity = context as? FragmentActivity
        AlertDialog(
            onDismissRequest = { showEnrollmentDialog = false },
            title = {
                Text(
                    text = "No Screen Lock Enrolled",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "To enable App Lock, your device needs a registered fingerprint or screen lock (PIN, pattern, or password). Would you like to open Android Security Settings now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnrollmentDialog = false
                        activity?.let { biometricAuthManager.openEnrollmentSettings(it) }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnrollmentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Warning Wizard for Delete Account
    if (showResetWizard) {
        AlertDialog(
            onDismissRequest = {
                showResetWizard = false
                resetWizardStep = 1
                resetConfirmText = ""
            },
            title = {
                Text(
                    text = if (resetWizardStep == 1) "Delete Account?" else "Final Confirmation",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (resetWizardStep == 1) {
                        Text("This will permanently delete all your data from this device, including your bank accounts, transactions, custom categories, and settings. This action cannot be undone.")
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                com.example.utils.HapticHelper.vibrateClick(context)
                                viewModel.createBackup(context) { file ->
                                    if (file != null) {
                                        com.example.utils.BackupService.shareBackupFile(context, file)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = IncomeGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup Data First", fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                    } else {
                        Text("To confirm deletion of all data, please type \"DELETE\" in the field below:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetConfirmText,
                            onValueChange = { resetConfirmText = it.uppercase() },
                            placeholder = { Text("DELETE") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.utils.HapticHelper.vibrateClick(context)
                        if (resetWizardStep == 1) {
                            resetWizardStep = 2
                        } else {
                            if (resetConfirmText.trim() == "DELETE") {
                                viewModel.clearAllUserData {
                                    authViewModel?.deleteAccount()
                                }
                                showResetWizard = false
                                resetWizardStep = 1
                                resetConfirmText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = if (resetWizardStep == 1) true else resetConfirmText.trim() == "DELETE"
                ) {
                    Text(if (resetWizardStep == 1) "Proceed" else "Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.example.utils.HapticHelper.vibrateClick(context)
                    showResetWizard = false
                    resetWizardStep = 1
                    resetConfirmText = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Confirmation Dialog for Log Out
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Log Out of Yosan?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out? You will be returned to the Login screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.utils.HapticHelper.vibrateClick(context)
                        showLogoutDialog = false
                        authViewModel?.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.example.utils.HapticHelper.vibrateClick(context)
                    showLogoutDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAccountsDialog) {
        var addMode by remember { mutableStateOf(false) }
        var bankName by remember { mutableStateOf("Indian Bank") }
        var accountTypeSelected by remember { mutableStateOf("Savings") }
        var accountNumber by remember { mutableStateOf("") }
        var initialBalance by remember { mutableStateOf("") }
        var editingAccount by remember { mutableStateOf<com.example.data.local.entity.AccountEntity?>(null) }
        val selectedAccountIds = remember { mutableStateListOf<Long>() }

        AlertDialog(
            onDismissRequest = { showAccountsDialog = false },
            title = {
                Text(
                    text = if (addMode) (if (editingAccount == null) "Add Account / Wallet" else "Edit Account") else "Bank Accounts & Wallets",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (!addMode) {
                        val isSelectionMode = selectedAccountIds.isNotEmpty()
                        if (isSelectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { selectedAccountIds.clear() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${selectedAccountIds.size} Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedAccountIds.forEach { id ->
                                            viewModel.deleteAccount(id)
                                        }
                                        selectedAccountIds.clear()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Selected",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (accounts.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Accounts Added",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add your bank accounts to view live balances and manage statements.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            accounts.forEach { acc ->
                                val isSelected = selectedAccountIds.contains(acc.id)
                                val dynamicBalance = remember(acc, allTransactions, accounts) {
                                    val isSingleOrPrimary = accounts.size <= 1 || acc.isDefault || acc.id == 1L
                                    val txs = allTransactions.filter {
                                        it.accountId == acc.id || (isSingleOrPrimary && (it.accountId == 0L || it.accountId == 1L || it.accountId == acc.id))
                                    }

                                    fun getCredit(tx: com.example.data.local.entity.TransactionEntity): Double = when {
                                        tx.creditAmount > 0.0 -> tx.creditAmount
                                        tx.transactionType in listOf(
                                            com.example.data.local.entity.TransactionType.INCOME,
                                            com.example.data.local.entity.TransactionType.REFUND,
                                            com.example.data.local.entity.TransactionType.BORROWING
                                        ) && tx.amount > 0.0 -> tx.amount
                                        else -> 0.0
                                    }

                                    fun getDebit(tx: com.example.data.local.entity.TransactionEntity): Double = when {
                                        tx.debitAmount > 0.0 -> tx.debitAmount
                                        tx.transactionType in listOf(
                                            com.example.data.local.entity.TransactionType.EXPENSE,
                                            com.example.data.local.entity.TransactionType.LENDING,
                                            com.example.data.local.entity.TransactionType.INVESTMENT
                                        ) && tx.amount > 0.0 -> tx.amount
                                        else -> 0.0
                                    }

                                    val latestTxWithBalance = txs
                                        .filter { it.balanceAfterTransaction != null }
                                        .maxWithOrNull(compareBy<com.example.data.local.entity.TransactionEntity> { it.transactionDate }.thenBy { it.id })

                                    if (latestTxWithBalance != null && latestTxWithBalance.balanceAfterTransaction != null) {
                                        val snapshot = latestTxWithBalance.balanceAfterTransaction!!
                                        val subCredits = txs.filter {
                                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                                        }.sumOf { getCredit(it) }
                                        val subDebits = txs.filter {
                                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                                        }.sumOf { getDebit(it) }
                                        com.example.utils.CurrencyFormatter.roundFinancialAmount(snapshot + subCredits - subDebits)
                                    } else if (txs.isNotEmpty()) {
                                        val totalCredits = txs.sumOf { getCredit(it) }
                                        val totalDebits = txs.sumOf { getDebit(it) }
                                        com.example.utils.CurrencyFormatter.roundFinancialAmount(acc.openingBalance + totalCredits - totalDebits)
                                    } else {
                                        if (acc.currentBalance != 0.0) acc.currentBalance else acc.openingBalance
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedAccountIds.isNotEmpty()) {
                                                    if (isSelected) {
                                                        selectedAccountIds.remove(acc.id)
                                                    } else {
                                                        selectedAccountIds.add(acc.id)
                                                    }
                                                } else {
                                                    editingAccount = acc
                                                    bankName = acc.bankName
                                                    accountTypeSelected = acc.accountName
                                                    accountNumber = acc.accountNumberMasked
                                                    initialBalance = if (acc.openingBalance > 0.0) String.format(Locale.US, "%.2f", acc.openingBalance) else if (acc.currentBalance > 0.0) String.format(Locale.US, "%.2f", acc.currentBalance) else ""
                                                    addMode = true
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectedAccountIds.contains(acc.id)) {
                                                    selectedAccountIds.add(acc.id)
                                                } else {
                                                    selectedAccountIds.remove(acc.id)
                                                }
                                            }
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    val icon = when (acc.accountType) {
                                                        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                                                        AccountType.WALLET -> Icons.Default.Wallet
                                                        else -> Icons.Default.AccountBalance
                                                    }
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = acc.accountType.name,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = acc.bankName.ifBlank { acc.accountName },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val subtitle = buildString {
                                                    append(acc.accountName)
                                                    if (acc.accountNumberMasked.isNotBlank()) {
                                                        append(" • ${acc.accountNumberMasked}")
                                                    }
                                                }
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "Balance",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            PrivacyAmountText(
                                                amount = dynamicBalance,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (dynamicBalance >= 0) IncomeGreen else MaterialTheme.colorScheme.error,
                                                isRevealed = isAmountTemporarilyRevealed,
                                                isPrivacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true,
                                                onTap = { viewModel.toggleAmountReveal() }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                editingAccount = null
                                bankName = "Indian Bank"
                                accountTypeSelected = "Savings"
                                accountNumber = ""
                                initialBalance = ""
                                addMode = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Account / Wallet")
                        }
                    } else {
                        Text(
                            text = if (editingAccount == null) "Add Account / Wallet" else "Edit Account Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Select Bank / Institution", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val indianBanks = listOf(
                            "Indian Bank",
                            "State Bank",
                            "YES Bank",
                            "IndusInd Bank"
                        )

                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            indianBanks.forEach { bName ->
                                val isSelected = bankName.equals(bName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        com.example.utils.HapticHelper.vibrateTick(context)
                                        bankName = bName
                                    },
                                    label = { Text(bName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Account Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val types = listOf("Savings", "Current", "Salary", "Wallet", "Credit Card")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            types.forEach { type ->
                                val isSelected = accountTypeSelected == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { accountTypeSelected = type },
                                    label = { Text(type) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it },
                            label = { Text("Account Number / Mask (Optional)") },
                            placeholder = { Text("e.g. •••• 4589 or 4589") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = initialBalance,
                            onValueChange = { initialBalance = it },
                            label = { Text("Opening / Starting Balance") },
                            placeholder = { Text("0.00") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (editingAccount != null) {
                                TextButton(
                                    onClick = {
                                        val curAcc = editingAccount
                                        if (curAcc != null) {
                                            if (accounts.size <= 1) {
                                                viewModel.showMessage("Cannot delete the only account. At least one account is required.")
                                            } else {
                                                val linkedCount = allTransactions.count { it.accountId == curAcc.id }
                                                if (linkedCount > 0) {
                                                    accountToDeleteWithReassign = curAcc
                                                } else {
                                                    viewModel.deleteAccount(curAcc.id)
                                                    addMode = false
                                                    editingAccount = null
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Account", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    addMode = false
                                    editingAccount = null
                                    bankName = ""
                                    accountNumber = ""
                                    initialBalance = ""
                                }) { Text("Cancel") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (bankName.isNotBlank()) {
                                            val resolvedType = when (accountTypeSelected) {
                                                "Wallet" -> AccountType.WALLET
                                                "Credit Card" -> AccountType.CREDIT_CARD
                                                else -> AccountType.BANK
                                            }
                                            val initBal = initialBalance.toDoubleOrNull() ?: 0.0
                                            val currentEditing = editingAccount
                                            if (currentEditing != null) {
                                                viewModel.updateAccount(
                                                    currentEditing.copy(
                                                        accountName = accountTypeSelected,
                                                        bankName = bankName.trim(),
                                                        accountNumberMasked = accountNumber.trim(),
                                                        accountType = resolvedType,
                                                        openingBalance = initBal,
                                                        currentBalance = if (currentEditing.currentBalance != 0.0) currentEditing.currentBalance else initBal
                                                    )
                                                )
                                            } else {
                                                viewModel.addAccount(
                                                    com.example.data.local.entity.AccountEntity(
                                                        accountName = accountTypeSelected,
                                                        bankName = bankName.trim(),
                                                        accountNumberMasked = accountNumber.trim(),
                                                        accountType = resolvedType,
                                                        openingBalance = initBal,
                                                        currentBalance = initBal,
                                                        isDefault = accounts.isEmpty()
                                                    )
                                                )
                                            }
                                            viewModel.recalculateAllAccountBalances()
                                            addMode = false
                                            editingAccount = null
                                            bankName = ""
                                            accountNumber = ""
                                            initialBalance = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!addMode) {
                    TextButton(onClick = { showAccountsDialog = false }) { Text("Close") }
                }
            }
        )
    }


    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit User Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            viewModel.updateUserProfile(tempName.trim(), userProfile?.currencySymbol ?: "₹")
                            showEditNameDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomizeDashboard) {
        CustomizeDashboardDialog(
            userProfile = userProfile,
            onSave = { updatedJson ->
                viewModel.updateDashboardCardsConfig(updatedJson)
            },
            onDismiss = { showCustomizeDashboard = false }
        )
    }

    if (showUndoHistorySheet) {
        UndoHistoryDialog(
            undoHistory = undoHistory,
            onRevert = { actionId ->
                viewModel.undoAction(actionId)
            },
            onClearAll = {
                viewModel.clearUndoHistory()
            },
            onDismiss = { showUndoHistorySheet = false }
        )
    }

    UpdateDialog(
        state = updateState,
        onDismiss = { updateManager.resetState() },
        onStartDownload = { info ->
            coroutineScope.launch {
                updateManager.downloadAndPrepareApk(info)
            }
        },
        onInstallApk = { file ->
            updateManager.installApk(file)
        }
    )



    // Google Drive with Strict Email Validation Dialog
    if (showGoogleDriveDialog) {
        val registeredEmail = userProfile?.email?.trim()?.lowercase() ?: authUser?.email?.trim()?.lowercase() ?: ""
        val isConnected = driveStatus?.isConnected == true
        val connectedDriveEmail = driveStatus?.driveEmail ?: registeredEmail

        AlertDialog(
            onDismissRequest = {
                showGoogleDriveDialog = false
                driveMismatchError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isConnected) IncomeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isConnected) "Google Drive Connected" else "Connect Google Drive",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isConnected) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = IncomeGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Drive Connected",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = IncomeGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Account: $connectedDriveEmail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val currentVer = driveStatus?.currentBackupVersion ?: 1
                                Text(
                                    text = "Current Version: v${String.format(Locale.getDefault(), "%04d", currentVer)} (detailsaboutbackup.json)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IncomeGreen
                                )
                                Text(
                                    text = "Folder: Yosan-Backup/ • Snapshots: Yosan-Backup/versions/",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                driveStatus?.lastBackupAt?.let { lastAt ->
                                    val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(lastAt))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Last synced: $formattedDate",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Backups are stored as detailsaboutbackup.json in your private Yosan-Backup folder with historical recovery snapshots in versions/.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Sync & Back Up Now
                        Button(
                            onClick = {
                                isDriveBackingUp = true
                                driveMismatchError = null
                                authViewModel?.backupNow { success, msg ->
                                    isDriveBackingUp = false
                                    if (success) {
                                        statusNotificationMsg = msg ?: "Cloud backup verified successfully!"
                                    } else {
                                        driveMismatchError = msg ?: "Backup sync failed"
                                    }
                                }
                            },
                            enabled = !isDriveBackingUp && !isDriveRestoring,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDriveBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing Backup to Drive...")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync & Back Up Now", fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2. Restore Latest Backup
                        FilledTonalButton(
                            onClick = {
                                targetRestoreVersion = null
                                showRestoreConfirmDialog = true
                            },
                            enabled = !isDriveBackingUp && !isDriveRestoring,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDriveRestoring && targetRestoreVersion == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restoring from Drive...")
                            } else {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Latest Backup", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 3. Backup Version History
                        OutlinedButton(
                            onClick = {
                                showBackupHistoryDialog = true
                                isLoadingHistory = true
                                historyErrorMessage = null
                                authViewModel?.getBackupHistory { success, backups, err ->
                                    isLoadingHistory = false
                                    if (success) {
                                        backupHistoryList = backups
                                    } else {
                                        historyErrorMessage = err ?: "Could not fetch versions"
                                    }
                                }
                            },
                            enabled = !isDriveBackingUp && !isDriveRestoring,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup Version History", fontWeight = FontWeight.SemiBold)
                        }

                        // 4. Disconnect Google Drive
                        OutlinedButton(
                            onClick = {
                                authViewModel?.disconnectGoogleDrive { success, msg ->
                                    if (success) {
                                        driveAuthManager.signOut()
                                        statusNotificationMsg = "Google Drive disconnected"
                                        showGoogleDriveDialog = false
                                    } else {
                                        driveMismatchError = msg ?: "Failed to disconnect"
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect Google Drive")
                        }
                    } else {
                        Text(
                            text = "Connect Google Drive to securely back up your accounts and financial records to the cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Scope and Permissions Notice
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Restricted Permission (drive.file scope)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Yosan requests access ONLY to the private backup directory it creates ('Yosan-Backup/'). The app can NEVER read, view, or modify any of your other Google Drive files, documents, or photos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (registeredEmail.isNotBlank())
                                        "Connected account will be matched with your registered Yosan email ($registeredEmail)."
                                    else
                                        "Sign in with your Google Account to authorize private cloud backup storage.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                driveMismatchError = null
                                isDriveConnecting = true
                                val intent = driveAuthManager.getDriveSignInIntent(registeredEmail.ifBlank { null })
                                driveSignInLauncher.launch(intent)
                            },
                            enabled = !isDriveConnecting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDriveConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive", fontWeight = FontWeight.Bold)
                        }
                    }

                    driveMismatchError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoogleDriveDialog = false
                        driveMismatchError = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showDifferentDriveAccountDialog && pendingDifferentDriveAccount != null) {
        val pendingAcc = pendingDifferentDriveAccount!!
        val selectedDriveEmail = (pendingAcc.email ?: "").trim().lowercase()
        val regEmail = userProfile?.email?.trim()?.lowercase() ?: authUser?.email?.trim()?.lowercase() ?: ""

        AlertDialog(
            onDismissRequest = {
                showDifferentDriveAccountDialog = false
                pendingDifferentDriveAccount = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Use Different Account for Drive?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are signed in to Yosan as:\n$regEmail",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "You selected for Google Drive backup:\n$selectedDriveEmail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Do you want to use $selectedDriveEmail as your backup destination? Your application identity will remain under $regEmail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDifferentDriveAccountDialog = false
                        connectDriveForAccount(pendingAcc)
                        pendingDifferentDriveAccount = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm & Use This Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDifferentDriveAccountDialog = false
                        pendingDifferentDriveAccount = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentClaims = pendingMigrationClaims
    if (showLinkOfflineConfirmDialog && currentClaims != null) {
        AlertDialog(
            onDismissRequest = {
                showLinkOfflineConfirmDialog = false
                pendingMigrationClaims = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Link Offline Profile to Google?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Google Sign-In verified for:\n${currentClaims.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Would you like to link your existing offline financial data (accounts, transactions, loans) to this Google account?\n\nAll existing records on this phone will be safely preserved and synchronized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLinkOfflineConfirmDialog = false
                        authViewModel?.linkOfflineProfileToGoogle(currentClaims) { success, msg ->
                            if (success) {
                                statusNotificationMsg = msg ?: "Profile linked to ${currentClaims.email}"
                            } else {
                                driveMismatchError = msg ?: "Failed to link profile"
                            }
                        }
                        pendingMigrationClaims = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Link & Keep My Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLinkOfflineConfirmDialog = false
                        pendingMigrationClaims = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Google Drive Backup History Dialog
    if (showBackupHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showBackupHistoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Backup Version History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = {
                            isLoadingHistory = true
                            historyErrorMessage = null
                            authViewModel?.getBackupHistory { success, backups, err ->
                                isLoadingHistory = false
                                if (success) {
                                    backupHistoryList = backups
                                } else {
                                    historyErrorMessage = err ?: "Could not fetch versions"
                                }
                            }
                        }
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
                        text = "Google Drive: Yosan-Backup/versions/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingHistory) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (historyErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = historyErrorMessage ?: "Error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else if (backupHistoryList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No backup snapshots found in Google Drive.\nCreate your first backup to see version history.",
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
                            items(backupHistoryList.size) { index ->
                                val item = backupHistoryList[index]
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isCurrent)
                                            IncomeGreen.copy(alpha = 0.08f)
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
                                                    tint = if (item.isCurrent) IncomeGreen else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Version v${String.format(Locale.getDefault(), "%04d", item.version)}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Surface(
                                                color = if (item.isCurrent) IncomeGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isCurrent) "CURRENT" else "SNAPSHOT",
                                                    color = if (item.isCurrent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
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

                                        if (item.transactionCount > 0) {
                                            Text(
                                                text = "Records: ${item.transactionCount} transactions",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        if (!item.checksum.isNullOrBlank()) {
                                            Text(
                                                text = "Checksum: ${item.checksum.take(12)}...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                targetRestoreVersion = item.version
                                                showRestoreConfirmDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (item.isCurrent) IncomeGreen else MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (item.isCurrent) "Restore Current Backup" else "Restore Snapshot v${item.version}",
                                                style = MaterialTheme.typography.labelMedium
                                            )
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
                TextButton(onClick = { showBackupHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Google Drive Restore Confirmation Dialog
    if (showRestoreConfirmDialog) {
        val ver = targetRestoreVersion
        AlertDialog(
            onDismissRequest = {
                if (!isDriveRestoring) showRestoreConfirmDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (ver != null) "Restore Snapshot v$ver?" else "Restore Latest Backup?",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (ver != null)
                            "You are about to restore your financial database to historical snapshot v$ver from Google Drive."
                        else
                            "You are about to restore your financial database from the latest Google Drive backup (detailsaboutbackup.json).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will update your local database records. All operations are transactional and verified before applying.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDriveRestoring = true
                        if (ver != null) {
                            authViewModel?.restoreBackupVersion(ver) { success, msg ->
                                isDriveRestoring = false
                                showRestoreConfirmDialog = false
                                if (success) {
                                    statusNotificationMsg = msg ?: "Snapshot v$ver restored successfully!"
                                    showBackupHistoryDialog = false
                                    showGoogleDriveDialog = false
                                } else {
                                    driveMismatchError = msg ?: "Failed to restore version $ver"
                                }
                            }
                        } else {
                            authViewModel?.restoreLatestBackup { success, msg ->
                                isDriveRestoring = false
                                showRestoreConfirmDialog = false
                                if (success) {
                                    statusNotificationMsg = msg ?: "Latest backup restored successfully!"
                                    showGoogleDriveDialog = false
                                } else {
                                    driveMismatchError = msg ?: "Failed to restore latest backup"
                                }
                            }
                        }
                    },
                    enabled = !isDriveRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isDriveRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restoring...")
                    } else {
                        Text("Confirm Restore")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmDialog = false },
                    enabled = !isDriveRestoring
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Formatted Feedback / Bug Report Dialog
    if (showFeedbackDialog) {
        val categoriesList = listOf(
            "BUG_REPORT" to "Bug Report",
            "STATEMENT_PARSING" to "Statement Parsing",
            "FEATURE_REQUEST" to "Feature Request",
            "GENERAL" to "General Feedback"
        )

        AlertDialog(
            onDismissRequest = {
                if (!isFeedbackSubmitting) {
                    showFeedbackDialog = false
                    feedbackErrorMessage = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Send Feedback", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Category Selection Chips
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.forEach { (key, label) ->
                            FilterChip(
                                selected = feedbackCategory == key,
                                onClick = { feedbackCategory = key },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // 2. Star Rating (1 to 5)
                    Column {
                        Text(
                            text = "Rating",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..5).forEach { starIndex ->
                                IconButton(
                                    onClick = { feedbackRating = starIndex },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (starIndex <= feedbackRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "$starIndex Stars",
                                        tint = if (starIndex <= feedbackRating) Color(0xFFFFB800) else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$feedbackRating / 5",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Subject Field
                    OutlinedTextField(
                        value = feedbackSubject,
                        onValueChange = {
                            feedbackSubject = it
                            feedbackErrorMessage = null
                        },
                        label = { Text("Subject") },
                        placeholder = { Text("Brief summary of your feedback") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = feedbackErrorMessage != null && feedbackSubject.trim().length < 3
                    )

                    // 4. Description Field
                    OutlinedTextField(
                        value = feedbackDescription,
                        onValueChange = {
                            feedbackDescription = it
                            feedbackErrorMessage = null
                        },
                        label = { Text("Description") },
                        placeholder = { Text("Please provide detailed feedback or steps to reproduce the issue...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        isError = feedbackErrorMessage != null && feedbackDescription.trim().length < 5
                    )

                    // 5. Technical Diagnostics Checkbox
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = feedbackIncludeMetadata,
                                    onCheckedChange = { feedbackIncludeMetadata = it },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Include device & app diagnostic info",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (feedbackIncludeMetadata) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 32.dp)
                                )
                            }
                        }
                    }

                    // Error text
                    feedbackErrorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.utils.HapticHelper.vibrateClick(context)
                        val sub = feedbackSubject.trim()
                        val desc = feedbackDescription.trim()

                        if (sub.length < 3) {
                            feedbackErrorMessage = "Subject must be at least 3 characters."
                            return@Button
                        }
                        if (desc.length < 5) {
                            feedbackErrorMessage = "Description must be at least 5 characters."
                            return@Button
                        }

                        // Fire and forget server logging
                        viewModel.submitFeedback(
                            category = feedbackCategory,
                            rating = feedbackRating,
                            subject = sub,
                            description = desc,
                            includeMetadata = feedbackIncludeMetadata,
                            onSuccess = {},
                            onError = {}
                        )

                        // Launch email draft to yosanfinance@gmail.com with fully URL-encoded parameters for mailto:
                        val body = buildString {
                            appendLine("Category: $feedbackCategory")
                            appendLine("Rating: $feedbackRating / 5")
                            appendLine("Subject: $sub")
                            appendLine()
                            appendLine("Description:")
                            appendLine(desc)
                            if (feedbackIncludeMetadata) {
                                appendLine()
                                appendLine("--- Device Diagnostic Info ---")
                                appendLine("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                            }
                        }
                        val subjectText = "[Yosan Feedback - $feedbackCategory] $sub"
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:yosanfinance@gmail.com")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("yosanfinance@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, subjectText)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }

                        try {
                            viewModel.setExternalIntentActive(true)
                            val chooser = Intent.createChooser(emailIntent, "Send Feedback to yosanfinance@gmail.com").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                            showFeedbackDialog = false
                            feedbackSubject = ""
                            feedbackDescription = ""
                            feedbackErrorMessage = null
                            statusNotificationMsg = "Opening email to yosanfinance@gmail.com..."
                        } catch (e: Exception) {
                            feedbackErrorMessage = "Could not open email app. Please send directly to yosanfinance@gmail.com"
                        }
                    },
                    enabled = feedbackSubject.trim().length >= 3 && feedbackDescription.trim().length >= 5
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Feedback")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        com.example.utils.HapticHelper.vibrateClick(context)
                        showFeedbackDialog = false
                        feedbackErrorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showServerConfigDialog) {
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = { Text("Backend Server URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Configure the backend API server endpoint for authentication and synchronization:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedServerUrl,
                        onValueChange = { editedServerUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text(currentServerUrl) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.data.remote.ApiClient.setCustomBaseUrl(context, editedServerUrl.ifBlank { null })
                        currentServerUrl = com.example.data.remote.ApiClient.getBaseUrl(context)
                        showServerConfigDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        com.example.data.remote.ApiClient.setCustomBaseUrl(context, null)
                        currentServerUrl = com.example.data.remote.ApiClient.getBaseUrl(context)
                        showServerConfigDialog = false
                    }
                ) {
                    Text("Reset Default")
                }
            }
        )
    }

    // Reassignment Dialog for Account Deletion
    accountToDeleteWithReassign?.let { targetAcc ->
        val otherAccounts = accounts.filter { it.id != targetAcc.id }
        var selectedFallbackId by remember(targetAcc) { mutableStateOf(otherAccounts.firstOrNull()?.id ?: 1L) }
        val linkedCount = allTransactions.count { it.accountId == targetAcc.id }

        AlertDialog(
            onDismissRequest = { accountToDeleteWithReassign = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Reassign Transactions Before Delete", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Account '${targetAcc.bankName}' has $linkedCount active transaction records.\n\nTo preserve your balance history and financial integrity, select the account to reassign these records to:"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Fallback Account:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        otherAccounts.forEach { other ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFallbackId = other.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedFallbackId == other.id,
                                    onClick = { selectedFallbackId = other.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(other.bankName.ifBlank { other.accountName }, fontWeight = FontWeight.SemiBold)
                                    Text(other.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccountWithReassignment(targetAcc.id, selectedFallbackId)
                        accountToDeleteWithReassign = null
                        showAccountsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reassign & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDeleteWithReassign = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Management Dialog
    if (showCategoryDialog) {
        CategoryManagementDialog(
            categories = categories,
            allTransactions = allTransactions,
            onAddCategory = { name, type, colorHex ->
                viewModel.addCategory(name, type, colorHex)
            },
            onUpdateCategory = { cat ->
                viewModel.updateCategory(cat)
            },
            onDeleteCategory = { id ->
                viewModel.deleteCategory(id)
            },
            onReassignAndDelete = { id, fallbackId, fallbackName ->
                viewModel.reassignAndDeleteCategory(id, fallbackId, fallbackName)
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Developer Details Dialog
    if (showDeveloperDetailsDialog) {
        DeveloperDetailsDialog(
            onOpenWalkthrough = {
                showDeveloperDetailsDialog = false
                onOpenWalkthrough()
            },
            onOpenIntroNote = {
                showDeveloperDetailsDialog = false
                onOpenIntroNote()
            },
            onDismiss = { showDeveloperDetailsDialog = false }
        )
    }
}

@Composable
fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconColor.copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeDashboardDialog(
    userProfile: UserProfileEntity?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cards by remember(userProfile?.dashboardCardsConfig) {
        mutableStateOf(DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig))
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Customize Dashboard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Reorder cards using the arrows or toggle switches to customize your Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                cards.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isEnabled) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Card Title & Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.type.icon,
                                            contentDescription = null,
                                            tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.type.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.type.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Up/Down reorder controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (index < cards.size - 1) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index < cards.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (index < cards.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Toggle Switch
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { checked ->
                                        val newList = cards.toMutableList()
                                        newList[index] = item.copy(isEnabled = checked)
                                        cards = newList
                                        onSave(DashboardCardConfigManager.serialize(newList))
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        cards = DashboardCardConfigManager.DEFAULT_CONFIG
                        onSave(DashboardCardConfigManager.serialize(DashboardCardConfigManager.DEFAULT_CONFIG))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset to Default Layout")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UndoHistoryDialog(
    undoHistory: List<UndoHistoryEntity>,
    onRevert: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Undo History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all recorded action history. Transactions and rules will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = LendingIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Undo & Recovery", fontWeight = FontWeight.Bold)
                }
                if (undoHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (undoHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Recent Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Edits, deletions, and rule applications will appear here for safe recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(undoHistory.size, key = { undoHistory[it].actionId }) { index ->
                        val action = undoHistory[index]
                        val icon = when (action.actionType) {
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> Icons.Default.Category
                            "DELETE_TRANSACTION", "BULK_DELETE" -> Icons.Default.DeleteOutline
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> Icons.Default.AutoAwesome
                            "CHANGE_TYPE" -> Icons.Default.SyncAlt
                            else -> Icons.Default.Edit
                        }
                        val iconTint = when (action.actionType) {
                            "DELETE_TRANSACTION", "BULK_DELETE" -> ExpenseRed
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> LendingIndigo
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val timeStr = remember(action.timestamp) {
                            val diff = System.currentTimeMillis() - action.timestamp
                            when {
                                diff < 60_000L -> "Just now"
                                diff < 3600_000L -> "${diff / 60_000L}m ago"
                                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(action.timestamp))
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = iconTint.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = action.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onRevert(action.actionId) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Revert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManagementDialog(
    categories: List<CategoryEntity>,
    allTransactions: List<com.example.data.local.entity.TransactionEntity>,
    onAddCategory: (String, CategoryType, String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onReassignAndDelete: (Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<CategoryType?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDeleteWithReassign by remember { mutableStateOf<CategoryEntity?>(null) }

    val filteredCategories = remember(categories, selectedFilter) {
        if (selectedFilter == null) categories else categories.filter { it.type == selectedFilter }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Categories", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showAddCategoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Category",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category Type Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${categories.size})") }
                    )
                    CategoryType.values().forEach { cType ->
                        val count = categories.count { it.type == cType }
                        FilterChip(
                            selected = selectedFilter == cType,
                            onClick = { selectedFilter = cType },
                            label = { Text("${cType.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredCategories.size) { idx ->
                        val cat = filteredCategories[idx]
                        val txCount = remember(cat, allTransactions) {
                            allTransactions.count { it.categoryId == cat.id || it.categoryName.equals(cat.name, ignoreCase = true) }
                        }
                        val colorParsed = remember(cat.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF10B981)
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = colorParsed,
                                        shape = CircleShape,
                                        modifier = Modifier.size(16.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (cat.isSystem) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "System",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${cat.type.name} • $txCount transactions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingCategory = cat },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Category",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!cat.isSystem) {
                                                if (txCount > 0) {
                                                    categoryToDeleteWithReassign = cat
                                                } else {
                                                    onDeleteCategory(cat.id)
                                                }
                                            }
                                        },
                                        enabled = !cat.isSystem,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Category",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (cat.isSystem) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || editingCategory != null) {
        val isEditing = editingCategory != null
        var catName by remember { mutableStateOf(editingCategory?.name ?: "") }
        var catType by remember { mutableStateOf(editingCategory?.type ?: CategoryType.EXPENSE) }
        var catColor by remember { mutableStateOf(editingCategory?.colorHex ?: "#3B82F6") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val palette = listOf(
            "#EF4444", "#10B981", "#3B82F6", "#8B5CF6",
            "#F59E0B", "#EC4899", "#06B6D4", "#64748B", "#14B8A6"
        )

        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                editingCategory = null
            },
            title = {
                Text(if (isEditing) "Edit Category" else "Add New Category", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = {
                            catName = it
                            errorMessage = null
                        },
                        label = { Text("Category Name") },
                        placeholder = { Text("e.g. Subscriptions") },
                        singleLine = true,
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Text("Classification Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.INVESTMENT, CategoryType.OTHER).forEach { t ->
                            FilterChip(
                                selected = catType == t,
                                onClick = { catType = t },
                                label = { Text(t.name) }
                            )
                        }
                    }

                    Text("Category Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSel = catColor.equals(hex, ignoreCase = true)
                            Surface(
                                color = c,
                                shape = CircleShape,
                                border = if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { catColor = hex }
                            ) {}
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = catName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Category name cannot be empty"
                            return@Button
                        }
                        val duplicate = categories.any {
                            it.name.trim().equals(trimmed, ignoreCase = true) && (!isEditing || it.id != editingCategory?.id)
                        }
                        if (duplicate) {
                            errorMessage = "Category '$trimmed' already exists"
                            return@Button
                        }
                        if (isEditing) {
                            editingCategory?.let { current ->
                                onUpdateCategory(
                                    current.copy(
                                        name = trimmed,
                                        type = catType,
                                        colorHex = catColor,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            onAddCategory(trimmed, catType, catColor)
                        }
                        showAddCategoryDialog = false
                        editingCategory = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEditing) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    editingCategory = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Deletion with Reassignment Dialog
    categoryToDeleteWithReassign?.let { targetCat ->
        val candidateFallbacks = remember(categories, targetCat) {
            categories.filter { it.id != targetCat.id }
        }
        var selectedFallbackId by remember(targetCat) {
            mutableStateOf(candidateFallbacks.firstOrNull()?.id ?: 1L)
        }
        val count = allTransactions.count { it.categoryId == targetCat.id || it.categoryName.equals(targetCat.name, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { categoryToDeleteWithReassign = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Reassign Category Records", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Category '${targetCat.name}' has $count linked transactions.\n\nTo prevent orphan transactions or calculation distortion, choose the category to reassign these records to:"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Target Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        candidateFallbacks.forEach { fallback ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFallbackId = fallback.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedFallbackId == fallback.id,
                                    onClick = { selectedFallbackId = fallback.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(fallback.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fallbackName = candidateFallbacks.firstOrNull { it.id == selectedFallbackId }?.name ?: "General"
                        onReassignAndDelete(targetCat.id, selectedFallbackId, fallbackName)
                        categoryToDeleteWithReassign = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reassign & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteWithReassign = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeveloperDetailsDialog(
    onOpenWalkthrough: () -> Unit,
    onOpenIntroNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Details About the Developer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Application", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Yosan Finance Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version v${BuildConfig.VERSION_NAME} • Release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Engineering Team", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Rooparaj & Gokulraj", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Architected with an offline-first, privacy-respecting methodology using Jetpack Compose, Room SQLite, Material 3 design system, and multi-threaded financial reconciliation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}




