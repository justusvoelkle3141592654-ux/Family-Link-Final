package com.familylink.ios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.components.PinPad

enum class PinMode { VERIFY, SET }

/**
 * PIN gate. In SET mode the parent enters a new 4-digit code twice; in VERIFY mode the entered
 * code is checked against the stored hash.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var entered by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    val length = 4

    fun submit(code: String) {
        when (mode) {
            PinMode.VERIFY -> {
                if (prefs.checkPin(code)) onSuccess()
                else { error = true; entered = "" }
            }
            PinMode.SET -> {
                val first = firstEntry
                if (first == null) {
                    firstEntry = code
                    entered = ""
                } else if (first == code) {
                    prefs.setPin(code)
                    onSuccess()
                } else {
                    error = true
                    firstEntry = null
                    entered = ""
                }
            }
        }
    }

    val title = when {
        mode == PinMode.SET && firstEntry == null -> "Neue PIN festlegen"
        mode == PinMode.SET -> "PIN bestätigen"
        else -> "PIN eingeben"
    }
    val subtitle = when {
        error && mode == PinMode.SET -> "Codes stimmen nicht überein"
        error -> "Falsche PIN"
        mode == PinMode.SET -> "4-stelligen Code wählen"
        else -> "Eltern-Zugang"
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFFF2F2F7)),
        contentAlignment = Alignment.Center
    ) {
        PinPad(
            entered = entered,
            length = length,
            title = title,
            subtitle = subtitle,
            error = error,
            onDigit = { d ->
                error = false
                if (entered.length < length) {
                    entered += d
                    if (entered.length == length) submit(entered)
                }
            },
            onDelete = {
                error = false
                if (entered.isNotEmpty()) entered = entered.dropLast(1)
            }
        )
    }
}
