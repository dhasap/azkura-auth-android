package id.azkura.auth.data.remote

import android.accounts.Account as AndroidAccount
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.data.local.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Sign-In / OAuth service.
 *
 * Android needs an OAuth access token, not just an ID token, because Google Drive
 * backup/restore calls the Drive REST API directly. This service therefore uses
 * Google Identity Services' AuthorizationClient to request the same scopes as the
 * browser extension (`openid email profile drive.file`). If Google Play services
 * needs user consent, callers receive a PendingIntent and must launch it from UI.
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
     */
    suspend fun authorize(activity: Activity): GoogleAuthorizationOutcome {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(GOOGLE_SCOPES.map(::Scope))

        preferencesManager.googleUserEmail.first()
            ?.takeIf { it.isNotBlank() }
            ?.let { email -> builder.setAccount(AndroidAccount(email, GOOGLE_ACCOUNT_TYPE)) }

        val result = Identity.getAuthorizationClient(activity)
            .authorize(builder.build())
            .await()

        return processAuthorizationResult(result)
    }

    /** Convenience alias for the settings UI sign-in action. */
    suspend fun signIn(activity: Activity): GoogleAuthorizationOutcome = authorize(activity)

    /**
     * Convert the ActivityResult data from a Google consent PendingIntent into an
     * authorized session and persist it.
     */
    suspend fun handleAuthorizationResult(intent: Intent?): GoogleAuthorizedSession {
        if (intent == null) {
            throw IllegalArgumentException("Google sign-in was cancelled")
        }

        val result = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        } catch (error: ApiException) {
            throw IllegalArgumentException(error.message ?: "Google sign-in failed", error)
        }

        return when (val outcome = processAuthorizationResult(result)) {
            is GoogleAuthorizationOutcome.Authorized -> outcome.session
            is GoogleAuthorizationOutcome.NeedsResolution -> {
                throw IllegalStateException("Google sign-in still requires user consent")
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
                ?: throw IllegalStateException("Google authorization requires consent but no resolution was returned")
            return GoogleAuthorizationOutcome.NeedsResolution(pendingIntent)
        }

        val accessToken = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google authorization did not return an access token")

        val accountProfile = result.toProfileOrNull()
        val fetchedProfile = fetchUserProfile(accessToken)
        val profile = mergeProfiles(fetchedProfile, accountProfile)
            ?: throw IllegalStateException("Unable to read Google user profile")

        preferencesManager.setGoogleAuthSession(
            name = profile.name,
            email = profile.email,
            picture = profile.picture,
            accessToken = accessToken,
            tokenTimeMillis = System.currentTimeMillis(),
        )

        return GoogleAuthorizationOutcome.Authorized(
            GoogleAuthorizedSession(
                accessToken = accessToken,
                user = profile,
            ),
        )
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
                if (!response.isSuccessful) return@use null
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
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
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
