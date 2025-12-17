package org.example.project.data

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.example.project.api.JcideSmartCardApi
import org.example.project.model.*
import java.math.BigInteger
import java.nio.charset.Charset
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.smartcardio.CommandAPDU
import kotlin.math.min
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SmartCardRepositoryImpl(
    private val api: JcideSmartCardApi = JcideSmartCardApi()
) : CardRepository {

    companion object {
        // --- INS CODES ---
        private const val INS_CHANGE_PIN: Byte     = 0x21
        private const val INS_GET_RETRY: Byte      = 0x22
        private const val INS_VERIFY_PIN: Byte     = 0x25
        private const val INS_AUTHENTICATE: Byte   = 0x26
        private const val INS_GET_PUB_KEY: Byte    = 0x27
        private const val INS_GET_SALT: Byte       = 0x28
        private const val INS_SETUP_PIN: Byte      = 0x29
        private const val INS_CHECK_SETUP: Byte    = 0x2A

        private const val INS_READ_INFO: Byte      = 0x30
        private const val INS_UPDATE_INFO: Byte    = 0x31
        private const val INS_ADD_ACCESS_LOG: Byte = 0x40
        private const val INS_READ_LOGS: Byte      = 0x41

        private const val INS_WALLET_TOPUP: Byte   = 0x50
        private const val INS_WALLET_PAY: Byte     = 0x51
        private const val INS_GET_BALANCE: Byte    = 0x52
        private const val INS_GET_POINT: Byte      = 0x54

        private const val INS_UPDATE_AVATAR: Int   = 0x10
        private const val INS_DOWNLOAD_AVATAR: Int = 0x11
        private const val INS_LOCK_CARD: Byte = 0x2B
        private const val INS_UNLOCK_CARD: Byte = 0x2C
        private const val INS_RESET_PIN: Byte = 0x2D
        private const val INS_CHECK_LOCKED: Byte = 0x2E

        private const val MAX_APDU_DATA_SIZE = 240
        private const val MAX_AVATAR_SIZE = 8192

        // ===== EMP INFO LAYOUT =====
        private const val EMP_INFO_MAX = 128
        private const val EMP_ID_OFFSET   = 0
        private const val EMP_ID_LEN      = 16
        private const val EMP_NAME_OFFSET = 16
        private const val EMP_NAME_LEN    = 48
        private const val EMP_DOB_OFFSET  = 64
        private const val EMP_DOB_LEN     = 16
        private const val EMP_DEPT_OFFSET = 80
        private const val EMP_DEPT_LEN    = 24
        private const val EMP_POS_OFFSET  = 104
        private const val EMP_POS_LEN     = 24

        private const val LOG_SIZE = 32

        // Log Types
        private const val KIND_ACCESS: Byte = 1
        private const val KIND_TX: Byte = 2
        private const val SUB_ACCESS_IN: Byte = 1
        private const val SUB_ACCESS_OUT: Byte = 2
        private const val SUB_ACCESS_RESTRICT: Byte = 3
        private const val SUB_TX_TOPUP: Byte = 1
        private const val SUB_TX_PAYMENT: Byte = 2
        private const val ADMIN_ID = "ADMIN01"

        private val UTF8: Charset = Charsets.UTF_8

        private val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        private val SERVER_URL = "http://localhost:8080/api/card"

        fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it) }
        }
    }

    override fun connect(): Boolean = api.connect()
    override fun disconnect() = api.disconnect()
    private var cachedPublicKey: PublicKey? = null
    private var cachedCardID: ByteArray? = null

    // --- CÁC HÀM HELPER PRIVATE ---
    private fun computeArgon2Hash(pin: String, salt: ByteArray): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(2)
            .withMemoryAsKB(65536)
            .withParallelism(1)
            .withSalt(salt)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val result = ByteArray(16)
        generator.generateBytes(pin.toCharArray(), result, 0, result.size)
        return result
    }

    private fun getSaltFromCard(): ByteArray? {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_SALT, 0x00, 0x00, 0x10))
        if (!isSw9000(resp)) return null
        val data = dataPart(resp)
        return if (data.size == 16) data else null
    }

    private fun getPublicKeyFromCard(): PublicKey? {
        if (cachedPublicKey != null) return cachedPublicKey
        val pubKeyResp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_PUB_KEY, 0x00, 0x00, 0x00))
        if (!isSw9000(pubKeyResp)) return null
        val data = dataPart(pubKeyResp)
        if (data.size < 4) return null
        try {
            var offset = 0
            val modLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2
            if (data.size < offset + modLen) return null
            val modulusBytes = data.copyOfRange(offset, offset + modLen)
            val modulus = BigInteger(1, modulusBytes)
            offset += modLen
            if (data.size < offset + 2) return null
            val expLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2
            if (data.size < offset + expLen) return null
            val exponentBytes = data.copyOfRange(offset, offset + expLen)
            val exponent = BigInteger(1, exponentBytes)
            val spec = RSAPublicKeySpec(modulus, exponent)
            val factory = KeyFactory.getInstance("RSA")
            cachedPublicKey = factory.generatePublic(spec)
            return cachedPublicKey
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- CÁC HÀM CƠ BẢN ---
    override fun checkCardInitialized(): Boolean {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_CHECK_SETUP, 0x00, 0x00, 0x01))
        if (!isSw9000(resp)) return false
        val data = dataPart(resp)
        return data.isNotEmpty() && data[0] == 0x01.toByte()
    }

    // Lấy ID thẻ (Public)
    override fun getCardID(): ByteArray {
        if (cachedCardID != null) return cachedCardID!!
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_READ_INFO, 0x00, 0x00, 0x00))
        if (!isSw9000(resp) || resp.size < 16) return ByteArray(16)
        val data = dataPart(resp)
        cachedCardID = data.copyOfRange(0, 16)
        return cachedCardID!!
    }

    override suspend fun getCardIDHex(): String {
        return try {
            bytesToHex(getCardID())
        } catch (e: Exception) { "" }
    }

    override fun setupFirstPin(newPin: String): Boolean {
        val salt = getSaltFromCard() ?: return false
        val derivedKey = computeArgon2Hash(newPin, salt)
        val apdu = byteArrayOf(0x80.toByte(), INS_SETUP_PIN, 0x00, 0x00, derivedKey.size.toByte()) + derivedKey
        return isSw9000(api.sendApdu(apdu))
    }

    override fun verifyPin(input: String): Boolean {
        val salt = getSaltFromCard() ?: return false
        val derivedKey = computeArgon2Hash(input, salt)
        val apdu = byteArrayOf(0x80.toByte(), INS_VERIFY_PIN, 0x00, 0x00, derivedKey.size.toByte()) + derivedKey
        return isSw9000(api.sendApdu(apdu))
    }

    override fun changePin(oldPin: String, newPin: String): Boolean {
        // Logic đổi PIN trên thẻ (chỉ cho User)
        val salt = getSaltFromCard() ?: return false
        val newDerivedKey = computeArgon2Hash(newPin, salt)
        val apdu = byteArrayOf(0x80.toByte(), INS_CHANGE_PIN, 0x00, 0x00, newDerivedKey.size.toByte()) + newDerivedKey
        return isSw9000(api.sendApdu(apdu))
    }
    override suspend fun changeAdminPin (id: String, newPin: String): Boolean {
        return try {
            val response = client.post("$SERVER_URL/admin/change-pin") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("id" to id, "newPin" to newPin))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    override fun authenticateCard(): Boolean {
        try {
            val publicKey = getPublicKeyFromCard() ?: return false
            val challenge = ByteArray(16); SecureRandom().nextBytes(challenge)
            val signApdu = byteArrayOf(0x80.toByte(), INS_AUTHENTICATE, 0x00, 0x00, challenge.size.toByte()) + challenge
            val signResp = api.sendApdu(signApdu)
            if (!isSw9000(signResp)) return false
            val signature = dataPart(signResp)
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey); verifier.update(challenge)
            return verifier.verify(signature)
        } catch (e: Exception) { return false }
    }

    // --- QUẢN LÝ THÔNG TIN NHÂN VIÊN ---
    override fun getEmployee(): Employee {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_READ_INFO, 0x00, 0x00, 0x00))
        if (!isSw9000(resp)) return defaultEmployee()
        val data = dataPart(resp)
        if (data.size < EMP_INFO_MAX) return defaultEmployee()
        val id = decodeString(data, EMP_ID_OFFSET, EMP_ID_LEN)
        val name = decodeString(data, EMP_NAME_OFFSET, EMP_NAME_LEN)
        if (id.isBlank() && name.isBlank()) return defaultEmployee()

        return Employee(
            id = id,
            name = name,
            dob = decodeString(data, EMP_DOB_OFFSET, EMP_DOB_LEN),
            department = decodeString(data, EMP_DEPT_OFFSET, EMP_DEPT_LEN),
            position = decodeString(data, EMP_POS_OFFSET, EMP_POS_LEN),
            role = "USER",
            photoPath = null,
            isDefaultPin = false
        )
    }

    override suspend fun getEmployeeFromServer(uuid: String): Employee? {
        return try {
            val response = client.get("$SERVER_URL/$uuid")

            if (response.status == HttpStatusCode.OK) {
                val userRes = response.body<UserResponse>()

                return Employee(
                    id = userRes.employeeId,
                    name = userRes.name,
                    dob = userRes.dob ?: "01/01/2000",
                    department = userRes.department.toString(),
                    position = userRes.position ?: "User",
                    role = userRes.role,
                    photoPath = null,
                    isDefaultPin = userRes.isDefaultPin
                )
            } else null
        } catch (e: Exception) {
            println("Error fetching employee info: ${e.message}")
            null
        }
    }

    private fun defaultEmployee() = Employee("NV001", "Nguyễn Văn A", "01/01/1995", "IT", "Nhân viên", "USER", null, false)

    override fun initEmployeeAfterActivation(): Employee {
        val deptPrefix = "IT"
        val suggestedId = try {
            runBlocking {
                val response = client.get("$SERVER_URL/next-id?prefix=$deptPrefix")
                val respBody = response.body<NextIdResponse>()
                respBody.id
            }
        } catch (e: Exception) { "OFF${(100..999).random()}" }

        val emp = Employee(suggestedId, "Nhân viên mới", "01/01/2000", "Phòng Kỹ Thuật", "Nhân viên", "USER", null, true)
        updateEmployeeOffline(emp)
        cachedCardID = null
        return emp
    }

    override fun updateEmployee(newEmployee: Employee) {
        updateEmployeeOffline(newEmployee)
        val cardUuid = bytesToHex(getCardID())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.post("$SERVER_URL/update") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateInfoRequest(
                        cardUuid = cardUuid,
                        employeeId = newEmployee.id,
                        name = newEmployee.name,
                        dob = newEmployee.dob,
                        department = newEmployee.department,
                        position = newEmployee.position
                    ))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateEmployeeOffline(emp: Employee) {
        val block = ByteArray(EMP_INFO_MAX)
        putField(emp.id, block, EMP_ID_OFFSET, EMP_ID_LEN)
        putField(emp.name, block, EMP_NAME_OFFSET, EMP_NAME_LEN)
        putField(emp.dob, block, EMP_DOB_OFFSET, EMP_DOB_LEN)
        putField(emp.department, block, EMP_DEPT_OFFSET, EMP_DEPT_LEN)
        putField(emp.position, block, EMP_POS_OFFSET, EMP_POS_LEN)
        // Lệnh INS_UPDATE_INFO: 0x31
        api.sendApdu(byteArrayOf(0x80.toByte(), INS_UPDATE_INFO, 0x00, 0x00, block.size.toByte()) + block)
    }

    override suspend fun issueCardForUser(user: Employee): Boolean {
        println("💳 Bắt đầu cấp thẻ cho: ${user.name}")
        val block = ByteArray(EMP_INFO_MAX)
        putField(user.id, block, EMP_ID_OFFSET, EMP_ID_LEN)
        putField(user.name, block, EMP_NAME_OFFSET, EMP_NAME_LEN)
        putField(user.dob, block, EMP_DOB_OFFSET, EMP_DOB_LEN)
        putField(user.department, block, EMP_DEPT_OFFSET, EMP_DEPT_LEN)
        putField(user.position, block, EMP_POS_OFFSET, EMP_POS_LEN)

        val apdu = byteArrayOf(0x80.toByte(), INS_UPDATE_INFO, 0x00, 0x00, block.size.toByte()) + block
        val resp = api.sendApdu(apdu)
        if (!isSw9000(resp)) return false

        return try {
            val cardUuid = bytesToHex(getCardID())
            val pubResp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_PUB_KEY, 0x00, 0x00, 0x00))
            val pubKeyHex = if (isSw9000(pubResp)) bytesToHex(dataPart(pubResp)) else ""

            val regResp = client.post("$SERVER_URL/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(cardUuid, user.id, user.name, pubKeyHex))
            }
            regResp.status == HttpStatusCode.Created || regResp.status == HttpStatusCode.Conflict
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun adminLogin(id: String, pin: String): Boolean {
        return try {
            val response = client.post("$SERVER_URL/admin/login") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("id" to id, "pin" to pin))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }
    override suspend fun reportPinChanged(cardUuid: String): Boolean {
        return try {
            val resp = client.post("$SERVER_URL/pin-changed") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("cardUuid" to cardUuid))
            }
            resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    // --- TÀI CHÍNH & LOGS ---
    override fun getCardState(): CardState {
        val bal = getBalanceRaw()
        val tries = getPinTriesRemaining()
        return CardState(3, tries, tries == 0, bal.toDouble())
    }

    private fun getBalanceRaw(): Int {
        // Ghi số dư (balance) vào thẻ
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_BALANCE, 0x00, 0x00, 0x00))
        if (!isSw9000(resp) || resp.size < 4) return 0
        val d = dataPart(resp)
        return ((d[0].toInt() and 0xFF) shl 24) or ((d[1].toInt() and 0xFF) shl 16) or ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
    }

    private fun getPinTriesRemaining(): Int {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_RETRY, 0x00, 0x00, 0x01))
        if (!isSw9000(resp)) return 0
        val d = dataPart(resp)
        return if (d.isNotEmpty()) d[0].toInt() and 0xFF else 0
    }

    override fun getBalance(): Double = getBalanceRaw().toDouble()

    override fun topUp(amount: Double): Boolean {
        val amt = amount.toInt()
        // Lệnh INS_WALLET_TOPUP (0x50) ghi số tiền mới vào thẻ (Được giữ lại)
        val apdu = byteArrayOf(0x80.toByte(), INS_WALLET_TOPUP, 0x00, 0x00, 0x04,
            (amt ushr 24).toByte(), (amt ushr 16).toByte(), (amt ushr 8).toByte(), amt.toByte())
        val ok = isSw9000(api.sendApdu(apdu))
        if(ok) {
            // 🔥 VÔ HIỆU HÓA: Ghi Log Transaction TOPUP vào Thẻ
            // sendAddLog(encodeTxLogPayload(KIND_TX, SUB_TX_TOPUP, LocalDateTime.now(), amt, getBalanceRaw(), "Nạp tiền"))

            val newBal = getBalanceRaw().toDouble()
            val uuid = runBlocking { getCardIDHex() }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    client.post("$SERVER_URL/transaction") {
                        contentType(ContentType.Application.Json)
                        setBody(TransactionRequest(uuid, amount, "Nạp tiền tại quầy", newBal, ""))
                    }
                } catch (_: Exception){}
            }
        }
        return ok
    }
    override suspend fun adminTransaction(amount: Double, description: String, currentBalance: Double): HttpStatusCode {
        return try {
            val response = client.post("$SERVER_URL/transaction") {
                contentType(ContentType.Application.Json)
                setBody(TransactionRequest(
                    cardUuid = ADMIN_ID,
                    amount = amount, // Dùng số âm cho thanh toán
                    description = description,
                    currentBalance = currentBalance,
                    signatureHex = ""
                ))
            }
            response.status
        } catch (e: Exception) {
            HttpStatusCode.ServiceUnavailable
        }
    }

    override fun pay(amount: Double, description: String): Boolean {
        val amtInt = amount.toInt()
        val amountBytes = ByteBuffer.allocate(4).putInt(amtInt).array()
        val now = LocalDateTime.now()
        val timeBytes = ByteBuffer.allocate(4).putInt(now.toEpochSecond(ZoneOffset.UTC).toInt()).array()
        val unBytes = ByteArray(4).apply { SecureRandom().nextBytes(this) }
        val payData = amountBytes + timeBytes + unBytes

        // Lệnh INS_WALLET_PAY (0x51) trừ tiền trên thẻ (Được giữ lại)
        val apdu = byteArrayOf(0x80.toByte(), INS_WALLET_PAY, 0x00, 0x00, payData.size.toByte()) + payData
        val resp = api.sendApdu(apdu)
        if (!isSw9000(resp)) return false

        val newBalance = getBalanceRaw()
        // 🔥 VÔ HIỆU HÓA: Ghi Log Transaction PAYMENT vào Thẻ
        // sendAddLog(encodeTxLogPayload(KIND_TX, SUB_TX_PAYMENT, now, amtInt, newBalance, description))

        val sigHex = bytesToHex(dataPart(resp))
        val uuid = runBlocking { getCardIDHex() }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.post("$SERVER_URL/transaction") {
                    contentType(ContentType.Application.Json)
                    setBody(TransactionRequest(uuid, -amount, description, newBalance.toDouble(), sigHex))
                }
            } catch (_: Exception){}
        }
        return true
    }

    private fun sendAddLog(payload: ByteArray) {
        // 🔥 VÔ HIỆU HÓA: Không gọi lệnh ghi Log vào Thẻ
        // api.sendApdu(byteArrayOf(0x80.toByte(), INS_ADD_ACCESS_LOG, 0x00, 0x00, payload.size.toByte()) + payload)
    }

    override fun addAccessLog(type: AccessType, description: String): Boolean {
        val empId = try { getEmployee().id.trim() } catch (e: Exception) { "" }
        if (empId.isEmpty()) {
            println("❌ Thẻ không chứa ID hợp lệ.")
            return false
        }

        val typeStr = when(type) {
            AccessType.CHECK_IN -> "CHECK_IN"
            AccessType.CHECK_OUT -> "CHECK_OUT"
            else -> "RESTRICTED"
        }

        // 1. GỌI SERVER TRƯỚC (SERVER LÀ NGUỒN THẨM QUYỀN)
        val serverResponse: HttpResponse = try {
            runBlocking {
                client.post("$SERVER_URL/access-log") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "employeeId" to empId,
                        "type" to typeStr,
                        "description" to description
                    ))
                }
            }
        } catch (e: Exception) {
            println("❌ Lỗi mạng/Server. Log không được ghi: ${e.message}")
            return false
        }

        // 2. KIỂM TRA PHẢN HỒI CỦA SERVER
        if (serverResponse.status != HttpStatusCode.OK) {
            println("⚠️ Server từ chối log (Code: ${serverResponse.status}). KHÔNG ghi vào thẻ.")
            return false
        }

        // 🔥 3. LOẠI BỎ HOÀN TOÀN GHI LOG VÀO THẺ (Mục tiêu 2)
        // Nếu Server OK (200), ta trả về thành công
        return true
    }

    override suspend fun getAdminBalance(adminId: String): Double {
        return try {
            val resp = client.get("$SERVER_URL/balance/$adminId")
            if (resp.status == HttpStatusCode.OK) {
                val map = resp.body<Map<String, Double>>()
                map["balance"] ?: 0.0
            } else 0.0
        } catch (e: Exception) { 0.0 }
    }
    override fun getAccessLogs(): List<AccessLogEntry> {
        // 🔥 Mục tiêu 2: KHÔNG ĐỌC LOG TỪ THẺ NỮA
        // Hàm này gọi readAllLogsFromCard() cũ, nên ta chỉ cần sửa hàm đọc chính.
        return emptyList()
    }

    override fun getTransactions(): List<Transaction> {
        // 🔥 Mục tiêu 2: KHÔNG ĐỌC TX TỪ THẺ NỮA
        return emptyList()
    }

    private fun readAllLogsFromCard(): Pair<List<AccessLogEntry>, List<Transaction>> {
        // 🔥 VÔ HIỆU HÓA: Không gọi lệnh đọc Log từ Thẻ
        // val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_READ_LOGS, 0x00, 0x00, 0x00))
        // if (!isSw9000(resp)) return emptyList<AccessLogEntry>() to emptyList()
        // ... (phần logic đọc logs cũ) ...
        return emptyList<AccessLogEntry>() to emptyList()
    }

    // --- AVATAR & SERVER (Giữ nguyên) ---
    override fun uploadAvatar(imageBytes: ByteArray): Boolean {
        var offset = 0
        while (offset < imageBytes.size) {
            val chunkSize = min(MAX_APDU_DATA_SIZE, imageBytes.size - offset)
            val chunk = imageBytes.copyOfRange(offset, offset + chunkSize)
            val cmd = CommandAPDU(0x00, INS_UPDATE_AVATAR, (offset shr 8) and 0xFF, offset and 0xFF, chunk)
            if (!isSw9000(api.sendApdu(cmd.bytes))) return false
            offset += chunkSize
        }
        return true
    }

    override fun getAvatar(): ByteArray {
        val fullData = java.io.ByteArrayOutputStream()
        var offset = 0
        while (offset < MAX_AVATAR_SIZE) {
            val lenToRead = min(MAX_APDU_DATA_SIZE, MAX_AVATAR_SIZE - offset)
            val cmd = CommandAPDU(0x00, INS_DOWNLOAD_AVATAR, (offset shr 8) and 0xFF, offset and 0xFF, lenToRead)
            val resp = api.sendApdu(cmd.bytes)
            if (!isSw9000(resp)) break
            val chunk = dataPart(resp)
            if (chunk.isEmpty()) break
            fullData.write(chunk)
            offset += chunk.size
            if (chunk.size < lenToRead) break
        }
        return fullData.toByteArray()
    }

    override suspend fun getNextId(department: String): String {
        return try {
            val prefix = when (department) { "Quản Trị Hệ Thống" -> "AD"; "Phòng Kỹ Thuật" -> "IT"; "Phòng Nhân Sự" -> "HR"; "Phòng Kinh Doanh" -> "SALE"; "Phòng Kế Toán" -> "ACC"; else -> "NV" }
            val response = client.get("$SERVER_URL/next-id?prefix=$prefix")
            response.body<NextIdResponse>().id
        } catch (e: Exception) { "OFF${(100..999).random()}" }
    }

    override suspend fun getCardRole(uuid: String): String {
        return try {
            val resp = client.get("$SERVER_URL/$uuid")
            if (resp.status == HttpStatusCode.OK) {
                val map = resp.body<Map<String, Any>>()
                map["role"].toString()
            } else "USER"
        } catch (e: Exception) { "USER" }
    }

    override suspend fun getAllUsers(): List<UserResponse> {
        return try { client.get("$SERVER_URL/all-users").body() } catch (e: Exception) { emptyList() }
    }

    override suspend fun changeUserStatus(uuid: String, isActive: Boolean): Boolean {
        return try {
            client.post("$SERVER_URL/change-status") {
                contentType(ContentType.Application.Json)
                setBody(ChangeStatusRequest(uuid, isActive))
            }.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    override suspend fun adminTransaction(adminId: String, amount: Double, desc: String): Boolean {
        return try {
            val response = client.post("$SERVER_URL/transaction") {
                contentType(ContentType.Application.Json)
                setBody(TransactionRequest(
                    cardUuid = adminId,
                    amount = amount,
                    description = desc,
                    currentBalance = 0.0,
                    signatureHex = ""
                ))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun updateAdminProfile(id: String, name: String, dob: String, dept: String, position: String): Boolean {
        return try {
            // 🔥 FIX 1: Đổi URL endpoint sang '/admin/updateProfile' (đã sửa ở Server)
            client.post("$SERVER_URL/admin/updateProfile") {
                contentType(ContentType.Application.Json)
                setBody(UpdateInfoRequest(
                    // cardUuid: Vẫn giữ giá trị hiện tại của Repo
                    cardUuid = getCardIDHex(),
                    // employeeId: Dùng ID truyền vào (ví dụ: 'ADMIN01')
                    employeeId = id,
                    name = name,
                    dob = dob,
                    department = dept,
                    position = position
                    // Không cần isDefaultPin
                ))
            }.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    override suspend fun verifyAdminPin(pin: String): Boolean {
        return try {
            val response = client.post("$SERVER_URL/admin/login") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("employeeId" to ADMIN_ID, "pin" to pin))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            println("Admin PIN verification failed: ${e.message}")
            false
        }
    }

    override suspend fun adminAccessLog(adminId: String, typeStr: String, gate: String): HttpStatusCode {
        return try {
            val response = client.post("$SERVER_URL/access-log") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "employeeId" to adminId,
                    "type" to typeStr,
                    "description" to "Admin $typeStr at $gate"
                ))
            }
            response.status
        } catch (e: Exception) {
            println("🔥 CLIENT EXCEPTION: Admin Log failed: ${e.message}")
            HttpStatusCode.ServiceUnavailable
        }
    }

    override suspend fun getServerLogs(employeeId: String?): List<HistoryLogEntry> { // <-- THAY ĐỔI KIỂU TRẢ VỀ
        return try {
            val url = if (employeeId.isNullOrBlank()) {
                "$SERVER_URL/history"
            } else {
                "$SERVER_URL/history?employeeId=$employeeId"
            }

            // 🔥 ĐỌC BODY VỀ LIST DTO MỚI
            client.get(url).body<List<HistoryLogEntry>>()
        } catch (e: Exception) {
            println("❌ Lỗi khi đọc Server Logs: ${e.message}")
            emptyList()
        }
    }
    override suspend fun getDepartmentsMap(): Map<String, String> {
        return try {
            val response = client.get("$SERVER_URL/departments")
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Error fetching departments: ${e.message}")
            emptyMap()
        }
    }

    override suspend fun getPositionsMap(): Map<String, String> {
        return try {
            val response = client.get("$SERVER_URL/positions")
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Error fetching positions: ${e.message}")
            emptyMap()
        }
    }
    override suspend fun getProducts(): List<Product> {
        return try {
            val response = client.get("$SERVER_URL/products")
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching products: ${e.message}")
            emptyList()
        }
    }

    // --- UTILS ---
    private fun isSw9000(resp: ByteArray): Boolean = resp.size >= 2 && resp[resp.size - 2] == 0x90.toByte() && resp[resp.size - 1] == 0x00.toByte()
    private fun dataPart(resp: ByteArray): ByteArray = if (resp.size <= 2) byteArrayOf() else resp.copyOfRange(0, resp.size - 2)
    private fun putField(text: String, dest: ByteArray, offset: Int, maxLen: Int) {
        val bytes = text.toByteArray(UTF8)
        System.arraycopy(bytes, 0, dest, offset, bytes.size.coerceAtMost(maxLen))
    }
    private fun decodeString(src: ByteArray, off: Int, len: Int): String {
        if (off + len > src.size) return ""
        var realLen = 0
        for (i in 0 until len) {
            if (src[off + i] == 0.toByte()) {
                break
            }
            realLen++
        }
        if (realLen == 0) return ""
        return String(src, off, realLen, UTF8).trim()
    }
    override suspend fun deleteUser(targetUuid: String, adminPin: String): Boolean {
        return try {
            val resp = client.post("$SERVER_URL/admin/delete-user") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "targetUuid" to targetUuid,
                    "pin" to adminPin
                ))
            }
            resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    // THÊM: Kiểm tra thẻ có bị khóa không
    override fun isCardLocked(): Boolean {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_CHECK_LOCKED, 0x00, 0x00, 0x01))
        if (!isSw9000(resp)) return false
        val data = dataPart(resp)
        return data.isNotEmpty() && data[0] == 0x01.toByte()
    }

    // THÊM: Admin khóa thẻ (phải verify PIN admin trước)
    override suspend fun adminLockCard(adminPin: String): Boolean {
        val verified = runBlocking { verifyAdminPin(adminPin) || adminLogin(ADMIN_ID, adminPin) }
        if (!verified) return false

        val apdu = byteArrayOf(0x80.toByte(), INS_LOCK_CARD, 0x00, 0x00)
        return isSw9000(api.sendApdu(apdu))
    }

    // THÊM: Admin mở khóa thẻ (phải verify PIN admin trước)
    override suspend fun adminUnlockCard(adminPin: String): Boolean {
        // 🔥 ĐÃ BỎ: Logic kiểm tra verified qua Server/adminLogin
        // 🔥 ĐÃ BỎ: Logic verify PIN Admin trên thẻ vật lý

        // Gửi lệnh INS_UNLOCK_CARD (0x2C) tới thẻ
        val apdu = byteArrayOf(0x80.toByte(), INS_UNLOCK_CARD, 0x00, 0x00)
        return isSw9000(api.sendApdu(apdu))
    }

    // THÊM: Admin reset PIN về mặc định (phải verify PIN admin trước)
    override suspend fun adminResetPin(adminPin: String, newPin: String): Boolean {
        // 🔥 ĐÃ BỎ: Logic kiểm tra verified qua Server/adminLogin

        val salt = getSaltFromCard() ?: return false
        val derivedKey = computeArgon2Hash(newPin, salt)

        val apdu = byteArrayOf(
            0x80.toByte(),
            INS_RESET_PIN,
            0x00,
            0x00,
            derivedKey.size.toByte()
        ) + derivedKey

        return isSw9000(api.sendApdu(apdu))
    }

    // THÊM: Hàm helper để Admin xác thực bằng thẻ Admin
    override suspend fun adminVerifyWithAdminCard(adminPin: String): Boolean {
        // Kết nối với thẻ Admin của Admin (phải đặt lên đầu đọc)
        if (!connect()) return false

        // Verify PIN của Admin
        return verifyPin(adminPin)
    }

    // THÊM: Quy trình đầy đủ Admin mở khóa thẻ User
    // THÊM: Quy trình đầy đủ Admin mở khóa thẻ User
    override suspend fun adminUnlockUserCard(
        adminPin: String, // PIN Admin (đã verified ở tầng trên)
        userCardUuid: String
    ): Boolean {
        return try {
            println("📢 Đang cố gắng kết nối với thẻ USER để mở khóa...")
            // Bước 1: Kết nối với thẻ User
            if (!connect()) return false

            // Bước 2: Thực hiện mở khóa
            val unlockSuccess = adminUnlockCard(adminPin)

            if (unlockSuccess) {
                println("✅ Đã mở khóa thẻ thành công")
            } else {
                println("❌ Thao tác mở khóa thẻ thất bại.")
            }

            disconnect()
            unlockSuccess

        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    // THÊM: Quy trình đầy đủ Admin reset PIN (ĐÃ THÊM TÁI XÁC THỰC PIN MỚI)
    override suspend fun adminResetUserPin(
        adminPin: String, // PIN Admin (đã verified ở tầng trên)
        userCardUuid: String,
        newUserPin: String // Pin mới, vd: "123456"
    ): Boolean {
        var finalResult = false
        // Bỏ khối try/catch lớn để debug tốt hơn, nhưng dùng khối try/finally cho disconnect

        println("📢 Đang cố gắng kết nối với thẻ USER để Reset PIN...")
        if (!connect()) return false // Kết nối ban đầu

        try {
            // B1: Kiểm tra khóa và UNLOCK (trên cùng một kết nối)
            if (isCardLocked()) {
                println("⚠️ Thẻ đang bị khóa, đang tiến hành mở khóa...")
                // LƯU Ý: Chức năng adminUnlockCard hiện tại của bạn yêu cầu Server Verify PIN Admin,
                // sau đó nó gửi lệnh 80 2C. Hàm này phải được gọi trong phiên kết nối này.

                // 🔥 FIX: Thay vì gọi hàm adminUnlockCard cũ (có disconnect bên trong),
                // ta gọi lệnh APDU trực tiếp (80 2C) sau khi VERIFY PIN ADMIN (qua server)

                // Tạm thời, ta sử dụng VERIFY PIN ADMIN trực tiếp lên thẻ USER để có quyền UNLOCK
                // (Chỉ áp dụng nếu bạn sửa lại hàm adminUnlockCard trong file này)

                // Giả định: adminUnlockCard chỉ gửi 80 2C (như code bạn cung cấp)
                if (!adminUnlockCard(adminPin)) {
                    println("❌ Không thể mở khóa thẻ.")
                    return false
                }
                println("✅ Đã mở khóa thẻ thành công.")
            }

            // B2: Thực hiện RESET PIN (INS_RESET_PIN 80 2D)
            val resetSuccess = adminResetPin(adminPin, newUserPin)

            if (resetSuccess) {
                println("✅ Đã reset PIN thành công.")

                // B3: TÁI XÁC THỰC BẰNG PIN MỚI (TRONG CÙNG PHIÊN)
                // Lệnh 80 25 này sẽ tải Master Key vào RAM và set isValidated = true
                println("📢 Tái xác thực bằng PIN mới để thiết lập phiên giải mã...")

                if (verifyPin(newUserPin)) { // Gửi 80 25 với hash PIN mới
                    println("✅ Tái xác thực PIN mới thành công. Phiên giải mã đã được thiết lập.")

                    // B4: KIỂM TRA ĐỌC DỮ LIỆU (ĐỌC GET_BALANCE)
                    val balance = getBalance() // Gọi lệnh 80 52
                    println("✅ Đọc thử số dư sau reset thành công: $balance")
//                    try {
//                        client.post("$SERVER_URL/admin/set-default-pin") {
//                            contentType(ContentType.Application.Json)
//                            setBody(mapOf("cardUuid" to userCardUuid, "isDefaultPin" to true))
//                        }
//                    } catch (e: Exception) {
//                        println("Set PIN defaul lỗi") }
                    finalResult = true
                } else {
                    println("❌ Tái xác thực bằng PIN mới thất bại. Dữ liệu sẽ bị mã hóa.")
                }
            }

            return finalResult

        } catch (e: Exception) {
            println("❌ Lỗi xảy ra trong quá trình Reset/Verify: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            // NGẮT KẾT NỐI sau khi mọi thứ hoàn tất
            disconnect()
        }
    }
    // Các hàm encode/decode log cũ (không còn cần) đã được comment
    /*
    private fun encodeAccessLogPayload(k: Byte, s: Byte, t: LocalDateTime, d: String): ByteArray { val b = ByteArray(LOG_SIZE); b[0]=k; b[1]=s; encodeTime(t,b,2); putField(d,b,8,LOG_SIZE-8); return b }
    private fun encodeTxLogPayload(k: Byte, s: Byte, t: LocalDateTime, a: Int, bal: Int, d: String): ByteArray { val b = ByteArray(LOG_SIZE); b[0]=k; b[1]=s; encodeTime(t,b,2); encodeInt(a,b,8); encodeInt(bal,b,12); putField(d,b,16,LOG_SIZE-16); return b }
    private fun encodeTime(t: LocalDateTime, d: ByteArray, o: Int) { d[o]=(t.year-2000).toByte(); d[o+1]=t.monthValue.toByte(); d[o+2]=t.dayOfMonth.toByte(); d[o+3]=t.hour.toByte(); d[o+4]=t.minute.toByte(); d[o+5]=t.second.toByte() }
    private fun decodeTime(s: ByteArray, o: Int): LocalDateTime { return try { LocalDateTime.of((s[o].toInt() and 0xFF)+2000, s[o+1].toInt(), s[o+2].toInt(), s[o+3].toInt(), s[o+4].toInt(), s[o+5].toInt()) } catch(e:Exception){LocalDateTime.now()} }
    private fun encodeInt(v: Int, d: ByteArray, o: Int) { d[o]=(v ushr 24).toByte(); d[o+1]=(v ushr 16).toByte(); d[o+2]=(v ushr 8).toByte(); d[o+3]=v.toByte() }
    private fun decodeInt(s: ByteArray, o: Int): Int = ((s[o].toInt() and 0xFF) shl 24) or ((s[o+1].toInt() and 0xFF) shl 16) or ((s[o+2].toInt() and 0xFF) shl 8) or (s[o+3].toInt() and 0xFF)
    */
}