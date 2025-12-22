package org.example.project.view.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.data.CardRepositoryProvider

@Composable
fun AdminLoginScreen(onLoginSuccess: () -> Unit) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var adminId by remember { mutableStateOf("AD001") } // Mặc định ID admin trong DB
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // STATE MỚI: Theo dõi trạng thái hiển thị mật khẩu
    var passwordVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(400.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Đăng Nhập Quản Trị", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = adminId,
                    onValueChange = { adminId = it },
                    label = { Text("Admin ID") },
                    modifier = Modifier.fillMaxWidth()
                )

                // SỬA: Thêm VisualTransformation và Trailing Icon
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Mã PIN Server") },
                    // Dùng VisualTransformation dựa trên trạng thái passwordVisible
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        // Nút chuyển đổi trạng thái passwordVisible
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Ẩn PIN" else "Hiện PIN")
                        }
                    }
                )

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            // Gọi Server để check
                            val success = repo.adminLogin(adminId, pin)
                            isLoading = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                error = "Đăng nhập thất bại! Kiểm tra ID/PIN."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Đang kiểm tra..." else "Đăng Nhập")
                }
            }
        }
    }
}