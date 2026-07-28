package com.familylink.ios.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.components.NovaButtonTonal
import com.familylink.ios.ui.theme.Nova

/** Whether this device can do fingerprint / face unlock right now. */
fun biometricsAvailable(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Parent app entry gate: fingerprint first (offered automatically), PIN as the fallback.
 * On success the parent lands directly in the management menu — the parent app never shows
 * the child's "Meine Zeit" screen.
 */
@Composable
fun ParentUnlockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val canBiometric = remember { biometricsAvailable(context) }

    var showPin by remember { mutableStateOf(!canBiometric) }
    var error by remember { mutableStateOf<String?>(null) }
    var promptShown by remember { mutableStateOf(false) }

    fun promptBiometric() {
        val activity = context as? FragmentActivity ?: run {
            showPin = true
            return
        }
        val prompt = BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    // User cancelled or hardware unavailable — fall back to the PIN.
                    showPin = true
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Eltern-Bereich entsperren")
                .setSubtitle("Mit Fingerabdruck bestätigen")
                .setNegativeButtonText("PIN verwenden")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }

    // Offer the fingerprint immediately on entry.
    LaunchedEffect(Unit) {
        if (canBiometric && !promptShown) {
            promptShown = true
            promptBiometric()
        }
    }

    if (showPin) {
        Box(Modifier.fillMaxSize()) {
            PinScreen(
                mode = PinMode.VERIFY,
                onSuccess = onUnlocked,
                onCancel = { showPin = false }
            )
            if (canBiometric) {
                Column(
                    Modifier.fillMaxSize().padding(bottom = 28.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Fingerabdruck verwenden",
                        fontSize = 15.sp, color = Nova.Primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { promptBiometric() }.padding(12.dp)
                    )
                }
            }
        }
        return
    }

    // Waiting for the system biometric sheet.
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient)).padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(78.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(Nova.BrandGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Eltern-Bereich", fontSize = 24.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Zum Entsperren bestätigen.",
            fontSize = 14.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 13.sp, color = Nova.Danger)
        }
        Spacer(Modifier.height(28.dp))
        Icon(
            Icons.Filled.Fingerprint, null, tint = Nova.Primary,
            modifier = Modifier.size(52.dp).clickable { promptBiometric() }
        )
        Spacer(Modifier.height(24.dp))
        NovaButtonTonal(text = "Stattdessen PIN eingeben") { showPin = true }
    }
}
