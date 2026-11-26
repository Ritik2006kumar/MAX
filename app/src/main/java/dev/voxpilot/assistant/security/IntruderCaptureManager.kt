package dev.voxpilot.assistant.security

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object IntruderCaptureManager {
    private const val TAG = "IntruderCapture"

    fun captureIntruder(context: Context) {
        val appContext = context.applicationContext
        try {
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = manager?.cameraIdList?.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager?.cameraIdList?.firstOrNull() ?: return

            val reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
            val handler = Handler(Looper.getMainLooper())

            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage()
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    ByteArray(buffer.remaining()).apply { buffer.get(this) }
                } catch (_: Exception) {
                    null
                } finally {
                    image?.close()
                }

                if (bytes != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dir = File(appContext.filesDir, "intruder_captures").apply { mkdirs() }
                            val file = File(dir, "intruder_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            Log.d(TAG, "Intruder selfie captured and saved at ${file.absolutePath}")
                        } catch (_: Exception) {}
                    }
                }
            }, handler)

            @Suppress("MissingPermission")
            manager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surface = reader.surface
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }

                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                            camera.close()
                                        }
                                    }, handler)
                                } catch (_: Exception) {
                                    camera.close()
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                camera.close()
                            }
                        }, handler)
                    } catch (_: Exception) {
                        camera.close()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Intruder capture failed: ${e.message}")
        }
    }
}
