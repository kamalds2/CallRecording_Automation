package com.autocall.mailrecorder.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.domain.model.DeliveryStatus
import com.autocall.mailrecorder.domain.repository.DeliveryRepository
import com.autocall.mailrecorder.domain.repository.RecordingRepository
import com.autocall.mailrecorder.domain.repository.SettingsRepository
import com.autocall.mailrecorder.permissions.PermissionManager
import com.autocall.mailrecorder.ui.navigation.Screen
import com.autocall.mailrecorder.ui.theme.AccentOrange
import com.autocall.mailrecorder.ui.theme.ErrorRed
import com.autocall.mailrecorder.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    settingsRepository: SettingsRepository,
    recordingRepository: RecordingRepository,
    deliveryRepository: DeliveryRepository
) {
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = settingsRepository.getSettings())
    val latestRecording by recordingRepository.getLatestRecordingFlow().collectAsState(initial = null)
    val pendingQueueCount by deliveryRepository.getPendingQueueCountFlow().collectAsState(initial = 0)

    val hasPermissions = remember(context) { PermissionManager.hasAllRequiredPermissions(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoCall Mail Recorder") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Diagnostics.route) }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* already on dashboard */ },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.History.route) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingQueueCount > 0) {
                                    Badge { Text("$pendingQueueCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                        }
                    },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Diagnostics.route) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                    label = { Text("Diagnostics") }
                )
            }
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
            Spacer(modifier = Modifier.height(8.dp))

            // Automation Status Master Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.automationEnabled && hasPermissions) {
                        SuccessGreen.copy(alpha = 0.12f)
                    } else {
                        ErrorRed.copy(alpha = 0.12f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (settings.automationEnabled && hasPermissions) SuccessGreen else ErrorRed,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (settings.automationEnabled && hasPermissions) Icons.Default.Check else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.automationEnabled && hasPermissions) "Automation Active" else "Automation Paused",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!hasPermissions) {
                                "Permissions revoked! Tap to restore."
                            } else if (settings.automationEnabled) {
                                "Ready to record supported calls"
                            } else {
                                "Paused by user toggle"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = settings.automationEnabled && hasPermissions,
                        onCheckedChange = { enabled ->
                            if (!hasPermissions) {
                                navController.navigate(Screen.Permissions.route)
                            } else {
                                settingsRepository.updateSettings(settings.copy(automationEnabled = enabled))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pending Queue Stat
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pending Queue", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$pendingQueueCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingQueueCount > 0) AccentOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Recipient Stat
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recipient", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (settings.recipientEmail.isNotBlank()) settings.recipientEmail else "Not Set",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Last Call Result Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Last Call Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (latestRecording != null) {
                        val rec = latestRecording!!
                        val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(rec.startedAt))
                        val durationMin = rec.durationSeconds / 60
                        val durationSec = rec.durationSeconds % 60

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${rec.direction} Call (${durationMin}m ${durationSec}s)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = dateFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Delivery Status Chip
                            DeliveryStatusChip(rec.deliveryStatus)
                        }
                    } else {
                        Text(
                            text = "No calls recorded yet. Once a phone call is received or dialed, it will appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // View Full History Button
            Button(
                onClick = { navController.navigate(Screen.History.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Complete Recording History")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DeliveryStatusChip(status: DeliveryStatus) {
    val (color, label) = when (status) {
        DeliveryStatus.SENT -> SuccessGreen to "SENT"
        DeliveryStatus.QUEUED -> AccentOrange to "QUEUED"
        DeliveryStatus.SENDING -> MaterialTheme.colorScheme.primary to "SENDING"
        DeliveryStatus.FAILED_RETRYABLE -> ErrorRed to "RETRYABLE"
        DeliveryStatus.FAILED_NEEDS_ATTENTION -> ErrorRed to "FAILED"
        DeliveryStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant to "PENDING"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
