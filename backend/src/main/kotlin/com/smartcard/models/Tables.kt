package com.smartcard.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object Departments : IntIdTable("departments") {
    val name = varchar("name", 100).uniqueIndex()
    val description = text("description").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object Positions : IntIdTable("positions") {
    val name = varchar("name", 100).uniqueIndex()
    val description = text("description").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object Employees : IntIdTable("employees") {
    val cardUuid = varchar("card_uuid", 36).uniqueIndex()
    val employeeId = varchar("employee_id", 20).uniqueIndex()
    val name = varchar("name", 100)
    val dateOfBirth = varchar("date_of_birth", 10).nullable()
    val departmentId = reference("department_id", Departments)
    val positionId = reference("position_id", Positions)
    val role = enumeration<EmployeeRole>("role").default(EmployeeRole.USER)
    val balance = decimal("balance", 15, 2).default(0.toBigDecimal())
    val photoPath = varchar("photo_path", 255).nullable()
    val rsaPublicKey = blob("rsa_public_key").nullable()
    val pinHash = varchar("pin_hash", 255).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object Products : IntIdTable("products") {
    val code = varchar("code", 50).uniqueIndex()
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val price = decimal("price", 10, 2)
    val category = varchar("category", 50).nullable()
    val isAvailable = bool("is_available").default(true)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object Transactions : IntIdTable("transactions") {
    val employeeId = reference("employee_id", Employees)
    val transType = enumeration<TransactionType>("trans_type")
    val amount = decimal("amount", 15, 2)
    val balanceBefore = decimal("balance_before", 15, 2)
    val balanceAfter = decimal("balance_after", 15, 2)
    val description = text("description").nullable()
    val productId = reference("product_id", Products).nullable()
    val signature = blob("signature").nullable()
    val uniqueNumber = integer("unique_number").nullable()
    val transactionTime = timestamp("transaction_time").defaultExpression(CurrentTimestamp)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object AttendanceLogs : IntIdTable("attendance_logs") {
    val employeeId = reference("employee_id", Employees)
    val workDate = varchar("work_date", 10)
    val checkInTime = varchar("check_in_time", 8).nullable()
    val checkOutTime = varchar("check_out_time", 8).nullable()
    val status = varchar("status", 20).default("PENDING")
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    
    init {
        uniqueIndex(employeeId, workDate)
    }
}

enum class EmployeeRole {
    ADMIN, USER
}

enum class TransactionType {
    TOPUP, PAYMENT
}
