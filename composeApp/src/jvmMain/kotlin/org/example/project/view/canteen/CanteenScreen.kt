package org.example.project.view.canteen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.CardRepositoryProvider
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CanteenScreen(
    onRequirePin: ((() -> Unit) -> Unit),
    onBalanceChanged: () -> Unit
) {
    val repo = CardRepositoryProvider.current
    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var balanceVersion by remember { mutableStateOf(0) } // Trigger reload balance

    // Các mệnh giá gợi ý
    val quickAmounts = listOf(10_000, 20_000, 50_000, 100_000, 200_000)

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- CỘT TRÁI: VÍ & SỐ DƯ ---
        Column(
            modifier = Modifier.width(320.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            WalletCard(balanceVersion)

            // Thông báo trạng thái
            if (message != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(message!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // --- CỘT PHẢI: THAO TÁC ---
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("Giao dịch", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

                // Input số tiền
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("Nhập số tiền (VNĐ)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                    trailingIcon = {
                        if (amountText.isNotEmpty()) {
                            IconButton(onClick = { amountText = "" }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true
                )

                // Chips chọn nhanh
                Text("Chọn nhanh mệnh giá:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickAmounts.forEach { amt ->
                        SuggestionChip(
                            onClick = { amountText = amt.toString() },
                            label = { Text(NumberFormat.getNumberInstance(Locale.US).format(amt)) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Các nút hành động
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                val ok = repo.topUp(amount)
                                message = if (ok) "Đã nạp thành công ${formatMoney(amount)}" else "Lỗi nạp tiền!"
                                if (ok) { balanceVersion++; onBalanceChanged(); amountText = "" }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.AddCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nạp Tiền")
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                onRequirePin {
                                    val ok = repo.pay(amount, "Thanh toán dịch vụ")
                                    message = if (ok) "Thanh toán thành công ${formatMoney(amount)}" else "Số dư không đủ!"
                                    if (ok) { balanceVersion++; onBalanceChanged(); amountText = "" }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ShoppingCart, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Thanh Toán")
                    }
                }
            }
        }
    }
}

@Composable
fun WalletCard(version: Int) {
    val repo = CardRepositoryProvider.current
    val balance = remember(version) { repo.getBalance().toLong() }
    val animatedBalance by animateIntAsState(balance.toInt())

    // Card giả lập thẻ ngân hàng
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)) // Gradient xanh lá
                )
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("E-Wallet", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("Số dư khả dụng", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Text(
                "${formatMoney(animatedBalance.toDouble())} VNĐ",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatMoney(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount)
}