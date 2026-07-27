package com.familylink.ios.sync

import android.content.Context
import android.os.Build
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.UsageMode
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
            focus = prefs.focusSession(),
            chores = prefs.getChores(),
            usageMode = prefs.usageMode.name
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

    // ---------------- chores ----------------

    /** Child: mark a chore as done so the parent can confirm it. */
    fun claimChore(id: String) {
        prefs.setChores(prefs.getChores().map {
            if (it.id == id && it.isOpen) it.copy(state = Chore.DONE, claimedAt = System.currentTimeMillis())
            else it
        })
        pushChores()
    }

    /** Parent: confirm a finished chore and credit the reward. */
    fun approveChore(id: String) {
        val chore = prefs.getChores().firstOrNull { it.id == id } ?: return
        prefs.addBonusMinutes(chore.rewardMinutes)
        prefs.setChores(prefs.getChores().map {
            if (it.id == id) it.copy(state = Chore.APPROVED, approvedAt = System.currentTimeMillis()) else it
        })
        pushConfig()
    }

    /** Parent: send a claimed chore back to the open list. */
    fun rejectChore(id: String) {
        prefs.setChores(prefs.getChores().map {
            if (it.id == id) it.copy(state = Chore.OPEN, claimedAt = 0) else it
        })
        pushConfig()
    }

    /** Child pushes only the chore list upward (it may not rewrite the parent's rules). */
    fun pushChores(): Boolean {
        val c = client() ?: return false
        return c.put(
            "${SyncClient.familyPath(prefs.familyId)}/chores",
            org.json.JSONObject().put("list", Chore.listToJson(prefs.getChores()))
        )
    }

    /** Parent: read chore claims coming from the child. */
    fun fetchChoreClaims(): List<Chore> {
        val c = client() ?: return emptyList()
        val node = c.get("${SyncClient.familyPath(prefs.familyId)}/chores") ?: return emptyList()
        return Chore.listFromJson(node.optJSONArray("list"))
    }

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
        if (ok) {
            prefs.lastSyncAt = System.currentTimeMillis()
            prefs.lastSyncError = ""
        } else {
            prefs.lastSyncError = c.lastError.orEmpty()
        }
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

        val focus = prefs.focusSession()
        val status = ChildStatus(
            globalUsedSeconds = prefs.globalUsedSeconds,
            // Whole-phone screen time today — this is what a parent means by "how much has the
            // phone been used", regardless of categories.
            totalDeviceSeconds = usage.filterKeys { it != Prefs.OWN_PKG }.values.sum(),
            limitSeconds = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday,
            bonusSeconds = prefs.bonusSecondsToday,
            perAppSeconds = usage,
            perAppLabels = labels,
            blockedToday = prefs.getBlockedToday().keys.toList(),
            bedtimeActive = prefs.isBedtime(),
            focusLabel = if (focus.isRunning()) focus.label else "",
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryPercent = readBattery()
        )
        val ok = c.put(SyncClient.statusPath(prefs.familyId), status.toJson())
        if (ok) {
            prefs.lastSyncAt = System.currentTimeMillis()
            prefs.lastSyncError = ""
        } else {
            prefs.lastSyncError = c.lastError.orEmpty()
        }
        return ok
    }

    private fun readBattery(): Int = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

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
        runCatching {
            prefs.usageMode = com.familylink.ios.data.UsageMode.valueOf(cfg.usageMode)
        }
        // Chores are shared state; the child only ever flips OPEN -> DONE locally, so we keep
        // a claim that has not been seen by the parent yet instead of overwriting it.
        val localChores = prefs.getChores().associateBy { it.id }
        prefs.setChores(cfg.chores.map { incoming ->
            val local = localChores[incoming.id]
            if (local != null && local.isClaimed && incoming.isOpen && local.claimedAt > cfg.updatedAt) local
            else incoming
        })

        // MERGE, never replace: a package the parent has not classified keeps its local
        // setting. Replacing would silently drop PLUS marks made on the child device, which
        // made allowed apps fall back to STANDARD and start consuming the daily budget.
        if (cfg.categories.isNotEmpty()) {
            val merged = HashMap<String, Pair<AppCategory, Int>>(prefs.getCategories())
            for ((pkg, raw) in cfg.categories) {
                val parts = raw.split(":")
                val cat = runCatching { AppCategory.valueOf(parts[0]) }.getOrDefault(AppCategory.STANDARD)
                val lim = parts.getOrNull(1)?.toIntOrNull() ?: 30
                merged[pkg] = cat to lim
            }
            prefs.replaceCategories(merged)
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
