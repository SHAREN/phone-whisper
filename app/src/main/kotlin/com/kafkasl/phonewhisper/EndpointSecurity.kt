package com.kafkasl.phonewhisper

import android.content.SharedPreferences
import java.net.URI

/**
 * Upgrade the known legacy Phone Whisper endpoint without changing unrelated
 * user-configured providers (including private HTTP endpoints).
 */
object EndpointSecurity {
    private const val OFFICIAL_HOST = "whisper.webuirenat.duckdns.org"
    private const val KEY = "transcription_base_url"

    fun normalize(input: String): String {
        val raw = input.trim()
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return raw
        }

        if (!uri.scheme.equals("http", ignoreCase = true) ||
            !uri.host.equals(OFFICIAL_HOST, ignoreCase = true) ||
            uri.port != -1 ||
            uri.rawUserInfo != null
        ) return raw

        return "https" + raw.substring(raw.indexOf(':'))
    }

    fun migrate(prefs: SharedPreferences) {
        val old = prefs.getString(KEY, null) ?: return
        val secure = normalize(old)
        if (secure != old) prefs.edit().putString(KEY, secure).apply()
    }
}
