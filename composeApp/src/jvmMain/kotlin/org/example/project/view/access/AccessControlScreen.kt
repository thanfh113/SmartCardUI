package org.example.project.view.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.AccessLogEntry
import org.example.project.model.AccessType
import java.time.format.DateTimeFormatter

@Composable
fun AccessControlScreen(
    userRole: String = "USER", // ✅ Thêm tham số phân quyền
    onRestrictedArea: ((() -> Unit) -> Unit)
) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy") }

    // State lưu log (Chỉ hiển thị log thẻ nếu là User)
    var logs by remember { mutableStateOf<List<AccessLogEntry>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var logRefreshKey by remember { mutableStateOf(0) }

    // Admin PIN dialog state
    var showAdminPinDialog by remember { mutableStateOf(false) }

    // Màu tím bảo mật (Deep Purple)
    val secureColor = Color(0xFF673AB7)

    // Hàm load Log thẻ (cho User)
    fun loadUserLogs() {
        if (userRole != "ADMIN") {
            scope.launch(Dispatchers.IO) {
                try {
                    val currentLogs = repo.getAccessLogs()
                    withContext(Dispatchers.Main) { logs = currentLogs }
                } catch (_: Exception) { /* Bỏ qua lỗi nếu mất kết nối thẻ */ }
            }
        }
    }

    // Kích hoạt tải log ban đầu và khi có yêu cầu làm mới
    LaunchedEffect(userRole, logRefreshKey) {
        if (userRole != "ADMIN") {
            loadUserLogs()
        } else {
            logs = emptyList() // Admin không hiển thị log thẻ
        }
    }

    // Hàm xử lý chung (Được gọi sau khi PIN đã được xác thực thành công ở tầng cha/Dialog)
    fun handleAccess(type: AccessType, desc: String, gate: String) {
        scope.launch(Dispatchers.IO) {
            if (userRole == "ADMIN") {
                // --- ADMIN: Ghi log Server trực tiếp ---
                val typeStr = when(type) {
                    AccessType.CHECK_IN -> "CHECK_IN"
                    AccessType.CHECK_OUT -> "CHECK_OUT"
                    else -> "RESTRICTED"
                }
                val adminId = "ADMIN01"

                val status = repo.adminAccessLog(adminId, typeStr, gate)

                withContext(Dispatchers.Main) {
                    when (status) {
                        HttpStatusCode.OK -> { message = "✅ Admin: Đã ghi log lên Server ($desc)" }
                        HttpStatusCode.Conflict -> {
                            message = "❌ Lỗi: Xung đột trạng thái phiên làm việc trên Server."
                        }
                        else -> { message = "❌ Lỗi Server: (${status.value})" }
                    }
                    logRefreshKey++
                }
            } else {
                // --- USER MODE: Kiểm tra thẻ vật lý trước ---
                try {
                    // 🔥 KIỂM TRA KHÓA VẬT LÝ NGAY LÚC NHẤN NÚT
                    if (repo.isCardLocked()) {
                        withContext(Dispatchers.Main) {
                            // Hiển thị thông báo lỗi đỏ tương tự như nhập sai PIN 3 lần
                            message = "❌ THẺ ĐÃ BỊ VÔ HIỆU HÓA! Vui lòng liên hệ Admin để mở lại."
                        }
                        return@launch // Kết thúc sớm, không gửi log lên server
                    }

                    // Nếu thẻ OK, thực hiện ghi log (Server + Thẻ)
                    val success = try {
                        repo.addAccessLog(type, desc)
                    } catch (e: Exception) {
                        false
                    }

                    withContext(Dispatchers.Main) {
                        if (success) {
                            message = "✅ Ghi log thành công: $desc"
                        } else {
                            val rejectionReason = when (type) {
                                AccessType.CHECK_IN -> "❌ Lỗi: Bạn đang có phiên làm việc mở."
                                AccessType.CHECK_OUT -> "❌ Lỗi: Không tìm thấy phiên để Check-Out."
                                else -> "❌ Lỗi truy cập hoặc thẻ đã bị ngắt kết nối!"
                            }
                            message = rejectionReason
                        }
                        logRefreshKey++
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        message = "❌ Lỗi: Thẻ không phản hồi hoặc đã bị rút ra."
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if(userRole == "ADMIN") "Kiểm Soát (Admin Mode)" else "Kiểm Soát Ra Vào",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- ACTION BUTTONS AREA ---
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nút Check-in
            AccessActionCard(
                title = "Check In",
                subtitle = "Vào cổng chính",
                icon = Icons.AutoMirrored.Filled.Login,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = { handleAccess(AccessType.CHECK_IN, "Vào cổng chính", "Cổng Chính") }
            )

            // Nút Check-out
            AccessActionCard(
                title = "Check Out",
                subtitle = "Ra cổng chính",
                icon = Icons.AutoMirrored.Filled.Logout,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = { handleAccess(AccessType.CHECK_OUT, "Ra cổng chính", "Cổng Chính") }
            )

            // 🔥 Nút Phòng Đặc Biệt: Admin show dialog cục bộ, User gọi callback cha
            AccessActionCard(
                title = "Phòng Máy Chủ",
                subtitle = "Xác thực PIN",
                icon = Icons.Default.AdminPanelSettings,
                color = secureColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (userRole == "ADMIN") {
                        showAdminPinDialog = true
                    } else {
                        // User: Yêu cầu nhập PIN trước khi ghi log (handled by parent)
                        onRestrictedArea {
                            handleAccess(AccessType.RESTRICTED_AREA, "Đã Truy Cập", "Server Room")
                        }
                    }
                }
            )
        }

        // Thông báo trạng thái (Feedback)
        if (message != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(message!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 🔥 DIALOG MỚI: XÁC THỰC PIN ADMIN CỤC BỘ (Tối ưu hóa UI/UX)
        if (showAdminPinDialog) {
            AdminPinInputDialog(
                onDismiss = { showAdminPinDialog = false; message = null },
                onPinConfirmed = { pin ->
                    scope.launch(Dispatchers.IO) {
                        val isPinValid = repo.adminLogin("ADMIN01", pin) // Dùng adminLogin để xác thực
                        withContext(Dispatchers.Main) {
                            if (isPinValid) {
                                showAdminPinDialog = false
                                // Thực hiện action sau khi xác thực thành công
                                handleAccess(AccessType.RESTRICTED_AREA, "Đã Truy Cập Server Room", "Server Room")
                            } else {
                                message = "❌ PIN không đúng hoặc lỗi xác thực Server!"
                            }
                        }
                    }
                }
            )
        }

        Divider()

        // --- HISTORY LIST (Giữ nguyên) ---
        Text(
            text = if(userRole == "ADMIN") "Log của Admin được lưu trên Server (Xem tại tab Lịch sử)" else "Nhật ký hoạt động trên thẻ",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (userRole != "ADMIN") {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    val (icon, iconColor) = when (log.accessType) {
                        AccessType.CHECK_IN -> Icons.AutoMirrored.Filled.Login to MaterialTheme.colorScheme.primary
                        AccessType.CHECK_OUT -> Icons.AutoMirrored.Filled.Logout to MaterialTheme.colorScheme.secondary
                        AccessType.RESTRICTED_AREA -> Icons.Default.AdminPanelSettings to secureColor
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            leadingContent = { Icon(icon, contentDescription = null, tint = iconColor) },
                            headlineContent = {
                                val displayName = if (log.accessType == AccessType.RESTRICTED_AREA) "Phòng Đặc Biệt" else log.accessType.name
                                Text(displayName, fontWeight = FontWeight.Bold, color = iconColor)
                            },
                            supportingContent = { Text(log.description) },
                            trailingContent = { Text(log.time.format(formatter), style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f)) // Placeholder cho Admin
        }
    }
}

// 🔥 COMPOSABLE MỚI: KHUNG NHẬP PIN ADMIN ĐÃ TỐI ƯU UI
@Composable
fun AdminPinInputDialog(
    onDismiss: () -> Unit,
    onPinConfirmed: suspend (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        icon = { Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Xác thực PIN Admin", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Đang kiểm tra Server...", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        localError = null
                        if (it.all { c -> c.isDigit() }) pin = it
                    },
                    label = { Text("Mã PIN") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isChecking) {
                            Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                        }
                    }
                )
                if (localError != null) Text(localError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) {
                        localError = "PIN phải có ít nhất 4 ký tự."
                    } else {
                        isChecking = true
                        scope.launch {
                            onPinConfirmed(pin)
                            isChecking = false // Sẽ được reset lại khi action hoàn thành
                        }
                    }
                },
                enabled = pin.length >= 4 && !isChecking
            ) {
                Text(if (isChecking) "Đang xác thực..." else "Xác nhận")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isChecking) { Text("Hủy") } }
    )
}

// Component nút bấm tùy chỉnh
@Composable
private fun AccessActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(40.dp), tint = color)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}