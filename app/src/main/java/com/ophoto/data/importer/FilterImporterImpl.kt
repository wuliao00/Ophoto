package com.ophoto.data.importer

import android.content.Context
import android.net.Uri
import com.ophoto.core.security.FileSandboxValidator
import com.ophoto.core.security.SandboxResult
import com.ophoto.core.security.SandboxViolation
import com.ophoto.engine.filter.CubeLutParser
import com.ophoto.engine.filter.FilterCategory
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.filter.FilterType
import com.ophoto.engine.filter.InvalidCubeLutException
import com.ophoto.engine.filter.MasterCameraReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * Complete Filter Importer & Codec (Doc §4, §7, §14):
 * Supports:
 * 1. Standard `.cube` 3D LUT files (up to 64^3 RGB)
 * 2. `.filterpack` / `.zip` multi-file filter packages (`manifest.json` + `lut.cube` + optional preview)
 * 3. Single filter `.json` recipes (Doc §4 & §14 format)
 * 4. Community Master Mode preset packs (`presets: [...]` with `sections` / `items` key-value pairs)
 * 5. `OPHOTO1:` Base64 compact clipboard / QR share codes
 */
class FilterImporterImpl(
    private val storageRoot: File,
    private val contentResolverProvider: (() -> android.content.ContentResolver)? = null,
    private val currentAppVersion: String = "1.0.0",
) : IFilterImporter {

    constructor(context: Context) : this(
        storageRoot = File(context.filesDir, "filters").apply { mkdirs() },
        contentResolverProvider = { context.contentResolver },
    )

    override suspend fun importFromUri(uri: Uri, displayNameHint: String?): ImportResult = withContext(Dispatchers.IO) {
        val resolver = contentResolverProvider?.invoke()
            ?: return@withContext ImportResult.Error(ImportErrorCode.UNSUPPORTED_FORMAT, "无法访问系统文件流")

        val fileName = (displayNameHint ?: uri.lastPathSegment ?: "imported.cube").lowercase()
        val ext = fileName.substringAfterLast('.', "")
        if (ext !in setOf("cube", "json", "zip", "filterpack")) {
            return@withContext ImportResult.Error(ImportErrorCode.UNSUPPORTED_FORMAT)
        }

        val tempFile = File(storageRoot, "staging_${UUID.randomUUID()}.$ext")
        val copyCheck = resolver.openInputStream(uri)?.use { stream ->
            FileSandboxValidator.copyLimitedStream(stream, tempFile)
        } ?: return@withContext ImportResult.Error(ImportErrorCode.UNSUPPORTED_FORMAT, "无法读取文件流")

        if (copyCheck is SandboxResult.Rejected) {
            tempFile.delete()
            return@withContext mapSandboxFailure(copyCheck)
        }

        val result = when (ext) {
            "cube" -> importFromCube(tempFile)
            "json" -> importFromJson(tempFile)
            "zip", "filterpack" -> importFromZip(tempFile)
            else -> ImportResult.Error(ImportErrorCode.UNSUPPORTED_FORMAT)
        }
        tempFile.delete()
        result
    }

    override suspend fun importFromCube(file: File): ImportResult = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_LUT, "LUT 文件不存在")
        }
        if (file.length() > FileSandboxValidator.MAX_SINGLE_FILE_BYTES) {
            return@withContext ImportResult.Error(ImportErrorCode.FILE_TOO_LARGE)
        }

        val parsedLut = try {
            file.inputStream().use { CubeLutParser.parse(it, file.nameWithoutExtension) }
        } catch (ex: InvalidCubeLutException) {
            val code = if (ex.isSecurityViolation) ImportErrorCode.SECURITY_RISK else ImportErrorCode.INVALID_LUT
            return@withContext ImportResult.Error(code, ex.message ?: code.userMessage)
        } catch (ex: Exception) {
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_LUT, ex.message ?: "LUT 解析失败")
        }

        val id = "lut_${UUID.randomUUID().toString().take(8)}"
        val destDir = File(storageRoot, id).apply { mkdirs() }
        val savedLut = File(destDir, "lut.cube")
        file.copyTo(savedLut, overwrite = true)

        val preset = FilterPreset(
            id = id,
            name = parsedLut.title.ifBlank { file.nameWithoutExtension },
            subtitle = "3D LUT (${parsedLut.size}³)",
            category = FilterCategory.IMPORTED,
            type = FilterType.CUBE_LUT,
            author = "本地导入",
            version = "1.0.0",
            description = "从 ${file.name} 导入的 ${parsedLut.size}×${parsedLut.size}×${parsedLut.size} 3D LUT 色彩查找表",
            accentHex = "#38BDF8",
            lutPath = savedLut.absolutePath,
            defaultStrength = 0.90f,
            isBuiltIn = false,
        )
        ImportResult.Success(preset)
    }

    override suspend fun importFromZip(file: File): ImportResult = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "滤镜包文件不存在")
        }
        if (file.length() > FileSandboxValidator.MAX_SINGLE_FILE_BYTES) {
            return@withContext ImportResult.Error(ImportErrorCode.FILE_TOO_LARGE)
        }

        val packDir = File(storageRoot, "pack_${UUID.randomUUID().toString().take(8)}")
        val extracted = file.inputStream().use { stream ->
            FileSandboxValidator.extractArchiveSafely(stream, packDir)
        }
        val files = when (extracted) {
            is SandboxResult.Valid -> extracted.value
            is SandboxResult.Rejected -> return@withContext mapSandboxFailure(extracted)
        }

        val manifestFile = files.firstOrNull { it.name.equals("manifest.json", ignoreCase = true) }
            ?: files.firstOrNull { it.name.equals("config.json", ignoreCase = true) }
        if (manifestFile == null) {
            // Allow bare zip containing a single .cube file as convenience
            val singleCube = files.firstOrNull { it.extension.equals("cube", ignoreCase = true) }
            if (singleCube != null) {
                val res = importFromCube(singleCube)
                packDir.deleteRecursively()
                return@withContext res
            }
            packDir.deleteRecursively()
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "滤镜包内缺少 manifest.json")
        }

        val jsonObj = runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
        if (jsonObj == null) {
            packDir.deleteRecursively()
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST)
        }

        val manifest = parseManifestHeader(jsonObj)
        when (val validation = validate(manifest)) {
            is ValidationResult.Invalid -> {
                packDir.deleteRecursively()
                return@withContext ImportResult.Error(validation.code, validation.reason)
            }
            ValidationResult.Valid -> Unit
        }

        var verifiedLutPath: String? = null
        if (!manifest.lutFileName.isNullOrBlank()) {
            val lutCandidate = files.firstOrNull { it.name.equals(File(manifest.lutFileName).name, ignoreCase = true) }
            if (lutCandidate == null || !lutCandidate.isFile) {
                packDir.deleteRecursively()
                return@withContext ImportResult.Error(ImportErrorCode.INVALID_LUT, "manifest 指定的 LUT 文件缺失")
            }
            val lutOk = try {
                lutCandidate.inputStream().use { CubeLutParser.parse(it, manifest.name) }
                true
            } catch (ex: InvalidCubeLutException) {
                packDir.deleteRecursively()
                val err = if (ex.isSecurityViolation) ImportErrorCode.SECURITY_RISK else ImportErrorCode.INVALID_LUT
                return@withContext ImportResult.Error(err, ex.message ?: err.userMessage)
            }
            if (lutOk) verifiedLutPath = lutCandidate.absolutePath
        }

        val previewPath = manifest.previewFileName?.let { prevName ->
            files.firstOrNull { it.name.equals(File(prevName).name, ignoreCase = true) }?.absolutePath
        }

        val paramsObj = jsonObj.optJSONObject("params") ?: jsonObj
        val preset = parseSingleJsonPreset(
            obj = jsonObj,
            paramsObj = paramsObj,
            lutPath = verifiedLutPath,
            previewPath = previewPath,
        )
        ImportResult.Success(preset)
    }

    override suspend fun importFromJson(file: File): ImportResult = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST)
        }
        if (file.length() > FileSandboxValidator.MAX_SINGLE_FILE_BYTES) {
            return@withContext ImportResult.Error(ImportErrorCode.FILE_TOO_LARGE)
        }
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST)
        importFromTextPayload(text)
    }

    override suspend fun importFromTextPayload(payload: String): ImportResult = withContext(Dispatchers.IO) {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "导入内容为空")
        }

        val rawJson = if (trimmed.startsWith("OPHOTO1:", ignoreCase = true)) {
            val encoded = trimmed.substringAfter(':').trim()
            runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }
                .getOrElse {
                    runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
                } ?: return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "配方口令解码失败")
        } else {
            trimmed
        }

        val root = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "不是合法的 JSON 滤镜配置")

        // Support community collection JSON with `presets: [ ... ]`
        val presetsArray: JSONArray? = root.optJSONArray("presets")
        if (presetsArray != null && presetsArray.length() > 0) {
            val importedList = mutableListOf<FilterPreset>()
            for (i in 0 until presetsArray.length().coerceAtMost(100)) {
                val item = presetsArray.optJSONObject(i) ?: continue
                importedList += parseCommunityOrNativePreset(item, root.optString("author", "社区预设"))
            }
            val first = importedList.firstOrNull()
                ?: return@withContext ImportResult.Error(ImportErrorCode.INVALID_MANIFEST, "预设列表为空")
            return@withContext ImportResult.Success(first, importedList)
        }

        val single = parseCommunityOrNativePreset(root, "自定义配方")
        ImportResult.Success(single)
    }

    override suspend fun validate(pack: FilterPackManifest): ValidationResult {
        if (pack.name.isBlank()) {
            return ValidationResult.Invalid(ImportErrorCode.INVALID_MANIFEST, "滤镜名称不能为空")
        }
        if (compareSemVer(pack.minAppVersion, currentAppVersion) > 0) {
            return ValidationResult.Invalid(
                ImportErrorCode.VERSION_NOT_COMPATIBLE,
                "需要 Ophoto ${pack.minAppVersion} 及以上版本（当前 $currentAppVersion）",
            )
        }
        return ValidationResult.Valid
    }

    private fun parseCommunityOrNativePreset(obj: JSONObject, fallbackAuthor: String): FilterPreset {
        val sections = obj.optJSONArray("sections")
        if (sections != null && sections.length() > 0) {
            return parseSectionBasedMasterPreset(obj, sections, fallbackAuthor)
        }
        val paramsObj = obj.optJSONObject("params") ?: obj
        return parseSingleJsonPreset(obj, paramsObj, lutPath = null, previewPath = null)
    }

    private fun parseSectionBasedMasterPreset(
        obj: JSONObject,
        sections: JSONArray,
        fallbackAuthor: String,
    ): FilterPreset {
        var filterLabel = "自然 85%"
        var softLightLabel = "无"
        var tone = 0
        var sat = 0
        var warm = 0
        var cyanMag = 0
        var sharp = 0
        var vignetteOn = false
        var recIso = "Auto"
        var recShutter = "1/125s"
        var recEv = "0.0 EV"
        var recKelvin = 5500

        for (s in 0 until sections.length()) {
            val sec = sections.optJSONObject(s) ?: continue
            val items = sec.optJSONArray("items") ?: continue
            for (k in 0 until items.length()) {
                val kv = items.optJSONObject(k) ?: continue
                val label = kv.optString("label").lowercase()
                val value = kv.optString("value").trim()
                val numVal = value.replace("+", "").toFloatOrNull()?.toInt()
                when {
                    "filter" in label || "滤镜" in label -> filterLabel = value
                    "soft" in label || "柔光" in label -> softLightLabel = value
                    "tone" in label || "影调" in label -> tone = numVal ?: 0
                    "saturation" in label || "饱和" in label -> sat = numVal ?: 0
                    "warm" in label || "冷暖" in label -> warm = numVal ?: 0
                    "cyan" in label || "magenta" in label || "青品" in label || "色调" in label -> cyanMag = numVal ?: 0
                    "sharp" in label || "锐" in label -> sharp = numVal ?: 0
                    "vignette" in label || "暗角" in label -> {
                        vignetteOn = value == "开" || value.equals("on", true) || (numVal ?: 0) > 0
                    }
                    "iso" in label -> recIso = value
                    "shutter" in label || "快门" in label -> recShutter = value
                    "ev" in label || "曝光" in label -> recEv = value
                    "temp" in label || "kelvin" in label || "色温" in label -> {
                        recKelvin = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(2500, 10000) ?: 5500
                    }
                }
            }
        }

        val descObj = obj.optJSONObject("description")
        val advice = descObj?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: obj.optString("description", "导入的大师模式调色预设")

        val name = obj.optString("name", "导入大师预设").ifBlank { "导入大师预设" }
        val author = obj.optString("author", fallbackAuthor).ifBlank { fallbackAuthor }

        return FilterPreset(
            id = "master_${UUID.randomUUID().toString().take(8)}",
            name = name,
            subtitle = "大师调色 · $filterLabel",
            category = FilterCategory.IMPORTED,
            type = FilterType.BASIC_PARAMS,
            author = author,
            description = advice,
            accentHex = "#F59E0B",
            params = FilterParams(
                contrast = (tone * 0.6f).toInt().coerceIn(-60, 60),
                saturation = sat.coerceIn(-80, 80),
                temperature = warm.coerceIn(-80, 80),
                tint = cyanMag.coerceIn(-80, 80),
                shadows = (-tone * 0.4f).toInt().coerceIn(-50, 50),
                sharpness = sharp.coerceIn(0, 100),
                vignette = if (vignetteOn) 24 else 0,
                softGlow = if (softLightLabel != "无" && softLightLabel.isNotBlank()) 28 else 0,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = filterLabel,
                softLightLabel = softLightLabel,
                tone = tone,
                saturation = sat,
                warmth = warm,
                cyanMagenta = cyanMag,
                sharpness = sharp,
                vignetteEnabled = vignetteOn,
                recommendedIso = recIso,
                recommendedShutter = recShutter,
                recommendedEv = recEv,
                recommendedKelvin = recKelvin,
                sceneAdvice = advice,
            ),
            defaultStrength = 0.85f,
            isBuiltIn = false,
        )
    }

    private fun parseSingleJsonPreset(
        obj: JSONObject,
        paramsObj: JSONObject,
        lutPath: String?,
        previewPath: String?,
    ): FilterPreset {
        val rawId = obj.optString("id").takeIf { it.isNotBlank() }
            ?: "custom_${UUID.randomUUID().toString().take(8)}"
        val name = obj.optString("name").ifBlank { "未命名滤镜" }
        val strengthPct = obj.optDouble("strength", 85.0)
        val normStrength = (if (strengthPct > 1.0) strengthPct / 100.0 else strengthPct)
            .toFloat()
            .coerceIn(0.1f, 1.0f)

        val hiTint = paramsObj.optString("highlight_tint", "#FFFFFF").ifBlank { "#FFFFFF" }
        val shTint = paramsObj.optString("shadow_tint", "#000000").ifBlank { "#000000" }
        val hasTintCast = !hiTint.equals("#FFFFFF", true) || !shTint.equals("#000000", true)

        val params = FilterParams(
            exposureEv = paramsObj.optDouble("exposure_ev", 0.0).toFloat().coerceIn(-2f, 2f),
            contrast = paramsObj.optInt("contrast", 0).coerceIn(-100, 100),
            saturation = paramsObj.optInt("saturation", 0).coerceIn(-100, 100),
            vibrance = paramsObj.optInt("vibrance", 0).coerceIn(-100, 100),
            temperature = paramsObj.optInt("temperature", 0).coerceIn(-100, 100),
            tint = paramsObj.optInt("tint", 0).coerceIn(-100, 100),
            highlights = paramsObj.optInt("highlights", 0).coerceIn(-100, 100),
            shadows = paramsObj.optInt("shadows", 0).coerceIn(-100, 100),
            whites = paramsObj.optInt("whites", 0).coerceIn(-100, 100),
            blacks = paramsObj.optInt("blacks", 0).coerceIn(-100, 100),
            highlightTintHex = hiTint,
            shadowTintHex = shTint,
            colorCastStrength = paramsObj.optInt("cast_strength", if (hasTintCast) 42 else 0).coerceIn(0, 100),
            grainStrength = paramsObj.optInt("grain_strength", paramsObj.optInt("grain", 0)).coerceIn(0, 100),
            vignette = paramsObj.optInt("vignette", 0).coerceIn(0, 100),
            fade = paramsObj.optInt("fade", 0).coerceIn(0, 100),
            softGlow = paramsObj.optInt("soft_glow", 0).coerceIn(0, 100),
            lightLeak = paramsObj.optInt("light_leak", 0).coerceIn(0, 100),
            sharpness = paramsObj.optInt("sharpness", 0).coerceIn(0, 100),
            monochrome = paramsObj.optBoolean("monochrome", false),
        )

        val masterObj = obj.optJSONObject("master_ref")
        val masterRef = if (masterObj != null) {
            MasterCameraReference(
                baseFilterLabel = masterObj.optString("base_filter", "大师自定义"),
                softLightLabel = masterObj.optString("soft_light", if (params.softGlow > 15) "柔雾" else "无"),
                tone = masterObj.optInt("tone", params.contrast / 2),
                saturation = masterObj.optInt("saturation", params.saturation / 2),
                warmth = masterObj.optInt("warmth", params.temperature / 2),
                cyanMagenta = masterObj.optInt("cyan_magenta", params.tint / 2),
                sharpness = masterObj.optInt("sharpness", params.sharpness / 2),
                vignetteEnabled = masterObj.optBoolean("vignette", params.vignette >= 12),
                recommendedIso = masterObj.optString("iso", "Auto (≤800)"),
                recommendedShutter = masterObj.optString("shutter", "1/125s"),
                recommendedEv = masterObj.optString("ev", "-0.3 EV"),
                recommendedKelvin = masterObj.optInt("kelvin", 5500),
                sceneAdvice = masterObj.optString("advice", obj.optString("description", "")),
            )
        } else {
            MasterCameraReference(
                baseFilterLabel = if (params.monochrome) "黑白 100%" else "大师原生 85%",
                softLightLabel = if (params.softGlow >= 15) "朦胧" else "无",
                tone = (params.contrast * 0.5f).toInt(),
                saturation = (params.saturation * 0.5f).toInt(),
                warmth = (params.temperature * 0.5f).toInt(),
                cyanMagenta = (params.tint * 0.5f).toInt(),
                sharpness = (params.sharpness * 0.5f).toInt(),
                vignetteEnabled = params.vignette >= 12,
                sceneAdvice = obj.optString("description", "支持实时预览与大师悬浮窗参考"),
            )
        }

        return FilterPreset(
            id = rawId,
            name = name,
            subtitle = obj.optString("subtitle", if (lutPath != null) "LUT + 参数复合" else "参数调色配方"),
            category = obj.optString("category")
                .let { raw -> FilterCategory.entries.firstOrNull { it.name.equals(raw, true) } }
                ?: FilterCategory.IMPORTED,
            type = if (lutPath != null) FilterType.LUT_PLUS_PARAMS else FilterType.BASIC_PARAMS,
            author = obj.optString("author", "创作者").ifBlank { "创作者" },
            version = obj.optString("version", "1.0.0"),
            description = obj.optString("description", ""),
            accentHex = obj.optString("accent_hex", "#E5B869"),
            lutPath = lutPath,
            previewPath = previewPath,
            params = params,
            masterReference = masterRef,
            defaultStrength = normStrength,
            isBuiltIn = false,
        )
    }

    private fun parseManifestHeader(jsonObj: JSONObject): FilterPackManifest {
        val compat = jsonObj.optJSONObject("compatible")
        return FilterPackManifest(
            id = jsonObj.optString("id", "pack_${UUID.randomUUID().toString().take(6)}"),
            name = jsonObj.optString("name", ""),
            version = jsonObj.optString("version", "1.0.0"),
            author = jsonObj.optString("author", "Unknown"),
            description = jsonObj.optString("description", ""),
            minAppVersion = compat?.optString("min_app_version", "1.0.0") ?: "1.0.0",
            lutFileName = jsonObj.optString("lut").takeIf { it.isNotBlank() },
            previewFileName = jsonObj.optString("preview").takeIf { it.isNotBlank() },
        )
    }

    private fun mapSandboxFailure(failure: SandboxResult.Rejected): ImportResult.Error {
        val code = when (failure.reason) {
            SandboxViolation.FILE_TOO_LARGE, SandboxViolation.ARCHIVE_BOMB_DETECTED -> ImportErrorCode.FILE_TOO_LARGE
            SandboxViolation.PATH_TRAVERSAL,
            SandboxViolation.FORBIDDEN_EXECUTABLE_OR_SCRIPT,
            SandboxViolation.TOO_MANY_ARCHIVE_ENTRIES -> ImportErrorCode.SECURITY_RISK
            SandboxViolation.IO_FAILURE -> ImportErrorCode.INVALID_MANIFEST
        }
        return ImportResult.Error(code, failure.detail)
    }

    companion object {
        fun exportPresetToJson(preset: FilterPreset, asShareCode: Boolean = false): String {
            val p = preset.params
            val m = preset.masterReference
            val root = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("subtitle", preset.subtitle)
                put("category", preset.category.name)
                put("version", preset.version)
                put("author", preset.author)
                put("description", preset.description)
                put("accent_hex", preset.accentHex)
                put("strength", (preset.defaultStrength * 100).toInt())
                put(
                    "params",
                    JSONObject().apply {
                        put("exposure_ev", p.exposureEv.toDouble())
                        put("contrast", p.contrast)
                        put("saturation", p.saturation)
                        put("vibrance", p.vibrance)
                        put("temperature", p.temperature)
                        put("tint", p.tint)
                        put("highlights", p.highlights)
                        put("shadows", p.shadows)
                        put("whites", p.whites)
                        put("blacks", p.blacks)
                        put("highlight_tint", p.highlightTintHex)
                        put("shadow_tint", p.shadowTintHex)
                        put("cast_strength", p.colorCastStrength)
                        put("grain_strength", p.grainStrength)
                        put("vignette", p.vignette)
                        put("fade", p.fade)
                        put("soft_glow", p.softGlow)
                        put("light_leak", p.lightLeak)
                        put("sharpness", p.sharpness)
                        put("monochrome", p.monochrome)
                    },
                )
                put(
                    "master_ref",
                    JSONObject().apply {
                        put("base_filter", m.baseFilterLabel)
                        put("soft_light", m.softLightLabel)
                        put("tone", m.tone)
                        put("saturation", m.saturation)
                        put("warmth", m.warmth)
                        put("cyan_magenta", m.cyanMagenta)
                        put("sharpness", m.sharpness)
                        put("vignette", m.vignetteEnabled)
                        put("iso", m.recommendedIso)
                        put("shutter", m.recommendedShutter)
                        put("ev", m.recommendedEv)
                        put("kelvin", m.recommendedKelvin)
                        put("advice", m.sceneAdvice)
                    },
                )
            }
            val pretty = root.toString(2)
            return if (asShareCode) {
                "OPHOTO1:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(root.toString().toByteArray(Charsets.UTF_8))
            } else {
                pretty
            }
        }

        fun compareSemVer(a: String, b: String): Int {
            val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val va = pa.getOrElse(i) { 0 }
                val vb = pb.getOrElse(i) { 0 }
                if (va != vb) return va.compareTo(vb)
            }
            return 0
        }
    }
}
