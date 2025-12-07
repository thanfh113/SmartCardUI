package org.example.project.view.employee

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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

    fun loadAvatarFromCard() {
        try {
            val bytes = repo.getAvatar()
            if (bytes.isNotEmpty()) avatarBitmap = ImageUtils.bytesToBitmap(bytes)
        } catch (e: Exception) { }
    }

    fun updateEmployee(name: String, dob: String, dept: String, position: String) {
        employee = employee.copy(name = name, dob = dob, department = dept, position = position)
        repo.updateEmployee(employee)
    }

    fun uploadAvatar(filePath: String) {
        val processedBytes = ImageUtils.processImageForCard(filePath)
        if (processedBytes != null) {
            if (repo.uploadAvatar(processedBytes)) {
                avatarBitmap = ImageUtils.bytesToBitmap(processedBytes)
            }
        }
    }
}

@Composable
fun EmployeeScreen(
    vm: EmployeeViewModel = remember { EmployeeViewModel() },
    onChangePin: ((() -> Unit) -> Unit)? = null,
    isAuthenticated: Boolean = false
) {
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) vm.loadAvatarFromCard() }

    val emp = vm.employee

    // State local để edit
    var name by remember { mutableStateOf(emp.name) }
    var dob by remember { mutableStateOf(emp.dob) }
    var dept by remember { mutableStateOf(emp.department) }
    var pos by remember { mutableStateOf(emp.position) }

    // State quản lý Dialog đổi PIN
    var showChangePinDialog by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // --- HIỂN THỊ DIALOG ĐỔI PIN NẾU ĐƯỢC KÍCH HOẠT ---
    if (showChangePinDialog) {
        ChangePinDialog(
            onClose = { showChangePinDialog = false },
            onSuccess = {
                saveMessage = "Đổi PIN thành công!"
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- CỘT TRÁI: THẺ ĐỊNH DANH (Profile Card) ---
        Card(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar lớn
                Box(contentAlignment = Alignment.BottomEnd) {
                    EditableAvatar(
                        currentBitmap = vm.avatarBitmap,
                        fallbackName = name,
                        onPickImage = {
                            pickFile()?.let { vm.uploadAvatar(it) }
                        }
                    )
                }

                Divider(Modifier.padding(vertical = 8.dp))

                // Tên & ID
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name.ifBlank { "Chưa nhập tên" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "ID: ${emp.id}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Info tóm tắt
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pos.ifBlank { "Chưa có chức vụ" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dept.ifBlank { "Chưa có phòng ban" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                // Các nút hành động
                Button(
                    onClick = {
                        vm.updateEmployee(name, dob, dept, pos)
                        saveMessage = "Đã lưu thông tin!"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lưu thay đổi")
                }

                // Nút mở Dialog Đổi PIN
                OutlinedButton(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.LockReset, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đổi mã PIN")
                }

                if (saveMessage != null) {
                    Text(
                        saveMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- CỘT PHẢI: FORM CHỈNH SỬA (Detailed Info) ---
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    "Thông tin chi tiết",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // Grid Layout cho Form
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoField(
                        value = emp.id,
                        onValueChange = {},
                        label = "Mã nhân viên",
                        icon = Icons.Default.Badge,
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                    InfoField(
                        value = name,
                        onValueChange = { name = it; saveMessage = null },
                        label = "Họ và tên",
                        icon = Icons.Default.Person,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoField(
                        value = dob,
                        onValueChange = { dob = it; saveMessage = null },
                        label = "Ngày sinh (DD/MM/YYYY)",
                        icon = Icons.Default.Cake,
                        modifier = Modifier.weight(1f)
                    )
                    InfoField(
                        value = dept,
                        onValueChange = { dept = it; saveMessage = null },
                        label = "Phòng ban",
                        icon = Icons.Default.Apartment,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoField(
                        value = pos,
                        onValueChange = { pos = it; saveMessage = null },
                        label = "Chức vụ",
                        icon = Icons.Default.Work,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// --- LOGIC DIALOG ĐỔI PIN (CÓ HIỆN MẬT KHẨU) ---
@Composable
fun ChangePinDialog(
    onClose: () -> Unit,
    onSuccess: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) } // ✅ Loading state

    val repo = CardRepositoryProvider.current
    val scope = rememberCoroutineScope() // ✅ Coroutine Scope

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
                    } else if (newPin != confirmPin) {
                        message = "PIN mới không khớp."
                    } else {
                        isLoading = true
                        message = null

                        // ✅ LOGIC XỬ LÝ BACKGROUND
                        scope.launch(Dispatchers.IO) {
                            // B1: Verify PIN cũ trước
                            val isOldPinOk = repo.verifyPin(oldPin)

                            if (isOldPinOk) {
                                // B2: Nếu đúng -> Tiến hành đổi sang PIN mới
                                val changeOk = repo.changePin(oldPin, newPin)

                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    if (changeOk) {
                                        // Verify lại lần nữa để đảm bảo session master key mới nhất (optional nhưng nên làm)
                                        repo.verifyPin(newPin)
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

// Helper: Ô nhập PIN có nút mắt thần
@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) }, // Chỉ cho nhập số
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        trailingIcon = {
            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
            val description = if (passwordVisible) "Hide password" else "Show password"

            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = description)
            }
        }
    )
}

// Component trường nhập liệu thông tin (Profile)
@Composable
fun InfoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        enabled = enabled,
        singleLine = true,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

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

fun pickFile(): String? {
    val dialog = FileDialog(null as Frame?, "Chọn ảnh đại diện", FileDialog.LOAD)
    dialog.file = "*.jpg;*.jpeg;*.png"
    dialog.isVisible = true
    return if (dialog.directory != null && dialog.file != null) dialog.directory + dialog.file else null
}