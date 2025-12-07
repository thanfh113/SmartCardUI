package org.example.project.view.canteen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Thanh tiêu đề trên cùng
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Wallet & Points",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // --- THẺ SỐ DƯ ---
        WalletCard(balanceVersion)

        // --- THẺ GIAO DỊCH ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Giao dịch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                // Input số tiền
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("Nhập số tiền") },
                    //leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                    trailingIcon = {
                        if (amountText.isNotEmpty()) {
                            IconButton(onClick = { amountText = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true
                )

                // Chips chọn nhanh
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Chọn nhanh mệnh giá",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickAmounts.forEach { amt ->
                            SuggestionChip(
                                onClick = { amountText = amt.toString() },
                                label = {
                                    Text(
                                        NumberFormat.getNumberInstance(Locale.US).format(amt)
                                    )
                                }
                            )
                        }
                    }
                }

                // Các nút hành động
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                val ok = repo.topUp(amount)
                                message = if (ok) {
                                    "Đã nạp thành công ${formatMoney(amount)}"
                                } else {
                                    "Lỗi nạp tiền!"
                                }
                                if (ok) {
                                    balanceVersion++
                                    onBalanceChanged()
                                    amountText = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
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
                                    message = if (ok) {
                                        "Thanh toán thành công ${formatMoney(amount)}"
                                    } else {
                                        "Số dư không đủ!"
                                    }
                                    if (ok) {
                                        balanceVersion++
                                        onBalanceChanged()
                                        amountText = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.ShoppingCart, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Thanh Toán")
                    }
                }
            }
        }

        // Thông báo trạng thái (nếu có)
        if (message != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        message!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // --- DANH SÁCH SẢN PHẨM (NHIỀU MÓN + SỐ LƯỢNG) ---
        ProductSection(
            onTotalAmountChange = { total ->
                // Mỗi lần tổng tiền giỏ hàng thay đổi -> cập nhật vào ô số tiền
                amountText = if (total > 0) total.toString() else ""
            }
        )
    }
}

@Composable
fun WalletCard(version: Int) {
    val repo = CardRepositoryProvider.current
    val balance = remember(version) { repo.getBalance().toLong() }
    val animatedBalance by animateIntAsState(balance.toInt())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D47A1),
                        Color(0xFF42A5F5)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    null,
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Current Balance",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(0.5f))

            Text(
                "${formatMoney(animatedBalance.toDouble())} VND",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

private data class ProductItem(
    val name: String,
    val category: String,
    val price: Int,
    val icon: ImageVector
)

// Compose chon đồ ăn
@Composable
private fun ProductSection(
    onTotalAmountChange: (Int) -> Unit
) {
    val products = listOf(
        ProductItem("Cà Phê Sữa", "Đồ uống", 25_000, Icons.Default.LocalCafe),
        ProductItem("Trà Đào", "Đồ uống", 30_000, Icons.Default.LocalDrink),
        ProductItem("Bánh Tiramisu", "Đồ ăn", 45_000, Icons.Default.Cake),
        ProductItem("Cơm Gà", "Đồ ăn", 50_000, Icons.Default.Fastfood),
        ProductItem("Hồng Trà Sữa", "Đồ uống", 28_000, Icons.Default.LocalCafe),
        ProductItem("Mì Ý Bò Bằm", "Đồ ăn", 55_000, Icons.Default.RamenDining),
        ProductItem("Bánh Mì Trứng", "Đồ ăn", 20_000, Icons.Default.LunchDining),
        ProductItem("Nước Suối", "Đồ uống", 10_000, Icons.Default.WaterDrop)
    )

    val quantities = remember { mutableStateMapOf<String, Int>() }

    var page by remember { mutableStateOf(0) }
    val itemsPerPage = 4
    val totalPages = (products.size + itemsPerPage - 1) / itemsPerPage

    val pageItems = products.drop(page * itemsPerPage).take(itemsPerPage)

    // Tính tổng tiền
    val total = remember(quantities) {
        derivedStateOf {
            products.sumOf { item ->
                val q = quantities[item.name] ?: 0
                item.price * q
            }
        }
    }

    LaunchedEffect(total.value) {
        onTotalAmountChange(total.value)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text("Danh sách sản phẩm", style = MaterialTheme.typography.titleMedium)
            Text("Available items to purchase",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)

            // --- NÚT CHUYỂN TRANG ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (page > 0) page-- }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                }

                Text("Trang ${page + 1} / $totalPages")

                IconButton(onClick = { if (page < totalPages - 1) page++ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                }
            }

            // --- GRID 2×2 mỗi trang ---
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                pageItems.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { item ->
                            val qty = quantities[item.name] ?: 0

                            ProductCard(
                                modifier = Modifier.weight(1f),
                                item = item,
                                quantity = qty,
                                onCardClick = {
                                    if (qty == 0) quantities[item.name] = 1
                                },
                                onIncrease = {
                                    quantities[item.name] = qty + 1
                                },
                                onDecrease = {
                                    val newQty = (qty - 1).coerceAtLeast(0)
                                    if (newQty == 0) quantities.remove(item.name)
                                    else quantities[item.name] = newQty
                                }
                            )
                        }

                        if (row.size == 1)
                            Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Text(
                "Lướt trái/phải để xem thêm món, chọn số lượng rồi bấm Thanh Toán.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ProductCard(
    modifier: Modifier = Modifier,
    item: ProductItem,
    quantity: Int,
    onCardClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val isSelected = quantity > 0

    Card(
        modifier = modifier.height(120.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = onCardClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatMoney(item.price.toDouble())} VND",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Stepper số lượng
            if (isSelected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDecrease,
                        enabled = quantity > 0
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Giảm")
                    }
                    Text(
                        quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = onIncrease) {
                        Icon(Icons.Default.Add, contentDescription = "Tăng")
                    }
                }
            }
        }
    }
}

fun formatMoney(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount)
}
