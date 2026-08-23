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

class AudioRecordEngine(
    private val context: Context,
    private val preferredSourceStr: String = "VOICE_RECOGNITION",
    private val gainMultiplier: Float = 2.5f
) : RecordingEngine {

    override val name: String = "Enhanced AudioRecord (PCM / WAV + Gain Amplification)"
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

            // Ensure microphone is unmuted via AudioManager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                try {
                    it.isMicrophoneMute = false
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "Could not unmute microphone: ${e.message}")
                }
            }

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(8192)

            // Resolve sources priority list
            val candidateSources = getSourceCandidates(preferredSourceStr)
            var initializedRecord: AudioRecord? = null

            for (source in candidateSources) {
                try {
                    val record = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        initializedRecord = record
                        Log.d("AudioRecordEngine", "Successfully initialized AudioRecord with source: $source")
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w("AudioRecordEngine", "Failed source $source: ${e.message}")
                }
            }

            if (initializedRecord == null) {
                return Result.failure(IllegalStateException("No AudioRecord source could be initialized."))
            }

            initializedRecord.startRecording()
            audioRecord = initializedRecord
            recording = true

            recordingThread = Thread {
                writePcmDataToWav(outputFile, initializedRecord, bufferSize, gainMultiplier)
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
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(3000)
            recordingThread = null

            currentOutputFile?.let {
                updateWavHeader(it)
                return Result.success(it)
            } ?: return Result.failure(IllegalStateException("No output file"))
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            audioRecord?.release()
            audioRecord = null
            recording = false
        }
    }

    override fun isRecording(): Boolean = recording

    private fun writePcmDataToWav(file: File, record: AudioRecord, bufferSize: Int, gain: Float) {
        val rawBuffer = ByteArray(bufferSize)
        FileOutputStream(file).use { out ->
            // write placeholder 44-byte WAV header
            out.write(ByteArray(44))

            while (recording) {
                val read = record.read(rawBuffer, 0, bufferSize)
                if (read > 0) {
                    // Apply software gain amplification to 16-bit PCM samples
                    if (gain > 1.0f) {
                        for (i in 0 until read - 1 step 2) {
                            val low = rawBuffer[i].toInt() and 0xFF
                            val high = rawBuffer[i + 1].toInt()
                            val sample = (high shl 8) or low

                            var amplified = (sample * gain).toInt()
                            // Soft clipping clamp
                            if (amplified > 32767) amplified = 32767
                            if (amplified < -32768) amplified = -32768

                            rawBuffer[i] = (amplified and 0xFF).toByte()
                            rawBuffer[i + 1] = ((amplified shr 8) and 0xFF).toByte()
                        }
                    }
                    out.write(rawBuffer, 0, read)
                }
            }
        }
    }

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
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1
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
            header[32] = (channels * 16 / 8).toByte()
            header[33] = 0
            header[34] = 16
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

    private fun getSourceCandidates(pref: String): List<Int> {
        val primary = when (pref) {
            "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            "MIC" -> MediaRecorder.AudioSource.MIC
            "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "UNPROCESSED" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

        return listOf(
            primary,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        ).distinct()
    }
}

class MediaRecorderEngine(
    private val context: Context,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION
) : RecordingEngine {

    override val name: String = "MediaRecorder (M4A / AAC)"
    override val fileExtension: String = "m4a"

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recording = false

    @Suppress("DEPRECATION")
    override fun startRecording(outputFile: File): Result<Unit> {
        currentOutputFile = outputFile
        
        val sources = listOf(
            audioSource,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        ).distinct()

        for (source in sources) {
            val res = tryStartMediaRecorder(outputFile, source)
            if (res.isSuccess) {
                return res
            }
        }

        return Result.failure(IllegalStateException("Failed to initialize MediaRecorder with any audio source"))
    }

    @Suppress("DEPRECATION")
    private fun tryStartMediaRecorder(outputFile: File, source: Int): Result<Unit> {
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
            Log.w("MediaRecorderEngine", "MediaRecorder failed with source $source: ${e.message}")
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
                currentOutputFile?.let { Result.success(it) } ?: Result.failure(IllegalStateException("No output file"))
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
    fun createEngine(
        context: Context,
        audioSource: String = "VOICE_RECOGNITION",
        gainMultiplier: Float = 2.5f
    ): RecordingEngine {
        // High-reliability AudioRecord engine with software gain amplification
        return AudioRecordEngine(context, audioSource, gainMultiplier)
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
