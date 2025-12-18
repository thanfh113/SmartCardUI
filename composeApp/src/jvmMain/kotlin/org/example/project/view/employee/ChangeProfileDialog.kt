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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.example.project.data.CardRepositoryProvider

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
    val repo = CardRepositoryProvider.current

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) vm.loadAvatarFromCard()
    }

    val emp = vm.employee
    val isAdmin = emp.role.equals("ADMIN", ignoreCase = true)

    var name by remember { mutableStateOf(emp.name) }
    var dob by remember { mutableStateOf(emp.dob) }
    var dept by remember { mutableStateOf(emp.department) }
    var pos by remember { mutableStateOf(emp.position) }
    val id = emp.id // Luôn lấy ID từ object gốc, không đổi

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // Báo lỗi tuổi
    var ageErrorMessage by remember { mutableStateOf<String?>(null) }

    var departmentsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var positionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            departmentsMap = repo.getDepartmentsMap()
            positionsMap = repo.getPositionsMap()
        }
    }

    val departmentNames = departmentsMap.values.toList()
    val positionNames = positionsMap.values.toList()

    var expandedDept by remember { mutableStateOf(false) }
    var expandedPos by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chỉnh sửa hồ sơ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    // 🔥 KIỂM TRA TUỔI >= 15
                    try {
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        val birthDate = LocalDate.parse(dob, formatter)
                        val age = Period.between(birthDate, LocalDate.now()).years

                        if (age < 15) {
                            ageErrorMessage = "❌ Nhân viên phải từ đủ 15 tuổi trở lên!"
                            return@TextButton
                        }
                    } catch (e: Exception) {
                        ageErrorMessage = "❌ Định dạng ngày sinh không hợp lệ!"
                        return@TextButton
                    }

                    // Nếu qua được check tuổi thì tiến hành lưu
                    if (isAdmin) {
                        vm.updateAdminProfile(id, name, dob, dept, pos)
                    } else {
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
                EditableAvatar(
                    currentBitmap = vm.avatarBitmap,
                    fallbackName = name,
                    onPickImage = { pickFile()?.let { vm.uploadAvatar(it) } },
                    isAdmin = isAdmin
                )

                // Hiển thị lỗi tuổi nếu có
                if (ageErrorMessage != null) {
                    Text(
                        text = ageErrorMessage!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 1. ID (KHÓA HOÀN TOÀN)
                        SimpleInfoField(
                            label = "Mã Nhân Viên (Hệ thống tự động)",
                            value = id,
                            onValueChange = {},
                            readOnly = true
                        )

                        // 2. Họ Tên
                        SimpleInfoField("Họ và Tên", name, {
                            name = it
                            ageErrorMessage = null // Reset lỗi khi gõ lại
                        })

                        // 3. Phòng Ban (Dropdown)
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

                        // 4. Chức Vụ (Dropdown)
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
                            onValueChange = { dob = it; ageErrorMessage = null },
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
                        dob = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        ageErrorMessage = null
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = datePickerState) }
    }
}