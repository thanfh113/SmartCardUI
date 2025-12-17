package org.example.project.view.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape // Cần cho EditableAvatar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap // Import cần thiết
import androidx.compose.ui.layout.ContentScale // Import cần thiết
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Cần cho Text size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.Employee
import org.example.project.model.UserResponse
import org.example.project.view.employee.pickFile // Giả định hàm này nằm trong employee package
import org.example.project.utils.ImageUtils // Giả định ImageUtils tồn tại
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

// THÊM VÀO AdminScreen.kt

@Composable
fun CardManagementDialog(
    user: UserResponse,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var adminPin by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showAction by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Quản Lý Thẻ: ${user.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Mã NV: ${user.employeeId}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Hiển thị trạng thái thẻ
                Card(
                    colors = CardDefaults.cardColors(
                        if (user.isActive) Color(0xFFE0F7EC) else Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (user.isActive) Icons.Default.LockOpen else Icons.Default.Lock,
                            null,
                            tint = if (user.isActive) Color(0xFF1BAA61) else Color.Red
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (user.isActive) "Thẻ Hoạt Động" else "Thẻ Bị Khóa",
                            fontWeight = FontWeight.Bold,
                            color = if (user.isActive) Color(0xFF1BAA61) else Color.Red
                        )
                    }
                }

                Divider()

                // Các nút hành động
                if (!user.isActive) {
                    Button(
                        onClick = { showAction = "unlock" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(Color(0xFF1BAA61))
                    ) {
                        Icon(Icons.Default.LockOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mở Khóa Thẻ")
                    }
                }

                Button(
                    onClick = { showAction = "reset_pin" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0xFFFF9800))
                ) {
                    Icon(Icons.Default.VpnKey, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset PIN")
                }

                // Form nhập PIN Admin khi chọn hành động
                if (showAction != null) {
                    Divider()

                    Text(
                        "Xác thực Admin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { if (it.all { c -> c.isDigit() }) adminPin = it },
                        label = { Text("PIN Admin") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    if (status.isNotEmpty()) {
                        Text(
                            status,
                            color = if (status.contains("thành công")) Color(0xFF1BAA61) else Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    isProcessing = true
                                    status = "Đang xử lý..."

                                    try {
                                        // 1) Verify admin PIN via server first
                                        val verified = try { repo.verifyAdminPin(adminPin) } catch (e: Exception) { false }

                                        if (!verified) {
                                            withContext(Dispatchers.Main) {
                                                status = "❌ Sai mã PIN Admin (server)."
                                            }
                                            return@launch
                                        }

                                        // 2) If verified, proceed to perform the card operation
                                        when (showAction) {
                                            "unlock" -> {
                                                val success = repo.adminUnlockUserCard(adminPin, user.cardUuid)
                                                withContext(Dispatchers.Main) {
                                                    if (success) onSuccess("Đã mở khóa thẻ thành công!")
                                                    else status = "❌ Không thể mở khóa. Thao tác thẻ thất bại."
                                                }
                                            }
                                            "reset_pin" -> {
                                                val success = repo.adminResetUserPin(adminPin, user.cardUuid, "123456")
                                                withContext(Dispatchers.Main) {
                                                    if (success) onSuccess("Đã reset PIN thành công về 123456!")
                                                    else status = "❌ Không thể reset PIN. Thao tác thẻ thất bại."
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            status = "❌ Lỗi: ${e.message}"
                                        }
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = !isProcessing && adminPin.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White
                                )
                            } else {
                                Text("Xác Nhận")
                            }
                        }

                        OutlinedButton(
                            onClick = { showAction = null; status = "" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hủy")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showAction == null) {
                TextButton(onClick = onDismiss) {
                    Text("Đóng")
                }
            }
        },
        dismissButton = {}
    )
}

// CẬP NHẬT UserItem để thêm nút quản lý thẻ
@Composable
fun UserItem(
    user: UserResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: (Boolean) -> Unit, // Đổi sang nhận tham số là trạng thái mới (isActive)
    onManageCard: () -> Unit // THÊM
) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            if (user.isActive) MaterialTheme.colorScheme.surface else Color(0xFFEEEEEE)
        )
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = if (user.isActive) Color(0xFF1BAA61) else Color.Gray,
                modifier = Modifier.size(40.dp).padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.Bold)
                Text(
                    "${user.employeeId} • ${user.role}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (user.role != "ADMIN") {
                // THÊM: Nút quản lý thẻ
                IconButton(onClick = onManageCard) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        "Quản lý thẻ",
                        tint = Color(0xFFFF9800)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Sửa", tint = MaterialTheme.colorScheme.primary)
                }
                // CHỈNH SỬA: Bấm vào nút sẽ kích hoạt dialog để nhập PIN Admin
                IconButton(onClick = { onToggleStatus(!user.isActive) }) {
                    Icon(
                        if (user.isActive) Icons.Default.LockOpen else Icons.Default.Lock,
                        null,
                        tint = if (user.isActive) Color.Gray else Color.Red
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var adminProfile by remember { mutableStateOf<Employee?>(null) }
    var adminBalance by remember { mutableStateOf(0.0) }
    var users by remember { mutableStateOf<List<UserResponse>>(emptyList()) }

    var showIssueDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<UserResponse?>(null) }
    var userToDelete by remember { mutableStateOf<UserResponse?>(null) }
    var userToToggleStatus by remember { mutableStateOf<UserResponse?>(null) } // THÊM
    var newStatusForToggle by remember { mutableStateOf(false) } // THÊM
    var message by remember { mutableStateOf<String?>(null) }

    var selectedUserForManage by remember { mutableStateOf<UserResponse?>(null) } // THÊM

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            val list = repo.getAllUsers()
            val me = repo.getEmployeeFromServer("ADMIN01")

            // Cập nhật số dư Admin
            val balance = repo.getAdminBalance("ADMIN01")

            withContext(Dispatchers.Main) {
                users = list
                adminBalance = balance
                if (me != null) {
                    adminProfile = me
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(adminProfile?.name?.take(1) ?: "A", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(adminProfile?.name ?: "Administrator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(14.dp), tint = Color(0xFF1BAA61))
                                Spacer(Modifier.width(4.dp))
                                Text("${formatMoney(adminBalance)} • ID: ${adminProfile?.id ?: "ADMIN01"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                actions = {
                    IconButton(onClick = { loadData() }) { Icon(Icons.Default.Refresh, "Tải lại") }
                    IconButton(onClick = { repo.disconnect(); exitProcess(0) }) { Icon(Icons.Default.Logout, "Đăng xuất", tint = MaterialTheme.colorScheme.error) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showIssueDialog = true },
                icon = { Icon(Icons.Default.AddCard, null) },
                text = { Text("Cấp Thẻ Mới") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (message != null) {
                Card(colors = CardDefaults.cardColors(if(message!!.contains("thành công")) Color(0xFFE0F7EC) else Color(0xFFFFEBEE)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if(message!!.contains("thành công")) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if(message!!.contains("thành công")) Color(0xFF1BAA61) else Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(message!!, color = if(message!!.contains("thành công")) Color(0xFF1BAA61) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Quản Lý Nhân Sự (${users.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

            // ... trong LazyColumn ...
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(users) { user ->
                    UserItem(
                        user = user,
                        onEdit = { selectedUserForEdit = user },
                        onDelete = { userToDelete = user },
                        onToggleStatus = { newStatus -> // CHỈNH SỬA: Mở dialog xác nhận
                            userToToggleStatus = user
                            newStatusForToggle = newStatus
                        },
                        onManageCard = { selectedUserForManage = user } // THÊM
                    )
                }
            }
        }
    }

    if (showIssueDialog) {
        IssueCardDialog(
            onDismiss = { showIssueDialog = false },
            onSuccess = { name -> message = "Cấp thẻ thành công cho: $name"; loadData(); showIssueDialog = false }
        )
    }

    if (selectedUserForEdit != null) {
        EditCardDialog(
            user = selectedUserForEdit!!,
            onDismiss = { selectedUserForEdit = null },
            onSuccess = { message = "Cập nhật thành công!"; loadData(); selectedUserForEdit = null }
        )
    }

    if (userToDelete != null) {
        ConfirmDeleteDialog(
            user = userToDelete!!,
            onDismiss = { userToDelete = null },
            onConfirm = { pin ->
                scope.launch(Dispatchers.IO) {
                    // VERIFY ADMIN PIN VIA SERVER FIRST
                    withContext(Dispatchers.Main) { /* show processing */ }
                    val verified = try { repo.verifyAdminPin(pin) } catch (e: Exception) { false }

                    if (!verified) {
                        withContext(Dispatchers.Main) {
                            message = "Sai mã PIN Admin (server)."
                            userToDelete = null
                        }
                        return@launch
                    }

                    // If verified, proceed to delete (server endpoint will still accept the PIN payload)
                    val success = try { repo.deleteUser(userToDelete!!.cardUuid, pin) } catch (e: Exception) { false }
                    withContext(Dispatchers.Main) {
                        message = if (success) "Đã xóa nhân viên thành công!" else "Xóa thất bại."
                        loadData()
                        userToDelete = null
                    }
                }
            }
        )
    }

    // THÊM: Dialog khóa/mở khóa
    if (userToToggleStatus != null) {
        ToggleStatusDialog(
            user = userToToggleStatus!!,
            newStatus = newStatusForToggle,
            onDismiss = { userToToggleStatus = null },
            onConfirm = { pin ->
                scope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) { /* show processing */ }
                    val verified = try { repo.verifyAdminPin(pin) } catch (e: Exception) { false }

                    if (!verified) {
                        withContext(Dispatchers.Main) {
                            message = "Sai mã PIN Admin (server)."
                            userToToggleStatus = null
                        }
                        return@launch
                    }

                    // Nếu verified, thực hiện thay đổi trạng thái
                    // Chức năng này chỉ thay đổi trạng thái trên Server, không cần tương tác thẻ vật lý
                    val success = try { repo.changeUserStatus(userToToggleStatus!!.cardUuid, newStatusForToggle) } catch (e: Exception) { false }
                    withContext(Dispatchers.Main) {
                        message = if (success) {
                            if (newStatusForToggle) "Đã mở khóa thẻ thành công!" else "Đã khóa thẻ thành công!"
                        } else "Thao tác thay đổi trạng thái thẻ thất bại."
                        loadData()
                        userToToggleStatus = null
                    }
                }
            }
        )
    }

    // THÊM: Dialog quản lý thẻ
    if (selectedUserForManage != null) {
        CardManagementDialog(
            user = selectedUserForManage!!,
            onDismiss = { selectedUserForManage = null },
            onSuccess = { msg ->
                message = msg
                loadData()
                selectedUserForManage = null
            }
        )
    }
}

// --- HELPER COMPOSABLES ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// ĐỔI TÊN TỪ DepartmentSelector SANG DataSelector VÀ THÊM THAM SỐ label
fun DataSelector(
    label: String, // Nhãn hiển thị
    currentValue: String,
    onValueSelected: (String) -> Unit,
    items: List<String> // Danh sách tên động
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) }, // Dùng nhãn động
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onValueSelected(item); expanded = false })
            }
        }
    }
}

// UserItem CŨ ĐÃ ĐƯỢC THAY THẾ Ở TRÊN

// --- AVATAR COMPONENT (Lấy từ EmployeeScreen.kt) ---

@Composable
fun EditableAvatar(
    currentBitmap: ImageBitmap?,
    fallbackName: String,
    onPickImage: () -> Unit
) {
    val size = 140.dp
    Box(modifier = Modifier.size(size)) {
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            val initials = fallbackName.split(" ").filter { it.isNotBlank() }
                .takeLast(2).joinToString("") { it.first().uppercase() }.ifBlank { "NV" }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp
                )
            }
        }

        SmallFloatingActionButton(
            onClick = onPickImage,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp)
        ) {
            Icon(Icons.Default.Edit, null, Modifier.size(20.dp))
        }
    }
}


