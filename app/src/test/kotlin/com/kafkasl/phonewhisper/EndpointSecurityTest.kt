package com.kafkasl.phonewhisper

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointSecurityTest {
    @Test fun `upgrades legacy Whisper endpoint`() {
        assertEquals(
            "https://whisper.webuirenat.duckdns.org/v1",
            EndpointSecurity.normalize("http://whisper.webuirenat.duckdns.org/v1")
        )
    }

    @Test fun `upgrades streaming endpoint too`() {
        assertEquals(
            "https://whisper.webuirenat.duckdns.org/v1/audio/transcriptions/stream",
            EndpointSecurity.normalize("http://whisper.webuirenat.duckdns.org/v1/audio/transcriptions/stream")
        )
    }

    @Test fun `preserves unrelated custom endpoints`() {
        assertEquals(
            "http://100.68.233.33:6022/v1",
            EndpointSecurity.normalize("http://100.68.233.33:6022/v1")
        )
    }

    @Test fun `does not upgrade lookalike hosts`() {
        assertEquals(
            "http://whisper.webuirenat.duckdns.org.attacker.test/v1",
            EndpointSecurity.normalize("http://whisper.webuirenat.duckdns.org.attacker.test/v1")
        )
    }

    @Test fun `does not rewrite explicit custom ports`() {
        assertEquals(
            "http://whisper.webuirenat.duckdns.org:8080/v1",
            EndpointSecurity.normalize("http://whisper.webuirenat.duckdns.org:8080/v1")
        )
    }

    @Test fun `preserves valid HTTPS endpoint`() {
        assertEquals(
            "https://whisper.webuirenat.duckdns.org/v1",
            EndpointSecurity.normalize("https://whisper.webuirenat.duckdns.org/v1")
        )
    }
}
