package org.example.project

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.project.DesktopApp
import org.example.project.view.theme.SmartCardTheme

@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, Desktop!"
        }) {
            Text(text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Thẻ nhân viên công ty thông minh"
    ) {
        SmartCardTheme {
            DesktopApp()
        }
    }
}

