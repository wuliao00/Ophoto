package com.ophoto.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room table `filter` strictly aligned with Doc §12 database specification.
 */
@Entity(tableName = "filter")
data class FilterEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "author")
    val author: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "version")
    val version: String,

    @ColumnInfo(name = "preview_path")
    val previewPath: String?,

    @ColumnInfo(name = "lut_path")
    val lutPath: String?,

    @ColumnInfo(name = "config_json")
    val configJson: String,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,

    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "checksum")
    val checksum: String,
)
