package com.familylink.ios.service

import android.content.Context
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.LockOverlay
import com.familylink.ios.util.LockState
import com.familylink.ios.util.LockUi
import com.familylink.ios.util.Permissions
import com.familylink.ios.util.TimeFmt

/**
 * One place that decides whether the lock is on screen, and puts it there.
 *
 * ## Why this exists
 *
 * The lock used to be raised only from the monitor's timer, and two things followed — both of
 * them the complaint. It arrived up to a tick and a half late, long enough to open an app, look
 * at it and wonder why nothing happened. And when the monitor was busy or restarting, it did not
 * arrive at all.
 *
 * The timer is now only one of the callers. The accessibility service calls [onForeground] the
 * instant an app comes to the front — the same event Android itself switches screens on — so the
 * lock is there before the app has finished drawing. The timer's job shrinks to what only a
 * timer can do: notice that a deadline passed while nothing else was happening.
 *
 * Everything here reads cached numbers. Measuring usage costs a query the accessibility service
 * must not make on every window change, and it does not need to: what seals the phone changes
 * with the clock, and the monitor refreshes the cache behind it every tick.
 */
object LockEnforcer {

    /** Why the phone is sealed. Wraps the engine's verdict so this file can add its own two. */
    sealed class Reason {
        /** The half-minute after a restart. */
        object Booting : Reason()

        /** A permission the enforcement rests on was switched off. */
        object GuardMissing : Reason()

        /** An ordinary rule: bedtime, the day's budget, the ceiling, a focus session. */
        data class Rule(val decision: LockDecision) : Reason()
    }

    /** What is on screen right now, or null while nothing is locked. */
    @Volatile
    private var current: LockUi? = null

    /**
     * Re-evaluate and put the lock on screen, or take it down.
     *
     * @param pkg the package in the foreground, if it is known.
     * @return the reason that sealed the device, or null when nothing does.
     */
    fun evaluate(context: Context, pkg: String?): Reason? {
        val prefs = Prefs.get(context)
        if (prefs.isParentDevice || !prefs.setupDone) {
            clear(context)
            return null
        }

        val reason = sealedReasonNow(context, prefs)
        if (reason == null) {
            clear(context)
            return null
        }

        // The windows the lock screen opens itself: the dialler, the settings page a repair
        // needs, the parent portal. While one is open the overlay steps aside — and the moment
        // the foreground leaves it, it is back.
        if (prefs.lockEscapeAllows(pkg)) {
            LockOverlay.hide(context)
            return reason
        }
        prefs.clearLockEscape()

        val bedtime = reason is Reason.Rule && reason.decision is LockDecision.Bedtime
        LockState.update(lockActive = true, hardLock = true, bedtime = bedtime)
        show(
            context,
            LockUi(
                title = titleFor(reason),
                detail = detailFor(context, reason),
                bedtime = bedtime,
                offline = reason is Reason.Rule && reason.decision is LockDecision.OfflineLock,
                repair = reason is Reason.GuardMissing
            )
        )
        return reason
    }

    /** Called by the accessibility service the moment a new app comes to the front. */
    fun onForeground(context: Context, pkg: String?) {
        runCatching { evaluate(context, pkg) }
    }

    /**
     * Put [ui] on the overlay, or hand it to the notification when there is no overlay to put it
     * on. Never tears the window down to change the text — that rebuild-per-second was what made
     * the lock flicker and, with a countdown running, never settle at all.
     */
    fun show(context: Context, ui: LockUi) {
        current = ui
        if (Permissions.hasOverlay(context) && !LockOverlay.lastShowFailed) {
            LockOverlay.update(context, ui) { LockOverlayHost() }
            // The overlay is the whole lock; while it is up, nothing else needs to nag.
            BlockNotifier.clear(context)
            return
        }
        // No overlay: a full-screen notification is the only route a background service is still
        // allowed to take from Android 10 on.
        BlockNotifier.show(
            context, ui.title, ui.detail,
            bedtime = ui.bedtime, hardLock = true, repair = ui.repair
        )
    }

