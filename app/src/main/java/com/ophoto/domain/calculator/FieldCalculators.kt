package com.ophoto.domain.calculator

import com.ophoto.engine.metadata.MetadataEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class CityLocationPreset(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val utcOffsetHours: Int,
) {
    companion object {
        val PRESETS = listOf(
            CityLocationPreset("hk", "香港 · 维港", 22.3193, 114.1694, 8),
            CityLocationPreset("sz", "深圳 · 深圳湾", 22.5431, 114.0579, 8),
            CityLocationPreset("gz", "广州 · 珠江新城", 23.1291, 113.2644, 8),
            CityLocationPreset("sh", "上海 · 外滩", 31.2304, 121.4737, 8),
            CityLocationPreset("bj", "北京 · 故宫角楼", 39.9042, 116.4074, 8),
            CityLocationPreset("hz", "杭州 · 西湖", 30.2741, 120.1551, 8),
            CityLocationPreset("cd", "成都 · 锦江", 30.5728, 104.0668, 8),
            CityLocationPreset("dl", "大理 · 洱海", 25.6065, 100.2676, 8),
            CityLocationPreset("tp", "台北 · 象山", 25.0330, 121.5654, 8),
            CityLocationPreset("tyo", "东京 · 涩谷", 35.6762, 139.6503, 9),
        )
    }
}

enum class SensorFormatSpec(
    val label: String,
    val widthMm: Double,
    val heightMm: Double,
    val cocMm: Double,
) {
    PHONE_1_INCH("手机 1.0 英寸大底", 13.2, 8.8, 0.011),
    PHONE_1_14("手机 1/1.4\" 主摄/潜望", 9.4, 7.05, 0.0072),
    PHONE_1_156("手机 1/1.56\" 标准主摄", 8.2, 6.15, 0.0062),
    MICRO_43("M43 画幅", 17.3, 13.0, 0.015),
    APS_C("APS-C 半画幅", 23.5, 15.6, 0.019),
    FULL_FRAME("35mm 全画幅", 36.0, 24.0, 0.029),
    MEDIUM_4433("44×33 中画幅", 43.8, 32.9, 0.036);

    val diagonalMm: Double get() = hypot(widthMm, heightMm)
    val cropFactor: Double get() = hypot(36.0, 24.0) / diagonalMm
}

enum class NdFilterSpec(val label: String, val stops: Int) {
    ND4("ND4 · 2档", 2),
    ND8("ND8 · 3档 (人像大光圈)", 3),
    ND16("ND16 · 4档 (车流拖影)", 4),
    ND32("ND32 · 5档", 5),
    ND64("ND64 · 6档 (丝绢溪流)", 6),
    ND256("ND256 · 8档 (日落海雾)", 8),
    ND1000("ND1000 · 10档 (正午流云/空城)", 10),
    ND4000("ND4000 · 12档", 12),
    ND32000("ND32000 · 15档 (极暗长曝)", 15),
}

data class SunSchedule(
    val date: LocalDate,
    val morningBlueStart: String,
    val morningGoldenStart: String,
    val sunrise: String,
    val morningGoldenEnd: String,
    val solarNoon: String,
    val maxElevationDeg: Double,
    val eveningGoldenStart: String,
    val sunset: String,
    val eveningGoldenEnd: String,
    val eveningBlueEnd: String,
    val lightingAdvice: String,
)

data class MoonReport(
    val ageDays: Double,
    val illuminationPercent: Int,
    val phaseName: String,
    val astroRating: String,
    val nightShootAdvice: String,
)

data class DofResult(
    val hyperfocalMeters: Double,
    val nearLimitMeters: Double,
    val farLimitMeters: Double, // Double.POSITIVE_INFINITY when focused past hyperfocal
    val totalDofMeters: Double,
    val summaryTip: String,
)

data class EquivalentLensResult(
    val equivalentFocalMm: Double,
    val cropFactor: Double,
    val horizontalFovDeg: Double,
    val verticalFovDeg: Double,
    val diagonalFovDeg: Double,
    val lensCategory: String,
)

data class NdExposureResult(
    val baseShutterText: String,
    val targetShutterSeconds: Double,
    val targetShutterText: String,
    val requiresTripodBulbTimer: Boolean,
    val stopMultiplier: Int,
)

data class StorageTimelapseResult(
    val ev100: Double,
    val estimatedPhotosCapacity: Int,
    val clipDurationSeconds: Double,
    val requiredIntervalFrames: Int,
    val estimatedTotalMegabytes: Double,
)

