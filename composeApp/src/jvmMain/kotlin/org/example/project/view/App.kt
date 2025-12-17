package org.example.project.view

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.view.access.AccessControlScreen
import org.example.project.view.admin.AdminScreen
import org.example.project.view.canteen.CanteenScreen
import org.example.project.view.common.CreatePinDialog
import org.example.project.view.common.PinDialog
import org.example.project.view.employee.EmployeeScreen
import org.example.project.view.employee.EmployeeViewModel
import org.example.project.view.history.HistoryScreen
import kotlin.system.exitProcess

enum class MainScreen(val title: String, val icon: ImageVector) {
    SCAN("Kết nối", Icons.Default.SettingsInputAntenna),
    EMPLOYEE_INFO("Hồ sơ", Icons.Default.Person),
    ACCESS_CONTROL("Ra/Vào", Icons.Default.Security),
    CANTEEN("Căng tin", Icons.Default.Restaurant),
    HISTORY("Lịch sử", Icons.Default.History),
    ADMIN("Quản lý thẻ", Icons.Default.AdminPanelSettings)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DesktopApp(isAdminLauncher: Boolean = false) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    // Helper: safely extract 'role' from server response via reflection (works for data class, POJO or Map-like)
    // Giữ nguyên hàm này nếu bạn chưa sửa Employee DTO để chứa 'role'
    fun extractRole(emp: Any?): String? {
        if (emp == null) return null
        return try {
            val cls = emp::class.java
            // try field first
            try {
                val field = cls.getDeclaredField("role")
                field.isAccessible = true
                field.get(emp) as? String
            } catch (e: NoSuchFieldException) {
                // try getter
                val getter = cls.methods.firstOrNull { it.name.equals("getRole", ignoreCase = true) }
                getter?.invoke(emp) as? String
            }
        } catch (e: Exception) {
            null
        }
    }

    // Nếu là Admin Launcher, mặc định vào màn Admin, ngược lại vào Scan
    var currentScreen by remember {
        mutableStateOf(if (isAdminLauncher) MainScreen.ADMIN else MainScreen.SCAN)
    }

    // State chung
    var isAuthenticated by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // State thẻ (chỉ dùng cho User)
    var cardState by remember { mutableStateOf(repo.getCardState()) }
    var forceEditProfile by remember { mutableStateOf(false) }

    // State phân quyền: Nếu chạy Launcher Admin -> Role là ADMIN, ngược lại là USER
    var userRole by remember { mutableStateOf(if (isAdminLauncher) "ADMIN" else "USER") }

    // State login Admin: Nếu là Launcher Admin -> Tự hiện dialog login ngay khi mở app
    var showAdminLogin by remember { mutableStateOf(isAdminLauncher) }

    // Dialog state
    var showPinDialog by remember { mutableStateOf(false) }
    var showCreatePinDialog by remember { mutableStateOf(false) }
    var showActionPinDialog by remember { mutableStateOf(false) }
    var pendingAction: (() -> Unit)? by remember { mutableStateOf(null) }

    // 🔥 STATE MỚI: Buộc đổi PIN
    var showForcePinChangeDialog by remember { mutableStateOf(false) }
    // 🔥 STATE MỚI: Lưu PIN mặc định (oldPin) vừa nhập
    var pendingOldPin by remember { mutableStateOf<String?>(null) }

    // Hàm refresh trạng thái thẻ (chỉ có tác dụng khi User dùng thẻ thật)
    fun refreshCardState() {
        if (userRole != "ADMIN") {
            try {
                val newState = repo.getCardState()
                cardState = newState.copy()
            } catch (e: Exception) { /* Bỏ qua lỗi nếu mất kết nối thẻ */ }
        }
    }


