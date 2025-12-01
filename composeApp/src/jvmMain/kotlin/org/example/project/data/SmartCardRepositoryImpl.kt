package org.example.project.data

import org.example.project.api.JcideSmartCardApi
import org.example.project.model.*
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.time.LocalDateTime
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.smartcardio.CommandAPDU
import kotlin.math.min

class SmartCardRepositoryImpl(
    private val api: JcideSmartCardApi = JcideSmartCardApi()
) : CardRepository {

    companion object {
        // --- INS CODES ---
        private const val INS_CHANGE_PIN: Byte     = 0x21
        private const val INS_GET_RETRY: Byte      = 0x22
        private const val INS_VERIFY_PIN_ENC: Byte = 0x25
        private const val INS_AUTHENTICATE: Byte   = 0x26
        private const val INS_GET_PUB_KEY: Byte    = 0x27

        private const val INS_READ_INFO: Byte      = 0x30
        private const val INS_UPDATE_INFO: Byte    = 0x31
        private const val INS_ADD_ACCESS_LOG: Byte = 0x40
        private const val INS_READ_LOGS: Byte      = 0x41
        private const val INS_WALLET_TOPUP: Byte   = 0x50
        private const val INS_WALLET_PAY: Byte     = 0x51
        private const val INS_GET_BALANCE: Byte    = 0x52

        // ✅ INS AVATAR (Giao thức mới)
        private const val INS_UPDATE_AVATAR: Int   = 0x10 // Upload
        private const val INS_DOWNLOAD_AVATAR: Int = 0x11 // Download

        // ✅ QUAN TRỌNG: Kích thước chunk phải chia hết cho 16 để mã hóa AES
        // 240 / 16 = 15 (Chẵn block) -> OK
        private const val MAX_APDU_DATA_SIZE = 240

        private const val MAX_AVATAR_SIZE = 8192

        // Cấu hình AES Key (Phải khớp với thẻ)
        private val APP_AES_KEY = byteArrayOf(
            0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
            0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F
        )

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

        private const val SW_EMP_ID_LOCKED: Int = 0x6985
        private const val LOG_SIZE = 32

        // Log Types
        private const val KIND_ACCESS: Byte = 1
        private const val KIND_TX: Byte = 2
        private const val SUB_ACCESS_IN: Byte = 1
        private const val SUB_ACCESS_OUT: Byte = 2
        private const val SUB_ACCESS_RESTRICT: Byte = 3
        private const val SUB_TX_TOPUP: Byte = 1
        private const val SUB_TX_PAYMENT: Byte = 2

        private val UTF8: Charset = Charsets.UTF_8
    }

    override fun connect(): Boolean = api.connect()
    override fun disconnect() = api.disconnect()

    // --- 🟢 UPLOAD AVATAR (CÓ MÃ HÓA AES) ---
    override fun uploadAvatar(imageBytes: ByteArray): Boolean {
        println("🖼️ Bắt đầu Upload Avatar Encrypted (${imageBytes.size} bytes)...")
        var offset = 0

        while (offset < imageBytes.size) {
            // Cắt chunk (đảm bảo chia hết cho 16 nhờ logic padding ở ImageUtils và MAX_APDU_DATA_SIZE=240)
            val chunkSize = min(MAX_APDU_DATA_SIZE, imageBytes.size - offset)
            val chunk = imageBytes.copyOfRange(offset, offset + chunkSize)

            // 🔐 MÃ HÓA CHUNK TRƯỚC KHI GỬI
            val encryptedChunk = encryptAes(chunk)

            // Gửi lệnh INS 0x10
            val cmd = CommandAPDU(
                0x00, INS_UPDATE_AVATAR,
                (offset shr 8) and 0xFF,
                offset and 0xFF,
                encryptedChunk
            )

            val resp = api.sendApdu(cmd.bytes)
            if (!isSw9000(resp)) {
                println("❌ Upload lỗi tại offset $offset SW=${Integer.toHexString(getSw(resp))}")
                return false
            }
            offset += chunkSize
        }
        println("✅ Upload thành công!")
        return true
    }

    // --- 🟢 DOWNLOAD AVATAR (CÓ GIẢI MÃ AES) ---
    override fun getAvatar(): ByteArray {
        println("🖼️ Bắt đầu Download Avatar Encrypted...")
        val fullData = java.io.ByteArrayOutputStream()
        var offset = 0

        while (offset < MAX_AVATAR_SIZE) {
            val lenToRead = min(MAX_APDU_DATA_SIZE, MAX_AVATAR_SIZE - offset)

            // Gửi lệnh INS 0x11
            val cmd = CommandAPDU(
                0x00, INS_DOWNLOAD_AVATAR,
                (offset shr 8) and 0xFF,
                offset and 0xFF,
                lenToRead
            )

            val resp = api.sendApdu(cmd.bytes)
            if (!isSw9000(resp)) {
                println("⚠️ Dừng download (SW khác 9000) tại offset $offset")
                break
            }

            val encryptedChunk = dataPart(resp)
            if (encryptedChunk.isEmpty()) break

            // 🔓 GIẢI MÃ CHUNK NHẬN ĐƯỢC
            // (Thẻ gửi về dữ liệu đã mã hóa, client phải giải mã để hiển thị)
            val plainChunk = decryptAes(encryptedChunk)

            fullData.write(plainChunk)
            offset += encryptedChunk.size // Tăng offset theo kích thước thực nhận

            if (encryptedChunk.size < lenToRead) break
        }

        val result = fullData.toByteArray()
        println("✅ Đã tải và giải mã: ${result.size} bytes")
        return result
    }

    // --- CÁC HÀM KHÁC (GIỮ NGUYÊN) ---

    override fun authenticateCard(): Boolean {
        try {
            val pubKeyResp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_PUB_KEY, 0x00, 0x00, 0x00))
            if (!isSw9000(pubKeyResp)) return false
            val keyData = dataPart(pubKeyResp)
            val modulusLen = 128
            if (keyData.size < modulusLen + 1) return false
            val modulus = BigInteger(1, keyData.copyOfRange(0, modulusLen))
            val exponent = BigInteger(1, keyData.copyOfRange(modulusLen, keyData.size))
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))

            val challenge = ByteArray(16) { (Math.random() * 255).toInt().toByte() }
            val signApdu = byteArrayOf(0x80.toByte(), INS_AUTHENTICATE, 0x00, 0x00, challenge.size.toByte()) + challenge
            val signResp = api.sendApdu(signApdu)
            if (!isSw9000(signResp)) return false

            val signature = dataPart(signResp)
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey)
            verifier.update(challenge)
            return verifier.verify(signature)
        } catch (e: Exception) { return false }
    }

    // 🔐 Hàm mã hóa AES
    private fun encryptAes(data: ByteArray): ByteArray {
        return try {
            val keySpec = SecretKeySpec(APP_AES_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0) // Trả về rỗng nếu lỗi
        }
    }

    // 🔓 Hàm giải mã AES (Mới thêm vào)
    private fun decryptAes(data: ByteArray): ByteArray {
        return try {
            if (data.isEmpty()) return ByteArray(0)
            val keySpec = SecretKeySpec(APP_AES_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    private fun isSw9000(resp: ByteArray): Boolean =
        resp.size >= 2 && resp[resp.size - 2] == 0x90.toByte() && resp[resp.size - 1] == 0x00.toByte()

    private fun getSw(resp: ByteArray): Int {
        if (resp.size < 2) return -1
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp[resp.size - 1].toInt() and 0xFF
        return (sw1 shl 8) or sw2
    }

    private fun dataPart(resp: ByteArray): ByteArray {
        if (resp.size <= 2) return byteArrayOf()
        return resp.copyOfRange(0, resp.size - 2)
    }

    override fun getCardState(): CardState {
        val bal = getBalanceRaw()
        val tries = getPinTriesRemaining()
        return CardState(3, tries, tries == 0, bal.toDouble())
    }

    private fun getBalanceRaw(): Int {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_BALANCE, 0x00, 0x00, 0x00))
        if (!isSw9000(resp) || resp.size < 4) return 0
        val d = dataPart(resp)
        return ((d[0].toInt() and 0xFF) shl 24) or ((d[1].toInt() and 0xFF) shl 16) or
                ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
    }

    private fun getPinTriesRemaining(): Int {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_GET_RETRY, 0x00, 0x00, 0x00))
        val d = dataPart(resp)
        return if (d.isNotEmpty()) d[0].toInt() and 0xFF else 3
    }

    override fun verifyPin(input: String): Boolean {
        val dataBlock = ByteArray(16) { 0xFF.toByte() }
        val rawPin = input.toByteArray(UTF8)
        System.arraycopy(rawPin, 0, dataBlock, 0, rawPin.size.coerceAtMost(16))
        val encryptedPin = encryptAes(dataBlock)
        val apdu = byteArrayOf(0x80.toByte(), INS_VERIFY_PIN_ENC, 0x00, 0x00, encryptedPin.size.toByte()) + encryptedPin
        return isSw9000(api.sendApdu(apdu))
    }

    override fun changePin(oldPin: String, newPin: String): Boolean {
        val data = newPin.encodeToByteArray()
        val apdu = byteArrayOf(0x80.toByte(), INS_CHANGE_PIN, 0x00, 0x00, data.size.toByte()) + data
        return isSw9000(api.sendApdu(apdu))
    }

    override fun getEmployee(): Employee {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_READ_INFO, 0x00, 0x00, 0x00))
        if (!isSw9000(resp)) return defaultEmployee()
        val data = dataPart(resp)
        if (data.size < EMP_INFO_MAX) return defaultEmployee()
        val id = decodeString(data, EMP_ID_OFFSET, EMP_ID_LEN)
        val name = decodeString(data, EMP_NAME_OFFSET, EMP_NAME_LEN)
        if (id.isBlank() && name.isBlank()) return defaultEmployee()
        return Employee(id, name, decodeString(data, EMP_DOB_OFFSET, EMP_DOB_LEN),
            decodeString(data, EMP_DEPT_OFFSET, EMP_DEPT_LEN), decodeString(data, EMP_POS_OFFSET, EMP_POS_LEN))
    }

    private fun defaultEmployee() = Employee("NV001", "Nguyễn Văn A", "01/01/1995", "IT", "Developer")

    override fun updateEmployee(newEmployee: Employee) {
        val block = ByteArray(EMP_INFO_MAX)
        putField(newEmployee.id, block, EMP_ID_OFFSET, EMP_ID_LEN)
        putField(newEmployee.name, block, EMP_NAME_OFFSET, EMP_NAME_LEN)
        putField(newEmployee.dob, block, EMP_DOB_OFFSET, EMP_DOB_LEN)
        putField(newEmployee.department, block, EMP_DEPT_OFFSET, EMP_DEPT_LEN)
        putField(newEmployee.position, block, EMP_POS_OFFSET, EMP_POS_LEN)
        api.sendApdu(byteArrayOf(0x80.toByte(), INS_UPDATE_INFO, 0x00, 0x00, block.size.toByte()) + block)
    }

    override fun getBalance(): Double = getCardState().balance

    override fun topUp(amount: Double): Boolean {
        val amt = amount.toInt()
        val apdu = byteArrayOf(0x80.toByte(), INS_WALLET_TOPUP, 0x00, 0x00, 0x04,
            (amt ushr 24).toByte(), (amt ushr 16).toByte(), (amt ushr 8).toByte(), amt.toByte())
        val ok = isSw9000(api.sendApdu(apdu))
        if(ok) sendAddLog(encodeTxLogPayload(KIND_TX, SUB_TX_TOPUP, LocalDateTime.now(), amt, getBalanceRaw(), "Nạp tiền"))
        return ok
    }

    override fun pay(amount: Double, description: String): Boolean {
        val amt = amount.toInt()
        val apdu = byteArrayOf(0x80.toByte(), INS_WALLET_PAY, 0x00, 0x00, 0x04,
            (amt ushr 24).toByte(), (amt ushr 16).toByte(), (amt ushr 8).toByte(), amt.toByte())
        val ok = isSw9000(api.sendApdu(apdu))
        if(ok) sendAddLog(encodeTxLogPayload(KIND_TX, SUB_TX_PAYMENT, LocalDateTime.now(), amt, getBalanceRaw(), description))
        return ok
    }

    private fun sendAddLog(payload: ByteArray) {
        api.sendApdu(byteArrayOf(0x80.toByte(), INS_ADD_ACCESS_LOG, 0x00, 0x00, payload.size.toByte()) + payload)
    }

    override fun addAccessLog(type: AccessType, description: String) {
        val subtype = when (type) { AccessType.CHECK_IN -> SUB_ACCESS_IN; AccessType.CHECK_OUT -> SUB_ACCESS_OUT; else -> SUB_ACCESS_RESTRICT }
        sendAddLog(encodeAccessLogPayload(KIND_ACCESS, subtype, LocalDateTime.now(), description))
    }

    override fun getAccessLogs(): List<AccessLogEntry> { val (a, _) = readAllLogsFromCard(); return a }
    override fun getTransactions(): List<Transaction> { val (_, t) = readAllLogsFromCard(); return t }

    private fun readAllLogsFromCard(): Pair<List<AccessLogEntry>, List<Transaction>> {
        val resp = api.sendApdu(byteArrayOf(0x80.toByte(), INS_READ_LOGS, 0x00, 0x00, 0x00))
        if (!isSw9000(resp)) return emptyList<AccessLogEntry>() to emptyList()
        val data = dataPart(resp)
        val aList = mutableListOf<AccessLogEntry>(); val tList = mutableListOf<Transaction>()
        var offset = 0
        while (offset + LOG_SIZE <= data.size) {
            val rec = data.sliceArray(offset until offset + LOG_SIZE)
            offset += LOG_SIZE
            if (rec[0].toInt() == 0) continue
            val time = decodeTime(rec, 2)

            // Sử dụng Named Arguments để tránh lỗi nhầm thứ tự constructor
            if (rec[0] == KIND_ACCESS) {
                val type = when(rec[1]) {
                    SUB_ACCESS_IN -> AccessType.CHECK_IN
                    SUB_ACCESS_OUT -> AccessType.CHECK_OUT
                    else -> AccessType.RESTRICTED_AREA
                }
                // Giả định constructor là (time, type, desc) theo code bạn đưa
                aList.add(AccessLogEntry(time = time, accessType = type, description = decodeString(rec, 8, LOG_SIZE - 8)))
            } else if (rec[0] == KIND_TX) {
                val type = if(rec[1] == SUB_TX_TOPUP) TransactionType.TOP_UP else TransactionType.PAYMENT
                val amt = decodeInt(rec, 8).toDouble()
                val desc = decodeString(rec, 16, LOG_SIZE - 16)
                val bal = decodeInt(rec, 12).toDouble()

                tList.add(Transaction(time = time, type = type, amount = amt, description = desc, balanceAfter = bal))
            }
        }
        return aList.reversed() to tList.reversed()
    }

    private fun putField(text: String, dest: ByteArray, offset: Int, maxLen: Int) {
        val bytes = text.toByteArray(UTF8)
        System.arraycopy(bytes, 0, dest, offset, bytes.size.coerceAtMost(maxLen))
    }
    private fun decodeString(src: ByteArray, off: Int, len: Int): String {
        var real = len; for(i in 0 until len) if(src[off+i]==0.toByte()){real=i;break}; return if(real<=0)"" else String(src, off, real, UTF8)
    }
    private fun encodeAccessLogPayload(k: Byte, s: Byte, t: LocalDateTime, d: String): ByteArray { val b = ByteArray(LOG_SIZE); b[0]=k; b[1]=s; encodeTime(t,b,2); putField(d,b,8,LOG_SIZE-8); return b }
    private fun encodeTxLogPayload(k: Byte, s: Byte, t: LocalDateTime, a: Int, bal: Int, d: String): ByteArray { val b = ByteArray(LOG_SIZE); b[0]=k; b[1]=s; encodeTime(t,b,2); encodeInt(a,b,8); encodeInt(bal,b,12); putField(d,b,16,LOG_SIZE-16); return b }
    private fun encodeTime(t: LocalDateTime, d: ByteArray, o: Int) { d[o]=(t.year-2000).toByte(); d[o+1]=t.monthValue.toByte(); d[o+2]=t.dayOfMonth.toByte(); d[o+3]=t.hour.toByte(); d[o+4]=t.minute.toByte(); d[o+5]=t.second.toByte() }
    private fun decodeTime(s: ByteArray, o: Int): LocalDateTime { return try{ LocalDateTime.of((s[o].toInt() and 0xFF)+2000, s[o+1].toInt(), s[o+2].toInt(), s[o+3].toInt(), s[o+4].toInt(), s[o+5].toInt())}catch(e:Exception){LocalDateTime.now()} }
    private fun encodeInt(v: Int, d: ByteArray, o: Int) { d[o]=(v ushr 24).toByte(); d[o+1]=(v ushr 16).toByte(); d[o+2]=(v ushr 8).toByte(); d[o+3]=v.toByte() }
    private fun decodeInt(s: ByteArray, o: Int): Int = ((s[o].toInt() and 0xFF) shl 24) or ((s[o+1].toInt() and 0xFF) shl 16) or ((s[o+2].toInt() and 0xFF) shl 8) or (s[o+3].toInt() and 0xFF)
}