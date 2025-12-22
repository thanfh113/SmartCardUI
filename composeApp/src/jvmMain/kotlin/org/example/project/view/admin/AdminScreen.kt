package org.example.project.view.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.Employee
import org.example.project.model.UserResponse
import org.example.project.view.employee.pickFile
import org.example.project.utils.ImageUtils
import java.awt.Frame
import java.awt.FileDialog
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

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
                Text("Mã NV: ${user.employeeId}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                Card(colors = CardDefaults.cardColors(if (user.isActive) Color(0xFFE0F7EC) else Color(0xFFFFEBEE))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (user.isActive) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = if (user.isActive) Color(0xFF1BAA61) else Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(if (user.isActive) "Thẻ Hoạt Động" else "Thẻ Bị Khóa", fontWeight = FontWeight.Bold, color = if (user.isActive) Color(0xFF1BAA61) else Color.Red)
                    }
                }
                HorizontalDivider()

                if (!user.isActive) {
                    Button(onClick = { showAction = "unlock" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF1BAA61))) {
                        Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Mở Khóa Thẻ")
                    }
                } else {
                    Button(onClick = { showAction = "lock" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color.Red)) {
                        Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Khóa Thẻ")
                    }
                }

                Button(onClick = { showAction = "reset_pin" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFFFF9800))) {
                    Icon(Icons.Default.VpnKey, null); Spacer(Modifier.width(8.dp)); Text("Reset PIN")
                }

                if (showAction != null) {
                    HorizontalDivider()
                    Text("Xác thực Admin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = adminPin, onValueChange = { if (it.all { c -> c.isDigit() }) adminPin = it },
                        label = { Text("PIN Admin") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (status.isNotEmpty()) Text(status, color = if (status.contains("thành công")) Color(0xFF1BAA61) else Color.Red, style = MaterialTheme.typography.bodySmall)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    isProcessing = true; status = "Đang xử lý..."
                                    try {
                                        val verified = try { repo.verifyAdminPin(adminPin) } catch (e: Exception) { false }
                                        if (!verified) { withContext(Dispatchers.Main) { status = "❌ Sai mã PIN Admin." }; return@launch }
                                        when (showAction) {
                                            "lock" -> {
                                                val success = repo.adminLockUserCard(adminPin, user.cardUuid)
                                                withContext(Dispatchers.Main) { if (success) onSuccess("✅ Đã khóa thẻ thành công!") else status = "❌ Không thể khóa thẻ." }
                                            }
                                            "unlock" -> {
                                                val success = repo.adminUnlockUserCard(adminPin, user.cardUuid)
                                                withContext(Dispatchers.Main) { if (success) onSuccess("✅ Đã mở khóa thẻ thành công!") else status = "❌ Không thể mở khóa." }
                                            }
                                            "reset_pin" -> {
                                                val success = repo.adminResetUserPin(adminPin, user.cardUuid, "123456")
                                                withContext(Dispatchers.Main) { if (success) onSuccess("✅ Đã reset PIN thành công!") else status = "❌ Không thể reset PIN." }
                                            }
                                        }
                                    } catch (e: Exception) { withContext(Dispatchers.Main) { status = "❌ Lỗi: ${e.message}" }
                                    } finally { isProcessing = false }
                                }
                            },
                            enabled = !isProcessing && adminPin.isNotBlank(), modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White) else Text("Xác Nhận")
                        }
                        OutlinedButton(onClick = { showAction = null; status = "" }, modifier = Modifier.weight(1f)) { Text("Hủy") }
                    }
                }
            }
        },
        confirmButton = { if (showAction == null) { TextButton(onClick = onDismiss) { Text("Đóng") } } }
    )
}

