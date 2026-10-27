package com.example.features.auth

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LendingIndigo
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel
import com.example.data.remote.LatestBackupResponse
import com.example.data.remote.ApiClient
import com.example.features.walkthrough.UserWalkthroughDialog
import com.example.utils.HapticHelper
import kotlinx.coroutines.launch

val POPULAR_INDIAN_BANKS = listOf(
    "Indian Bank",
    "State Bank",
    "YES Bank",
    "IndusInd Bank"
)

@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModel,
    financeViewModel: FinanceViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: Int = 1
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = remember { com.example.utils.AppPreferences(context) }
    val accounts by financeViewModel.accounts.collectAsState()

    var step by remember { mutableStateOf(initialStep) }
    var isOfflineModeSelected by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(appPreferences.lastUserName) }
    var currencySymbol by remember { mutableStateOf("₹") }
    var selectedThemeMode by remember { mutableStateOf("SYSTEM") }
    var isPrivacyBlurEnabled by remember { mutableStateOf(true) }
    var blurTimeoutSeconds by remember { mutableStateOf(5) }
    var selectedStorageMode by remember { mutableStateOf(com.example.features.sync.StorageMode.LOCAL_ONLY) }
    var selectedBankName by remember {
        mutableStateOf(
            appPreferences.lastBankName.takeIf { it in POPULAR_INDIAN_BANKS }
                ?: accounts.firstOrNull()?.bankName?.takeIf { it in POPULAR_INDIAN_BANKS }
                ?: "Indian Bank"
        )
    }
    var customBankName by remember { mutableStateOf("") }
    var accountTypeSelected by remember { mutableStateOf("Savings") }

    var emailInput by remember { mutableStateOf(appPreferences.lastUserEmail) }
    val rememberedAccounts by authViewModel.rememberedAccounts.collectAsState()
    var accountToRemove by remember { mutableStateOf<com.example.utils.RememberedAccount?>(null) }

    var showBackupFoundDialog by remember { mutableStateOf(false) }
    var foundBackupInfo by remember { mutableStateOf<LatestBackupResponse?>(null) }
    var isCheckingBackup by remember { mutableStateOf(false) }
    var isRestoringFromBackup by remember { mutableStateOf(false) }
    var restoreStatusMessage by remember { mutableStateOf<String?>(null) }

    val accountPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                val cleanEmail = selectedEmail.trim().lowercase()
                val currentBank = appPreferences.lastBankName.ifBlank {
                    accounts.firstOrNull()?.bankName ?: "Indian Bank"
                }
                val autoName = cleanEmail.substringBefore("@").replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                authViewModel.signInWithGoogleAccount(
                    email = cleanEmail,
                    name = autoName,
                    bankName = currentBank
                ) { claims ->
                    userName = claims.name
                    emailInput = claims.email
                    financeViewModel.updateUserProfile(userName, currencySymbol)
                    if (accounts.isNotEmpty()) {
                        appPreferences.isLoggedOut = false
                        appPreferences.isOnboardingCompleted = true
                        authViewModel.completeOnboarding()
                        onComplete()
                    } else {
                        isCheckingBackup = true
                        authViewModel.checkDriveOrCloudBackup(claims.email) { hasBackup, backupResp ->
                            isCheckingBackup = false
                            if (hasBackup && backupResp != null) {
                                foundBackupInfo = backupResp
                                showBackupFoundDialog = true
                            } else {
                                step = 2
                            }
                        }
                    }
                }
            }
        }
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
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = tween(350)) { width -> width } + fadeIn(animationSpec = tween(350))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(350)) { width -> -width } + fadeOut(animationSpec = tween(350))
                            )
                        } else {
                            (slideInHorizontally(animationSpec = tween(350)) { width -> -width } + fadeIn(animationSpec = tween(350))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(350)) { width -> width } + fadeOut(animationSpec = tween(350))
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "OnboardingStepTransition"
                ) { currentStep ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (currentStep) {
                            0 -> {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Welcome to Yosan Finance",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Introduction & Overview",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Yosan Finance helps you keep your financial records organized in one place. Record and review personal expenses, official/company expenses, income, money borrowed, money lent, investments, and transfers using the features available in the app.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Use the Dashboard to review your recorded activity and spending categories. Use Settings to manage supported categories, appearance and privacy options, accounts, backup and restore, and other app preferences.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Why use Yosan Finance Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Why use Yosan Finance?",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val reasons = listOf(
                                            "Keep financial records organized.",
                                            "Separate personal and official expenses.",
                                            "Review spending and income by category.",
                                            "Track money borrowed and lent where supported.",
                                            "Organize investment entries and transfers.",
                                            "Back up or move supported records using the available backup and import/export features."
                                        )
                                        reasons.forEach { reason ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 3.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = IncomeGreen,
                                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = reason,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Getting started Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Getting started:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val steps = listOf(
                                            "1. Review your profile and available Settings.",
                                            "2. Set up the categories and accounts you need.",
                                            "3. Add or import a small set of records, using supported features.",
                                            "4. Review the Dashboard and verify that the displayed totals match your records.",
                                            "5. Set up backup or connected services only if you want to use them and the feature is available."
                                        )
                                        steps.forEach { stepText ->
                                            Text(
                                                text = stepText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Privacy Note Surface
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = LendingIndigo.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, LendingIndigo.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = LendingIndigo,
                                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Privacy note:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = LendingIndigo
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Yosan Finance may use device features or connected services for functions such as screen lock, sign-in, or file import/export. The app explains each permission when it becomes relevant. Review the permission request and the app's privacy policy before granting access.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Disclaimer Footer
                                Text(
                                    text = "Your summaries depend on the accuracy of the records entered or imported. Unless explicitly supported by the installed version, do not assume that the app automatically connects to banks, verifies transactions, provides live investment values, or offers financial advice.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = "Choose your operating mode to get started:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Mode Choice 1: Online Mode
                                Button(
                                    onClick = {
                                        isOfflineModeSelected = false
                                        step = 1
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Continue with Online Mode",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Mode Choice 2: Offline Mode
                                OutlinedButton(
                                    onClick = {
                                        isOfflineModeSelected = true
                                        step = 1
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
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
                                        text = "Continue with Offline Mode",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { showWalkthroughDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LendingIndigo)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Explore Walkthrough Guide", fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            1 -> {
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

                                Spacer(modifier = Modifier.height(16.dp))

                                if (rememberedAccounts.isNotEmpty()) {
                                    Text(
                                        text = "Accounts on this device",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rememberedAccounts.forEach { acc ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        HapticHelper.vibrateClick(context)
                                                        authViewModel.switchAccount(acc.email) {
                                                            if (appPreferences.isOnboardingCompleted || accounts.isNotEmpty()) {
                                                                appPreferences.isLoggedOut = false
                                                                appPreferences.isOnboardingCompleted = true
                                                                authViewModel.completeOnboarding()
                                                                onComplete()
                                                            } else {
                                                                userName = acc.name.ifBlank { "User" }
                                                                emailInput = acc.email
                                                                step = 2
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = acc.name.take(1).uppercase().ifBlank { "U" },
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = acc.name.ifBlank { "User" },
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = acc.email.ifBlank { "Offline Profile" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (acc.bankName.isNotBlank()) {
                                                            Text(
                                                                text = "Bank: ${acc.bankName}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            HapticHelper.vibrateClick(context)
                                                            accountToRemove = acc
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove account",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Or add another account:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // 1. Primary Action: Continue with Google
                                Button(
                                    onClick = {
                                        HapticHelper.vibrateClick(context)
                                        authViewModel.signInWithGoogle(
                                            context = context,
                                            onFallbackPicker = {
                                                val googleAuthManager = GoogleAuthManager(context)
                                                val pickerIntent = googleAuthManager.createSystemAccountPickerIntent()
                                                if (pickerIntent != null) {
                                                    try {
                                                        accountPickerLauncher.launch(pickerIntent)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        ) { claims ->
                                            userName = claims.name.ifBlank { claims.email.substringBefore("@") }
                                            emailInput = claims.email
                                            financeViewModel.updateUserProfile(userName, currencySymbol)
                                            if (accounts.isNotEmpty()) {
                                                appPreferences.isLoggedOut = false
                                                appPreferences.isOnboardingCompleted = true
                                                authViewModel.completeOnboarding()
                                                onComplete()
                                            } else {
                                                isCheckingBackup = true
                                                authViewModel.checkDriveOrCloudBackup(claims.email) { hasBackup, backupResp ->
                                                    isCheckingBackup = false
                                                    if (hasBackup && backupResp != null) {
                                                        foundBackupInfo = backupResp
                                                        showBackupFoundDialog = true
                                                    } else {
                                                        step = 2
                                                    }
                                                }
                                            }
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
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
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

                                Spacer(modifier = Modifier.height(14.dp))

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
                                        if (accounts.isNotEmpty()) {
                                            appPreferences.isOfflineMode = true
                                            appPreferences.isLoggedOut = false
                                            appPreferences.isOnboardingCompleted = true
                                            authViewModel.completeOnboarding()
                                            onComplete()
                                        } else {
                                            if (userName.isBlank()) {
                                                userName = "User"
                                            }
                                            financeViewModel.updateUserProfile(userName, currencySymbol)
                                            step = 2
                                        }
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

                                authError?.let { err ->
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
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
                                                text = err,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
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
                                            restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
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

                            2 -> {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select Your Primary Bank",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select your Indian bank account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { userName = it },
                                    label = { Text("Display Name") },
                                    placeholder = { Text("Enter your name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    POPULAR_INDIAN_BANKS.forEach { bank ->
                                        val isSelected = selectedBankName == bank
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                HapticHelper.vibrateTick(context)
                                                selectedBankName = bank
                                            },
                                            label = { Text(bank, maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            modifier = Modifier.fillMaxWidth(),
                                            leadingIcon = {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Account Type",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val types = listOf("Savings", "Current", "Salary", "Wallet", "Credit Card")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    types.forEach { type ->
                                        val isSelected = accountTypeSelected == type
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                HapticHelper.vibrateTick(context)
                                                accountTypeSelected = type
                                            },
                                            label = { Text(type) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            3 -> {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = LendingIndigo,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Personal Preferences",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure visual theme and privacy blur",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Theme Preference
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedThemeMode == "SYSTEM",
                                        onClick = {
                                            HapticHelper.vibrateTick(context)
                                            selectedThemeMode = "SYSTEM"
                                            financeViewModel.setThemeMode("SYSTEM")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.SettingsBrightness,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("System", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectedThemeMode == "LIGHT",
                                        onClick = {
                                            HapticHelper.vibrateTick(context)
                                            selectedThemeMode = "LIGHT"
                                            financeViewModel.setThemeMode("LIGHT")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Light", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectedThemeMode == "DARK",
                                        onClick = {
                                            HapticHelper.vibrateTick(context)
                                            selectedThemeMode = "DARK"
                                            financeViewModel.setThemeMode("DARK")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DarkMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Dark", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Financial Privacy Blur
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Financial Privacy Blur",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Mask amounts; tap to reveal temporarily",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isPrivacyBlurEnabled,
                                        onCheckedChange = { isPrivacyBlurEnabled = it }
                                    )
                                }

                                if (isPrivacyBlurEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3, 5, 10, 15).forEach { sec ->
                                            FilterChip(
                                                selected = blurTimeoutSeconds == sec,
                                                onClick = { blurTimeoutSeconds = sec },
                                                label = { Text("${sec}s") }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Data Storage Preference",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                if (isOfflineModeSelected) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Local Device Storage",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Your financial data is encrypted and kept exclusively on this device.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Choose where your financial records are saved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedStorageMode == com.example.features.sync.StorageMode.LOCAL_ONLY,
                                            onClick = { selectedStorageMode = com.example.features.sync.StorageMode.LOCAL_ONLY },
                                            label = { Text("Local Only", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = selectedStorageMode == com.example.features.sync.StorageMode.GOOGLE_DRIVE,
                                            onClick = { selectedStorageMode = com.example.features.sync.StorageMode.GOOGLE_DRIVE },
                                            label = { Text("Google Drive", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = selectedStorageMode == com.example.features.sync.StorageMode.LOCAL_PLUS_DRIVE,
                                            onClick = { selectedStorageMode = com.example.features.sync.StorageMode.LOCAL_PLUS_DRIVE },
                                            label = { Text("Local + Drive", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            4 -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "You're All Set!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isOfflineModeSelected) {
                                        "Your offline profile is ready. Your data stays 100% private on this device. You can now start managing accounts and importing bank statements."
                                    } else {
                                        "Your account is initialized. You can now import bank statements directly into Yosan."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons with strict validation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step == 2) {
                        TextButton(onClick = {
                            HapticHelper.vibrateClick(context)
                            step = 1
                        }) {
                            Text("Back")
                        }

                        val finalBank = selectedBankName.trim()
                        val isNextEnabled = finalBank.isNotBlank()

                        Button(
                            onClick = {
                                HapticHelper.vibrateClick(context)
                                val bank = finalBank.ifBlank { "Indian Bank" }
                                val finalName = userName.trim().ifBlank { "User" }
                                coroutineScope.launch {
                                    val existingAcc = accounts.firstOrNull { it.bankName.equals(bank, ignoreCase = true) }
                                    if (existingAcc == null) {
                                        financeViewModel.addAccount(
                                            AccountEntity(
                                                accountName = accountTypeSelected,
                                                bankName = bank,
                                                accountNumberMasked = "",
                                                accountType = when (accountTypeSelected) {
                                                    "Wallet" -> AccountType.WALLET
                                                    "Credit Card" -> AccountType.CREDIT_CARD
                                                    else -> AccountType.BANK
                                                },
                                                openingBalance = 0.0,
                                                currentBalance = 0.0,
                                                isDefault = true
                                            )
                                        )
                                    } else {
                                        financeViewModel.updateAccount(existingAcc.copy(isDefault = true))
                                    }
                                    financeViewModel.completeOnboarding(
                                        themeMode = selectedThemeMode,
                                        privacyBlurEnabled = isPrivacyBlurEnabled,
                                        timeoutSeconds = blurTimeoutSeconds
                                    )
                                    financeViewModel.updateUserProfile(finalName, currencySymbol)
                                    authViewModel.setStoragePreference(
                                        if (isOfflineModeSelected) com.example.features.sync.StorageMode.LOCAL_ONLY else selectedStorageMode
                                    )
                                    val prefs = com.example.utils.AppPreferences(context)
                                    prefs.isOfflineMode = isOfflineModeSelected
                                    prefs.isLoggedOut = false
                                    prefs.isOnboardingCompleted = true
                                    prefs.lastBankName = bank
                                    authViewModel.completeOnboarding()
                                    onComplete()
                                }
                            },
                            enabled = isNextEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Get Started",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (step > 2) {
                        TextButton(onClick = { step-- }) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                val prefs = com.example.utils.AppPreferences(context)
                                prefs.isOfflineMode = isOfflineModeSelected
                                prefs.isLoggedOut = false
                                prefs.isOnboardingCompleted = true
                                authViewModel.completeOnboarding()
                                onComplete()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Go to Dashboard", fontWeight = FontWeight.Bold)
                        }
                    }
                }

            }
        }
    }

    if (accountToRemove != null) {
        val targetAcc = accountToRemove!!
        val displayName = targetAcc.name.ifBlank { targetAcc.email.ifBlank { "Offline Profile" } }
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Remove Account?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"$displayName\" from this device? Your local saved session and offline login on this device will be cleared.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticHelper.vibrateClick(context)
                        authViewModel.removeAccount(targetAcc.email) {
                            accountToRemove = null
                        }
                        accountToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackupFoundDialog && foundBackupInfo != null) {
        val backup = foundBackupInfo!!
        AlertDialog(
            onDismissRequest = { /* Require explicit choice */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = IncomeGreen.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = IncomeGreen,
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
                            Text(
                                text = "📧 ${emailInput.ifBlank { authViewModel.user.value?.email ?: "your account" }}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            backup.backupDate?.let { dateStr ->
                                Text(
                                    text = "📅 Last Backup: $dateStr",
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
                                    authViewModel.completeOnboarding()
                                    onComplete()
                                } else {
                                    restoreStatusMessage = "Failed to restore backup. You can continue manually."
                                }
                            }
                        }
                    },
                    enabled = !isRestoringFromBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
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
                        step = 2 // Smoothly proceed to standard new account creation
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
