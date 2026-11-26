package dev.voxpilot.assistant.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import java.io.File
import java.io.FileOutputStream

class AppIconCustomizerActivity : AppCompatActivity() {

    private var selectedTargetPackage = "com.whatsapp"

    private val customIconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveAndCropBitmap(uri, selectedTargetPackage)
        }
    }

    private fun saveAndCropBitmap(uri: android.net.Uri, targetPkg: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val width = originalBitmap.width
                val height = originalBitmap.height
                val dimension = Math.min(width, height)
                val x = (width - dimension) / 2
                val y = (height - dimension) / 2

                val squareBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, x, y, dimension, dimension)
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(squareBitmap, 256, 256, true)

                val shapeGroup = findViewById<android.widget.RadioGroup>(R.id.iconShapeGroup)
                val isCircle = shapeGroup?.checkedRadioButtonId == R.id.shapeCircle

                val finalBitmap = if (isCircle) {
                    val output = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(output)
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = android.graphics.BitmapShader(scaledBitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                    }
                    val rect = android.graphics.RectF(0f, 0f, 256f, 256f)
                    canvas.drawOval(rect, paint)
                    output
                } else {
                    scaledBitmap
                }

                val file = File(filesDir, "app_icon_$targetPkg.png")
                FileOutputStream(file).use { output ->
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                Toast.makeText(this, "Custom icon set for $targetPkg (${if (isCircle) "Circle" else "Square"})! ✂️🖼️", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to crop image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_icon_customizer)

        findViewById<Button>(R.id.applyMidnightBtn).setOnClickListener {
            applyTheme("Midnight Dark Aesthetic", "midnight")
        }

        findViewById<Button>(R.id.applyNeonPinkBtn).setOnClickListener {
            applyTheme("Neon Pink Glow", "neon")
        }

        findViewById<Button>(R.id.applyGlassBtn).setOnClickListener {
            applyTheme("Glassmorphism Frosted", "glass")
        }

        findViewById<Button>(R.id.applyGoldFoilBtn).setOnClickListener {
            applyTheme("Gold Foil Luxury", "gold")
        }

        findViewById<Button>(R.id.applyHackerBtn).setOnClickListener {
            applyTheme("Hacker Matrix", "hacker")
        }

        findViewById<Button>(R.id.setCustomPhotoIconBtn).setOnClickListener {
            showTargetAppDialog()
        }

        findViewById<Button>(R.id.resetIconsBtn).setOnClickListener {
            val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("app_icon_theme").apply()
            try {
                filesDir.listFiles()?.filter { it.name.startsWith("app_icon_") }?.forEach { it.delete() }
            } catch (_: Exception) {}
            Toast.makeText(this, "All app icons reset to default! 🔄", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showTargetAppDialog() {
        val input = EditText(this).apply {
            hint = "Enter package name (e.g. com.whatsapp)"
            setText("com.whatsapp")
            setPadding(32, 32, 32, 32)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x88888888.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Custom App Icon")
            .setMessage("Enter package name of the app (e.g. com.whatsapp, com.instagram.android):")
            .setView(input)
            .setPositiveButton("Choose Photo") { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotBlank()) {
                    selectedTargetPackage = pkg
                    customIconPickerLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Package name cannot be blank", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTheme(themeName: String, themeKey: String) {
        val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_icon_theme", themeKey).apply()
        Toast.makeText(this, "'$themeName' applied to App Drawer icons! 🎨", Toast.LENGTH_SHORT).show()
        finish()
    }
}
