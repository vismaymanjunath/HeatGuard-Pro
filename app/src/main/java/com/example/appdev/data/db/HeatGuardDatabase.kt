package com.example.appdev.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version history:
 * - 1: incidents and incident samples
 * - 2: adds cooling sessions and samples (auto-migration; existing incident data is preserved)
 */
@Database(
    entities = [
        IncidentEntity::class,
        IncidentSampleEntity::class,
        CoolingSessionEntity::class,
        CoolingSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class HeatGuardDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun coolingDao(): CoolingDao

    companion object {
        fun create(context: Context): HeatGuardDatabase =
            Room.databaseBuilder(context.applicationContext, HeatGuardDatabase::class.java, "heatguard.db").build()
    }
}
