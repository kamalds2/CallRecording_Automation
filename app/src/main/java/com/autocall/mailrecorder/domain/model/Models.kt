package com.autocall.mailrecorder.domain.model

enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

enum class CaptureStatus {
    IDLE,
    CALL_ACTIVE,
    RECORDING,
    FINALIZING,
    COMPLETED,
    FAILED,
    UNSUPPORTED
}

enum class DeliveryStatus {
    PENDING,
    QUEUED,
    SENDING,
    SENT,
    FAILED_RETRYABLE,
    FAILED_NEEDS_ATTENTION
}

data class Recording(
    val id: Long = 0,
    val localPath: String,
    val startedAt: Long,
    val endedAt: Long = 0,
    val durationSeconds: Long = 0,
    val direction: CallDirection = CallDirection.UNKNOWN,
    val fileFormat: String = "m4a",
    val fileSize: Long = 0,
    val captureStatus: CaptureStatus = CaptureStatus.IDLE,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)

data class DeliveryJob(
    val id: Long = 0,
    val recordingId: Long,
    val status: DeliveryStatus = DeliveryStatus.QUEUED,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val providerMessageId: String? = null,
    val lastError: String? = null
)

data class AppSettings(
    val id: Int = 1,
    val automationEnabled: Boolean = true,
    val recipientEmail: String = "",
    val deliveryProvider: String = "SMTP",
    val senderEmail: String = "kamalkumar.doddi@gmail.com",
    val senderHost: String = "smtp.gmail.com",
    val senderPort: Int = 465, // SSL port
    val useTls: Boolean = false,
    val backendUrl: String = "",
    val retentionDays: Int = 30,
    val wifiOnly: Boolean = false,
    val autoDeleteAfterSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DiagnosticEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "TELECOM", "RECORDING", "DELIVERY", "PERMISSION"
    val message: String,
    val capabilityResult: String? = null
)

data class PermissionState(
    val permissionName: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val rationale: String,
    val lastCheckedAt: Long = System.currentTimeMillis()
)
