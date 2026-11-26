package dev.voxpilot.assistant.security

import android.webkit.URLUtil
import java.util.Locale

object QrContentDecoder {

    data class DecodedQr(
        val rawContent: String,
        val category: String, // URL, WIFI, CONTACT, PAYMENT, TEXT
        val riskLevel: String, // HIGH, MEDIUM, LOW, UNKNOWN
        val summary: String,
        val details: List<String>
    )

    fun decode(raw: String): DecodedQr {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return DecodedQr(raw, "TEXT", "LOW", "Empty QR Code", listOf("QR code contains no data."))
        }

        // 1. Payment QR Code (UPI / Paytm / GPay / PhonePe)
        if (PaymentQrParser.isPaymentQr(trimmed)) {
            return decodePayment(trimmed)
        }

        // 2. Wi-Fi QR Code (WIFI:T:WPA;S:MyNetwork;P:password;;)
        if (trimmed.uppercase(Locale.ROOT).startsWith("WIFI:")) {
            return decodeWifi(trimmed)
        }

        // 3. Contact vCard
        if (trimmed.uppercase(Locale.ROOT).contains("BEGIN:VCARD")) {
            return decodeVCard(trimmed)
        }

        // 4. URL
        if (URLUtil.isValidUrl(trimmed) || trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) || trimmed.startsWith("www.", true)) {
            val validUrl = if (trimmed.startsWith("www.", true)) "https://$trimmed" else trimmed
            val analysis = PhishingAnalyzer.analyzeUrl(validUrl)
            return DecodedQr(
                rawContent = validUrl,
                category = "URL",
                riskLevel = analysis.riskLevel,
                summary = "URL Link: ${analysis.summary}",
                details = analysis.indicators
            )
        }

        // 5. Plain Text / Other
        return DecodedQr(
            rawContent = trimmed,
            category = "TEXT",
            riskLevel = "LOW",
            summary = "Plain Text Content",
            details = listOf("Decoded text: $trimmed", "No executable links or configuration scripts detected.")
        )
    }

    private fun decodePayment(raw: String): DecodedQr {
        val p = PaymentQrParser.parse(raw)
        val details = mutableListOf(
            "Receiver Name: ${p.payeeName}",
            "UPI ID / VPA: ${p.payeeVpa}",
            "Amount: ${p.amount} (${p.currency})",
            "Payment Note: ${p.transactionNote}",
            "QR Source: ${p.qrSourceType}",
            "Merchant Code: ${p.merchantCode}",
            "Transaction Ref ID: ${p.transactionRef}",
            "Safety Warning: MYRA will NEVER automatically approve transactions, enter PIN, or execute payments. Verify receiver identity before completing payment in your UPI app."
        )

        val risk = when {
            p.payeeVpa == "Unavailable" -> "HIGH"
            p.payeeName == "Unverified Receiver" -> "MEDIUM"
            else -> "LOW"
        }

        val summary = if (p.isFixedAmount) {
            "Fixed Payment Request of ${p.amount} to ${p.payeeName}"
        } else {
            "Payment Request to ${p.payeeName} (${p.payeeVpa})"
        }

        return DecodedQr(
            rawContent = raw,
            category = "PAYMENT",
            riskLevel = risk,
            summary = summary,
            details = details
        )
    }

    private fun decodeWifi(raw: String): DecodedQr {
        var ssid = "Unknown"
        var encryption = "Open"
        var password = ""

        try {
            val ssidMatch = Regex("S:([^;]+);", RegexOption.IGNORE_CASE).find(raw)
            if (ssidMatch != null) ssid = ssidMatch.groupValues[1]

            val typeMatch = Regex("T:([^;]+);", RegexOption.IGNORE_CASE).find(raw)
            if (typeMatch != null) encryption = typeMatch.groupValues[1]

            val passMatch = Regex("P:([^;]+);", RegexOption.IGNORE_CASE).find(raw)
            if (passMatch != null) password = passMatch.groupValues[1]
        } catch (_: Exception) {}

        val details = mutableListOf(
            "Wi-Fi Network SSID: $ssid",
            "Encryption Type: $encryption",
            "Password: ${if (password.isNotEmpty()) "******** (Protected)" else "None (Open Network)"}",
            "Security Note: Connecting to unknown Wi-Fi networks can expose traffic. Do not connect automatically without verification."
        )

        val risk = if (encryption.equals("nopass", true) || encryption.isEmpty()) "MEDIUM" else "LOW"
        val summary = if (risk == "MEDIUM") "Unsecured Open Wi-Fi Network QR" else "Wi-Fi Network Configuration QR"

        return DecodedQr(
            rawContent = raw,
            category = "WIFI",
            riskLevel = risk,
            summary = summary,
            details = details
        )
    }

    private fun decodeVCard(raw: String): DecodedQr {
        var name = "Unknown Contact"
        try {
            val fnMatch = Regex("FN:([^\n\r]+)", RegexOption.IGNORE_CASE).find(raw)
            if (fnMatch != null) name = fnMatch.groupValues[1]
        } catch (_: Exception) {}

        return DecodedQr(
            rawContent = raw,
            category = "CONTACT",
            riskLevel = "LOW",
            summary = "Contact vCard for $name",
            details = listOf("Contact Name: $name", "Contains vCard contact information.", "Safe to import or view details.")
        )
    }
}
