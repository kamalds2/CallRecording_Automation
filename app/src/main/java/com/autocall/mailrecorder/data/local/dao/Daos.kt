package com.autocall.mailrecorder.data.local.dao

import androidx.room.*
import com.autocall.mailrecorder.data.local.entity.DeliveryJobEntity
import com.autocall.mailrecorder.data.local.entity.DiagnosticEventEntity
import com.autocall.mailrecorder.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    suspend fun getAllRecordings(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC LIMIT 1")
    fun getLatestRecordingFlow(): Flow<RecordingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)

    @Query("DELETE FROM recordings WHERE startedAt < :cutoffTimestamp")
    suspend fun deleteRecordingsOlderThan(cutoffTimestamp: Long)
}

@Dao
interface DeliveryJobDao {
    @Query("SELECT * FROM delivery_jobs WHERE status = 'QUEUED' OR status = 'FAILED_RETRYABLE' ORDER BY nextAttemptAt ASC")
    suspend fun getPendingJobs(): List<DeliveryJobEntity>

    @Query("SELECT COUNT(*) FROM delivery_jobs WHERE status = 'QUEUED' OR status = 'FAILED_RETRYABLE'")
    fun getPendingQueueCountFlow(): Flow<Int>

    @Query("SELECT * FROM delivery_jobs WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getJobByRecordingId(recordingId: Long): DeliveryJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: DeliveryJobEntity): Long

    @Update
    suspend fun updateJob(job: DeliveryJobEntity)

    @Query("DELETE FROM delivery_jobs WHERE recordingId = :recordingId")
    suspend fun deleteJobByRecordingId(recordingId: Long)
}

@Dao
interface DiagnosticEventDao {
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT 100")
    fun getAllEventsFlow(): Flow<List<DiagnosticEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DiagnosticEventEntity): Long

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearAll()
}
