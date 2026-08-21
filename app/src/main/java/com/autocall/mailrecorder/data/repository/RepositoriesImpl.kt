package com.autocall.mailrecorder.data.repository

import com.autocall.mailrecorder.data.local.AppDatabase
import com.autocall.mailrecorder.data.local.entity.DeliveryJobEntity
import com.autocall.mailrecorder.data.local.entity.DiagnosticEventEntity
import com.autocall.mailrecorder.data.local.entity.RecordingEntity
import com.autocall.mailrecorder.data.secure.SecurePreferencesManager
import com.autocall.mailrecorder.domain.model.*
import com.autocall.mailrecorder.domain.repository.DeliveryRepository
import com.autocall.mailrecorder.domain.repository.DiagnosticsRepository
import com.autocall.mailrecorder.domain.repository.RecordingRepository
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class RecordingRepositoryImpl(
    private val database: AppDatabase
) : RecordingRepository {

    override fun getAllRecordingsFlow(): Flow<List<Recording>> {
        return database.recordingDao().getAllRecordingsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllRecordings(): List<Recording> {
        return database.recordingDao().getAllRecordings().map { it.toDomain() }
    }

    override suspend fun getRecordingById(id: Long): Recording? {
        return database.recordingDao().getRecordingById(id)?.toDomain()
    }

    override fun getLatestRecordingFlow(): Flow<Recording?> {
        return database.recordingDao().getLatestRecordingFlow().map { it?.toDomain() }
    }

    override suspend fun saveRecording(recording: Recording): Long {
        return database.recordingDao().insertRecording(RecordingEntity.fromDomain(recording))
    }

    override suspend fun updateRecording(recording: Recording) {
        database.recordingDao().updateRecording(RecordingEntity.fromDomain(recording))
    }

    override suspend fun deleteRecording(id: Long) {
        val existing = database.recordingDao().getRecordingById(id)
        existing?.let {
            try {
                val file = File(it.localPath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // ignore file delete errors
            }
            database.recordingDao().deleteRecordingById(id)
            database.deliveryJobDao().deleteJobByRecordingId(id)
        }
    }

    override suspend fun purgeOldRecordings(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val old = database.recordingDao().getAllRecordings().filter { it.startedAt < cutoff }
        for (r in old) {
            deleteRecording(r.id)
        }
    }
}

class DeliveryRepositoryImpl(
    private val database: AppDatabase
) : DeliveryRepository {

    override suspend fun getPendingJobs(): List<DeliveryJob> {
        return database.deliveryJobDao().getPendingJobs().map { entity ->
            DeliveryJob(
                id = entity.id,
                recordingId = entity.recordingId,
                status = try { DeliveryStatus.valueOf(entity.status) } catch (e: Exception) { DeliveryStatus.QUEUED },
                nextAttemptAt = entity.nextAttemptAt,
                attemptCount = entity.attemptCount,
                providerMessageId = entity.providerMessageId,
                lastError = entity.lastError
            )
        }
    }

    override fun getPendingQueueCountFlow(): Flow<Int> {
        return database.deliveryJobDao().getPendingQueueCountFlow()
    }

    override suspend fun queueDelivery(recordingId: Long): Long {
        val existingJob = database.deliveryJobDao().getJobByRecordingId(recordingId)
        val jobId = if (existingJob != null) {
            val updated = existingJob.copy(
                status = DeliveryStatus.QUEUED.name,
                nextAttemptAt = System.currentTimeMillis(),
                attemptCount = 0,
                lastError = null
            )
            database.deliveryJobDao().updateJob(updated)
            existingJob.id
        } else {
            val newEntity = DeliveryJobEntity(
                recordingId = recordingId,
                status = DeliveryStatus.QUEUED.name,
                nextAttemptAt = System.currentTimeMillis(),
                attemptCount = 0
            )
            database.deliveryJobDao().insertJob(newEntity)
        }

        // update recording status
        database.recordingDao().getRecordingById(recordingId)?.let {
            database.recordingDao().updateRecording(it.copy(deliveryStatus = DeliveryStatus.QUEUED.name))
        }

        return jobId
    }

    override suspend fun updateJob(job: DeliveryJob) {
        database.deliveryJobDao().updateJob(
            DeliveryJobEntity(
                id = job.id,
                recordingId = job.recordingId,
                status = job.status.name,
                nextAttemptAt = job.nextAttemptAt,
                attemptCount = job.attemptCount,
                providerMessageId = job.providerMessageId,
                lastError = job.lastError
            )
        )
    }

    override suspend fun markDelivered(recordingId: Long, providerMessageId: String?) {
        database.deliveryJobDao().getJobByRecordingId(recordingId)?.let {
            database.deliveryJobDao().updateJob(
                it.copy(status = DeliveryStatus.SENT.name, providerMessageId = providerMessageId)
            )
        }
        database.recordingDao().getRecordingById(recordingId)?.let {
            database.recordingDao().updateRecording(
                it.copy(deliveryStatus = DeliveryStatus.SENT.name, lastError = null)
            )
        }
    }

    override suspend fun markFailed(recordingId: Long, error: String, retryable: Boolean) {
        val nextStatus = if (retryable) DeliveryStatus.FAILED_RETRYABLE else DeliveryStatus.FAILED_NEEDS_ATTENTION
        database.deliveryJobDao().getJobByRecordingId(recordingId)?.let {
            val newAttempt = it.attemptCount + 1
            // Exponential backoff: 2^attempt * 30 seconds
            val delayMillis = (1L shl (newAttempt.coerceAtMost(6))) * 30_000L
            database.deliveryJobDao().updateJob(
                it.copy(
                    status = nextStatus.name,
                    attemptCount = newAttempt,
                    nextAttemptAt = System.currentTimeMillis() + delayMillis,
                    lastError = error
                )
            )
        }
        database.recordingDao().getRecordingById(recordingId)?.let {
            database.recordingDao().updateRecording(
                it.copy(
                    deliveryStatus = nextStatus.name,
                    retryCount = it.retryCount + 1,
                    lastError = error
                )
            )
        }
    }
}

class SettingsRepositoryImpl(
    private val securePreferencesManager: SecurePreferencesManager
) : SettingsRepository {

    override val settingsFlow: Flow<AppSettings> = securePreferencesManager.settingsFlow

    override fun getSettings(): AppSettings = securePreferencesManager.loadSettings()

    override fun updateSettings(settings: AppSettings) {
        securePreferencesManager.saveSettings(settings)
    }

    override fun setSenderPassword(password: String) {
        securePreferencesManager.setSenderPassword(password)
    }

    override fun getSenderPassword(): String {
        return securePreferencesManager.getSenderPassword()
    }

    override fun isConsentAccepted(): Boolean {
        return securePreferencesManager.isConsentAccepted()
    }

    override fun setConsentAccepted(accepted: Boolean) {
        securePreferencesManager.setConsentAccepted(accepted)
    }

    override fun isFirstLaunchCompleted(): Boolean {
        return securePreferencesManager.isFirstLaunchCompleted()
    }

    override fun setFirstLaunchCompleted(completed: Boolean) {
        securePreferencesManager.setFirstLaunchCompleted(completed)
    }
}

class DiagnosticsRepositoryImpl(
    private val database: AppDatabase
) : DiagnosticsRepository {

    override fun getEventsFlow(): Flow<List<DiagnosticEvent>> {
        return database.diagnosticEventDao().getAllEventsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun logEvent(category: String, message: String, capabilityResult: String?) {
        database.diagnosticEventDao().insertEvent(
            DiagnosticEventEntity(
                category = category,
                message = message,
                capabilityResult = capabilityResult
            )
        )
    }

    override suspend fun clearLogs() {
        database.diagnosticEventDao().clearAll()
    }
}
