package com.example.features.auth

import android.app.Activity
import android.content.Context
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

class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    private val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

    fun checkCapability(): BiometricCapability {
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricCapability.SECURITY_UPDATE_REQUIRED
            else -> BiometricCapability.UNKNOWN
        }
    }

    fun isBiometricAvailable(): Boolean {
        val cap = checkCapability()
        return cap == BiometricCapability.READY
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Yosan",
        subtitle: String = "Verify your fingerprint, face, or phone PIN",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
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
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, promptCallback)

        // Note: Do NOT set setNegativeButtonText when DEVICE_CREDENTIAL is included in allowedAuthenticators!
        // Calling setNegativeButtonText with DEVICE_CREDENTIAL throws IllegalArgumentException on Android.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Unable to start authentication")
        }
    }

    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Yosan",
        subtitle: String = "Verify your fingerprint or face",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
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
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, promptCallback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // Fallback to general authenticate if biometric only throws
            authenticate(activity, title, subtitle, onSuccess, onError, onFailed)
        }
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
