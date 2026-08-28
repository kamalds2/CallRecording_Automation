package com.autocall.mailrecorder.delivery

import android.util.Log
import com.autocall.mailrecorder.domain.model.AppSettings
import com.autocall.mailrecorder.domain.model.Recording
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

interface EmailDeliveryProvider {
    suspend fun sendRecording(
        recording: Recording,
        settings: AppSettings,
        senderPassword: String
    ): Result<String> // Returns provider message ID or success token

    suspend fun sendTestEmail(
        settings: AppSettings,
        senderPassword: String
    ): Result<String>
}

class SmtpDeliveryProvider : EmailDeliveryProvider {

    override suspend fun sendRecording(
        recording: Recording,
        settings: AppSettings,
        senderPassword: String
    ): Result<String> {
        val audioFile = File(recording.localPath)
        if (!audioFile.exists()) {
            return Result.failure(IllegalStateException("Recording file not found on disk: ${recording.localPath}"))
        }

        // Try primary port (587 STARTTLS), fallback to 465 (SSL) if network blocks it
        val portsToTry = listOf(587, 465)
        var lastException: Exception? = null

        for (port in portsToTry) {
            try {
                val effectiveSettings = settings.copy(senderPort = port, useTls = (port == 587))
                val session = createSmtpSession(effectiveSettings, senderPassword)
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(cleanEmail(effectiveSettings.senderEmail)))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(cleanEmail(effectiveSettings.recipientEmail)))

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(recording.startedAt))
                    val directionStr = recording.direction.name.lowercase().replaceFirstChar { it.uppercase() }
                    subject = "Call Recording – $directionStr – $dateStr"

                    val multipart = MimeMultipart()

                    // Metadata Body
                    val bodyPart = MimeBodyPart().apply {
                        val durationMin = recording.durationSeconds / 60
                        val durationSec = recording.durationSeconds % 60
                        val sizeKb = recording.fileSize / 1024
                        setText(
                            """
                            AutoCall Mail Recorder
                            ========================
                            Call Direction: ${recording.direction}
                            Timestamp: $dateStr
                            Duration: ${durationMin}m ${durationSec}s
                            File Size: ${sizeKb} KB
                            File Format: ${recording.fileFormat.uppercase()}
                            
                            This is an automated delivery from your configured AutoCall Mail Recorder application.
                            """.trimIndent()
                        )
                    }
                    multipart.addBodyPart(bodyPart)

                    // Attachment Part
                    val attachmentPart = MimeBodyPart().apply {
                        val source = FileDataSource(audioFile)
                        dataHandler = DataHandler(source)
                        fileName = audioFile.name
                    }
                    multipart.addBodyPart(attachmentPart)

                    setContent(multipart)
                    sentDate = Date()
                }

                Transport.send(message)
                val messageId = message.messageID ?: "MSG_${System.currentTimeMillis()}"
                Log.d("SmtpDeliveryProvider", "Successfully sent recording on port $port")
                return Result.success(messageId)
            } catch (e: Exception) {
                Log.w("SmtpDeliveryProvider", "Delivery attempt on port $port failed: ${e.message}")
                lastException = e
            }
        }

        val finalError = parseSmtpError(lastException ?: Exception("Unknown SMTP connection error"))
        return Result.failure(Exception(finalError, lastException))
    }

    override suspend fun sendTestEmail(
        settings: AppSettings,
        senderPassword: String
    ): Result<String> {
        val portsToTry = listOf(587, 465)
        var lastException: Exception? = null

        for (port in portsToTry) {
            try {
                val effectiveSettings = settings.copy(senderPort = port, useTls = (port == 587))
                val session = createSmtpSession(effectiveSettings, senderPassword)
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(cleanEmail(effectiveSettings.senderEmail)))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(cleanEmail(effectiveSettings.recipientEmail)))
                    subject = "AutoCall Mail Recorder – Test Email"
                    setText(
                        """
                        Success!
                        
                        Your AutoCall Mail Recorder email configuration is working properly.
                        
                        Sender: ${effectiveSettings.senderEmail}
                        Host: ${effectiveSettings.senderHost}:$port
                        Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
                        """.trimIndent()
                    )
                    sentDate = Date()
                }

                Transport.send(message)
                val messageId = message.messageID ?: "TEST_OK_${System.currentTimeMillis()}"
                return Result.success(messageId)
            } catch (e: Exception) {
                lastException = e
            }
        }

        val finalError = parseSmtpError(lastException ?: Exception("Unknown SMTP error"))
        return Result.failure(Exception(finalError, lastException))
    }

    private fun parseSmtpError(e: Exception): String {
        val rawMsg = e.message ?: e.cause?.message ?: "Unknown error"
        return when {
            rawMsg.contains("535", ignoreCase = true) || rawMsg.contains("Authentication", ignoreCase = true) || rawMsg.contains("Username and Password not accepted", ignoreCase = true) -> {
                "Authentication Failed (535): Google rejected the login credentials. Ensure your 16-character Google App Password is correct."
            }
            rawMsg.contains("ConnectException", ignoreCase = true) || rawMsg.contains("SocketTimeout", ignoreCase = true) || rawMsg.contains("Could not connect", ignoreCase = true) || rawMsg.contains("timeout", ignoreCase = true) -> {
                "Connection Failed: Could not reach SMTP server. Checked Port 587 (TLS) and Port 465 (SSL). Please check internet connection."
            }
            rawMsg.contains("Invalid Addresses", ignoreCase = true) || rawMsg.contains("AddressException", ignoreCase = true) -> {
                "Invalid Email Address: Please verify the Recipient email address."
            }
            else -> rawMsg
        }
    }

    private fun cleanPassword(password: String): String {
        return password.replace("\\s+".toRegex(), "").trim()
    }

    private fun cleanEmail(email: String): String {
        return email.trim()
    }

    private fun createSmtpSession(settings: AppSettings, rawPassword: String): Session {
        val cleanPass = cleanPassword(rawPassword)
        val cleanSender = cleanEmail(settings.senderEmail)
        val host = settings.senderHost.trim().ifBlank { "smtp.gmail.com" }
        val port = settings.senderPort

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.connectiontimeout", "25000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")
            put("mail.smtp.ssl.trust", "*")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")

            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(cleanSender, cleanPass)
            }
        })
    }
}
