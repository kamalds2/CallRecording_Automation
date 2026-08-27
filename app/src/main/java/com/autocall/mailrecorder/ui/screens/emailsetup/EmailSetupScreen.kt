package com.autocall.mailrecorder.ui.screens.emailsetup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
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
import com.autocall.mailrecorder.ui.navigation.Screen
import com.autocall.mailrecorder.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailSetupScreen(
    navController: NavController,
    settingsRepository: SettingsRepository
) {
    val initialSettings = remember { settingsRepository.getSettings() }
    var recipientEmail by remember { mutableStateOf(initialSettings.recipientEmail) }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testSuccessful by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Auto-request permissions if missing
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (!PermissionManager.hasAllRequiredPermissions(context)) {
            permissionLauncher.launch(PermissionManager.getRequiredPermissions().toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receiver Email Setup") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Where Should Recordings Go?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your email address. Every recorded call will be automatically delivered here in high quality.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Receiver / Recipient Email Field
            OutlinedTextField(
                value = recipientEmail,
                onValueChange = { recipientEmail = it },
                label = { Text("Receiver Email Address") },
                placeholder = { Text("your_email@example.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Test Email Button
            OutlinedButton(
                onClick = {
                    isTesting = true
                    testStatusMessage = null
                    coroutineScope.launch {
                        val currentSettings = initialSettings.copy(
                            recipientEmail = recipientEmail.trim()
                        )
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
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Email Delivery…")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Test Email")
                }
            }

            // Test Status Result
            testStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
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
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Save & Continue Button
            Button(
                onClick = {
                    val updated = initialSettings.copy(
                        automationEnabled = true,
                        recipientEmail = recipientEmail.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    settingsRepository.updateSettings(updated)
                    settingsRepository.setFirstLaunchCompleted(true)

                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.EmailSetup.route) { inclusive = true }
                    }
                },
                enabled = recipientEmail.isNotBlank() && recipientEmail.contains("@"),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Enable Call Automation")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
