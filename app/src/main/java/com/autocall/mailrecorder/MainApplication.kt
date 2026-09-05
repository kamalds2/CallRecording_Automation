package com.autocall.mailrecorder

import android.app.Application
import com.autocall.mailrecorder.data.local.AppDatabase
import com.autocall.mailrecorder.data.repository.DeliveryRepositoryImpl
import com.autocall.mailrecorder.data.repository.DiagnosticsRepositoryImpl
import com.autocall.mailrecorder.data.repository.RecordingRepositoryImpl
import com.autocall.mailrecorder.data.repository.SettingsRepositoryImpl
import com.autocall.mailrecorder.data.secure.SecurePreferencesManager
import com.autocall.mailrecorder.domain.repository.DeliveryRepository
import com.autocall.mailrecorder.domain.repository.DiagnosticsRepository
import com.autocall.mailrecorder.domain.repository.RecordingRepository
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.workers.WorkManagerScheduler

class MainApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var securePreferencesManager: SecurePreferencesManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var recordingRepository: RecordingRepository
        private set
    lateinit var deliveryRepository: DeliveryRepository
        private set
    lateinit var diagnosticsRepository: DiagnosticsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        securePreferencesManager = SecurePreferencesManager(this)
        settingsRepository = SettingsRepositoryImpl(securePreferencesManager)
        recordingRepository = RecordingRepositoryImpl(database)
        deliveryRepository = DeliveryRepositoryImpl(database)
        diagnosticsRepository = DiagnosticsRepositoryImpl(database)

        // Schedule periodic background delivery worker to retry any failed/offline jobs
        WorkManagerScheduler.schedulePeriodicRetry(this)

        // Perform instant startup storage cleanup
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val settings = securePreferencesManager.loadSettings()
                if (settings.autoDeleteAfterSent) {
                    recordingRepository.purgeSentRecordings()
                }
                com.autocall.mailrecorder.recording.RecordingEngineFactory.cleanupOrphanedRecordings(this@MainApplication)
            } catch (e: Exception) {
                android.util.Log.w("MainApplication", "Startup cleanup note", e)
            }
        }
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}
