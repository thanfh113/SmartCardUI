package org.example.project.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.view.employee.PinInputField // Tận dụng component cũ

@Composable
fun CreatePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = { Icon(Icons.Default.LockClock, null, Modifier.size(48.dp)) },
        title = {
            Text("KÍCH HOẠT THẺ", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Đây là lần đầu sử dụng. Vui lòng thiết lập mã PIN mới.", textAlign = TextAlign.Center)

                if (isLoading) {
                    CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                } else {
                    PinInputField(pin1, { pin1 = it; error = null }, "Nhập mã PIN mới")
                    PinInputField(pin2, { pin2 = it; error = null }, "Xác nhận mã PIN")
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    if (pin1.length < 4) error = "PIN quá ngắn (tối thiểu 4 số)"
                    else if (pin1 != pin2) error = "Hai mã PIN không khớp"
                    else {
                        isLoading = true
                        onConfirm(pin1) // Gửi ra ngoài để xử lý Coroutine
                    }
                }
            ) { Text("Thiết lập") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Hủy") }
        }
    )
}