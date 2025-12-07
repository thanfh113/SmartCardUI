package org.example.project.view.employee
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeProfileDialog(
    vm: EmployeeViewModel,
    isAuthenticated: Boolean = false,
    onClose: () -> Unit
) {
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) vm.loadAvatarFromCard()
    }

    val emp = vm.employee

    var name by remember { mutableStateOf(emp.name) }
    var dob by remember { mutableStateOf(emp.dob) }
    var dept by remember { mutableStateOf(emp.department) }
    var pos by remember { mutableStateOf(emp.position) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showChangePinDialog) {
        ChangePinDialog(
            onClose = { showChangePinDialog = false },
            onSuccess = { saveMessage = "Đổi PIN thành công!" }
        )
    }

    AlertDialog(
        onDismissRequest = onClose,
        // mình tự custom header và body nên confirm/dismiss button để trống
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("Close")
                }
                Text(
                    text = "Edit Employee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = {
                        vm.updateEmployee(name, dob, dept, pos)
                        onClose()
                    }
                ) {
                    Text("Save")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar
                EditableAvatar(
                    currentBitmap = vm.avatarBitmap,
                    fallbackName = name,
                    onPickImage = { pickFile()?.let { vm.uploadAvatar(it) } }
                )

                // Tên + mô tả
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (name.isBlank()) "Chưa nhập tên" else name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (pos.isBlank()) "Chưa có chức vụ" else pos,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (dept.isBlank()) "Chưa có phòng ban" else dept,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Chip ID
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "ID: ${emp.id}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Thông báo lưu / đổi PIN
                if (saveMessage != null) {
                    Text(
                        text = saveMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Card thông tin chi tiết
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Detailed Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        SimpleInfoField(
                            label = "Full Name",
                            value = name,
                            onValueChange = {
                                name = it
                                saveMessage = null
                            }
                        )

                        SimpleInfoField(
                            label = "Date of Birth",
                            value = dob,
                            onValueChange = {
                                dob = it
                                saveMessage = null
                            },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Pick date",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        SimpleInfoField(
                            label = "Department",
                            value = dept,
                            onValueChange = {
                                dept = it
                                saveMessage = null
                            }
                        )

                        SimpleInfoField(
                            label = "Position",
                            value = pos,
                            onValueChange = {
                                pos = it
                                saveMessage = null
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(24.dp)
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val formatted = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

                            dob = formatted
                            saveMessage = null
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState
            )
        }
    }

}

@Composable
fun SimpleInfoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    trailingIcon: (@Composable (() -> Unit))? = null
) {
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
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = trailingIcon,
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
