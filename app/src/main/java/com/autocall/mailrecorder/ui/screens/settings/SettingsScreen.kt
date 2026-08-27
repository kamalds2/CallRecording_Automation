package com.autocall.mailrecorder.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.delivery.SmtpDeliveryProvider
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.permissions.PermissionManager
import com.autocall.mailrecorder.telecom.CallAccessibilityService
import com.autocall.mailrecorder.ui.navigation.Screen
import com.autocall.mailrecorder.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val settings = remember { settingsRepository.getSettings() }

    var recipientEmail by remember { mutableStateOf(settings.recipientEmail) }
    var wifiOnly by remember { mutableStateOf(settings.wifiOnly) }
    var retentionDays by remember { mutableStateOf(settings.retentionDays.toString()) }
    var autoDelete by remember { mutableStateOf(settings.autoDeleteAfterSent) }

    var savedNotification by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testSuccessful by remember { mutableStateOf(false) }

    val isAccessibilityEnabled = remember(context) {
        CallAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Section: Delivery Destination
            Text(
                text = "Receiver Email Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All automated call recordings will be sent to this email address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = recipientEmail,
                onValueChange = { recipientEmail = it },
                label = { Text("Receiver Email (Deliver To)") },
                placeholder = { Text("your_email@example.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Send Test Email Button
            OutlinedButton(
                onClick = {
                    isTesting = true
                    testStatusMessage = null
                    coroutineScope.launch {
                        val currentSettings = settings.copy(recipientEmail = recipientEmail.trim())
                        val provider = SmtpDeliveryProvider()
                        val senderPassword = settingsRepository.getSenderPassword()
                        val result = provider.sendTestEmail(currentSettings, senderPassword)
                        isTesting = false
                        if (result.isSuccess) {
                            testSuccessful = true
                            testStatusMessage = "Test email sent successfully to ${currentSettings.recipientEmail}!"
                        } else {
                            testSuccessful = false
                            testStatusMessage = "Test failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                },
                enabled = !isTesting && recipientEmail.isNotBlank() && recipientEmail.contains("@"),
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing…")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Test Email Now")
                }
            }

            testStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testSuccessful) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testSuccessful) SuccessGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Android Clear Audio Accessibility Support
            Text(
                text = "Call Audio Recording Service",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isAccessibilityEnabled) {
                    "Accessibility audio service is ACTIVE. Both sides of calls will be recorded clearly."
                } else {
                    "Enable the Accessibility Service to bypass Android 10+ microphone restrictions and record both parties clearly."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isAccessibilityEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!isAccessibilityEnabled) {
                FilledTonalButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Accessibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enable Accessibility Audio Service")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Storage & Upload Policies
            Text(
                text = "Delivery & Storage Policies",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Wi-Fi Only Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Upload on Wi-Fi Only", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Prevents sending audio on mobile data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-delete switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Auto-Delete After Delivery", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Saves disk space once email is sent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoDelete, onCheckedChange = { autoDelete = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Retention days
            OutlinedTextField(
                value = retentionDays,
                onValueChange = { retentionDays = it },
                label = { Text("Retention Period (Days)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permissions setup button
            OutlinedButton(
                onClick = { navController.navigate(Screen.Permissions.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage App Permissions")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val updated = settings.copy(
                        recipientEmail = recipientEmail.trim(),
                        wifiOnly = wifiOnly,
                        retentionDays = retentionDays.toIntOrNull() ?: 30,
                        autoDeleteAfterSent = autoDelete,
                        updatedAt = System.currentTimeMillis()
                    )
                    settingsRepository.updateSettings(updated)
                    savedNotification = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Settings")
            }

            if (savedNotification) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Settings saved successfully!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
