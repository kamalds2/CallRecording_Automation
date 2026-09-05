package com.autocall.mailrecorder.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.autocall.mailrecorder.domain.model.PermissionState

object PermissionManager {

    fun getRequiredPermissions(): List<String> {
        return listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECORD_AUDIO
        )
    }

    fun checkPermissionStates(context: Context): List<PermissionState> {
        val list = mutableListOf<PermissionState>()

        // 1. Phone State
        val phoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionState(
                permissionName = Manifest.permission.READ_PHONE_STATE,
                isGranted = phoneGranted,
                isRequired = true,
                rationale = "Required to detect incoming and outgoing calls and start/stop recordings automatically."
            )
        )

        // 2. Call Log
        val callLogGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionState(
                permissionName = Manifest.permission.READ_CALL_LOG,
                isGranted = callLogGranted,
                isRequired = true,
                rationale = "Used to identify call direction (Incoming vs Outgoing) and call timestamps."
            )
        )

        // 3. Audio Record
        val recordAudioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionState(
                permissionName = Manifest.permission.RECORD_AUDIO,
                isGranted = recordAudioGranted,
                isRequired = true,
                rationale = "Required to capture audio stream during an active phone call."
            )
        )

        // 4. Notifications (Optional, non-blocking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionState(
                    permissionName = Manifest.permission.POST_NOTIFICATIONS,
                    isGranted = notifGranted,
                    isRequired = false, // Optional: App runs fully even when notifications are disabled
                    rationale = "Optional: Displays foreground sync indicators when enabled."
                )
            )
        }

        return list
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        // Only evaluate mandatory permissions (Phone state, Call log, Audio record)
        return checkPermissionStates(context).filter { it.isRequired }.all { it.isGranted }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
