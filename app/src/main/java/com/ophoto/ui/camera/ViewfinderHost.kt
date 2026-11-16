package com.ophoto.ui.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 取景会话在应用内共享，避免「切到滤镜库再回来」时 CameraX 重绑导致的二次黑屏。
 *
 * Compose 侧只需要记住 [PreviewView] 这一个 View，把它按需挂到 `AndroidView` 上；
 * 离开相机页时并不解绑相机（由相机页的 `DisposableEffect` 决定），
 * 这样从暗房/助手返回时画面会立即出现，而不是重新经历 1~3 秒的黑场。
 */
class ViewfinderHost {

    var previewView: PreviewView? = null
        private set

    /** 首次进入相机页时创建 PreviewView，之后跨页复用同一实例。 */
    fun ensurePreviewView(factory: () -> PreviewView): PreviewView =
        previewView ?: factory().also { previewView = it }

    /** true 表示最近一次绑定已完成，可以被重新挂载。 */
    var sessionReady: Boolean = false

    /** 最近一次真实帧到达时间（elapsedRealtime），用于黑场判定。 */
    @Volatile
    var lastFrameAtMillis: Long = 0L

    /** 是否正在录制视频（返回相机页时要恢复快门状态的提示）。 */
    var recordingVideo: Boolean = false

    private val _frames = MutableStateFlow(0L)
    val frames: StateFlow<Long> = _frames

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    fun onFrameArrived() {
        lastFrameAtMillis = android.os.SystemClock.elapsedRealtime()
        _frames.value = lastFrameAtMillis
    }

    fun setStreaming(value: Boolean) {
        _streaming.value = value
        if (value) onFrameArrived()
    }

    fun reset() {
        sessionReady = false
        lastFrameAtMillis = 0L
        _streaming.value = false
    }
}

/** 相机页内部使用的可观察预览状态（Compose State 包装，便于直接读值）。 */
class LivePreviewState {
    var streaming by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var brightnessWarning by mutableStateOf<String?>(null)
    var lastThumbnail by mutableStateOf<Bitmap?>(null)
    var bindingLabel by mutableStateOf("")
    var previewResolution by mutableStateOf("")
}
