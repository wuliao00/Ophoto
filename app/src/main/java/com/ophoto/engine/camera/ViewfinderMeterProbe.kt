package com.ophoto.engine.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.ophoto.core.log.SafeLogger
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 取景亮度遥测（Live Metering）。
 *
 * 这是「取景框很黑」问题的可观测手臂：
 * 当 ColorOS 的 HAL 输出的预览帧偏暗时，UI 需要能区分三种情况：
 * 1. 画面真的没到（黑场 / 会话未出帧）；
 * 2. 画面到了但传感器曝光不足（AE 目标偏低、被锁在 EV-）；
 * 3. 画面正常，只是 UI 覆盖层太暗压住了观感。
 *
 * 探针使用一条独立的 Camera2 会话，只开一路 32×32 的 YUV_420_888 ImageReader，
 * 取 Y 平面平均亮度后立刻关闭会话，随后由 CameraX 重新绑定正常预览。
 * 全程不写相册、不占用额外分辨率，也不会改变用户设置。
 */
data class MeteringSample(
    val meanLuma: Float,
    val meanLumaSrgb: Int,
    val iso: Int,
    val exposureTimeNs: Long,
    val evIndex: Int,
    val aeStateLabel: String,
    val capturedAtMillis: Long,
) {
    /** 0..100 的观感亮度分。低于 12 判定为「疑似黑场」。 */
    val brightnessPercent: Int get() = ((meanLumaSrgb / 255f) * 100f).roundToInt().coerceIn(0, 100)
    val isBlackFrame: Boolean get() = meanLumaSrgb <= 14
    val isUnderexposed: Boolean get() = meanLumaSrgb in 15..45
    val isOverexposed: Boolean get() = meanLumaSrgb >= 235
}

/**
 * 单次取景测光探针。返回 null 表示设备不支持或探针失败（此时不应把画面判为黑场）。
 */
object ViewfinderMeterProbe {

    private const val TAG = "ViewfinderMeterProbe"
    private const val PROBE_EDGE = 32
    private const val PROBE_TIMEOUT_MS = 1600L
    private const val PROBE_IMAGE_TIMEOUT_MS = 1400L

    @SuppressLint("MissingPermission")
    fun probe(context: Context, cameraId: String, timeoutMillis: Long = PROBE_TIMEOUT_MS): MeteringSample? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        val handlerThread = HandlerThread("ophoto-meter").apply { start() }
        val handler = Handler(handlerThread.looper)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        var sample: MeteringSample? = null

        try {
            val chars = runCatching { manager.getCameraCharacteristics(cameraId) }.getOrNull() ?: return null
            val localReader = ImageReader.newInstance(PROBE_EDGE, PROBE_EDGE, ImageFormat.YUV_420_888, 2)
            reader = localReader
            val latch = java.util.concurrent.CountDownLatch(1)
            val imageSlot = java.util.concurrent.atomic.AtomicReference<Image?>(null)

            localReader.setOnImageAvailableListener({ r ->
                val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                imageSlot.getAndSet(img)?.close()
                latch.countDown()
            }, handler)

            val opened = java.util.concurrent.CountDownLatch(1)
            var openError: Int = 0
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    opened.countDown()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    openError = CameraDevice.StateCallback.ERROR_CAMERA_DISCONNECTED
                    runCatching { camera.close() }
                    opened.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    openError = error
                    runCatching { camera.close() }
                    opened.countDown()
                }
            }, handler)

            if (!opened.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                SafeLogger.warn(TAG, "openCamera timeout for lens $cameraId")
                return null
            }
            val cam = device ?: return null
            if (openError != 0) return null

            val ready = java.util.concurrent.CountDownLatch(1)
            cam.createCaptureSession(
                listOf(localReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        session = configured
                        ready.countDown()
                    }

                    override fun onConfigureFailed(failed: CameraCaptureSession) {
                        ready.countDown()
                    }
                },
                handler,
            )
            if (!ready.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                SafeLogger.warn(TAG, "createCaptureSession timeout for lens $cameraId")
                return null
            }
            val active = session ?: return null

            // 预览型请求：AE 打开、自动白平衡、自动对焦
            val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(localReader.surface)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    if (chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                            ?.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
                    ) {
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    } else {
                        CaptureRequest.CONTROL_AF_MODE_AUTO
                    },
                )
            }

            // 取景亮度是否被「曝光补偿」或「手动 ISO」压暗，是黑场的常见根因，先暴露给 UI。
            var aeStateLabel = "未知"
            var iso = 0
            var exposureTime = 0L
            var evIndex = 0

            active.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    s: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: iso
                    exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: exposureTime
                    evIndex = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: evIndex
                    aeStateLabel = when (result.get(CaptureResult.CONTROL_AE_STATE)) {
                        CaptureResult.CONTROL_AE_STATE_CONVERGED -> "已收敛"
                        CaptureResult.CONTROL_AE_STATE_SEARCHING -> "搜索中"
                        CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> "需要补光"
                        CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "预闪"
                        CaptureResult.CONTROL_AE_STATE_LOCKED -> "已锁定"
                        else -> "未知"
                    }
                }
            }, handler)

            if (!latch.await(PROBE_IMAGE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                SafeLogger.warn(TAG, "no probe frame for lens $cameraId")
                return null
            }

            val image = imageSlot.getAndSet(null) ?: return null
            runCatching { image.close() }
            val meanLuma = runCatching { averageY(image) }.getOrNull() ?: return null
            // Y 平面为 BT.601 limited range（16–235），换算为 0–255 观感亮度。
            val srgb = ((meanLuma - 16f) / 219f * 255f).coerceIn(0f, 255f).roundToInt()

            sample = MeteringSample(
                meanLuma = meanLuma,
                meanLumaSrgb = srgb,
                iso = iso,
                exposureTimeNs = exposureTime,
                evIndex = evIndex,
                aeStateLabel = aeStateLabel,
                capturedAtMillis = SystemClock.elapsedRealtime(),
            )
            SafeLogger.info(TAG, "probe lens=$cameraId luma=${srgb} iso=$iso ns=$exposureTime evIdx=$evIndex state=$aeStateLabel")
            return sample
        } catch (e: Exception) {
            SafeLogger.warn(TAG, "probe failed for lens $cameraId: ${e.javaClass.simpleName}")
            return null
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader?.close() }
            handlerThread.quitSafely()
        }
    }

    /** 取 Y 平面均值（YUV_420_888 的 Y 平面可能带行/像素跨度）。 */
    private fun averageY(image: Image): Float {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = min(image.width, PROBE_EDGE)
        val height = min(image.height, PROBE_EDGE)
        var sum = 0L
        var count = 0
        val row = ByteArray(rowStride)
        buffer.rewind()
        for (y in 0 until height) {
            val remaining = buffer.remaining()
            if (remaining <= 0) break
            val read = min(rowStride, remaining)
            buffer.get(row, 0, read)
            var x = 0
            while (x < width) {
                val idx = x * pixelStride
                if (idx < read) {
                    sum += row[idx].toInt() and 0xFF
                    count++
                }
                x++
            }
        }
        if (count == 0) return 0f
        return sum.toFloat() / count
    }

    /** 判断当前设备是否值得跑探针（有相机且非模拟器明显缺失时）。 */
    fun canProbe(context: Context): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching { manager.cameraIdList.isNotEmpty() }.getOrDefault(false)
    }
}
