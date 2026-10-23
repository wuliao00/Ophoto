package com.ophoto.data.repository

import com.ophoto.core.security.FileSandboxValidator
import com.ophoto.data.importer.FilterImporterImpl
import com.ophoto.data.local.dao.CaptureRecordDao
import com.ophoto.data.local.dao.EditHistoryDao
import com.ophoto.data.local.dao.FieldProgressDao
import com.ophoto.data.local.dao.FilterDao
import com.ophoto.data.local.entity.CaptureRecordEntity
import com.ophoto.data.local.entity.EditHistoryEntity
import com.ophoto.data.local.entity.FieldProgressEntity
import com.ophoto.data.local.entity.FilterEntity
import com.ophoto.engine.filter.FilterPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class FilterRepository(
    private val filterDao: FilterDao,
    private val importer: FilterImporterImpl,
) {
    val allPresetsFlow: Flow<List<FilterPreset>> = filterDao.observeAll().map { rows ->
        if (rows.isEmpty()) {
            BuiltInPresets.ALL
        } else {
            val mapped = rows.mapNotNull { entityToPreset(it) }
            val existingIds = mapped.map { it.id }.toSet()
            val missingBuiltIns = BuiltInPresets.ALL.filter { it.id !in existingIds }
            mapped + missingBuiltIns
        }
    }

    suspend fun ensureBuiltInsSeeded() {
        val existing = filterDao.getAllOnce().associateBy { it.id }
        val toInsert = BuiltInPresets.ALL.filter { it.id !in existing }.map { presetToEntity(it, "builtin") }
        if (toInsert.isNotEmpty()) {
            filterDao.upsertAll(toInsert)
        }
    }

    suspend fun savePreset(preset: FilterPreset, source: String = "custom") {
        filterDao.upsert(presetToEntity(preset, source))
    }

    suspend fun savePresets(presets: List<FilterPreset>, source: String = "imported") {
        filterDao.upsertAll(presets.map { presetToEntity(it, source) })
    }

    suspend fun toggleFavorite(preset: FilterPreset) {
        val nextFav = !preset.isFavorite
        val existing = filterDao.getById(preset.id)
        if (existing != null) {
            filterDao.setFavorite(preset.id, nextFav, System.currentTimeMillis())
        } else {
            filterDao.upsert(presetToEntity(preset.copy(isFavorite = nextFav), if (preset.isBuiltIn) "builtin" else "custom"))
        }
    }

    suspend fun deletePreset(preset: FilterPreset) {
        if (preset.isBuiltIn) {
            filterDao.setHidden(preset.id, true, System.currentTimeMillis())
        } else {
            preset.lutPath?.let { path ->
                runCatching { File(path).parentFile?.takeIf { it.name.startsWith("lut_") || it.name.startsWith("pack_") }?.deleteRecursively() }
            }
            filterDao.deleteById(preset.id)
        }
    }

    suspend fun clearAllUserImportedFilters() {
        filterDao.deleteAllUserImported()
    }

    private fun presetToEntity(preset: FilterPreset, source: String): FilterEntity {
        val configJson = FilterImporterImpl.exportPresetToJson(preset, asShareCode = false)
        val checksum = preset.lutPath
            ?.let { File(it) }
            ?.takeIf { it.isFile }
            ?.let { FileSandboxValidator.sha256OfFile(it) }
            ?: configJson.hashCode().toUInt().toString(16)

        return FilterEntity(
            id = preset.id,
            name = preset.name,
            type = preset.type.name,
            author = preset.author,
            createdAt = preset.createdAt,
            updatedAt = System.currentTimeMillis(),
            source = source,
            version = preset.version,
            previewPath = preset.previewPath,
            lutPath = preset.lutPath,
            configJson = configJson,
            isFavorite = preset.isFavorite,
            isHidden = preset.isHidden,
            sortOrder = preset.sortOrder,
            checksum = checksum,
        )
    }

    private fun entityToPreset(entity: FilterEntity): FilterPreset? {
        if (entity.isHidden) return null
        val builtInMatch = BuiltInPresets.ALL.firstOrNull { it.id == entity.id }
        if (builtInMatch != null && entity.source == "builtin") {
            return builtInMatch.copy(
                isFavorite = entity.isFavorite,
                sortOrder = entity.sortOrder,
            )
        }
        val parsed = runCatching {
            kotlinx.coroutines.runBlocking { importer.importFromTextPayload(entity.configJson) }
        }.getOrNull()
        val base = (parsed as? com.ophoto.data.importer.ImportResult.Success)?.filter
            ?: return builtInMatch
        return base.copy(
            id = entity.id,
            name = entity.name,
            lutPath = entity.lutPath ?: base.lutPath,
            previewPath = entity.previewPath ?: base.previewPath,
            isFavorite = entity.isFavorite,
            isBuiltIn = entity.source == "builtin",
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
        )
    }
}

class MediaRepository(
    private val captureRecordDao: CaptureRecordDao,
    private val editHistoryDao: EditHistoryDao,
    private val fieldProgressDao: FieldProgressDao,
) {
    val captureRecordsFlow: Flow<List<CaptureRecordEntity>> = captureRecordDao.observeAll()

    val completedKeysFlow: Flow<Set<String>> = fieldProgressDao.observeAll().map { list ->
        list.filter { it.completed }.map { it.itemKey }.toSet()
    }

    suspend fun recordCapture(record: CaptureRecordEntity): Long {
        return captureRecordDao.insert(record)
    }

    suspend fun recordEditStep(step: EditHistoryEntity): Long {
        return editHistoryDao.insert(step)
    }

    suspend fun toggleFieldItem(itemKey: String, category: String, currentlyCompleted: Boolean) {
        fieldProgressDao.upsert(
            FieldProgressEntity(
                itemKey = itemKey,
                category = category,
                completed = !currentlyCompleted,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