@Composable
fun UserItem(user: UserResponse, onEdit: () -> Unit, onDelete: () -> Unit, onManageCard: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = if (user.isActive) MaterialTheme.colorScheme.surface else Color(0xFFF5F5F5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, tint = if (user.isActive) Color(0xFF1BAA61) else Color.Gray, modifier = Modifier.size(40.dp).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(imageVector = if (user.isActive) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (user.isActive) Color(0xFF1BAA61) else Color.Red)
                }
                Text("${user.employeeId} • ${user.role} • ${if (user.isActive) "Hoạt động" else "Bị khóa"}", style = MaterialTheme.typography.bodySmall, color = if (user.isActive) Color.Unspecified else Color.Red)
            }
            if (user.role != "ADMIN") {
                IconButton(onClick = onManageCard) { Icon(Icons.Default.AdminPanelSettings, "Quản lý thẻ", tint = Color(0xFFFF9800)) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Sửa", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
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
    var message by remember { mutableStateOf<String?>(null) }
    var selectedUserForManage by remember { mutableStateOf<UserResponse?>(null) }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            val list = repo.getAllUsers()
            val me = repo.getEmployeeFromServer("ADMIN01")
            val balance = repo.getAdminBalance("ADMIN01")
            withContext(Dispatchers.Main) {
                users = list
                adminBalance = balance
                if (me != null) adminProfile = me
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
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
                actions = {
                    IconButton(onClick = { loadData() }) { Icon(Icons.Default.Refresh, "Tải lại") }
                    IconButton(onClick = { repo.disconnect(); exitProcess(0) }) { Icon(Icons.Default.Logout, "Đăng xuất", tint = MaterialTheme.colorScheme.error) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showIssueDialog = true }, icon = { Icon(Icons.Default.AddCard, null) }, text = { Text("Cấp Thẻ Mới") }, containerColor = MaterialTheme.colorScheme.primary)
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
            Text("Quản Lý Nhân Sự (${users.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(users) { user ->
                    UserItem(user = user, onEdit = { selectedUserForEdit = user }, onDelete = { userToDelete = user }, onManageCard = { selectedUserForManage = user })
                }
            }
        }
    }

    if (showIssueDialog) IssueCardDialog(onDismiss = { showIssueDialog = false }, onSuccess = { name -> message = "Cấp thẻ thành công cho: $name"; loadData(); showIssueDialog = false })
    if (selectedUserForEdit != null) EditCardDialog(user = selectedUserForEdit!!, onDismiss = { selectedUserForEdit = null }, onSuccess = { message = "Cập nhật thành công!"; loadData(); selectedUserForEdit = null })

    // --- CONFIRM DELETE DIALOG ---
    if (userToDelete != null) {
        ConfirmDeleteDialog(
            user = userToDelete!!,
            onDismiss = { userToDelete = null },
            onConfirm = { pin ->
                scope.launch(Dispatchers.IO) {
                    val verified = try { repo.verifyAdminPin(pin) } catch (e: Exception) { false }
                    if (!verified) { withContext(Dispatchers.Main) { message = "Sai mã PIN Admin." }; return@launch }
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

    if (selectedUserForManage != null) CardManagementDialog(user = selectedUserForManage!!, onDismiss = { selectedUserForManage = null }, onSuccess = { msg -> message = msg; loadData(); selectedUserForManage = null })
}

@Composable
fun ConfirmDeleteDialog(user: UserResponse, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác nhận xóa") },
        text = {
            Column {
                Text("Xóa nhân viên '${user.name}'? Thao tác này không thể hoàn tác.", color = Color.Red)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { if(it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN Admin") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, colors = ButtonDefaults.buttonColors(Color.Red), enabled = pin.isNotBlank()) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSelector(label: String, currentValue: String, onValueSelected: (String) -> Unit, items: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = currentValue, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onValueSelected(item); expanded = false }) }
        }
    }
}

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
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
            if (departmentsMap.isNotEmpty()) dept = departmentsMap.values.first()
            if (positionsMap.isNotEmpty()) pos = positionsMap.values.first()
        }
    }

    LaunchedEffect(dept) { scope.launch { id = repo.getNextId(dept) } }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Cấp Thẻ Mới") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditableAvatar(currentBitmap = avatarBitmap, fallbackName = name, onPickImage = { pickFile()?.let { filePath -> ImageUtils.processImageForCard(filePath)?.let { avatarBytes = it; avatarBitmap = ImageUtils.bytesToBitmap(it) } } })
                DataSelector(label = "Phòng ban", currentValue = dept, onValueSelected = { dept = it }, items = departmentsMap.values.toList())
                DataSelector(label = "Chức vụ", currentValue = pos, onValueSelected = { pos = it }, items = positionsMap.values.toList())
                OutlinedTextField(value = id, onValueChange = {}, label = { Text("Mã NV (Tự động)") }, modifier = Modifier.fillMaxWidth(), enabled = false, readOnly = true)
                OutlinedTextField(name, { name = it }, label = { Text("Họ tên") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Ngày sinh") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, null) } })
                if (status.isNotEmpty()) Text(status, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !isProcessing && name.isNotBlank() && dob.isNotBlank(), onClick = {
                try {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val birthDate = LocalDate.parse(dob, formatter)
                    if (Period.between(birthDate, LocalDate.now()).years < 15) { status = "❌ Nhân viên phải từ đủ 15 tuổi!"; return@Button }
                } catch (e: Exception) { status = "❌ Ngày sinh không hợp lệ!"; return@Button }

                scope.launch(Dispatchers.IO) {
                    isProcessing = true; repo.disconnect()
                    if (repo.connect()) {
                        val newEmp = Employee(id, name, dob, dept, pos, "USER", null, true)
                        if (!repo.checkCardInitialized()) repo.setupFirstPin("123456")
                        repo.verifyPin("123456")
                        var avatarSuccess = true
                        if (avatarBytes != null) avatarSuccess = repo.uploadAvatar(avatarBytes!!)
                        val issueSuccess = repo.issueCardForUser(newEmp)
                        repo.disconnect()
                        withContext(Dispatchers.Main) { if (issueSuccess && avatarSuccess) onSuccess(name) else status = "Lỗi Server/Ghi thẻ!"; isProcessing = false }
                    } else withContext(Dispatchers.Main) { status = "Không tìm thấy thẻ!"; isProcessing = false }
                }
            }) { Text("Cấp Thẻ") }
        },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { dob = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardDialog(user: UserResponse, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf(user.name) }
    var newDept by remember { mutableStateOf(user.department ?: "") }
    var newPos by remember { mutableStateOf(user.position ?: "") }
    var newDob by remember { mutableStateOf(user.dob ?: "") }
    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            repo.disconnect()
            if (repo.connect()) { repo.getAvatar().let { if (it.isNotEmpty()) avatarBitmap = ImageUtils.bytesToBitmap(it) }; repo.disconnect() }
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Sửa Thông Tin: ${user.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditableAvatar(currentBitmap = avatarBitmap, fallbackName = newName, onPickImage = { pickFile()?.let { filePath -> ImageUtils.processImageForCard(filePath)?.let { avatarBytes = it; avatarBitmap = ImageUtils.bytesToBitmap(it) } } })
                OutlinedTextField(value = user.employeeId, onValueChange = {}, label = { Text("Mã NV (Khóa)") }, modifier = Modifier.fillMaxWidth(), enabled = false, readOnly = true)
                DataSelector(label = "Phòng ban", currentValue = newDept, onValueSelected = { newDept = it }, items = departmentsMap.values.toList())
                DataSelector(label = "Chức vụ", currentValue = newPos, onValueSelected = { newPos = it }, items = positionsMap.values.toList())
                OutlinedTextField(newName, { newName = it }, label = { Text("Họ tên") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = newDob, onValueChange = { newDob = it }, label = { Text("Ngày sinh") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, null) } })
                if (status.isNotEmpty()) Text(status, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !isProcessing, onClick = {
                try {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    if (Period.between(LocalDate.parse(newDob, formatter), LocalDate.now()).years < 15) { status = "❌ Nhân viên phải từ đủ 15 tuổi!"; return@Button }
                } catch (e: Exception) { status = "❌ Ngày sinh không hợp lệ!"; return@Button }

                scope.launch(Dispatchers.IO) {
                    isProcessing = true; repo.disconnect()
                    if (repo.connect()) {
                        val cardData = repo.getEmployee()
                        if (cardData.id == user.employeeId) {
                            repo.updateEmployee(cardData.copy(name = newName, department = newDept, position = newPos, dob = newDob))
                            if (avatarBytes != null) repo.uploadAvatar(avatarBytes!!)
                            repo.disconnect(); withContext(Dispatchers.Main) { onSuccess() }
                        } else { repo.disconnect(); withContext(Dispatchers.Main) { status = "Sai thẻ!" } }
                    } else withContext(Dispatchers.Main) { status = "Không thấy thẻ!" }
                    isProcessing = false
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton({ onDismiss() }) { Text("Hủy") } }
    )
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { newDob = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }) { DatePicker(state = datePickerState) }
    }
}

@Composable
fun EditableAvatar(currentBitmap: ImageBitmap?, fallbackName: String, onPickImage: () -> Unit) {
    Box(modifier = Modifier.size(140.dp)) {
        if (currentBitmap != null) {
            Image(bitmap = currentBitmap, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape))
        } else {
            val initials = fallbackName.split(" ").filter { it.isNotBlank() }.takeLast(2).joinToString("") { it.first().uppercase() }.ifBlank { "NV" }
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = initials, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 40.sp)
            }
        }
        SmallFloatingActionButton(onClick = onPickImage, containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp)) { Icon(Icons.Default.Edit, null, Modifier.size(20.dp)) }
    }
}

fun formatMoney(amount: Double): String = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)