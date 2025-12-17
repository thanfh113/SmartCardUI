package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.project.view.DesktopApp
import org.example.project.view.theme.SmartCardTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dịch Vụ Thẻ Thông Minh (MÁY TRẠM USER)"
    ) {
        SmartCardTheme {
            // isAdminLauncher = false -> Chạy chế độ User (Quẹt thẻ)
            DesktopApp(isAdminLauncher = false)
        }
    }
}