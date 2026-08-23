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
        return try {
            val audioFile = File(recording.localPath)
            if (!audioFile.exists()) {
                return Result.failure(IllegalStateException("Recording file not found on disk: ${recording.localPath}"))
            }

            val session = createSmtpSession(settings, senderPassword)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(cleanEmail(settings.senderEmail)))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(cleanEmail(settings.recipientEmail)))

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
            Result.success(messageId)
        } catch (e: Exception) {
            Log.e("SmtpDeliveryProvider", "Failed to send email via SMTP", e)
            val errorMsg = parseSmtpError(e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    override suspend fun sendTestEmail(
        settings: AppSettings,
        senderPassword: String
    ): Result<String> {
        return try {
            val session = createSmtpSession(settings, senderPassword)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(cleanEmail(settings.senderEmail)))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(cleanEmail(settings.recipientEmail)))
                subject = "AutoCall Mail Recorder – Test Email"
                setText(
                    """
                    Success!
                    
                    Your AutoCall Mail Recorder email configuration is working properly.
                    
                    Sender: ${settings.senderEmail}
                    Host: ${settings.senderHost}:${settings.senderPort}
                    TLS Enabled: ${settings.useTls}
                    Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
                    """.trimIndent()
                )
                sentDate = Date()
            }

            Transport.send(message)
            val messageId = message.messageID ?: "TEST_OK_${System.currentTimeMillis()}"
            Result.success(messageId)
        } catch (e: Exception) {
            Log.e("SmtpDeliveryProvider", "Test email failed", e)
            val errorMsg = parseSmtpError(e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    private fun parseSmtpError(e: Exception): String {
        val rawMsg = e.message ?: e.cause?.message ?: "Unknown error"
        return when {
            rawMsg.contains("535", ignoreCase = true) || rawMsg.contains("Authentication", ignoreCase = true) || rawMsg.contains("Username and Password not accepted", ignoreCase = true) -> {
                "Authentication Failed (535): Google rejected the login credentials. Ensure you are using a 16-character Google App Password (not your regular Gmail password) from myaccount.google.com/apppasswords."
            }
            rawMsg.contains("ConnectException", ignoreCase = true) || rawMsg.contains("SocketTimeout", ignoreCase = true) || rawMsg.contains("Could not connect", ignoreCase = true) -> {
                "Connection Failed: Could not connect to SMTP server. Please check your internet connection or try switching between Port 465 (SSL) and Port 587 (TLS)."
            }
            rawMsg.contains("Invalid Addresses", ignoreCase = true) || rawMsg.contains("AddressException", ignoreCase = true) -> {
                "Invalid Email Address: Please verify that both Sender and Recipient email addresses are correctly formatted."
            }
            else -> rawMsg
        }
    }

    private fun cleanPassword(password: String): String {
        // Strip all spaces (e.g. from Google 16-character App Passwords copied as "abcd efgh ijkl mnop")
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
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.ssl.trust", "*")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")

            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.port", "465")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
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
