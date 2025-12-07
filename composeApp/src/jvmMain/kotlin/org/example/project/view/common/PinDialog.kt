package org.example.project.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
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
import kotlin.system.exitProcess // ✅ Import thư viện để thoát app

@Composable
fun PinDialog(
    title: String,
    cardState: CardState,
    onDismiss: () -> Unit,
    onPinOk: (String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    // Logic tính toán lỗi
    val maxTries = cardState.maxPinTries
    val currentTries = cardState.pinTriesRemaining
    val wrongCount = maxTries - currentTries

    // Có lỗi nếu số lần còn lại < tối đa và thẻ chưa bị khóa
    val isError = currentTries < maxTries && !cardState.isBlocked

    AlertDialog(
        onDismissRequest = {
            // Nếu bị khóa mà bấm ra ngoài dialog thì cũng thoát luôn cho chắc (tùy chọn)
            if (cardState.isBlocked) exitProcess(0) else onDismiss()
        },
        icon = {
            Icon(
                imageVector = if (cardState.isBlocked) Icons.Default.Warning else Icons.Default.Lock,
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
                textAlign = TextAlign.Center,
                color = if (cardState.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    // ✅ HIỂN THỊ KHI ĐANG TÍNH ARGON2
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Đang xác thực bảo mật...", style = MaterialTheme.typography.bodySmall)
                } else if (!cardState.isBlocked) {
                    // (Giữ nguyên code OutlinedTextField cũ)
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinText = it },
                        label = { Text("Nhập mã PIN") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = isError,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        supportingText = {
                            if (isError) {
                                Text("PIN sai! Còn lại $currentTries/$maxTries lần.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                if (cardState.isBlocked) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bạn đã nhập sai quá số lần quy định. Vui lòng liên hệ quản trị viên để mở khóa.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else if (isError) {
                    Text(
                        text = "Đã nhập sai $wrongCount lần.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                // Khóa nút xác nhận khi thẻ bị Block
                enabled = !cardState.isBlocked && pinText.isNotEmpty() && !isLoading,
                onClick = {
                    isLoading = true
                    onPinOk(pinText)
                    pinText = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isError) "Thử lại" else "Xác nhận")
            }
        },
        dismissButton = {
            // ✅ SỬA Ở ĐÂY: Logic nút Hủy bỏ
            TextButton(
                enabled = !isLoading,
                onClick = {
                    if (cardState.isBlocked) {
                        // Nếu thẻ bị khóa -> Thoát App luôn
                        exitProcess(0)
                    } else {
                        // Nếu chưa khóa -> Đóng dialog bình thường
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Đổi chữ hiển thị cho ngầu
                Text(if (cardState.isBlocked) "Thoát" else "Hủy bỏ")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp

    )
    LaunchedEffect(cardState) { isLoading = false }
}