package org.example.project.api

import javax.smartcardio.*

class JcideSmartCardApi : SmartCardApi {

    private var terminal: CardTerminal? = null
    private var card: Card? = null
    private var channel: CardChannel? = null

    // AID của Applet (Phải khớp với file cấu hình JCIDE)
    private val APPLET_AID = byteArrayOf(
        0x11, 0x22, 0x33, 0x44, 0x00, 0x00
    )

    override fun connect(): Boolean {
        return try {
            val factory = TerminalFactory.getDefault()
            val terminals = factory.terminals().list()

            // Ưu tiên tìm JCIDE Virtual Reader, nếu không thì lấy cái đầu tiên
            terminal = terminals.find { it.name.contains("JCIDE", ignoreCase = true) }
                ?: terminals.firstOrNull()

            if (terminal == null) {
                println("❌ Không tìm thấy đầu đọc thẻ nào!")
                return false
            }

            println("➡️ Kết nối tới: ${terminal!!.name}")

            // Connect protocol T=1 hoặc T=0 (*)
            card = terminal!!.connect("*")
            channel = card!!.basicChannel
            println("✅ Đã kết nối vật lý với thẻ")

            // --- SELECT APPLET ---
            val selectCmd = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00) +
                    APPLET_AID.size.toByte() + APPLET_AID

            val resp = channel!!.transmit(CommandAPDU(selectCmd))
            val bytes = resp.bytes

            println("⬅️ SELECT RESP: ${HexUtils.bin2hex(bytes)}")

            // Check 90 00
            if (bytes.size >= 2 && bytes[bytes.size - 2] == 0x90.toByte() && bytes.last() == 0x00.toByte()) {
                println("✅ Đã Select Applet thành công")
                return true
            } else {
                println("❌ Select Applet thất bại (SW khác 9000)")
                return false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override fun disconnect() {
        try {
            card?.disconnect(false)
            println("🔌 Đã ngắt kết nối")
        } catch (_: Exception) {}
    }

    override fun sendApdu(apdu: ByteArray): ByteArray {
        return try {
            if (channel == null) return byteArrayOf()

            // Debug log
            // println("➡️ SEND: ${HexUtils.bin2hex(apdu)}")

            val resp = channel!!.transmit(CommandAPDU(apdu))
            val bytes = resp.bytes

            // Debug log
            // println("⬅️ RESP: ${HexUtils.bin2hex(bytes)}")

            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            byteArrayOf()
        }
    }
}

private object HexUtils {
    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    fun bin2hex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }
}