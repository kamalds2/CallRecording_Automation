package com.autocall.mailrecorder.diagnostics

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import com.autocall.mailrecorder.permissions.PermissionManager

data class DeviceCapabilityReport(
    val deviceModel: String,
    val androidVersion: String,
    val telephonyAvailable: Boolean,
    val micAudioRecordSupported: Boolean,
    val voiceCommSupported: Boolean,
    val allPermissionsGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val overallStatus: CapabilityStatus,
    val summaryMessage: String
)

enum class CapabilityStatus {
    FULLY_SUPPORTED,
    PARTIALLY_SUPPORTED,
    PERMISSION_REQUIRED,
    UNSUPPORTED
}

object DeviceCapabilityChecker {

    fun runCapabilityCheck(context: Context): DeviceCapabilityReport {
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val telephonyAvailable = telephonyManager != null && telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE

        val permissionsGranted = PermissionManager.hasAllRequiredPermissions(context)
        val batteryIgnored = PermissionManager.isIgnoringBatteryOptimizations(context)

        // Test AudioRecord capability
        var micSupported = false
        var voiceCommSupported = false

        if (permissionsGranted) {
            micSupported = testAudioSource(MediaRecorder.AudioSource.MIC)
            voiceCommSupported = testAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }

        val (status, message) = when {
            !permissionsGranted -> {
                CapabilityStatus.PERMISSION_REQUIRED to "Required runtime permissions are missing. Automation is paused."
            }
            !telephonyAvailable -> {
                CapabilityStatus.UNSUPPORTED to "Telephony hardware is not available on this device."
            }
            voiceCommSupported || micSupported -> {
                CapabilityStatus.FULLY_SUPPORTED to "Device supports standard call audio capture and telephony background lifecycle."
            }
            else -> {
                CapabilityStatus.PARTIALLY_SUPPORTED to "Audio capture source restricted by OEM/OS policies. MIC fallback active."
            }
        }

        return DeviceCapabilityReport(
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            telephonyAvailable = telephonyAvailable,
            micAudioRecordSupported = micSupported,
            voiceCommSupported = voiceCommSupported,
            allPermissionsGranted = permissionsGranted,
            batteryOptimizationIgnored = batteryIgnored,
            overallStatus = status,
            summaryMessage = message
        )
    }

    private fun testAudioSource(source: Int): Boolean {
        return try {
            val sampleRate = 44100
            val channel = AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, format)
            if (bufferSize <= 0) return false

            val record = AudioRecord(source, sampleRate, channel, format, bufferSize)
            val initialized = record.state == AudioRecord.STATE_INITIALIZED
            record.release()
            initialized
        } catch (e: Exception) {
            false
        }
    }
}
