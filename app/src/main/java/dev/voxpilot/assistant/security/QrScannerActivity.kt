package dev.voxpilot.assistant.security

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.voxpilot.assistant.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var isScanningEnabled = true
    private var lastScannedContent = ""
    private var lastScannedTime = 0L

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        viewFinder = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.historyBtn).setOnClickListener {
            // Open history list / dialog or view recent scans
            showHistoryDialog()
        }

        checkCameraPermissionAndStart()
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val scanner = BarcodeScanning.getClient()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && isScanningEnabled) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue
                                if (!rawValue.isNullOrEmpty()) {
                                    val now = System.currentTimeMillis()
                                    // Prevent duplicate scan events within 3 seconds for the same QR code
                                    if (rawValue != lastScannedContent || (now - lastScannedTime > 3000)) {
                                        lastScannedContent = rawValue
                                        lastScannedTime = now
                                        handleScannedQr(rawValue)
                                        break
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            // Ignore frame analysis failures
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleScannedQr(rawContent: String) {
        isScanningEnabled = false
        val decoded = QrContentDecoder.decode(rawContent)
        val record = QrHistoryManager.QrRecord(
            rawContent = decoded.rawContent,
            category = decoded.category,
            riskLevel = decoded.riskLevel,
            summary = decoded.summary,
            details = decoded.details,
            timestamp = System.currentTimeMillis()
        )
        QrHistoryManager.saveRecord(this, record)

        val intent = Intent(this, QrReportDetailActivity::class.java).apply {
            putExtra("qr_content", decoded.rawContent)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        isScanningEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun showHistoryDialog() {
        val history = QrHistoryManager.getHistory(this)
        if (history.isEmpty()) {
            Toast.makeText(this, "No QR scan history found", Toast.LENGTH_SHORT).show()
            return
        }

        val items = history.map { "[${it.category}] ${it.rawContent} (${it.riskLevel})" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Recent QR Scans")
            .setItems(items) { _, which ->
                val selected = history[which]
                val intent = Intent(this, QrReportDetailActivity::class.java).apply {
                    putExtra("qr_content", selected.rawContent)
                }
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
