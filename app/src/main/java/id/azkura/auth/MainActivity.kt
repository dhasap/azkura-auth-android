package id.azkura.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.azkura.auth.ui.components.PrivacyOverlay
import id.azkura.auth.ui.navigation.AzkuraNavGraph
import id.azkura.auth.ui.theme.AzkuraAuthTheme
import id.azkura.auth.ui.theme.BgBase

import android.view.WindowManager
import id.azkura.auth.data.local.crypto.VaultManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val pendingOtpauthUri = mutableStateOf<String?>(null)

    // Backs the Recents-thumbnail privacy overlay. See PrivacyOverlay.kt for
    // why this exists and exactly why it is safe (FLAG_SECURE is only ever
    // cleared while this is already showing, and is restored before it is
    // hidden again — see onPause()/onResume() below).
    private val isPrivacyOverlayVisible = mutableStateOf(false)

    @Inject lateinit var vaultManager: VaultManager

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        vaultManager.lockVault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pendingOtpauthUri.value = extractOtpauthUri(intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AzkuraAuthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgBase,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        AzkuraNavGraph(
                            navController = navController,
                            vaultManager = vaultManager,
                            pendingOtpauthUri = pendingOtpauthUri.value,
                            onPendingOtpauthUriConsumed = { pendingOtpauthUri.value = null },
                        )

                        val overlayVisible by isPrivacyOverlayVisible
                        PrivacyOverlay(visible = overlayVisible)
                    }
                }
            }
        }
    }

    /**
     * Called right before the activity becomes invisible (Home/Recents
     * button, switching apps, an incoming call, etc.) — always fires before
     * the system captures the Recents thumbnail.
     *
     * Order matters for security: the overlay is made visible FIRST, then
     * FLAG_SECURE is cleared. Because both happen on the main thread before
     * this function returns, and the OS only takes the Recents snapshot
     * once the activity has actually moved to the background (after
     * onPause()/onStop() return), the real account/TOTP UI is never in an
     * unprotected + capturable state at the same time.
     */
    override fun onPause() {
        super.onPause()
        isPrivacyOverlayVisible.value = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Called when returning to the foreground. Order is the mirror image of
     * onPause(): FLAG_SECURE is restored FIRST, and only then is the privacy
     * overlay hidden — so screenshots/screen-recording protection is back in
     * effect before any real content becomes visible again.
     */
    override fun onResume() {
        super.onResume()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        isPrivacyOverlayVisible.value = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOtpauthUri.value = extractOtpauthUri(intent)
    }

    private fun extractOtpauthUri(intent: Intent?): String? {
        val data = intent?.dataString?.trim()
        return data?.takeIf { it.startsWith("otpauth://") }
    }
}
