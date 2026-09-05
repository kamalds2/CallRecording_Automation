package com.autocall.mailrecorder.recording

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
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

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentOutputFile: File? = null

    @Volatile
    private var recording = false

    // 16 kHz HD Voice sampling rate (standard telecom voice frequency, highly optimized storage)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun startRecording(outputFile: File): Result<Unit> {
        return try {
            // Stop any dangling previous recording session safely
            stopRecording()

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

            val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT
                )
            } else {
                listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT
                )
            }

            var record: AudioRecord? = null
            for (source in sources) {
                try {
                    val r = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (r.state == AudioRecord.STATE_INITIALIZED) {
                        record = r
                        Log.d("AudioRecordEngine", "Initialized AudioRecord with source: $source at $sampleRate Hz")
                        break
                    } else {
                        r.release()
                    }
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "Source $source failed: ${e.message}")
                }
            }

            if (record == null) {
                return Result.failure(IllegalStateException("Failed to initialize any AudioRecord source"))
            }

            record.startRecording()
            audioRecord = record
            recording = true

            recordingThread = Thread {
                val data = ByteArray(bufferSize)
                try {
                    FileOutputStream(outputFile).use { out ->
                        out.write(ByteArray(44)) // 44-byte WAV header placeholder

                        while (recording && !Thread.currentThread().isInterrupted) {
                            val read = record.read(data, 0, bufferSize)
                            if (read > 0) {
                                // 4.5x soft gain booster for clear incoming & outgoing speech
                                for (i in 0 until read - 1 step 2) {
                                    val low = data[i].toInt() and 0xFF
                                    val high = data[i + 1].toInt()
                                    val sample = (high shl 8) or low

                                    var amplified = (sample * 4.5f).toInt()
                                    if (amplified > 32767) amplified = 32767
                                    if (amplified < -32768) amplified = -32768

                                    data[i] = (amplified and 0xFF).toByte()
                                    data[i + 1] = ((amplified shr 8) and 0xFF).toByte()
                                }
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

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AudioRecordEngine", "Failed to start AudioRecord", e)
            recording = false
            audioRecord?.release()
            audioRecord = null
            Result.failure(e)
        }
    }

    override fun stopRecording(): Result<File> {
        recording = false
        return try {
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                Log.w("AudioRecordEngine", "Error stopping audioRecord", e)
            }
            audioRecord?.release()
            audioRecord = null

            recordingThread?.interrupt()
            recordingThread?.join(1000)
            recordingThread = null

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
        } finally {
            recording = false
            audioRecord?.release()
            audioRecord = null
            recordingThread = null
        }
    }

    override fun isRecording(): Boolean = recording

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
                    // Remove 0-byte or corrupt temporary files older than 1 hour
                    if (file.isFile && (file.length() == 0L || System.currentTimeMillis() - file.lastModified() > 86400000)) {
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
