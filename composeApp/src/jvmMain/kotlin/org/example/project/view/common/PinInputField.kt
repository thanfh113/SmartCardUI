package org.example.project.view.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Component nhập PIN 6 số với 6 ô riêng biệt (OTP-style)
 * @param value Giá trị PIN hiện tại (tối đa 6 ký tự)
 * @param onValueChange Callback khi giá trị thay đổi
 * @param enabled Trạng thái enable/disable
 * @param isPassword Hiển thị dạng password (•) hay text
 */
@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = true
) {
    // Giới hạn 6 ký tự
    val pinValue = value.take(6)
    val focusRequester = remember { FocusRequester() }
    
    // Auto focus khi component được tạo
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore nếu không thể focus
        }
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Hiển thị 6 ô PIN ở trên
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val char = pinValue.getOrNull(index)?.toString() ?: ""
                val isFocused = pinValue.length == index
                
                PinDigitBox(
                    digit = char,
                    isFocused = isFocused,
                    isPassword = isPassword,
                    enabled = enabled
                )
            }
        }
        
        // TextField ẩn để nhận input (đặt phía sau, trong suốt)
        BasicTextField(
            value = pinValue,
            onValueChange = { newValue ->
                // Chỉ chấp nhận số, tối đa 6 ký tự
                if (newValue.all { it.isDigit() } && newValue.length <= 6) {
                    onValueChange(newValue)
                }
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(focusRequester)
                .padding(horizontal = 50.dp), // Padding để không che ô hiển thị
            decorationBox = { innerTextField ->
                // Không hiển thị gì, chỉ để nhận input
                Box(modifier = Modifier.size(0.dp))
            }
        )
    }
}

/**
 * Một ô hiển thị 1 chữ số PIN
 */
@Composable
private fun PinDigitBox(
    digit: String,
    isFocused: Boolean,
    isPassword: Boolean,
    enabled: Boolean
) {
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        isFocused -> MaterialTheme.colorScheme.primary
        digit.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline
    }
    
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        digit.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPassword && digit.isNotEmpty()) "•" else digit,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
