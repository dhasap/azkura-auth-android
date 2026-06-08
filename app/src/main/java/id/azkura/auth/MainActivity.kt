package id.azkura.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.azkura.auth.ui.navigation.AzkuraNavGraph
import id.azkura.auth.ui.theme.AzkuraAuthTheme
import id.azkura.auth.ui.theme.BgBase

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AzkuraAuthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgBase,
                ) {
                    val navController = rememberNavController()
                    AzkuraNavGraph(navController = navController)
                }
            }
        }
    }
}
