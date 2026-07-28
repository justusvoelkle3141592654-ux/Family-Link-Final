package com.familylink.ios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.ui.screens.BlockListScreen
import com.familylink.ios.ui.screens.ExtendTimeScreen
import com.familylink.ios.ui.theme.FamilyLinkTheme

/**
 * The block screen.
 *
 * Dismissibility follows the scope of the block, in three steps:
 *  - a single app's own limit -> dismissible: the child can go home and use other apps,
 *  - the day limit            -> hard lock: BACK is swallowed and there is no "Startbildschirm"
 *    link, but the allowed Plus apps and an extension request are still reachable,
 *  - bedtime, the absolute ceiling and a manual lock by the parent -> SEALED: nothing at all
 *    can be opened from here. Only the phone, the emergency dialler and the PIN-protected
 *    parent entry remain.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Zeitlimit erreicht"
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Diese App ist gerade gesperrt."
        val bedtime = intent.getBooleanExtra(EXTRA_BEDTIME, false)
        val hardLock = intent.getBooleanExtra(EXTRA_HARD_LOCK, false)
        val sealedLock = intent.getBooleanExtra(EXTRA_SEALED, false)

        // Device owner only: pin this screen so HOME and Recents stop working during a hard
        // lock. This is the piece that makes bedtime / day-limit genuinely unescapable.
        if (hardLock || sealedLock) com.familylink.ios.admin.DeviceOwner.startKiosk(this)

        setContent {
            val prefs = com.familylink.ios.data.Prefs.get(this)
            val dark = when (prefs.themeMode) {
                com.familylink.ios.ui.theme.ThemeMode.DARK -> true
                com.familylink.ios.ui.theme.ThemeMode.LIGHT -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            FamilyLinkTheme(dark = dark) {
                // Hard locks cannot be dismissed with BACK.
                if (hardLock || sealedLock) BackHandler(enabled = true) { /* swallow */ }

                var screen by remember { mutableStateOf("block") }
                when (screen) {
                    "extend" -> ExtendTimeScreen(onClose = { screen = "block" })
                    else -> BlockListScreen(
                        reasonTitle = title,
                        reasonDetail = detail,
                        bedtime = bedtime,
                        hardLock = hardLock,
                        sealed = sealedLock,
                        onLaunchApp = { pkg -> launchApp(pkg) },
                        onExtend = { screen = "extend" },
                        onOpenPortal = { openPortal() },
                        onClose = { goHome() }
                    )
                }
            }
        }
    }

    private fun launchApp(pkg: String) {
        leaveKiosk()
        val intent = InstalledApps.launchIntent(this, pkg)
        if (intent != null) {
            runCatching { startActivity(intent) }
            finish()
        }
    }

    override fun onDestroy() {
        runCatching { com.familylink.ios.admin.DeviceOwner.stopKiosk(this) }
        super.onDestroy()
    }

    private fun leaveKiosk() {
        runCatching { com.familylink.ios.admin.DeviceOwner.stopKiosk(this) }
    }

    private fun goHome() {
        leaveKiosk()
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openPortal() {
        leaveKiosk()
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_BEDTIME = "bedtime"
        const val EXTRA_HARD_LOCK = "hard_lock"
        const val EXTRA_SEALED = "sealed"

        fun launch(
            context: Context,
            title: String,
            detail: String,
            bedtime: Boolean,
            hardLock: Boolean,
            sealed: Boolean = false
        ) {
            val i = Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_BEDTIME, bedtime)
                putExtra(EXTRA_HARD_LOCK, hardLock)
                putExtra(EXTRA_SEALED, sealed)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            runCatching { context.startActivity(i) }
        }
    }
}
