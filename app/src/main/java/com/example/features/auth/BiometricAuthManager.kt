package com.example.features.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricCapability {
    READY,
    NOT_ENROLLED,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    SECURITY_UPDATE_REQUIRED,
    UNKNOWN
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    // Primary authenticators: Strong biometric or device PIN/pattern
    private val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

    fun checkCapability(): BiometricCapability {
        val authResult = biometricManager.canAuthenticate(authenticators)
        return when (authResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // If strong biometrics not enrolled, verify whether device lock (PIN/pattern) is configured
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (km?.isDeviceSecure == true) {
                    BiometricCapability.READY
                } else {
                    BiometricCapability.NOT_ENROLLED
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (km?.isDeviceSecure == true) {
                    BiometricCapability.READY
                } else {
                    BiometricCapability.NO_HARDWARE
                }
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricCapability.SECURITY_UPDATE_REQUIRED
            else -> {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (km?.isDeviceSecure == true) {
                    BiometricCapability.READY
                } else {
                    BiometricCapability.UNKNOWN
                }
            }
        }
    }

    fun isBiometricAvailable(): Boolean {
        val cap = checkCapability()
        return cap == BiometricCapability.READY
    }

    fun hasEnrolledBiometrics(): Boolean {
        return biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Yosan",
        subtitle: String = "Verify your fingerprint or phone PIN",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onUsePin: (() -> Unit)? = null,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && onUsePin != null) {
                    onUsePin()
                } else {
                    onError(errorCode, errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, promptCallback)

        // Standard Android Jetpack combination: BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        // Enables under-display fingerprint sensor natively alongside the "Use PIN / Pattern" button
        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // Fallback for OEMs / older Android versions requiring explicit negative button
            try {
                val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(if (onUsePin != null) "Use PIN / Password" else "Cancel")
                    .build()
                prompt.authenticate(fallbackPromptInfo)
            } catch (ex: Exception) {
                onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, ex.localizedMessage ?: "Unable to start biometric prompt")
            }
        }
    }

    /**
     * Backward-compatible alias for authenticate() that passes through title, subtitle, and pin handlers.
     */
    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Yosan",
        subtitle: String = "Verify your fingerprint or phone PIN",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onUsePin: (() -> Unit)? = null,
        onFailed: () -> Unit = {}
    ) {
        authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = onSuccess,
            onError = onError,
            onUsePin = onUsePin,
            onFailed = onFailed
        )
    }

    fun openEnrollmentSettings(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        authenticators
                    )
                }
                if (enrollIntent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(enrollIntent)
                    return
                }
            }
            val secIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            if (secIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(secIntent)
            } else {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }
}
