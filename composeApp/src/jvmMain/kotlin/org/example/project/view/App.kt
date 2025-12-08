package org.example.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.data.CardRepositoryProvider
import org.example.project.view.access.AccessControlScreen
import org.example.project.view.canteen.CanteenScreen
import org.example.project.view.common.PinDialog
import org.example.project.view.employee.EmployeeScreen
import org.example.project.view.history.HistoryScreen
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.view.common.CreatePinDialog

enum class MainScreen(val title: String, val icon: ImageVector) {
    SCAN("Kết nối", Icons.Default.SettingsInputAntenna),
    EMPLOYEE_INFO("Hồ sơ", Icons.Default.Person),
    ACCESS_CONTROL("Ra/Vào", Icons.Default.Security),
    CANTEEN("Căng tin", Icons.Default.Restaurant),
    HISTORY("Lịch sử", Icons.Default.History)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DesktopApp() {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(MainScreen.SCAN) }

    // State quản lý kết nối
    var isAuthenticated by remember { mutableStateOf(false) }
    var isCardGenuine by remember { mutableStateOf<Boolean?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(false) }

    // State quản lý trạng thái thẻ (PIN, Khóa, Số dư)
    var cardState by remember { mutableStateOf(repo.getCardState()) }
    var forceEditProfile by remember { mutableStateOf(false) }

    // Hàm ép cập nhật lại trạng thái thẻ từ phần cứng
    fun refreshCardState() {
        // Lấy state mới từ repo
        val newState = repo.getCardState()
        // QUAN TRỌNG: .copy() tạo ra một instance mới để Compose nhận biết thay đổi và vẽ lại UI
        // Nếu không có .copy(), Compose có thể nghĩ object chưa đổi và không cập nhật dòng chữ đỏ.
        cardState = newState.copy()
    }

    // Dialog state
    var showPinDialog by remember { mutableStateOf(false) }
    var showCreatePinDialog by remember { mutableStateOf(false) }
    var showActionPinDialog by remember { mutableStateOf(false) }
    var pendingAction: (() -> Unit)? by remember { mutableStateOf(null) }

