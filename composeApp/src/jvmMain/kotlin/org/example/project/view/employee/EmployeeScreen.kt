package org.example.project.view.employee

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.CardRepository
import org.example.project.data.CardRepositoryProvider
import org.example.project.model.Employee
import org.example.project.utils.ImageUtils
import java.awt.FileDialog
import java.awt.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.PinIdenticalException

// --- VIEW MODEL ---
class EmployeeViewModel(private val repo: CardRepository = CardRepositoryProvider.current) {
    var employee by mutableStateOf(repo.getEmployee())
        private set
    var avatarBitmap: ImageBitmap? by mutableStateOf<ImageBitmap?>(null)
        private set

    suspend fun loadFromServer() {
        try {
            val adminData = repo.getEmployeeFromServer("ADMIN")
            if (adminData != null) {
                employee = adminData
            } else {
                employee = Employee(
                    id = "ADMIN01",
                    name = "Administrator",
                    dob = "01/01/1990",
                    department = "System Admin",
                    position = "Super User",
                    role = "ADMIN",
                    photoPath = null,
                    isDefaultPin = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAvatarFromCard() {
        try {
            val bytes = repo.getAvatar()
            if (bytes.isNotEmpty()) avatarBitmap = ImageUtils.bytesToBitmap(bytes)
        } catch (e: Exception) { }
    }

    fun updateEmployee(id: String, name: String, dob: String, dept: String, position: String) {
        employee = employee.copy(id = id, name = name, dob = dob, department = dept, position = position)
        repo.updateEmployee(employee)
    }

    suspend fun generateNextId(department: String): String {
        return repo.getNextId(department)
    }

    fun uploadAvatar(filePath: String) {
        val processedBytes = ImageUtils.processImageForCard(filePath)
        if (processedBytes != null) {
            if (repo.uploadAvatar(processedBytes)) {
                avatarBitmap = ImageUtils.bytesToBitmap(processedBytes)
            }
        }
    }

    fun updateAdminProfile(id: String, name: String, dob: String, dept: String, position: String) {
        employee = employee.copy(id = id, name = name, dob = dob, department = dept, position = position)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.updateAdminProfile(id, name, dob, dept, position)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

@Composable
fun EmployeeScreen(
    vm: EmployeeViewModel = remember { EmployeeViewModel() },
    onChangePin: ((() -> Unit) -> Unit)? = null,
    isAuthenticated: Boolean = false,
    forceEditProfile: Boolean = false,
    onForceEditConsumed: () -> Unit = {}
) {
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) vm.loadAvatarFromCard() }

    val scope = rememberCoroutineScope()
    val emp = vm.employee
    val isAdmin = emp.role.equals("ADMIN", ignoreCase = true)

    // State điều khiển giao diện
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeProfileDialog by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) } // Mới: dùng để ẩn/hiện thông tin
    var showChangePinSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(forceEditProfile) {
        if (forceEditProfile) {
            showChangeProfileDialog = true
            onForceEditConsumed()
        }
    }

    if (showChangePinDialog && onChangePin != null) {
        ChangePinDialogWithVerify(
            onDismiss = { showChangePinDialog = false },
            onSuccess = {
                showChangePinDialog = false
                showChangePinSuccess = true
            },
            isAdmin = isAdmin,
            adminId = if (isAdmin) emp.id else null
        )
    }

    if (showChangeProfileDialog) {
        ChangeProfileDialog(
            vm = vm,
            isAuthenticated = isAuthenticated,
            onClose = { showChangeProfileDialog = false }
        )
    }

    if (showChangePinSuccess) {
        LaunchedEffect(Unit) {
            delay(3000)
            showChangePinSuccess = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hồ sơ nhân viên",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Thẻ ID có khả năng co giãn thông tin
            VisualEmployeeIdCard(
                avatarBitmap = vm.avatarBitmap,
                name = emp.name,
                id = emp.id,
                dob = emp.dob,
                dept = emp.department,
                pos = emp.position,
                isAdmin = isAdmin,
                isExpanded = showDetails
            )

            Spacer(Modifier.height(8.dp))

            // Nút Xem chi tiết hồ sơ (Màu xanh lá theo mẫu)
            Button(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(if (showDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text(if (showDetails) "Ẩn chi tiết" else "Xem hồ sơ")
            }

            // Nút Chỉnh sửa (Việt hóa) - Chỉ hiển thị khi đã xem chi tiết
            if (showDetails) {
                OutlinedButton(
                    onClick = { showChangeProfileDialog = true },
                    modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Chỉnh sửa hồ sơ")
                }
            }

            // Nút Đổi PIN (Việt hóa)
            OutlinedButton(
                onClick = { showChangePinDialog = true },
                modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Đổi mã PIN")
            }
        }

        if (showChangePinSuccess) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { showChangePinSuccess = false }) { Text("OK") } }
            ) {
                Text("✅ Đổi mã PIN thành công!")
            }
        }
    }
}

