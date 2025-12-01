package org.example.project.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.model.CardState

@Composable
fun PinDialog(
    title: String,
    cardState: CardState,
    onDismiss: () -> Unit,
    onPinOk: (String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    // State quản lý ẩn/hiện mật khẩu
    var passwordVisible by remember { mutableStateOf(false) }

    val isError = cardState.pinTriesRemaining < cardState.maxPinTries

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (cardState.isBlocked) Icons.Default.Warning else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (cardState.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (cardState.isBlocked) "THẺ ĐÃ BỊ KHÓA" else title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!cardState.isBlocked) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinText = it },
                        label = { Text("Nhập mã PIN") },
                        singleLine = true,
                        // Logic chuyển đổi chế độ hiển thị
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = isError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        // Icon con mắt ở cuối
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (passwordVisible) "Ẩn PIN" else "Hiện PIN"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        }
                    )
                }

                // Hiển thị số lần còn lại
                if (isError || cardState.isBlocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (cardState.isBlocked)
                                "Vui lòng liên hệ quản trị viên để mở khóa."
                            else
                                "Sai mã PIN! Còn lại ${cardState.pinTriesRemaining}/${cardState.maxPinTries} lần thử.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !cardState.isBlocked && pinText.isNotEmpty(),
                onClick = { onPinOk(pinText); pinText = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Hủy bỏ")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}