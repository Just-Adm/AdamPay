package com.adampay.app.domain.account

import java.time.Instant

/** The Google account the backups go to. No token is kept: a fresh one is asked for each time. */
data class GoogleAccount(
    val id: String,
    val name: String,
    val email: String,
    val linkedAt: Instant = Instant.EPOCH,
)

/** What the backup screen shows. Sizes are what is stored in Drive (compressed). */
data class BackupStatus(
    val lastAt: Instant? = null,
    val lastUploadedBytes: Long = 0,
    val usedBytes: Long = 0,
    val backups: Int = 0,
    /** SETUP, SIGN_IN, OFFLINE or FAILED (see BackupEngine.errorCode). */
    val lastError: String? = null,
)

data class AccountState(
    val google: GoogleAccount? = null,
    /** Nightly automatic backup at [BACKUP_HOUR]:[BACKUP_MINUTE]. */
    val autoBackup: Boolean = true,
    val status: BackupStatus = BackupStatus(),
) {
    companion object {
        const val BACKUP_HOUR = 23
        const val BACKUP_MINUTE = 30
    }
}
