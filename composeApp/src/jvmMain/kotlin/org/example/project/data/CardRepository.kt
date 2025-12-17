package org.example.project.data

import io.ktor.http.HttpStatusCode
import org.example.project.model.*

interface CardRepository {
    fun connect(): Boolean
    fun disconnect()
    fun authenticateCard(): Boolean

    fun getCardState(): CardState

    fun checkCardInitialized(): Boolean
    fun setupFirstPin(newPin: String): Boolean
    fun verifyPin(input: String): Boolean
    fun changePin(oldPin: String, newPin: String): Boolean

    fun getEmployee(): Employee
    fun getCardID(): ByteArray
    fun updateEmployee(newEmployee: Employee)

    fun initEmployeeAfterActivation(): Employee

    fun getBalance(): Double
    fun topUp(amount: Double): Boolean
    fun pay(amount: Double, description: String): Boolean
    fun addAccessLog(type: AccessType, description: String): Boolean
    fun getAccessLogs(): List<AccessLogEntry>

    fun getTransactions(): List<Transaction>

    fun uploadAvatar(imageBytes: ByteArray): Boolean
    fun getAvatar(): ByteArray

    // --- CÁC HÀM TƯƠNG TÁC SERVER (CHUNG) ---
    suspend fun getNextId(department: String): String
    suspend fun getCardRole(uuid: String): String
    suspend fun getAllUsers(): List<UserResponse>
    suspend fun changeUserStatus(uuid: String, isActive: Boolean): Boolean

    // --- CÁC HÀM DÀNH RIÊNG CHO ADMIN (SERVER MODE) ---
    suspend fun issueCardForUser(user: Employee): Boolean
    suspend fun getEmployeeFromServer(uuid: String): Employee?
    suspend fun getCardIDHex(): String
    suspend fun adminLogin(id: String, pin: String): Boolean
    suspend fun deleteUser(targetUuid: String, adminPin: String): Boolean

    // ✅ 3 hàm quan trọng mới thêm cho Admin:
    suspend fun adminAccessLog(adminId: String, typeStr: String, gate: String): HttpStatusCode
    suspend fun updateAdminProfile(id: String, name: String, dob: String, dept: String, position: String): Boolean
    suspend fun adminTransaction(adminId: String, amount: Double, desc: String): Boolean
    suspend fun verifyAdminPin(pin: String): Boolean
    suspend fun adminTransaction(amount: Double, description: String, currentBalance: Double): HttpStatusCode
    suspend fun getAdminBalance(adminId: String): Double
    suspend fun changeAdminPin(id: String, newPin: String): Boolean
    suspend fun reportPinChanged(cardUuid: String): Boolean
    /** Lấy Map các Phòng ban từ Server (ID -> Name) */
    suspend fun getDepartmentsMap(): Map<String, String>

    /** Lấy Map các Chức vụ từ Server (ID -> Name) */
    suspend fun getPositionsMap(): Map<String, String>
    suspend fun getProducts(): List<Product>
    suspend fun getServerLogs(employeeId: String? = null): List<HistoryLogEntry>
    // THÊM: Quản lý khóa thẻ
    fun isCardLocked(): Boolean
    suspend fun adminUnlockCard(adminPin: String): Boolean
    suspend fun adminResetPin(adminPin: String, newPin: String): Boolean
    suspend fun adminVerifyWithAdminCard(adminPin: String): Boolean
    suspend fun adminLockCard(adminPin: String): Boolean

    suspend fun adminUnlockUserCard(adminPin: String, userCardUuid: String): Boolean
    suspend fun adminResetUserPin(
        adminPin: String,
        userCardUuid: String,
        newUserPin: String = "123456"
    ): Boolean
}


object CardRepositoryProvider {
    var current: CardRepository = SmartCardRepositoryImpl()
}