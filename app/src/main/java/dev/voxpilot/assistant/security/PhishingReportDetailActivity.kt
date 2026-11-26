package dev.voxpilot.assistant.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class PhishingReportDetailActivity : AppCompatActivity() {

    private lateinit var reportBadge: TextView
    private lateinit var reportSummary: TextView
    private lateinit var reportUrlText: TextView
    private lateinit var indicatorsContainer: LinearLayout
    private lateinit var copyUrlBtn: Button
    private lateinit var deleteReportBtn: Button

    private var currentUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phishing_report)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        reportBadge = findViewById(R.id.reportBadge)
        reportSummary = findViewById(R.id.reportSummary)
        reportUrlText = findViewById(R.id.reportUrlText)
        indicatorsContainer = findViewById(R.id.indicatorsContainer)
        copyUrlBtn = findViewById(R.id.copyUrlBtn)
        deleteReportBtn = findViewById(R.id.deleteReportBtn)

        currentUrl = intent.getStringExtra("scan_url") ?: run {
            finish()
            return
        }

        loadReportData(currentUrl)

        copyUrlBtn.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Scanned URL", currentUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        deleteReportBtn.setOnClickListener {
            PhishingHistoryManager.deleteRecord(this, currentUrl)
            Toast.makeText(this, "Scan record deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadReportData(url: String) {
        val history = PhishingHistoryManager.getHistory(this)
        val record = history.find { it.url.equals(url, true) } ?: run {
            // Fallback to fresh analysis if not found in history
            val result = PhishingAnalyzer.analyzeUrl(url)
            PhishingHistoryManager.ScanRecord(
                url = result.url,
                riskLevel = result.riskLevel,
                summary = result.summary,
                indicators = result.indicators,
                timestamp = System.currentTimeMillis()
            )
        }

        reportUrlText.text = record.url
        reportSummary.text = record.summary
        reportBadge.text = record.riskLevel

        when (record.riskLevel) {
            "HIGH" -> {
                reportBadge.setTextColor(0xFFFF6B6B.toInt())
                reportBadge.setBackgroundColor(0x33FF6B6B)
            }
            "MEDIUM" -> {
                reportBadge.setTextColor(0xFFFFAB40.toInt())
                reportBadge.setBackgroundColor(0x33FFAB40)
            }
            "LOW" -> {
                reportBadge.setTextColor(0xFF3DDC84.toInt())
                reportBadge.setBackgroundColor(0x333DDC84)
            }
            else -> {
                reportBadge.setTextColor(0xFF8E8E9A.toInt())
                reportBadge.setBackgroundColor(0x338E8E9A)
            }
        }

        indicatorsContainer.removeAllViews()
        for (indicator in record.indicators) {
            val tv = TextView(this).apply {
                text = " • $indicator"
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 14f
                setPadding(0, 4, 0, 8)
                setLineSpacing(4f, 1.1f)
            }
            indicatorsContainer.addView(tv)
        }

        if (record.indicators.isEmpty()) {
            val tv = TextView(this).apply {
                text = " • No suspicious structural indicators flagged locally."
                setTextColor(0xFF3DDC84.toInt())
                textSize = 14f
                setPadding(0, 4, 0, 8)
            }
            indicatorsContainer.addView(tv)
        }
    }
}
