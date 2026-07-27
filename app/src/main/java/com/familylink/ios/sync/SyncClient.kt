package com.familylink.ios.sync

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Firebase Realtime Database client over plain HTTPS — no Firebase SDK and no
 * google-services.json needed, so the app stays buildable for anyone.
 *
 *  - write/read: normal REST calls on `<db>/<path>.json`
 *  - real time:  the same URL with `Accept: text/event-stream` keeps an open connection and
 *                pushes every change immediately (Server-Sent Events).
 *
 * Set up once: create a free Firebase project, enable Realtime Database, and paste its URL
 * during setup (see README).
 */
class SyncClient(private val databaseUrl: String) {

    private fun url(path: String) = URL(databaseUrl.trimEnd('/') + "/" + path.trim('/') + ".json")

    /** Last transport error, so the UI can explain why nothing arrives. */
    var lastError: String? = null
        private set

    /** Write (replace) the JSON at [path]. Returns true on success. */
    fun put(path: String, body: JSONObject): Boolean = try {
        val conn = (url(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        val code = conn.responseCode
        val ok = code in 200..299
        lastError = if (ok) null else {
            // Read the server's explanation — this is where Firebase reports rejected keys.
            val detail = runCatching {
                conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
            }.getOrNull().orEmpty()
            "HTTP $code $detail".trim()
        }
        conn.disconnect()
        ok
    } catch (t: Throwable) {
        lastError = t.message ?: t.javaClass.simpleName
        false
    }

    /** Delete the node at [path]. */
    fun delete(path: String): Boolean = try {
        val conn = (url(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    } catch (_: Throwable) {
        false
    }

    /** Read the JSON object at [path], or null if missing/unreachable. */
    fun get(path: String): JSONObject? = try {
        val conn = (url(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val result = if (conn.responseCode in 200..299) {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank() || text == "null") null else JSONObject(text)
        } else null
        conn.disconnect()
        result
    } catch (_: Throwable) {
        null
    }

    /**
     * Open a real-time SSE stream on [path]. Blocks the calling thread and invokes [onData]
     * with the payload of every `put`/`patch` event until [shouldStop] returns true or the
     * connection drops (the caller reconnects).
     */
    fun stream(path: String, shouldStop: () -> Boolean, onData: (JSONObject) -> Unit) {
        var conn: HttpURLConnection? = null
        try {
            conn = (url(path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 0 // keep the stream open
                setRequestProperty("Accept", "text/event-stream")
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) return

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            var event: String? = null
            while (!shouldStop()) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val raw = line.removePrefix("data:").trim()
                        if ((event == "put" || event == "patch") && raw.isNotBlank() && raw != "null") {
                            runCatching {
                                val payload = JSONObject(raw)
                                // {"path":"/","data":{...}} — we only care about the data blob.
                                payload.optJSONObject("data")?.let(onData)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Caller retries with backoff.
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        fun familyPath(familyId: String) = "families/$familyId"
        fun configPath(familyId: String) = "${familyPath(familyId)}/config"
        fun statusPath(familyId: String) = "${familyPath(familyId)}/status"
    }
}
