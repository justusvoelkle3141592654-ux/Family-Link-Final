package com.familylink.ios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
 * The block screen (Listen-Ansicht) — a normal, leavable screen, not a screen lock. Hosts the
 * block list (with the Plus-Apps filter that can launch apps) and the parent-protected time
 * extension flow.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Zeitlimit erreicht"
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Diese App ist gerade gesperrt."

        setContent {
            FamilyLinkTheme {
                var screen by remember { mutableStateOf("block") }
                when (screen) {
                    "extend" -> ExtendTimeScreen(onClose = { screen = "block" })
                    else -> BlockListScreen(
                        reasonTitle = title,
                        reasonDetail = detail,
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
        val intent = InstalledApps.launchIntent(this, pkg)
        if (intent != null) {
            runCatching { startActivity(intent) }
            finish()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openPortal() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_DETAIL = "detail"

        fun launch(context: Context, title: String, detail: String) {
            val i = Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DETAIL, detail)
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
