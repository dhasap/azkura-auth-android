package id.azkura.auth.data.remote

import android.accounts.Account as AndroidAccount
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.data.local.prefs.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "GoogleAuthService"
private const val AUTHORIZE_TIMEOUT_MS = 25_000L

/**
 * Classified Google sign-in / authorization failures.
 *
 * Google Play services and network failures surface as opaque
 * [ApiException]/[IOException] instances with English, developer-oriented
 * messages. Classifying them here means the Settings UI can show one clear,
 * actionable message per failure category (cancel, no network, bad/expired
 * token, Play services problem) instead of leaking raw SDK text to the user.
 */
sealed class GoogleSignInException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Cancelled(cause: Throwable? = null) :
        GoogleSignInException("Login Google dibatalkan", cause)

    class NetworkError(cause: Throwable? = null) :
        GoogleSignInException("Tidak ada koneksi internet. Periksa jaringan Anda dan coba lagi.", cause)

    class SessionExpired(cause: Throwable? = null) :
        GoogleSignInException("Sesi Google telah berakhir. Silakan login kembali.", cause)

    class TokenError(cause: Throwable? = null) :
        GoogleSignInException("Gagal mendapatkan token akses Google. Coba login ulang.", cause)

    class PlayServicesError(cause: Throwable? = null) :
        GoogleSignInException("Google Play Services bermasalah di perangkat ini. Perbarui Google Play Services dan coba lagi.", cause)

    class Timeout(cause: Throwable? = null) :
        GoogleSignInException("Login Google memakan waktu terlalu lama. Coba lagi.", cause)

    class Unknown(message: String, cause: Throwable? = null) :
        GoogleSignInException(message, cause)
}

/**
 * Google Sign-In / OAuth service.
 *
 * Android needs an OAuth access token, not just an ID token, because Google Drive
 * backup/restore calls the Drive REST API directly. This service therefore uses
 * Google Identity Services' AuthorizationClient to request the same scopes as the
 * browser extension (`openid email profile drive.file`). If Google Play services
 * needs user consent, callers receive a PendingIntent and must launch it from UI.
 *
 * All failure paths are translated into [GoogleSignInException] subtypes so the
 * UI layer can render a specific, actionable message instead of a raw SDK error.
 */
