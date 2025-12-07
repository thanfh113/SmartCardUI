package org.example.project.data

import org.example.project.model.*

interface CardRepository {
    // --- Hàm nghiệp vụ mới ---
    fun connect(): Boolean
    fun disconnect()
    fun authenticateCard(): Boolean // <-- Hàm quan trọng để chống thẻ giả

    // --- Các hàm nghiệp vụ cũ ---
    fun getCardState(): CardState

    fun checkCardInitialized(): Boolean
    fun setupFirstPin(newPin: String): Boolean
    fun verifyPin(input: String): Boolean
    fun changePin(oldPin: String, newPin: String): Boolean

    fun getEmployee(): Employee
    fun updateEmployee(newEmployee: Employee)

    fun getBalance(): Double
    fun topUp(amount: Double): Boolean
    fun pay(amount: Double, description: String): Boolean

    fun addAccessLog(type: AccessType, description: String)
    fun getAccessLogs(): List<AccessLogEntry>

    fun getTransactions(): List<Transaction>

    // Upload ảnh (trả về true nếu thành công)
    fun uploadAvatar(imageBytes: ByteArray): Boolean

    // Lấy ảnh về (trả về mảng byte raw, sau đó UI sẽ convert sang Bitmap)
    fun getAvatar(): ByteArray
}

object CardRepositoryProvider {
    // DÙNG THẺ THẬT (Class này đã được cập nhật logic ở câu trả lời trước)
    var current: CardRepository = SmartCardRepositoryImpl()

    // Nếu muốn test mock thì đổi lại:
    // var current: CardRepository = MockCardRepository
}