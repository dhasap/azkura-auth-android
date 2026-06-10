package id.azkura.auth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Azkura Auth color palette — ported from CSS variables in style.css.
 * Dark-first design with cyan accent.
 */

// ── Accent ───────────────────────────────────────────────────────────────────
val Accent = Color(0xFF00E5FF)
val AccentDim = Color(0x2600E5FF)    // ~15% alpha
val AccentDim2 = Color(0x1400E5FF)   // ~8% alpha

// ── Backgrounds ──────────────────────────────────────────────────────────────
val BgBase = Color(0xFF0F172A)
val BgCard = Color(0xFF1E293B)
val BgCardHover = Color(0xFF334155)
val BgElevated = Color(0xFF1E293B)
val BgInput = Color(0xFF0F172A)
val BgModal = Color(0xFF0F172A)

// ── Text ─────────────────────────────────────────────────────────────────────
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF888888)
val TextMuted = Color(0xFF555555)
val TextOnAccent = Color(0xFF000000)

// ── Borders ──────────────────────────────────────────────────────────────────
val BorderSubtle = Color(0x0FFFFFFF)   // ~6% alpha
val BorderMedium = Color(0x1FFFFFFF)   // ~12% alpha
val BorderAccent = Color(0x4D00E5FF)   // ~30% alpha

// ── TOTP countdown states ────────────────────────────────────────────────────
val TotpNormal = Color(0xFF00E5FF)
val TotpWarning = Color(0xFFFF8800)
val TotpDanger = Color(0xFFFF3B3B)

// ── Service colors (Material You seed colors) ────────────────────────────────
val GoogleBlue = Color(0xFF4285F4)
val Success = Color(0xFF00C853)
val Error = Color(0xFFFF3B3B)
val Warning = Color(0xFFFF8800)

// ── Folder default colors ────────────────────────────────────────────────────
val FolderColors = listOf(
    Color(0xFF00E5FF), // Cyan (default)
    Color(0xFF4285F4), // Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF00BCD4), // Teal
    Color(0xFF795548), // Brown
    Color(0xFF607D8B), // Blue Grey
)
