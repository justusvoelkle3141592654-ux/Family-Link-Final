package com.familylink.launcher

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The launcher's own line to the family's Firebase, read-only.
 *
 * Deliberately a copy of the main app's client rather than a shared module: the two apps ship
 * separately and must keep working when the other is broken or missing, and a hundred lines of
 * HTTP is a smaller price than a coupling that turns one bad release into two.
 *
 * Same shape as the main app's: plain REST on `<db>/<path>.json`, and the same URL with
 * `Accept: text/event-stream` for a connection that stays open and pushes every change.
 * No SDK and no google-services.json, so nothing extra has to be set up.
 */
class Sync(private val databaseUrl: String) {

    private fun url(path: String) = URL(databaseUrl.trimEnd('/') + "/" + path.trim('/') + ".json")

    /** One-shot read. Returns null on any failure — the caller keeps its last known state. */
    fun get(path: String): JSONObject? = try {
        val conn = (url(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val body = if (conn.responseCode in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else null
        conn.disconnect()
        body?.takeIf { it.isNotBlank() && it != "null" }?.let { JSONObject(it) }
    } catch (_: Throwable) {
        null
    }

    /**
     * Hold the connection open and hand over every change as it happens.
     *
     * Blocks until [shouldStop] says otherwise or the stream dies, so callers run it on their
     * own thread and simply start it again — a dropped mobile connection is normal, not an
     * error worth reporting on a home screen.
     */
    fun stream(path: String, shouldStop: () -> Boolean, onData: (JSONObject) -> Unit) {
        var conn: HttpURLConnection? = null
        try {
            conn = (url(path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                // No read timeout: an idle stream is the normal state, not a stall.
                readTimeout = 0
                setRequestProperty("Accept", "text/event-stream")
            }
            if (conn.responseCode !in 200..299) return
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (!shouldStop()) {
                    val line = reader.readLine() ?: break
                    // Firebase sends "event: put" then "data: {...}"; only the payload matters.
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "null") continue
                    runCatching { onData(JSONObject(payload)) }
                }
            }
        } catch (_: Throwable) {
            // Falls through: the caller reconnects.
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        fun configPath(familyId: String) = "families/$familyId/config"
        fun statusPath(familyId: String) = "families/$familyId/status"
    }
}
