package com.daniele21.trafficmonitoring.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ValidationRunEntity::class,
        NetworkEventEntity::class,
        CounterSnapshotEntity::class,
        LifecycleEventEntity::class,
        ManualTestMarkerEntity::class,
        AttributionIntervalEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ValidationDatabase : RoomDatabase() {
    abstract fun validationDao(): ValidationDao

    companion object {
        @Volatile
        private var instance: ValidationDatabase? = null

        fun getInstance(context: Context): ValidationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ValidationDatabase::class.java,
                    "traffic-monitoring-validation.db"
                ).build().also { instance = it }
            }
    }
}
