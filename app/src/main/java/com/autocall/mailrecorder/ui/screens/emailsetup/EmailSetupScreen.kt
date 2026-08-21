package com.autocall.mailrecorder.ui.screens.emailsetup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.delivery.SmtpDeliveryProvider
import com.autocall.mailrecorder.domain.repository.SettingsRepository
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
    val initialPassword = remember { settingsRepository.getSenderPassword() }

    var recipientEmail by remember { mutableStateOf(initialSettings.recipientEmail) }
    var senderEmail by remember { mutableStateOf(initialSettings.senderEmail) }
    var senderPassword by remember { mutableStateOf(initialPassword) }
    var senderHost by remember { mutableStateOf(initialSettings.senderHost) }
    var senderPort by remember { mutableStateOf(initialSettings.senderPort.toString()) }
    var useTls by remember { mutableStateOf(initialSettings.useTls) }
    var passwordVisible by remember { mutableStateOf(false) }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testSuccessful by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Delivery Setup") }
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Configure your sender credentials and destination recipient email address.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Recipient Email
            OutlinedTextField(
                value = recipientEmail,
                onValueChange = { recipientEmail = it },
                label = { Text("Recipient Email (Deliver To)") },
                placeholder = { Text("recipient@example.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sender Email
            OutlinedTextField(
                value = senderEmail,
                onValueChange = { senderEmail = it },
                label = { Text("Sender Email Account") },
                placeholder = { Text("youraccount@gmail.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sender Password
            OutlinedTextField(
                value = senderPassword,
                onValueChange = { senderPassword = it },
                label = { Text("Sender Password / App Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SMTP Host & Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = senderHost,
                    onValueChange = { senderHost = it },
                    label = { Text("SMTP Host") },
                    placeholder = { Text("smtp.gmail.com") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )

                OutlinedTextField(
                    value = senderPort,
                    onValueChange = { senderPort = it },
                    label = { Text("Port") },
                    placeholder = { Text("587") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TLS Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enable STARTTLS / SSL Security",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = useTls,
                    onCheckedChange = { useTls = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Email Button
            OutlinedButton(
                onClick = {
                    isTesting = true
                    testStatusMessage = null
                    coroutineScope.launch {
                        val currentSettings = initialSettings.copy(
                            recipientEmail = recipientEmail.trim(),
                            senderEmail = senderEmail.trim(),
                            senderHost = senderHost.trim(),
                            senderPort = senderPort.toIntOrNull() ?: 587,
                            useTls = useTls
                        )
                        val provider = SmtpDeliveryProvider()
                        val result = provider.sendTestEmail(currentSettings, senderPassword.trim())
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
                enabled = !isTesting && recipientEmail.isNotBlank() && senderEmail.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing SMTP Connection…")
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Continue Button
            Button(
                onClick = {
                    val updated = initialSettings.copy(
                        automationEnabled = true,
                        recipientEmail = recipientEmail.trim(),
                        senderEmail = senderEmail.trim(),
                        senderHost = senderHost.trim(),
                        senderPort = senderPort.toIntOrNull() ?: 587,
                        useTls = useTls,
                        updatedAt = System.currentTimeMillis()
                    )
                    settingsRepository.updateSettings(updated)
                    settingsRepository.setSenderPassword(senderPassword.trim())
                    settingsRepository.setFirstLaunchCompleted(true)

                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.EmailSetup.route) { inclusive = true }
                    }
                },
                enabled = recipientEmail.isNotBlank() && senderEmail.isNotBlank() && senderPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Configuration & Enable Automation")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
