package com.dotancohen.voiceandroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * A [FocusRequester] that takes the keyboard as soon as its field appears.
 *
 * For a field that is the whole point of the control it sits in: the tag
 * dialog exists to type a tag, so the user should be typing, not hunting for
 * the field and waiting for the keyboard.
 *
 * Not for a field that merely happens to be on a screen. The search box on
 * the notes list is the example: opening the list to read it must not throw
 * a keyboard over half of it.
 *
 * Use as `Modifier.focusRequester(autoFocus())`.
 */
@Composable
fun autoFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // A dialog's field is not attached on the first frame; requesting
        // focus then throws. Trying once the composition has settled is
        // enough, and a failure must not take the screen down.
        runCatching { requester.requestFocus() }
    }
    return requester
}
