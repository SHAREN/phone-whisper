package com.kafkasl.phonewhisper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TranscriptionHistoryEntry(
    val id: String,
    val text: String,
    val createdAtMs: Long
)

object TranscriptionHistoryStore {
    private const val PREFS_NAME = "phonewhisper_transcription_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100
    private const val MAX_TOTAL_CHARS = 500_000

    @Synchronized
    fun add(context: Context, text: String): TranscriptionHistoryEntry? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null

        val entry = TranscriptionHistoryEntry(
            id = UUID.randomUUID().toString(),
            text = normalized,
            createdAtMs = System.currentTimeMillis()
        )
        val retained = ArrayList<TranscriptionHistoryEntry>(MAX_ENTRIES)
        retained += entry

        var totalChars = normalized.length
        for (previous in read(context)) {
            if (retained.size >= MAX_ENTRIES) break
            if (totalChars + previous.text.length > MAX_TOTAL_CHARS) break
            retained += previous
            totalChars += previous.text.length
        }

        write(context, retained)
        return entry
    }

    @Synchronized
    fun read(context: Context): List<TranscriptionHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
            ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList(array.length().coerceAtMost(MAX_ENTRIES)) {
                for (index in 0 until array.length().coerceAtMost(MAX_ENTRIES)) {
                    val item = array.optJSONObject(index) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isEmpty()) continue
                    add(
                        TranscriptionHistoryEntry(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            createdAtMs = item.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENTRIES)
            .apply()
    }

    private fun write(context: Context, entries: List<TranscriptionHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("text", entry.text)
                    .put("createdAtMs", entry.createdAtMs)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}
