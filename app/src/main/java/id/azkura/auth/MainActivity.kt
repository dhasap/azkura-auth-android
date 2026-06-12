package id.azkura.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.azkura.auth.ui.navigation.AzkuraNavGraph
import id.azkura.auth.ui.theme.AzkuraAuthTheme
import id.azkura.auth.ui.theme.BgBase
import androidx.fragment.app.FragmentActivity

import android.view.WindowManager
import id.azkura.auth.data.local.crypto.VaultManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val pendingOtpauthUri = mutableStateOf<String?>(null)


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
