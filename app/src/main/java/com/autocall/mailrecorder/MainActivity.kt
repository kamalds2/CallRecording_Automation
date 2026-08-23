package com.autocall.mailrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.autocall.mailrecorder.ui.navigation.AppNavGraph
import com.autocall.mailrecorder.ui.theme.AutoCallMailRecorderTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Automatically prompt system permissions if any are missing
        if (!com.autocall.mailrecorder.permissions.PermissionManager.hasAllRequiredPermissions(this)) {
            permissionLauncher.launch(
                com.autocall.mailrecorder.permissions.PermissionManager.getRequiredPermissions().toTypedArray()
            )
        }

        val app = application as MainApplication
        val settings = app.settingsRepository.getSettings()
        if (settings.automationEnabled && com.autocall.mailrecorder.permissions.PermissionManager.hasAllRequiredPermissions(this)) {
            com.autocall.mailrecorder.service.CallRecordingService.startMonitoring(this)
        }

        setContent {
            AutoCallMailRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        context = this,
                        settingsRepository = app.settingsRepository,
                        recordingRepository = app.recordingRepository,
                        deliveryRepository = app.deliveryRepository,
                        diagnosticsRepository = app.diagnosticsRepository
                    )
                }
            }
        }
    }
}
