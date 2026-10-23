package com.ophoto.core.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ophoto.engine.metadata.CaptureExifMetadata
import com.ophoto.engine.metadata.MetadataEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ExportImageFormat(
    val extension: String,
    val mimeType: String,
    val compressFormat: Bitmap.CompressFormat,
) {
    JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG),
    WEBP(
        "webp",
        "image/webp",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        },
    ),
}

object MediaStoreSaver {

    fun formatFileName(
        template: String,
        modeTag: String = "PHOTO",
        filterName: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val time = now.format(DateTimeFormatter.ofPattern("HHmmss"))
        val ms = (now.nano / 1_000_000).toString().padStart(3, '0')
        val cleanFilter = (filterName ?: "RAW")
            .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]"), "")
            .take(12)
            .ifEmpty { "ORIG" }
        val resolved = template
            .replace("{yyyyMMdd}", date)
            .replace("{HHmmss}", time)
            .replace("{SSS}", ms)
            .replace("{MODE}", modeTag.uppercase())
            .replace("{FILTER}", cleanFilter)
            .trim()
        return resolved.ifEmpty { "OPHOTO_${date}_${time}_$ms" }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileBaseName: String,
        format: ExportImageFormat = ExportImageFormat.JPEG,
        quality: Int = 95,
        subFolder: String = "Ophoto",
        exifMetadata: CaptureExifMetadata? = null,
        stripLocation: Boolean = true,
    ): Uri? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "save_${System.nanoTime()}.${format.extension}")
        try {
            tempFile.outputStream().use { out ->
                bitmap.compress(format.compressFormat, quality.coerceIn(50, 100), out)
            }
            if (format == ExportImageFormat.JPEG) {
                MetadataEngine.applyExifToFile(
                    file = tempFile,
                    metadata = exifMetadata,
                    stripLocation = stripLocation,
                )
            }

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileBaseName.${format.extension}")
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$subFolder",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { ins -> ins.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }
}
