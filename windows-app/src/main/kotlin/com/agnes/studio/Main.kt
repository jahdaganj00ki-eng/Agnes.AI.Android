package com.agnes.studio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agnes AI Image Studio",
        state = rememberWindowState(width = 440.dp, height = 820.dp),
    ) {
        App()
    }
}
