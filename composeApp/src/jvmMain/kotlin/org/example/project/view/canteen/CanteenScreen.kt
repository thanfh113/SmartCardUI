package org.example.project.view.canteen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
// Giả định import này là cần thiết để fix lỗi DTO
import org.example.project.model.Product
import java.text.NumberFormat
import java.util.Locale

// DTO tạm thời cho UI (chứa ImageVector)
data class ProductItem(
    val name: String,
    val category: String,
    val price: Int,
    val icon: ImageVector
)

// HÀM ÁNH XẠ ICON
fun mapProductToItem(productDto: Product): ProductItem {
    val icon = when (productDto.category.trim().uppercase(Locale.ROOT)) {
        "MÓN CHÍNH" -> Icons.Default.Fastfood
        "ĐỒ UỐNG" -> {
            when (productDto.name.trim().uppercase(Locale.ROOT)) {
                "NƯỚC SUỐI", "NƯỚC NGỌT" -> Icons.Default.LocalDrink
                else -> Icons.Default.LocalCafe
            }
        }
        "ĂN VẶT" -> Icons.Default.LunchDining
        else -> Icons.Default.Star
    }
    return ProductItem(productDto.name, productDto.category, productDto.price, icon)
}

@Composable
fun CanteenScreen(
    userRole: String = "USER",
    onRequirePin: ((() -> Unit) -> Unit),
    onBalanceChanged: () -> Unit
) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var balanceVersion by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    val quickAmounts = listOf(10_000, 20_000, 50_000, 100_000, 200_000)

    var dynamicProducts by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoadingProducts by remember { mutableStateOf(true) }

    // 🔥 STATE MỚI: Dùng cho Dialog PIN Admin
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var adminPin by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var adminPinError by remember { mutableStateOf<String?>(null) }


    // LOGIC TẢI SẢN PHẨM TỪ REPOSITORY
    LaunchedEffect(Unit) {
        isLoadingProducts = true
        scope.launch(Dispatchers.IO) {
            val fetchedProducts = try {
                // Gọi hàm lấy Product DTOs từ Repository
                val productsDto = repo.getProducts()
                productsDto.map { mapProductToItem(it) }
            } catch (e: Exception) {
                println("Error loading products: ${e.message}")
                emptyList()
            }
            withContext(Dispatchers.Main) {
                dynamicProducts = fetchedProducts
                isLoadingProducts = false
            }
        }
    }

    // --- HÀM THANH TOÁN (Logic chung) ---
    val performPayment = { amount: Double ->
        scope.launch(Dispatchers.IO) {
            isProcessing = true

            val success = if (userRole == "ADMIN") {
                repo.adminTransaction("ADMIN01", -amount, "Admin Thanh toán")
            } else {
                repo.pay(amount, "Thanh toán dịch vụ")
            }

            withContext(Dispatchers.Main) {
                isProcessing = false
                if (success) {
                    message = "Thanh toán thành công ${formatMoney(amount)}"
                    balanceVersion++
                    onBalanceChanged()
                    amountText = ""
                } else {
                    message = "Lỗi thanh toán hoặc không đủ số dư!"
                }
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (userRole == "ADMIN") "Ví Server (Admin)" else "Ví Thẻ (User)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        WalletCard(balanceVersion, userRole)

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

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("Nhập số tiền") },
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
                                    Text(formatMoney(amt.toDouble()).replace(" VND", ""))
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- NÚT NẠP TIỀN (Không cần PIN) ---
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                scope.launch(Dispatchers.IO) {
                                    isProcessing = true

                                    val success = if (userRole == "ADMIN") {
                                        repo.adminTransaction("ADMIN01", amount, "Admin Nạp tiền")
                                    } else {
                                        repo.topUp(amount)
                                    }

                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (success) {
                                            message = "Đã nạp thành công ${formatMoney(amount)}"
                                            balanceVersion++
                                            onBalanceChanged()
                                            amountText = ""
                                        } else {
                                            message = "Lỗi nạp tiền! (Kiểm tra kết nối)"
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Icon(Icons.Default.AddCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nạp Tiền")
                    }

                    // --- NÚT THANH TOÁN ---
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                if (userRole == "ADMIN") {
                                    // 🔥 ADMIN: Mở Dialog PIN để xác thực Server
                                    adminPin = ""
                                    adminPinError = null
                                    showAdminPinDialog = true
                                } else {
                                    // USER: Gọi callback PIN (PIN Thẻ)
                                    onRequirePin { performPayment(amount) }
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

        // Thông báo kết quả
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

        // 🔥 DIALOG MỚI: XÁC THỰC PIN ADMIN CỤC BỘ
        if (showAdminPinDialog) {
            AdminPinInputDialog(
                pin = adminPin,
                error = adminPinError,
                isChecking = isVerifying,
                onPinChange = { adminPin = it },
                onDismiss = { showAdminPinDialog = false; adminPin = "" },
                onPinConfirmed = { pin ->
                    isVerifying = true
                    scope.launch(Dispatchers.IO) {
                        val isPinValid = repo.adminLogin("ADMIN01", pin) // Dùng adminLogin để xác thực
                        withContext(Dispatchers.Main) {
                            isVerifying = false
                            if (isPinValid) {
                                showAdminPinDialog = false
                                // Thực hiện payment sau khi xác thực thành công
                                performPayment(amountText.toDoubleOrNull() ?: 0.0)
                            } else {
                                adminPinError = "❌ PIN không đúng hoặc lỗi xác thực Server!"
                            }
                        }
                    }
                }
            )
        }


        // DANH SÁCH SẢN PHẨM
        if (isLoadingProducts) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ProductSection(
                products = dynamicProducts,
                onTotalAmountChange = { total ->
                    amountText = if (total > 0) total.toString() else ""
                }
            )
        }
    }
}

/**
 * Hiển thị số dư
 */
@Composable
fun WalletCard(version: Int, userRole: String) {
    val repo = CardRepositoryProvider.current

    var balance by remember { mutableStateOf(0.0) }

    LaunchedEffect(version, userRole) {
        if (userRole == "ADMIN") {
            balance = repo.getAdminBalance("ADMIN01")
        } else {
            balance = try { repo.getBalance() } catch (e: Exception) { 0.0 }
        }
    }

    val animatedBalance by animateIntAsState(balance.toInt())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = if (userRole == "ADMIN")
                        listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))
                    else
                        listOf(Color(0xFF0D47A1), Color(0xFF42A5F5))
                )
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (userRole == "ADMIN") "Số Dư Hệ Thống (Admin)" else "Số Dư Thẻ (User)",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(0.5f))

            Text(
                text = "${formatMoney(animatedBalance.toDouble())}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

// --- CÁC COMPOSENT PHỤ (Product List, v.v.) ---

// ProductItem đã được định nghĩa ở trên

@Composable
private fun ProductSection(
    products: List<ProductItem>,
    onTotalAmountChange: (Int) -> Unit
) {
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var page by remember { mutableStateOf(0) }

    val itemsPerPage = 4
    val totalPages = (products.size + itemsPerPage - 1) / itemsPerPage
    val pageItems = products.drop(page * itemsPerPage).take(itemsPerPage)

    val total = remember(quantities, products) {
        derivedStateOf {
            products.sumOf { item -> (quantities[item.name] ?: 0) * item.price }
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

            // Phân trang
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (page > 0) page-- }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                }
                Text("Trang ${page + 1} / $totalPages")
                IconButton(onClick = { if (page < totalPages - 1) page++ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                }
            }

            // Grid sản phẩm
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
                                onCardClick = { if (qty == 0) quantities[item.name] = 1 },
                                onIncrease = { quantities[item.name] = qty + 1 },
                                onDecrease = {
                                    val newQty = (qty - 1).coerceAtLeast(0)
                                    if (newQty == 0) quantities.remove(item.name)
                                    else quantities[item.name] = newQty
                                }
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
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
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${formatMoney(item.price.toDouble())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
            if (isSelected) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease) { Icon(Icons.Default.Remove, null) }
                    Text(quantity.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = onIncrease) { Icon(Icons.Default.Add, null) }
                }
            }
        }
    }
}

fun formatMoney(amount: Double): String {
    return try {
        NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    } catch (e: Exception) { "$amount VND" }
}


// 🔥 COMPOSABLE MỚI: KHUNG NHẬP PIN ADMIN ĐÃ TỐI ƯU UI
@Composable
fun AdminPinInputDialog(
    pin: String,
    error: String?,
    isChecking: Boolean,
    onPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

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
                        if (it.all { c -> c.isDigit() }) onPinChange(it)
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
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinConfirmed(pin) },
                enabled = pin.length >= 4 && !isChecking
            ) {
                Text(if (isChecking) "Đang xác thực..." else "Xác nhận")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isChecking) { Text("Hủy") } }
    )
}