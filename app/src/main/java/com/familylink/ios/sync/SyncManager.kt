package com.familylink.ios.sync

import android.content.Context
import android.os.Build
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.UsageMode
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.UsageStatsTracker
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
            offlineLockEnabled = prefs.offlineLockEnabled,
            streakEnabled = prefs.streakEnabled,
            schoolTimeEnabled = prefs.schoolTimeEnabled,
            schoolStartMin = prefs.schoolStartMin,
            schoolEndMin = prefs.schoolEndMin,
            schoolDays = prefs.schoolDays,
            childName = prefs.childName,
            accentChoice = prefs.accentChoice,
            streakPenaltyMinutes = prefs.streakPenaltyMinutes,
            offlineLockMinutes = prefs.offlineLockMinutes,
            bonusMinutes = prefs.bonusSecondsToday / 60,
            offUntilEpoch = prefs.offUntilEpoch,
            settingsUnlockedUntil = prefs.settingsUnlockedUntil,
            categories = cats,
            focus = prefs.focusSession(),
            chores = prefs.getChores(),
            usageMode = prefs.usageMode.name,
            hardCapEnabled = prefs.hardCapEnabled,
            hardCapMinutes = prefs.hardCapMinutes,
            limitScope = prefs.limitScope.name,
            weeklyLimitMinutes = prefs.weeklyLimitMinutes,
            hardCapScope = prefs.hardCapScope.name,
            weeklyHardCapMinutes = prefs.weeklyHardCapMinutes,
            screenLockUntil = prefs.screenLockUntil,
            ownLockRewardEnabled = prefs.ownLockRewardEnabled,
            ownLockRewardPerHour = prefs.ownLockRewardPerHour,
            extensionMinutes = prefs.extensionMinutesToday,
            bonusUntilEpoch = prefs.bonusUntilEpoch,
            manualLock = prefs.manualLockEnabled,
            manualLockReason = prefs.manualLockReason
        )
    }

    // ---------------- focus mode ----------------

    /** Parent: start a focus session that takes effect on the child within ~1s. */
    fun startFocus(label: String, minutes: Int, allowed: List<String>) {
        val now = System.currentTimeMillis()
        prefs.setFocusSession(
            FocusSession(
                active = true,
                endsAt = now + minutes * 60_000L,
                label = label,
                allowed = allowed,
                // The child anchors the countdown on its own clock using the duration; startedAt
                // only identifies the session so a resync does not restart it.
                startedAt = now,
                durationSeconds = minutes * 60
            )
        )
    }

    fun stopFocus() = prefs.setFocusSession(FocusSession.OFF)

    /**
     * Parent: lock the child's device for a fixed time. Implemented as a focus session with an
     * empty allow-list — nothing but the phone and emergency dialler survives — so it inherits
     * the same clock-skew-proof countdown and automatic end.
     */
    fun lockForMinutes(minutes: Int, reason: String = "") =
        startFocus(if (reason.isBlank()) "Gesperrt" else reason, minutes, emptyList())

    /** Parent: lock the device with no end time, until it is lifted again. */
    fun lockDevice(reason: String = "") {
        prefs.manualLockReason = reason
        prefs.manualLockEnabled = true
    }

    /**
     * Parent: switch the child's display off for [minutes] (capped at 15). Not an overlay —
     * the screen locks and re-locks on every unlock until the time is up.
     */
    fun lockScreenForMinutes(minutes: Int) = prefs.startScreenLock(minutes)

    fun releaseScreenLock() = prefs.stopScreenLock()

    /**
     * Parent: hand out more time, either as an extension of the limits or as a free countdown.
     * Returns the minutes actually granted after the daily allowance.
     */
    /**
     * Give the child time. Always a countdown now: bonus time is a window in which everything is
     * open, and it ends on the clock. Zero minutes ends a running one early.
     */
    fun grantTime(minutes: Int, asBonusCountdown: Boolean = true): Int {
        if (minutes <= 0) {
            prefs.stopBonusCountdown()
            pushConfigAsync()
            return 0
        }
        val given = prefs.grantBonusCountdown(minutes)
        pushConfigAsync()
        return given
    }

    private fun pushConfigAsync() {
        kotlin.concurrent.thread(isDaemon = true) { runCatching { pushConfig() } }
    }

    fun unlockDevice() {
        prefs.manualLockEnabled = false
        prefs.manualLockReason = ""
    }

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

    // ---------------- the family PIN ----------------

    /**
     * Publish this device's PIN for the whole family. Only the salt and the hash travel; the
     * PIN itself never leaves the device and cannot be recovered from what is stored.
     */
    fun pushPortalPin(): Boolean {
        val c = client() ?: return false
        val (salt, hash) = prefs.sharablePin() ?: return false
        return c.put(
            "${SyncClient.familyPath(prefs.familyId)}/pin",
            JSONObject().put("salt", salt).put("hash", hash).put("updatedAt", System.currentTimeMillis())
        )
    }

    /** Adopt the family PIN, so the same code opens the portal on every device. */
    fun fetchPortalPin(): Boolean {
        val c = client() ?: return false
        val node = c.get("${SyncClient.familyPath(prefs.familyId)}/pin") ?: return false
        val salt = node.optString("salt", "")
        val hash = node.optString("hash", "")
        if (salt.isBlank() || hash.isBlank()) return false
        prefs.setSharedPin(salt, hash)
        return true
    }

    /** Parent: read the child's latest status once. */
    fun fetchChildStatus(): ChildStatus? {
        val c = client() ?: return null
        val json = c.get(SyncClient.statusPath(prefs.familyId)) ?: return null
        prefs.cachedChildStatus = json.toString()
        prefs.lastSyncAt = System.currentTimeMillis()
        return runCatching { ChildStatus.fromJson(json) }.getOrNull()
    }

    /**
     * Force a full exchange right now, in both directions. Used by the refresh button so a
     * parent never has to wait for the next polling tick — and so a child can push its usage
     * immediately after the parent asks for it.
     *
     * Blocking; call from a background thread. Returns true when the device's own upload
     * succeeded.
     */
    fun syncNow(): Boolean {
        if (!prefs.syncConfigured) return false
        return if (prefs.isParentDevice) {
            // Publish current rules, then pull the child's latest numbers and app list.
            val pushed = pushConfig()
            fetchChildStatus()
            runCatching { fetchChildApps() }
            runCatching { fetchPortalPin() }
            fetchChoreClaims().takeIf { it.isNotEmpty() }?.let { claims ->
                // Merge chore claims coming from the child so the portal shows them at once.
                val local = prefs.getChores().associateBy { it.id }
                prefs.setChores(claims.map { incoming ->
                    val l = local[incoming.id]
                    if (l != null && l.isApproved && !incoming.isApproved) l else incoming
                })
            }
            pushed
        } else {
            // Child: apply the newest rules, then report its own usage and app list.
            fetchConfigOnce()
            val ok = pushStatus()
            runCatching { pushAppList(force = true) }
            runCatching { fetchPortalPin() }
            ok
        }
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
        // Read the usage straight from the OS at push time. The cached numbers written by the
        // monitor service are only a fallback now: if the service was killed, or has not ticked
        // since midnight, the cache is stale and the parent used to receive a total of zero.
        val live = runCatching { UsageStatsTracker.todayUsageSeconds(context) }.getOrDefault(emptyMap())
        val cached = prefs.getPerAppSeconds()
        val usage = if (live.isNotEmpty()) live else cached

        // Only send labels for apps that were actually used, to keep the payload small.
        val labels = HashMap<String, String>()
        for (pkg in usage.keys) labels[pkg] = InstalledApps.labelFor(context, pkg)

        // Whole-phone screen time today, across every app — this is what a parent means by
        // "how much has the phone been used", regardless of categories and limits. Computed by
        // the engine so it is the exact same figure the absolute ceiling is measured against;
        // summing it differently here made the portal show one number and the ceiling use
        // another.
        val engine = LimitEngine(prefs)
        val totalDevice = runCatching { engine.computeTotalDeviceSeconds(usage) }.getOrDefault(0)
        // The share that counts against the daily budget, recomputed from the same fresh
        // numbers so the two values a parent sees can never contradict each other.
        val counted = runCatching { engine.computeGlobalUsedSeconds(usage) }
            .getOrDefault(prefs.globalUsedSeconds)

        val focus = prefs.effectiveFocusSession()
        // Rolls the day over if needed, so a report sent just after midnight already carries
        // yesterday's verdict rather than a stale count.
        val streak = prefs.streakState()
        val status = ChildStatus(
            globalUsedSeconds = counted,
            totalDeviceSeconds = totalDevice,
            // The limit as the engine really applies it, streak included, so the parent never
            // sees a different number than the child is measured against.
            limitSeconds = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday +
                prefs.streakBonusSecondsToday - prefs.streakPenaltySecondsToday,
            bonusSeconds = prefs.bonusSecondsToday,
            streakCurrent = streak.current,
            streakLongest = streak.longest,
            streakBonusMinutes = streak.bonusMinutesToday,
            streakPenaltyMinutes = streak.penaltyMinutesToday,
            weekCountedSeconds = prefs.weekCountedSeconds(),
            weekTotalSeconds = prefs.weekTotalSeconds(),
            weekHistory = runCatching { prefs.getWeekHistory() }.getOrDefault(emptyList()),
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
        // Publish the app list too. Fingerprinted, so this is a no-op unless something changed.
        runCatching { pushAppList() }
        return ok
    }

    // ---------------- child app list (child -> parent) ----------------

    /**
     * Child -> server: the full launchable app list with the category each app really has.
     *
     * Without this the parent only ever saw apps the child had *used today*, and only its own
     * local idea of their categories — so a Plus/Limit/Blocked mark made on the child device
     * never showed up in the parent app, and an app the child had never opened could not be
     * classified at all.
     *
     * Gated on a fingerprint: the list is large and changes rarely, so it is uploaded only when
     * something actually differs, not on every status push.
     */
    fun pushAppList(force: Boolean = false): Boolean {
        val c = client() ?: return false
        val apps = runCatching { InstalledApps.load(context) }.getOrDefault(emptyList())
        if (apps.isEmpty()) return false

        val list = apps.map { app ->
            ChildApp(
                pkg = app.packageName,
                label = app.label,
                category = prefs.categoryOf(app.packageName).name,
                limitMinutes = prefs.limitMinutesOf(app.packageName)
            )
        }.sortedBy { it.label.lowercase() }

        val hash = list.joinToString("|") { "${it.pkg}:${it.category}:${it.limitMinutes}" }.hashCode()
        if (!force && hash == prefs.lastAppListHash) return true

        val ok = c.put(
            SyncClient.appsPath(prefs.familyId),
            JSONObject()
                .put("list", ChildApp.listToJson(list))
                .put("updatedAt", System.currentTimeMillis())
        )
        if (ok) prefs.lastAppListHash = hash else prefs.lastSyncError = c.lastError.orEmpty()
        return ok
    }

    /**
     * Parent: read the child's app list and adopt the categories for every package the parent
     * has not classified itself, so the portal shows what is really in force on the child.
     */
    fun fetchChildApps(): List<ChildApp> {
        val c = client() ?: return emptyList()
        val node = c.get(SyncClient.appsPath(prefs.familyId)) ?: return emptyList()
        val list = ChildApp.listFromJson(node.optJSONArray("list"))
        if (list.isEmpty()) return emptyList()

        prefs.cachedChildApps = node.toString()
        adoptChildCategories(list)
        return list
    }

    /** Last app list received from the child, without touching the network. */
    fun cachedChildApps(): List<ChildApp> {
        val raw = prefs.cachedChildApps
        if (raw.isBlank()) return emptyList()
        return runCatching { ChildApp.listFromJson(JSONObject(raw).optJSONArray("list")) }
            .getOrDefault(emptyList())
    }

    /**
     * Fill in the categories the parent has no opinion on yet. Packages the parent has set
     * explicitly are left untouched — the parent stays the owner of the rules, this only stops
     * the portal from showing "Standard" for apps the child has long had marked otherwise.
     */
    private fun adoptChildCategories(list: List<ChildApp>) {
        val own = prefs.getCategories()
        var changed = false
        val merged = HashMap<String, Pair<AppCategory, Int>>(own)
        for (app in list) {
            if (own.containsKey(app.pkg)) continue
            val cat = runCatching { AppCategory.valueOf(app.category) }.getOrDefault(AppCategory.STANDARD)
            if (cat == AppCategory.STANDARD && app.limitMinutes == 30) continue // nothing to learn
            merged[app.pkg] = cat to app.limitMinutes
            changed = true
        }
        if (changed) prefs.replaceCategories(merged)
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
        prefs.offlineLockEnabled = cfg.offlineLockEnabled
        prefs.streakEnabled = cfg.streakEnabled
        prefs.schoolTimeEnabled = cfg.schoolTimeEnabled
        prefs.schoolStartMin = cfg.schoolStartMin
        prefs.schoolEndMin = cfg.schoolEndMin
        prefs.schoolDays = cfg.schoolDays
        if (cfg.childName.isNotBlank()) prefs.childName = cfg.childName
        prefs.accentChoice = cfg.accentChoice
        prefs.streakPenaltyMinutes = cfg.streakPenaltyMinutes
        prefs.offlineLockMinutes = cfg.offlineLockMinutes
        prefs.setBonusMinutesAbsolute(cfg.bonusMinutes)
        prefs.setOffUntilEpoch(cfg.offUntilEpoch)
        prefs.setSettingsUnlockedUntil(cfg.settingsUnlockedUntil)
        // Re-anchor the focus countdown on this device's clock. Taking the parent's absolute
        // end time literally made a session expire instantly (or run far too long) whenever the
        // two phones' clocks were a few minutes apart.
        prefs.setFocusSession(cfg.focus.anchorLocally(prefs.focusSession()))
        runCatching {
            prefs.usageMode = com.familylink.ios.data.UsageMode.valueOf(cfg.usageMode)
        }
        prefs.hardCapEnabled = cfg.hardCapEnabled
        prefs.hardCapMinutes = cfg.hardCapMinutes
        runCatching {
            prefs.limitScope = com.familylink.ios.data.LimitScope.valueOf(cfg.limitScope)
            prefs.hardCapScope = com.familylink.ios.data.LimitScope.valueOf(cfg.hardCapScope)
        }
        prefs.weeklyLimitMinutes = cfg.weeklyLimitMinutes
        prefs.weeklyHardCapMinutes = cfg.weeklyHardCapMinutes
        // Only the parent's own lock travels with the config; the child's lives in its own
        // field precisely so this line cannot wipe it.
        prefs.screenLockUntil = cfg.screenLockUntil
        prefs.ownLockRewardEnabled = cfg.ownLockRewardEnabled
        prefs.ownLockRewardPerHour = cfg.ownLockRewardPerHour
        prefs.applyGrants(cfg.extensionMinutes, cfg.bonusUntilEpoch)
        prefs.manualLockEnabled = cfg.manualLock
        prefs.manualLockReason = cfg.manualLockReason
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
        // The family PIN lives beside the config; pick it up on the same pass so a device that
        // was set up before the PIN existed still ends up on the same code.
        runCatching { fetchPortalPin() }
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
