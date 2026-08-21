package com.autocall.mailrecorder.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsRepository: SettingsRepository
) {
    val settings = remember { settingsRepository.getSettings() }
    val currentPassword = remember { settingsRepository.getSenderPassword() }

    var recipientEmail by remember { mutableStateOf(settings.recipientEmail) }
    var senderEmail by remember { mutableStateOf(settings.senderEmail) }
    var senderPassword by remember { mutableStateOf(currentPassword) }
    var senderHost by remember { mutableStateOf(settings.senderHost) }
    var senderPort by remember { mutableStateOf(settings.senderPort.toString()) }
    var useTls by remember { mutableStateOf(settings.useTls) }
    var wifiOnly by remember { mutableStateOf(settings.wifiOnly) }
    var retentionDays by remember { mutableStateOf(settings.retentionDays.toString()) }
    var autoDelete by remember { mutableStateOf(settings.autoDeleteAfterSent) }
    var passwordVisible by remember { mutableStateOf(false) }

    var savedNotification by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                text = "Email Delivery Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = recipientEmail,
                onValueChange = { recipientEmail = it },
                label = { Text("Recipient Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = senderEmail,
                onValueChange = { senderEmail = it },
                label = { Text("Sender Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = senderPassword,
                onValueChange = { senderPassword = it },
                label = { Text("Sender Password / App Password") },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = senderHost,
                    onValueChange = { senderHost = it },
                    label = { Text("SMTP Host") },
                    modifier = Modifier.weight(2f)
                )

                OutlinedTextField(
                    value = senderPort,
                    onValueChange = { senderPort = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Policies
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Automation & Storage Policies",
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
                        senderEmail = senderEmail.trim(),
                        senderHost = senderHost.trim(),
                        senderPort = senderPort.toIntOrNull() ?: 587,
                        useTls = useTls,
                        wifiOnly = wifiOnly,
                        retentionDays = retentionDays.toIntOrNull() ?: 30,
                        autoDeleteAfterSent = autoDelete,
                        updatedAt = System.currentTimeMillis()
                    )
                    settingsRepository.updateSettings(updated)
                    settingsRepository.setSenderPassword(senderPassword.trim())
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
