package com.ophoto.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room table `edit_history` strictly aligned with Doc §12 database specification.
 */
@Entity(tableName = "edit_history")
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "media_id")
    val mediaId: String,

    @ColumnInfo(name = "filter_id")
    val filterId: String?,

    @ColumnInfo(name = "params_json")
    val paramsJson: String,

    @ColumnInfo(name = "crop_json")
    val cropJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "version")
    val version: Int,
)

@Entity(tableName = "field_progress")
data class FieldProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_key")
    val itemKey: String,

    @ColumnInfo(name = "category")
    val category: String, // "checklist" | "shot_bookmark" | "challenge_done"

    @ColumnInfo(name = "completed")
    val completed: Boolean,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
