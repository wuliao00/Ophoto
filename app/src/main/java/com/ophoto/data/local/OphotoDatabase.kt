package com.ophoto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ophoto.data.local.dao.CaptureRecordDao
import com.ophoto.data.local.dao.EditHistoryDao
import com.ophoto.data.local.dao.FieldProgressDao
import com.ophoto.data.local.dao.FilterDao
import com.ophoto.data.local.entity.CaptureRecordEntity
import com.ophoto.data.local.entity.EditHistoryEntity
import com.ophoto.data.local.entity.FieldProgressEntity
import com.ophoto.data.local.entity.FilterEntity

@Database(
    entities = [
        FilterEntity::class,
        CaptureRecordEntity::class,
        EditHistoryEntity::class,
        FieldProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OphotoDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao
    abstract fun captureRecordDao(): CaptureRecordDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun fieldProgressDao(): FieldProgressDao

    companion object {
        @Volatile
        private var instance: OphotoDatabase? = null

        fun getInstance(context: Context): OphotoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OphotoDatabase::class.java,
                    "ophoto.db",
                ).build().also { instance = it }
            }
        }
    }
}
