package org.example.project.view.employee

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- VIEW MODEL ---
class EmployeeViewModel(private val repo: CardRepository = CardRepositoryProvider.current) {
    var employee by mutableStateOf(repo.getEmployee())
        private set
    var avatarBitmap: ImageBitmap? by mutableStateOf<ImageBitmap?>(null)
        private set

    // ✅ SỬA LỖI THIẾU THAM SỐ TRONG loadFromServer
    suspend fun loadFromServer() {
        try {
            // Giả định Admin ID là ADMIN01 (khớp với logic login)
            val adminData = repo.getEmployeeFromServer("ADMIN")
            if (adminData != null) {
                employee = adminData
            } else {
                // 🔥 Fallback nếu không tải được: Cung cấp đủ 8 tham số
                employee = Employee(
                    id = "ADMIN01",
                    name = "Administrator",
                    dob = "01/01/1990",
                    department = "System Admin",
                    position = "Super User",
                    role = "ADMIN",         // ✅ Thêm role
                    photoPath = null,
                    isDefaultPin = false    // ✅ Thêm isDefaultPin
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

    // Cập nhật thông tin (Ghi đè cả thẻ và Server)
    // Giữ nguyên: Dùng Employee.copy() tự động giữ lại các trường không được truyền vào (role, isDefaultPin, photoPath)
    fun updateEmployee(id: String, name: String, dob: String, dept: String, position: String) {
        // Cập nhật object local
        employee = employee.copy(id = id, name = name, dob = dob, department = dept, position = position)
        // Gọi xuống Repo để lưu thẻ + Server
        repo.updateEmployee(employee)
    }

    // Hàm xin ID gợi ý từ Server
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

    // New: update admin profile via server endpoint (non-blocking from UI)
    fun updateAdminProfile(id: String, name: String, dob: String, dept: String, position: String) {
        // Update local object immediately
        employee = employee.copy(id = id, name = name, dob = dob, department = dept, position = position)
        // Call server-side admin update in background
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // repo.updateAdminProfile is suspend
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

    val emp = vm.employee // luôn cập nhật từ viewmodel
    val isAdmin = emp.role.equals("ADMIN", ignoreCase = true)

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeProfileDialog by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(forceEditProfile) {
        if (forceEditProfile) {
            showChangeProfileDialog = true
            onForceEditConsumed()
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onClose = { showChangePinDialog = false },
            onSuccess = {
                saveMessage = "Đổi PIN thành công!"
            },
            isAdmin = isAdmin,               // <-- pass admin flag
            adminId = if (isAdmin) emp.id else null // <-- pass admin id when admin
        )
    }

    if (showChangeProfileDialog) {
        ChangeProfileDialog(
            vm = vm,
            isAuthenticated = isAuthenticated,
            onClose = { showChangeProfileDialog = false }
        )
    }

    // --- GIAO DIỆN MỚI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header "Employee Profile"
        Text(
            text = "Employee Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // ----- Visual ID Card đưa lên ngay sau header -----
        VisualEmployeeIdCard(
            avatarBitmap = vm.avatarBitmap,
            name = emp.name,
            id = emp.id,
            dob = emp.dob,
            dept = emp.department,
            pos = emp.position,
            isAdmin = isAdmin // <-- pass admin flag
        )

        // ----- Hai nút ở dưới card -----
        Button(
            onClick = {
                showChangeProfileDialog = true
            },
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Change Profile")
        }

        OutlinedButton(
            onClick = { showChangePinDialog = true },
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Change PIN")
        }

        // Thông báo lưu / đổi PIN thành công
        if (saveMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFE0F7EC),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF1BAA61)
                )
                Text(
                    text = saveMessage!!,
                    color = Color(0xFF1BAA61),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Card phía dưới “Visual Employee ID Card”
 */
@Composable
fun VisualEmployeeIdCard(
    avatarBitmap: ImageBitmap?,
    name: String,
    id: String,
    dob: String,
    dept: String,
    pos: String,
    isAdmin: Boolean = false // <-- new parameter
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.large
            )
            .heightIn(min = 260.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                // Ảnh chữ nhật bên trái
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(170.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Show avatar only for non-admin users; admins always see default initials
                    val shouldShowAvatar = !isAdmin && avatarBitmap != null

                    if (shouldShowAvatar) {
                        Image(
                            bitmap = avatarBitmap!!,
                            contentDescription = "Employee photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = name
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .takeLast(2)
                                .joinToString("") { it.first().uppercase() }
                                .ifBlank { "NV" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Các trường ở bên phải (Name, ID, DOB) – CHỈ HIỂN THỊ
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StaticInfoLine(
                        label = "Full Name",
                        value = name
                    )
                    StaticInfoLine(
                        label = "ID Number",
                        value = id
                    )
                    StaticInfoLine(
                        label = "Date of Birth",
                        value = dob
                    )
                }
            }

            Divider()

            // Department & Position hàng dưới – CHỈ HIỂN THỊ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    StaticInfoLine(
                        label = "Department",
                        value = dept
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    StaticInfoLine(
                        label = "Position",
                        value = pos
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableInfoLine(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun StaticInfoLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
fun ChangePinDialog(
    onClose: () -> Unit,
    onSuccess: () -> Unit,
    isAdmin: Boolean = false,   // <-- new parameter
    adminId: String? = null     // <-- admin id when isAdmin=true
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }

    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onClose() },
        icon = { Icon(Icons.Default.LockReset, null, modifier = Modifier.size(32.dp)) },
        title = { Text("Đổi Mã PIN", textAlign = TextAlign.Center) },
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
                    PinInputField(value = oldPin, onValueChange = { oldPin = it }, label = "PIN hiện tại")
                    PinInputField(value = newPin, onValueChange = { newPin = it }, label = "PIN mới")
                    PinInputField(value = confirmPin, onValueChange = { confirmPin = it }, label = "Nhập lại PIN mới")
                }

                if (message != null) {
                    Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    if (newPin.length < 4) {
                        message = "PIN phải có ít nhất 4 ký tự."
                        return@Button
                    } else if (newPin != confirmPin) {
                        message = "PIN mới không khớp."
                        return@Button
                    }

                    isLoading = true
                    message = null

                    if (isAdmin) {
                        // Admin flow: verify admin pin via server then change via admin API
                        scope.launch(Dispatchers.IO) {
                            try {
                                val isOldPinOk = repo.verifyAdminPin(oldPin) // suspend
                                if (isOldPinOk) {
                                    val changeOk = adminId?.let { repo.changeAdminPin(it, newPin) } ?: false
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (changeOk) {
                                            onSuccess()
                                            onClose()
                                        } else {
                                            message = "Lỗi khi đổi PIN cho Admin (server)."
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        message = "PIN hiện tại không đúng (Admin)."
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    message = "Lỗi khi gọi server."
                                }
                            }
                        }
                    } else {
                        // Existing user flow: verify + change on card
                        scope.launch(Dispatchers.IO) {
                            val isOldPinOk = repo.verifyPin(oldPin)
                            if (isOldPinOk) {
                                val changeOk = repo.changePin(oldPin, newPin)
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    if (changeOk) {
                                        repo.verifyPin(newPin) // refresh card state
                                        onSuccess()
                                        onClose()
                                    } else {
                                        message = "Lỗi khi ghi dữ liệu xuống thẻ!"
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    message = "PIN hiện tại không đúng!"
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !isLoading) { Text("Hủy") }
        }
    )
}

// Helper: Ô nhập PIN có nút mắt thần (Giữ nguyên)
@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                val desc = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = desc)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}


@Composable
fun EditableAvatar(
    currentBitmap: ImageBitmap?,
    fallbackName: String,
    onPickImage: () -> Unit,
    isAdmin: Boolean = false // <-- added flag
) {
    val size = 140.dp
    Box(modifier = Modifier.size(size)) {
        val shouldShowAvatar = !isAdmin && currentBitmap != null

        if (shouldShowAvatar) {
            Image(
                bitmap = currentBitmap!!,
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

        // Hide or disable pick button for admins
        if (!isAdmin) {
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
}

fun pickFile(): String? {
    val dialog = FileDialog(null as Frame?, "Chọn ảnh đại diện", FileDialog.LOAD)
    dialog.file = "*.jpg;*.jpeg;*.png"
    dialog.isVisible = true
    return if (dialog.directory != null && dialog.file != null) dialog.directory + dialog.file else null
}