@Composable
fun VisualEmployeeIdCard(
    avatarBitmap: ImageBitmap?,
    name: String,
    id: String,
    dob: String,
    dept: String,
    pos: String,
    isAdmin: Boolean = false,
    isExpanded: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            )
            .animateContentSize(), // Hiệu ứng mượt khi mở rộng/thu nhỏ
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large
    ){
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isAdmin && avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = name.split(" ").filter { it.isNotBlank() }.takeLast(2).joinToString("") { it.first().uppercase() }.ifBlank { "NV" },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StaticInfoLine(label = "Họ và Tên", value = name)
                    StaticInfoLine(label = "Ngày sinh", value = dob)

                    if (isExpanded) {
                        StaticInfoLine(label = "Mã nhân viên", value = id)
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StaticInfoLine(label = "Phòng ban", value = dept)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StaticInfoLine(label = "Chức vụ", value = pos)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticInfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- GIỮ NGUYÊN CÁC DIALOG NHƯ CŨ NHƯNG VIỆT HÓA NỘI DUNG ---

@Composable
fun ChangePinDialogWithVerify(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    isAdmin: Boolean = false,
    adminId: String? = null
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = { Icon(Icons.Default.LockReset, null, modifier = Modifier.size(32.dp)) },
        title = { Text("Đổi Mã PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    PinInputField(value = oldPin, onValueChange = { oldPin = it; errorMessage = null }, label = "Mã PIN hiện tại")
                    PinInputField(value = newPin, onValueChange = { newPin = it; errorMessage = null }, label = "Mã PIN mới")
                    PinInputField(value = confirmPin, onValueChange = { confirmPin = it; errorMessage = null }, label = "Xác nhận PIN mới")
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    if (newPin.length < 4) {
                        errorMessage = "PIN phải có ít nhất 4 ký tự."
                    } else if (newPin != confirmPin) {
                        errorMessage = "Xác nhận PIN không khớp."
                    } else {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val isOldPinOk = if (isAdmin) repo.verifyAdminPin(oldPin) else repo.verifyPin(oldPin)
                                if (isOldPinOk) {
                                    val changeOk = if (isAdmin) adminId?.let { repo.changeAdminPin(it, newPin) } ?: false else repo.changePin(oldPin, newPin)
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (changeOk) onSuccess() else errorMessage = "Lỗi khi cập nhật PIN."
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = "Mã PIN hiện tại không chính xác."
                                    }
                                }
                            } catch (e: PinIdenticalException) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = "Mã PIN mới không được trùng mã cũ."
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = "Lỗi hệ thống: ${e.message}"
                                }
                            }
                        }
                    }
                }
            ) { Text("Cập nhật") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Hủy bỏ") }
        }
    )
}

@Composable
fun PinInputField(value: String, onValueChange: (String) -> Unit, label: String) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun EditableAvatar(currentBitmap: ImageBitmap?, fallbackName: String, onPickImage: () -> Unit, isAdmin: Boolean = false) {
    Box(modifier = Modifier.size(120.dp)) {
        if (!isAdmin && currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackName.split(" ").filter { it.isNotBlank() }.takeLast(2).joinToString("") { it.first().uppercase() }.ifBlank { "NV" },
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (!isAdmin) {
            SmallFloatingActionButton(
                onClick = onPickImage,
                modifier = Modifier.align(Alignment.BottomEnd),
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp)) }
        }
    }
}

fun pickFile(): String? {
    val dialog = FileDialog(null as Frame?, "Chọn ảnh hồ sơ", FileDialog.LOAD)
    dialog.file = "*.jpg;*.jpeg;*.png"
    dialog.isVisible = true
    return if (dialog.directory != null && dialog.file != null) dialog.directory + dialog.file else null
}