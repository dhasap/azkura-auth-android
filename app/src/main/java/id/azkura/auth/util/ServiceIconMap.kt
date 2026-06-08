package id.azkura.auth.util

import kotlin.math.abs

/**
 * Service icon/color mapping.
 * Port of src/core/service-icons.js — colors and letter icons for popular services.
 * SVG icons are not used on Android; we use colored letters/emoji in Compose UI.
 */
data class ServiceMeta(
    val bg: String,
    val letter: String,
    val emoji: String? = null,
    val hasLogo: Boolean = false,
)

object ServiceIconMap {

    /** Known services: keyword -> background color. */
    private val serviceColors: Map<String, String> = mapOf(
        "google" to "#4285F4",
        "github" to "#24292e",
        "discord" to "#5865F2",
        "microsoft" to "#00A4EF",
        "notion" to "#000000",
        "twitter" to "#000000",
        "x" to "#000000",
        "facebook" to "#1877F2",
        "instagram" to "#E1306C",
        "linkedin" to "#0A66C2",
        "aws" to "#FF9900",
        "amazon" to "#FF9900",
        "slack" to "#4A154B",
        "dropbox" to "#0061FF",
        "gitlab" to "#FC6D26",
        "bitbucket" to "#0052CC",
        "apple" to "#555555",
        "reddit" to "#FF4500",
        "twitch" to "#9146FF",
        "cloudflare" to "#F48120",
        "digitalocean" to "#0080FF",
        "vercel" to "#000000",
        "netlify" to "#00C7B7",
        "firebase" to "#FFCA28",
        "docker" to "#2496ED",
        "figma" to "#F24E1E",
        "trello" to "#0079BF",
        "stripe" to "#6772E5",
        "coinbase" to "#0052FF",
        "binance" to "#F0B90B",
        "paypal" to "#003087",
        "shopify" to "#96BF48",
        "wordpress" to "#21759B",
        "steam" to "#1B2838",
        "epic" to "#2F2F2F",
        "playstation" to "#003791",
        "xbox" to "#107C10",
        "nintendo" to "#E60012",
        "spotify" to "#1DB954",
        "snapchat" to "#FFFC00",
        "tiktok" to "#000000",
        "telegram" to "#26A5E4",
        "whatsapp" to "#25D366",
        "signal" to "#3A76F0",
        "zoom" to "#2D8CFF",
        "teams" to "#6264A7",
        "heroku" to "#430098",
        "namecheap" to "#DE3723",
        "godaddy" to "#1BDBDB",
        "porkbun" to "#F27141",
        "ovh" to "#123F6D",
        "hetzner" to "#D50C2D",
        "linode" to "#00A95C",
        "vultr" to "#007BFC",
        "oracle" to "#F80000",
        "azure" to "#0078D4",
        "gcp" to "#4285F4",
        "ibm" to "#054ADA",
        "salesforce" to "#00A1E0",
        "zendesk" to "#03363D",
        "jira" to "#0052CC",
        "confluence" to "#172B4D",
        "asana" to "#F06A6A",
        "monday" to "#6161FF",
        "hubspot" to "#FF7A59",
        "mailchimp" to "#FFE01B",
        "sendgrid" to "#3368FA",
        "twilio" to "#F22F46",
        "auth0" to "#EB5424",
        "okta" to "#007DC1",
        "1password" to "#0572EC",
        "lastpass" to "#D32D27",
        "bitwarden" to "#175DDC",
        "dashlane" to "#0E353D",
        "protonmail" to "#6D4AFF",
        "proton" to "#6D4AFF",
        "tutanota" to "#840010",
        "fastmail" to "#69ACE2",
        "yahoo" to "#6001D2",
        "outlook" to "#0078D4",
        "icloud" to "#3693F3",
        "mega" to "#D90007",
        "onedrive" to "#0078D4",
        "box" to "#0061D5",
        "evernote" to "#00A82D",
        "notion" to "#000000",
        "obsidian" to "#7C3AED",
        "roam" to "#337EFF",
        "airtable" to "#18BFFF",
        "coda" to "#F46A54",
        "linear" to "#5E6AD2",
        "npm" to "#CB3837",
        "pypi" to "#3775A9",
        "nuget" to "#004880",
        "maven" to "#C71A36",
        "homebrew" to "#FBB040",
        "terraform" to "#7B42BC",
        "ansible" to "#EE0000",
        "kubernetes" to "#326CE5",
        "nginx" to "#009639",
        "apache" to "#D22128",
        "caddy" to "#1F88C0",
        "jenkins" to "#D24939",
        "circleci" to "#343434",
        "travis" to "#3EAAAF",
        "github actions" to "#2088FF",
        "vercel" to "#000000",
        "render" to "#46E3B7",
        "fly" to "#7B3BE2",
        "railway" to "#0B0D0E",
        "supabase" to "#3FCF8E",
        "planetscale" to "#000000",
        "mongodb" to "#47A248",
        "redis" to "#DC382D",
        "postgres" to "#4169E1",
        "mysql" to "#4479A1",
        "elastic" to "#005571",
        "algolia" to "#003DFF",
        "sentry" to "#362D59",
        "datadog" to "#632CA6",
        "grafana" to "#F46800",
        "pagerduty" to "#06AC38",
        "opsgenie" to "#172B4D",
        "uptime" to "#28B463",
        "pingdom" to "#FFF000",
        "statuspage" to "#3571DC",
        "atlassian" to "#0052CC",
        "postman" to "#FF6C37",
        "insomnia" to "#5849BE",
        "swagger" to "#85EA2D",
        "graphql" to "#E10098",
        "hasura" to "#1EB4D4",
        "prisma" to "#2D3748",
        "sanity" to "#F36458",
        "contentful" to "#2478CC",
        "strapi" to "#4945FF",
        "ghost" to "#15171A",
        "webflow" to "#4353FF",
        "squarespace" to "#000000",
        "wix" to "#0C6EFC",
        "godot" to "#478CBF",
        "unity" to "#222C37",
        "unreal" to "#0E1128",
        "roblox" to "#000000",
        "riot" to "#D32936",
        "blizzard" to "#01B2F1",
        "ubisoft" to "#000000",
        "ea" to "#000000",
        "rockstar" to "#FCAF17",
        "itch" to "#FA5C5C",
        "gog" to "#86328A",
        "humble" to "#CC2929",
        "kraken" to "#5741D9",
        "gemini" to "#00DCFA",
        "crypto.com" to "#002D74",
        "kucoin" to "#23AF91",
        "gate" to "#17E6A1",
        "bybit" to "#F7A600",
        "okx" to "#000000",
        "metamask" to "#F6851B",
        "phantom" to "#AB9FF2",
        "robinhood" to "#00C805",
        "etoro" to "#69C83E",
        "interactive brokers" to "#D41F22",
        "charles schwab" to "#00A0DF",
        "fidelity" to "#3B7D23",
        "vanguard" to "#822529",
        "wise" to "#9FE870",
        "revolut" to "#0075EB",
        "n26" to "#36A18B",
        "monzo" to "#FF3B72",
        "chime" to "#1FC64C",
        "cash app" to "#00D632",
        "venmo" to "#3D95CE",
        "zelle" to "#6C1CD3",
        "plaid" to "#000000",
        "square" to "#006AFF",
        "adobe" to "#FF0000",
        "canva" to "#00C4CC",
        "miro" to "#FFD02F",
        "loom" to "#625DF5",
        "calendly" to "#006BFF",
        "zoom" to "#2D8CFF",
        "webex" to "#000000",
        "meet" to "#00897B",
        "skype" to "#00AFF0",
        "clickup" to "#7B68EE",
        "basecamp" to "#1D2D35",
        "todoist" to "#E44332",
        "ticktick" to "#4772FA",
        "habitica" to "#6133B4",
        "duolingo" to "#58CC02",
        "coursera" to "#0056D2",
        "udemy" to "#A435F0",
        "edx" to "#02262B",
        "khan" to "#14BF96",
        "leetcode" to "#FFA116",
        "hackerrank" to "#00EA64",
        "codewars" to "#AD2C27",
        "codeforces" to "#1F8ACB",
        "codepen" to "#000000",
        "codesandbox" to "#151515",
        "replit" to "#F26207",
        "stackblitz" to "#1389FD",
        "netlify" to "#00C7B7",
        "upwork" to "#14A800",
        "fiverr" to "#1DBF73",
        "toptal" to "#3863A0",
        "indeed" to "#2164F3",
        "glassdoor" to "#0CAA41",
        "angellist" to "#000000",
        "producthunt" to "#DA552F",
        "medium" to "#000000",
        "substack" to "#FF6719",
        "dev.to" to "#0A0A0A",
        "hashnode" to "#2962FF",
        "wordpress" to "#21759B",
        "blogger" to "#FF5722",
        "tumblr" to "#001935",
        "pinterest" to "#E60023",
        "flickr" to "#0063DC",
        "unsplash" to "#000000",
        "pexels" to "#05A081",
        "dribbble" to "#EA4C89",
        "behance" to "#1769FF",
    )

