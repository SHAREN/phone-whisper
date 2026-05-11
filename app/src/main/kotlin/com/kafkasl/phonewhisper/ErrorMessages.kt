package com.kafkasl.phonewhisper

object ErrorMessages {
    const val NO_TRANSCRIPT_RETURNED = "No transcript returned"

    fun transcription(error: String?, statusCode: Int?): String {
        val raw = error.orEmpty()
        val lower = raw.lowercase()

        return when {
            statusCode == 401 || lower.contains("unauthorized") || lower.contains("incorrect api key") ->
                "Wrong transcription token"
            statusCode == 429 || lower.contains("rate limit") ->
                "Transcription rate limited"
            statusCode != null && statusCode >= 500 ->
                "Transcription server error"
            lower.contains("failed to connect") ||
                lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("unable to resolve host") ||
                lower.contains("network") ->
                "Network error"
            raw.isBlank() || lower.contains("empty transcript") ->
                NO_TRANSCRIPT_RETURNED
            else -> raw.take(120)
        }
    }

    fun local(error: String?): String =
        "Local transcription failed: ${error.orEmpty().ifBlank { "unknown error" }.take(80)}"
}
