package com.autocall.mailrecorder.ui.screens.history

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autocall.mailrecorder.domain.model.Recording
import com.autocall.mailrecorder.domain.repository.DeliveryRepository
import com.autocall.mailrecorder.domain.repository.RecordingRepository
import com.autocall.mailrecorder.ui.screens.dashboard.DeliveryStatusChip
import com.autocall.mailrecorder.workers.WorkManagerScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingHistoryScreen(
    navController: NavController,
    recordingRepository: RecordingRepository,
    deliveryRepository: DeliveryRepository
) {
    val context = LocalContext.current
    val recordings by recordingRepository.getAllRecordingsFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording History (${recordings.size})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No recordings found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(recordings, key = { it.id }) { item ->
                    RecordingItemCard(
                        recording = item,
                        isPlaying = currentlyPlayingPath == item.localPath,
                        onPlayToggle = {
                            if (currentlyPlayingPath == item.localPath) {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                currentlyPlayingPath = null
                            } else {
                                mediaPlayer?.release()
                                val mp = MediaPlayer().apply {
                                    setDataSource(item.localPath)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        currentlyPlayingPath = null
                                        release()
                                    }
                                }
                                mediaPlayer = mp
                                currentlyPlayingPath = item.localPath
                            }
                        },
                        onResend = {
                            coroutineScope.launch {
                                deliveryRepository.queueDelivery(item.id)
                                WorkManagerScheduler.scheduleDelivery(context)
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                if (currentlyPlayingPath == item.localPath) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    currentlyPlayingPath = null
                                }
                                recordingRepository.deleteRecording(item.id)
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun RecordingItemCard(
    recording: Recording,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(recording.startedAt))
    val durationMin = recording.durationSeconds / 60
    val durationSec = recording.durationSeconds % 60
    val sizeKb = recording.fileSize / 1024
    val fileExists = File(recording.localPath).exists()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${recording.direction} Call",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$dateFormatted • ${durationMin}m ${durationSec}s • $sizeKb KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DeliveryStatusChip(recording.deliveryStatus)
            }

            if (recording.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: ${recording.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fileExists) {
                    IconButton(onClick = onPlayToggle) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play/Stop",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onResend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Resend Email",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