    // --- CÁC DIALOG KÍCH HOẠT/NHẬP PIN CHO USER ---
    // (Khối CreatePinDialog giữ nguyên)
    if (showCreatePinDialog) {
        CreatePinDialog(
            onDismiss = {
                showCreatePinDialog = false
                isConnected = false
                repo.disconnect()
            },
            onConfirm = { newPin ->
                scope.launch(Dispatchers.IO) {
                    val success = repo.setupFirstPin(newPin)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            isAuthenticated = true
                            showCreatePinDialog = false
                            try { repo.initEmployeeAfterActivation() } catch (_: Exception) {}
                            refreshCardState()
                            currentScreen = MainScreen.EMPLOYEE_INFO
                            forceEditProfile = true
                        } else {
                            connectionError = "Lỗi kích hoạt thẻ!"
                            isConnected = false
                        }
                    }
                }
            }
        )
    }

    // 🔥 SỬA: Logic PinDialog để kiểm tra PIN mặc định và lưu PIN
    if (showPinDialog) {
        PinDialog(
            title = "Mở khóa thẻ (User)",
            cardState = cardState,
            onDismiss = { /* Bắt buộc nhập mới vào được */ },
            onPinOk = { pin ->
                scope.launch(Dispatchers.IO) {
                    val ok = repo.verifyPin(pin) // 1. Xác thực PIN (Offline)

                    withContext(Dispatchers.Main) {
                        refreshCardState()
                        if (ok) {
                            val isAuthenticatedByRSA = repo.authenticateCard()

                            if (!isAuthenticatedByRSA) {
                                // Nếu Ký RSA thất bại (dù PIN đúng), có thể Master Key bị lỗi hoặc lỗi thẻ
                                connectionError = "Thẻ không hợp lệ (Lỗi xác thực RSA sau khi nhập PIN)!"
                                return@withContext
                            }
                            // 2. Nếu PIN thẻ OK -> Kiểm tra Server (trạng thái PIN)
                            val cardUuid = repo.getCardIDHex()
                            val empInfo = repo.getEmployeeFromServer(cardUuid)

                            showPinDialog = false // Đóng PinDialog

                            if (empInfo?.isDefaultPin == true) { // 3. Nếu đang dùng PIN mặc định
                                // BUỘC ĐỔI PIN
                                showForcePinChangeDialog = true
                                pendingOldPin = pin // ✅ LƯU PIN MẶC ĐỊNH VỪA NHẬP VÀO STATE
                                connectionError = "⚠️ Vui lòng đổi mã PIN để kích hoạt thẻ!"
                            } else {
                                // PIN đã được User đổi -> Login thành công
                                isAuthenticated = true
                                // Cập nhật role từ Server
                                userRole = empInfo?.role ?: "USER"
                                currentScreen = MainScreen.EMPLOYEE_INFO
                            }
                        } else {
                            // PIN sai (Giữ nguyên logic cũ)
                        }
                    }
                }
            }
        )
    }

    // Dialog xác nhận giao dịch (Dùng chung)
    if (showActionPinDialog) {
        PinDialog(
            title = "Xác nhận PIN",
            cardState = cardState,
            onDismiss = { showActionPinDialog = false },
            onPinOk = { pin ->
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

    // 🔥 KHỐI DIALOG BUỘC ĐỔI PIN (Dùng lại CreatePinDialog)
    if (showForcePinChangeDialog) {
        CreatePinDialog(
            onDismiss = {
                connectionError = "Thẻ chưa được kích hoạt hoàn toàn. Vui lòng đổi PIN!"
                exitProcess(0)
            },
            // 🔥 QUAN TRỌNG: Truyền state lỗi vào đây
            externalError = connectionError,
            onConfirm = { newPin ->
                val oldPin = pendingOldPin
                if (oldPin == null) {
                    connectionError = "❌ Lỗi: Không tìm thấy PIN cũ."
                    return@CreatePinDialog
                }

                scope.launch(Dispatchers.IO) {
                    try {
                        // Reset lỗi cũ khi bắt đầu nhấn nút
                        withContext(Dispatchers.Main) { connectionError = null }

                        val cardUuid = repo.getCardIDHex()

                        // 1. Ghi PIN mới vào Thẻ (Có thể ném PinIdenticalException)
                        val cardSuccess = repo.changePin(oldPin, newPin)

                        // 2. Báo Server set isDefaultPin = false
                        if (cardSuccess) {
                            val serverOk = repo.reportPinChanged(cardUuid)

                            withContext(Dispatchers.Main) {
                                if (serverOk) {
                                    showForcePinChangeDialog = false
                                    isAuthenticated = true
                                    val empInfo = repo.getEmployeeFromServer(cardUuid)
                                    userRole = empInfo?.role ?: "USER"
                                    currentScreen = MainScreen.EMPLOYEE_INFO
                                    connectionError = "✅ Đổi PIN thành công!"
                                    pendingOldPin = null
                                } else {
                                    connectionError = "❌ Lỗi: Thẻ đã đổi nhưng Server không cập nhật được."
                                }
                            }
                        }
                    } catch (e: org.example.project.data.PinIdenticalException) {
                        // ✅ Bắt lỗi trùng PIN: Cập nhật biến này sẽ làm Dialog tắt xoay vòng nhờ LaunchedEffect
                        withContext(Dispatchers.Main) {
                            connectionError = "⚠️ PIN mới không được trùng PIN mặc định (123456)!"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            connectionError = "❌ Lỗi hệ thống: ${e.message}"
                        }
                    }
                }
            }
        )
    }


    // --- GIAO DIỆN CHÍNH (Giữ nguyên) ---
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // 1. MENU BÊN TRÁI (Hiển thị khi đã Login thành công)
        if (isAuthenticated) {
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
                // LOGIC LỌC MENU:
                // - Ẩn màn hình SCAN
                // - Nếu là User -> Ẩn màn hình ADMIN
                MainScreen.values().filter { screen ->
                    when {
                        screen == MainScreen.SCAN -> false
                        userRole != "ADMIN" && screen == MainScreen.ADMIN -> false
                        else -> true
                    }
                }.forEach { screen ->
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

                NavigationRailItem(
                    selected = false,
                    onClick = {
                        // Logout Logic
                        isAuthenticated = false
                        isConnected = false
                        userRole = if (isAdminLauncher) "ADMIN" else "USER"
                        repo.disconnect()

                        // Nếu là Admin Launcher logout -> Hiện lại dialog login
                        if (isAdminLauncher) {
                            showAdminLogin = true
                        } else {
                            currentScreen = MainScreen.SCAN
                        }
                    },
                    icon = { Icon(Icons.Default.ExitToApp, "Thoát") },
                    label = { Text("Thoát") }
                )
            }
        }

        // 2. NỘI DUNG CHÍNH (Giữ nguyên)
        Box(
            modifier = Modifier.weight(1f).fillMaxSize().padding(if (isAuthenticated) 24.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isAuthenticated) {
                if (isAdminLauncher) {
                    // ✅ Admin Launcher: Chỉ hiện background chờ Dialog Login bật lên
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Vui lòng đăng nhập Quản trị viên...", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    // ✅ User Launcher: Hiện màn hình Scan thẻ (ẩn nút Admin Login)
                    UnifiedLoginScreen(
                        connectionError = connectionError,
                        onUserConnect = {
                            // Logic User Connect
                            connectionError = null
                            val connected = repo.connect()
                            isConnected = connected

                            if (connected) {
//                                if (!repo.authenticateCard()) {
//                                    connectionError = "Thẻ không hợp lệ (Sai Master Key)!"
//                                    return@UnifiedLoginScreen
//                                }
                                scope.launch(Dispatchers.IO) {
                                    val isInitialized = repo.checkCardInitialized()
                                    withContext(Dispatchers.Main) {
                                        if (!isInitialized) {
                                            // Chặn User tự tạo thẻ mới
                                            connectionError = "Thẻ chưa được định dạng! Vui lòng liên hệ Admin để cấp thẻ."
                                            repo.disconnect()
                                        } else {
                                            // Thẻ OK -> Set role USER -> Hiện nhập PIN
                                            userRole = "USER"
                                            refreshCardState()
                                            showPinDialog = true
                                        }
                                    }
                                }
                            } else {
                                connectionError = "Không tìm thấy thẻ!"
                            }
                        },
                        onAdminLoginClick = { /* Không dùng trong mode User Only */ },
                        showAdminButton = false // Ẩn nút Admin ở màn hình User
                    )
                }
            } else {
                // --- KHI ĐÃ ĐĂNG NHẬP THÀNH CÔNG (Giữ nguyên) ---
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() with fadeOut() }
                ) { screen ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        when (screen) {
                            // Màn hình dành riêng cho Admin (Quản lý nhân sự)
                            MainScreen.ADMIN -> AdminScreen()

                            // Màn hình Thông tin cá nhân
                            MainScreen.EMPLOYEE_INFO -> {
                                val vm = remember { EmployeeViewModel() }
                                LaunchedEffect(userRole) {
                                    // Nếu là Admin -> Load info từ Server
                                    if (userRole == "ADMIN") {
                                        vm.loadFromServer()
                                    }
                                    // Nếu là User -> VM tự load từ thẻ (mặc định)
                                }
                                EmployeeScreen(
                                    vm = vm,
                                    onChangePin = { act -> pendingAction = act; showActionPinDialog = true },
                                    isAuthenticated = isAuthenticated,
                                    forceEditProfile = forceEditProfile,
                                    onForceEditConsumed = { forceEditProfile = false }
                                )
                            }

                            // ✅ SỬA 1: Truyền userRole vào AccessControlScreen
                            MainScreen.ACCESS_CONTROL -> AccessControlScreen(
                                userRole = userRole,
                                onRestrictedArea = { act -> pendingAction = act; showActionPinDialog = true }
                            )

                            // ✅ SỬA 2: Truyền userRole vào CanteenScreen
                            MainScreen.CANTEEN -> CanteenScreen(
                                userRole = userRole,
                                onRequirePin = { act -> pendingAction = act; showActionPinDialog = true },
                                onBalanceChanged = { refreshCardState() }
                            )

                            // ✅ SỬA 3: Truyền userRole vào HistoryScreen
                            MainScreen.HISTORY -> HistoryScreen(
                                userRole = userRole
                            )

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG ĐĂNG NHẬP ADMIN (SERVER) (Giữ nguyên) ---
    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = {
                showAdminLogin = false
                // Nếu là Admin Launcher mà tắt dialog login -> Thoát App luôn
                if (isAdminLauncher) exitProcess(0)
            },
            onLoginSuccess = {
                userRole = "ADMIN"
                isAuthenticated = true
                isConnected = true
                currentScreen = MainScreen.ADMIN // Vào thẳng Dashboard Admin
                showAdminLogin = false
            }
        )
    }
}

// --- CÁC COMPOSABLE PHỤ TRỢ (Giữ nguyên) ---

@Composable
fun UnifiedLoginScreen(
    connectionError: String?,
    onUserConnect: () -> Unit,
    onAdminLoginClick: () -> Unit,
    showAdminButton: Boolean = true
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(Icons.Default.VerifiedUser, null, Modifier.size(100.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Hệ Thống Thẻ Thông Minh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onUserConnect,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Nfc, null)
                Spacer(Modifier.width(8.dp))
                Text("Quét Thẻ Nhân Viên")
            }

            if (showAdminButton) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAdminLoginClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng nhập Quản trị viên")
                }
            }

            if (connectionError != null) {
                Spacer(Modifier.height(16.dp))
                Text(connectionError, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onLoginSuccess: () -> Unit) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đăng nhập Admin (Server)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Mã PIN Quản trị") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    isLoading = true
                    // Gọi Server xác thực (ID cố định ADMIN01)
                    val ok = repo.adminLogin("ADMIN01", pin)
                    isLoading = false
                    if (ok) onLoginSuccess() else error = "Sai mã PIN hoặc lỗi Server!"
                }
            }, enabled = !isLoading) {
                Text(if (isLoading) "Đang kiểm tra..." else "Đăng nhập")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}