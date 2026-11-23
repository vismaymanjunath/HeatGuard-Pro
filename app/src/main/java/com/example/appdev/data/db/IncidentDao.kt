package com.example.appdev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Insert
    suspend fun insertIncident(entity: IncidentEntity): Long

    @Update
    suspend fun updateIncident(entity: IncidentEntity)

    @Insert
    suspend fun insertSample(entity: IncidentSampleEntity)

    @Query("SELECT * FROM incidents WHERE source = :source AND endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun getActive(source: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE source = :source ORDER BY startMillis DESC")
    fun observeIncidents(source: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    fun observeIncident(id: Long): Flow<IncidentEntity?>

    @Query("SELECT * FROM incident_samples WHERE incidentId = :incidentId ORDER BY timestampMillis ASC, id ASC")
    fun observeSamples(incidentId: Long): Flow<List<IncidentSampleEntity>>

    @Query("DELETE FROM incidents WHERE source = :source")
    suspend fun deleteAll(source: String)
}
