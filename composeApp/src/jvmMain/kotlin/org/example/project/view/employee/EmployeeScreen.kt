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
    onChangePin: ((() -> Unit) -> Unit)? = null, // vẫn giữ để không phá API cũ
    isAuthenticated: Boolean = false,
) {
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) vm.loadAvatarFromCard() }

    val emp = vm.employee // luôn cập nhật từ viewmodel

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeProfileDialog by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    if (showChangePinDialog) {
        ChangePinDialog(
            onClose = { showChangePinDialog = false },
            onSuccess = {
                saveMessage = "Đổi PIN thành công!"
            }
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
            pos = emp.position
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

        // Thông báo lưu / đổi PIN thành công (giữ nguyên)
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
 * – Bên trái là ảnh (hoặc placeholder)
 * – Bên phải là các trường: Full Name, ID Number, Date of Birth, Department, Position
 */
@Composable
fun VisualEmployeeIdCard(
    avatarBitmap: ImageBitmap?,
    name: String,
    id: String,
    dob: String,
    dept: String,
    pos: String
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
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
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

    val repo = CardRepositoryProvider.current

    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Default.LockReset, null, modifier = Modifier.size(32.dp)) },
        title = { Text("Đổi Mã PIN", textAlign = TextAlign.Center) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 3 Ô nhập liệu có nút ẩn/hiện
                PinInputField(
                    value = oldPin,
                    onValueChange = { oldPin = it },
                    label = "PIN hiện tại"
                )

                PinInputField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = "PIN mới"
                )

                PinInputField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = "Nhập lại PIN mới"
                )

                if (message != null) {
                    Text(
                        text = message!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length < 4) {
                        message = "PIN phải có ít nhất 4 ký tự."
                    } else if (newPin != confirmPin) {
                        message = "PIN mới không khớp."
                    } else {
                        // Gọi Repository để đổi PIN
                        val ok = repo.changePin(oldPin, newPin)
                        if (ok) {
                            // QUAN TRỌNG: Verify lại ngay bằng PIN mới để không bị mất session
                            repo.verifyPin(newPin)
                            onSuccess()
                            onClose()
                        } else {
                            message = "PIN hiện tại không đúng hoặc thẻ bị lỗi."
                        }
                    }
                }
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Hủy") }
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