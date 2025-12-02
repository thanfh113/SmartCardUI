package org.example.project.view.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.TransactionType
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HistoryScreen() {
    val repo = CardRepositoryProvider.current

    // --- SỬA Ở ĐÂY: Sắp xếp giảm dần theo thời gian (Mới nhất lên đầu) ---
    val accessLogs = remember {
        repo.getAccessLogs().sortedByDescending { it.time }
    }
    val transactions = remember {
        repo.getTransactions().sortedByDescending { it.time }
    }
    // ----------------------------------------------------------------------

    val dateFormatter = remember { DateTimeFormatter.ofPattern("HH:mm dd/MM") }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- CỘT 1: LỊCH SỬ RA VÀO ---
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Lịch sử Ra/Vào", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Divider(Modifier.padding(vertical = 12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(accessLogs) { log ->
                        val icon = if (log.accessType.name.contains("IN")) Icons.Default.Login else Icons.Default.Logout

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

        // --- CỘT 2: LỊCH SỬ GIAO DỊCH ---
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Lịch sử Giao dịch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Divider(Modifier.padding(vertical = 12.dp))

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
                                leadingContent = {
                                    Icon(icon, null, tint = amountColor)
                                },
                                headlineContent = {
                                    Text(
                                        text = "$sign ${currencyFormatter.format(tx.amount)} đ",
                                        color = amountColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    Text(tx.description)
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(tx.time.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Dư: ${currencyFormatter.format(tx.balanceAfter)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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