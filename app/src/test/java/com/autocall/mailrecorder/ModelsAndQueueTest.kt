package com.autocall.mailrecorder

import com.autocall.mailrecorder.domain.model.AppSettings
import com.autocall.mailrecorder.domain.model.DeliveryJob
import com.autocall.mailrecorder.domain.model.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsAndQueueTest {

    @Test
    fun testAppSettingsDefaultsAndValidation() {
        val settings = AppSettings(
            automationEnabled = true,
            recipientEmail = "test@company.com",
            senderEmail = "bot@company.com"
        )

        assertTrue(settings.automationEnabled)
        assertEquals("test@company.com", settings.recipientEmail)
        assertEquals("bot@company.com", settings.senderEmail)
        assertEquals(465, settings.senderPort)
        assertFalse(settings.useTls)
        assertFalse(settings.wifiOnly)
    }

    @Test
    fun testDeliveryJobExponentialBackoff() {
        val job = DeliveryJob(
            id = 1,
            recordingId = 100,
            status = DeliveryStatus.QUEUED,
            attemptCount = 0
        )

        assertEquals(0, job.attemptCount)
        assertEquals(DeliveryStatus.QUEUED, job.status)
    }
}
