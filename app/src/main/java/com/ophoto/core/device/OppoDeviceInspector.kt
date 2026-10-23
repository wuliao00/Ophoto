package com.ophoto.core.device

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.SizeF
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class OemEcosystem(val displayLabel: String) {
    OPPO_COLOROS("OPPO · ColorOS"),
    ONEPLUS_COLOROS("OnePlus · ColorOS / OxygenOS"),
    REALME_UI("realme · realme UI"),
    ANDROID_GENERIC("Android 通用设备"),
}

data class PhysicalLensSpec(
    val cameraId: String,
    val isFrontFacing: Boolean,
    val focalLengthMm: Float,
    val equivalent35mm: Int,
    val maxAperture: Float?,
    val roleLabel: String,
    val supportsRaw: Boolean,
    val isoRange: IntRange,
    val exposureNsRange: LongRange,
    val minFocusDistanceDiopters: Float,
)

data class DeviceHardwareProfile(
    val ecosystem: OemEcosystem,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val displayTitle: String,
    val romVersionHint: String,
    val mainSensorFormat: String,
    val maxRefreshRateHz: Int,
    val lenses: List<PhysicalLensSpec>,
    val supportsProManual: Boolean,
    val supportsRawCapture: Boolean,
)

object OppoDeviceInspector {

    private data class KnownModelSpec(
        val prefixOrKeyword: String,
        val title: String,
        val mainSensor: String,
    )

    private val KNOWN_MODELS = listOf(
        KnownModelSpec("PKB110", "OPPO Find X8 Pro", "1/1.4\""),
        KnownModelSpec("PKC110", "OPPO Find X8", "1/1.56\""),
        KnownModelSpec("PHY110", "OPPO Find X7 Ultra", "1.0\""),
        KnownModelSpec("PHZ110", "OPPO Find X7", "1/1.4\""),
        KnownModelSpec("CPH2619", "OPPO Find X Series Pro", "1/1.4\""),
        KnownModelSpec("CPH2621", "OPPO Find X Series Ultra", "1.0\""),
        KnownModelSpec("CPH2526", "OPPO Find X Ultra", "1.0\""),
        KnownModelSpec("PJZ110", "OnePlus 13", "1/1.4\""),
        KnownModelSpec("PJD110", "OnePlus 12", "1/1.4\""),
        KnownModelSpec("CPH2639", "OnePlus 13", "1/1.4\""),
        KnownModelSpec("PKR110", "OnePlus Ace 5 Pro", "1/1.56\""),
        KnownModelSpec("RMX5010", "realme GT7 Pro", "1/1.56\""),
        KnownModelSpec("RMX3888", "realme GT5 Pro", "1/1.4\""),
    )

    private val SYSTEM_CAMERA_PACKAGES = listOf(
        "com.oplus.camera",
        "com.oppo.camera",
        "com.oneplus.camera",
        "com.android.camera",
    )

    fun detectEcosystem(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
    ): OemEcosystem {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return when {
            "oneplus" in m || "oneplus" in b || "1+" in b -> OemEcosystem.ONEPLUS_COLOROS
            "realme" in m || "realme" in b -> OemEcosystem.REALME_UI
            "oppo" in m || "oppo" in b || "oplus" in m -> OemEcosystem.OPPO_COLOROS
            else -> OemEcosystem.ANDROID_GENERIC
        }
    }

    fun resolveKnownModel(model: String, brand: String): Pair<String, String> {
        val upper = model.uppercase()
        val matched = KNOWN_MODELS.firstOrNull { upper.contains(it.prefixOrKeyword.uppercase()) }
        if (matched != null) return matched.title to matched.mainSensor
        val cleanBrand = brand.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val display = if (model.startsWith(cleanBrand, ignoreCase = true)) model else "$cleanBrand $model".trim()
        return display.ifBlank { "Android Camera Device" } to "1/1.56\""
    }

