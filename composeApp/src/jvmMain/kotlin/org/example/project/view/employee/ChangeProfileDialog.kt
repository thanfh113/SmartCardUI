package org.example.project.view.employee

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.example.project.data.CardRepositoryProvider // Import này là cần thiết

@Composable
fun SimpleInfoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (!readOnly) onValueChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !readOnly,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeProfileDialog(
    vm: EmployeeViewModel,
    isAuthenticated: Boolean = false,
    onClose: () -> Unit
) {
    val repo = CardRepositoryProvider.current // Lấy repository

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) vm.loadAvatarFromCard()
    }

    val emp = vm.employee
    val isAdmin = emp.role.equals("ADMIN", ignoreCase = true)

    var name by remember { mutableStateOf(emp.name) }
    var dob by remember { mutableStateOf(emp.dob) }
    var dept by remember { mutableStateOf(emp.department) }
    var pos by remember { mutableStateOf(emp.position) }
    val id = emp.id // ID bị khóa

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // 🔥 TẢI DỮ LIỆU ĐỘNG TỪ SERVER
    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // Map: ID -> Name
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // Map: ID -> Name

    // Kích hoạt tải dữ liệu khi khởi tạo
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
        }
    }

    // Lấy danh sách tên cho Dropdown
    val departmentNames = departmentsMap.values.toList()
    val positionNames = positionsMap.values.toList()

    var expandedDept by remember { mutableStateOf(false) }
    var expandedPos by remember { mutableStateOf(false) } // 🔥 Thêm state cho Dropdown chức vụ

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chỉnh sửa hồ sơ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (isAdmin) {
                        // Use admin update endpoint for ADMIN
                        vm.updateAdminProfile(id, name, dob, dept, pos)
                    } else {
                        // Keep existing behavior for normal users (write to card + server)
                        vm.updateEmployee(id, name, dob, dept, pos)
                    }
                    onClose()
                }) { Text("Lưu") }
                TextButton(onClick = onClose) { Text("Hủy") }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar (Giữ nguyên logic cũ) -> pass isAdmin so EditableAvatar hides pick for admin
                EditableAvatar(
                    currentBitmap = vm.avatarBitmap,
                    fallbackName = name,
                    onPickImage = { pickFile()?.let { vm.uploadAvatar(it) } },
                    isAdmin = emp.role.equals("ADMIN", ignoreCase = true) // <-- new argument
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 1. ID (Khóa)
                        SimpleInfoField("Mã Nhân Viên (Không thể đổi)", id, {}, readOnly = true)

                        // 2. Họ Tên
                        SimpleInfoField("Họ và Tên", name, { name = it })

                        // 3. Phòng Ban (Dropdown - Dữ liệu động)
                        ExposedDropdownMenuBox(
                            expanded = expandedDept,
                            onExpandedChange = { expandedDept = !expandedDept }
                        ) {
                            OutlinedTextField(
                                value = dept,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Phòng Ban") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDept) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDept,
                                onDismissRequest = { expandedDept = false }
                            ) {
                                departmentNames.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = { dept = item; expandedDept = false }
                                    )
                                }
                            }
                        }

                        // 🔥 4. Chức Vụ (Dropdown - Dữ liệu động)
                        ExposedDropdownMenuBox(
                            expanded = expandedPos,
                            onExpandedChange = { expandedPos = !expandedPos }
                        ) {
                            OutlinedTextField(
                                value = pos,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chức Vụ") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPos) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedPos,
                                onDismissRequest = { expandedPos = false }
                            ) {
                                positionNames.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = { pos = item; expandedPos = false }
                                    )
                                }
                            }
                        }

                        // 5. Ngày Sinh
                        SimpleInfoField(
                            label = "Ngày Sinh",
                            value = dob,
                            onValueChange = { dob = it },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Chọn ngày")
                                }
                            }
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )

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