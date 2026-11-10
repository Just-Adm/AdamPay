package com.adampay.app.core.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Fingerprint unlock with the platform BiometricPrompt (Android 11+), so no extra library and no
 * FragmentActivity. Older phones simply use the PIN.
 */
object Biometrics {

    private fun authenticators(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bm = context.getSystemService(BiometricManager::class.java) ?: return null
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val weak = BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when {
            bm.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS -> strong
            bm.canAuthenticate(weak) == BiometricManager.BIOMETRIC_SUCCESS -> weak
            else -> null
        }
    }

    /** A fingerprint (or other biometric) is set up on this phone and usable. */
    fun available(context: Context): Boolean = runCatching { authenticators(context) != null }.getOrDefault(false)

    /**
     * Shows the system prompt. [onResult] gets true on success, false when cancelled or failed for good
     * (a single unrecognised finger just lets the user try again inside the prompt).
     */
    fun prompt(context: Context, title: String, cancel: String, onResult: (Boolean) -> Unit): CancellationSignal? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val auth = runCatching { authenticators(context) }.getOrNull() ?: return null
        val executor = context.mainExecutor
        val signal = CancellationSignal()
        return runCatching {
            BiometricPrompt.Builder(context)
                .setTitle(title)
                .setAllowedAuthenticators(auth)
                .setNegativeButton(cancel, executor) { _, _ -> onResult(false) }
                .setConfirmationRequired(false)
                .build()
                .authenticate(
                    signal, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
                    },
                )
            signal
        }.getOrNull()
    }
}
