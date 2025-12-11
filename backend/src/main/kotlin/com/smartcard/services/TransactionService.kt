package com.smartcard.services

import com.smartcard.data.DatabaseFactory.dbQuery
import com.smartcard.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TransactionService(
    private val employeeService: EmployeeService,
    private val cryptoService: CryptoService
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun topUp(request: TopUpRequest): TransactionDTO = dbQuery {
        if (request.amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Top-up amount must be positive")
        }

        val currentBalance = employeeService.getBalance(request.employeeId)
            ?: throw IllegalArgumentException("Employee not found")

        val newBalance = currentBalance + request.amount

        // Update employee balance
        employeeService.updateBalance(request.employeeId, newBalance)

        // Create transaction record
        val id = Transactions.insertAndGetId {
            it[employeeId] = request.employeeId
            it[transType] = TransactionType.TOPUP
            it[amount] = request.amount
            it[balanceBefore] = currentBalance
            it[balanceAfter] = newBalance
            it[description] = request.description ?: "Top-up"
        }

        getTransactionById(id.value)!!
    }

    suspend fun processPayment(request: PaymentRequest): TransactionDTO = dbQuery {
        if (request.amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Payment amount must be positive")
        }

        // Get employee and verify balance
        val employee = employeeService.getEmployeeById(request.employeeId)
            ?: throw IllegalArgumentException("Employee not found")

        val currentBalance = employee.balance
        if (currentBalance < request.amount) {
            throw IllegalArgumentException("Insufficient balance")
        }

        // Get product
        val product = Products.selectAll().where { Products.id eq request.productId }
            .singleOrNull() ?: throw IllegalArgumentException("Product not found")

        if (!product[Products.isAvailable]) {
            throw IllegalArgumentException("Product is not available")
        }

        // Verify RSA signature if public key exists
        val publicKeyBytes = Employees.selectAll().where { Employees.id eq request.employeeId }
            .map { it[Employees.rsaPublicKey]?.bytes }
            .singleOrNull()

        if (publicKeyBytes != null) {
            val signatureBytes = cryptoService.decodeBase64(request.signature)
            val employeeIdBytes = employee.employeeId.toByteArray().copyOf(16) // Pad to 16 bytes
            val amountCents = (request.amount.toDouble() * 100).toInt()
            val timestamp = request.timestamp.toInt()

            val isValid = cryptoService.verifyPaymentTransaction(
                signature = signatureBytes,
                employeeId = employeeIdBytes,
                amount = amountCents,
                timestamp = timestamp,
                uniqueNumber = request.uniqueNumber,
                publicKeyBytes = publicKeyBytes
            )

            if (!isValid) {
                throw SecurityException("Invalid transaction signature")
            }
        }

        val newBalance = currentBalance - request.amount

        // Update employee balance
        employeeService.updateBalance(request.employeeId, newBalance)

        // Create transaction record
        val id = Transactions.insertAndGetId {
            it[employeeId] = request.employeeId
            it[transType] = TransactionType.PAYMENT
            it[amount] = request.amount
            it[balanceBefore] = currentBalance
            it[balanceAfter] = newBalance
            it[description] = "Payment for ${product[Products.name]}"
            it[productId] = request.productId
            it[signature] = ExposedBlob(cryptoService.decodeBase64(request.signature))
            it[uniqueNumber] = request.uniqueNumber
        }

        getTransactionById(id.value)!!
    }

    suspend fun getTransactionHistory(employeeId: Int, limit: Int = 50): List<TransactionDTO> = dbQuery {
        Transactions
            .innerJoin(Employees)
            .leftJoin(Products)
            .selectAll()
            .where { Transactions.employeeId eq employeeId }
            .orderBy(Transactions.transactionTime to SortOrder.DESC)
            .limit(limit)
            .map { toTransactionDTO(it) }
    }

    suspend fun getTransactionById(id: Int): TransactionDTO? = dbQuery {
        Transactions
            .innerJoin(Employees)
            .leftJoin(Products)
            .selectAll()
            .where { Transactions.id eq id }
            .map { toTransactionDTO(it) }
            .singleOrNull()
    }

    private fun toTransactionDTO(row: ResultRow): TransactionDTO {
        val timestamp = row[Transactions.transactionTime]
        val formattedTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
            .format(dateFormatter)

        return TransactionDTO(
            id = row[Transactions.id].value,
            employeeId = row[Transactions.employeeId].value,
            employeeName = row[Employees.name],
            transType = row[Transactions.transType].name,
            amount = row[Transactions.amount],
            balanceBefore = row[Transactions.balanceBefore],
            balanceAfter = row[Transactions.balanceAfter],
            description = row[Transactions.description],
            productId = row[Transactions.productId]?.value,
            productName = row.getOrNull(Products.name),
            transactionTime = formattedTime
        )
    }
}
