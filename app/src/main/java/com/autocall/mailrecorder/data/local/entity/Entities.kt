package com.autocall.mailrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autocall.mailrecorder.domain.model.CallDirection
import com.autocall.mailrecorder.domain.model.CaptureStatus
import com.autocall.mailrecorder.domain.model.DeliveryStatus
import com.autocall.mailrecorder.domain.model.DiagnosticEvent
import com.autocall.mailrecorder.domain.model.Recording

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localPath: String,
    val startedAt: Long,
    val endedAt: Long = 0,
    val durationSeconds: Long = 0,
    val direction: String = CallDirection.UNKNOWN.name,
    val fileFormat: String = "m4a",
    val fileSize: Long = 0,
    val captureStatus: String = CaptureStatus.IDLE.name,
    val deliveryStatus: String = DeliveryStatus.PENDING.name,
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    fun toDomain(): Recording = Recording(
        id = id,
        localPath = localPath,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        direction = try { CallDirection.valueOf(direction) } catch (e: Exception) { CallDirection.UNKNOWN },
        fileFormat = fileFormat,
        fileSize = fileSize,
        captureStatus = try { CaptureStatus.valueOf(captureStatus) } catch (e: Exception) { CaptureStatus.IDLE },
        deliveryStatus = try { DeliveryStatus.valueOf(deliveryStatus) } catch (e: Exception) { DeliveryStatus.PENDING },
        retryCount = retryCount,
        lastError = lastError
    )

    companion object {
        fun fromDomain(model: Recording): RecordingEntity = RecordingEntity(
            id = model.id,
            localPath = model.localPath,
            startedAt = model.startedAt,
            endedAt = model.endedAt,
            durationSeconds = model.durationSeconds,
            direction = model.direction.name,
            fileFormat = model.fileFormat,
            fileSize = model.fileSize,
            captureStatus = model.captureStatus.name,
            deliveryStatus = model.deliveryStatus.name,
            retryCount = model.retryCount,
            lastError = model.lastError
        )
    }
}

@Entity(tableName = "delivery_jobs")
data class DeliveryJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val status: String = DeliveryStatus.QUEUED.name,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val providerMessageId: String? = null,
    val lastError: String? = null
)

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val message: String,
    val capabilityResult: String? = null
) {
    fun toDomain(): DiagnosticEvent = DiagnosticEvent(
        id = id,
        timestamp = timestamp,
        category = category,
        message = message,
        capabilityResult = capabilityResult
    )

    companion object {
        fun fromDomain(model: DiagnosticEvent): DiagnosticEventEntity = DiagnosticEventEntity(
            id = model.id,
            timestamp = model.timestamp,
            category = model.category,
            message = model.message,
            capabilityResult = model.capabilityResult
        )
    }
}
