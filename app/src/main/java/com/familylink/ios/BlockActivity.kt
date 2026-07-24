package com.familylink.ios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.familylink.ios.ui.screens.BlockListScreen
import com.familylink.ios.ui.theme.FamilyLinkTheme

/**
 * Shown (as a normal, leavable screen — not a screen lock) when the child opens a blocked app.
 * It presents the Listen-Ansicht: which apps are blocked vs. still available. The child can
 * always leave via Home; PLUS apps stay usable.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Zeitlimit erreicht"
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Diese App ist gerade gesperrt."
        setContent {
            FamilyLinkTheme {
                BlockListScreen(reasonTitle = title, reasonDetail = detail, onClose = { goHome() })
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
