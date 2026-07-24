package com.familylink.ios.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Returns a State that increments every time the host returns to RESUMED. Read it inside a
 * composable so status derived from system settings (permissions granted in the Settings app)
 * is re-evaluated the moment the user comes back.
 */
@Composable
fun rememberResumeTick(): State<Int> {
    val owner = LocalLifecycleOwner.current
    val tick = remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick.value = tick.value + 1
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}
