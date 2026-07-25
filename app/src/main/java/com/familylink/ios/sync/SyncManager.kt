package com.familylink.ios.sync

import android.content.Context
import android.os.Build
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import org.json.JSONObject

/**
 * Keeps the parent and the child device in sync.
 *
 *  PARENT: writes [FamilyConfig] whenever a setting changes, and streams the child's
 *          [ChildStatus] so usage shows up live in the portal.
 *  CHILD:  streams [FamilyConfig] and applies it immediately (limits, bedtime, categories,
 *          bonus, off-button, settings release), and pushes its own usage upward.
 *
 * Real time is achieved with an open SSE connection; a periodic push acts as a safety net.
 */
class SyncManager(private val context: Context) {

    private val prefs = Prefs.get(context)

    private fun client(): SyncClient? =
        if (prefs.syncConfigured) SyncClient(prefs.syncUrl) else null

    // ---------------- parent side ----------------

    /** Snapshot the parent's current rules. */
    fun buildConfig(): FamilyConfig {
        val cats = prefs.getCategories().mapValues { (_, v) -> "${v.first.name}:${v.second}" }
        return FamilyConfig(
            globalLimitMinutes = prefs.globalLimitMinutes,
            bedtimeEnabled = prefs.bedtimeEnabled,
            bedtimeStartMin = prefs.bedtimeStartMin,
            bedtimeEndMin = prefs.bedtimeEndMin,
            bedtimeSoundEnabled = prefs.bedtimeSoundEnabled,
            bonusMinutes = prefs.bonusSecondsToday / 60,
            offUntilEpoch = prefs.offUntilEpoch,
            settingsUnlockedUntil = prefs.settingsUnlockedUntil,
            categories = cats,
            focus = prefs.focusSession()
        )
    }

    // ---------------- focus mode ----------------

    /** Parent: start a focus session that takes effect on the child within ~1s. */
    fun startFocus(label: String, minutes: Int, allowed: List<String>) {
        prefs.setFocusSession(
            FocusSession(
                active = true,
                endsAt = System.currentTimeMillis() + minutes * 60_000L,
                label = label,
                allowed = allowed
            )
        )
    }

    fun stopFocus() = prefs.setFocusSession(FocusSession.OFF)

    // ---------------- time requests ----------------

    /** Child: ask for extra minutes. */
    fun sendRequest(minutes: Int, reason: String): Boolean {
        val c = client() ?: return false
        val req = TimeRequest(minutes = minutes, reason = reason)
        prefs.requestJson = req.toJson().toString()
        return c.put("${SyncClient.familyPath(prefs.familyId)}/request", req.toJson())
    }

    fun readRequest(): TimeRequest? {
        val c = client() ?: return null
        val json = c.get("${SyncClient.familyPath(prefs.familyId)}/request") ?: return null
        return runCatching { TimeRequest.fromJson(json) }.getOrNull()
    }

    /** Parent: approve (grants the minutes) or decline. */
    fun decideRequest(req: TimeRequest, approve: Boolean): Boolean {
        val c = client() ?: return false
        if (approve) prefs.addBonusMinutes(req.minutes)
        val updated = req.copy(
            state = if (approve) TimeRequest.APPROVED else TimeRequest.DECLINED,
            decidedAt = System.currentTimeMillis()
        )
        val ok = c.put("${SyncClient.familyPath(prefs.familyId)}/request", updated.toJson())
        if (approve) pushConfig()
        return ok
    }

    /** Parent -> server. Safe to call from any background thread. */
    fun pushConfig(): Boolean {
        val c = client() ?: return false
        val ok = c.put(SyncClient.configPath(prefs.familyId), buildConfig().toJson())
        if (ok) prefs.lastSyncAt = System.currentTimeMillis()
        return ok
    }

