package org.example.project.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.view.employee.PinInputField // Tận dụng component cũ

@Composable
fun CreatePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    externalError: String? = null // Nhận thông báo lỗi từ App.kt
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) } // Đổi tên để phân biệt
    var isLoading by remember { mutableStateOf(false) }

    // 🔥 QUAN TRỌNG: Lắng nghe lỗi bên ngoài để tắt xoay vòng
    LaunchedEffect(externalError) {
        if (externalError != null) {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = { Icon(Icons.Default.LockClock, null, Modifier.size(48.dp)) },
        title = { Text("KÍCH HOẠT THẺ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Vui lòng thiết lập mã PIN mới để kích hoạt thẻ.", textAlign = TextAlign.Center)

                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    PinInputField(pin1, { pin1 = it; localError = null }, "Nhập mã PIN mới")
                    PinInputField(pin2, { pin2 = it; localError = null }, "Xác nhận mã PIN")
                }

                // 🔥 HIỂN THỊ LỖI: Ưu tiên lỗi từ thẻ/server (externalError) trước
                val displayError = externalError ?: localError
                if (displayError != null) {
                    Text(
                        text = displayError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    if (pin1.length < 4) localError = "PIN quá ngắn (tối thiểu 4 số)"
                    else if (pin1 != pin2) localError = "Hai mã PIN không khớp"
                    else {
                        isLoading = true
                        onConfirm(pin1)
                    }
                }
            ) { Text("Thiết lập") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Hủy") }
        }
    )
}