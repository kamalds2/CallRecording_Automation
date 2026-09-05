package com.autocall.mailrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autocall.mailrecorder.R
import com.autocall.mailrecorder.data.local.AppDatabase
import com.autocall.mailrecorder.data.repository.DeliveryRepositoryImpl
import com.autocall.mailrecorder.data.repository.DiagnosticsRepositoryImpl
import com.autocall.mailrecorder.data.repository.RecordingRepositoryImpl
import com.autocall.mailrecorder.data.secure.SecurePreferencesManager
import com.autocall.mailrecorder.domain.model.CallDirection
import com.autocall.mailrecorder.domain.model.CaptureStatus
import com.autocall.mailrecorder.domain.model.DeliveryStatus
import com.autocall.mailrecorder.domain.model.Recording
import com.autocall.mailrecorder.recording.RecordingEngine
import com.autocall.mailrecorder.recording.RecordingEngineFactory
import com.autocall.mailrecorder.workers.WorkManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class CallRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingEngine: RecordingEngine? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0
    private var currentDirection: CallDirection = CallDirection.UNKNOWN

    private lateinit var database: AppDatabase
    private lateinit var recordingRepository: RecordingRepositoryImpl
    private lateinit var deliveryRepository: DeliveryRepositoryImpl
    private lateinit var diagnosticsRepository: DiagnosticsRepositoryImpl
    private lateinit var securePreferencesManager: SecurePreferencesManager

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        recordingRepository = RecordingRepositoryImpl(database)
        deliveryRepository = DeliveryRepositoryImpl(database)
        diagnosticsRepository = DiagnosticsRepositoryImpl(database)
        securePreferencesManager = SecurePreferencesManager(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING

        when (action) {
            ACTION_START_MONITORING -> {
                startForegroundMonitoring()
            }
            ACTION_START_RECORDING -> {
                val directionStr = intent?.getStringExtra(EXTRA_DIRECTION) ?: CallDirection.UNKNOWN.name
                currentDirection = try { CallDirection.valueOf(directionStr) } catch (e: Exception) { CallDirection.UNKNOWN }
                startForegroundRecording(currentDirection)
                startRecordingSession(currentDirection)
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingSession()
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
            }
        }

        return START_STICKY
    }

    private fun startForegroundMonitoring() {
        val notification = buildNotification("System Sync Active: Ready")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("CallRecordingService", "startForegroundMonitoring error", e)
            try { startForeground(NOTIFICATION_ID, notification) } catch (ignored: Exception) {}
        }
    }

    private fun startForegroundRecording(direction: CallDirection) {
        val dirName = direction.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = buildNotification("System Sync: Active Call Sync ($dirName)…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("CallRecordingService", "startForegroundRecording error", e)
            try { startForeground(NOTIFICATION_ID, notification) } catch (ignored: Exception) {}
        }
    }

    private fun stopMonitoring() {
        serviceScope.launch {
            if (recordingEngine?.isRecording() == true) {
                stopRecordingSession()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startRecordingSession(direction: CallDirection) {
        if (recordingEngine?.isRecording() == true) {
            Log.w("CallRecordingService", "Already recording active session")
            return
        }

        serviceScope.launch {
            try {
                val engine = RecordingEngineFactory.createEngine(this@CallRecordingService)
                val targetFile = RecordingEngineFactory.generateRecordingFile(
                    this@CallRecordingService,
                    direction,
                    engine.fileExtension
                )

                val result = engine.startRecording(targetFile)
                if (result.isSuccess) {
                    recordingEngine = engine
                    currentRecordingFile = targetFile
                    recordingStartTime = System.currentTimeMillis()
                    diagnosticsRepository.logEvent(
                        "RECORDING",
                        "Started call audio capture with ${engine.name} to ${targetFile.name}"
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown recording start error"
                    diagnosticsRepository.logEvent("RECORDING", "Failed to start audio capture: $error")
                    // revert notification to monitoring
                    startForegroundMonitoring()
                }
            } catch (e: Exception) {
                diagnosticsRepository.logEvent("RECORDING", "Exception starting capture: ${e.message}")
                startForegroundMonitoring()
            }
        }
    }

    private fun stopRecordingSession() {
        serviceScope.launch {
            try {
                val engine = recordingEngine
                if (engine != null && engine.isRecording()) {
                    val stopResult = engine.stopRecording()
                    val endedAt = System.currentTimeMillis()
                    val duration = ((endedAt - recordingStartTime) / 1000).coerceAtLeast(0)

                    if (stopResult.isSuccess) {
                        val file = stopResult.getOrThrow()
                        val fileSize = if (file.exists()) file.length() else 0L

                        val recording = Recording(
                            localPath = file.absolutePath,
                            startedAt = recordingStartTime,
                            endedAt = endedAt,
                            durationSeconds = duration,
                            direction = currentDirection,
                            fileFormat = engine.fileExtension,
                            fileSize = fileSize,
                            captureStatus = CaptureStatus.COMPLETED,
                            deliveryStatus = DeliveryStatus.QUEUED
                        )

                        val savedId = recordingRepository.saveRecording(recording)
                        deliveryRepository.queueDelivery(savedId)

                        diagnosticsRepository.logEvent(
                            "RECORDING",
                            "Recording finalized successfully: ${file.name} ($duration s, $fileSize bytes). Added to delivery queue."
                        )

                        val settings = securePreferencesManager.loadSettings()

                        // Dispatch single reliable email delivery through WorkManager
                        WorkManagerScheduler.scheduleDelivery(this@CallRecordingService, settings.wifiOnly)

                        // Purge old recordings according to retention policy and cleanup cache
                        recordingRepository.purgeOldRecordings(settings.retentionDays)
                        RecordingEngineFactory.cleanupOrphanedRecordings(this@CallRecordingService)
                    } else {
                        val error = stopResult.exceptionOrNull()?.message ?: "Stop recording failed"
                        diagnosticsRepository.logEvent("RECORDING", "Failed to finalize audio file: $error")
                    }
                }
            } catch (e: Exception) {
                diagnosticsRepository.logEvent("RECORDING", "Error during recording finalization: ${e.message}")
            } finally {
                recordingEngine = null
                currentRecordingFile = null
                // Return to active monitoring state instead of killing the service
                startForegroundMonitoring()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "call_recording_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START_MONITORING = "com.autocall.mailrecorder.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.autocall.mailrecorder.ACTION_STOP_MONITORING"
        const val ACTION_START_RECORDING = "com.autocall.mailrecorder.ACTION_START"
        const val ACTION_STOP_RECORDING = "com.autocall.mailrecorder.ACTION_STOP"
        const val EXTRA_DIRECTION = "extra_direction"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("CallRecordingService", "Failed to start monitoring service", e)
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("CallRecordingService", "Failed to stop monitoring service", e)
            }
        }
    }
}
