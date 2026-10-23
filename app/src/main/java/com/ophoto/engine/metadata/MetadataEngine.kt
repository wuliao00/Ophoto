package com.ophoto.engine.metadata

import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class CaptureExifMetadata(
    val make: String = Build.MANUFACTURER.orEmpty().ifBlank { "Ophoto" },
    val model: String = Build.MODEL.orEmpty().ifBlank { "Android Camera" },
    val iso: Int = 100,
    val exposureTimeSec: Double = 1.0 / 125.0,
    val aperture: Float = 1.8f,
    val focalLengthMm: Float = 24f,
    val focalLength35mm: Int = 24,
    val kelvin: Int = 5500,
    val filterName: String? = null,
    val filterStrength: Float = 1f,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    fun formattedShutter(): String = MetadataEngine.formatShutterSpeed(exposureTimeSec)

    fun summaryLine(): String {
        return "${focalLength35mm}mm  f/${"%.1f".format(aperture)}  ${formattedShutter()}  ISO $iso"
    }
}

object MetadataEngine {

    private val EXIF_DATE_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun formatShutterSpeed(seconds: Double): String {
        if (seconds <= 0.0) return "Auto"
        return if (seconds >= 0.95) {
            if (seconds >= 10.0 || kotlin.math.abs(seconds - seconds.roundToInt()) < 0.05) {
                "${seconds.roundToInt()}s"
            } else {
                "${"%.1f".format(seconds)}s"
            }
        } else {
            val denom = (1.0 / seconds).roundToInt().coerceAtLeast(1)
            "1/${denom}s"
        }
    }

    fun applyExifToFile(
        file: File,
        metadata: CaptureExifMetadata?,
        stripLocation: Boolean,
    ) {
        runCatching {
            val exif = ExifInterface(file)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Ophoto 1.0")
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, LocalDateTime.now().format(EXIF_DATE_FMT))

            if (metadata != null) {
                exif.setAttribute(ExifInterface.TAG_MAKE, metadata.make)
                exif.setAttribute(ExifInterface.TAG_MODEL, metadata.model)
                exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, metadata.iso.toString())
                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "%.6f".format(metadata.exposureTimeSec))
                exif.setAttribute(ExifInterface.TAG_F_NUMBER, "%.2f".format(metadata.aperture))
                val focalNum = (metadata.focalLengthMm * 100).roundToInt()
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$focalNum/100")
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, metadata.focalLength35mm.toString())
                metadata.filterName?.let { preset ->
                    val pct = (metadata.filterStrength * 100).roundToInt()
                    exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Ophoto Preset: $preset ($pct%)")
                }

                if (!stripLocation && metadata.latitude != null && metadata.longitude != null) {
                    exif.setLatLong(metadata.latitude, metadata.longitude)
                }
            }

            if (stripLocation) {
                scrubGpsAttributes(exif)
            }
            exif.saveAttributes()
        }
    }

    fun scrubGpsAttributes(exif: ExifInterface) {
        val gpsTags = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
        )
        for (tag in gpsTags) {
            exif.setAttribute(tag, null)
        }
    }

    fun readMetadataFromStream(stream: InputStream): CaptureExifMetadata {
        return runCatching {
            val exif = ExifInterface(stream)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().ifBlank { "Camera" }
            val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().ifBlank { "Lens" }
            val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 100)
            val expSec = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 1.0 / 125.0)
            val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 1.8).toFloat()
            val eq35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 24)
            val focalReal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 5.6).toFloat()
            CaptureExifMetadata(
                make = make,
                model = model,
                iso = iso,
                exposureTimeSec = expSec,
                aperture = fNum,
                focalLengthMm = focalReal,
                focalLength35mm = if (eq35 > 0) eq35 else 24,
            )
        }.getOrDefault(CaptureExifMetadata())
    }
}
