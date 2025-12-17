package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.project.view.DesktopApp
import org.example.project.view.theme.SmartCardTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hệ Thống Quản Trị (MÁY CHỦ ADMIN)"
    ) {
        SmartCardTheme {
            // isAdminLauncher = true -> Chạy chế độ Admin (Login Server)
            DesktopApp(isAdminLauncher = true)
        }
    }
}