package com.ophoto.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room table `capture_record` strictly aligned with Doc §12 database specification.
 */
@Entity(tableName = "capture_record")
data class CaptureRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "raw_path")
    val rawPath: String?,

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,

    @ColumnInfo(name = "capture_mode")
    val captureMode: String,

    @ColumnInfo(name = "camera_id")
    val cameraId: String,

    @ColumnInfo(name = "iso")
    val iso: Int,

    @ColumnInfo(name = "shutter")
    val shutter: String,

    @ColumnInfo(name = "ev")
    val ev: Float,

    @ColumnInfo(name = "wb")
    val wb: Int,

    @ColumnInfo(name = "focal_length")
    val focalLength: Int,

    @ColumnInfo(name = "gps_enabled")
    val gpsEnabled: Boolean,

    @ColumnInfo(name = "filter_id")
    val filterId: String?,

    @ColumnInfo(name = "filter_strength")
    val filterStrength: Float,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
