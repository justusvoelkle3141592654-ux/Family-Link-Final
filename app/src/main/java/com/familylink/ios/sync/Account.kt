package com.familylink.ios.sync

import android.os.Build
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Lightweight account layer on top of the Realtime Database.
 *
 * A family account is identified by the e-mail address; the node key is a hash of it so the
 * raw address never becomes a database key. The password is stored salted+hashed — the server
 * never sees the plaintext.
 *
 * NOTE ON SECURITY: this is client-side auth against an open database node. It keeps honest
 * people out and separates families, but it is not equivalent to a real auth provider. Lock
 * the database down with the rules from the README so only known family nodes are writable.
 */
object Account {

    const val MAX_DEVICES = 3

    fun emailKey(email: String): String =
        sha256(email.trim().lowercase()).take(24)

    fun hashPassword(password: String, salt: String): String = sha256("$salt|$password")

    fun newSalt(): String = UUID.randomUUID().toString()

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Stable per-installation device id. */
    fun deviceId(existing: String?): String =
        if (!existing.isNullOrBlank()) existing else UUID.randomUUID().toString().take(12)

    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}

/** Result of a sign-up / sign-in attempt. */
sealed class AuthResult {
    data class Success(val familyId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** A device registered on the family account. */
data class RegisteredDevice(
    val id: String,
    val name: String,
    val role: String,
    val lastSeen: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("role", role).put("lastSeen", lastSeen)

    companion object {
        fun fromJson(id: String, o: JSONObject) = RegisteredDevice(
            id = id,
            name = o.optString("name", "Gerät"),
            role = o.optString("role", "CHILD"),
            lastSeen = o.optLong("lastSeen", 0)
        )
    }
}

/**
 * Account operations against the database. All calls block — run them off the main thread.
 */
class AccountClient(private val client: SyncClient) {

    private fun accountPath(email: String) = "accounts/${Account.emailKey(email)}"

    /** Create a new family account. Fails if the e-mail is already taken. */
    fun signUp(email: String, password: String): AuthResult {
        if (!email.contains("@") || email.length < 5) return AuthResult.Error("Bitte eine gültige E-Mail eingeben.")
        if (password.length < 6) return AuthResult.Error("Passwort muss mindestens 6 Zeichen haben.")

        if (client.get(accountPath(email)) != null) {
            return AuthResult.Error("Für diese E-Mail existiert bereits ein Konto.")
        }
        val salt = Account.newSalt()
        val familyId = UUID.randomUUID().toString().take(10)
        val node = JSONObject()
            .put("salt", salt)
            .put("hash", Account.hashPassword(password, salt))
            .put("familyId", familyId)
            .put("createdAt", System.currentTimeMillis())

        return if (client.put(accountPath(email), node)) AuthResult.Success(familyId)
        else AuthResult.Error("Server nicht erreichbar. Adresse prüfen.")
    }

    /** Sign in to an existing account. */
    fun signIn(email: String, password: String): AuthResult {
        val node = client.get(accountPath(email))
            ?: return AuthResult.Error("Konto nicht gefunden.")
        val salt = node.optString("salt", "")
        val hash = node.optString("hash", "")
        if (salt.isBlank() || hash != Account.hashPassword(password, salt)) {
            return AuthResult.Error("Passwort stimmt nicht.")
        }
        val familyId = node.optString("familyId", "")
        return if (familyId.isBlank()) AuthResult.Error("Konto ist beschädigt.")
        else AuthResult.Success(familyId)
    }

    fun listDevices(familyId: String): List<RegisteredDevice> {
        val node = client.get("${SyncClient.familyPath(familyId)}/devices") ?: return emptyList()
        val out = ArrayList<RegisteredDevice>()
        node.keys().forEach { id ->
            node.optJSONObject(id)?.let { out.add(RegisteredDevice.fromJson(id, it)) }
        }
        return out.sortedBy { it.lastSeen }
    }

    /**
     * Register this device on the family. Enforces the [Account.MAX_DEVICES] limit —
     * an already-registered device just refreshes its entry.
     */
    fun registerDevice(familyId: String, deviceId: String, role: DeviceRole): AuthResult {
        val existing = listDevices(familyId)
        if (existing.none { it.id == deviceId } && existing.size >= Account.MAX_DEVICES) {
            return AuthResult.Error(
                "Maximal ${Account.MAX_DEVICES} Geräte pro Konto. Bitte zuerst ein Gerät im Portal entfernen."
            )
        }
        val device = RegisteredDevice(deviceId, Account.deviceName(), role.name, System.currentTimeMillis())
        return if (client.put("${SyncClient.familyPath(familyId)}/devices/$deviceId", device.toJson()))
            AuthResult.Success(familyId)
        else AuthResult.Error("Gerät konnte nicht registriert werden.")
    }

    fun removeDevice(familyId: String, deviceId: String): Boolean =
        client.delete("${SyncClient.familyPath(familyId)}/devices/$deviceId")

    /** Heartbeat so the parent portal can show which devices are alive. */
    fun touchDevice(familyId: String, deviceId: String, role: DeviceRole) {
        val device = RegisteredDevice(deviceId, Account.deviceName(), role.name, System.currentTimeMillis())
        client.put("${SyncClient.familyPath(familyId)}/devices/$deviceId", device.toJson())
    }
}
