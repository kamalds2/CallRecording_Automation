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
import kotlin.math.abs

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

    override val name: String = "Robust AudioRecord (PCM WAV with Digital Voice Booster)"
    override val fileExtension: String = "wav"

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentOutputFile: File? = null
    private var recording = false

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun startRecording(outputFile: File): Result<Unit> {
        return try {
            currentOutputFile = outputFile

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                try {
                    it.isMicrophoneMute = false
                    // Keep in-call volume boosted so other party voice is audible
                    val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    it.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "AudioManager volume setup note: ${e.message}")
                }
            }

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(8192)

            // Prioritize sources that DO NOT mute the caller voice with echo cancellation
            val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.DEFAULT
                )
            } else {
                listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.DEFAULT
                )
            }

            var record: AudioRecord? = null
            var selectedSource = MediaRecorder.AudioSource.MIC

            for (source in sources) {
                try {
                    val r = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (r.state == AudioRecord.STATE_INITIALIZED) {
                        record = r
                        selectedSource = source
                        Log.d("AudioRecordEngine", "Successfully initialized AudioRecord with source: $source")
                        break
                    } else {
                        r.release()
                    }
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "Source $source failed to initialize: ${e.message}")
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
                FileOutputStream(outputFile).use { out ->
                    out.write(ByteArray(44)) // 44-byte placeholder for WAV header

                    while (recording) {
                        val read = record.read(data, 0, bufferSize)
                        if (read > 0) {
                            // Apply intelligent software gain amplification (5.0x gain)
                            // This ensures the opposite person's voice from the earpiece/speaker is clearly amplified!
                            for (i in 0 until read - 1 step 2) {
                                val low = data[i].toInt() and 0xFF
                                val high = data[i + 1].toInt()
                                val sample = (high shl 8) or low

                                // 5.0x amplification multiplier with soft limiter
                                var amplified = (sample * 5.0f).toInt()
                                if (amplified > 32767) amplified = 32767
                                if (amplified < -32768) amplified = -32768

                                data[i] = (amplified and 0xFF).toByte()
                                data[i + 1] = ((amplified shr 8) and 0xFF).toByte()
                            }
                            out.write(data, 0, read)
                        }
                    }
                }
            }
            recordingThread?.start()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AudioRecordEngine", "Failed to start AudioRecord", e)
            audioRecord?.release()
            audioRecord = null
            recording = false
            Result.failure(e)
        }
    }

    override fun stopRecording(): Result<File> {
        recording = false
        return try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(2500)
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
            audioRecord?.release()
            audioRecord = null
            recording = false
        }
    }

    override fun isRecording(): Boolean = recording

    private fun updateWavHeader(file: File) {
        if (!file.exists() || file.length() < 44) return
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (16 * sampleRate * channels / 8).toLong()

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
    }
}

class MediaRecorderEngine(
    private val context: Context
) : RecordingEngine {

    override val name: String = "MediaRecorder (M4A AAC)"
    override val fileExtension: String = "m4a"

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recording = false

    @Suppress("DEPRECATION")
    override fun startRecording(outputFile: File): Result<Unit> {
        currentOutputFile = outputFile

        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        for (source in sourcesToTry) {
            val result = tryStartRecorder(outputFile, source)
            if (result.isSuccess) {
                return result
            }
        }

        return Result.failure(IllegalStateException("Failed to initialize MediaRecorder"))
    }

    @Suppress("DEPRECATION")
    private fun tryStartRecorder(outputFile: File, source: Int): Result<Unit> {
        return try {
            mediaRecorder?.release()
            mediaRecorder = null

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.setAudioSource(source)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)

            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recording = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("MediaRecorderEngine", "Source $source failed: ${e.message}")
            mediaRecorder?.release()
            mediaRecorder = null
            recording = false
            Result.failure(e)
        }
    }

    override fun stopRecording(): Result<File> {
        return try {
            if (recording && mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                } catch (e: Exception) {
                    Log.w("MediaRecorderEngine", "Stop exception", e)
                }
                mediaRecorder?.release()
                mediaRecorder = null
                recording = false

                currentOutputFile?.let {
                    if (it.exists() && it.length() > 0) {
                        Result.success(it)
                    } else {
                        Result.failure(IllegalStateException("Recording file is empty"))
                    }
                } ?: Result.failure(IllegalStateException("No output file"))
            } else {
                Result.failure(IllegalStateException("Not recording"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            recording = false
        }
    }

    override fun isRecording(): Boolean = recording
}

object RecordingEngineFactory {
    fun createEngine(context: Context): RecordingEngine {
        // Robust AudioRecord engine with real-time 5x digital PCM amplification
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
}