    /**
     * Resolve service metadata from an issuer name.
     * Matches by lowercase contains — same logic as the JS version.
     */
    fun getServiceMeta(issuer: String): ServiceMeta {
        val name = issuer.lowercase().trim()
        if (name.isEmpty()) {
            return ServiceMeta(
                bg = generateColorFromString(""),
                letter = "?",
            )
        }

        for ((key, color) in serviceColors) {
            if (name.contains(key)) {
                return ServiceMeta(
                    bg = color,
                    letter = issuer.first().uppercaseChar().toString(),
                    hasLogo = true,
                )
            }
        }

        return ServiceMeta(
            bg = generateColorFromString(name),
            letter = issuer.first().uppercaseChar().toString(),
            emoji = "\uD83D\uDCF1",  // phone emoji fallback
        )
    }

    /** Generate a consistent HSL color from a string. Same algorithm as JS version. */
    private fun generateColorFromString(str: String): String {
        val input = str.ifEmpty { "A" }
        var hash = 0
        for (c in input) {
            hash = (hash * 31 + c.code) and 0x7FFFFFFF
        }
        val hue = abs(hash) % 360
        // Return as hex approximation of hsl(hue, 60%, 40%)
        return hslToHex(hue, 0.6f, 0.4f)
    }

    private fun hslToHex(h: Int, s: Float, l: Float): String {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val ri = ((r + m) * 255).toInt().coerceIn(0, 255)
        val gi = ((g + m) * 255).toInt().coerceIn(0, 255)
        val bi = ((b + m) * 255).toInt().coerceIn(0, 255)

        return "#%02X%02X%02X".format(ri, gi, bi)
    }
}
