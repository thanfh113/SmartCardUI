package org.example.project.data

import org.example.project.model.*
import java.time.LocalDateTime
import kotlin.math.max

object MockCardRepository : CardRepository {

    private var pin: String = "1234"
    private val maxPinTries = 3
    private var pinTriesRemaining = maxPinTries
    private var isBlocked = false
    private var isCardSetup = false

    private var balance: Double = 100_000.0

    private var employee = Employee(
        id = "NV001",
        name = "Nguyễn Văn A",
        dob = "01/01/1995",
        department = "IT",
        position = "Developer",
        photoPath = null
    )

    private val accessLogs = mutableListOf<AccessLogEntry>()
    private val transactions = mutableListOf<Transaction>()
    override fun connect(): Boolean {
        TODO("Not yet implemented")
    }

    override fun disconnect() {
        TODO("Not yet implemented")
    }

    override fun authenticateCard(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCardState(): CardState =
        CardState(
            maxPinTries = maxPinTries,
            pinTriesRemaining = pinTriesRemaining,
            isBlocked = isBlocked,
            balance = balance
        )

    // ✅ MỚI: Implement hàm check
    override fun checkCardInitialized(): Boolean {
        return isCardSetup
    }

    // ✅ MỚI: Implement hàm setup
    override fun setupFirstPin(newPin: String): Boolean {
        pin = newPin
        isCardSetup = true
        return true
    }

    override fun verifyPin(input: String): Boolean {
        if (isBlocked) return false
        if (input == pin) {
            pinTriesRemaining = maxPinTries
            return true
        }
        pinTriesRemaining = max(pinTriesRemaining - 1, 0)
        if (pinTriesRemaining == 0) isBlocked = true
        return false
    }

    override fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        pin = newPin
        return true
    }

    override fun getEmployee(): Employee = employee

    override fun updateEmployee(newEmployee: Employee) {
        employee = newEmployee
        // Thực tế: mã hóa + ghi xuống thẻ
    }

    override fun getBalance(): Double = balance

    override fun topUp(amount: Double): Boolean {
        if (amount <= 0) return false
        balance += amount
        transactions += Transaction(
            time = LocalDateTime.now(),
            type = TransactionType.TOP_UP,
            amount = amount,
            description = "Nạp tiền tại quầy",
            balanceAfter = balance
        )
        return true
    }

    override fun pay(amount: Double, description: String): Boolean {
        if (amount <= 0 || amount > balance) return false
        balance -= amount
        transactions += Transaction(
            time = LocalDateTime.now(),
            type = TransactionType.PAYMENT,
            amount = amount,
            description = description,
            balanceAfter = balance
        )
        return true
    }

    override fun addAccessLog(type: AccessType, description: String) {
        accessLogs.add(
            0,
            AccessLogEntry(
                time = LocalDateTime.now(),
                accessType = type,
                description = description
            )
        )
        if (accessLogs.size > 50) accessLogs.removeLast()
    }

    override fun getAccessLogs(): List<AccessLogEntry> = accessLogs
    override fun getTransactions(): List<Transaction> = transactions
    override fun uploadAvatar(imageBytes: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAvatar(): ByteArray {
        TODO("Not yet implemented")
    }
}
