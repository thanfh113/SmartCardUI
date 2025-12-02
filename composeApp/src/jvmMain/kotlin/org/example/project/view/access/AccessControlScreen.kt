package org.example.project.view.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.AccessType
import java.time.format.DateTimeFormatter

@Composable
fun AccessControlScreen(
    onRestrictedArea: ((() -> Unit) -> Unit)
) {
    val repo = CardRepositoryProvider.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy") }
    var logs by remember { mutableStateOf(repo.getAccessLogs()) }

    // Màu tím bảo mật (Deep Purple) - Nhìn sang và bí mật hơn màu đỏ
    val secureColor = Color(0xFF673AB7)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- HEADER ---
        Text(
            "Kiểm Soát Ra Vào",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // --- ACTION BUTTONS AREA (Dạng thẻ to) ---
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
                onClick = {
                    repo.addAccessLog(AccessType.CHECK_IN, "Vào cổng chính")
                    logs = repo.getAccessLogs()
                }
            )

            // Nút Check-out
            AccessActionCard(
                title = "Check Out",
                subtitle = "Ra cổng chính",
                icon = Icons.AutoMirrored.Filled.Logout,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    repo.addAccessLog(AccessType.CHECK_OUT, "Ra cổng chính")
                    logs = repo.getAccessLogs()
                }
            )

            // Nút Phòng Đặc Biệt
            AccessActionCard(
                title = "Phòng Đặc Biệt",
                subtitle = "Quyền riêng tư cao", // Slogan nghe nhẹ nhàng hơn
                icon = Icons.Default.AdminPanelSettings, // Icon người quản trị
                color = secureColor, // Màu tím
                modifier = Modifier.weight(1f),
                onClick = {
                    onRestrictedArea {
                        repo.addAccessLog(
                            AccessType.RESTRICTED_AREA,
                            "Đã Truy Cập"
                        )
                        logs = repo.getAccessLogs()
                    }
                }
            )
        }

        Divider()

        // --- HISTORY LIST ---
        Text(
            "Nhật ký hoạt động gần đây",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                // Logic chọn Icon và màu sắc dựa trên AccessType
                val (icon, iconColor) = when (log.accessType) {
                    AccessType.CHECK_IN -> Icons.AutoMirrored.Filled.Login to MaterialTheme.colorScheme.primary
                    AccessType.CHECK_OUT -> Icons.AutoMirrored.Filled.Logout to MaterialTheme.colorScheme.secondary

                    // Cập nhật lại logic hiển thị cho log cũ
                    AccessType.RESTRICTED_AREA -> Icons.Default.AdminPanelSettings to secureColor
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(icon, contentDescription = null, tint = iconColor)
                        },
                        headlineContent = {
                            // Nếu tên type cũ là RESTRICTED_AREA thì hiển thị tên tiếng Việt đẹp hơn
                            val displayName = if (log.accessType == AccessType.RESTRICTED_AREA) "Phòng Đặc Biệt" else log.accessType.name
                            Text(displayName, fontWeight = FontWeight.Bold, color = iconColor)
                        },
                        supportingContent = {
                            Text(log.description)
                        },
                        trailingContent = {
                            Text(log.time.format(formatter), style = MaterialTheme.typography.bodySmall)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

// Component nút bấm tùy chỉnh (Private để không ảnh hưởng file khác)
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}