package com.familylink.ios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.familylink.ios.data.Prefs
import com.familylink.ios.service.AppAccessibilityService
import com.familylink.ios.ui.components.PinPad
import com.familylink.ios.ui.theme.FamilyLinkTheme
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.ui.theme.ThemeMode

/**
 * The PIN in front of the system settings.
 *
 * Bouncing the child out of Settings was never quite enough: the parent also has to get in
 * there, and "ask the portal first" made every real repair a two-device errand. So Settings is
 * no longer forbidden — it is behind the family PIN.
 *
 *  - correct PIN -> Settings is released for a few minutes and this screen steps aside, so the
 *    page underneath simply resumes,
 *  - wrong PIN or cancel -> the accessibility service sends the phone to the home screen, which
 *    is the same reaction the old bounce had, only now it is the answer to a failed attempt
 *    rather than to opening Settings at all.
 *
 * Deliberately its own task and excluded from Recents: it must not become a card the child can
 * swipe back to, and it must not drag the portal up with it.
 */
class PinVerificationActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    /** Set once the outcome is decided, so BACK and onDestroy do not fire a second reaction. */
    private var resolved = false
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        // Keep the PIN out of screenshots and the Recents preview, and let it show over the
        // lock screen — the settings page it guards can be reached from there too.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        setContent {
            val dark = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            FamilyLinkTheme(dark = dark) {
                // BACK is a cancel, not a way past.
                BackHandler(enabled = true) { cancel() }

                var entered by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }

                Box(
                    Modifier.fillMaxSize().background(Nova.Canvas),
                    contentAlignment = Alignment.Center
                ) {
                    PinPad(
                        entered = entered,
                        length = PIN_LENGTH,
                        title = "Einstellungen gesperrt",
                        subtitle = "Eltern-PIN eingeben, um die Einstellungen zu öffnen.",
                        error = error,
                        dark = dark,
                        onDigit = { d ->
                            if (entered.length < PIN_LENGTH) {
                                error = false
                                entered += d
                                if (entered.length == PIN_LENGTH) {
                                    if (prefs.checkPin(entered)) {
                                        grantAccess()
                                    } else {
                                        error = true
                                        entered = ""
                                        onWrongPin()
                                    }
                                }
                            }
                        },
                        onDelete = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
                    )
                }
            }
        }
    }

    /** Correct PIN: open the access window and let the settings page underneath resume. */
    private fun grantAccess() {
        if (resolved) return
        resolved = true
        prefs.unlockSettings(GRANT_MINUTES)
        AppAccessibilityService.onPinPromptClosed()
        finish()
        overridePendingTransition(0, 0)
    }

    /** Wrong PIN. A few tries are a typo; past that it is an attempt, and the phone leaves. */
    private fun onWrongPin() {
        attempts++
        if (attempts >= MAX_ATTEMPTS) cancel()
    }

    /** Cancelled or exhausted: leave for the home screen, then close. */
    private fun cancel() {
        if (resolved) return
        resolved = true
        AppAccessibilityService.onPinPromptClosed()
        // The service does it properly. Without it (not yet connected, just switched off) we
        // still have to get off the settings page, so fall back to launching Home ourselves.
        if (!AppAccessibilityService.goHomeNow()) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // Dismissed by some other route (Recents, the system) — still a cancel, so the child is
        // never left sitting on an open settings page.
        if (!resolved) cancel()
        AppAccessibilityService.onPinPromptClosed()
        super.onDestroy()
    }

    companion object {
        private const val PIN_LENGTH = 6
        private const val MAX_ATTEMPTS = 3

        /** How long a correct PIN keeps Settings released. */
        private const val GRANT_MINUTES = 5

        /** Raise the PIN over whatever is in the foreground. */
        fun launch(context: Context) {
            val i = Intent(context, PinVerificationActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            runCatching { context.startActivity(i) }
        }
    }
}
