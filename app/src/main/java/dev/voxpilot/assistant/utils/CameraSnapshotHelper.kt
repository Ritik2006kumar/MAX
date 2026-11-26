package dev.voxpilot.assistant.utils

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object CameraSnapshotHelper {

    suspend fun capturePhoto(context: Context): File? = suspendCancellableCoroutine { continuation ->
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = manager?.cameraIdList?.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager?.cameraIdList?.firstOrNull()

            if (cameraId == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
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
                            val file = File(context.cacheDir, "vision_snapshot_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            continuation.resume(file)
                        } catch (_: Exception) {
                            continuation.resume(null)
                        }
                    }
                } else {
                    continuation.resume(null)
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
                                    continuation.resume(null)
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                camera.close()
                                continuation.resume(null)
                            }
                        }, handler)
                    } catch (_: Exception) {
                        camera.close()
                        continuation.resume(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    continuation.resume(null)
                }
            }, handler)
        } catch (_: Exception) {
            continuation.resume(null)
        }
    }
}