    fun clear(context: Context) {
        if (current == null && !LockOverlay.isShowing) return
        current = null
        LockState.update(lockActive = false, hardLock = false, bedtime = false)
        LockOverlay.hide(context)
        BlockNotifier.clear(context)
    }

    /**
     * The watchdog. The system can take an overlay away — a configuration change, a process the
     * OS reclaimed — and without this the lock would quietly be gone until its reason happened
     * to change. Called from the monitor's tick, so a window that disappeared is back within a
     * second and a half at worst.
     */
    fun ensureStillUp(context: Context) {
        if (current == null) return
        if (!Permissions.hasOverlay(context)) return
        LockOverlay.ensureAttached(context) { LockOverlayHost() }
    }

    /**
     * What seals the phone at this instant, decided from cached numbers only.
     *
     * The order is the priority: the restart seal outranks a missing permission, which outranks
     * every rule about time — a phone that cannot enforce anything must not be reporting that
     * everything is fine.
     */
    private fun sealedReasonNow(context: Context, prefs: Prefs): Reason? {
        if (prefs.bootLockActive()) return Reason.Booting
        // The overlay permission is deliberately not in here: it is the one whose absence cannot
        // be announced by sealing, because sealing is what needs it. That one degrades to the
        // notification and is covered by the ordinary rules below.
        if (!Permissions.accessibilityEnabled(context) || !Permissions.hasUsageAccess(context)) {
            return Reason.GuardMissing
        }
        // While the display itself is locked there is nothing to draw over.
        if (prefs.screenLockActive()) return null
        val decision = LimitEngine(prefs).sealedReason(prefs.getPerAppSeconds()) ?: return null
        return Reason.Rule(decision)
    }

    private fun titleFor(reason: Reason): String = when (reason) {
        is Reason.Booting -> "Kindersicherung startet"
        is Reason.GuardMissing -> "Schutz ausgeschaltet"
        is Reason.Rule -> when (val d = reason.decision) {
            is LockDecision.Bedtime -> "Ruhezeit"
            is LockDecision.OfflineLock -> "Keine Verbindung"
            is LockDecision.HardCapReached -> "Gesamtlimit erreicht"
            is LockDecision.ManualLock -> "Gesperrt"
            is LockDecision.FocusActive -> d.label.ifBlank { "Fokus" }
            is LockDecision.GlobalLimitReached -> "Zeit ist um"
            else -> "Gesperrt"
        }
    }

    private fun detailFor(context: Context, reason: Reason): String {
        val prefs = Prefs.get(context)
        return when (reason) {
            is Reason.Booting ->
                "Das Handy ist noch ${prefs.bootLockRemainingSeconds()} Sekunden gesperrt, " +
                    "während der Schutz hochfährt."
            is Reason.GuardMissing ->
                "Es fehlt: ${Permissions.firstMissing(context)?.label ?: "eine Berechtigung"}. " +
                    "Tippe unten auf „Berechtigung erteilen“ — vorher bleibt das Handy gesperrt."
            is Reason.Rule -> when (val d = reason.decision) {
                is LockDecision.Bedtime ->
                    "Bis ${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr ist Schluss."
                is LockDecision.OfflineLock ->
                    "Das Handy hat sich zu lange nicht gemeldet. Schalte die Verbindung wieder ein."
                is LockDecision.HardCapReached ->
                    "Die gesamte Handyzeit für heute ist aufgebraucht " +
                        "(${TimeFmt.hm(d.usedSeconds)} von ${TimeFmt.hm(d.capSeconds)})."
                is LockDecision.ManualLock ->
                    d.reason.ifBlank { "Deine Eltern haben das Handy gesperrt." }
                is LockDecision.FocusActive ->
                    "Noch ${TimeFmt.hm(d.remainingSeconds)}."
                is LockDecision.GlobalLimitReached ->
                    "Die Bildschirmzeit für heute ist aufgebraucht " +
                        "(${TimeFmt.hm(d.usedSeconds)} von ${TimeFmt.hm(d.limitSeconds)})."
                else -> "Diese App ist gerade gesperrt."
            }
        }
    }
}
