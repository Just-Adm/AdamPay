package com.adampay.app.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adampay.app.domain.account.AccountState
import com.adampay.app.domain.account.BackupStatus
import com.adampay.app.domain.account.GoogleAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The linked Google account and backup status. Kept apart from the settings on purpose: settings are
 * inside every backup, while "who is signed in on this phone" and "when did it last back up" are not.
 */
private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

@Singleton
class AccountStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val id = stringPreferencesKey("google_id")
        val name = stringPreferencesKey("google_name")
        val email = stringPreferencesKey("google_email")
        val linkedAt = longPreferencesKey("google_linked_at")
        val auto = booleanPreferencesKey("backup_auto")
        val lastAt = longPreferencesKey("backup_last_at")
        val lastBytes = longPreferencesKey("backup_last_bytes")
        val used = longPreferencesKey("backup_used_bytes")
        val count = intPreferencesKey("backup_count")
        val error = stringPreferencesKey("backup_error")
    }

    val state: Flow<AccountState> = context.accountStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AccountState(
                google = p[K.id]?.let { GoogleAccount(it, p[K.name].orEmpty(), p[K.email].orEmpty(), Instant.ofEpochMilli(p[K.linkedAt] ?: 0)) },
                autoBackup = p[K.auto] ?: true,
                status = BackupStatus(
                    lastAt = p[K.lastAt]?.let(Instant::ofEpochMilli),
                    lastUploadedBytes = p[K.lastBytes] ?: 0,
                    usedBytes = p[K.used] ?: 0,
                    backups = p[K.count] ?: 0,
                    lastError = p[K.error],
                ),
            )
        }

    suspend fun current(): AccountState = state.first()

    suspend fun link(a: GoogleAccount) = context.accountStore.edit {
        it[K.id] = a.id; it[K.name] = a.name; it[K.email] = a.email; it[K.linkedAt] = a.linkedAt.toEpochMilli()
        it.remove(K.error)
    }

    /** Signing out forgets the account and this phone's backup record (the backups stay in Drive). */
    suspend fun unlink() = context.accountStore.edit { p ->
        listOf(K.id, K.name, K.email, K.error).forEach { p.remove(it) }
        listOf(K.linkedAt, K.lastAt, K.lastBytes, K.used).forEach { p.remove(it) }
        p.remove(K.count)
    }

    suspend fun setAuto(on: Boolean) = context.accountStore.edit { it[K.auto] = on }

    suspend fun setStatus(s: BackupStatus) = context.accountStore.edit {
        if (s.lastAt != null) it[K.lastAt] = s.lastAt.toEpochMilli() else it.remove(K.lastAt)
        it[K.lastBytes] = s.lastUploadedBytes; it[K.used] = s.usedBytes; it[K.count] = s.backups
        if (s.lastError != null) it[K.error] = s.lastError else it.remove(K.error)
    }
}
