package com.adampay.app.core.security

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adampay.app.domain.security.LockPolicy
import com.adampay.app.domain.security.LockTimeout
import com.adampay.app.domain.security.Pin
import com.adampay.app.domain.security.PinCheck
import com.adampay.app.domain.security.PinThrottle
import com.adampay.app.domain.security.SecurityConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** App lock settings. A store of its own: it belongs to this phone and is never backed up or restored. */
private val Context.securityStore: DataStore<Preferences> by preferencesDataStore(name = "security")

@Singleton
class AppLock @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val enabled = booleanPreferencesKey("lock_enabled")
        val pin = stringPreferencesKey("pin_hash")
        val biometric = booleanPreferencesKey("biometric")
        val timeout = stringPreferencesKey("lock_timeout")
        val secure = booleanPreferencesKey("secure_screen")
        val failures = intPreferencesKey("pin_failures")
        val waitUntil = longPreferencesKey("pin_wait_until")
    }

    val config: Flow<SecurityConfig> = context.securityStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            SecurityConfig(
                lockEnabled = p[K.enabled] ?: false,
                pinHash = p[K.pin],
                biometric = p[K.biometric] ?: false,
                timeout = p[K.timeout]?.let { n -> LockTimeout.entries.firstOrNull { it.name == n } } ?: LockTimeout.IMMEDIATELY,
                secureScreen = p[K.secure] ?: false,
                failures = p[K.failures] ?: 0,
                waitUntil = p[K.waitUntil] ?: 0,
            )
        }

    suspend fun current(): SecurityConfig = config.first()

    /* ------------------------------------------------------------ session */

    private val _locked = MutableStateFlow(true)
    /** True until the PIN / fingerprint is given in this process (only matters while the lock is on). */
    val locked: StateFlow<Boolean> = _locked

    private var backgroundAt: Long? = null
    private var expectingReturn = false

    fun unlock() { _locked.value = false; backgroundAt = null }

    /** The app's screen left the foreground. */
    fun onBackground() { if (backgroundAt == null) backgroundAt = SystemClock.elapsedRealtime() }

    /** The app's screen is visible again: lock if it was away long enough. */
    suspend fun onForeground() {
        val away = backgroundAt
        backgroundAt = null
        if (expectingReturn) { expectingReturn = false; return }
        if (LockPolicy.shouldLock(current().timeout, away, SystemClock.elapsedRealtime())) _locked.value = true
    }

    /** About to open Google's own screen (sign-in): coming back from it doesn't ask for the PIN. */
    fun expectReturn() { expectingReturn = true }

    /* ------------------------------------------------------------ PIN */

    /** Checks a PIN typed on the lock screen, counting wrong ones (they survive closing the app). */
    suspend fun check(pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        val c = current()
        if (now < c.waitUntil) return PinCheck.Wait(c.waitUntil)
        val ok = withContext(Dispatchers.Default) { Pin.matches(pin, c.pinHash) }
        if (ok) {
            context.securityStore.edit { it[K.failures] = 0; it.remove(K.waitUntil) }
            unlock()
            return PinCheck.Ok
        }
        val failures = c.failures + 1
        val wait = PinThrottle.waitAfter(failures)
        val until = if (wait > 0) now + wait else 0
        context.securityStore.edit { it[K.failures] = failures; if (until > 0) it[K.waitUntil] = until else it.remove(K.waitUntil) }
        return PinCheck.Wrong(PinThrottle.triesLeft(failures), until)
    }

    /** Fingerprint accepted. */
    suspend fun biometricUnlocked() {
        context.securityStore.edit { it[K.failures] = 0; it.remove(K.waitUntil) }
        unlock()
    }

    /** Sets a new PIN and turns the lock on (this session stays unlocked). */
    suspend fun setPin(pin: String) {
        require(Pin.isValid(pin))
        val hash = withContext(Dispatchers.Default) { Pin.hash(pin) }
        context.securityStore.edit { it[K.pin] = hash; it[K.enabled] = true; it[K.failures] = 0; it.remove(K.waitUntil) }
        unlock()
    }

    /** Turns the lock off and forgets the PIN and fingerprint choice. */
    suspend fun disable() = context.securityStore.edit {
        it[K.enabled] = false; it.remove(K.pin); it[K.biometric] = false; it[K.failures] = 0; it.remove(K.waitUntil)
    }

    suspend fun setBiometric(on: Boolean) = context.securityStore.edit { it[K.biometric] = on }
    suspend fun setTimeout(t: LockTimeout) = context.securityStore.edit { it[K.timeout] = t.name }
    suspend fun setSecureScreen(on: Boolean) = context.securityStore.edit { it[K.secure] = on }
}