    if (showCreatePinDialog) {
        CreatePinDialog(
            onDismiss = {
                // Nếu bấm hủy thì quay về màn hình chờ, ngắt kết nối
                showCreatePinDialog = false
                isConnected = false
                repo.disconnect()
            },
            onConfirm = { newPin ->
                // Chạy setup dưới background
                scope.launch(Dispatchers.IO) {
                    val success = repo.setupFirstPin(newPin)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            // 1) Setup PIN xong => đăng nhập luôn
                            isAuthenticated = true
                            showCreatePinDialog = false

                            // 2) Khởi tạo thông tin nhân viên: sinh ID NV00x + ghi xuống thẻ
                            try {
                                repo.initEmployeeAfterActivation()
                            } catch (e: Exception) {
                                connectionError = "Lỗi khi khởi tạo thông tin nhân viên: ${e.message}"
                            }

                            // 3) Cập nhật state thẻ + chuyển sang màn hồ sơ
                            refreshCardState()
                            currentScreen = MainScreen.EMPLOYEE_INFO

                            // 4) Báo cho màn EmployeeScreen rằng cần mở ChangeProfileDialog lần đầu
                            forceEditProfile = true
                        } else {
                            connectionError = "Lỗi khi kích hoạt thẻ!"
                            showCreatePinDialog = false
                            isConnected = false
                        }
                    }
                }
            }
        )
    }
    // --- LOGIC XỬ LÝ PIN (Đã sửa để cập nhật UI khi nhập sai) ---
    if (showPinDialog) {
        PinDialog(
            title = "Mở khóa thẻ",
            cardState = cardState,
            onDismiss = { /* Không cho tắt */ },
            onPinOk = { pin ->
                // ✅ CHẠY TRÊN BACKGROUND THREAD
                scope.launch(Dispatchers.IO) {
                    val ok = repo.verifyPin(pin)

                    // Cập nhật UI trên Main Thread
                    withContext(Dispatchers.Main) {
                        refreshCardState() // Update số lần sai
                        if (ok) {
                            isAuthenticated = true
                            showPinDialog = false
                            currentScreen = MainScreen.EMPLOYEE_INFO
                        }
                        // Nếu sai, PinDialog sẽ tự tắt loading nhờ LaunchedEffect(cardState)
                    }
                }
            }
        )
    }

    if (showActionPinDialog) {
        PinDialog(
            title = "Xác nhận giao dịch",
            cardState = cardState,
            onDismiss = { showActionPinDialog = false },
            onPinOk = { pin ->
                // ✅ CHẠY TRÊN BACKGROUND THREAD
                scope.launch(Dispatchers.IO) {
                    val ok = repo.verifyPin(pin)

                    withContext(Dispatchers.Main) {
                        refreshCardState()
                        if (ok) {
                            showActionPinDialog = false
                            pendingAction?.invoke()
                            pendingAction = null
                        }
                    }
                }
            }
        )
    }

    // --- GIAO DIỆN CHÍNH ---
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // 1. THANH ĐIỀU HƯỚNG BÊN TRÁI (Chỉ hiện khi đã đăng nhập)
        if (isAuthenticated && isConnected) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                header = {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.padding(vertical = 24.dp).size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                // Chỉ hiện các tab chức năng, bỏ tab SCAN
                MainScreen.values().filter { it != MainScreen.SCAN }.forEach { screen ->
                    NavigationRailItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                        alwaysShowLabel = true,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))

                // Nút Đăng xuất / Thoát
                NavigationRailItem(
                    selected = false,
                    onClick = { exitProcess(0) },
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Thoát") },
                    label = { Text("Thoát") }
                )
            }
        }

        // 2. NỘI DUNG CHÍNH (Bên phải)
        Box(
            modifier = Modifier.weight(1f).fillMaxSize().padding(if (isAuthenticated) 24.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            // Logic hiển thị màn hình
            if (!isConnected || currentScreen == MainScreen.SCAN || !isAuthenticated) {
                // --- MÀN HÌNH CHỜ / QUÉT THẺ (Welcome Screen) ---
                WelcomeScreen(
                    isConnected = isConnected,
                    connectionError = connectionError,
                    isGenuine = isCardGenuine,
                    onConnect = {
                        connectionError = null
                        val connected = repo.connect()
                        isConnected = connected
                        if (!connected) {
                            connectionError = "Không tìm thấy đầu đọc thẻ!"
                            return@WelcomeScreen
                        }

                        val genuine = repo.authenticateCard()
                        isCardGenuine = genuine
                        if (!genuine) {
                            connectionError = "Cảnh báo: Thẻ không hợp lệ!"
                            return@WelcomeScreen
                        }

                        scope.launch(Dispatchers.IO) {
                            val isInitialized = repo.checkCardInitialized()

                            withContext(Dispatchers.Main) {
                                if (!isInitialized) {
                                    // -> Chưa có PIN -> Hiện Dialog tạo mới
                                    showCreatePinDialog = true
                                } else {
                                    // -> Đã có PIN -> Hiện Dialog đăng nhập
                                    refreshCardState()
                                    showPinDialog = true
                                }
                            }
                        }
                    }
                )
            } else {
                // --- CÁC MÀN HÌNH CHỨC NĂNG ---
                // Hiệu ứng chuyển cảnh mượt mà
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() with fadeOut() }
                ) { screen ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp, // Đổ bóng cho khung nội dung
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        when (screen) {
                            MainScreen.EMPLOYEE_INFO -> EmployeeScreen(
                                onChangePin = { act -> pendingAction = act; showActionPinDialog = true },
                                isAuthenticated = isAuthenticated,
                                forceEditProfile = forceEditProfile,
                                onForceEditConsumed = { forceEditProfile = false }
                            )
                            MainScreen.ACCESS_CONTROL -> AccessControlScreen(
                                onRestrictedArea = { act -> pendingAction = act; showActionPinDialog = true }
                            )
                            MainScreen.CANTEEN -> CanteenScreen(
                                onRequirePin = { act -> pendingAction = act; showActionPinDialog = true },
                                onBalanceChanged = { refreshCardState() }
                            )
                            MainScreen.HISTORY -> HistoryScreen()
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

// Layout riêng cho màn hình chờ cho đẹp
@Composable
fun WelcomeScreen(
    isConnected: Boolean,
    connectionError: String?,
    isGenuine: Boolean?,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // Logo / Icon lớn
            Icon(
                imageVector = if (isConnected) Icons.Default.VerifiedUser else Icons.Default.Sensors,
                contentDescription = null,
                modifier = Modifier.size(120.dp).padding(bottom = 24.dp),
                tint = if (connectionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            Text(
                "Hệ Thống Thẻ Thông Minh",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (connectionError != null) connectionError!!
                else if (isGenuine == true) "Đang xác thực người dùng..."
                else "Vui lòng đặt thẻ lên đầu đọc",
                style = MaterialTheme.typography.bodyLarge,
                color = if (connectionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // Nút bấm lớn
            if (!isConnected || connectionError != null) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kết nối & Quét thẻ", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}