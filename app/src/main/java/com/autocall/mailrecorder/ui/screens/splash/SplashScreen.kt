package com.autocall.mailrecorder.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.permissions.PermissionManager
import com.autocall.mailrecorder.ui.navigation.Screen
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    settingsRepository: SettingsRepository,
    context: android.content.Context
) {
    LaunchedEffect(Unit) {
        delay(1200)
        val consentAccepted = settingsRepository.isConsentAccepted()
        val settings = settingsRepository.getSettings()
        val emailConfigured = settings.recipientEmail.isNotBlank()

        when {
            !consentAccepted -> {
                navController.navigate(Screen.Consent.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
            !emailConfigured -> {
                navController.navigate(Screen.EmailSetup.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
            else -> {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneInTalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AutoCall Mail Recorder",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Secure Call Recording & Automated Delivery",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
