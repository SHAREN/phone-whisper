package com.kafkasl.phonewhisper

import android.content.Context
import android.os.SystemClock
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object TranscriberClient {
    data class Result(
        val text: String?,
        val error: String?,
        val statusCode: Int? = null,
        val elapsedMs: Long? = null
    )

    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val TRANSCRIPTIONS_PATH = "/audio/transcriptions"
    private const val TAG = "PhoneWhisper"

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val IO_TIMEOUT_SECONDS = 120L
    private const val CALL_TIMEOUT_SECONDS = 150L

    private val client = createHttpClient()
    private val streamingClient = createStreamingHttpClient()
    @Volatile private var diagnosticContext: Context? = null

    fun initializeDiagnostics(context: Context) {
        diagnosticContext = context.applicationContext
    }

    internal fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .eventListenerFactory { call ->
            TimingEventListener(call.request().tag(String::class.java) ?: "unknown")
        }
        .build()

    internal fun createStreamingHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .eventListenerFactory { call ->
            TimingEventListener(call.request().tag(String::class.java) ?: "unknown")
        }
        .build()

    fun transcriptionUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
        return when {
            base.endsWith(TRANSCRIPTIONS_PATH) -> base
            base.endsWith("/v1") -> "$base$TRANSCRIPTIONS_PATH"
            else -> "$base/v1$TRANSCRIPTIONS_PATH"
        }
    }

    fun streamingTranscriptionUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.endsWith("$TRANSCRIPTIONS_PATH/stream")) return base
        return "${transcriptionUrl(baseUrl).trimEnd('/')}/stream"
    }

    fun parseResponse(json: String): Result = try {
        val obj = JSONObject(json)
        when {
            obj.has("text") -> Result(obj.getString("text"), null)
            obj.has("error") -> {
                val error = obj.get("error")
                val message = if (error is JSONObject) {
                    error.optString("message", error.toString())
                } else {
                    error.toString()
                }
                Result(null, message)
            }
            else -> Result(null, "Unknown response")
        }
    } catch (e: Exception) {
        Result(null, e.message ?: "Parse error")
    }

    fun transcribe(
        audioData: ByteArray,
        mimeType: String,
        fileName: String,
        apiKey: String,
        baseUrl: String = "",
        requestId: String = "unknown",
        callback: (Result) -> Unit
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        log(requestId, "client_build_start", "audioBytes=${audioData.size} mime=$mimeType file=$fileName hasToken=${apiKey.isNotBlank()} baseUrl=${baseUrl.ifBlank { DEFAULT_BASE_URL }}")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("file", fileName, audioData.toRequestBody(mimeType.toMediaType()))
            .build()

        val requestBuilder = try {
            Request.Builder()
                .url(transcriptionUrl(baseUrl))
                .tag(String::class.java, requestId)
                .post(body)
        } catch (e: IllegalArgumentException) {
            log(requestId, "client_invalid_url", e.message ?: "invalid")
            callback(Result(null, "Invalid transcription URL: ${e.message}"))
            return
        }

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        log(requestId, "client_enqueue", "url=${request.url}")

        client.newCall(request).enqueue(resultCallback(startedAt, requestId, callback))
    }

    fun startStreamingTranscription(
        mimeType: String,
        fileName: String,
        apiKey: String,
        baseUrl: String,
        requestId: String = "unknown",
        callback: (Result) -> Unit
    ): StreamingUpload? {
        if (baseUrl.isBlank()) {
            log(requestId, "stream_client_skipped", "reason=blank_base_url")
            return null
        }

        val startedAt = SystemClock.elapsedRealtime()
        val body = StreamingMultipartRequestBody(
            mimeType = mimeType,
            fileName = fileName,
            model = "whisper-1"
        )
        val requestBuilder = try {
            Request.Builder()
                .url(streamingTranscriptionUrl(baseUrl))
                .tag(String::class.java, requestId)
                .post(body)
        } catch (e: IllegalArgumentException) {
            log(requestId, "stream_client_invalid_url", e.message ?: "invalid")
            callback(Result(null, "Invalid streaming transcription URL: ${e.message}"))
            return null
        }

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val call = streamingClient.newCall(request)
        val upload = StreamingUpload(
            body = body,
            call = call,
            networkCallback = resultCallback(startedAt, requestId, callback, stagePrefix = "stream_client")
        )
        log(requestId, "stream_client_prepared", "url=${request.url} mime=$mimeType file=$fileName")
        return upload
    }

    private fun resultCallback(
        startedAt: Long,
        requestId: String,
        callback: (Result) -> Unit,
        stagePrefix: String = "client"
    ) = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            log(requestId, "${stagePrefix}_failure", "elapsedMs=$elapsedMs error=${e.message}")
            callback(Result(null, e.message, elapsedMs = elapsedMs))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                val raw = it.body?.string() ?: ""
                log(requestId, "${stagePrefix}_response_body", "elapsedMs=$elapsedMs status=${it.code} bytes=${raw.length}")
                val parsed = parseResponse(raw)
                callback(parsed.copy(statusCode = it.code, elapsedMs = elapsedMs))
            }
        }
    }

    class StreamingUpload internal constructor(
        private val body: StreamingMultipartRequestBody,
        private val call: Call,
        private val networkCallback: Callback
    ) {
        private val started = AtomicBoolean(false)

        fun start() {
            if (started.compareAndSet(false, true)) {
                call.enqueue(networkCallback)
            }
        }

        fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
            body.offer(bytes, offset, length)
        }

        fun finish() {
            body.finish()
        }

        fun cancel() {
            body.cancel()
            call.cancel()
        }
    }

    internal class StreamingMultipartRequestBody(
        private val mimeType: String,
        private val fileName: String,
        private val model: String
    ) : RequestBody() {
        private val boundary = "----PhoneWhisperStream${UUID.randomUUID().toString().replace("-", "")}"
        private val queue = LinkedBlockingQueue<ByteArray>()
        private val closed = AtomicBoolean(false)

        override fun contentType(): MediaType = "multipart/form-data; boundary=$boundary".toMediaType()

        override fun contentLength(): Long = -1L

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("--$boundary\r\n")
            sink.writeUtf8("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            sink.writeUtf8(model)
            sink.writeUtf8("\r\n--$boundary\r\n")
            sink.writeUtf8("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            sink.writeUtf8("Content-Type: $mimeType\r\n\r\n")
            sink.flush()

            while (true) {
                val chunk = queue.take()
                if (chunk.isEmpty()) break
                sink.write(chunk)
                sink.flush()
            }

            sink.writeUtf8("\r\n--$boundary--\r\n")
            sink.flush()
        }

        fun offer(bytes: ByteArray, offset: Int, length: Int) {
            if (length <= 0 || closed.get()) return
            require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
            queue.put(bytes.copyOfRange(offset, offset + length))
        }

        fun finish() {
            if (closed.compareAndSet(false, true)) {
                queue.offer(ByteArray(0))
            }
        }

        fun cancel() {
            finish()
        }
    }

    private fun log(requestId: String, stage: String, message: String = "") {
        Log.i(TAG, "trace=$requestId stage=$stage $message")
        persistDiagnostic(requestId, stage, message)
    }

    private fun persistDiagnostic(requestId: String, stage: String, message: String) {
        diagnosticContext?.let { context ->
            DiagnosticTraceStore.append(context, requestId, stage, message)
        }
    }

    private class TimingEventListener(private val requestId: String) : EventListener() {
        private val startedAt = SystemClock.elapsedRealtime()

        override fun dnsStart(call: Call, domainName: String) {
            log("dns_start", "elapsedMs=${elapsed()} host=$domainName")
        }

        override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
            log("connect_start", "elapsedMs=${elapsed()} address=${inetSocketAddress.hostString}:${inetSocketAddress.port}")
        }

        override fun secureConnectStart(call: Call) {
            log("tls_start", "elapsedMs=${elapsed()}")
        }

        override fun requestHeadersStart(call: Call) {
            log("request_headers_start", "elapsedMs=${elapsed()}")
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            log("request_body_end", "elapsedMs=${elapsed()} bytes=$byteCount")
        }

        override fun responseHeadersStart(call: Call) {
            log("response_headers_start", "elapsedMs=${elapsed()}")
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            log("response_body_end", "elapsedMs=${elapsed()} bytes=$byteCount")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            log("call_failed", "elapsedMs=${elapsed()} error=${ioe.message}")
        }

        private fun elapsed() = SystemClock.elapsedRealtime() - startedAt
        private fun log(stage: String, message: String = "") {
            val persistedStage = "http_$stage"
            Log.i(TAG, "trace=$requestId stage=$persistedStage $message")
            persistDiagnostic(requestId, persistedStage, message)
        }
    }
}