object FieldCalculators {

    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI

    /**
     * NOAA spherical solar astronomy solver computing Sunrise, Sunset, Golden Hour (-4°..+6°),
     * and Blue Hour (-6°..-4°) for any geographic location and calendar date.
     */
    fun calculateSunSchedule(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zoneOffset: ZoneOffset = ZoneOffset.ofHours(8),
    ): SunSchedule {
        val latClamped = latitude.coerceIn(-65.5, 65.5)
        val dayOfYear = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1 + 0.5)

        // Equation of time (minutes)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // Solar declination (radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val tzMinutes = zoneOffset.totalSeconds / 60.0
        val solarNoonMinutes = 720.0 - 4.0 * longitude - eqTime + tzMinutes

        fun minutesForAltitude(altitudeDeg: Double, isMorning: Boolean): Double {
            val zenithRad = (90.0 - altitudeDeg) * DEG_TO_RAD
            val latRad = latClamped * DEG_TO_RAD
            val cosHa = (
                (cos(zenithRad) / (cos(latRad) * cos(decl))) -
                    (sin(latRad) / cos(latRad)) * (sin(decl) / cos(decl))
                ).coerceIn(-1.0, 1.0)
            val haDeg = acos(cosHa) * RAD_TO_DEG
            return if (isMorning) {
                solarNoonMinutes - haDeg * 4.0
            } else {
                solarNoonMinutes + haDeg * 4.0
            }
        }

        val maxElev = asin(
            (sin(latClamped * DEG_TO_RAD) * sin(decl) + cos(latClamped * DEG_TO_RAD) * cos(decl))
                .coerceIn(-1.0, 1.0),
        ) * RAD_TO_DEG

        val mBlueStart = formatDayMinutes(minutesForAltitude(-6.0, true))
        val mGoldStart = formatDayMinutes(minutesForAltitude(-4.0, true))
        val sunrise = formatDayMinutes(minutesForAltitude(-0.833, true))
        val mGoldEnd = formatDayMinutes(minutesForAltitude(6.0, true))
        val noon = formatDayMinutes(solarNoonMinutes)
        val eGoldStart = formatDayMinutes(minutesForAltitude(6.0, false))
        val sunset = formatDayMinutes(minutesForAltitude(-0.833, false))
        val eGoldEnd = formatDayMinutes(minutesForAltitude(-4.0, false))
        val eBlueEnd = formatDayMinutes(minutesForAltitude(-6.0, false))

