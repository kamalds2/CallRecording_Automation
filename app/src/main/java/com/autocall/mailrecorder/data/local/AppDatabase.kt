package com.autocall.mailrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autocall.mailrecorder.data.local.dao.DeliveryJobDao
import com.autocall.mailrecorder.data.local.dao.DiagnosticEventDao
import com.autocall.mailrecorder.data.local.dao.RecordingDao
import com.autocall.mailrecorder.data.local.entity.DeliveryJobEntity
import com.autocall.mailrecorder.data.local.entity.DiagnosticEventEntity
import com.autocall.mailrecorder.data.local.entity.RecordingEntity

@Database(
    entities = [
        RecordingEntity::class,
        DeliveryJobEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun deliveryJobDao(): DeliveryJobDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autocall_mail_recorder.db"
                ).setJournalMode(JournalMode.TRUNCATE) // Truncate mode prevents db-wal growth and frees disk pages immediately
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
