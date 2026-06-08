package id.azkura.auth.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.data.local.prefs.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Sign-In service.
 * Port of src/core/google-auth.js.
 *
 * Uses Credential Manager API on Android.
 * TODO: Implement with Google Identity Services.
 */
@Singleton
class GoogleAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) {
    /**
     * Sign in with Google.
     * TODO: Implement with CredentialManager + GetSignInWithGoogleOption.
     */
    suspend fun signIn(): Boolean {
        // TODO
        return false
    }

    /**
     * Sign out and clear stored user data.
     */
    suspend fun signOut() {
        preferencesManager.clearGoogleUser()
    }

    /**
     * Check if user is signed in.
     */
    suspend fun isSignedIn(): Boolean {
        // Check if we have stored Google user data
        return false
    }
}
