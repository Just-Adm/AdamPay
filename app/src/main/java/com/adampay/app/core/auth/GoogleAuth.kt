package com.adampay.app.core.auth

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.adampay.app.data.account.AccountStore
import com.adampay.app.domain.account.AccountState
import com.adampay.app.domain.account.GoogleAccount
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Outcome lines the backup screen shows after a sign-in attempt. */
enum class AuthMessage { LINKED, SETUP, CANCELLED, OFFLINE }

/** Google's answer to "may I?": either done, or a consent screen to show first. */
sealed interface GoogleStep {
    data class NeedsConsent(val intent: PendingIntent) : GoogleStep
    data object Done : GoogleStep
    data object Failed : GoogleStep
}

class NeedsSignInException : Exception("Google sign-in needed")

/**
 * Google sign-in with the Identity AuthorizationClient (same approach as Hyper Athlete): one consent
 * for name / e-mail and the hidden Drive app folder (drive.appdata). The app never sees the password,
 * and the access token is asked for fresh (silently) whenever it's needed, so no token is stored.
 */
@Singleton
class GoogleAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AccountStore,
    private val http: OkHttpClient,
) {
    val state: Flow<AccountState> = store.state

    private val _messages = MutableSharedFlow<AuthMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<AuthMessage> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Google's status code behind the last failure (shown small, so set-up problems can be told apart). */
    @Volatile var lastCode: Int? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    private fun request(email: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES.map(::Scope))
        .apply { if (email != null) setAccount(Account(email, "com.google")) }
        .build()

    /** Step 1: shows the account chooser / consent through [GoogleStep.NeedsConsent]. */
    suspend fun start(): GoogleStep {
        _busy.value = true
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request(null)).await()
            if (result.hasResolution()) GoogleStep.NeedsConsent(result.pendingIntent!!)
            else { finish(result); GoogleStep.Done }
        } catch (e: Exception) {
            _busy.value = false
            _messages.tryEmit(e.toMessage()); GoogleStep.Failed
        }
    }

    /** Step 2: the consent screen came back (possibly cancelled; the reason is inside the intent). */
    suspend fun complete(data: Intent?) {
        try {
            if (data == null) { _messages.tryEmit(AuthMessage.CANCELLED); return }
            finish(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
        } catch (e: Exception) {
            _messages.tryEmit(e.toMessage())
        } finally {
            _busy.value = false
        }
    }

    private suspend fun finish(result: AuthorizationResult) {
        val token = result.accessToken ?: throw IllegalStateException("no token")
        val info = withContext(Dispatchers.IO) { userInfo(token) }
        val email = info.str("email") ?: result.toGoogleSignInAccount()?.email ?: ""
        store.link(
            GoogleAccount(
                id = info.str("sub") ?: email, name = info.str("name") ?: email.substringBefore('@'),
                email = email, linkedAt = Instant.now(),
            ),
        )
        _busy.value = false
        _messages.tryEmit(AuthMessage.LINKED)
    }

    /** A fresh Drive token without any UI. Throws [NeedsSignInException] when Google wants consent again. */
    suspend fun token(): String {
        val email = store.current().google?.email?.takeIf { it.contains('@') } ?: throw NeedsSignInException()
        val result = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        if (result.hasResolution()) throw NeedsSignInException()
        return result.accessToken ?: throw NeedsSignInException()
    }

    /** Forgets the account on this phone and asks Google to drop the permission (best effort, works offline). */
    suspend fun signOut() {
        runCatching {
            val email = store.current().google?.email
            if (email != null) {
                Identity.getAuthorizationClient(context).revokeAccess(
                    RevokeAccessRequest.builder().setAccount(Account(email, "com.google")).setScopes(SCOPES.map(::Scope)).build(),
                ).await()
            }
        }
        store.unlink()
    }

    private fun userInfo(token: String): JsonObject =
        http.newCall(Request.Builder().url(USERINFO).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.parseToJsonElement(r.body.string()).jsonObject
        }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun Exception.toMessage(): AuthMessage = when {
        this is IOException -> AuthMessage.OFFLINE
        // 16 = CANCELED, 12501 = SIGN_IN_CANCELLED: the user backed out.
        this is ApiException && (statusCode == CommonStatusCodes.CANCELED || statusCode == 12501) -> AuthMessage.CANCELLED.also { lastCode = statusCode }
        // DEVELOPER_ERROR (10) and friends: the Google Cloud OAuth client isn't set up for this app/key yet.
        this is ApiException -> AuthMessage.SETUP.also { lastCode = statusCode }
        else -> AuthMessage.SETUP
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        val SCOPES = listOf("openid", "email", "profile", DRIVE_APPDATA)
        private const val USERINFO = "https://openidconnect.googleapis.com/v1/userinfo"
    }
}

/** Play services Task → coroutine, without an extra library. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