// --- DIALOGS SỬA ĐỔI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueCardDialog(onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("Phòng Kỹ Thuật") }
    var pos by remember { mutableStateOf("Nhân viên") }
    var dob by remember { mutableStateOf("") }

    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // 🔥 STATE AVATAR
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // TẢI DỮ LIỆU ĐỘNG TỪ SERVER
    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val departmentNames = departmentsMap.values.toList()
    val positionNames = positionsMap.values.toList()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
            if (departmentsMap.isNotEmpty()) dept = departmentsMap.values.first()
            if (positionsMap.isNotEmpty()) pos = positionsMap.values.first()
        }
    }

    // State cho DatePicker
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    LaunchedEffect(dept) {
        scope.launch { id = repo.getNextId(dept) }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Cấp Thẻ Mới") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 🔥 AVATAR COMPONENT (1)
                EditableAvatar(
                    currentBitmap = avatarBitmap,
                    fallbackName = name,
                    onPickImage = {
                        pickFile()?.let { filePath ->
                            val processedBytes = ImageUtils.processImageForCard(filePath)
                            if (processedBytes != null) {
                                avatarBytes = processedBytes
                                avatarBitmap = ImageUtils.bytesToBitmap(processedBytes)
                            }
                        }
                    }
                )

                Text("Vui lòng nhập đầy đủ thông tin.", style = MaterialTheme.typography.bodySmall)

                // DROP DOWNS VÀ TEXT FIELDS (ĐÃ SỬA NHÃN)
                DataSelector(label = "Phòng ban", currentValue = dept, onValueSelected = { dept = it }, items = departmentNames)
                DataSelector(label = "Chức vụ", currentValue = pos, onValueSelected = { pos = it }, items = positionNames)

                OutlinedTextField(id, { id = it }, label = { Text("Mã NV") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Họ tên") }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Ngày sinh") },
                    placeholder = { Text("dd/MM/yyyy") },
                    modifier = Modifier.fillMaxWidth(),
                    // 🔥 BẬT LOGIC DatePicker:
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, null) } }
                )

                if (status.isNotEmpty()) Text(status, color = Color.Red)
            }
        },
        confirmButton = {
            Button(enabled = !isProcessing && name.isNotBlank(), onClick = {
                scope.launch(Dispatchers.IO) {
                    isProcessing = true
                    repo.disconnect() // Đảm bảo ngắt kết nối cũ
                    val finalId = repo.getNextId(dept)

                    if (repo.connect()) { // Bắt đầu kết nối mới

                        // 1. Setup PIN và issue Card (Phần này chỉ set data Text)
                        val newEmp = Employee(finalId, name, dob.ifBlank { "01/01/2000" }, dept, pos, "USER", null, true)
                        if (!repo.checkCardInitialized()) repo.setupFirstPin("123456")
                        repo.verifyPin("123456") // verify để mở khóa

                        // 2. Ghi Avatar (Nếu có ảnh)
                        var avatarSuccess = true
                        if (avatarBytes != null) {
                            avatarSuccess = repo.uploadAvatar(avatarBytes!!)
                        }

                        // 3. Issue Card (ghi thông tin text và đăng ký Server)
                        val issueSuccess = repo.issueCardForUser(newEmp)

                        repo.disconnect() // Ngắt kết nối sau khi hoàn thành

                        withContext(Dispatchers.Main) {
                            if (issueSuccess && avatarSuccess) {
                                onSuccess(name)
                            } else {
                                status = "Lỗi Server/Ghi thẻ! (Avatar: $avatarSuccess, Issue: $issueSuccess)"
                            }
                            isProcessing = false
                        }
                    } else withContext(Dispatchers.Main) { status = "Lỗi thẻ! (Không tìm thấy đầu đọc)" ; isProcessing = false }
                }
            }) { Text("Cấp Thẻ") }
        },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )

    // ✅ DIALOG LỊCH (DATE PICKER)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dob = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardDialog(user: UserResponse, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf(user.name) }
    var newDept by remember { mutableStateOf(user.department ?: "Phòng Kỹ Thuật") }
    var newPos by remember { mutableStateOf(user.position ?: "Nhân viên") }
    var newDob by remember { mutableStateOf(user.dob ?: "01/01/2000") }

    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // 🔥 STATE AVATAR
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Tải Avatar hiện tại và Dữ liệu động
    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val departmentNames = departmentsMap.values.toList()
    val positionNames = positionsMap.values.toList()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            // Tải Avatar hiện tại
            repo.disconnect()
            if (repo.connect()) {
                val bytes = repo.getAvatar()
                if (bytes.isNotEmpty()) avatarBitmap = ImageUtils.bytesToBitmap(bytes)
                repo.disconnect()
            }
            // Tải Dropdowns
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
        }
    }

    // State cho DatePicker
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try { java.time.LocalDate.parse(user.dob, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { Instant.now().toEpochMilli() }
    )
    var showDatePicker by remember { mutableStateOf(false) }


    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Sửa Thông Tin: ${user.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 🔥 AVATAR COMPONENT (2)
                EditableAvatar(
                    currentBitmap = avatarBitmap,
                    fallbackName = newName,
                    onPickImage = {
                        pickFile()?.let { filePath ->
                            val processedBytes = ImageUtils.processImageForCard(filePath)
                            if (processedBytes != null) {
                                avatarBytes = processedBytes // Lưu bytes mới
                                avatarBitmap = ImageUtils.bytesToBitmap(processedBytes)
                            }
                        }
                    }
                )

                Text("Đặt thẻ của '${user.name}' lên đầu đọc để xác nhận.", color = Color.Red)

                OutlinedTextField(user.employeeId, {}, label = { Text("Mã NV (Khóa)") }, enabled = false, modifier = Modifier.fillMaxWidth())

                // DROP DOWNS VÀ TEXT FIELDS (ĐÃ SỬA NHÃN)
                DataSelector(label = "Phòng ban", currentValue = newDept, onValueSelected = { newDept = it }, items = departmentNames)
                DataSelector(label = "Chức vụ", currentValue = newPos, onValueSelected = { newPos = it }, items = positionNames)

                OutlinedTextField(newName, { newName = it }, label = { Text("Họ tên") }, modifier = Modifier.fillMaxWidth())

                // Ô CHỌN NGÀY SINH
                OutlinedTextField(
                    value = newDob,
                    onValueChange = { newDob = it },
                    readOnly = true,
                    label = { Text("Ngày sinh") },
                    placeholder = { Text("dd/MM/yyyy") },
                    modifier = Modifier.fillMaxWidth(),
                    // 🔥 BẬT LOGIC DatePicker:
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, null) } }
                )
                if (status.isNotEmpty()) Text(status, color = Color.Red)
            }
        },
        confirmButton = {
            Button(enabled = !isProcessing, onClick = {
                scope.launch(Dispatchers.IO) {
                    isProcessing = true
                    repo.disconnect()

                    if (repo.connect()) {
                        val cardData = repo.getEmployee()

                        if (cardData.id == user.employeeId) {
                            // 1. CẬP NHẬT DỮ LIỆU CHÍNH (Thẻ + Server)
                            repo.updateEmployee(cardData.copy(
                                name = newName, department = newDept, position = newPos, dob = newDob
                            ))

                            // 2. UPLOAD AVATAR (Nếu có ảnh mới)
                            var avatarSuccess = true
                            if (avatarBytes != null) {
                                avatarSuccess = repo.uploadAvatar(avatarBytes!!)
                            }

                            repo.disconnect();
                            withContext(Dispatchers.Main) {
                                if(avatarSuccess) onSuccess()
                                else status = "Lỗi ghi Avatar xuống thẻ!"
                            }
                        } else {
                            repo.disconnect(); withContext(Dispatchers.Main) { status = "Sai thẻ! Vui lòng đặt thẻ của ${user.employeeId}." }
                        }
                    } else withContext(Dispatchers.Main) { status = "Không thấy thẻ!" }
                    isProcessing = false
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )

    // ✅ DIALOG LỊCH (DATE PICKER)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        newDob = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
fun ConfirmDeleteDialog(user: UserResponse, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác nhận xóa") },
        text = {
            Column {
                Text("Xóa nhân viên '${user.name}'?", color = Color.Red)
                OutlinedTextField(pin, { if(it.all { c -> c.isDigit() }) pin = it }, label = { Text("PIN Admin") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, colors = ButtonDefaults.buttonColors(Color.Red)) { Text("Xóa") } },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )
}

// THÊM: Dialog nhập PIN Admin để khóa/mở khóa thẻ qua Server
@Composable
fun ToggleStatusDialog(
    user: UserResponse,
    newStatus: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (newStatus) "Mở khóa thẻ" else "Khóa thẻ") },
        text = {
            Column {
                Text(
                    if (newStatus) "Xác nhận mở khóa thẻ cho '${user.name}'?"
                    else "Xác nhận khóa thẻ cho '${user.name}'? Thao tác này sẽ vô hiệu hóa thẻ."
                )
                OutlinedTextField(
                    pin,
                    { if(it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN Admin") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.isNotBlank()) { Text(if (newStatus) "Mở Khóa" else "Khóa") } },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )
}


fun formatMoney(amount: Double): String {
    return try { NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount) } catch (e: Exception) { "$amount" }
}