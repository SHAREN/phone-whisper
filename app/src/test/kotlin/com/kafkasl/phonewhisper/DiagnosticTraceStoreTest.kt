package com.kafkasl.phonewhisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTraceStoreTest {
    @Test
    fun `retention keeps only newest hundred sessions`() {
        val now = 10_000_000L
        val events = (0 until 105).map { index ->
            DiagnosticTraceEvent(
                timestampMs = now - index * 1_000L,
                elapsedRealtimeMs = index.toLong(),
                traceId = "trace-$index",
                stage = "record_start_requested",
                details = ""
            )
        }

        val retained = DiagnosticTraceStore.retainRecentEvents(
            events = events,
            nowMs = now,
            maxSessions = 100,
            maxAgeMs = Long.MAX_VALUE / 4
        )

        assertEquals(100, retained.map { it.traceId }.distinct().size)
        assertTrue(retained.any { it.traceId == "trace-0" })
        assertFalse(retained.any { it.traceId == "trace-104" })
    }

    @Test
    fun `summary isolates android stop latency`() {
        val events = listOf(
            event(1_000, 1_000, "record_start_requested"),
            event(5_000, 5_000, "record_stop_requested", "recordDurationMs=4000"),
            event(5_010, 5_010, "media_recorder_stop_end", "durationMs=850 ok=true"),
            event(5_020, 5_020, "media_recorder_release_end", "durationMs=4"),
            event(5_030, 5_030, "stream_upload_final_pump", "durationMs=18 uploadedBytes=1000 fileBytes=1000 tailBytes=200"),
            event(5_040, 5_040, "compressed_file_read_end", "durationMs=2 audioBytes=1000"),
            event(6_400, 6_400, "transcription_result_received", "chars=42"),
            event(6_450, 6_450, "inject_end", "injected=true copyToClipboard=false")
        )
        val session = DiagnosticTraceSession("abc123", 1_000, events)

        val summary = DiagnosticTraceStore.summarize(session)

        assertEquals(4_000L, summary.recordDurationMs)
        assertEquals(850L, summary.recorderStopMs)
        assertEquals(4L, summary.recorderReleaseMs)
        assertEquals(18L, summary.finalPumpMs)
        assertEquals(2L, summary.fileReadMs)
        assertEquals(1_400L, summary.postStopToResultMs)
        assertEquals(1_450L, summary.postStopToInjectMs)
        assertFalse(summary.fallbackUsed)
        assertTrue(summary.completed)
    }

    @Test
    fun `summary marks fallback`() {
        val session = DiagnosticTraceSession(
            traceId = "fallback",
            startedAtMs = 1,
            events = listOf(
                event(1, 1, "record_stop_requested", "recordDurationMs=1"),
                event(2, 2, "stream_upload_fallback", "reason=test")
            )
        )

        assertTrue(DiagnosticTraceStore.summarize(session).fallbackUsed)
    }

    private fun event(
        timestampMs: Long,
        elapsedRealtimeMs: Long,
        stage: String,
        details: String = ""
    ) = DiagnosticTraceEvent(
        timestampMs = timestampMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
        traceId = "abc123",
        stage = stage,
        details = details
    )
}
