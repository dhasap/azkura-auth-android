package id.azkura.auth.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.azkura.auth.R
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.util.ServiceIconMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * One universal service logo renderer for Home cards and Statistics.
 *
 * Resolution strategy / hierarchy of truth:
 * 1. Trust the issuer/service name first. Known issuers use the bundled brand logo immediately.
 * 2. If no issuer exists, only inspect the label/account text after stripping every email domain.
 * 3. Unknown trusted issuers may use dynamic logo/favicons from a detected domain.
 * 4. If nothing matches, render an elegant deterministic initials fallback.
 */
@Composable
fun ServiceLogoImage(
    serviceName: String,
    modifier: Modifier = Modifier,
    accountHint: String? = null,
    size: Dp = 46.dp,
) {
    val lookup = remember(serviceName, accountHint) { resolveLogoLookup(serviceName, accountHint) }
    val bundledLogo = remember(lookup.matchText) { bundledLogoFor(lookup.matchText) }
    val fallback = remember(lookup.displayName) { ServiceIconMap.getServiceMeta(lookup.displayName) }
    val backgroundColor = remember(lookup.displayName, bundledLogo) {
        when {
            bundledLogo?.forceWhiteBackground == true -> Color.White
            else -> parseColorOrAccent(fallback.bg)
        }
    }
    val candidateDomains = remember(lookup, bundledLogo) {
        if (bundledLogo != null) emptyList() else lookup.candidateDomains
    }
    var remoteLogo by remember(candidateDomains) { mutableStateOf<Bitmap?>(LogoMemoryCache.get(candidateDomains)) }

    LaunchedEffect(candidateDomains) {
        if (remoteLogo == null && candidateDomains.isNotEmpty()) {
            remoteLogo = LogoMemoryCache.fetch(candidateDomains)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bundledLogo != null -> {
                Image(
                    painter = painterResource(bundledLogo.drawableRes),
                    contentDescription = "${lookup.displayName} logo",
                    modifier = Modifier.size(size * bundledLogo.scale),
                    contentScale = ContentScale.Fit,
                )
            }
            remoteLogo != null -> {
                Image(
                    bitmap = remoteLogo!!.asImageBitmap(),
                    contentDescription = "${lookup.displayName} logo",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                Text(
                    text = fallback.letter,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
        }
    }
}

private data class BundledLogo(
    val keywords: List<String>,
    @DrawableRes val drawableRes: Int,
    val domain: String,
    val forceWhiteBackground: Boolean = false,
    val scale: Float = 0.66f,
)

private data class LogoLookup(
    val displayName: String,
    val matchText: String,
    val candidateDomains: List<String>,
)

private data class OtpLogoIdentity(
    val queryIssuer: String,
    val labelIssuer: String,
    val account: String,
)

private val bundledLogos = listOf(
    BundledLogo(listOf("google", "gmail", "gcp", "google cloud"), R.drawable.ic_brand_google, "google.com", forceWhiteBackground = true, scale = 0.62f),
    BundledLogo(listOf("github"), R.drawable.ic_brand_github, "github.com"),
    BundledLogo(listOf("microsoft", "outlook", "azure", "teams", "office", "xbox"), R.drawable.ic_brand_microsoft, "microsoft.com"),
    BundledLogo(listOf("amazon", "aws"), R.drawable.ic_brand_amazon, "amazon.com"),
    BundledLogo(listOf("facebook", "meta"), R.drawable.ic_brand_facebook, "facebook.com"),
    BundledLogo(listOf("instagram"), R.drawable.ic_brand_instagram, "instagram.com"),
    BundledLogo(listOf("discord"), R.drawable.ic_brand_discord, "discord.com"),
    BundledLogo(listOf("netflix"), R.drawable.ic_brand_netflix, "netflix.com"),
    BundledLogo(listOf("linkedin"), R.drawable.ic_brand_linkedin, "linkedin.com"),
    BundledLogo(listOf("slack"), R.drawable.ic_brand_slack, "slack.com"),
    BundledLogo(listOf("dropbox"), R.drawable.ic_brand_dropbox, "dropbox.com"),
    BundledLogo(listOf("gitlab"), R.drawable.ic_brand_gitlab, "gitlab.com"),
    BundledLogo(listOf("bitbucket"), R.drawable.ic_brand_bitbucket, "bitbucket.org"),
    BundledLogo(listOf("apple", "icloud"), R.drawable.ic_brand_apple, "apple.com"),
    BundledLogo(listOf("reddit"), R.drawable.ic_brand_reddit, "reddit.com"),
    BundledLogo(listOf("twitch"), R.drawable.ic_brand_twitch, "twitch.tv"),
    BundledLogo(listOf("cloudflare"), R.drawable.ic_brand_cloudflare, "cloudflare.com"),
    BundledLogo(listOf("digitalocean"), R.drawable.ic_brand_digitalocean, "digitalocean.com"),
    BundledLogo(listOf("vercel"), R.drawable.ic_brand_vercel, "vercel.com"),
    BundledLogo(listOf("netlify"), R.drawable.ic_brand_netlify, "netlify.com"),
    BundledLogo(listOf("firebase"), R.drawable.ic_brand_firebase, "firebase.google.com"),
    BundledLogo(listOf("docker"), R.drawable.ic_brand_docker, "docker.com"),
    BundledLogo(listOf("figma"), R.drawable.ic_brand_figma, "figma.com"),
    BundledLogo(listOf("trello"), R.drawable.ic_brand_trello, "trello.com"),
    BundledLogo(listOf("stripe"), R.drawable.ic_brand_stripe, "stripe.com"),
    BundledLogo(listOf("coinbase"), R.drawable.ic_brand_coinbase, "coinbase.com"),
    BundledLogo(listOf("binance"), R.drawable.ic_brand_binance, "binance.com"),
    BundledLogo(listOf("paypal"), R.drawable.ic_brand_paypal, "paypal.com"),
    BundledLogo(listOf("shopify"), R.drawable.ic_brand_shopify, "shopify.com"),
    BundledLogo(listOf("wordpress"), R.drawable.ic_brand_wordpress, "wordpress.com"),
    BundledLogo(listOf("steam"), R.drawable.ic_brand_steam, "steampowered.com"),
    BundledLogo(listOf("spotify"), R.drawable.ic_brand_spotify, "spotify.com"),
    BundledLogo(listOf("snapchat"), R.drawable.ic_brand_snapchat, "snapchat.com"),
    BundledLogo(listOf("tiktok"), R.drawable.ic_brand_tiktok, "tiktok.com"),
    BundledLogo(listOf("telegram"), R.drawable.ic_brand_telegram, "telegram.org"),
    BundledLogo(listOf("whatsapp"), R.drawable.ic_brand_whatsapp, "whatsapp.com"),
    BundledLogo(listOf("signal"), R.drawable.ic_brand_signal, "signal.org"),
    BundledLogo(listOf("zoom"), R.drawable.ic_brand_zoom, "zoom.us"),
    BundledLogo(listOf("namecheap"), R.drawable.ic_brand_namecheap, "namecheap.com"),
    BundledLogo(listOf("zendesk"), R.drawable.ic_brand_zendesk, "zendesk.com"),
    BundledLogo(listOf("jira"), R.drawable.ic_brand_jira, "atlassian.com"),
    BundledLogo(listOf("confluence", "atlassian"), R.drawable.ic_brand_confluence, "atlassian.com"),
    BundledLogo(listOf("asana"), R.drawable.ic_brand_asana, "asana.com"),
    BundledLogo(listOf("hubspot"), R.drawable.ic_brand_hubspot, "hubspot.com"),
    BundledLogo(listOf("mailchimp"), R.drawable.ic_brand_mailchimp, "mailchimp.com"),
    BundledLogo(listOf("auth0"), R.drawable.ic_brand_auth0, "auth0.com"),
    BundledLogo(listOf("okta"), R.drawable.ic_brand_okta, "okta.com"),
    BundledLogo(listOf("bitwarden"), R.drawable.ic_brand_bitwarden, "bitwarden.com"),
    BundledLogo(listOf("protonmail", "proton"), R.drawable.ic_brand_protonmail, "proton.me"),
    BundledLogo(listOf("mega"), R.drawable.ic_brand_mega, "mega.nz"),
    BundledLogo(listOf("kucoin"), R.drawable.ic_brand_kucoin, "kucoin.com"),
    BundledLogo(listOf("okx"), R.drawable.ic_brand_okx, "okx.com"),
    BundledLogo(listOf("metamask"), R.drawable.ic_brand_metamask, "metamask.io"),
    BundledLogo(listOf("trust wallet", "trustwallet"), R.drawable.ic_brand_trust_wallet, "trustwallet.com"),
    BundledLogo(listOf("ethereum", "eth"), R.drawable.ic_brand_ethereum, "ethereum.org"),
    BundledLogo(listOf("solana", "sol"), R.drawable.ic_brand_solana, "solana.com"),
    BundledLogo(listOf("walletconnect"), R.drawable.ic_brand_walletconnect, "walletconnect.com"),
    BundledLogo(listOf("brave"), R.drawable.ic_brand_brave, "brave.com"),
)

private fun bundledLogoFor(serviceName: String): BundledLogo? {
    val normalized = normalizeForMatching(serviceName)
    if (normalized == "x" || "twitter" in normalized) {
        return BundledLogo(listOf("x", "twitter"), R.drawable.ic_brand_x, "x.com")
    }
    return bundledLogos.firstOrNull { logo ->
        logo.keywords.any { keyword -> normalized.contains(keyword) }
    }
}

private fun resolveLogoLookup(serviceName: String, accountHint: String?): LogoLookup {
    val serviceText = serviceName.trim()
    val accountText = accountHint.orEmpty().trim()
    val otpIdentity = parseOtpLogoIdentity(serviceText) ?: parseOtpLogoIdentity(accountText)

    otpIdentity?.queryIssuer?.takeIf(::isMeaningfulIdentity)?.let { issuer ->
        // Priority 1: the explicit otpauth ?issuer= value is the source of truth.
        return logoLookupForTrustedIdentity(issuer)
    }

    serviceText
        .takeIf { !isOtpAuthUri(it) }
        ?.let(::extractLabelIssuer)
        ?.takeIf(::isMeaningfulIdentity)
        ?.let { labelIssuer ->
            // Priority 2: if a raw label is passed (Issuer:Account), trust the prefix before ':'.
            return logoLookupForTrustedIdentity(labelIssuer)
        }

    serviceText
        .takeIf { !isOtpAuthUri(it) }
        ?.takeIf(::isMeaningfulIdentity)
        ?.let { issuer ->
            // Already-parsed accounts pass issuer/serviceName here; ignore the account email entirely.
            return logoLookupForTrustedIdentity(issuer)
        }

    otpIdentity?.labelIssuer?.takeIf(::isMeaningfulIdentity)?.let { labelIssuer ->
        // Priority 2 fallback for raw otpauth URIs without ?issuer=.
        return logoLookupForTrustedIdentity(labelIssuer)
    }

    extractLabelIssuer(accountText)?.takeIf(::isMeaningfulIdentity)?.let { labelIssuer ->
        // Priority 2 fallback for account text shaped as Issuer:Account.
        return logoLookupForTrustedIdentity(labelIssuer)
    }

    // Priority 3: scan only sanitized text. Email domains are removed before any keyword/domain detection,
    // so kagenocid666@gmail.com can never resolve to a Google/Gmail logo by accident.
    val rawFallbackText = listOfNotNull(otpIdentity?.account, accountText, serviceText)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val sanitizedAccount = removeEmailDomains(rawFallbackText).trim()
    val displayName = sanitizedAccount.takeIf { it.isNotBlank() } ?: "Unknown"
    val candidateDomains = if (rawFallbackText.contains('@')) {
        emptyList()
    } else {
        trustedCandidateDomainsFor(sanitizedAccount, allowInferredDomain = false)
    }

    return LogoLookup(
        displayName = displayName,
        matchText = sanitizedAccount,
        candidateDomains = candidateDomains,
    )
}

private fun logoLookupForTrustedIdentity(identity: String): LogoLookup = LogoLookup(
    displayName = identity,
    matchText = identity,
    candidateDomains = trustedCandidateDomainsFor(identity, allowInferredDomain = true),
)

private fun parseOtpLogoIdentity(input: String): OtpLogoIdentity? {
    val trimmed = input.trim()
    if (!isOtpAuthUri(trimmed)) return null

    return runCatching {
        val parsed = Uri.parse(trimmed)
        val labelRaw = Uri.decode(parsed.path?.removePrefix("/").orEmpty())
        val colonIndex = labelRaw.indexOf(':')
        val labelIssuer = if (colonIndex > 0) labelRaw.substring(0, colonIndex).trim() else ""
        val account = if (colonIndex >= 0) labelRaw.substring(colonIndex + 1).trim() else labelRaw.trim()

        OtpLogoIdentity(
            queryIssuer = parsed.getQueryParameter("issuer")?.trim().orEmpty(),
            labelIssuer = labelIssuer,
            account = account,
        )
    }.getOrNull()
}

private fun isOtpAuthUri(input: String): Boolean = input.startsWith("otpauth://", ignoreCase = true)

private fun trustedCandidateDomainsFor(text: String, allowInferredDomain: Boolean): List<String> {
    val candidates = mutableListOf<String>()
    extractDomain(text)?.let(candidates::add)
    if (allowInferredDomain) inferLikelyDomain(text)?.let(candidates::add)

    return candidates
        .map { it.lowercase(Locale.US).removePrefix("www.") }
        .filter { it.contains('.') && it.length <= 253 }
        .distinct()
}

private fun extractLabelIssuer(input: String): String? {
    val decoded = runCatching { Uri.decode(input) }.getOrElse { input }
    if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(decoded)) return null

    val colonIndex = decoded.indexOf(':')
    if (colonIndex <= 0) return null

    return decoded.substring(0, colonIndex).trim().takeIf { candidate ->
        candidate.isNotBlank() && !candidate.contains('@')
    }
}

private fun extractDomain(input: String): String? {
    val trimmed = removeEmailDomains(input).trim().lowercase(Locale.US)
    if (trimmed.isBlank()) return null

    val regex = Regex("(?:https?://)?(?:www\\.)?([a-z0-9][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)+)")
    return regex.find(trimmed)?.groupValues?.getOrNull(1)?.let(::cleanDomain)
}

private fun removeEmailDomains(input: String): String {
    val decoded = runCatching { Uri.decode(input) }.getOrElse { input }
    return Regex(
        pattern = "([A-Z0-9._%+-]+)@([A-Z0-9.-]+\\.[A-Z]{2,})",
        options = setOf(RegexOption.IGNORE_CASE),
    ).replace(decoded) { match -> match.groupValues[1] }
}

private fun cleanDomain(domain: String): String = domain
    .trim()
    .trimEnd('.', '/', '?', '#')
    .removePrefix("www.")

private fun inferLikelyDomain(serviceName: String): String? {
    val normalized = normalizeForMatching(removeEmailDomains(serviceName))
    if (normalized.isBlank() || normalized == "unknown") return null

    val compact = normalized
        .replace("authenticator", "")
        .replace("auth", "")
        .replace("account", "")
        .replace("login", "")
        .replace(" ", "")
        .replace(Regex("[^a-z0-9-]"), "")
        .trim('-')

    return compact
        .takeIf { it.length in 2..40 }
        ?.let { "$it.com" }
}

private fun isMeaningfulIdentity(value: String): Boolean {
    val normalized = value.trim().lowercase(Locale.US)
    return normalized.isNotBlank() && normalized != "unknown" && !containsEmailAddress(normalized)
}

private fun containsEmailAddress(value: String): Boolean = Regex(
    pattern = "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
    options = setOf(RegexOption.IGNORE_CASE),
).containsMatchIn(value)

private fun normalizeForMatching(value: String): String = removeEmailDomains(value)
    .lowercase(Locale.US)
    .replace('_', ' ')
    .replace('-', ' ')
    .trim()

private fun parseColorOrAccent(color: String): Color = try {
    Color(android.graphics.Color.parseColor(color))
} catch (_: Exception) {
    Accent
}

private object LogoMemoryCache {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val failedDomains = ConcurrentHashMap.newKeySet<String>()

    fun get(domains: List<String>): Bitmap? = domains.firstNotNullOfOrNull { domain -> bitmaps[domain] }

    suspend fun fetch(domains: List<String>): Bitmap? = withContext(Dispatchers.IO) {
        for (domain in domains) {
            bitmaps[domain]?.let { return@withContext it }
            if (failedDomains.contains(domain)) continue

            val bitmap = fetchFromDomain(domain)
            if (bitmap != null) {
                bitmaps[domain] = bitmap
                return@withContext bitmap
            }
            failedDomains.add(domain)
        }
        null
    }

    private fun fetchFromDomain(domain: String): Bitmap? {
        val encodedDomain = URLEncoder.encode(domain, Charsets.UTF_8.name())
        val urls = listOf(
            "https://logo.clearbit.com/$domain",
            "https://www.google.com/s2/favicons?sz=128&domain=$encodedDomain",
        )

        for (url in urls) {
            runCatching { fetchBitmap(url) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2_500
            readTimeout = 2_500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AzkuraAuth/1.0")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return null
            connection.inputStream.use { input -> BitmapFactory.decodeStream(input) }
        } finally {
            connection.disconnect()
        }
    }
}
