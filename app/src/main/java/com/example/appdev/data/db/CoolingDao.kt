package com.example.appdev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CoolingDao {
    @Insert
    suspend fun insertSession(entity: CoolingSessionEntity): Long

    @Update
    suspend fun updateSession(entity: CoolingSessionEntity)

    @Insert
    suspend fun insertSample(entity: CoolingSampleEntity)

    @Query("SELECT * FROM cooling_sessions WHERE source = :source AND outcome IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun getActive(source: String): CoolingSessionEntity?

    @Query("SELECT * FROM cooling_sessions WHERE source = :source ORDER BY startMillis DESC")
    fun observeSessions(source: String): Flow<List<CoolingSessionEntity>>

    @Query("SELECT * FROM cooling_samples WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    fun observeSamples(sessionId: Long): Flow<List<CoolingSampleEntity>>

    @Query("SELECT * FROM cooling_samples WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    suspend fun getSamples(sessionId: Long): List<CoolingSampleEntity>

    @Query("DELETE FROM cooling_sessions WHERE source = :source")
    suspend fun deleteAll(source: String)
}
