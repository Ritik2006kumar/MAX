package dev.voxpilot.assistant.security

import android.webkit.URLUtil
import java.net.URL
import java.util.Locale

object PhishingAnalyzer {

    data class AnalysisResult(
        val url: String,
        val riskLevel: String, // HIGH, MEDIUM, LOW, UNKNOWN
        val summary: String,
        val indicators: List<String>,
        val isHttps: Boolean,
        val domain: String
    )

    fun analyzeUrl(rawUrl: String): AnalysisResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || !URLUtil.isValidUrl(trimmed)) {
            return AnalysisResult(
                url = rawUrl,
                riskLevel = "UNKNOWN",
                summary = "Invalid or malformed URL format",
                indicators = listOf("URL format is invalid or incomplete."),
                isHttps = false,
                domain = "Unknown"
            )
        }

        val indicators = mutableListOf<String>()
        var isHttps = false
        var domain = "Unknown"
        var riskScore = 0

        try {
            val urlObj = URL(trimmed)
            isHttps = urlObj.protocol.lowercase(Locale.ROOT) == "https"
            domain = urlObj.host ?: "Unknown"

            // 1. Protocol check
            if (!isHttps) {
                riskScore += 2
                indicators.appendIndicator("Unsecure HTTP Protocol: Connection is not encrypted using HTTPS.")
            } else {
                indicators.appendIndicator("Secure HTTPS Protocol: Connection uses TLS/SSL encryption.")
            }

            // 2. IP Address Hostname check
            if (isIpAddress(domain)) {
                riskScore += 4
                indicators.appendIndicator("Suspicious IP-Based URL: Hostname is a raw IP address instead of a domain name.")
            }

            // 3. Subdomain analysis (Excessive subdomains)
            val parts = domain.split(".")
            if (parts.size > 3) {
                riskScore += 2
                indicators.appendIndicator("Excessive Subdomains: Domain contains ${parts.size} segments, often used to spoof legitimate brands.")
            }

            // 4. Brand spoofing / Sensitive keywords in subdomain
            val brandKeywords = listOf("paypal", "google", "apple", "netflix", "amazon", "microsoft", "bank", "login", "verify", "secure", "update", "account", "support")
            val lowerDomain = domain.lowercase(Locale.ROOT)
            val lowerPath = (urlObj.path ?: "").lowercase(Locale.ROOT)

            val matchedKeywords = brandKeywords.filter { lowerDomain.contains(it) || lowerPath.contains(it) }
            if (matchedKeywords.isNotEmpty() && !isKnownOfficialDomain(lowerDomain)) {
                riskScore += 3
                indicators.appendIndicator("Potential Brand Spoofing: Contains sensitive brand/action keywords (${matchedKeywords.joinToString(", ")} on an unverified domain.")
            }

            // 5. Excessive length & obfuscation characters
            if (trimmed.length > 75) {
                riskScore += 1
                indicators.appendIndicator("Excessive URL Length: URL is unusually long (>75 characters), which can be used to hide malicious parameters.")
            }

            if (trimmed.contains("@")) {
                riskScore += 3
                indicators.appendIndicator("Deceptive '@' Character: The '@' symbol in URLs can obscure the true destination domain.")
            }

        } catch (e: Exception) {
            return AnalysisResult(
                url = rawUrl,
                riskLevel = "UNKNOWN",
                summary = "Error parsing URL: ${e.message}",
                indicators = listOf("Could not fully parse URL structure."),
                isHttps = false,
                domain = "Unknown"
            )
        }

        val riskLevel = when {
            riskScore >= 4 -> "HIGH"
            riskScore >= 2 -> "MEDIUM"
            else -> "LOW"
        }

        val summary = when (riskLevel) {
            "HIGH" -> "High risk of phishing or scam indicators detected."
            "MEDIUM" -> "Moderate risk indicators detected. Exercise caution."
            else -> "No suspicious patterns detected locally — exercise caution. (Local analysis only)"
        }

        return AnalysisResult(
            url = trimmed,
            riskLevel = riskLevel,
            summary = summary,
            indicators = indicators,
            isHttps = isHttps,
            domain = domain
        )
    }

    private fun isIpAddress(host: String): Boolean {
        val ipRegex = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
        return ipRegex.matches(host) || host.startsWith("[") && host.endsWith("]")
    }

    private fun isKnownOfficialDomain(domain: String): Boolean {
        val official = listOf("google.com", "apple.com", "microsoft.com", "amazon.com", "netflix.com", "paypal.com")
        return official.any { domain == it || domain.endsWith(".$it") }
    }

    private fun MutableList<String>.appendIndicator(text: String) {
        this.add(text)
    }
}
