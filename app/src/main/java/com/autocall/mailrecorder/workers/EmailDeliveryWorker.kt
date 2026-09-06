package com.autocall.mailrecorder.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.autocall.mailrecorder.data.local.AppDatabase
import com.autocall.mailrecorder.data.repository.DeliveryRepositoryImpl
import com.autocall.mailrecorder.data.repository.DiagnosticsRepositoryImpl
import com.autocall.mailrecorder.data.repository.RecordingRepositoryImpl
import com.autocall.mailrecorder.data.secure.SecurePreferencesManager
import com.autocall.mailrecorder.delivery.SmtpDeliveryProvider
import java.io.File
import java.util.concurrent.TimeUnit

class EmailDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)
        val deliveryRepo = DeliveryRepositoryImpl(db)
        val recordingRepo = RecordingRepositoryImpl(db)
        val diagnosticsRepo = DiagnosticsRepositoryImpl(db)
        val securePrefs = SecurePreferencesManager(context)

        val settings = securePrefs.loadSettings()
        val senderPassword = securePrefs.getSenderPassword()

        if (settings.recipientEmail.isBlank() || settings.senderEmail.isBlank()) {
            diagnosticsRepo.logEvent("DELIVERY", "Delivery worker skipped: Email configuration incomplete")
            return Result.failure()
        }

        val pendingJobs = deliveryRepo.getPendingJobs()
        if (pendingJobs.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false
        val emailProvider = SmtpDeliveryProvider()

        for (job in pendingJobs) {
            val recording = recordingRepo.getRecordingById(job.recordingId)
            if (recording == null) {
                deliveryRepo.markFailed(job.recordingId, "Recording not found on disk or database", retryable = false)
                continue
            }

            if (job.status == com.autocall.mailrecorder.domain.model.DeliveryStatus.SENT || recording.deliveryStatus == com.autocall.mailrecorder.domain.model.DeliveryStatus.SENT) {
                continue
            }

            // Mark sending to prevent race conditions
            deliveryRepo.updateJob(job.copy(status = com.autocall.mailrecorder.domain.model.DeliveryStatus.SENDING))

            // Attempt delivery
            val sendResult = emailProvider.sendRecording(recording, settings, senderPassword)
            if (sendResult.isSuccess) {
                val messageId = sendResult.getOrNull()
                deliveryRepo.markDelivered(job.recordingId, messageId)
                diagnosticsRepo.logEvent(
                    "DELIVERY",
                    "Email sent successfully for recording #${recording.id} to ${settings.recipientEmail}"
                )

                if (settings.autoDeleteAfterSent) {
                    try {
                        val file = File(recording.localPath)
                        if (file.exists()) file.delete()
                        recordingRepo.deleteRecording(recording.id)
                        com.autocall.mailrecorder.recording.RecordingEngineFactory.cleanupOrphanedRecordings(context)
                        diagnosticsRepo.logEvent(
                            "RECORDING",
                            "Auto-deleted recording #${recording.id} permanently from disk and database after successful delivery."
                        )
                    } catch (e: Exception) {
                        Log.w("EmailDeliveryWorker", "Could not auto-delete recording after send", e)
                    }
                }
            } else {
                anyFailed = true
                val errorMsg = sendResult.exceptionOrNull()?.message ?: "Unknown SMTP error"
                val isRetryable = job.attemptCount < 5
                deliveryRepo.markFailed(job.recordingId, errorMsg, isRetryable)
                diagnosticsRepo.logEvent(
                    "DELIVERY",
                    "Email delivery failed for recording #${recording.id} (attempt ${job.attemptCount + 1}): $errorMsg"
                )
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}

object WorkManagerScheduler {
    private const val UNIQUE_DELIVERY_WORK = "autocall_email_delivery_work"

    fun scheduleDelivery(context: Context, wifiOnly: Boolean = false) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val deliveryRequest = OneTimeWorkRequestBuilder<EmailDeliveryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_DELIVERY_WORK,
                ExistingWorkPolicy.REPLACE,
                deliveryRequest
            )
    }

    fun schedulePeriodicRetry(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<EmailDeliveryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "autocall_periodic_delivery_work",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
    }
}
