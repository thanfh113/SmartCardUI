package org.example.project.view.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.AccessLogEntry
import org.example.project.model.AccessType
import org.example.project.model.HistoryLogEntry
import org.example.project.model.Transaction
import org.example.project.model.TransactionType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HistoryScreen(
    userRole: String = "USER"
) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var accessLogs by remember { mutableStateOf<List<AccessLogEntry>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Hàm load dữ liệu
    fun loadHistory() {
        scope.launch(Dispatchers.IO) {
            isLoading = true

            // 1. Xác định Employee ID cần lấy log
            val employeeIdToFetch = try {
                // Admin không cần ID (null) -> Server trả về tất cả
                if (userRole == "ADMIN") null
                // User cần ID của mình -> Server trả về logs cá nhân (Server đã lọc theo employeeId)
                else repo.getEmployee().id.trim()
            } catch (e: Exception) { "" }

            // Nếu là User và không lấy được ID, thoát
            if (userRole == "USER" && employeeIdToFetch?.isBlank() == true) {
                withContext(Dispatchers.Main) { isLoading = false }
                return@launch
            }

            // ======================================================
            // TRUY VẤN SERVER (NGUỒN DUY NHẤT) CHO CẢ USER VÀ ADMIN
            // ======================================================
            // 🔥 GỌI API SERVER: NHẬN VỀ LIST DTO MỚI
            // *Lưu ý: Bạn cần sửa getServerLogs trong CardRepository để trả về List<HistoryLogEntry>
            val serverLogs: List<HistoryLogEntry> = try {
                repo.getServerLogs(employeeIdToFetch) // Giờ trả về List<HistoryLogEntry>
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }


            val tempAccess = mutableListOf<AccessLogEntry>()
            val tempTrans = mutableListOf<Transaction>()

            // 🔥 VÒNG LẶP SỬ DỤNG DTO MỚI
            serverLogs.forEach { log ->
                try {
                    val logOwnerName = log.name // Dùng thuộc tính DTO

                    val time = try {
                        // log.time là String chuẩn ISO (từ Server), parse thành LocalDateTime
                        LocalDateTime.parse(log.time)
                    } catch(e: Exception) {
                        // Xử lý lỗi nếu parse thất bại (nên là không xảy ra nếu Server đã sửa)
                        LocalDateTime.now()
                    }

                    val type = log.type
                    // 🔥 PARSE CÁC GIÁ TRỊ SỐ TỪ STRING SANG DOUBLE AN TOÀN
                    val amount = log.amount.toDoubleOrNull() ?: 0.0
                    val balanceAfter = log.balanceAfter.toDoubleOrNull() ?: 0.0

                    val desc = log.desc

                    // Phân loại Log
                    when (type) {
                        "CHECK_IN", "CHECK_OUT", "RESTRICTED" -> {
                            val accessType = when (type) {
                                "CHECK_IN" -> AccessType.CHECK_IN
                                "CHECK_OUT" -> AccessType.CHECK_OUT
                                else -> AccessType.RESTRICTED_AREA
                            }
                            // Hiển thị tên người sở hữu nếu là Admin (vì thấy log của người khác)
                            val finalDesc = if (userRole == "ADMIN") "$logOwnerName: $desc" else desc
                            tempAccess.add(AccessLogEntry(time, accessType, finalDesc))
                        }
                        "TOPUP", "PAYMENT" -> {
                            val txType = if (type == "TOPUP") TransactionType.TOP_UP else TransactionType.PAYMENT

                            tempTrans.add(
                                Transaction(
                                    time = time,
                                    type = txType,
                                    amount = amount,
                                    description = if (userRole == "ADMIN") "$logOwnerName: $desc" else desc,
                                    balanceAfter = balanceAfter
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                accessLogs = tempAccess.sortedByDescending { it.time }
                transactions = tempTrans.sortedByDescending { it.time }
                isLoading = false
            }
        }
    }

    // Tự động load khi vào màn hình hoặc đổi role
    LaunchedEffect(userRole) { loadHistory() }

    // Formatter hiển thị đẹp
    val dateFormatter = remember { DateTimeFormatter.ofPattern("HH:mm dd/MM") }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }

    // --- GIAO DIỆN ---
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- CỘT 1: LỊCH SỬ RA VÀO ---
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        // Hiển thị nhãn chính xác
                        Text(
                            text = if (userRole == "ADMIN") "Lịch sử Hệ Thống (Server)" else "Lịch sử Cá nhân (Server)",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { loadHistory() }) { Icon(Icons.Default.Refresh, "Reload") }
                    }
                    Divider(Modifier.padding(vertical = 12.dp))

                    if (accessLogs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Chưa có dữ liệu", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(accessLogs) { log ->
                                val icon = when (log.accessType) {
                                    AccessType.CHECK_IN -> Icons.Default.Login
                                    AccessType.CHECK_OUT -> Icons.Default.Logout
                                    else -> Icons.Default.AdminPanelSettings
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    ListItem(
                                        leadingContent = { Icon(icon, null) },
                                        headlineContent = { Text(log.description) },
                                        supportingContent = { Text(log.accessType.name) },
                                        trailingContent = { Text(log.time.format(dateFormatter), style = MaterialTheme.typography.bodySmall) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- CỘT 2: LỊCH SỬ GIAO DỊCH ---
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Lịch sử Giao dịch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Divider(Modifier.padding(vertical = 12.dp))

                    if (transactions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Chưa có giao dịch", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(transactions) { tx ->
                                val isTopUp = tx.type == TransactionType.TOP_UP
                                val amountColor = if (isTopUp) Color(0xFF2E7D32) else Color(0xFFC62828)
                                val sign = if (isTopUp) "+" else "-"
                                val icon = if (isTopUp) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    ListItem(
                                        leadingContent = { Icon(icon, null, tint = amountColor) },
                                        headlineContent = {
                                            Text(
                                                text = "$sign ${currencyFormatter.format(tx.amount)} đ",
                                                color = amountColor,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        },
                                        supportingContent = { Text(tx.description) },
                                        trailingContent = {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(tx.time.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                                                if (tx.balanceAfter > 0) {
                                                    Text(
                                                        "Dư: ${currencyFormatter.format(tx.balanceAfter)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}