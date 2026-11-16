package com.ophoto.engine.camera

import androidx.camera.core.ImageCapture
import kotlin.math.roundToInt

/**
 * 取景绑定结果。工程里多个机型（尤其 ColorOS / realme UI 上的高像素主摄）无法同时
 * 满足「预览 + 拍照 + 录像 + 高分辨率」组合，因此按能力逐级降级而不是直接黑屏。
 */
data class CameraBindingResult(
    val sessionLabel: String,
    val photoAvailable: Boolean,
    val videoAvailable: Boolean,
    val highResolutionPhoto: Boolean,
    /** 绑定时第一次尝试失败的简短原因（用于设置页诊断，不抛给用户）。 */
    val degradedReason: String? = null,
)

/** 预览 / 拍照 / 录像 组合的逐级降级顺序（从最高画质到最低依赖）。 */
internal enum class BindingStrategy(val label: String) {
    UHD_PHOTO_AND_VIDEO("UHD 预览 + 拍照 + 录像"),
    PHOTO_AND_VIDEO("预览 + 拍照 + 录像"),
    PHOTO_ONLY("预览 + 拍照（录像不可用）"),
    PREVIEW_ONLY("仅预览（拍照降级为取帧）"),
}

/**
 * 把 CameraX 绑定异常翻译成用户可读的中文提示。
 * 常见：Camera in use（ColorOS 相机被系统占用）、权限被系统拒绝、无兼容组合。
 */
fun describeCameraBindingFailure(error: Throwable): String {
    val text = buildString {
        append(error.javaClass.simpleName)
        append(": ")
        append(error.message.orEmpty())
    }.lowercase()
    return when {
        "in use" in text || "camera_in_use" in text || "already in use" in text ->
            "相机正被其他应用占用（如系统相机、微信视频），请关闭后重试"
        "permission" in text || "denied" in text ->
            "相机权限被系统或安全中心拦截，请到设置中重新授权"
        "no available camera" in text || "camera not available" in text ->
            "当前镜头不可用，请切换到其他镜头或重新插拔折叠形态"
        "unsupported" in text || "no supported surface combination" in text ->
            "当前设备不支持该预览组合，已尝试降级仍失败"
        "disconnected" in text || "error_camera_disconnected" in text ->
            "相机被系统回收（可能是省电策略），请重试"
        else -> "无法启动相机预览，请重试或重启应用"
    }
}

/** 预览画面元数据，用于 HUD 显示真实分辨率与画质等级。 */
data class PreviewFormatInfo(
    val width: Int = 0,
    val height: Int = 0,
    val sessionLabel: String = "",
    val highResolutionPhoto: Boolean = false,
) {
    val isValid: Boolean get() = width > 0 && height > 0
    val megapixels: Float get() = (width.toLong() * height.toLong()) / 1_000_000f
    val display: String
        get() = if (!isValid) "预览待定" else "${width}×${height} · ${"%.1f".format(megapixels)}MP"
}

/** 缩放比例限制：取景、拍照、录像共享同一 [Camera] 时以 zoomState 为准。 */
internal fun clampZoomRatio(requested: Float, min: Float?, max: Float?): Float {
    if (!requested.isFinite()) return 1f
    val lo = (min ?: 1f).coerceAtLeast(0.1f)
    val hi = (max ?: 10f).coerceAtLeast(lo)
    return requested.coerceIn(lo, hi)
}

/** 便于在 Compose 中显示：0.6x / 1.0x / 2.5x 统一格式。 */
fun formatZoomLabel(ratio: Float): String {
    val rounded = (ratio * 10f).roundToInt() / 10f
    return if (rounded >= 10f) "${rounded.toInt()}x" else "${"%.1f".format(rounded)}x"
}

/** 该错误是否值得自动重试（相机被占用 / 短暂断流）。 */
internal fun isTransientCameraError(code: Int): Boolean = when (code) {
    ImageCapture.ERROR_CAMERA_CLOSED,
    ImageCapture.ERROR_CAPTURE_FAILED,
    ImageCapture.ERROR_UNKNOWN,
    -> true
    else -> false
}

/** 该错误是否值得自动重试（相机被占用 / 短暂断流）。 */
internal fun isTransientBindingError(error: Throwable): Boolean {
    val text = (error.message.orEmpty() + error.javaClass.simpleName).lowercase()
    return "in use" in text || "disconnected" in text || "closed" in text || "busy" in text
}