        return SunSchedule(
            date = date,
            morningBlueStart = mBlueStart,
            morningGoldenStart = mGoldStart,
            sunrise = sunrise,
            morningGoldenEnd = mGoldEnd,
            solarNoon = noon,
            maxElevationDeg = (maxElev * 10.0).roundToInt() / 10.0,
            eveningGoldenStart = eGoldStart,
            sunset = sunset,
            eveningGoldenEnd = eGoldEnd,
            eveningBlueEnd = eBlueEnd,
            lightingAdvice = "黄金时刻($eGoldStart–$eGoldEnd)适合侧逆光发丝发光人像；日落后蓝调时刻($eGoldEnd–$eBlueEnd)天空呈深邃宝石蓝，是城市建筑与霓虹夜景的黄金 20 分钟。",
        )
    }

    /**
     * Lunar phase & night astro photography index based on J2000 synodic lunation cycle.
     */
    fun calculateMoonReport(date: LocalDate): MoonReport {
        val knownNewMoon = LocalDateTime.of(2000, 1, 6, 18, 14)
        val targetNoon = LocalDateTime.of(date, LocalTime.NOON)
        val minutesDiff = ChronoUnit.MINUTES.between(knownNewMoon, targetNoon).toDouble()
        val synodicDays = 29.530588853
        val daysDiff = minutesDiff / 1440.0
        val age = ((daysDiff % synodicDays) + synodicDays) % synodicDays
        val phaseAngle = 2.0 * PI * (age / synodicDays)
        val illum = ((1.0 - cos(phaseAngle)) * 0.5 * 100.0).roundToInt().coerceIn(0, 100)

        val (name, rating, advice) = when {
            age < 1.85 || age > 27.7 -> Triple(
                "新月 · 朔夜",
                "★★★★★ 极佳暗夜",
                "月光干扰近乎为零，最适合拍摄银河银心、星轨与暗夜风光。",
            )
            age < 5.5 -> Triple(
                "蛾眉月",
                "★★★★☆ 适合星空与暮光月",
                "日落后西方低空可见弯月剪影，入夜后月落即可衔接银河拍摄。",
            )
            age < 9.2 -> Triple(
                "上弦月",
                "★★★☆☆ 半月地景补光",
                "上半夜月光可为山川建筑提供柔和侧光，后半夜月落后可拍深空星空。",
            )
            age < 13.0 -> Triple(
                "盈凸月",
                "★★☆☆☆ 明亮月色",
                "地面景物层次丰富，适合城市月悬地标长焦合影，不建议拍暗弱星云。",
            )
            age < 16.6 -> Triple(
                "满月 · 望夜",
                "★☆☆☆☆ 长焦悬月之夜",
                "建议使用潜望长焦拍摄建筑悬月同框（点测光对准月面，快门约 1/250s ISO 100）。",
            )
            age < 20.3 -> Triple(
                "亏凸月",
                "★★☆☆☆ 前半夜暗空",
                "月亮较晚升起，入夜初段仍有约 2 小时无月暗夜窗口。",
            )
            age < 24.0 -> Triple(
                "下弦月",
                "★★★☆☆ 上半夜星空窗口",
                "上半夜无月光干扰适合拍星，凌晨可拍晨雾残月风光。",
            )
            else -> Triple(
                "残月",
                "★★★★☆ 极佳星空窗口",
                "整夜暗空纯净，日出前东方低空可拍晨曦金星伴残月。",
            )
        }

        return MoonReport(
            ageDays = (age * 10.0).roundToInt() / 10.0,
            illuminationPercent = illum,
            phaseName = name,
            astroRating = rating,
            nightShootAdvice = advice,
        )
    }

    /**
     * Optical Depth-of-Field & Hyperfocal Distance solver.
     */
    fun calculateDof(
        focalLengthMm: Double,
        apertureF: Double,
        subjectDistanceMeters: Double,
        sensor: SensorFormatSpec,
    ): DofResult {
        val f = focalLengthMm.coerceAtLeast(1.0)
        val n = apertureF.coerceAtLeast(0.7)
        val sMm = (subjectDistanceMeters.coerceAtLeast(0.1)) * 1000.0
        val c = sensor.cocMm

        val hyperfocalMm = (f * f) / (n * c) + f
        val nearMm = (hyperfocalMm * sMm) / (hyperfocalMm + (sMm - f).coerceAtLeast(1.0))
        val farMm = if (sMm < hyperfocalMm) {
            (hyperfocalMm * sMm) / (hyperfocalMm - (sMm - f))
        } else {
            Double.POSITIVE_INFINITY
        }
        val totalM = if (farMm.isInfinite()) Double.POSITIVE_INFINITY else (farMm - nearMm) / 1000.0
        val nearM = nearMm / 1000.0
        val farM = if (farMm.isInfinite()) Double.POSITIVE_INFINITY else farMm / 1000.0
        val hyperM = hyperfocalMm / 1000.0

        val tip = when {
            farM.isInfinite() -> "已进入超焦距区间：从 ${"%.2f".format(nearM)}m 直至无穷远全部清晰合焦，极适合风光大片。"
            totalM < 0.25 -> "景深极浅（仅 ${"%.1f".format(totalM * 100)}cm）：建议开启人眼对焦或红色峰值对焦确保眼部锐利。"
            totalM < 1.5 -> "奶油虚化区间（总景深 ${"%.2f".format(totalM)}m）：主体与背景自然分离，适合半身与特写人像。"
            else -> "中大景深（${"%.2f".format(totalM)}m）：人物与环境交代均衡，适合街头人文与环境人像。"
        }

        return DofResult(
            hyperfocalMeters = hyperM,
            nearLimitMeters = nearM,
            farLimitMeters = farM,
            totalDofMeters = totalM,
            summaryTip = tip,
        )
    }

    /**
     * Multi-format Equivalent Focal Length & Field-of-View converter.
     */
    fun calculateEquivalentLens(
        physicalFocalMm: Double,
        sensor: SensorFormatSpec,
    ): EquivalentLensResult {
        val f = physicalFocalMm.coerceAtLeast(0.8)
        val eq = f * sensor.cropFactor
        val hFov = 2.0 * atan(sensor.widthMm / (2.0 * f)) * RAD_TO_DEG
        val vFov = 2.0 * atan(sensor.heightMm / (2.0 * f)) * RAD_TO_DEG
        val dFov = 2.0 * atan(sensor.diagonalMm / (2.0 * f)) * RAD_TO_DEG

        val category = when {
            eq < 18.0 -> "超广角视野 · 适合建筑张力、星空与大风光前景"
            eq < 28.0 -> "黄金广角主摄 · 适合街头环境、旅行纪实与日常叙事"
            eq < 42.0 -> "经典人文焦段 (35mm系) · 兼顾人物互动与现场氛围"
            eq < 65.0 -> "标准自然视角 (50mm系) · 透视平实，适合静物、美食与半身人像"
            eq < 105.0 -> "黄金人像中长焦 (70–85mm系) · 压缩杂乱背景，五官比例最讨喜"
            else -> "潜望超长焦 · 强烈空间压缩感，适合建筑局部、远山与悬月"
        }

        return EquivalentLensResult(
            equivalentFocalMm = (eq * 10.0).roundToInt() / 10.0,
            cropFactor = (sensor.cropFactor * 100.0).roundToInt() / 100.0,
            horizontalFovDeg = (hFov * 10.0).roundToInt() / 10.0,
            verticalFovDeg = (vFov * 10.0).roundToInt() / 10.0,
            diagonalFovDeg = (dFov * 10.0).roundToInt() / 10.0,
            lensCategory = category,
        )
    }

    /**
     * Neutral Density (ND) Filter Long-Exposure shutter calculator.
     */
    fun calculateNdExposure(
        baseShutterSeconds: Double,
        ndFilter: NdFilterSpec,
    ): NdExposureResult {
        val factor = 1 shl ndFilter.stops
        val targetSec = baseShutterSeconds.coerceAtLeast(1.0 / 16000.0) * factor
        return NdExposureResult(
            baseShutterText = MetadataEngine.formatShutterSpeed(baseShutterSeconds),
            targetShutterSeconds = targetSec,
            targetShutterText = formatLongDuration(targetSec),
            requiresTripodBulbTimer = targetSec >= 1.0,
            stopMultiplier = factor,
        )
    }

    /**
     * Exposure EV100 & Card Capacity + Timelapse planner.
     */
    fun calculateStorageAndTimelapse(
        cardSizeGb: Int,
        averagePhotoSizeMb: Double,
        aperture: Double,
        shutterSeconds: Double,
        iso: Int,
        shootDurationMinutes: Int,
        intervalSeconds: Double,
        outputFps: Int,
    ): StorageTimelapseResult {
        val ev100 = (
            ln((aperture * aperture) / shutterSeconds.coerceAtLeast(1e-5)) / ln(2.0) -
                ln(iso.coerceAtLeast(25) / 100.0) / ln(2.0)
            )
        val usableMb = cardSizeGb.coerceAtLeast(1) * 1024.0 * 0.93
        val perFileMb = averagePhotoSizeMb.coerceAtLeast(1.0)
        val capacity = (usableMb / perFileMb).toInt()

        val totalFrames = ((shootDurationMinutes.coerceAtLeast(1) * 60.0) / intervalSeconds.coerceAtLeast(0.5))
            .roundToInt()
            .coerceAtLeast(1)
        val clipSec = totalFrames.toDouble() / outputFps.coerceAtLeast(1)
        val seqMb = totalFrames * perFileMb

        return StorageTimelapseResult(
            ev100 = (ev100 * 10.0).roundToInt() / 10.0,
            estimatedPhotosCapacity = capacity,
            clipDurationSeconds = (clipSec * 10.0).roundToInt() / 10.0,
            requiredIntervalFrames = totalFrames,
            estimatedTotalMegabytes = (seqMb * 10.0).roundToInt() / 10.0,
        )
    }

    private fun formatDayMinutes(minutes: Double): String {
        val wrapped = (((minutes.roundToInt()) % 1440) + 1440) % 1440
        val h = wrapped / 60
        val m = wrapped % 60
        return "%02d:%02d".format(h, m)
    }

    private fun formatLongDuration(seconds: Double): String {
        return when {
            seconds < 1.0 -> MetadataEngine.formatShutterSpeed(seconds)
            seconds < 60.0 -> if (seconds >= 10.0) "${seconds.roundToInt()}s" else "${"%.1f".format(seconds)}s"
            else -> {
                val mins = (seconds / 60.0).toInt()
                val remSec = (seconds % 60.0).roundToInt()
                "${mins}m ${remSec}s"
            }
        }
    }
}