    fun inspect(context: Context): DeviceHardwareProfile {
        val ecosystem = detectEcosystem()
        val (displayTitle, sensorHint) = resolveKnownModel(Build.MODEL.orEmpty(), Build.BRAND.orEmpty())
        val romVersion = readColorOsProperty()
        val refreshRateHz = queryMaxRefreshRateHz(context)
        val lenses = enumerateAvailableLenses(context)

        return DeviceHardwareProfile(
            ecosystem = ecosystem,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            displayTitle = displayTitle,
            romVersionHint = romVersion,
            mainSensorFormat = sensorHint,
            maxRefreshRateHz = refreshRateHz,
            lenses = lenses,
            supportsProManual = lenses.any { it.minFocusDistanceDiopters > 0f || it.isoRange.last > 800 },
            supportsRawCapture = lenses.any { it.supportsRaw },
        )
    }

    fun enumerateAvailableLenses(context: Context): List<PhysicalLensSpec> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()
        return runCatching {
            manager.cameraIdList.mapNotNull { id ->
                runCatching { parseLensSpec(id, manager.getCameraCharacteristics(id)) }.getOrNull()
            }.sortedWith(
                compareBy<PhysicalLensSpec> { it.isFrontFacing }.thenBy { it.equivalent35mm }
            )
        }.getOrDefault(emptyList())
    }

    private fun parseLensSpec(id: String, chars: CameraCharacteristics): PhysicalLensSpec {
        val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
        val isFront = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focalMm = focalLengths?.firstOrNull() ?: 4.8f
        val sensorSize: SizeF? = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val eq35 = computeEquivalent35mm(focalMm, sensorSize)
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?.let { it.lower..it.upper } ?: (100..6400)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?.let { it.lower..it.upper } ?: (125_000L..30_000_000_000L)
        val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        val role = when {
            isFront -> "前置人像 (${eq35}mm)"
            eq35 <= 18 -> "超广角 (${eq35}mm)"
            eq35 in 19..34 -> "主摄广角 (${eq35}mm)"
            eq35 in 35..65 -> "人文中焦 (${eq35}mm)"
            else -> "潜望长焦 (${eq35}mm)"
        }

        return PhysicalLensSpec(
            cameraId = id,
            isFrontFacing = isFront,
            focalLengthMm = focalMm,
            equivalent35mm = eq35,
            maxAperture = apertures?.minOrNull(),
            roleLabel = role,
            supportsRaw = supportsRaw,
            isoRange = isoRange,
            exposureNsRange = expRange,
            minFocusDistanceDiopters = minFocus,
        )
    }

    fun computeEquivalent35mm(focalMm: Float, sensorSize: SizeF?): Int {
        if (sensorSize == null || sensorSize.width <= 0.5f || sensorSize.height <= 0.5f) {
            return (focalMm * 5.0f).roundToInt().coerceIn(12, 240)
        }
        val sensorDiag = hypot(sensorSize.width.toDouble(), sensorSize.height.toDouble())
        val fullFrameDiag = hypot(36.0, 24.0) // 43.2666 mm
        val cropFactor = fullFrameDiag / sensorDiag
        return (focalMm * cropFactor).roundToInt().coerceIn(10, 400)
    }

    @Suppress("DEPRECATION")
    private fun queryMaxRefreshRateHz(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 60
        return runCatching {
            val modes = wm.defaultDisplay.supportedModes
            (modes.maxOfOrNull { it.refreshRate } ?: wm.defaultDisplay.refreshRate)
                .roundToInt()
                .coerceIn(60, 165)
        }.getOrDefault(60)
    }

    private fun readColorOsProperty(): String {
        val keys = listOf(
            "ro.build.version.oplusrom",
            "ro.build.version.opporom",
            "ro.oxygen.version",
        )
        for (key in keys) {
            val value = runCatching {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                (method.invoke(null, key, "") as? String)?.trim().orEmpty()
            }.getOrDefault("")
            if (value.isNotEmpty()) return value
        }
        return "Android ${Build.VERSION.RELEASE}"
    }

    fun buildSystemCameraLaunchIntent(context: Context): Intent {
        val pm = context.packageManager
        for (pkg in SYSTEM_CAMERA_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch
            }
        }
        return Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildAppDetailsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
