package com.familylink.ios.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.familylink.ios.service.AppAccessibilityService

/** Central checks + settings intents for every special permission the app needs. */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${AppAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** A permission that has been taken away, with the page that grants it back. */
    data class Missing(val label: String, val hint: String, val intent: Intent)

    /**
     * The first permission the enforcement is missing, or null while everything is granted.
     *
     * Order matters: the accessibility service comes first because it is the one that makes the
     * others repairable — without it the app cannot even see that Settings was opened.
     */
    fun firstMissing(context: Context): Missing? = when {
        !accessibilityEnabled(context) -> Missing(
            "Bedienungshilfe",
            "Unter „Installierte Apps“ die Kindersicherung wieder einschalten.",
            accessibilityIntent()
        )
        !hasUsageAccess(context) -> Missing(
            "Nutzungszugriff",
            "Der Kindersicherung den Zugriff auf die Nutzungsdaten wieder erlauben.",
            usageAccessIntent()
        )
        !hasOverlay(context) -> Missing(
            "Über anderen Apps anzeigen",
            "Der Kindersicherung erlauben, sich über andere Apps zu legen.",
            overlayIntent(context)
        )
        else -> null
    }
}
