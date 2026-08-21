package com.autocall.mailrecorder.ui.screens.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.diagnostics.CapabilityStatus
import com.autocall.mailrecorder.diagnostics.DeviceCapabilityChecker
import com.autocall.mailrecorder.domain.repository.DiagnosticsRepository
import com.autocall.mailrecorder.ui.theme.AccentOrange
import com.autocall.mailrecorder.ui.theme.ErrorRed
import com.autocall.mailrecorder.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    navController: NavController,
    diagnosticsRepository: DiagnosticsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var capabilityReport by remember { mutableStateOf(DeviceCapabilityChecker.runCapabilityCheck(context)) }
    val events by diagnosticsRepository.getEventsFlow().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { capabilityReport = DeviceCapabilityChecker.runCapabilityCheck(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-check")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Overall Capability Card
                val (statusColor, statusTitle) = when (capabilityReport.overallStatus) {
                    CapabilityStatus.FULLY_SUPPORTED -> SuccessGreen to "Fully Supported Device"
                    CapabilityStatus.PARTIALLY_SUPPORTED -> AccentOrange to "Partially Supported (Fallback Active)"
                    CapabilityStatus.PERMISSION_REQUIRED -> AccentOrange to "Permissions Required"
                    CapabilityStatus.UNSUPPORTED -> ErrorRed to "Unsupported Hardware/OS"
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (capabilityReport.overallStatus == CapabilityStatus.FULLY_SUPPORTED) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = capabilityReport.summaryMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                // Device Hardware Info
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Platform & Hardware Matrix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DiagnosticRow("Device", capabilityReport.deviceModel)
                        DiagnosticRow("OS Version", capabilityReport.androidVersion)
                        DiagnosticRow("Telephony Radio", if (capabilityReport.telephonyAvailable) "Available" else "Not Present")
                        DiagnosticRow("Microphone AudioRecord", if (capabilityReport.micAudioRecordSupported) "Supported" else "Restricted / Blocked")
                        DiagnosticRow("Voice Comm Source", if (capabilityReport.voiceCommSupported) "Supported" else "Not Available (Mic Fallback)")
                        DiagnosticRow("Battery Optimization", if (capabilityReport.batteryOptimizationIgnored) "Ignored (Background Safe)" else "Active (May Restrict Background)")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Logs (${events.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { coroutineScope.launch { diagnosticsRepository.clearLogs() } }) {
                        Text("Clear Logs")
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "No diagnostic events logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(events) { event ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${event.category}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(event.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
