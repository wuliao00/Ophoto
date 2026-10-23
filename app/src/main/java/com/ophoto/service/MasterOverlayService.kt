package com.ophoto.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ophoto.OphotoApplication
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.data.local.FloatingHudStyle
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.filter.FilterPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Semi-transparent Master Parameter Floating HUD Service (`TYPE_APPLICATION_OVERLAY`):
 * Displays non-occluding OPPO / OnePlus / realme Master Mode tuning parameters & pro exposure hints
 * right above the native system camera viewfinder, supporting drag avoidance, prev/next switching,
 * 3 compact display modes, and one-tap system camera launch.
 */
class MasterOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootOverlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var presets: List<FilterPreset> = BuiltInPresets.ALL
    private var currentIndex: Int = 0
    private var currentStyle: FloatingHudStyle = FloatingHudStyle.STANDARD_CARD
    private var currentOpacity: Float = 0.86f

    private var titleView: TextView? = null
    private var paramsPrimaryView: TextView? = null
    private var paramsProView: TextView? = null
    private var bodyContainer: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val container = (application as? OphotoApplication)?.container
        if (container != null) {
            serviceScope.launch {
                combine(
                    container.filterRepository.allPresetsFlow,
                    container.preferences.settingsFlow,
                    container.preferences.overlayPresetIdFlow,
                ) { allPresets, settings, preferredId ->
                    Triple(allPresets, settings, preferredId)
                }.collect { (allPresets, settings, preferredId) ->
                    presets = allPresets.ifEmpty { BuiltInPresets.ALL }
                    if (preferredId != null) {
                        val idx = presets.indexOfFirst { it.id == preferredId }
                        if (idx >= 0) currentIndex = idx
                    }
                    currentStyle = settings.floatingHudStyle
                    currentOpacity = settings.floatingHudOpacity
                    refreshOverlayUi()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SELECT_PRESET -> {
                val targetId = intent.getStringExtra(EXTRA_PRESET_ID)
                if (targetId != null) {
                    val idx = presets.indexOfFirst { it.id == targetId }
                    if (idx >= 0) currentIndex = idx
                }
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (rootOverlayView == null) {
            attachOverlayWindow()
        }
        refreshOverlayUi()
        _isRunning.value = true
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlayWindow() {
        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(96)
        }
        layoutParams = params

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(222, 12, 14, 19))
                setStroke(dp(1), Color.argb(120, 229, 184, 105))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prevBtn = createActionChip("◀") {
            stepPreset(-1)
        }
        val titleTv = TextView(this).apply {
            setTextColor(Color.rgb(229, 184, 105))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
        }
        titleView = titleTv

        val nextBtn = createActionChip("▶") {
            stepPreset(1)
        }
        val collapseBtn = createActionChip("◐") {
            currentStyle = when (currentStyle) {
                FloatingHudStyle.STANDARD_CARD -> FloatingHudStyle.COMPACT_PILL
                FloatingHudStyle.COMPACT_PILL -> FloatingHudStyle.MINI_ORB
                FloatingHudStyle.MINI_ORB -> FloatingHudStyle.STANDARD_CARD
            }
            refreshOverlayUi()
        }
        val closeBtn = createActionChip("✕") {
            stopSelf()
        }

        headerRow.addView(prevBtn)
        headerRow.addView(titleTv)
        headerRow.addView(nextBtn)
        headerRow.addView(collapseBtn)
        headerRow.addView(closeBtn)

        val detailBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        bodyContainer = detailBox

        val primaryTv = TextView(this).apply {
            setTextColor(Color.rgb(241, 245, 249))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        paramsPrimaryView = primaryTv

        val proTv = TextView(this).apply {
            setTextColor(Color.rgb(148, 163, 184))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        paramsProView = proTv

        detailBox.addView(primaryTv)
        detailBox.addView(proTv)

        card.addView(headerRow)
        card.addView(detailBox)

        // Drag & horizontal swipe preset switching
        var startX = 0
        var startY = 0
        var touchDownX = 0f
        var touchDownY = 0f
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownX).toInt()
                    val dy = (event.rawY - touchDownY).toInt()
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(dp(36))
                    runCatching { wm.updateViewLayout(card, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val totalDx = event.rawX - touchDownX
                    val totalDy = event.rawY - touchDownY
                    if (abs(totalDx) < dp(6) && abs(totalDy) < dp(6) && currentStyle == FloatingHudStyle.MINI_ORB) {
                        currentStyle = FloatingHudStyle.STANDARD_CARD
                        refreshOverlayUi()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(card, params)
            rootOverlayView = card
        }
    }

    private fun stepPreset(delta: Int) {
        if (presets.isEmpty()) return
        currentIndex = ((currentIndex + delta) % presets.size + presets.size) % presets.size
        refreshOverlayUi()
    }

    private fun refreshOverlayUi() {
        val view = rootOverlayView ?: return
        val preset = presets.getOrNull(currentIndex) ?: BuiltInPresets.ALL.first()
        val m = preset.masterReference
        view.alpha = currentOpacity.coerceIn(0.45f, 1.0f)

        when (currentStyle) {
            FloatingHudStyle.MINI_ORB -> {
                titleView?.text = "◉ ${preset.name.take(4)}"
                bodyContainer?.visibility = View.GONE
            }
            FloatingHudStyle.COMPACT_PILL -> {
                titleView?.text = "${preset.name} (${currentIndex + 1}/${presets.size})"
                bodyContainer?.visibility = View.VISIBLE
                paramsPrimaryView?.text = "影调 ${fmtSigned(m.tone)} · 饱和 ${fmtSigned(m.saturation)} · 冷暖 ${fmtSigned(m.warmth)} · 青品 ${fmtSigned(m.cyanMagenta)}"
                paramsProView?.visibility = View.GONE
            }
            FloatingHudStyle.STANDARD_CARD -> {
                titleView?.text = "✦ ${preset.name} (${currentIndex + 1}/${presets.size})"
                bodyContainer?.visibility = View.VISIBLE
                paramsPrimaryView?.text = buildString {
                    appendLine("滤镜: ${m.baseFilterLabel}  柔光: ${m.softLightLabel}")
                    appendLine("影调 ${fmtSigned(m.tone)} | 饱和 ${fmtSigned(m.saturation)} | 冷暖 ${fmtSigned(m.warmth)}")
                    append("青品 ${fmtSigned(m.cyanMagenta)} | 锐度 ${fmtSigned(m.sharpness)} | 暗角 ${if (m.vignetteEnabled) "开" else "关"}")
                }
                paramsProView?.visibility = View.VISIBLE
                paramsProView?.text = "曝光 ${m.recommendedEv} · 快门 ${m.recommendedShutter} · ${m.recommendedKelvin}K"
            }
        }
    }

    private fun createActionChip(symbol: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            setTextColor(Color.rgb(226, 232, 240))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(7), dp(3), dp(7), dp(3))
            setOnClickListener { onClick() }
        }
    }

    private fun fmtSigned(v: Int): String = if (v > 0) "+$v" else v.toString()

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        _isRunning.value = false
        rootOverlayView?.let { v ->
            runCatching { windowManager?.removeView(v) }
        }
        rootOverlayView = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ophoto.overlay.START"
        const val ACTION_STOP = "com.ophoto.overlay.STOP"
        const val ACTION_SELECT_PRESET = "com.ophoto.overlay.SELECT_PRESET"
        const val EXTRA_PRESET_ID = "preset_id"

        private val _isRunning = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunning

        fun startWithPreset(context: Context, presetId: String, launchSystemCamera: Boolean = false) {
            val intent = Intent(context, MasterOverlayService::class.java).apply {
                action = ACTION_SELECT_PRESET
                putExtra(EXTRA_PRESET_ID, presetId)
            }
            context.startService(intent)
            if (launchSystemCamera) {
                runCatching {
                    context.startActivity(OppoDeviceInspector.buildSystemCameraLaunchIntent(context))
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MasterOverlayService::class.java))
        }
    }
}
