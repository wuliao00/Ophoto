package com.ophoto.data.importer

import android.net.Uri
import com.ophoto.engine.filter.FilterPreset
import java.io.File

data class FilterPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val minAppVersion: String,
    val lutFileName: String?,
    val previewFileName: String?,
)

enum class ImportErrorCode(val userMessage: String) {
    FILE_TOO_LARGE("滤镜文件超出 10MB 安全限制"),
    UNSUPPORTED_FORMAT("不支持的格式（支持 .cube / .json / .filterpack / .zip）"),
    INVALID_MANIFEST("manifest.json 或预设 JSON 格式无效"),
    INVALID_LUT(".cube 3D LUT 数据损坏或尺寸超过 64"),
    MISSING_PREVIEW("缺少必要的预览资源"),
    SECURITY_RISK("安全拦截：包含路径穿越或可执行脚本"),
    VERSION_NOT_COMPATIBLE("滤镜包要求的最低 App 版本高于当前版本"),
}

sealed class ImportResult {
    data class Success(
        val filter: FilterPreset,
        val importedBatch: List<FilterPreset> = listOf(filter),
    ) : ImportResult()

    data class Error(
        val code: ImportErrorCode,
        val detail: String = code.userMessage,
    ) : ImportResult()
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val code: ImportErrorCode, val reason: String) : ValidationResult()
}

/**
 * Filter Import Pipeline contract strictly per Doc §7.
 */
interface IFilterImporter {
    suspend fun importFromUri(uri: Uri, displayNameHint: String? = null): ImportResult
    suspend fun importFromZip(file: File): ImportResult
    suspend fun importFromCube(file: File): ImportResult
    suspend fun importFromJson(file: File): ImportResult
    suspend fun importFromTextPayload(payload: String): ImportResult
    suspend fun validate(pack: FilterPackManifest): ValidationResult
}
