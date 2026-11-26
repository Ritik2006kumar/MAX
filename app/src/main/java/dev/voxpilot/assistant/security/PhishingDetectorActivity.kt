package dev.voxpilot.assistant.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class PhishingDetectorActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var pasteBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var clearHistoryBtn: TextView
    private lateinit var emptyHistoryText: TextView
    private lateinit var historyListView: ListView

    private val historyList = mutableListOf<PhishingHistoryManager.ScanRecord>()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phishing_detector)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        urlInput = findViewById(R.id.urlInput)
        pasteBtn = findViewById(R.id.pasteBtn)
        clearBtn = findViewById(R.id.clearBtn)
        scanBtn = findViewById(R.id.scanBtn)
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)
        historyListView = findViewById(R.id.historyListView)

        pasteBtn.setOnClickListener { pasteFromClipboard() }
        clearBtn.setOnClickListener { urlInput.setText("") }
        scanBtn.setOnClickListener { performScan() }
        clearHistoryBtn.setOnClickListener {
            PhishingHistoryManager.clearHistory(this)
            loadHistory()
            Toast.makeText(this, "Scan history cleared", Toast.LENGTH_SHORT).show()
        }

        historyAdapter = HistoryAdapter(this, historyList)
        historyListView.adapter = historyAdapter

        historyListView.setOnItemClickListener { _, _, position, _ ->
            val record = historyList[position]
            openReport(record.url)
        }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            urlInput.setText(text)
            Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performScan() {
        val rawUrl = urlInput.text.toString().trim()
        if (rawUrl.isEmpty()) {
            Toast.makeText(this, "Please enter or paste a URL to scan", Toast.LENGTH_SHORT).show()
            return
        }

        val result = PhishingAnalyzer.analyzeUrl(rawUrl)
        val record = PhishingHistoryManager.ScanRecord(
            url = result.url,
            riskLevel = result.riskLevel,
            summary = result.summary,
            indicators = result.indicators,
            timestamp = System.currentTimeMillis()
        )

        PhishingHistoryManager.saveRecord(this, record)
        openReport(result.url)
    }

    private fun openReport(url: String) {
        val intent = Intent(this, PhishingReportDetailActivity::class.java).apply {
            putExtra("scan_url", url)
        }
        startActivity(intent)
    }

    private fun loadHistory() {
        historyList.clear()
        historyList.addAll(PhishingHistoryManager.getHistory(this))
        historyAdapter.notifyDataSetChanged()

        if (historyList.isEmpty()) {
            emptyHistoryText.visibility = View.VISIBLE
            historyListView.visibility = View.GONE
        } else {
            emptyHistoryText.visibility = View.GONE
            historyListView.visibility = View.VISIBLE
        }
    }

    private class HistoryAdapter(context: Context, private val items: List<PhishingHistoryManager.ScanRecord>) :
        ArrayAdapter<PhishingHistoryManager.ScanRecord>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_phishing_history, parent, false)
            val record = items[position]

            val urlView = view.findViewById<TextView>(R.id.historyUrl)
            val summaryView = view.findViewById<TextView>(R.id.historySummary)
            val badgeView = view.findViewById<TextView>(R.id.historyBadge)

            urlView.text = record.url
            summaryView.text = record.summary
            badgeView.text = record.riskLevel

            when (record.riskLevel) {
                "HIGH" -> {
                    badgeView.setTextColor(0xFFFF6B6B.toInt())
                    badgeView.setBackgroundColor(0x33FF6B6B)
                }
                "MEDIUM" -> {
                    badgeView.setTextColor(0xFFFFAB40.toInt())
                    badgeView.setBackgroundColor(0x33FFAB40)
                }
                "LOW" -> {
                    badgeView.setTextColor(0xFF3DDC84.toInt())
                    badgeView.setBackgroundColor(0x333DDC84)
                }
                else -> {
                    badgeView.setTextColor(0xFF8E8E9A.toInt())
                    badgeView.setBackgroundColor(0x338E8E9A)
                }
            }

            return view
        }
    }
}
