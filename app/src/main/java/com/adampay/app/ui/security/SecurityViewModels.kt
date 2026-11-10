package com.adampay.app.ui.security

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adampay.app.core.auth.AuthMessage
import com.adampay.app.core.auth.GoogleAuth
import com.adampay.app.core.auth.GoogleStep
import com.adampay.app.core.backup.BackupEngine
import com.adampay.app.core.backup.BackupWork
import com.adampay.app.core.backup.NoBackupException
import com.adampay.app.data.account.AccountStore
import com.adampay.app.domain.account.AccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** One-off results the backup screen shows as a snackbar or dialog. */
sealed interface BackupEvent {
    data class Auth(val message: AuthMessage, val code: Int?) : BackupEvent
    data class BackedUp(val newBackup: Boolean) : BackupEvent
    data object Restored : BackupEvent
    data object NoBackups : BackupEvent
    data class Failed(val code: String) : BackupEvent
    /** Just signed in on a phone that never backed up, and Drive already has backups: offer them. */
    data class Found(val backups: List<Instant>) : BackupEvent
    data class Pick(val backups: List<Instant>) : BackupEvent
}

/** Settings → Backup: Google account, back up now, restore. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val google: GoogleAuth,
    private val engine: BackupEngine,
    private val store: AccountStore,
) : ViewModel() {

    val account: StateFlow<AccountState?> = store.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val signingIn: StateFlow<Boolean> = google.busy
    val running: StateFlow<BackupWork?> = engine.running

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events

    init {
        viewModelScope.launch {
            google.messages.collect { m ->
                _events.emit(BackupEvent.Auth(m, google.lastCode))
                if (m == AuthMessage.LINKED) offerRestoreIfAny()
            }
        }
    }

    suspend fun startSignIn(): GoogleStep = google.start()
    fun completeSignIn(data: Intent?) = viewModelScope.launch { google.complete(data) }
    fun signOut() = viewModelScope.launch { google.signOut() }
    fun setAuto(on: Boolean) = viewModelScope.launch { store.setAuto(on) }

    fun backupNow() = viewModelScope.launch {
        runCatching { engine.backup() }
            .onSuccess { _events.emit(BackupEvent.BackedUp(it.newBackup)) }
            .onFailure { _events.emit(BackupEvent.Failed(BackupEngine.errorCode(it))) }
    }

    /** Lists the backups in Drive for the restore picker. */
    fun pickBackup() = viewModelScope.launch {
        runCatching { engine.list() }
            .onSuccess { _events.emit(if (it.isEmpty()) BackupEvent.NoBackups else BackupEvent.Pick(it)) }
            .onFailure { _events.emit(BackupEvent.Failed(BackupEngine.errorCode(it))) }
    }

    fun restore(at: Instant) = viewModelScope.launch {
        runCatching { engine.restore(at) }
            .onSuccess { _events.emit(BackupEvent.Restored) }
            .onFailure { _events.emit(if (it is NoBackupException) BackupEvent.NoBackups else BackupEvent.Failed(BackupEngine.errorCode(it))) }
    }

    private suspend fun offerRestoreIfAny() {
        if (store.current().status.lastAt != null) return
        val list = runCatching { engine.list() }.getOrNull().orEmpty()
        if (list.isNotEmpty()) _events.emit(BackupEvent.Found(list))
    }
}
