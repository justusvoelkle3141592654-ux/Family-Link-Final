package com.familylink.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The small weather line beside the clock.
 *
 * Open-Meteo is used because it needs no account, no API key and no SDK — the same reason the
 * rest of this project talks to Firebase over plain REST. Nothing is sent but a rounded
 * latitude and longitude, and only when the parent has granted coarse location.
 *
 * Everything here fails soft: no permission, no fix, no network, no forecast — the clock stays
 * and the weather line simply is not drawn. A home screen must never wait on the internet.
 */
object Weather {

    data class Now(val celsius: Int, val code: Int) {
        /** Open-Meteo's WMO code, as the one glyph that says the most. */
        val symbol: String
            get() = when (code) {
                0 -> "☀"
                1, 2 -> "🌤"
                3 -> "☁"
                in 45..48 -> "🌫"
                in 51..57 -> "🌦"
                in 61..67 -> "🌧"
                in 71..77 -> "🌨"
                in 80..82 -> "🌧"
                in 85..86 -> "🌨"
                in 95..99 -> "⛈"
                else -> "🌡"
            }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The last known position, never a fresh fix.
     *
     * Asking for a live location would wake the radio and cost battery every time the home
     * screen is opened, for a number that only needs to be roughly right. If the phone has no
     * cached position yet there is simply no weather until something else asks for one.
     */
    private fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            providers.firstNotNullOfOrNull { p ->
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(p)?.let { it.latitude to it.longitude }
            }
        }.getOrNull()
    }

    /** Fetch the current temperature, or null. Call from a background thread. */
    fun fetch(context: Context): Now? {
        val (lat, lon) = lastKnown(context) ?: return null
        // Two decimals is roughly a kilometre — plenty for a temperature, and less than the
        // phone knows.
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${"%.2f".format(java.util.Locale.US, lat)}" +
            "&longitude=${"%.2f".format(java.util.Locale.US, lon)}" +
            "&current=temperature_2m,weather_code"
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else null
            conn.disconnect()
            body ?: return null
            val current = JSONObject(body).optJSONObject("current") ?: return null
            Now(
                celsius = Math.round(current.optDouble("temperature_2m", Double.NaN)).toInt(),
                code = current.optInt("weather_code", -1)
            ).takeIf { !current.optDouble("temperature_2m", Double.NaN).isNaN() }
        }.getOrNull()
    }
}
