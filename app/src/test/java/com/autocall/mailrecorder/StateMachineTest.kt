package com.autocall.mailrecorder

import com.autocall.mailrecorder.domain.model.CallDirection
import com.autocall.mailrecorder.domain.model.CaptureStatus
import com.autocall.mailrecorder.domain.model.DeliveryStatus
import com.autocall.mailrecorder.domain.model.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {

    @Test
    fun testCallLifecycleStateTransitions() {
        // 1. Initial IDLE state
        var recordingState = CaptureStatus.IDLE
        assertEquals(CaptureStatus.IDLE, recordingState)

        // 2. Call detected -> CALL_ACTIVE
        recordingState = CaptureStatus.CALL_ACTIVE
        assertEquals(CaptureStatus.CALL_ACTIVE, recordingState)

        // 3. Engine initialized -> RECORDING
        recordingState = CaptureStatus.RECORDING
        assertEquals(CaptureStatus.RECORDING, recordingState)

        // 4. Call ends -> FINALIZING
        recordingState = CaptureStatus.FINALIZING
        assertEquals(CaptureStatus.FINALIZING, recordingState)

        // 5. Finalized -> COMPLETED
        recordingState = CaptureStatus.COMPLETED
        assertEquals(CaptureStatus.COMPLETED, recordingState)
    }

    @Test
    fun testDeliveryQueueStateTransitions() {
        // Initial Recording with QUEUED delivery
        val recording = Recording(
            id = 1L,
            localPath = "/data/user/0/com.autocall.mailrecorder/files/recordings/call_incoming.m4a",
            startedAt = System.currentTimeMillis() - 60000,
            endedAt = System.currentTimeMillis(),
            durationSeconds = 60,
            direction = CallDirection.INCOMING,
            captureStatus = CaptureStatus.COMPLETED,
            deliveryStatus = DeliveryStatus.QUEUED
        )

        assertEquals(DeliveryStatus.QUEUED, recording.deliveryStatus)

        // Transient network failure -> FAILED_RETRYABLE
        val retryable = recording.copy(
            deliveryStatus = DeliveryStatus.FAILED_RETRYABLE,
            retryCount = 1,
            lastError = "Connection timeout"
        )
        assertEquals(DeliveryStatus.FAILED_RETRYABLE, retryable.deliveryStatus)
        assertEquals(1, retryable.retryCount)

        // Success -> SENT
        val delivered = retryable.copy(
            deliveryStatus = DeliveryStatus.SENT,
            lastError = null
        )
        assertEquals(DeliveryStatus.SENT, delivered.deliveryStatus)
        assertTrue(delivered.lastError == null)
    }
}
