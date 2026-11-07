package com.kafkasl.phonewhisper

object ErrorMessages {
    const val NO_TRANSCRIPT_RETURNED = "No transcript returned"

    const val CODEX_AUTH_UNAVAILABLE =
        "Авторизация Codex на сервере недоступна. Проверьте вход в Codex и доступ к токену."
    const val BRIDGE_AUTH_INVALID =
        "Неверный ключ подключения к серверу распознавания. Проверьте настройки Phone Whisper."

    fun isCodexAuthError(error: String?): Boolean {
        val lower = error.orEmpty().lowercase()
        return (lower.contains("codex") && (
            lower.contains("access token") ||
            lower.contains("token refresh") ||
            lower.contains("authorization") ||
            lower.contains("authentication") ||
            lower.contains("auth unavailable") ||
            lower.contains("авторизац")
        )) || lower.contains("codex_auth_unavailable")
    }

    fun transcription(error: String?, statusCode: Int?): String {
        val raw = error.orEmpty()
        val lower = raw.lowercase()

        return when {
            isCodexAuthError(raw) ->
                CODEX_AUTH_UNAVAILABLE
            statusCode == 401 || lower.contains("unauthorized") || lower.contains("incorrect api key") ->
                BRIDGE_AUTH_INVALID
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
