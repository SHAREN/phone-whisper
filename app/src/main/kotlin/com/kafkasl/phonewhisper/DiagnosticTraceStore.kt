package com.kafkasl.phonewhisper

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class DiagnosticTraceEvent(
    val timestampMs: Long,
    val elapsedRealtimeMs: Long,
    val traceId: String,
    val stage: String,
    val details: String
)

data class DiagnosticTraceSession(
    val traceId: String,
    val startedAtMs: Long,
    val events: List<DiagnosticTraceEvent>
)

data class DiagnosticTraceSummary(
    val recordDurationMs: Long?,
    val recorderStopMs: Long?,
    val recorderReleaseMs: Long?,
    val finalPumpMs: Long?,
    val fileReadMs: Long?,
    val postStopToResultMs: Long?,
    val postStopToInjectMs: Long?,
    val fallbackUsed: Boolean,
    val completed: Boolean
)

object DiagnosticTraceStore {
    private const val TAG = "PhoneWhisperDiagnostics"
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "dictation-timing.jsonl"
    private const val MAX_SESSIONS = 100
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    private const val PRUNE_EVERY_EVENTS = 100
    private const val MAX_DETAILS_CHARS = 1200

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "phone-whisper-diagnostics").apply { isDaemon = true }
    }
    private val appendedSincePrune = AtomicInteger(0)

    fun append(context: Context, traceId: String, stage: String, details: String = "") {
        if (traceId.isBlank() || stage.isBlank()) return
        val appContext = context.applicationContext
        val event = DiagnosticTraceEvent(
            timestampMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            traceId = traceId.take(64),
            stage = stage.take(96),
            details = sanitizeDetails(details)
        )
        executor.execute {
            try {
                val file = logFile(appContext)
                file.parentFile?.mkdirs()
                file.appendText(eventToJson(event).toString() + "\n", Charsets.UTF_8)
                val count = appendedSincePrune.incrementAndGet()
                if (
                    stage == "record_start_requested" ||
                    count >= PRUNE_EVERY_EVENTS ||
                    file.length() > MAX_FILE_BYTES
                ) {
                    appendedSincePrune.set(0)
                    pruneNow(appContext, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to persist diagnostic trace: ${e.message}")
            }
        }
    }

    fun readSessions(context: Context): List<DiagnosticTraceSession> {
        flushPending()
        return sessionsFromEvents(readEventsNow(context.applicationContext))
    }

    fun clear(context: Context) {
        flushPending()
        try {
            logFile(context.applicationContext).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to clear diagnostic trace: ${e.message}")
        }
    }

    fun exportText(context: Context): String = exportText(readSessions(context))

    internal fun sessionsFromEvents(events: List<DiagnosticTraceEvent>): List<DiagnosticTraceSession> =
        events.groupBy { it.traceId }
            .map { (traceId, grouped) ->
                val sorted = grouped.sortedWith(compareBy<DiagnosticTraceEvent> { it.timestampMs }.thenBy { it.elapsedRealtimeMs })
                DiagnosticTraceSession(
                    traceId = traceId,
                    startedAtMs = sorted.firstOrNull()?.timestampMs ?: 0L,
                    events = sorted
                )
            }
            .sortedByDescending { it.startedAtMs }

    internal fun retainRecentEvents(
        events: List<DiagnosticTraceEvent>,
        nowMs: Long,
        maxSessions: Int = MAX_SESSIONS,
        maxAgeMs: Long = MAX_AGE_MS
    ): List<DiagnosticTraceEvent> {
        val cutoff = nowMs - maxAgeMs
        val recent = events.filter { it.timestampMs >= cutoff }
        val keepTraceIds = recent
            .groupBy { it.traceId }
            .mapValues { (_, grouped) -> grouped.maxOfOrNull { it.timestampMs } ?: 0L }
            .entries
            .sortedByDescending { it.value }
            .take(maxSessions)
            .map { it.key }
            .toSet()
        return recent.filter { it.traceId in keepTraceIds }
            .sortedWith(compareBy<DiagnosticTraceEvent> { it.timestampMs }.thenBy { it.elapsedRealtimeMs })
    }

    fun summarize(session: DiagnosticTraceSession): DiagnosticTraceSummary {
        val stop = session.events.firstOrNull { it.stage == "record_stop_requested" }
        val result = session.events.firstOrNull { it.stage == "transcription_result_received" }
        val inject = session.events.lastOrNull { it.stage == "inject_end" }
        val recorderStop = session.events.lastOrNull { it.stage == "media_recorder_stop_end" }
        val recorderRelease = session.events.lastOrNull { it.stage == "media_recorder_release_end" }
        val finalPump = session.events.lastOrNull { it.stage == "stream_upload_final_pump" }
        val fileRead = session.events.lastOrNull { it.stage == "compressed_file_read_end" }
        return DiagnosticTraceSummary(
            recordDurationMs = detailLong(stop, "recordDurationMs"),
            recorderStopMs = detailLong(recorderStop, "durationMs"),
            recorderReleaseMs = detailLong(recorderRelease, "durationMs"),
            finalPumpMs = detailLong(finalPump, "durationMs"),
            fileReadMs = detailLong(fileRead, "durationMs"),
            postStopToResultMs = elapsedBetween(stop, result),
            postStopToInjectMs = elapsedBetween(stop, inject),
            fallbackUsed = session.events.any { it.stage.contains("fallback") },
            completed = result != null || inject != null
        )
    }

    fun formatSession(session: DiagnosticTraceSession): String {
        val summary = summarize(session)
        val baseElapsed = session.events.firstOrNull()?.elapsedRealtimeMs ?: 0L
        return buildString {
            appendLine("Phone Whisper diagnostic trace")
            appendLine("traceId=${session.traceId}")
            appendLine("startedAtMs=${session.startedAtMs}")
            appendLine("events=${session.events.size}")
            summary.recordDurationMs?.let { appendLine("recordDurationMs=$it") }
            summary.recorderStopMs?.let { appendLine("mediaRecorderStopMs=$it") }
            summary.recorderReleaseMs?.let { appendLine("mediaRecorderReleaseMs=$it") }
            summary.finalPumpMs?.let { appendLine("finalPumpMs=$it") }
            summary.fileReadMs?.let { appendLine("fileReadMs=$it") }
            summary.postStopToResultMs?.let { appendLine("stopToResultMs=$it") }
            summary.postStopToInjectMs?.let { appendLine("stopToInjectMs=$it") }
            appendLine("fallbackUsed=${summary.fallbackUsed}")
            appendLine("completed=${summary.completed}")
            appendLine()
            appendLine("timeline:")
            session.events.forEach { event ->
                val delta = (event.elapsedRealtimeMs - baseElapsed).coerceAtLeast(0L)
                append("+").append(delta).append("ms ")
                    .append(event.stage)
                if (event.details.isNotBlank()) append(" ").append(event.details)
                appendLine()
            }
        }.trimEnd()
    }

    fun exportText(sessions: List<DiagnosticTraceSession>): String = buildString {
        appendLine("Phone Whisper diagnostics")
        appendLine("sessions=${sessions.size}")
        appendLine("retention=last $MAX_SESSIONS sessions / 7 days")
        appendLine("containsAudio=false")
        appendLine("containsTranscriptText=false")
        sessions.forEachIndexed { index, session ->
            if (index > 0) appendLine("\n----------------------------------------")
            appendLine(formatSession(session))
        }
    }.trimEnd()

    private fun elapsedBetween(start: DiagnosticTraceEvent?, end: DiagnosticTraceEvent?): Long? {
        if (start == null || end == null) return null
        return (end.elapsedRealtimeMs - start.elapsedRealtimeMs).takeIf { it >= 0 }
    }

    private fun detailLong(event: DiagnosticTraceEvent?, key: String): Long? {
        if (event == null) return null
        val match = Regex("(?:^|\\s)${Regex.escape(key)}=(-?\\d+)(?:\\s|$)").find(event.details) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
    }

    private fun sanitizeDetails(details: String): String {
        var value = details
            .replace(Regex("(?i)Bearer\\s+[^\\s]+"), "Bearer ***")
            .replace(Regex("(?i)sk-[A-Za-z0-9_-]+"), "sk-***")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        if (value.length > MAX_DETAILS_CHARS) value = value.take(MAX_DETAILS_CHARS) + "..."
        return value
    }

    private fun logFile(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    private fun readEventsNow(context: Context): List<DiagnosticTraceEvent> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return try {
            file.useLines(Charsets.UTF_8) { lines ->
                lines.mapNotNull { line -> eventFromJson(line) }.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read diagnostic trace: ${e.message}")
            emptyList()
        }
    }

    private fun pruneNow(context: Context, nowMs: Long) {
        val file = logFile(context)
        if (!file.exists()) return
        val retained = retainRecentEvents(readEventsNow(context), nowMs)
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.bufferedWriter(Charsets.UTF_8).use { writer ->
            retained.forEach { event ->
                writer.append(eventToJson(event).toString()).append('\n')
            }
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun flushPending() {
        val latch = CountDownLatch(1)
        executor.execute { latch.countDown() }
        try {
            latch.await(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun eventToJson(event: DiagnosticTraceEvent): JSONObject = JSONObject()
        .put("timestampMs", event.timestampMs)
        .put("elapsedRealtimeMs", event.elapsedRealtimeMs)
        .put("traceId", event.traceId)
        .put("stage", event.stage)
        .put("details", event.details)

    private fun eventFromJson(raw: String): DiagnosticTraceEvent? = try {
        val json = JSONObject(raw)
        DiagnosticTraceEvent(
            timestampMs = json.optLong("timestampMs", 0L),
            elapsedRealtimeMs = json.optLong("elapsedRealtimeMs", 0L),
            traceId = json.optString("traceId", ""),
            stage = json.optString("stage", ""),
            details = json.optString("details", "")
        ).takeIf { it.timestampMs > 0L && it.traceId.isNotBlank() && it.stage.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
