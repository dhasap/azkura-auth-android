package id.azkura.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.azkura.auth.ui.navigation.AzkuraNavGraph
import id.azkura.auth.ui.theme.AzkuraAuthTheme
import id.azkura.auth.ui.theme.BgBase

import android.view.WindowManager
import id.azkura.auth.data.local.crypto.VaultManager
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val pendingOtpauthUri = mutableStateOf<String?>(null)

    @Inject lateinit var vaultManager: VaultManager

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        vaultManager.lockVault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE is set once, here, and never toggled at runtime.
        //
        // An earlier version of this code cleared FLAG_SECURE in onPause()
        // to swap in a custom blurred overlay before the Recents thumbnail
        // was captured, then restored it in onResume(). That pattern is a
        // documented race condition: the system can capture the Recents
        // snapshot before onPause() finishes running (or before Compose has
        // actually drawn the overlay to a new frame), so there is no way to
        // guarantee the flag is off only while the overlay is fully on
        // screen. In the best case it silently does nothing (still black);
        // in the worst case it is a real security regression (a frame with
        // FLAG_SECURE off could be captured before the overlay is visible).
        // See: https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean)
        //
        // The officially recommended, race-free fix is
        // setRecentsScreenshotEnabled(false) below, which tells the system
        // to never attempt a Recents screenshot for this Activity at all —
        // Android then falls back to its own placeholder (app icon on the
        // theme's windowBackground) instead of either a real screenshot or
        // a plain black rectangle. Security (screenshot/screen-recording
        // protection while the app is on screen) is unaffected either way.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRecentsScreenshotEnabled(false)
            Log.d(TAG, "setRecentsScreenshotEnabled(false) applied (API ${Build.VERSION.SDK_INT})")
        } else {
            Log.d(TAG, "setRecentsScreenshotEnabled unavailable below API 31 (running API ${Build.VERSION.SDK_INT}); Recents will show the FLAG_SECURE default placeholder")
        }

        pendingOtpauthUri.value = extractOtpauthUri(intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AzkuraAuthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgBase,
                ) {
                    val navController = rememberNavController()
                    AzkuraNavGraph(
                        navController = navController,
                        vaultManager = vaultManager,
                        pendingOtpauthUri = pendingOtpauthUri.value,
                        onPendingOtpauthUriConsumed = { pendingOtpauthUri.value = null },
                    )
                }
            }
        }
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