    /** Parent: read the child's latest status once. */
    fun fetchChildStatus(): ChildStatus? {
        val c = client() ?: return null
        val json = c.get(SyncClient.statusPath(prefs.familyId)) ?: return null
        prefs.cachedChildStatus = json.toString()
        prefs.lastSyncAt = System.currentTimeMillis()
        return runCatching { ChildStatus.fromJson(json) }.getOrNull()
    }

    /** Last known child status from cache (no network). */
    fun cachedChildStatus(): ChildStatus? {
        val raw = prefs.cachedChildStatus
        if (raw.isBlank()) return null
        return runCatching { ChildStatus.fromJson(JSONObject(raw)) }.getOrNull()
    }

    // ---------------- child side ----------------

    /** Child -> server: report live usage. */
    fun pushStatus(): Boolean {
        val c = client() ?: return false
        val usage = prefs.getPerAppSeconds()
        // Only send labels for apps that were actually used, to keep the payload small.
        val labels = HashMap<String, String>()
        for (pkg in usage.keys) labels[pkg] = InstalledApps.labelFor(context, pkg)

        val status = ChildStatus(
            globalUsedSeconds = prefs.globalUsedSeconds,
            perAppSeconds = usage,
            perAppLabels = labels,
            blockedToday = prefs.getBlockedToday().keys.toList(),
            bedtimeActive = prefs.isBedtime(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        val ok = c.put(SyncClient.statusPath(prefs.familyId), status.toJson())
        if (ok) prefs.lastSyncAt = System.currentTimeMillis()
        return ok
    }

    /** Child: apply a config received from the parent. */
    fun applyConfig(cfg: FamilyConfig) {
        // Ignore stale revisions.
        if (cfg.updatedAt > 0 && cfg.updatedAt <= prefs.lastConfigStamp) return
        prefs.lastConfigStamp = cfg.updatedAt

        prefs.globalLimitMinutes = cfg.globalLimitMinutes
        prefs.bedtimeEnabled = cfg.bedtimeEnabled
        prefs.bedtimeStartMin = cfg.bedtimeStartMin
        prefs.bedtimeEndMin = cfg.bedtimeEndMin
        prefs.bedtimeSoundEnabled = cfg.bedtimeSoundEnabled
        prefs.setBonusMinutesAbsolute(cfg.bonusMinutes)
        prefs.setOffUntilEpoch(cfg.offUntilEpoch)
        prefs.setSettingsUnlockedUntil(cfg.settingsUnlockedUntil)
        prefs.setFocusSession(cfg.focus)

        if (cfg.categories.isNotEmpty()) {
            val parsed = HashMap<String, Pair<AppCategory, Int>>()
            for ((pkg, raw) in cfg.categories) {
                val parts = raw.split(":")
                val cat = runCatching { AppCategory.valueOf(parts[0]) }.getOrDefault(AppCategory.STANDARD)
                val lim = parts.getOrNull(1)?.toIntOrNull() ?: 30
                parsed[pkg] = cat to lim
            }
            prefs.replaceCategories(parsed)
        }
        prefs.lastSyncAt = System.currentTimeMillis()
    }

    /** Child: fetch the config once (used right after pairing). */
    fun fetchConfigOnce(): Boolean {
        val c = client() ?: return false
        val json = c.get(SyncClient.configPath(prefs.familyId)) ?: return false
        applyConfig(FamilyConfig.fromJson(json))
        return true
    }

    // ---------------- pairing ----------------

    /** Verify that a family node exists (child side, after entering the code). */
    fun familyExists(url: String, familyId: String): Boolean {
        val probe = SyncClient(url)
        return probe.get(SyncClient.familyPath(familyId)) != null
    }

    /** Parent: create the family node so the child can find it. */
    fun createFamily(url: String, familyId: String): Boolean {
        val probe = SyncClient(url)
        val node = JSONObject().put("createdAt", System.currentTimeMillis())
        return probe.put("${SyncClient.familyPath(familyId)}/meta", node)
    }

    companion object {
        /** 6-digit pairing code, easy to read out loud. */
        fun generatePairingCode(): String = (100000..999999).random().toString()
    }
}
