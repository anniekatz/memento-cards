package com.annie.memento.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
fun Modifier.dismissKeyboardGestures(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val connection = remember(keyboard, focus, ime, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && ime.getBottom(density) > 0) {
                    focus.clearFocus()
                    keyboard?.hide()
                }
                return Offset.Zero
            }
        }
    }
    return this
        .pointerInput(keyboard, focus, ime, density) {
            detectTapGestures {
                if (ime.getBottom(density) > 0) {
                    focus.clearFocus()
                    keyboard?.hide()
                }
            }
        }
        .nestedScroll(connection)
}
