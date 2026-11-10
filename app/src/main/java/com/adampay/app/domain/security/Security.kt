package com.adampay.app.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** How long the app may stay in the background before it asks for the PIN again. */
enum class LockTimeout(val millis: Long) { IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000) }

/** The app lock as stored on this phone (never part of a backup). */
data class SecurityConfig(
    val lockEnabled: Boolean = false,
    /** "pbkdf2$<iterations>$<salt>$<hash>", see [Pin]. */
    val pinHash: String? = null,
    val biometric: Boolean = false,
    val timeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** FLAG_SECURE: blank in the recent-apps list and no screenshots. */
    val secureScreen: Boolean = false,
    val failures: Int = 0,
    /** Wall-clock millis before which no PIN is accepted (after too many wrong ones). */
    val waitUntil: Long = 0,
) {
    val locks: Boolean get() = lockEnabled && pinHash != null
}

/** A 4-digit PIN, kept only as a salted PBKDF2 hash. */
object Pin {
    const val LENGTH = 4
    private const val ITERATIONS = 120_000
    private val random = SecureRandom()

    fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also(random::nextBytes), iterations: Int = ITERATIONS): String {
        val b64 = Base64.getEncoder()
        return "pbkdf2\$$iterations\$${b64.encodeToString(salt)}\$${b64.encodeToString(derive(pin, salt, iterations))}"
    }

    fun matches(pin: String, stored: String?): Boolean {
        val parts = stored?.split('$') ?: return false
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Wrong PINs: five free tries, then a growing wait (30 s, 1 min, 2 min … up to 15 min). */
object PinThrottle {
    const val FREE_TRIES = 5
    private const val FIRST_WAIT = 30_000L
    private const val MAX_WAIT = 15 * 60_000L

    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0
        val steps = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT shl steps).coerceAtMost(MAX_WAIT)
    }

    fun triesLeft(failures: Int): Int = (FREE_TRIES - failures).coerceAtLeast(0)
}

/** When coming back from the background re-locks the app. Times are SystemClock.elapsedRealtime(). */
object LockPolicy {
    fun shouldLock(timeout: LockTimeout, backgroundAt: Long?, now: Long): Boolean =
        backgroundAt != null && now - backgroundAt >= timeout.millis
}

/** The answer to a PIN typed on the lock screen. */
sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val triesLeft: Int, val waitUntil: Long) : PinCheck
    data class Wait(val until: Long) : PinCheck
}
