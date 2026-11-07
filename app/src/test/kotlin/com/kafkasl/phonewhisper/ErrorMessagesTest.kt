package com.kafkasl.phonewhisper

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    @Test fun `distinguishes invalid bridge key`() {
        assertEquals(
            ErrorMessages.BRIDGE_AUTH_INVALID,
            ErrorMessages.transcription("Unauthorized", 401)
        )
    }

    @Test fun `maps missing Codex auth file token before generic 401`() {
        assertEquals(
            ErrorMessages.CODEX_AUTH_UNAVAILABLE,
            ErrorMessages.transcription(
                "No Codex access token found in /home/user/CLIProxyAPI/auths/codex.json",
                401
            )
        )
    }

    @Test fun `maps explicit Codex authentication errors`() {
        assertEquals(
            ErrorMessages.CODEX_AUTH_UNAVAILABLE,
            ErrorMessages.transcription("Codex authentication unavailable", 424)
        )
    }

    @Test fun `maps Codex token refresh problems`() {
        assertEquals(
            ErrorMessages.CODEX_AUTH_UNAVAILABLE,
            ErrorMessages.transcription("Codex token refresh failed with HTTP 401", 401)
        )
    }

    @Test fun `maps network failures`() {
        assertEquals(
            "Network error",
            ErrorMessages.transcription("Unable to resolve host whisper.example", null)
        )
    }

    @Test fun `maps server errors`() {
        assertEquals(
            "Transcription server error",
            ErrorMessages.transcription("bad gateway", 502)
        )
    }

    @Test fun `maps empty transcript`() {
        assertEquals(
            ErrorMessages.NO_TRANSCRIPT_RETURNED,
            ErrorMessages.transcription("empty transcript", 200)
        )
    }
}