@Singleton
class GoogleAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /**
     * Start or refresh Google authorization. The returned outcome is either an
     * immediate authorized session or a consent PendingIntent that must be
     * resolved by the caller.
     *
     * Wrapped with a 25s timeout so a hung Play services call (poor network,
     * ANR'd system process, etc.) can never leave the caller's "signing in..."
     * UI state spinning forever — it always resolves to either success or a
     * clear [GoogleSignInException].
     */
    suspend fun authorize(activity: Activity): GoogleAuthorizationOutcome {
        return try {
            withTimeout(AUTHORIZE_TIMEOUT_MS) {
                val builder = AuthorizationRequest.builder()
                    .setRequestedScopes(GOOGLE_SCOPES.map(::Scope))

                preferencesManager.googleUserEmail.first()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { email -> builder.setAccount(AndroidAccount(email, GOOGLE_ACCOUNT_TYPE)) }

                Log.d(TAG, "authorize: requesting scopes $GOOGLE_SCOPES")
                val result = Identity.getAuthorizationClient(activity)
                    .authorize(builder.build())
                    .await()

                processAuthorizationResult(result)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google authorize() timed out", e)
            throw GoogleSignInException.Timeout(e)
        } catch (e: ApiException) {
            throw e.toSignInException()
        } catch (e: IOException) {
            Log.e(TAG, "Google authorize() network error", e)
            throw GoogleSignInException.NetworkError(e)
        } catch (e: GoogleSignInException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google authorize() failed", e)
            throw GoogleSignInException.Unknown(e.message ?: "Login Google gagal", e)
        }
    }

    /** Convenience alias for the settings UI sign-in action. */
    suspend fun signIn(activity: Activity): GoogleAuthorizationOutcome = authorize(activity)

    /**
     * Convert the ActivityResult data from a Google consent PendingIntent into
     * either a fully authorized session, or ANOTHER resolution the caller must
     * launch.
     *
     * Root cause of the previous "must press Connect twice" bug: Google's
     * Identity Services AuthorizationClient can require more than one round
     * of user consent for a single sign-in — e.g. the account picker is one
     * resolution, and granting the `drive.file` scope to a not-yet-trusted
     * app can be a SECOND, separate resolution. The old implementation only
     * handled a single round-trip and *threw* if a second resolution was
     * needed, which surfaced as a generic error and reset the UI — so the
     * user had to tap "Connect" again from scratch (which then usually
     * succeeded immediately, because the account was already cached).
     * Returning [GoogleAuthorizationOutcome] here — instead of throwing —
     * lets the caller keep launching each subsequent PendingIntent in the
     * same "Connect" tap until the flow is genuinely done.
     */
    suspend fun handleAuthorizationResult(intent: Intent?): GoogleAuthorizationOutcome {
        if (intent == null) {
            Log.w(TAG, "handleAuthorizationResult called with null intent (user cancelled or launch failed)")
            throw GoogleSignInException.Cancelled()
        }

        val result = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        } catch (error: ApiException) {
            Log.e(TAG, "getAuthorizationResultFromIntent failed", error)
            throw error.toSignInException()
        }

        return processAuthorizationResult(result).also { outcome ->
            when (outcome) {
                is GoogleAuthorizationOutcome.Authorized ->
                    Log.d(TAG, "handleAuthorizationResult: fully authorized for ${outcome.session.user.email}")
                is GoogleAuthorizationOutcome.NeedsResolution ->
                    Log.d(TAG, "handleAuthorizationResult: another consent round is required, chaining resolution")
            }
        }
    }

    /** Sign out locally and clear stored Google session data. */
    suspend fun signOut() {
        preferencesManager.clearGoogleUser()
    }

    /** Check whether a non-expired Google session is stored locally. */
    suspend fun isSignedIn(): Boolean = getStoredAccessTokenIfFresh() != null &&
        !preferencesManager.googleUserEmail.first().isNullOrBlank()

    /** Return the stored access token only when it is still within Google's TTL. */
    suspend fun getStoredAccessTokenIfFresh(): String? {
        val token = preferencesManager.googleAccessToken.first()?.takeIf { it.isNotBlank() } ?: return null
        val tokenTime = preferencesManager.googleAuthTokenTime.first() ?: return null
        val elapsed = System.currentTimeMillis() - tokenTime
        return if (elapsed in 0 until TOKEN_VALIDITY_MS) {
            token
        } else {
            Log.d(TAG, "Stored Google access token expired locally, clearing")
            preferencesManager.clearGoogleAccessToken()
            null
        }
    }

    /** Clear a bad/expired access token while keeping the displayed profile. */
    suspend fun clearInvalidToken() {
        preferencesManager.clearGoogleAccessToken()
    }

    private suspend fun processAuthorizationResult(result: AuthorizationResult): GoogleAuthorizationOutcome {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: throw GoogleSignInException.Unknown("Google authorization requires consent but no resolution was returned")
            Log.d(TAG, "processAuthorizationResult: result.hasResolution() == true, returning NeedsResolution")
            return GoogleAuthorizationOutcome.NeedsResolution(pendingIntent)
        }

        val accessToken = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw GoogleSignInException.TokenError()

        val accountProfile = result.toProfileOrNull()
        val fetchedProfile = fetchUserProfile(accessToken)
        val profile = mergeProfiles(fetchedProfile, accountProfile)
            ?: throw GoogleSignInException.Unknown("Unable to read Google user profile")

        preferencesManager.setGoogleAuthSession(
            name = profile.name,
            email = profile.email,
            picture = profile.picture,
            accessToken = accessToken,
            tokenTimeMillis = System.currentTimeMillis(),
        )
        Log.d(TAG, "Google authorization successful for ${profile.email}")

        return GoogleAuthorizationOutcome.Authorized(
            GoogleAuthorizedSession(
                accessToken = accessToken,
                user = profile,
            ),
        )
    }

    /** Map a Play services [ApiException] status code to a classified, user-facing exception. */
    private fun ApiException.toSignInException(): GoogleSignInException = when (statusCode) {
        CommonStatusCodes.CANCELED -> GoogleSignInException.Cancelled(this)
        CommonStatusCodes.NETWORK_ERROR -> GoogleSignInException.NetworkError(this)
        CommonStatusCodes.TIMEOUT -> GoogleSignInException.Timeout(this)
        CommonStatusCodes.SIGN_IN_REQUIRED,
        CommonStatusCodes.INVALID_ACCOUNT,
        -> GoogleSignInException.SessionExpired(this)
        CommonStatusCodes.INTERNAL_ERROR,
        CommonStatusCodes.DEVELOPER_ERROR,
        CommonStatusCodes.API_NOT_CONNECTED,
        -> GoogleSignInException.PlayServicesError(this)
        else -> GoogleSignInException.Unknown(message ?: "Login Google gagal (code $statusCode)", this)
    }

    private fun AuthorizationResult.toProfileOrNull(): GoogleUserProfile? {
        return runCatching {
            val account = toGoogleSignInAccount() ?: return@runCatching null
            val email = account.email?.takeIf { it.isNotBlank() } ?: return@runCatching null
            GoogleUserProfile(
                name = account.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@'),
                email = email,
                picture = account.photoUrl?.toString(),
            )
        }.getOrNull()
    }

    private fun mergeProfiles(primary: GoogleUserProfile?, fallback: GoogleUserProfile?): GoogleUserProfile? {
        val email = primary?.email?.takeIf { it.isNotBlank() }
            ?: fallback?.email?.takeIf { it.isNotBlank() }
            ?: return null
        val name = primary?.name?.takeIf { it.isNotBlank() }
            ?: fallback?.name?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')
        val picture = primary?.picture?.takeIf { it.isNotBlank() }
            ?: fallback?.picture?.takeIf { it.isNotBlank() }
        return GoogleUserProfile(name = name, email = email, picture = picture)
    }

    private suspend fun fetchUserProfile(accessToken: String): GoogleUserProfile? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GOOGLE_USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "userinfo request failed: HTTP ${response.code}")
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                val data = json.parseToJsonElement(body).jsonObject
                val email = data["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@use null
                GoogleUserProfile(
                    name = data["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore('@'),
                    email = email,
                    picture = data["picture"]?.jsonPrimitive?.contentOrNull,
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "userinfo request network error", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "userinfo request failed", e)
            null
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }

    companion object {
        private const val GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val TOKEN_VALIDITY_MS = 55L * 60L * 1000L

        private val GOOGLE_SCOPES = listOf(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/drive.file",
        )
    }
}

data class GoogleUserProfile(
    val name: String,
    val email: String,
    val picture: String?,
)

data class GoogleAuthorizedSession(
    val accessToken: String,
    val user: GoogleUserProfile,
)

sealed class GoogleAuthorizationOutcome {
    data class Authorized(val session: GoogleAuthorizedSession) : GoogleAuthorizationOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleAuthorizationOutcome()
}
