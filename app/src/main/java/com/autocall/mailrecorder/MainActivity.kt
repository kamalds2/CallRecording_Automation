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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MainApplication

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
