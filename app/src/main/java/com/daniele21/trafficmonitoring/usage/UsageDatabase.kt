package com.daniele21.trafficmonitoring.usage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NetworkProfileEntity::class,
        UsageBucketEntity::class,
        ProcessedIntervalEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile private var instance: UsageDatabase? = null

        fun getInstance(context: Context): UsageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UsageDatabase::class.java,
                "traffic-usage.db"
            ).build().also { instance = it }
        }
    }
}
