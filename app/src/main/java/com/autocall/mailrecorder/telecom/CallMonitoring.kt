package com.autocall.mailrecorder.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.autocall.mailrecorder.data.local.AppDatabase
import com.autocall.mailrecorder.data.repository.DiagnosticsRepositoryImpl
import com.autocall.mailrecorder.data.secure.SecurePreferencesManager
import com.autocall.mailrecorder.domain.model.CallDirection
import com.autocall.mailrecorder.service.CallRecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface CallStateProvider {
    fun onCallRinging(incomingNumber: String?)
    fun onCallOffhook()
    fun onCallIdle()
    fun onOutgoingCall(outgoingNumber: String?)
}

class CallBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val securePrefs = SecurePreferencesManager(context)
        val settings = securePrefs.loadSettings()

        // If automation is disabled, ignore telephony broadcasts
        if (!settings.automationEnabled && action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val db = AppDatabase.getDatabase(context)
        val diagnostics = DiagnosticsRepositoryImpl(db)

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            CallRecordingService.startMonitoring(context)
            CoroutineScope(Dispatchers.IO).launch {
                diagnostics.logEvent("SYSTEM", "Device booted. Call automation monitoring started.")
            }
            return
        }

        @Suppress("DEPRECATION")
        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            lastCallDirection = CallDirection.OUTGOING
            return
        }

        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            Log.d("CallBroadcastReceiver", "Telephony state: $stateStr, Prior Direction: $lastCallDirection")

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    lastCallDirection = CallDirection.INCOMING
                    CoroutineScope(Dispatchers.IO).launch {
                        diagnostics.logEvent(
                            "TELECOM",
                            "Incoming call ringing detected."
                        )
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call is now active (answered or outgoing dialed)
                    val direction = if (lastCallDirection == CallDirection.INCOMING) {
                        CallDirection.INCOMING
                    } else {
                        CallDirection.OUTGOING
                    }
                    lastCallDirection = direction

                    val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                        this.action = CallRecordingService.ACTION_START_RECORDING
                        putExtra(CallRecordingService.EXTRA_DIRECTION, direction.name)
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e("CallBroadcastReceiver", "Failed to start recording service", e)
                        CoroutineScope(Dispatchers.IO).launch {
                            diagnostics.logEvent("TELECOM", "Foreground start error: ${e.message}. Attempting fallback startService.")
                        }
                        try {
                            context.startService(serviceIntent)
                        } catch (ex: Exception) {
                            CoroutineScope(Dispatchers.IO).launch {
                                diagnostics.logEvent("TELECOM", "Fallback service start failed: ${ex.message}")
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        diagnostics.logEvent(
                            "TELECOM",
                            "Active call started (${direction.name}). Dispatched recording service."
                        )
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended / disconnected
                    val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                        this.action = CallRecordingService.ACTION_STOP_RECORDING
                    }
                    try {
                        context.startService(serviceIntent)
                    } catch (e: Exception) {
                        Log.e("CallBroadcastReceiver", "Failed to stop CallRecordingService", e)
                    }

                    // reset direction
                    lastCallDirection = CallDirection.UNKNOWN

                    CoroutineScope(Dispatchers.IO).launch {
                        diagnostics.logEvent("TELECOM", "Call disconnected (IDLE state). Dispatched recording stop.")
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        var lastCallDirection: CallDirection = CallDirection.UNKNOWN
    }
}
