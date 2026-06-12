package id.azkura.auth.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardHelper {

    private const val CLEAR_DELAY_MS = 30_000L

    /**
     * Copy text to clipboard and show a toast.
     * On Android 13+ the system shows its own clipboard toast, so we skip ours.
     * The clipboard is automatically cleared after 30 seconds.
     */
    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        // Mark as sensitive so it doesn't appear in clipboard previews
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

        clipboard.setPrimaryClip(clip)

        // Android 13+ shows its own visual confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Auto-clear clipboard after 30 seconds to limit TOTP code exposure
        CoroutineScope(Dispatchers.Main).launch {
            delay(CLEAR_DELAY_MS)
            try {
                val current = clipboard.primaryClipDescription
                if (current?.label == label) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (_: Exception) {
                // Ignore — app may have been killed
            }
        }
    }
}
