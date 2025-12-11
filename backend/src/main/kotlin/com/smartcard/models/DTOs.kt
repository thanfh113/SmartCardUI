package com.smartcard.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// ===================================
// Department DTOs
// ===================================
@Serializable
data class DepartmentDTO(
    val id: Int? = null,
    val name: String,
    val description: String? = null
)

// ===================================
// Position DTOs
// ===================================
@Serializable
data class PositionDTO(
    val id: Int? = null,
    val name: String,
    val description: String? = null
)

// ===================================
// Employee DTOs
// ===================================
@Serializable
data class EmployeeDTO(
    val id: Int? = null,
    val cardUuid: String,
    val employeeId: String,
    val name: String,
    val dateOfBirth: String? = null,
    val departmentId: Int,
    val departmentName: String? = null,
    val positionId: Int,
    val positionName: String? = null,
    val role: String = "USER",
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal = BigDecimal.ZERO,
    val photoPath: String? = null,
    val isActive: Boolean = true
)

@Serializable
data class CreateEmployeeRequest(
    val cardUuid: String,
    val employeeId: String,
    val name: String,
    val dateOfBirth: String? = null,
    val departmentId: Int,
    val positionId: Int,
    val role: String = "USER",
    val photoPath: String? = null,
    val rsaPublicKey: String? = null, // Base64 encoded
    val pinHash: String? = null
)

@Serializable
data class UpdateEmployeeRequest(
    val name: String? = null,
    val dateOfBirth: String? = null,
    val departmentId: Int? = null,
    val positionId: Int? = null,
    val photoPath: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class CardSyncRequest(
    val cardUuid: String,
    val rsaPublicKey: String? = null, // Base64 encoded
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal? = null
)

// ===================================
// Product DTOs
// ===================================
@Serializable
data class ProductDTO(
    val id: Int? = null,
    val code: String,
    val name: String,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal,
    val category: String? = null,
    val isAvailable: Boolean = true
)

// ===================================
// Transaction DTOs
// ===================================
@Serializable
data class TransactionDTO(
    val id: Int? = null,
    val employeeId: Int,
    val employeeName: String? = null,
    val transType: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceBefore: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceAfter: BigDecimal,
    val description: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val transactionTime: String
)

@Serializable
data class TopUpRequest(
    val employeeId: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val description: String? = null
)

@Serializable
data class PaymentRequest(
    val employeeId: Int,
    val productId: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val signature: String, // Base64 encoded RSA signature
    val uniqueNumber: Int,
    val timestamp: Long
)

@Serializable
data class BalanceResponse(
    val employeeId: Int,
    val employeeName: String,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal
)

// ===================================
// Attendance DTOs
// ===================================
@Serializable
data class AttendanceLogDTO(
    val id: Int? = null,
    val employeeId: Int,
    val employeeName: String? = null,
    val workDate: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val status: String = "PENDING",
    val notes: String? = null
)

@Serializable
data class CheckInRequest(
    val employeeId: Int,
    val workDate: String, // YYYY-MM-DD
    val checkInTime: String // HH:mm:ss
)

@Serializable
data class CheckOutRequest(
    val employeeId: Int,
    val workDate: String, // YYYY-MM-DD
    val checkOutTime: String // HH:mm:ss
)

@Serializable
data class AttendanceReportRequest(
    val startDate: String? = null,
    val endDate: String? = null,
    val employeeId: Int? = null
)

// ===================================
// Common Response DTOs
// ===================================
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String,
    val code: String
)
