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

    val performPayment = { amount: Double ->
        scope.launch(Dispatchers.IO) {
            isProcessing = true
            val success = if (userRole == "ADMIN") repo.adminTransaction("ADMIN01", -amount, "Canteen") else repo.pay(amount, "Canteen")
            withContext(Dispatchers.Main) {
                isProcessing = false
                if (success) {
                    lastTransactionItems = dynamicProducts.filter { (selectedQuantities[it.name] ?: 0) > 0 }.map { it to (selectedQuantities[it.name] ?: 0) }
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

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("Số tiền giao dịch") },
                    prefix = { Text("₫ ") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = { if(amountText.isNotEmpty()) IconButton(onClick = { amountText = "" }) { Icon(Icons.Default.Clear, null) } }
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gợi ý nhanh", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickAmounts.take(4).forEach { amt ->
                            SuggestionChip(
                                onClick = { amountText = amt.toString() },
                                label = { Text(formatMoney(amt.toDouble()).replace(" ₫", "")) }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                scope.launch(Dispatchers.IO) {
                                    isProcessing = true
                                    val ok = if (userRole == "ADMIN") repo.adminTransaction("ADMIN01", amt, "Nạp tiền") else repo.topUp(amt)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (ok) {
                                            statusMessage = "Đã nạp ${formatMoney(amt)} thành công!" to true
                                            balanceVersion++; onBalanceChanged(); amountText = ""
                                        } else statusMessage = "Nạp tiền thất bại!" to false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !isProcessing && amountText.isNotEmpty()
                    ) {
                        Icon(Icons.Default.AddCard, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nạp Tiền")
                    }

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                if (userRole == "ADMIN") {
                                    showAdminPinDialog = true
                                    adminPin = ""
                                    adminPinError = null
                                } else onRequirePin { performPayment(amt) }
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !isProcessing && amountText.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.ShoppingCartCheckout, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Thanh Toán")
                    }
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

        if (isLoadingProducts) LinearProgressIndicator(Modifier.fillMaxWidth())
        else ProductSection(products = dynamicProducts, quantities = selectedQuantities, onTotalAmountChange = { amountText = it.toString() })
    }

    // --- DIALOG HÓA ĐƠN ---
    if (showReceipt) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Receipt, null, Modifier.size(40.dp), tint = Color(0xFF2E7D32)) },
            title = { Text("Hóa đơn thanh toán", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider()
                    lastTransactionItems.forEach { (item, qty) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.name, modifier = Modifier.weight(1f)); Text("x$qty  ${formatMoney((item.price * qty).toDouble())}")
                        }
                    }
                    Divider()
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

    // --- DIALOG PIN ADMIN ĐÃ CẬP NHẬT ---
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

// --- CẬP NHẬT: COMPOSABLE PIN ADMIN PHONG CÁCH USER ---
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
        title = { Text("Xác thực mã PIN Admin", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vui lòng nhập mã PIN quản trị viên để hoàn tất thanh toán.", style = MaterialTheme.typography.bodyMedium)

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.all { c -> c.isDigit() }) onPinChange(it) },
                    label = { Text("Mã PIN Admin") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !isChecking,
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = "Toggle password visibility")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinConfirmed(pin) },
                enabled = !isChecking && pin.length >= 4,
                modifier = Modifier.widthIn(min = 100.dp)
            ) {
                if (isChecking) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isChecking) {
                Text("Hủy bỏ")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// --- GIỮ NGUYÊN CÁC COMPOSABLE KHÁC ---

@Composable
fun ProductSection(products: List<ProductItem>, quantities: MutableMap<String, Int>, onTotalAmountChange: (Int) -> Unit) {
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
fun ProductCard(modifier: Modifier, item: ProductItem, quantity: Int, onIncrease: () -> Unit, onDecrease: () -> Unit) {
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