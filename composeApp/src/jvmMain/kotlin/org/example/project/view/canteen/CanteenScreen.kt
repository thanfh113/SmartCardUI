package org.example.project.view.canteen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.Product
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.draw.alpha

// --- DTO & Mapper ---
data class ProductItem(
    val name: String,
    val category: String,
    val price: Int,
    val icon: ImageVector
)

fun mapProductToItem(productDto: Product): ProductItem {
    val icon = when (productDto.category.trim().uppercase(Locale.ROOT)) {
        "MÓN CHÍNH" -> Icons.Default.Fastfood
        "ĐỒ UỐNG" -> if (productDto.name.contains("Nước", true)) Icons.Default.LocalDrink else Icons.Default.LocalCafe
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
    var isAmountEnabled by remember { mutableStateOf(false) }
    var showBillPreview by remember { mutableStateOf(false) }

    val selectedQuantities = remember { mutableStateMapOf<String, Int>() }
    val quickAmounts = listOf(10_000, 20_000, 50_000, 100_000, 200_000, 500_000)

    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showReceipt by remember { mutableStateOf(false) }
    var lastTransactionItems by remember { mutableStateOf<List<Pair<ProductItem, Int>>>(emptyList()) }

    var balanceVersion by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var dynamicProducts by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoadingProducts by remember { mutableStateOf(true) }

    // Admin PIN Dialog states
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var adminPin by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var adminPinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(4000)
            statusMessage = null
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val fetched = try { repo.getProducts().map { mapProductToItem(it) } } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                dynamicProducts = fetched
                isLoadingProducts = false
            }
        }
    }

    // 🔥 HÀM THỰC HIỆN THANH TOÁN (ĐÃ SỬA LOGIC HÓA ĐƠN)
    val performPayment = { amount: Double ->
        scope.launch(Dispatchers.IO) {
            isProcessing = true
            val success = if (userRole == "ADMIN") repo.adminTransaction("ADMIN01", -amount, "Canteen") else repo.pay(amount, "Canteen")

            withContext(Dispatchers.Main) {
                isProcessing = false
                if (success) {
                    // Lọc danh sách món ăn thực tế đã chọn
                    val selectedItems = dynamicProducts
                        .filter { (selectedQuantities[it.name] ?: 0) > 0 }
                        .map { it to (selectedQuantities[it.name] ?: 0) }

                    // 🔥 NẾU KHÔNG CHỌN MÓN (CHỈ NHẬP TIỀN THỦ CÔNG) -> TẠO ITEM GIẢ ĐỂ HIỆN HÓA ĐƠN
                    lastTransactionItems = if (selectedItems.isEmpty()) {
                        listOf(ProductItem("Thanh toán dịch vụ", "Dịch vụ", amount.toInt(), Icons.Default.Payments) to 1)
                    } else {
                        selectedItems
                    }

                    showReceipt = true
                    balanceVersion++
                    onBalanceChanged()
                    statusMessage = "Thanh toán thành công!" to true
                } else {
                    statusMessage = "Thanh toán thất bại! Số dư không đủ." to false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Canteen & Dịch vụ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        WalletCard(balanceVersion, userRole)

        // === CHẾ ĐỘ NẠP TIỀN (isAmountEnabled = true) ===
        if (isAmountEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFFFF8F00), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("CHẾ ĐỘ NẠP TIỀN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                        label = { Text("Số tiền cần nạp") },
                        prefix = { Text("₫ ") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (amountText.isNotEmpty()) {
                                IconButton(onClick = { amountText = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF8F00),
                            focusedLabelColor = Color(0xFFFF8F00)
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chọn nhanh", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE65100).copy(alpha = 0.7f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickAmounts.take(3).forEach { amt ->
                                SuggestionChip(
                                    onClick = { amountText = amt.toString() },
                                    label = { Text(formatMoney(amt.toDouble()).replace(" ₫", "")) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color.White,
                                        labelColor = Color(0xFFE65100)
                                    )
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                isAmountEnabled = false
                                amountText = ""
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Hủy")
                        }

                        Button(
                            onClick = {
                                val amt = amountText.toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    scope.launch(Dispatchers.IO) {
                                        if (userRole != "ADMIN" && repo.isCardLocked()) {
                                            withContext(Dispatchers.Main) {
                                                statusMessage = "❌ THẺ ĐÃ BỊ KHÓA! Không thể thực hiện nạp tiền." to false
                                            }
                                            return@launch
                                        }

                                        isProcessing = true
                                        val ok = if (userRole == "ADMIN") repo.adminTransaction("ADMIN01", amt, "Nạp tiền") else repo.topUp(amt)

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            if (ok) {
                                                statusMessage = "✅ Đã nạp ${formatMoney(amt)} thành công!" to true
                                                balanceVersion++
                                                onBalanceChanged()
                                                amountText = ""
                                                isAmountEnabled = false
                                            } else {
                                                statusMessage = "❌ Nạp tiền thất bại!" to false
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !isProcessing && amountText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Xác nhận nạp", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        // === CHẾ ĐỘ THANH TOÁN (isAmountEnabled = false) ===
        else {
            // Hiển thị tổng tiền thanh toán nếu có
            val totalAmount = amountText.toIntOrNull() ?: 0
            if (totalAmount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tổng tiền thanh toán", style = MaterialTheme.typography.labelLarge, color = Color(0xFF1B5E20).copy(alpha = 0.7f))
                                Text(formatMoney(totalAmount.toDouble()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(40.dp), tint = Color(0xFF2E7D32).copy(alpha = 0.6f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isAmountEnabled = true
                                    amountText = ""
                                    selectedQuantities.clear()
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8F00))
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Nạp tiền")
                            }

                            Button(
                                onClick = {
                                    showBillPreview = true
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Payment, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Thanh toán", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Nếu chưa có tổng tiền, chỉ hiển thị nút nạp tiền
                Button(
                    onClick = {
                        isAmountEnabled = true
                        amountText = ""
                        selectedQuantities.clear()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nạp tiền", color = Color.White)
                }
            }
        }

        statusMessage?.let { (msg, isSuccess) ->
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (isSuccess) Color(0xFF2E7D32) else Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Text(msg, color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Hiển thị ProductSection hoặc thông báo
        if (!isAmountEnabled) {
            if (isLoadingProducts) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ProductSection(
                    products = dynamicProducts,
                    quantities = selectedQuantities,
                    onTotalAmountChange = { total ->
                        amountText = total.toString()
                    }
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Color(0xFFFFF3E0).copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(40.dp), tint = Color(0xFFFF8F00).copy(alpha = 0.7f))
                        Text("Chế độ nạp tiền đang được kích hoạt", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE65100), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        Text("Vui lòng hoàn tất hoặc hủy để tiếp tục mua hàng", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100).copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    // --- DIALOG XEM BILL TRƯỚC KHI THANH TOÁN ---
    if (showBillPreview) {
        val previewItems = dynamicProducts
            .filter { (selectedQuantities[it.name] ?: 0) > 0 }
            .map { it to (selectedQuantities[it.name] ?: 0) }
        val totalPreview = if (previewItems.isEmpty()) {
            amountText.toIntOrNull() ?: 0
        } else {
            previewItems.sumOf { it.first.price * it.second }
        }

        AlertDialog(
            onDismissRequest = { showBillPreview = false },
            icon = { Icon(Icons.Default.Receipt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Xác nhận đơn hàng", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vui lòng kiểm tra thông tin trước khi thanh toán:", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    
                    if (previewItems.isNotEmpty()) {
                        previewItems.forEach { (item, qty) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text("${formatMoney(item.price.toDouble())} x $qty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatMoney((item.price * qty).toDouble()), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Thanh toán dịch vụ", modifier = Modifier.weight(1f))
                            Text(formatMoney(totalPreview.toDouble()), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TỔNG CỘNG", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(formatMoney(totalPreview.toDouble()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBillPreview = false
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        scope.launch(Dispatchers.IO) {
                            if (userRole != "ADMIN" && repo.isCardLocked()) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "❌ THẺ ĐÃ BỊ KHÓA! Không thể thanh toán." to false
                                }
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                if (userRole == "ADMIN") {
                                    showAdminPinDialog = true
                                    adminPin = ""
                                    adminPinError = null
                                } else {
                                    onRequirePin { performPayment(amt) }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Xác nhận & Nhập PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBillPreview = false }) {
                    Text("Quay lại")
                }
            }
        )
    }

    // --- DIALOG HÓA ĐƠN SAU KHI THANH TOÁN THÀNH CÔNG ---
    if (showReceipt) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Receipt, null, Modifier.size(40.dp), tint = Color(0xFF2E7D32)) },
            title = { Text("Hóa đơn thanh toán", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    lastTransactionItems.forEach { (item, qty) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.name, modifier = Modifier.weight(1f))
                            Text("x$qty  ${formatMoney((item.price * qty).toDouble())}")
                        }
                    }
                    HorizontalDivider()
                    Text("Tổng cộng: ${formatMoney(lastTransactionItems.sumOf { it.first.price * it.second }.toDouble())}",
                        fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = Color(0xFF2E7D32))
                }
            },
            confirmButton = {
                Button(onClick = { showReceipt = false; selectedQuantities.clear(); amountText = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hoàn tất & Làm mới")
                }
            }
        )
    }

    // --- DIALOG PIN ADMIN ---
    if (showAdminPinDialog) {
        AdminPinInputDialog(
            pin = adminPin,
            error = adminPinError,
            isChecking = isVerifying,
            onPinChange = { adminPin = it },
            onDismiss = { showAdminPinDialog = false },
            onPinConfirmed = { pin ->
                isVerifying = true
                scope.launch(Dispatchers.IO) {
                    val valid = repo.adminLogin("ADMIN01", pin)
                    withContext(Dispatchers.Main) {
                        isVerifying = false
                        if (valid) {
                            showAdminPinDialog = false
                            performPayment(amountText.toDoubleOrNull() ?: 0.0)
                        } else {
                            adminPinError = "Mã PIN Admin không đúng!"
                        }
                    }
                }
            }
        )
    }
}

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
        icon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Xác thực PIN Admin", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nhập PIN quản trị viên để hoàn tất thanh toán.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.all { c -> c.isDigit() }) onPinChange(it) },
                    label = { Text("PIN Admin") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(icon, null) }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) Text(error, color = Color.Red, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = { onPinConfirmed(pin) }, enabled = !isChecking && pin.length >= 4) {
                if (isChecking) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White) else Text("Xác nhận")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isChecking) { Text("Hủy") } }
    )
}

@Composable
fun ProductSection(products: List<ProductItem>,
                   quantities: MutableMap<String, Int>,
                   onTotalAmountChange: (Int) -> Unit
) {
    val total = products.sumOf { (quantities[it.name] ?: 0) * it.price }
    LaunchedEffect(total) { onTotalAmountChange(total) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chọn món thực đơn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        products.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    val qty = quantities[item.name] ?: 0
                    ProductCard(modifier = Modifier.weight(1f), item = item, quantity = qty,
                        onIncrease = { quantities[item.name] = qty + 1 },
                        onDecrease = { if (qty > 1) quantities[item.name] = qty - 1 else quantities.remove(item.name) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProductCard(modifier: Modifier,
                item: ProductItem,
                quantity: Int,
                onIncrease: () -> Unit,
                onDecrease: () -> Unit) {
    Card(
        modifier = modifier.height(115.dp),
        onClick = { if (quantity == 0) onIncrease() },
        colors = CardDefaults.cardColors(containerColor = if (quantity > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp)
            }
            Text(formatMoney(item.price.toDouble()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (quantity > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.primary) }
                    Text("$quantity", fontWeight = FontWeight.Bold)
                    IconButton(onClick = onIncrease, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun WalletCard(version: Int, userRole: String) {
    val repo = CardRepositoryProvider.current
    var balance by remember { mutableStateOf(0.0) }
    LaunchedEffect(version) { balance = if (userRole == "ADMIN") repo.getAdminBalance("ADMIN01") else repo.getBalance() }
    val animBal by animateIntAsState(balance.toInt())

    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF1E88E5)))).padding(20.dp)) {
        Column {
            Text("Số dư khả dụng", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Text(formatMoney(animBal.toDouble()), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun formatMoney(amount: Double): String = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)