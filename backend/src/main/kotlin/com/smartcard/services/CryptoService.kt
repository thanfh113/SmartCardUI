package com.smartcard.services

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

class CryptoService {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    /**
     * Verify RSA signature from SmartCard
     * @param signature RSA signature bytes
     * @param employeeId 16-byte employee ID
     * @param amount 4-byte amount
     * @param timestamp 4-byte timestamp
     * @param uniqueNumber 4-byte unique number
     * @param publicKeyBytes RSA public key in card format: [Len Modulus][Modulus][Len Exponent][Exponent]
     * @return true if signature is valid
     */
    fun verifyPaymentTransaction(
        signature: ByteArray,
        employeeId: ByteArray,
        amount: Int,
        timestamp: Int,
        uniqueNumber: Int,
        publicKeyBytes: ByteArray
    ): Boolean {
        return try {
            val publicKey = parseCardPublicKey(publicKeyBytes)
            val message = buildTransactionMessage(employeeId, amount, timestamp, uniqueNumber)
            
            val verifier = Signature.getInstance("SHA256withRSA", "BC")
            verifier.initVerify(publicKey)
            verifier.update(message)
            verifier.verify(signature)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Parse RSA public key from SmartCard format
     * Format: [Len Modulus (2 bytes)][Modulus][Len Exponent (2 bytes)][Exponent]
     */
    fun parseCardPublicKey(keyBytes: ByteArray): PublicKey {
        val buffer = ByteBuffer.wrap(keyBytes)
        
        // Read modulus length (2 bytes, big-endian)
        val modulusLen = buffer.short.toInt() and 0xFFFF
        
        // Read modulus
        val modulusBytes = ByteArray(modulusLen)
        buffer.get(modulusBytes)
        val modulus = java.math.BigInteger(1, modulusBytes)
        
        // Read exponent length (2 bytes, big-endian)
        val exponentLen = buffer.short.toInt() and 0xFFFF
        
        // Read exponent
        val exponentBytes = ByteArray(exponentLen)
        buffer.get(exponentBytes)
        val exponent = java.math.BigInteger(1, exponentBytes)
        
        // Create RSA public key
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Build transaction message for signature verification
     * Format: [Employee ID (16 bytes)][Amount (4 bytes)][Timestamp (4 bytes)][Unique Number (4 bytes)]
     */
    private fun buildTransactionMessage(
        employeeId: ByteArray,
        amount: Int,
        timestamp: Int,
        uniqueNumber: Int
    ): ByteArray {
        require(employeeId.size == 16) { "Employee ID must be 16 bytes" }
        
        return ByteBuffer.allocate(28).apply {
            put(employeeId)
            putInt(amount)
            putInt(timestamp)
            putInt(uniqueNumber)
        }.array()
    }

    /**
     * Hash PIN using BCrypt
     */
    fun hashPin(pin: String): String {
        return at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
            .hashToString(12, pin.toCharArray())
    }

    /**
     * Verify PIN against BCrypt hash
     */
    fun verifyPin(pin: String, hash: String): Boolean {
        return at.favre.lib.crypto.bcrypt.BCrypt.verifyer()
            .verify(pin.toCharArray(), hash).verified
    }

    /**
     * Encode bytes to Base64 string
     */
    fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Decode Base64 string to bytes
     */
    fun decodeBase64(str: String): ByteArray {
        return Base64.getDecoder().decode(str)
    }
}
