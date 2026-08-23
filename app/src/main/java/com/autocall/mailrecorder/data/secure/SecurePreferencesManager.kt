package com.autocall.mailrecorder.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.autocall.mailrecorder.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurePreferencesManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_app_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for emulators/unsupported crypto hardware
        context.getSharedPreferences("fallback_app_prefs", Context.MODE_PRIVATE)
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun loadSettings(): AppSettings {
        return AppSettings(
            automationEnabled = prefs.getBoolean(KEY_AUTOMATION_ENABLED, true),
            recipientEmail = prefs.getString(KEY_RECIPIENT_EMAIL, "") ?: "",
            deliveryProvider = prefs.getString(KEY_DELIVERY_PROVIDER, "SMTP") ?: "SMTP",
            senderEmail = prefs.getString(KEY_SENDER_EMAIL, "") ?: "",
            senderHost = prefs.getString(KEY_SENDER_HOST, "smtp.gmail.com") ?: "smtp.gmail.com",
            senderPort = prefs.getInt(KEY_SENDER_PORT, 587),
            useTls = prefs.getBoolean(KEY_USE_TLS, true),
            backendUrl = prefs.getString(KEY_BACKEND_URL, "") ?: "",
            retentionDays = prefs.getInt(KEY_RETENTION_DAYS, 30),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            autoDeleteAfterSent = prefs.getBoolean(KEY_AUTO_DELETE, false),
            audioSource = prefs.getString(KEY_AUDIO_SOURCE, "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION",
            audioGainMultiplier = prefs.getFloat(KEY_AUDIO_GAIN, 2.5f)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTOMATION_ENABLED, settings.automationEnabled)
            .putString(KEY_RECIPIENT_EMAIL, settings.recipientEmail)
            .putString(KEY_DELIVERY_PROVIDER, settings.deliveryProvider)
            .putString(KEY_SENDER_EMAIL, settings.senderEmail)
            .putString(KEY_SENDER_HOST, settings.senderHost)
            .putInt(KEY_SENDER_PORT, settings.senderPort)
            .putBoolean(KEY_USE_TLS, settings.useTls)
            .putString(KEY_BACKEND_URL, settings.backendUrl)
            .putInt(KEY_RETENTION_DAYS, settings.retentionDays)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_AUTO_DELETE, settings.autoDeleteAfterSent)
            .putString(KEY_AUDIO_SOURCE, settings.audioSource)
            .putFloat(KEY_AUDIO_GAIN, settings.audioGainMultiplier)
            .apply()

        _settingsFlow.value = settings
    }

    fun setSenderPassword(password: String) {
        prefs.edit().putString(KEY_SENDER_PASSWORD, password).apply()
    }

    fun getSenderPassword(): String {
        return prefs.getString(KEY_SENDER_PASSWORD, "") ?: ""
    }

    fun setConsentAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_ACCEPTED, accepted).apply()
    }

    fun isConsentAccepted(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_ACCEPTED, false)
    }

    fun setFirstLaunchCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, completed).apply()
    }

    fun isFirstLaunchCompleted(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
    }

    companion object {
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_DELIVERY_PROVIDER = "delivery_provider"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_SENDER_HOST = "sender_host"
        private const val KEY_SENDER_PORT = "sender_port"
        private const val KEY_USE_TLS = "use_tls"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_DELETE = "auto_delete"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_AUDIO_GAIN = "audio_gain"
        private const val KEY_CONSENT_ACCEPTED = "consent_accepted"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
    }
}
