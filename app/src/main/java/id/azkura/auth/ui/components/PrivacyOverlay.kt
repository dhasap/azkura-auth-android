package id.azkura.auth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary

/**
 * A branded, blurred placeholder shown over the entire app UI while it is
 * backgrounded (Recent Apps / App Switcher).
 *
 * Why this exists: the window carries [android.view.WindowManager.LayoutParams.FLAG_SECURE]
 * at all times to block screenshots and screen recording of TOTP secrets.
 * FLAG_SECURE also makes the OS compositor refuse to capture *any* image of
 * the window for the Recents thumbnail, which is why — without this overlay
 * — the app previously showed a plain black/blank card in the app switcher.
 *
 * [id.azkura.auth.MainActivity] temporarily clears FLAG_SECURE in `onPause()`
 * — but only *after* this overlay is already the only thing on screen — so
 * the Recents snapshot the system takes captures this safe, branded overlay
 * instead of real account data, and FLAG_SECURE is restored in `onResume()`
 * *before* the overlay is removed. No sensitive content is ever exposed to
 * a screenshot/recording/Recents capture at any point in that sequence.
 */
@Composable
fun PrivacyOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgBase),
            contentAlignment = Alignment.Center,
        ) {
            // Soft decorative glow — purely cosmetic, contains no app data,
            // so blurring it heavily is always safe to render.
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(y = (-40).dp)
                    .blur(90.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.35f), Accent.copy(alpha = 0f)),
                        ),
                        shape = CircleShape,
                    ),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Azkura Auth",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Konten disembunyikan demi keamanan",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
