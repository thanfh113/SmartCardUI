package org.example.project.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

object ImageUtils {

    // Kích thước tối đa cho phép trên thẻ (8KB)
    private const val MAX_CARD_IMAGE_SIZE = 8192

    // Kích thước pixel ảnh hiển thị
    private const val TARGET_WIDTH = 128
    private const val TARGET_HEIGHT = 128

    fun processImageForCard(path: String): ByteArray? {
        return try {
            val file = File(path)
            if (!file.exists()) return null

            val originalImage = ImageIO.read(file) ?: return null

            // 1. Resize
            val resizedImage = BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = resizedImage.createGraphics()
            g.drawImage(originalImage.getScaledInstance(TARGET_WIDTH, TARGET_HEIGHT, Image.SCALE_SMOOTH), 0, 0, null)
            g.dispose()

            // 2. Nén JPG
            val baos = ByteArrayOutputStream()
            ImageIO.write(resizedImage, "jpg", baos)
            var bytes = baos.toByteArray()

            // 3. Kiểm tra dung lượng
            if (bytes.size > MAX_CARD_IMAGE_SIZE) {
                println("⚠️ Ảnh gốc quá lớn (${bytes.size}).")
                return null
            }

            // 4. ✅ [QUAN TRỌNG] Padding cho đủ 16 byte (AES Block Size)
            val remainder = bytes.size % 16
            if (remainder != 0) {
                val padding = 16 - remainder
                // Tạo mảng mới lớn hơn và copy dữ liệu sang + padding 0
                bytes = bytes.copyOf(bytes.size + padding)
            }

            // Kiểm tra lại lần cuối sau khi padding
            if (bytes.size > MAX_CARD_IMAGE_SIZE) {
                println("⚠️ Sau khi padding ảnh quá lớn (${bytes.size}).")
                return null
            }

            println("✅ Ảnh OK (Đã Padding AES): ${bytes.size} bytes")
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun bytesToBitmap(data: ByteArray): ImageBitmap? {
        return try {
            if (data.isEmpty()) return null
            // ByteArrayInputStream của Java IO thông minh, nó đọc header JPG
            // và tự động bỏ qua các byte 0 padding ở cuối -> Không cần unpad thủ công
            val bais = ByteArrayInputStream(data)
            val bufferedImage = ImageIO.read(bais)
            bufferedImage?.toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}