package com.autocall.mailrecorder.domain.repository

import com.autocall.mailrecorder.domain.model.*
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    fun getAllRecordingsFlow(): Flow<List<Recording>>
    suspend fun getAllRecordings(): List<Recording>
    suspend fun getRecordingById(id: Long): Recording?
    fun getLatestRecordingFlow(): Flow<Recording?>
    suspend fun saveRecording(recording: Recording): Long
    suspend fun updateRecording(recording: Recording)
    suspend fun deleteRecording(id: Long)
    suspend fun purgeOldRecordings(retentionDays: Int)
}

interface DeliveryRepository {
    suspend fun getPendingJobs(): List<DeliveryJob>
    fun getPendingQueueCountFlow(): Flow<Int>
    suspend fun queueDelivery(recordingId: Long): Long
    suspend fun updateJob(job: DeliveryJob)
    suspend fun markDelivered(recordingId: Long, providerMessageId: String?)
    suspend fun markFailed(recordingId: Long, error: String, retryable: Boolean)
}

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    fun getSettings(): AppSettings
    fun updateSettings(settings: AppSettings)
    fun setSenderPassword(password: String)
    fun getSenderPassword(): String
    fun isConsentAccepted(): Boolean
    fun setConsentAccepted(accepted: Boolean)
    fun isFirstLaunchCompleted(): Boolean
    fun setFirstLaunchCompleted(completed: Boolean)
}

interface DiagnosticsRepository {
    fun getEventsFlow(): Flow<List<DiagnosticEvent>>
    suspend fun logEvent(category: String, message: String, capabilityResult: String? = null)
    suspend fun clearLogs()
}
