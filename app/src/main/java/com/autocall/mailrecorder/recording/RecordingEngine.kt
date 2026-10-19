package com.autocall.mailrecorder.recording

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.autocall.mailrecorder.domain.model.CallDirection
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface RecordingEngine {
    val name: String
    val fileExtension: String
    fun startRecording(outputFile: File): Result<Unit>
    fun stopRecording(): Result<File>
    fun isRecording(): Boolean
}

class RobustAudioRecordEngine(
    private val context: Context
) : RecordingEngine {

    override val name: String = "Robust AudioRecord (16kHz PCM WAV with Voice Booster)"
    override val fileExtension: String = "wav"

    private var currentOutputFile: File? = null

    // 16 kHz HD Voice sampling rate (standard telecom voice frequency, highly optimized storage)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    companion object {
        private val globalLock = Any()

        @Volatile
        private var globalAudioRecord: AudioRecord? = null

        @Volatile
        private var globalRecordingThread: Thread? = null

        @Volatile
        private var isGloballyRecording = false

        private var globalAec: AcousticEchoCanceler? = null
        private var globalNs: NoiseSuppressor? = null

        fun forceStopAll() {
            synchronized(globalLock) {
                isGloballyRecording = false
                try {
                    globalAudioRecord?.stop()
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "forceStopAll: audioRecord stop error", e)
                }
                try {
                    globalAudioRecord?.release()
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "forceStopAll: audioRecord release error", e)
                }
                globalAudioRecord = null

                try {
                    globalAec?.release()
                } catch (ignored: Exception) {}
                globalAec = null

                try {
                    globalNs?.release()
                } catch (ignored: Exception) {}
                globalNs = null

                globalRecordingThread?.interrupt()
                globalRecordingThread = null
            }
        }
    }

    override fun startRecording(outputFile: File): Result<Unit> {
        return synchronized(globalLock) {
            try {
                // Force stop any dangling or previous recording thread immediately
                forceStopAll()

                currentOutputFile = outputFile

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.let {
                    try {
                        it.isMicrophoneMute = false
                        val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                        it.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
                    } catch (e: Exception) {
                        Log.w("AudioRecordEngine", "AudioManager volume setup: ${e.message}")
                    }
                }

                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

                // Prioritize sources capable of capturing both call parties:
                // 1. VOICE_CALL (4) - Direct 2-way call audio from modem/baseband where supported by hardware/OEM
                // 2. VOICE_COMMUNICATION (7) - VoIP/Telephony bidirectional communication
                // 3. UNPROCESSED (9) - Pure microphone without hardware AEC/NS cancelling the earpiece
                // 4. MIC (1) - Standard hardware microphone
                // 5. DEFAULT (0) - System default
                // 6. VOICE_RECOGNITION (6) - Last resort fallback
                val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    listOf(
                        MediaRecorder.AudioSource.VOICE_CALL,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.UNPROCESSED,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.DEFAULT,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                    )
                } else {
                    listOf(
                        MediaRecorder.AudioSource.VOICE_CALL,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.DEFAULT,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                    )
                }

                var record: AudioRecord? = null
                var activeSource: Int = -1
                for (source in sources) {
                    try {
                        val r = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                        if (r.state == AudioRecord.STATE_INITIALIZED) {
                            try {
                                r.startRecording()
                                record = r
                                activeSource = source
                                Log.d("AudioRecordEngine", "Initialized and started AudioRecord with source: $source at $sampleRate Hz")
                                break
                            } catch (startEx: Throwable) {
                                Log.w("AudioRecordEngine", "Source $source startRecording failed: ${startEx.message}")
                                r.release()
                            }
                        } else {
                            r.release()
                        }
                    } catch (e: Throwable) {
                        Log.w("AudioRecordEngine", "Source $source failed: ${e.message}")
                    }
                }

                if (record == null) {
                    return@synchronized Result.failure(IllegalStateException("Failed to initialize any AudioRecord source"))
                }

                // If using acoustic mic capture, disable Acoustic Echo Canceler and Noise Suppressor
                // so the phone DSP does not actively subtract/erase the other party's voice
                if (activeSource != MediaRecorder.AudioSource.VOICE_CALL) {
                    try {
                        if (AcousticEchoCanceler.isAvailable()) {
                            globalAec = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                                enabled = false
                            }
                            Log.d("AudioRecordEngine", "Explicitly disabled AcousticEchoCanceler")
                        }
                    } catch (e: Throwable) {
                        Log.w("AudioRecordEngine", "AEC disable note: ${e.message}")
                    }

                    try {
                        if (NoiseSuppressor.isAvailable()) {
                            globalNs = NoiseSuppressor.create(record.audioSessionId)?.apply {
                                enabled = false
                            }
                            Log.d("AudioRecordEngine", "Explicitly disabled NoiseSuppressor")
                        }
                    } catch (e: Throwable) {
                        Log.w("AudioRecordEngine", "NS disable note: ${e.message}")
                    }
                }

                globalAudioRecord = record
                isGloballyRecording = true

                val thread = Thread {
                    val data = ByteArray(bufferSize)
                    try {
                        FileOutputStream(outputFile).use { out ->
                            out.write(ByteArray(44)) // 44-byte WAV header placeholder

                            while (isGloballyRecording && !Thread.currentThread().isInterrupted) {
                                val read = record.read(data, 0, bufferSize)
                                if (read > 0) {
                                    applyDynamicVoiceBoost(data, read)
                                    out.write(data, 0, read)
                                }
                            }
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.w("AudioRecordEngine", "Recording thread stream error", e)
                    }
                }.apply {
                    isDaemon = true
                    name = "AudioRecordWorkerThread"
                    start()
                }

                globalRecordingThread = thread
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("AudioRecordEngine", "Failed to start AudioRecord", e)
                forceStopAll()
                Result.failure(e)
            }
        }
    }

    /**
     * Intelligent Dual-Band Voice Booster & Soft-Knee Dynamic Range Compressor
     *
     * - Remote caller audio from earpiece (very low amplitude < 1500) gets up to 16x gain,
     *   making the other party's voice punchy, loud, and crystal clear.
     * - Local user's speech right into the bottom microphone (amplitude > 8000) gets
     *   mild 1.3x gain to stay natural without distortion.
     * - Soft-knee limiting ensures zero digital clipping or harshness.
     */
    private fun applyDynamicVoiceBoost(data: ByteArray, read: Int) {
        for (i in 0 until read - 1 step 2) {
            val low = data[i].toInt() and 0xFF
            val high = data[i + 1].toInt()
            val sample = (high shl 8) or low
            val absVal = kotlin.math.abs(sample)

            val gain = when {
                absVal < 600 -> 16.0f     // Extremely faint remote voice: 16x boost
                absVal < 1500 -> 12.0f    // Quiet remote voice: 12x boost
                absVal < 4000 -> 6.0f     // Moderate sound: 6x boost
                absVal < 8000 -> 3.0f     // Normal speech: 3x boost
                else -> 1.3f              // Loud local voice: 1.3x boost
            }

            var amplified = (sample * gain).toInt()

            // Soft-knee limiting / smooth saturation curve
            if (amplified > 31500) {
                amplified = 31500 + ((amplified - 31500) / 4).coerceAtMost(1267)
            } else if (amplified < -31500) {
                amplified = -31500 + ((amplified + 31500) / 4).coerceAtLeast(-1268)
            }

            data[i] = (amplified and 0xFF).toByte()
            data[i + 1] = ((amplified shr 8) and 0xFF).toByte()
        }
    }

    override fun stopRecording(): Result<File> {
        return synchronized(globalLock) {
            try {
                forceStopAll()

                currentOutputFile?.let {
                    updateWavHeader(it)
                    if (it.exists() && it.length() > 44) {
                        Result.success(it)
                    } else {
                        Result.failure(IllegalStateException("Recording file is empty"))
                    }
                } ?: Result.failure(IllegalStateException("No output file"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun isRecording(): Boolean = isGloballyRecording

    private fun updateWavHeader(file: File) {
        if (!file.exists() || file.length() < 44) return
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (16 * sampleRate * channels / 8).toLong()

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                val header = ByteArray(44)
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = (totalDataLen shr 8 and 0xff).toByte()
                header[6] = (totalDataLen shr 16 and 0xff).toByte()
                header[7] = (totalDataLen shr 24 and 0xff).toByte()
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                header[16] = 16 // SubChunk1Size (16 for PCM)
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1 // AudioFormat (1 for PCM)
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = (sampleRate shr 8 and 0xff).toByte()
                header[26] = (sampleRate shr 16 and 0xff).toByte()
                header[27] = (sampleRate shr 24 and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = (byteRate shr 8 and 0xff).toByte()
                header[30] = (byteRate shr 16 and 0xff).toByte()
                header[31] = (byteRate shr 24 and 0xff).toByte()
                header[32] = (channels * 16 / 8).toByte() // BlockAlign
                header[33] = 0
                header[34] = 16 // BitsPerSample (16 bit)
                header[35] = 0
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = (totalAudioLen shr 8 and 0xff).toByte()
                header[42] = (totalAudioLen shr 16 and 0xff).toByte()
                header[43] = (totalAudioLen shr 24 and 0xff).toByte()
                raf.write(header, 0, 44)
            }
        } catch (e: Exception) {
            Log.w("AudioRecordEngine", "Failed to update WAV header", e)
        }
    }
}

object RecordingEngineFactory {
    fun createEngine(context: Context): RecordingEngine {
        return RobustAudioRecordEngine(context)
    }

    fun generateRecordingFile(context: Context, direction: CallDirection, extension: String): File {
        val recordingsDir = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = timeFormat.format(Date())
        val dirName = direction.name.lowercase()
        return File(recordingsDir, "call_${dirName}_$timestamp.$extension")
    }

    fun cleanupOrphanedRecordings(context: Context) {
        try {
            val recordingsDir = File(context.filesDir, "recordings")
            if (recordingsDir.exists() && recordingsDir.isDirectory) {
                recordingsDir.listFiles()?.forEach { file ->
                    // Remove 0-byte or corrupt temporary files older than 3 minutes
                    if (file.isFile && (file.length() == 0L || System.currentTimeMillis() - file.lastModified() > 180000)) {
                        file.delete()
                    }
                }
            }
            // Clear JavaMail / cacheDir temporary files
            context.cacheDir?.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.startsWith("javamail") || file.name.endsWith(".tmp"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w("RecordingEngineFactory", "Cleanup error", e)
        }
    }
}
