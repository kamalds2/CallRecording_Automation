package com.autocall.mailrecorder.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.autocall.mailrecorder.domain.repository.DeliveryRepository
import com.autocall.mailrecorder.domain.repository.DiagnosticsRepository
import com.autocall.mailrecorder.domain.repository.RecordingRepository
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.ui.screens.consent.ConsentScreen
import com.autocall.mailrecorder.ui.screens.dashboard.DashboardScreen
import com.autocall.mailrecorder.ui.screens.diagnostics.DiagnosticsScreen
import com.autocall.mailrecorder.ui.screens.emailsetup.EmailSetupScreen
import com.autocall.mailrecorder.ui.screens.history.RecordingHistoryScreen
import com.autocall.mailrecorder.ui.screens.permissions.PermissionsScreen
import com.autocall.mailrecorder.ui.screens.settings.SettingsScreen
import com.autocall.mailrecorder.ui.screens.splash.SplashScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    context: Context,
    settingsRepository: SettingsRepository,
    recordingRepository: RecordingRepository,
    deliveryRepository: DeliveryRepository,
    diagnosticsRepository: DiagnosticsRepository
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController, settingsRepository, context)
        }
        composable(Screen.Consent.route) {
            ConsentScreen(navController, settingsRepository)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController)
        }
        composable(Screen.EmailSetup.route) {
            EmailSetupScreen(navController, settingsRepository)
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController, settingsRepository, recordingRepository, deliveryRepository)
        }
        composable(Screen.History.route) {
            RecordingHistoryScreen(navController, recordingRepository, deliveryRepository)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController, settingsRepository)
        }
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(navController, diagnosticsRepository)
        }
    }
}
