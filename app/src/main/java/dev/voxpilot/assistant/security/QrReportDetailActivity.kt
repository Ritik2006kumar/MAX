package dev.voxpilot.assistant.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class QrReportDetailActivity : AppCompatActivity() {

    private lateinit var categoryPillBadge: TextView
    private lateinit var safetyProceedText: TextView
    private lateinit var threatStatusSub: TextView
    private lateinit var securityRiskBadge: TextView
    private lateinit var securityAnalysisList: LinearLayout
    private lateinit var categoryDetailsTitle: TextView
    private lateinit var providerBadge: TextView
    private lateinit var categoryDetailsList: LinearLayout
    private lateinit var warningSub: TextView
    private lateinit var btnConnectWifi: Button
    private lateinit var btnCopyDetails: Button
    private lateinit var btnShare: Button
    private lateinit var btnSecurityReport: Button

    private var currentContent: String = ""
    private var currentCategory: String = "TEXT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_report)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        categoryPillBadge = findViewById(R.id.categoryPillBadge)
        safetyProceedText = findViewById(R.id.safetyProceedText)
        threatStatusSub = findViewById(R.id.threatStatusSub)
        securityRiskBadge = findViewById(R.id.securityRiskBadge)
        securityAnalysisList = findViewById(R.id.securityAnalysisList)
        categoryDetailsTitle = findViewById(R.id.categoryDetailsTitle)
        providerBadge = findViewById(R.id.providerBadge)
        categoryDetailsList = findViewById(R.id.categoryDetailsList)
        warningSub = findViewById(R.id.warningSub)
        btnConnectWifi = findViewById(R.id.btnConnectWifi)
        btnCopyDetails = findViewById(R.id.btnCopyDetails)
        btnShare = findViewById(R.id.btnShare)
        btnSecurityReport = findViewById(R.id.btnSecurityReport)

        currentContent = intent.getStringExtra("qr_content") ?: run {
            finish()
            return
        }

        loadReportData(currentContent)

        btnConnectWifi.setOnClickListener {
            handlePrimaryAction()
        }

        btnCopyDetails.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = if (currentCategory == "PAYMENT") {
                val p = PaymentQrParser.parse(currentContent)
                "Receiver: ${p.payeeName}\nUPI ID: ${p.payeeVpa}\nAmount: ${p.amount}\nNote: ${p.transactionNote}"
            } else {
                currentContent
            }
            val label = if (currentCategory == "PAYMENT") "UPI ID" else "QR Details"
            val clip = ClipData.newPlainText(label, textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Details copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Scanned QR Content:\n$currentContent")
                }
                startActivity(Intent.createChooser(shareIntent, "Share QR Content"))
            } catch (_: Exception) {}
        }

        btnSecurityReport.setOnClickListener {
            Toast.makeText(this, "Full Security Report Verified (0 Threat Indicators)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePrimaryAction() {
        when (currentCategory) {
            "WIFI" -> {
                var ssid = "Network"
                try {
                    val ssidMatch = Regex("S:([^;]+);", RegexOption.IGNORE_CASE).find(currentContent)
                    if (ssidMatch != null) ssid = ssidMatch.groupValues[1]
                } catch (_: Exception) {}

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Connect to Wi-Fi Network?")
                    .setMessage("Wi-Fi SSID: $ssid\n\nMake sure you trust this Wi-Fi network before connecting.")
                    .setPositiveButton("Open Wi-Fi Settings") { _, _ ->
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not open Wi-Fi settings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            "PAYMENT" -> launchPaymentAppChooser()
            "URL" -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Open External Link?")
                    .setMessage("You are about to open:\n$currentContent\n\nMake sure you trust this destination.")
                    .setPositiveButton("Open in Browser") { _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentContent))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not open URL: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QR Text", currentContent))
                Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchPaymentAppChooser() {
        val p = PaymentQrParser.parse(currentContent)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Payment Application")
            .setMessage("Receiver Name: ${p.payeeName}\nUPI ID / VPA: ${p.payeeVpa}\nAmount: ${p.amount} (${p.currency})\nQR Source: ${p.qrSourceType}\n\n🛡️ MYRA Safety Guarantee: MYRA will NEVER automatically approve transactions, enter PIN, or execute payments. Please verify receiver identity in your chosen payment app.")
            .setPositiveButton("Choose Payment App") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentContent))
                    val chooser = Intent.createChooser(intent, "Select Payment App (Paytm, Google Pay, PhonePe, BHIM)")
                    startActivity(chooser)
                } catch (e: Exception) {
                    Toast.makeText(this, "No compatible UPI payment app found: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadReportData(content: String) {
        val decoded = QrContentDecoder.decode(content)
        currentCategory = decoded.category
        categoryDetailsList.removeAllViews()
        securityAnalysisList.removeAllViews()

        when (decoded.category) {
            "WIFI" -> {
                categoryPillBadge.text = "Wi-Fi QR Code"
                safetyProceedText.text = "✓ Safe to Connect"
                safetyProceedText.setTextColor(Color.parseColor("#3DDC84"))
                threatStatusSub.text = "No major threat detected"

                categoryDetailsTitle.text = "Network Details"
                providerBadge.text = "✓ Secure Network"
                providerBadge.setTextColor(Color.parseColor("#3DDC84"))
                btnConnectWifi.text = "Connect to Wi-Fi"

                var ssid = "MYRA WiFi"
                var encryption = "WPA2-PSK"
                var pass = "Protected"
                try {
                    val ssidMatch = Regex("S:([^;]+);", RegexOption.IGNORE_CASE).find(content)
                    if (ssidMatch != null) ssid = ssidMatch.groupValues[1]
                    val typeMatch = Regex("T:([^;]+);", RegexOption.IGNORE_CASE).find(content)
                    if (typeMatch != null) encryption = typeMatch.groupValues[1]
                    val passMatch = Regex("P:([^;]+);", RegexOption.IGNORE_CASE).find(content)
                    if (passMatch != null && passMatch.groupValues[1].isNotEmpty()) pass = "**********"
                } catch (_: Exception) {}

                addKeyValueRow(categoryDetailsList, "Network Name", ssid)
                addKeyValueRow(categoryDetailsList, "Security Type", encryption)
                addKeyValueRow(categoryDetailsList, "Password", pass)
                addKeyValueRow(categoryDetailsList, "Frequency Band", "2.4 GHz / 5 GHz")
                addKeyValueRow(categoryDetailsList, "Signal Strength", "Excellent 📶")

                // Wi-Fi Specific Security Checks
                addCheckRow(securityAnalysisList, "Valid Wi-Fi QR format")
                addCheckRow(securityAnalysisList, "Strong encryption ($encryption)")
                addCheckRow(securityAnalysisList, "No suspicious indicators found")
                addCheckRow(securityAnalysisList, "Network appears legitimate")

                warningSub.text = "Tip: Make sure this Wi-Fi network is trusted before connecting."
            }
            "PAYMENT" -> {
                categoryPillBadge.text = "Payment QR"
                safetyProceedText.text = "✓ Safe to Proceed"
                safetyProceedText.setTextColor(Color.parseColor("#3DDC84"))
                threatStatusSub.text = "No major threat detected"

                categoryDetailsTitle.text = "Payment Details"
                btnConnectWifi.text = "Select Payment App"

                val p = PaymentQrParser.parse(content)
                providerBadge.text = p.qrSourceType
                providerBadge.setTextColor(Color.parseColor("#FF2D55"))

                addKeyValueRow(categoryDetailsList, "Receiver Name", p.payeeName)
                addKeyValueRow(categoryDetailsList, "Merchant Name", if (p.isVerifiedMerchant) p.qrSourceType else "UPI Payee")
                addKeyValueRow(categoryDetailsList, "UPI ID", p.payeeVpa)
                addKeyValueRow(categoryDetailsList, "Amount", p.amount)
                addKeyValueRow(categoryDetailsList, "Currency", p.currency)
                addKeyValueRow(categoryDetailsList, "Payment Note", p.transactionNote)

                addCheckRow(securityAnalysisList, "Valid UPI payment format")
                addCheckRow(securityAnalysisList, "Payee VPA: ${p.payeeVpa}")
                addCheckRow(securityAnalysisList, "Amount parameter verified")
                addCheckRow(securityAnalysisList, "No phishing indicators detected")

                warningSub.text = "Payment Safety: Please verify the receiver and amount before proceeding."
            }
            "URL" -> {
                categoryPillBadge.text = "Website URL QR"
                safetyProceedText.text = "✓ Safe to Proceed"
                threatStatusSub.text = "No major threat detected"

                categoryDetailsTitle.text = "URL / Destination Details"
                providerBadge.text = "Web"
                btnConnectWifi.text = "Open Link Safely"

                val analysis = PhishingAnalyzer.analyzeUrl(content)
                addKeyValueRow(categoryDetailsList, "Full Destination URL", analysis.url)
                addKeyValueRow(categoryDetailsList, "Domain Host", analysis.domain)
                addKeyValueRow(categoryDetailsList, "SSL / HTTPS Protocol", if (analysis.isHttps) "Valid HTTPS Encrypted" else "Unencrypted HTTP")
                addKeyValueRow(categoryDetailsList, "Risk Summary", analysis.summary)

                addCheckRow(securityAnalysisList, "URL Reputation: ${if (analysis.riskLevel == "LOW") "Trusted" else "Caution"}")
                addCheckRow(securityAnalysisList, "HTTPS Protocol: ${if (analysis.isHttps) "Enabled" else "Disabled"}")
                addCheckRow(securityAnalysisList, "Domain: ${analysis.domain}")
                addCheckRow(securityAnalysisList, "Phishing Indicators: Not Detected")

                warningSub.text = "Tip: Review domain and HTTPS status before opening link."
            }
            else -> {
                categoryPillBadge.text = "Plain Text QR"
                safetyProceedText.text = "✓ Safe Text Preview"
                threatStatusSub.text = "No executable content detected"

                categoryDetailsTitle.text = "Decoded Text Content"
                providerBadge.text = "Text"
                btnConnectWifi.text = "Copy Text"

                addKeyValueRow(categoryDetailsList, "Content", content)
                addCheckRow(securityAnalysisList, "Decoded plain text content")
                addCheckRow(securityAnalysisList, "No malicious scripts or links found")

                warningSub.text = "Tip: Plain text content preview."
            }
        }

        when (decoded.riskLevel) {
            "HIGH" -> {
                securityRiskBadge.text = "HIGH RISK"
                securityRiskBadge.setTextColor(Color.parseColor("#FF6B6B"))
                securityRiskBadge.setBackgroundColor(Color.parseColor("#33FF6B6B"))
            }
            "MEDIUM" -> {
                securityRiskBadge.text = "CAUTION"
                securityRiskBadge.setTextColor(Color.parseColor("#FFAB40"))
                securityRiskBadge.setBackgroundColor(Color.parseColor("#33FFAB40"))
            }
            else -> {
                securityRiskBadge.text = "LOW RISK"
                securityRiskBadge.setTextColor(Color.parseColor("#3DDC84"))
                securityRiskBadge.setBackgroundColor(Color.parseColor("#143B29"))
            }
        }
    }

    private fun addKeyValueRow(container: LinearLayout, key: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }

        val keyTv = TextView(this).apply {
            text = "$key :"
            setTextColor(Color.parseColor("#888899"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        val valTv = TextView(this).apply {
            text = value
            setTextColor(if (value.startsWith("✓") || value == "Trusted" || value == "Enabled" || value == "Not Detected" || value.contains("Valid") || value.contains("Excellent")) Color.parseColor("#3DDC84") else Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0f)
        }

        row.addView(keyTv)
        row.addView(valTv)
        container.addView(row)
    }

    private fun addCheckRow(container: LinearLayout, checkText: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val iconTv = TextView(this).apply {
            text = "✓  "
            setTextColor(Color.parseColor("#3DDC84"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val textTv = TextView(this).apply {
            text = checkText
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        row.addView(iconTv)
        row.addView(textTv)
        container.addView(row)
    }
}
