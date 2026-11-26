package dev.voxpilot.assistant.security

import android.net.Uri
import java.net.URLDecoder
import java.util.Locale

object PaymentQrParser {

    data class PaymentQrData(
        val rawUri: String,
        val payeeVpa: String,
        val payeeName: String,
        val merchantCode: String,
        val amount: String,
        val currency: String,
        val transactionNote: String,
        val transactionRef: String,
        val qrSourceType: String,
        val isFixedAmount: Boolean,
        val isVerifiedMerchant: Boolean
    )

    fun isPaymentQr(raw: String): Boolean {
        val lower = raw.trim().lowercase(Locale.ROOT)
        return lower.startsWith("upi://pay") ||
                lower.startsWith("paytm://") ||
                lower.startsWith("gpay://") ||
                lower.startsWith("phonepe://") ||
                (lower.contains("upi://pay") && lower.contains("pa="))
    }

    fun parse(raw: String): PaymentQrData {
        var vpa = ""
        var name = ""
        var mc = ""
        var amount = ""
        var currency = "INR"
        var note = ""
        var ref = ""
        var sourceType = "UPI QR"

        try {
            val trimmed = raw.trim()
            if (trimmed.lowercase(Locale.ROOT).startsWith("paytm://")) sourceType = "Paytm QR"
            else if (trimmed.lowercase(Locale.ROOT).startsWith("phonepe://")) sourceType = "PhonePe QR"
            else if (trimmed.lowercase(Locale.ROOT).startsWith("gpay://")) sourceType = "Google Pay QR"

            val uri = Uri.parse(trimmed)
            vpa = decodeParam(uri, "pa")
            name = decodeParam(uri, "pn")
            mc = decodeParam(uri, "mc")
            amount = decodeParam(uri, "am")
            currency = decodeParam(uri, "cu").ifEmpty { "INR" }
            note = decodeParam(uri, "tn")
            ref = decodeParam(uri, "tr").ifEmpty { decodeParam(uri, "tid") }
        } catch (_: Exception) {}

        val isFixed = amount.isNotEmpty() && amount != "0" && amount != "0.00"
        val isMerchant = mc.isNotEmpty() && mc != "0000"

        return PaymentQrData(
            rawUri = raw,
            payeeVpa = vpa.ifEmpty { "Unavailable" },
            payeeName = name.ifEmpty { "Unverified Receiver" },
            merchantCode = mc.ifEmpty { "N/A" },
            amount = if (isFixed) "₹$amount" else "User Editable",
            currency = currency,
            transactionNote = note.ifEmpty { "No Payment Note" },
            transactionRef = ref.ifEmpty { "N/A" },
            qrSourceType = sourceType,
            isFixedAmount = isFixed,
            isVerifiedMerchant = isMerchant
        )
    }

    private fun decodeParam(uri: Uri, param: String): String {
        return try {
            val value = uri.getQueryParameter(param) ?: ""
            URLDecoder.decode(value, "UTF-8").trim()
        } catch (_: Exception) {
            ""
        }
    }
}
